import Foundation

/// Identifies the existing session's wire contract, never a fabricated device fingerprint.
public enum ClientPlatform: String, Codable, Sendable {
    case ios, android
    public var appPlatformHeader: String { self == .ios ? "iOS" : "Android" }
}

public struct LegacyAuth: Codable, Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public var accountID: String
    public var token: String
    public var refreshToken: String
    public var expiresAt: Date?
    public init(accountID: String, token: String, refreshToken: String = "", expiresAt: Date? = nil) {
        self.accountID = accountID; self.token = token; self.refreshToken = refreshToken; self.expiresAt = expiresAt
    }
    public var description: String { "LegacyAuth([redacted])" }
    public var debugDescription: String { description }
}

public struct GatewayAuth: Codable, Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public var accountID: String
    public var accessToken: String
    public var refreshToken: String
    public var signKey: Data
    public var expiresAt: Date?
    public init(accountID: String, accessToken: String, refreshToken: String = "", signKey: Data, expiresAt: Date? = nil) {
        self.accountID = accountID; self.accessToken = accessToken; self.refreshToken = refreshToken
        self.signKey = signKey; self.expiresAt = expiresAt
    }
    public var description: String { "GatewayAuth([redacted])" }
    public var debugDescription: String { description }
}

public struct RouteData: Codable, Equatable, Sendable {
    public var appRegion: String
    public var appCenter: String?
    public init(appRegion: String, appCenter: String? = nil) {
        self.appRegion = appRegion; self.appCenter = appCenter
    }
}

/// Persist this entire value only in the app's protected Keychain store.
/// Operation PINs, risk fingerprints and vehicle coordinates are deliberately absent.
public struct Session: Codable, Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public var deviceID: String
    public var phone: String
    public var clientPlatform: ClientPlatform
    public var oldAuth: LegacyAuth?
    public var newAuth: GatewayAuth?
    public var selectedVIN: String
    public var selectedCarType: String
    public var route: RouteData?
    public var appVersion: String
    public var subVersion: String

    public init(deviceID: String = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased(),
                phone: String = "", clientPlatform: ClientPlatform = .ios,
                oldAuth: LegacyAuth? = nil, newAuth: GatewayAuth? = nil,
                selectedVIN: String = "", selectedCarType: String = "", route: RouteData? = nil,
                appVersion: String = "1.22.96", subVersion: String = "3.21.3-2") {
        self.deviceID = deviceID; self.phone = phone; self.clientPlatform = clientPlatform
        self.oldAuth = oldAuth; self.newAuth = newAuth; self.selectedVIN = selectedVIN
        self.selectedCarType = selectedCarType; self.route = route
        self.appVersion = appVersion; self.subVersion = subVersion
    }
    public var isAuthenticated: Bool { oldAuth != nil && newAuth != nil }
    public var description: String { "Session(authenticated: \(isAuthenticated), credentials: [redacted])" }
    public var debugDescription: String { description }
}

public enum SessionImportError: Error, LocalizedError, Equatable {
    case invalidJSON, tooLarge, missingCredentials, missingDeviceIdentity, missingPlatform, invalidPhone
    public var errorDescription: String? {
        switch self {
        case .invalidJSON: return "会话文件必须是有效 JSON。"
        case .tooLarge: return "会话文件过大。"
        case .missingCredentials: return "会话文件缺少有效的 accountId 和 token。"
        case .missingDeviceIdentity: return "请提供会话原有的 deviceID，不能伪造设备身份。"
        case .missingPlatform: return "会话文件需明确 clientPlatform 为 ios 或 android。"
        case .invalidPhone: return "手机号格式无效。"
        }
    }
}

/// Developer recovery only. Call from an explicit, local file-import action.
/// Parsing does not authenticate the user; gateway exchange and vehicle listing do.
public enum SessionImporter {
    public static func parseLegacyJSON(_ text: String, phone: String = "", now: Date = Date()) throws -> Session {
        guard text.utf8.count <= 128 * 1024 else { throw SessionImportError.tooLarge }
        guard let root = try? JSONValue(data: Data(text.utf8)), root.objectValue != nil else {
            throw SessionImportError.invalidJSON
        }
        guard let platformText = root["clientPlatform"]?.stringValue,
              let platform = ClientPlatform(rawValue: platformText.lowercased()) else { throw SessionImportError.missingPlatform }
        let device = (root["deviceID"]?.stringValue ?? root["deviceId"]?.stringValue ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard WireJSON.isIdentifier(device, min: 16, max: 256) else { throw SessionImportError.missingDeviceIdentity }
        guard let auth = WireJSON.findObject(in: root, matching: { $0["accountId"] != nil && $0["token"] != nil }),
              let accountID = auth["accountId"]?.scalarString,
              let token = auth["token"]?.stringValue,
              WireJSON.isIdentifier(accountID, min: 1, max: 128), WireJSON.isCredential(token) else {
            throw SessionImportError.missingCredentials
        }
        let cleanPhone = (phone.isEmpty ? root["phone"]?.stringValue ?? "" : phone).trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanPhone.isEmpty || (cleanPhone.utf8.count == 11 && cleanPhone.utf8.allSatisfy({ (48...57).contains($0) })) else {
            throw SessionImportError.invalidPhone
        }
        let refresh = auth["refreshToken"]?.stringValue ?? ""
        guard refresh.isEmpty || WireJSON.isCredential(refresh) else { throw SessionImportError.missingCredentials }
        // Unknown issue time stays unknown. Do not reset the lifetime of an old imported token.
        let expires: Date?
        if let issuedMS = auth["tokenObtainedAt"]?.doubleValue, issuedMS > 0,
           let ttl = auth["tokenExpired"]?.doubleValue, ttl > 0, ttl <= 31_536_000 {
            expires = Date(timeIntervalSince1970: issuedMS / 1000 + ttl)
        } else { expires = nil }
        return Session(deviceID: device, phone: cleanPhone, clientPlatform: platform,
                       oldAuth: LegacyAuth(accountID: accountID, token: token, refreshToken: refresh, expiresAt: expires))
    }
}

/// Integration boundary for an authorized native Shumei + GeeTest implementation.
/// SDK collection must start only after the application's privacy-consent flow.
public protocol DeviceVerificationProvider: Sendable {
    var isNativeLoginAvailable: Bool { get }
    func verifiedDeviceIdentifier() async throws -> String
}

public struct UnavailableDeviceVerificationProvider: DeviceVerificationProvider {
    public init() {}
    public var isNativeLoginAvailable: Bool { false }
    public func verifiedDeviceIdentifier() async throws -> String { throw LeapAPIError.nativeSMSUnavailable }
}

internal enum WireJSON {
    static func isIdentifier(_ text: String, min: Int = 1, max: Int = 256) -> Bool {
        (min...max).contains(text.utf8.count) && text.utf8.allSatisfy {
            (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) || $0 == 45 || $0 == 95
        }
    }
    static func isCredential(_ text: String) -> Bool {
        (16...16_384).contains(text.utf8.count) && text.utf8.allSatisfy { (33...126).contains($0) }
    }
    static func findObject(in value: JSONValue, depth: Int = 0,
                           matching predicate: ([String: JSONValue]) -> Bool) -> [String: JSONValue]? {
        guard depth < 20 else { return nil }
        if let object = value.objectValue {
            if predicate(object) { return object }
            for key in object.keys.sorted() {
                if let found = findObject(in: object[key]!, depth: depth + 1, matching: predicate) { return found }
            }
        } else if let array = value.arrayValue {
            for item in array {
                if let found = findObject(in: item, depth: depth + 1, matching: predicate) { return found }
            }
        }
        return nil
    }
    static func objects(in value: JSONValue, depth: Int = 0, matching predicate: ([String: JSONValue]) -> Bool) -> [[String: JSONValue]] {
        guard depth < 20 else { return [] }
        if let object = value.objectValue {
            var output = predicate(object) ? [object] : []
            for key in object.keys.sorted() { output += objects(in: object[key]!, depth: depth + 1, matching: predicate) }
            return output
        }
        return value.arrayValue?.flatMap { objects(in: $0, depth: depth + 1, matching: predicate) } ?? []
    }
}
