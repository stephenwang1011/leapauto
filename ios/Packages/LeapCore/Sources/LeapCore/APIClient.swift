import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public enum LeapAPIError: Error, LocalizedError, Equatable, Sendable {
    case nativeSMSUnavailable, authenticationRequired, invalidSession, invalidResponse
    case legacyRefreshUnavailable, untrustedRoute, redirectRejected, networkUnavailable
    case httpStatus(Int), serverRejected(code: String?), noVehicle, unknownVehicle
    case busy, sessionChanged, commandOutcomeUnknown

    public var errorDescription: String? {
        switch self {
        case .nativeSMSUnavailable: return "原生 iOS 短信登录尚未接入受授权的设备安全 SDK；当前仅支持开发者本地会话恢复。"
        case .authenticationRequired: return "会话已失效，请重新导入有效会话。"
        case .invalidSession: return "会话资料无效，请重新导入。"
        case .invalidResponse: return "服务端响应格式异常。"
        case .legacyRefreshUnavailable: return "旧会话续期需要原手机号和刷新凭据，请重新导入会话。"
        case .untrustedRoute: return "服务端返回了不受信任的地址，已停止请求。"
        case .redirectRejected: return "服务器重定向已阻止，身份凭据未转发。"
        case .networkUnavailable: return "网络请求失败，请检查网络后重试。"
        case .httpStatus(let code): return "服务暂不可用（HTTP \(code)）。"
        case .serverRejected(let code): return code.map { "服务端拒绝请求（\($0)）。" } ?? "服务端未确认请求成功。"
        case .noVehicle: return "请先选择车辆。"
        case .unknownVehicle: return "所选车辆不在当前账户的车辆列表中。"
        case .busy: return "上一项请求尚未结束，请稍后重试。"
        case .sessionChanged: return "会话已更改，已丢弃旧请求结果。"
        case .commandOutcomeUnknown: return "指令结果尚未确认，系统不会自动重发。请刷新车况核实后再决定。"
        }
    }
}

public struct HTTPResult: Sendable {
    public let statusCode: Int
    public let body: Data
    public let responseURL: URL?
    public init(statusCode: Int, body: Data, responseURL: URL? = nil) {
        self.statusCode = statusCode; self.body = body; self.responseURL = responseURL
    }
}

public protocol HTTPTransport: Sendable {
    func execute(_ request: URLRequest) async throws -> HTTPResult
}

private final class RejectRedirectsDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        // Never forward tokens/PIN ciphertext or other identity headers, including same-host redirects.
        completionHandler(nil)
    }
}

public final class URLSessionTransport: HTTPTransport, @unchecked Sendable {
    private let session: URLSession
    public init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil; configuration.httpCookieStorage = nil; configuration.urlCredentialStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.timeoutIntervalForRequest = 30; configuration.timeoutIntervalForResource = 45
        session = URLSession(configuration: configuration, delegate: RejectRedirectsDelegate(), delegateQueue: nil)
    }
    deinit { session.invalidateAndCancel() }
    public func execute(_ request: URLRequest) async throws -> HTTPResult {
        guard let url = request.url else { throw LeapAPIError.untrustedRoute }
        try TrustedEndpoints.validateRequestURL(url)
        let (body, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw LeapAPIError.invalidResponse }
        return HTTPResult(statusCode: http.statusCode, body: body, responseURL: http.url)
    }
}

public struct ControlSubmission: Equatable, Sendable {
    public let messageID: String?
    public var isAccepted: Bool { true }
    public var accepted: Bool { true }
    public var outcome: ControlOutcome { .accepted(messageID: messageID) }

    /// Only create after an explicit server success; a POST is never execution proof.
    public init(response: JSONValue) throws {
        try LeapAPIResponse.requireSuccess(response)
        let object = WireJSON.findObject(in: response) { $0["msgID"] != nil || $0["msgId"] != nil || $0["eventId"] != nil }
        let candidate = object?["msgID"]?.scalarString ?? object?["msgId"]?.scalarString
            ?? object?["eventId"]?.scalarString ?? response["data"]?.scalarString
        let clean = candidate?.trimmingCharacters(in: .whitespacesAndNewlines)
        messageID = clean.flatMap { WireJSON.isIdentifier($0, min: 1, max: 256) ? $0 : nil }
    }
}

internal enum LeapAPIResponse {
    static func businessCode(_ value: JSONValue) -> String? {
        let result = value["result"].flatMap { $0.isNull ? nil : $0 }
        let code = value["code"].flatMap { $0.isNull ? nil : $0 }
        return (result ?? code)?.scalarString
    }
    static func isAuthenticationFailure(_ value: JSONValue) -> Bool {
        if let code = businessCode(value), ["401", "403", "39"].contains(code) { return true }
        let message = ((value["msg"]?.stringValue ?? "") + (value["message"]?.stringValue ?? "")).lowercased()
        let auth = ["token", "登录", "鉴权", "认证"].contains { message.contains($0) }
        let invalid = ["expire", "invalid", "过期", "失效", "无效", "重新登录"].contains { message.contains($0) }
        return auth && invalid
    }
    static func requireSuccess(_ value: JSONValue) throws {
        if isAuthenticationFailure(value) { throw LeapAPIError.authenticationRequired }
        guard let code = businessCode(value), ["0", "200"].contains(code) else {
            let code = businessCode(value).flatMap { $0.range(of: "^-?[0-9]{1,9}$", options: .regularExpression) == nil ? nil : $0 }
            throw LeapAPIError.serverRejected(code: code)
        }
    }
}

/// Network orchestration is serialized. Logout invalidates in-flight requests;
/// credentials rotate in memory and the app persists snapshot() to Keychain.
public actor LeapAPI {
    private var state: Session
    private let transport: any HTTPTransport
    private let builder: LeapRequestBuilder
    private let now: @Sendable () -> Date
    private var generation: UInt64 = 0
    private var activeOperation: UUID?
    private var vehicles: [Vehicle] = []
    private var lastStatus: VehicleStatus?

    public init(session: Session = Session(), transport: any HTTPTransport = URLSessionTransport(),
                crypto: any ProtocolCryptography = SystemProtocolCryptography(),
                now: @escaping @Sendable () -> Date = { Date() }) {
        state = session; self.transport = transport; builder = LeapRequestBuilder(crypto: crypto); self.now = now
    }

    public func snapshot() -> Session { state }
    public func logout() {
        generation &+= 1; activeOperation = nil; vehicles = []; lastStatus = nil
        state = Session()
    }

    /// No SMS request is sent until a separately reviewed native vendor integration exists.
    public func sendSMS(phone: String) async throws { throw LeapAPIError.nativeSMSUnavailable }
    public func loginWithSMS(phone: String, code: String) async throws { throw LeapAPIError.nativeSMSUnavailable }

    public func importLegacyAuth(rawJSON: String, phone: String = "") async throws {
        let operation = try begin(); defer { finish(operation.id) }
        var candidate = try SessionImporter.parseLegacyJSON(rawJSON, phone: phone, now: now())
        try await exchange(&candidate, epoch: operation.epoch)
        try check(operation.epoch)
        state = candidate; vehicles = []; lastStatus = nil
    }

    public func exchangeGateway() async throws {
        let operation = try begin(); defer { finish(operation.id) }
        var candidate = state
        defer { commit(candidate, epoch: operation.epoch) }
        try await exchange(&candidate, epoch: operation.epoch)
    }

    /// Validates server authentication, lists vehicles and resolves the selected route.
    public func bootstrap() async throws -> [Vehicle] {
        let operation = try begin(); defer { finish(operation.id) }
        var candidate = state
        defer { commit(candidate, epoch: operation.epoch) }
        let response = try await gateway(path: "/app/app-global-service/v1/vehicle/list", session: &candidate, epoch: operation.epoch)
        var seen = Set<String>()
        let parsed = WireJSON.objects(in: response) { object in
            object["vin"]?.stringValue.map { WireJSON.isIdentifier($0, min: 8, max: 32) } ?? false
        }.map { Vehicle(json: .object($0)) }.filter { seen.insert($0.vin).inserted }
        vehicles = parsed
        if let chosen = parsed.first(where: { $0.vin == candidate.selectedVIN }) ?? parsed.first {
            if candidate.selectedVIN != chosen.vin { candidate.route = nil; lastStatus = nil }
            candidate.selectedVIN = chosen.vin; candidate.selectedCarType = chosen.carType ?? ""
            try await resolveRoute(&candidate, epoch: operation.epoch)
        } else { candidate.selectedVIN = ""; candidate.selectedCarType = ""; candidate.route = nil; lastStatus = nil }
        return parsed
    }

    public func selectVehicle(_ vehicle: Vehicle) async throws {
        let operation = try begin(); defer { finish(operation.id) }
        guard vehicles.contains(where: { $0.vin == vehicle.vin }) else { throw LeapAPIError.unknownVehicle }
        var candidate = state
        defer {
            // Refresh credentials can rotate before route resolution fails. Preserve
            // those rotations without changing the selected vehicle on a failed switch.
            if operation.epoch == generation {
                state.oldAuth = candidate.oldAuth; state.newAuth = candidate.newAuth
                state.deviceID = candidate.deviceID
            }
        }
        candidate.selectedVIN = vehicle.vin; candidate.selectedCarType = vehicle.carType ?? ""; candidate.route = nil
        lastStatus = nil
        try await resolveRoute(&candidate, epoch: operation.epoch)
        try check(operation.epoch); state = candidate
    }

    public func refreshStatus() async throws -> VehicleStatus {
        let operation = try begin(); defer { finish(operation.id) }
        var candidate = state
        defer { commit(candidate, epoch: operation.epoch) }
        try await resolveRoute(&candidate, epoch: operation.epoch)
        let response = try await gateway(base: candidate.route!.appRegion, path: "/app/app-signal-service/signal/info/query",
                                         method: "POST", parameters: ["vin": candidate.selectedVIN], session: &candidate, epoch: operation.epoch)
        let status = VehicleStatus(raw: response, receivedAt: now(), previousLocation: lastStatus?.location)
        try check(operation.epoch); lastStatus = status
        return status
    }

    /// One user intent → one POST. Auth refresh happens before dispatch only.
    /// A timeout/disconnect cannot distinguish accepted from unsent, so never retry.
    public func send(command: LeapCommand, pin: String) async throws -> ControlSubmission {
        let operation = try begin(); defer { finish(operation.id) }
        var candidate = state
        defer { commit(candidate, epoch: operation.epoch) }
        try command.validate(status: lastStatus, now: now())
        try requireVIN(candidate)
        try await ensureGateway(&candidate, epoch: operation.epoch)
        try await ensureLegacy(&candidate, epoch: operation.epoch)
        // Refresh/crypto awaits can take time: check telemetry again immediately before dispatch.
        try command.validate(status: lastStatus, now: now())
        let request = try builder.control(command: command.build(), pin: pin, session: candidate, context: .live(now: now()))
        do {
            let response = try await execute(request, epoch: operation.epoch)
            return try ControlSubmission(response: response)
        } catch let error as LeapAPIError {
            switch error {
            case .networkUnavailable, .invalidResponse, .httpStatus, .redirectRejected:
                throw LeapAPIError.commandOutcomeUnknown
            case .serverRejected(code: nil): throw LeapAPIError.commandOutcomeUnknown
            default: throw error
            }
        } catch is CancellationError { throw LeapAPIError.commandOutcomeUnknown }
    }

    public func pollResult(messageID: String) async throws -> ControlPollResult {
        let operation = try begin(); defer { finish(operation.id) }
        var candidate = state
        defer { commit(candidate, epoch: operation.epoch) }
        try requireVIN(candidate)
        try await ensureGateway(&candidate, epoch: operation.epoch)
        try await ensureLegacy(&candidate, epoch: operation.epoch)
        let request = try builder.controlResult(messageID: messageID, session: candidate, context: .live(now: now()))
        let response = try await execute(request, epoch: operation.epoch, requireSuccess: false)
        return ControlPollResult(raw: response)
    }

    public func refreshTokens() async throws {
        let operation = try begin(); defer { finish(operation.id) }
        var candidate = state
        defer { commit(candidate, epoch: operation.epoch) }
        try await refreshGateway(&candidate, epoch: operation.epoch)
    }

    private func begin() throws -> (id: UUID, epoch: UInt64) {
        guard activeOperation == nil else { throw LeapAPIError.busy }
        let id = UUID(); activeOperation = id
        return (id, generation)
    }
    private func finish(_ id: UUID) { if activeOperation == id { activeOperation = nil } }
    private func check(_ epoch: UInt64) throws {
        guard epoch == generation else { throw LeapAPIError.sessionChanged }
        try Task.checkCancellation()
    }
    private func commit(_ candidate: Session, epoch: UInt64) { if epoch == generation { state = candidate } }
    private func requireVIN(_ session: Session) throws {
        guard WireJSON.isIdentifier(session.selectedVIN, min: 8, max: 32) else { throw LeapAPIError.noVehicle }
    }
    private func validate(_ session: Session) throws {
        guard WireJSON.isIdentifier(session.deviceID, min: 16, max: 256),
              let auth = session.oldAuth, WireJSON.isCredential(auth.token),
              WireJSON.isIdentifier(auth.accountID, min: 1, max: 128) else { throw LeapAPIError.invalidSession }
        if let gateway = session.newAuth {
            guard WireJSON.isCredential(gateway.accessToken), !gateway.signKey.isEmpty else { throw LeapAPIError.invalidSession }
        }
    }

    private func execute(_ request: URLRequest, epoch: UInt64, requireSuccess: Bool = true) async throws -> JSONValue {
        try check(epoch)
        guard let url = request.url else { throw LeapAPIError.untrustedRoute }
        try TrustedEndpoints.validateRequestURL(url)
        let response: HTTPResult
        do { response = try await transport.execute(request) }
        catch is CancellationError { throw CancellationError() }
        catch let error as LeapAPIError { throw error }
        catch { throw LeapAPIError.networkUnavailable }
        try check(epoch)
        if (300..<400).contains(response.statusCode) { throw LeapAPIError.redirectRejected }
        if let finalURL = response.responseURL, finalURL != url { throw LeapAPIError.redirectRejected }
        if [401, 403].contains(response.statusCode) { throw LeapAPIError.authenticationRequired }
        guard (200..<300).contains(response.statusCode) else { throw LeapAPIError.httpStatus(response.statusCode) }
        guard response.body.count <= 4 * 1024 * 1024,
              let value = try? JSONValue(data: response.body), value.objectValue != nil else { throw LeapAPIError.invalidResponse }
        if LeapAPIResponse.isAuthenticationFailure(value) { throw LeapAPIError.authenticationRequired }
        if requireSuccess { try LeapAPIResponse.requireSuccess(value) }
        return value
    }

    private func gateway(base: String = TrustedEndpoints.global, path: String, method: String = "GET",
                         parameters: [String: String] = [:], session: inout Session, epoch: UInt64) async throws -> JSONValue {
        try await ensureGateway(&session, epoch: epoch)
        func request(_ session: Session) throws -> URLRequest {
            try builder.gateway(base: base, path: path, method: method, parameters: parameters, session: session, context: .live(now: now()))
        }
        do { return try await execute(request(session), epoch: epoch) }
        catch LeapAPIError.authenticationRequired {
            // Only read-only operations call this helper (status POST is a query).
            try await refreshGateway(&session, epoch: epoch)
            return try await execute(request(session), epoch: epoch)
        }
    }

    private func ensureGateway(_ session: inout Session, epoch: UInt64) async throws {
        try validate(session)
        if session.newAuth == nil { try await exchange(&session, epoch: epoch) }
        else if let expiry = session.newAuth?.expiresAt, expiry.timeIntervalSince(now()) <= 300 {
            try await refreshGateway(&session, epoch: epoch)
        }
    }
    private func ensureLegacy(_ session: inout Session, epoch: UInt64) async throws {
        guard let auth = session.oldAuth else { throw LeapAPIError.authenticationRequired }
        if let expiry = auth.expiresAt, expiry.timeIntervalSince(now()) <= 60 {
            try await refreshLegacy(&session, epoch: epoch)
            try await exchange(&session, epoch: epoch)
        }
    }
    private func exchange(_ session: inout Session, epoch: UInt64) async throws {
        try validate(session)
        guard let old = session.oldAuth else { throw LeapAPIError.authenticationRequired }
        let request = try builder.gateway(path: "/base/base-user/account/v1/login", method: "POST",
                                          parameters: ["identifier": old.accountID, "identifierType": "1", "security": old.token],
                                          session: session, context: .live(now: now()), authenticated: false)
        let response = try await execute(request, epoch: epoch)
        try applyGateway(response, to: &session)
    }
    private func refreshGateway(_ session: inout Session, epoch: UInt64) async throws {
        if let auth = session.newAuth, !auth.refreshToken.isEmpty {
            let request = try builder.gateway(path: "/base/base-user/token/v1/refresh", method: "POST",
                                              parameters: ["refreshToken": auth.refreshToken], session: session, context: .live(now: now()))
            do {
                let response = try await execute(request, epoch: epoch)
                try applyGateway(response, to: &session)
                return
            } catch LeapAPIError.authenticationRequired {
                // A definitively rejected refresh may use the supported legacy recovery flow.
            } catch LeapAPIError.serverRejected { }
            // Network failures/redirects/invalid JSON are not an invitation to rotate more credentials.
        }
        try await refreshLegacy(&session, epoch: epoch)
        try await exchange(&session, epoch: epoch)
    }
    private func refreshLegacy(_ session: inout Session, epoch: UInt64) async throws {
        guard var old = session.oldAuth else { throw LeapAPIError.authenticationRequired }
        let response = try await execute(builder.refreshLegacy(session: session, context: .live(now: now())), epoch: epoch)
        let data = response["data"] ?? response
        guard let token = data["token"]?.stringValue, WireJSON.isCredential(token) else { throw LeapAPIError.invalidResponse }
        old.token = token
        if let refresh = data["refreshToken"]?.stringValue, !refresh.isEmpty {
            guard WireJSON.isCredential(refresh) else { throw LeapAPIError.invalidResponse }
            old.refreshToken = refresh
        }
        let ttl = data["tokenExpired"]?.doubleValue ?? 21_600
        guard ttl > 0, ttl <= 31_536_000 else { throw LeapAPIError.invalidResponse }
        old.expiresAt = now().addingTimeInterval(ttl); session.oldAuth = old
    }
    private func applyGateway(_ response: JSONValue, to session: inout Session) throws {
        guard let data = WireJSON.findObject(in: response, matching: { $0["accessToken"] != nil && $0["signParam"] != nil }),
              let access = data["accessToken"]?.stringValue, WireJSON.isCredential(access),
              let r2 = data["signParam"]?["r2"]?.stringValue, let r3 = data["signParam"]?["r3"]?.stringValue,
              let old = session.oldAuth else { throw LeapAPIError.invalidResponse }
        let key = try TokenClaims.deriveSignKey(accessToken: access, r2: r2, r3: r3)
        let refresh = data["refreshToken"]?.stringValue ?? ""
        guard refresh.isEmpty || WireJSON.isCredential(refresh) else { throw LeapAPIError.invalidResponse }
        var expiry = TokenClaims.expiry(access)
        if expiry == nil, let interval = data["tokenExpireTime"]?.doubleValue, interval > 0 {
            // Captures differ: accept JWT exp first, then epoch ms / epoch seconds / relative seconds.
            if interval >= 1_000_000_000_000 { expiry = Date(timeIntervalSince1970: interval / 1000) }
            else if interval >= 1_000_000_000 { expiry = Date(timeIntervalSince1970: interval) }
            else if interval <= 31_536_000 { expiry = now().addingTimeInterval(interval) }
        }
        if let bound = TokenClaims.deviceID(access) {
            guard WireJSON.isIdentifier(bound, min: 16, max: 256) else { throw LeapAPIError.invalidResponse }
            session.deviceID = bound
        }
        session.newAuth = GatewayAuth(accountID: old.accountID, accessToken: access, refreshToken: refresh, signKey: key, expiresAt: expiry)
    }
    private func resolveRoute(_ session: inout Session, epoch: UInt64) async throws {
        try requireVIN(session)
        if let route = session.route { _ = try TrustedEndpoints.validateBase(route.appRegion, regionalOnly: true); return }
        let response = try await gateway(path: "/app/app-global-service/v1/vehicle/getCarRoute",
                                         parameters: ["vin": session.selectedVIN], session: &session, epoch: epoch)
        guard let object = WireJSON.findObject(in: response, matching: { $0["appRegion"] != nil }),
              let region = object["appRegion"]?.stringValue else { throw LeapAPIError.invalidResponse }
        _ = try TrustedEndpoints.validateBase(region, regionalOnly: true)
        let center = object["appCenter"]?.stringValue
        if let center, !center.isEmpty { _ = try TrustedEndpoints.validateBase(center, regionalOnly: true) }
        session.route = RouteData(appRegion: region, appCenter: center)
    }
}
