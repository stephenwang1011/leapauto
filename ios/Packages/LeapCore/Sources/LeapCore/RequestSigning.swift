import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public struct SigningContext: Equatable, Sendable {
    public let timestampMilliseconds: Int64
    public let nonce: String
    public init(timestampMilliseconds: Int64, nonce: String) {
        self.timestampMilliseconds = timestampMilliseconds; self.nonce = nonce
    }
    public static func live(now: Date = Date()) -> SigningContext {
        SigningContext(timestampMilliseconds: Int64(now.timeIntervalSince1970 * 1000), nonce: String(Int.random(in: 10_000...10_009_999)))
    }
}

public enum TrustedEndpoints {
    public static let appUser = "https://appuser.leapmotor.cn"
    public static let global = "https://app-gw-global-master.leapmotor.com"
    public static let control = "https://appgateway.leapmotor.com"

    /// Only HTTPS vendor gateways. No URL credentials, nonstandard ports, query,
    /// fragment, trailing-dot host, IP address or arbitrary route path is accepted.
    public static func validateBase(_ text: String, regionalOnly: Bool = false) throws -> URL {
        guard let components = URLComponents(string: text), components.scheme == "https",
              let host = components.host, host == host.lowercased(),
              components.user == nil, components.password == nil,
              components.port == nil || components.port == 443,
              components.query == nil, components.fragment == nil,
              components.path.isEmpty || components.path == "/",
              let url = components.url else { throw LeapAPIError.untrustedRoute }
        let gateway = host.range(of: "^app-gw-[a-z0-9-]+\\.leapmotor\\.com$", options: .regularExpression) != nil
        let allowed = gateway || host == "appgateway.leapmotor.com" || (!regionalOnly && host == "appuser.leapmotor.cn")
        guard allowed else { throw LeapAPIError.untrustedRoute }
        return url
    }

    public static func validateRequestURL(_ url: URL) throws {
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false), components.fragment == nil else {
            throw LeapAPIError.untrustedRoute
        }
        components.path = ""; components.query = nil
        guard let base = components.string else { throw LeapAPIError.untrustedRoute }
        _ = try validateBase(base)
    }
}

/// Pure construction: it never performs I/O, reads Keychain, or changes a session.
public struct LeapRequestBuilder: Sendable {
    public let crypto: any ProtocolCryptography
    public init(crypto: any ProtocolCryptography = SystemProtocolCryptography()) { self.crypto = crypto }

    /// Executable Kotlin + JavaScript source use sorted VALUES only. API.md's
    /// key+value/uppercase prose differs; the port deliberately follows the code.
    public static func sortedValues(_ maps: [String: String?]...) -> String {
        var merged: [String: String] = [:]
        for map in maps { for (key, value) in map { if let value { merged[key] = value } } }
        return merged.keys.sorted().map { merged[$0]! }.joined()
    }

    public func oldSignedParameters(_ business: [String: String], session: Session,
                                    context: SigningContext, refresh: Bool = false) throws -> [String: String] {
        guard let auth = session.oldAuth else { throw LeapAPIError.authenticationRequired }
        var params = business
        params["timespan"] = String(context.timestampMilliseconds)
        params["nonce"] = context.nonce; params["deviceID"] = session.deviceID
        let secretName = refresh ? "refreshtoken" : "token"
        params[secretName] = refresh ? auth.refreshToken : auth.token
        let signature = try crypto.md5Short(Self.sortedValues(params.mapValues(Optional.some)))
        params.removeValue(forKey: "token"); params.removeValue(forKey: "refreshtoken")
        params["signStr"] = signature
        return params
    }

    public func oldHeaders(session: Session, includeToken: Bool = true) -> [String: String] {
        var result = ["User-Agent": "LeapAuto-iOS/1.0", "APPPlatform": session.clientPlatform.appPlatformHeader,
                      "APPVersion": session.appVersion, "APPImei": session.deviceID, "C-VERSIONS": "APP", "XFX-CDN-VRS": "v4"]
        if includeToken, let auth = session.oldAuth { result["XFX-CDN-CROSS-NODE"] = auth.token }
        return result
    }

    public func gatewayHeaders(parameters: [String: String], session: Session,
                               context: SigningContext, authenticated: Bool = true) throws -> [String: String] {
        let signed = ["acceptLanguage": "zh-CN", "channel": "1", "deviceId": session.deviceID,
                      "deviceType": session.clientPlatform.rawValue, "nonce": context.nonce, "source": "leapmotor",
                      "timestamp": String(context.timestampMilliseconds), "version": session.appVersion]
        var headers = signed
        headers.merge(["x-region": "CN", "x-api-signature-version": "2.0", "digest": "", "x-subversion": session.subVersion,
                       "userId": session.oldAuth?.accountID ?? "", "carvin": session.selectedVIN,
                       "cartype": session.selectedCarType]) { _, new in new }
        let signBase = Self.sortedValues(signed.mapValues(Optional.some), parameters.mapValues(Optional.some))
        if authenticated {
            guard let auth = session.newAuth, !auth.signKey.isEmpty else { throw LeapAPIError.authenticationRequired }
            headers["token"] = auth.accessToken; headers["userId"] = auth.accountID
            headers["sign"] = try crypto.hmacSHA256Hex(key: auth.signKey, value: signBase)
        } else { headers["sign"] = try crypto.sha256Hex(signBase) }
        return headers
    }

    public func gateway(base: String = TrustedEndpoints.global, path: String, method: String = "GET",
                        parameters: [String: String] = [:], session: Session, context: SigningContext,
                        authenticated: Bool = true) throws -> URLRequest {
        let headers = try gatewayHeaders(parameters: parameters, session: session, context: context, authenticated: authenticated)
        return try request(base: base, path: path, method: method, headers: headers,
                           query: method == "GET" ? parameters : [:],
                           json: method == "POST" ? parameters.mapValues(JSONValue.string) : nil)
    }

    public func control(command: CommandPayload, pin: String, session: Session, context: SigningContext) throws -> URLRequest {
        guard WireJSON.isIdentifier(session.selectedVIN, min: 8, max: 32) else { throw LeapAPIError.noVehicle }
        let token = session.newAuth?.accessToken ?? session.oldAuth?.token ?? ""
        let body = try oldSignedParameters(["cmdid": command.commandID, "state": command.stateJSON,
                                            "carvin": session.selectedVIN, "oppwd": crypto.encryptOperationPIN(pin, token: token)],
                                           session: session, context: context)
        var headers = oldHeaders(session: session)
        headers.merge(try gatewayHeaders(parameters: body, session: session, context: context)) { _, new in new }
        // A mutating command has exactly one destination and is never automatically replayed.
        return try request(base: TrustedEndpoints.control, path: "/app/app-control-service/v3/api/appremotectl",
                           method: "POST", headers: headers, form: body)
    }

    public func controlResult(messageID: String, session: Session, context: SigningContext) throws -> URLRequest {
        guard WireJSON.isIdentifier(messageID, min: 1, max: 256) else { throw LeapAPIError.invalidResponse }
        let params = try oldSignedParameters(["msgID": messageID], session: session, context: context)
        var headers = oldHeaders(session: session)
        headers.merge(try gatewayHeaders(parameters: params, session: session, context: context)) { _, new in new }
        return try request(base: TrustedEndpoints.control, path: "/app/app-control-service/v3/api/appremotectl/query",
                           headers: headers, query: params)
    }

    public func refreshLegacy(session: Session, context: SigningContext) throws -> URLRequest {
        guard let auth = session.oldAuth, !auth.refreshToken.isEmpty, !session.phone.isEmpty else {
            throw LeapAPIError.legacyRefreshUnavailable
        }
        let params = try oldSignedParameters(["accountId": auth.accountID, "accountNumber": crypto.rsaEncryptPhone(session.phone)],
                                             session: session, context: context, refresh: true)
        var headers = oldHeaders(session: session)
        headers["XFX-CDN-CROSS-REFRESH-NODE"] = auth.refreshToken
        return try request(base: TrustedEndpoints.appUser, path: "/app-user/appuseroperate/getnewtoken", headers: headers, query: params)
    }

    public func request(base: String, path: String, method: String = "GET", headers: [String: String] = [:],
                        query: [String: String] = [:], json: [String: JSONValue]? = nil,
                        form: [String: String]? = nil) throws -> URLRequest {
        let root = try TrustedEndpoints.validateBase(base)
        guard path.hasPrefix("/"), !path.hasPrefix("//"), !path.contains(".."),
              !path.contains("?"), !path.contains("#"), ["GET", "POST"].contains(method),
              !(json != nil && form != nil), method != "GET" || (json == nil && form == nil),
              var components = URLComponents(url: root, resolvingAgainstBaseURL: false) else { throw LeapAPIError.untrustedRoute }
        components.path = path
        if !query.isEmpty { components.percentEncodedQuery = Self.formEncoded(query) }
        guard let url = components.url else { throw LeapAPIError.untrustedRoute }
        var result = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        result.httpMethod = method
        for (name, value) in headers {
            guard !name.contains("\r"), !name.contains("\n"), !value.contains("\r"), !value.contains("\n") else {
                throw LeapAPIError.invalidSession
            }
            result.setValue(value, forHTTPHeaderField: name)
        }
        result.setValue("application/json", forHTTPHeaderField: "Accept")
        result.setValue("no-store", forHTTPHeaderField: "Cache-Control")
        if let json {
            result.httpBody = try JSONValue.object(json).encodedData()
            result.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        } else if let form {
            result.httpBody = Data(Self.formEncoded(form).utf8)
            result.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
        }
        return result
    }

    public static func formEncoded(_ values: [String: String]) -> String {
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        func encode(_ value: String) -> String { value.addingPercentEncoding(withAllowedCharacters: allowed) ?? "" }
        return values.keys.sorted().map { "\(encode($0))=\(encode(values[$0]!))" }.joined(separator: "&")
    }
}
