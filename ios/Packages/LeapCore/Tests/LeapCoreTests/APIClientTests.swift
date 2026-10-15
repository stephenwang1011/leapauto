import XCTest
@testable import LeapCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Deliberately non-cryptographic fixture signer, injected only by this test target.
private struct FixtureCryptography: ProtocolCryptography {
    func md5Short(_ value: String) throws -> String { "fixture-md5" }
    func sha256Hex(_ value: String) throws -> String { "fixture-sha256" }
    func hmacSHA256Hex(key: Data, value: String) throws -> String { "fixture-hmac" }
    func encryptOperationPIN(_ pin: String, token: String) throws -> String { "fixture-ciphertext" }
    func rsaEncryptPhone(_ phone: String) throws -> String { "fixture-phone-ciphertext" }
}

private actor FixtureTransport: HTTPTransport {
    enum Reply: Sendable { case json(String, Int = 200), error(LeapAPIError), cancelled, redirected(String) }
    var replies: [Reply]
    var requests: [URLRequest] = []
    init(_ replies: [Reply]) { self.replies = replies }
    func execute(_ request: URLRequest) async throws -> HTTPResult {
        requests.append(request)
        guard !replies.isEmpty else { throw LeapAPIError.invalidResponse }
        switch replies.removeFirst() {
        case .json(let text, let status): return HTTPResult(statusCode: status, body: Data(text.utf8), responseURL: request.url)
        case .error(let error): throw error
        case .cancelled: throw CancellationError()
        case .redirected(let address): return HTTPResult(statusCode: 200, body: Data("{\"code\":0}".utf8), responseURL: URL(string: address))
        }
    }
    func count() -> Int { requests.count }
    func commandCount() -> Int { requests.filter { $0.url?.path == "/app/app-control-service/v3/api/appremotectl" }.count }
    func allRequests() -> [URLRequest] { requests }
}

private actor SuspendedTransport: HTTPTransport {
    let firstResponse: String?
    var requests: [URLRequest] = []
    var continuation: CheckedContinuation<HTTPResult, Error>?
    var startWaiters: [CheckedContinuation<Void, Never>] = []
    init(firstResponse: String? = nil) { self.firstResponse = firstResponse }
    func execute(_ request: URLRequest) async throws -> HTTPResult {
        requests.append(request)
        if requests.count == 1, let firstResponse {
            return HTTPResult(statusCode: 200, body: Data(firstResponse.utf8), responseURL: request.url)
        }
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            startWaiters.forEach { $0.resume() }; startWaiters = []
        }
    }
    func waitUntilSuspended() async {
        if continuation != nil { return }
        await withCheckedContinuation { startWaiters.append($0) }
    }
    func complete(_ response: String) {
        continuation?.resume(returning: HTTPResult(statusCode: 200, body: Data(response.utf8), responseURL: requests.last?.url))
        continuation = nil
    }
    func commandCount() -> Int { requests.filter { $0.url?.path == "/app/app-control-service/v3/api/appremotectl" }.count }
}

private final class TestClock: @unchecked Sendable {
    private let lock = NSLock()
    private var date: Date
    init(_ date: Date) { self.date = date }
    func read() -> Date { lock.lock(); defer { lock.unlock() }; return date }
    func advance(_ seconds: TimeInterval) { lock.lock(); defer { lock.unlock() }; date = date.addingTimeInterval(seconds) }
}

final class APIClientTests: XCTestCase {
    private let instant = Date(timeIntervalSince1970: 1_800_000_000)
    private func session(expiry: Date? = nil) -> Session {
        // Synthetic values constructed at runtime; never real account credentials.
        Session(deviceID: String(repeating: "d", count: 32), clientPlatform: .android,
                oldAuth: LegacyAuth(accountID: "fixture-account", token: String(repeating: "o", count: 32)),
                newAuth: GatewayAuth(accountID: "fixture-account", accessToken: String(repeating: "g", count: 80),
                                     refreshToken: String(repeating: "r", count: 32), signKey: Data([1, 2, 3]), expiresAt: expiry),
                selectedVIN: "FIXTUREVIN0000001", selectedCarType: "C16", route: RouteData(appRegion: TrustedEndpoints.global))
    }
    private var parked: String { "{\"code\":0,\"data\":{\"signalMap\":{\"gearStatus\":\"P\",\"speed\":0,\"sts\":1800000000000,\"soc\":70}}}" }
    private func client(_ transport: any HTTPTransport, session: Session? = nil) -> LeapAPI {
        let time = instant
        return LeapAPI(session: session ?? self.session(), transport: transport, crypto: FixtureCryptography(), now: { time })
    }

    private func gatewayReply() throws -> (json: String, access: String) {
        func url64(_ data: Data) -> String {
            data.base64EncodedString().replacingOccurrences(of: "=", with: "").replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
        }
        let payload = try JSONValue.object(["exp": .number(1_800_003_600), "user_name": .string("deviceId:" + String(repeating: "b", count: 32))]).encodedData()
        let token = "fixture." + url64(payload) + "." + url64(Data([3, 4, 5, 6]))
        let root: JSONValue = .object(["code": .number(0), "data": .object([
            "accountId": .string("gateway-different-account"), "accessToken": .string(token),
            "refreshToken": .string(String(repeating: "n", count: 32)),
            "signParam": .object(["r2": .string(Data([1, 2, 3, 4]).base64EncodedString()),
                                  "r3": .string(Data([5, 6, 7, 8]).base64EncodedString())])
        ])])
        return (String(decoding: try root.encodedData(), as: UTF8.self), token)
    }

    func testGatewayCredentialRotationSurvivesLaterReadFailure() async throws {
        let reply = try gatewayReply()
        let transport = FixtureTransport([.json(reply.json), .error(.networkUnavailable)])
        let api = client(transport, session: session(expiry: instant.addingTimeInterval(30)))
        do { _ = try await api.refreshStatus(); XCTFail("The later read fails") }
        catch { XCTAssertEqual(error as? LeapAPIError, .networkUnavailable) }
        let snapshot = await api.snapshot()
        XCTAssertEqual(snapshot.newAuth?.accessToken, reply.access)
        XCTAssertEqual(snapshot.newAuth?.accountID, "fixture-account", "Keep upstream legacy account identity")
        XCTAssertEqual(snapshot.deviceID, String(repeating: "b", count: 32))
        XCTAssertEqual(snapshot.newAuth?.signKey, Data([7, 0, 1, 10]))
    }

    func testReadOnlyAuthenticationFailureRefreshesAndRetriesOnce() async throws {
        let reply = try gatewayReply()
        let transport = FixtureTransport([.json("{}", 401), .json(reply.json), .json(parked)])
        let api = client(transport)
        _ = try await api.refreshStatus()
        let requests = await transport.allRequests()
        XCTAssertEqual(requests.count, 3)
        XCTAssertEqual(requests[1].url?.path, "/base/base-user/token/v1/refresh")
        XCTAssertEqual(requests[2].value(forHTTPHeaderField: "token"), reply.access)
    }

    func testFailedImportDoesNotReplaceExistingSession() async throws {
        let transport = FixtureTransport([.json("{\"code\":401}")])
        let existing = session(); let api = client(transport, session: existing)
        let imported: JSONValue = .object([
            "clientPlatform": .string("android"), "deviceID": .string(String(repeating: "a", count: 32)),
            "accountId": .string("different-import"), "token": .string(String(repeating: "i", count: 32))
        ])
        do { try await api.importLegacyAuth(rawJSON: String(decoding: try imported.encodedData(), as: UTF8.self)); XCTFail("Must reject failed exchange") }
        catch { XCTAssertEqual(error as? LeapAPIError, .authenticationRequired) }
        let after = await api.snapshot(); XCTAssertEqual(after, existing)
    }

    func testSuccessfulImportExchangesBeforeBecomingAuthenticated() async throws {
        let reply = try gatewayReply()
        let transport = FixtureTransport([.json(reply.json)])
        let api = client(transport)
        let imported: JSONValue = .object([
            "clientPlatform": .string("android"), "deviceID": .string(String(repeating: "a", count: 32)),
            "accountId": .string("different-import"), "token": .string(String(repeating: "i", count: 32))
        ])
        try await api.importLegacyAuth(rawJSON: String(decoding: try imported.encodedData(), as: UTF8.self))
        let after = await api.snapshot()
        XCTAssertTrue(after.isAuthenticated); XCTAssertEqual(after.newAuth?.accountID, "different-import")
        XCTAssertEqual(after.selectedVIN, ""); XCTAssertNil(after.route)
        let requests = await transport.allRequests()
        XCTAssertEqual(requests.count, 1); XCTAssertEqual(requests[0].url?.path, "/base/base-user/account/v1/login")
        XCTAssertNil(requests[0].value(forHTTPHeaderField: "token"))
    }

    func testSMSFailsClosedWithoutNetwork() async throws {
        let transport = FixtureTransport([])
        let api = client(transport)
        do { try await api.sendSMS(phone: ""); XCTFail("Must reject unintegrated SMS") }
        catch { XCTAssertEqual(error as? LeapAPIError, .nativeSMSUnavailable) }
        do { try await api.loginWithSMS(phone: "", code: ""); XCTFail("Must reject unintegrated login") }
        catch { XCTAssertEqual(error as? LeapAPIError, .nativeSMSUnavailable) }
        let count = await transport.count(); XCTAssertEqual(count, 0)
    }

    func testBootstrapListsVehiclesAndValidatesRoute() async throws {
        let transport = FixtureTransport([
            .json("{\"code\":0,\"data\":[{\"vin\":\"FIXTUREVIN0000002\",\"carType\":\"C10\"}]}"),
            .json("{\"code\":0,\"data\":{\"appRegion\":\"https://app-gw-global-master.leapmotor.com\"}}")
        ])
        let api = client(transport)
        let cars = try await api.bootstrap()
        XCTAssertEqual(cars.map(\.vin), ["FIXTUREVIN0000002"])
        let snapshot = await api.snapshot(); XCTAssertEqual(snapshot.selectedVIN, "FIXTUREVIN0000002")
        XCTAssertNotNil(snapshot.route)
    }

    func testUntrustedRouteRejectedBeforeAnyTelemetryRequest() async throws {
        let transport = FixtureTransport([.json("{\"code\":0,\"data\":{\"appRegion\":\"https://app-gw-global-master.leapmotor.com.attacker.invalid\"}}")])
        var state = session(); state.route = nil
        let api = client(transport, session: state)
        do { _ = try await api.refreshStatus(); XCTFail("Must reject route") }
        catch { XCTAssertEqual(error as? LeapAPIError, .untrustedRoute) }
        let count = await transport.count(); XCTAssertEqual(count, 1)
    }

    func testRedirectsAndChangedFinalURLAreRejected() async throws {
        for reply in [FixtureTransport.Reply.json("{}", 302), .redirected("https://attacker.invalid/")] {
            let transport = FixtureTransport([reply]); let api = client(transport)
            do { _ = try await api.refreshStatus(); XCTFail("Must reject redirect") }
            catch { XCTAssertEqual(error as? LeapAPIError, .redirectRejected) }
            let count = await transport.count(); XCTAssertEqual(count, 1)
        }
    }

    func testControlNeedsFreshParkedTelemetry() async throws {
        let transport = FixtureTransport([]); let api = client(transport)
        do { _ = try await api.send(command: .lock, pin: "0000"); XCTFail("Must reject without telemetry") }
        catch { XCTAssertEqual(error as? CommandValidationError, .noVehicleStatus) }
        let count = await transport.count(); XCTAssertEqual(count, 0)
    }

    func testControlAcceptanceNeverMeansExecution() async throws {
        let transport = FixtureTransport([.json(parked), .json("{\"code\":0,\"data\":{\"msgID\":\"fixture-job\"}}"), .json("{\"result\":7}"), .json("{\"result\":0}")])
        let api = client(transport)
        _ = try await api.refreshStatus()
        let accepted = try await api.send(command: .lock, pin: "0000")
        XCTAssertEqual(accepted.outcome, .accepted(messageID: "fixture-job"))
        let pending = try await api.pollResult(messageID: "fixture-job"); XCTAssertEqual(pending, .pending)
        let success = try await api.pollResult(messageID: "fixture-job"); XCTAssertEqual(success, .succeeded)
        let count = await transport.commandCount(); XCTAssertEqual(count, 1)
        let requests = await transport.allRequests()
        XCTAssertEqual(requests[1].url?.host, "appgateway.leapmotor.com")
        XCTAssertEqual(requests[1].httpMethod, "POST")
        XCTAssertFalse(String(decoding: requests[1].httpBody ?? Data(), as: UTF8.self).contains("oppwd=0000"))
    }

    func testControlFailuresNeverReplayPOST() async throws {
        let replies: [FixtureTransport.Reply] = [.error(.networkUnavailable), .cancelled,
            .json("{}", 500), .json("{}", 401), .json("{}", 403), .json("{\"result\":39}"),
            .json("{\"code\":401}"), .json("{}", 302), .redirected("https://attacker.invalid/"), .json("not-json")]
        for reply in replies {
            let transport = FixtureTransport([.json(parked), reply]); let api = client(transport)
            _ = try await api.refreshStatus()
            do { _ = try await api.send(command: .lock, pin: "0000"); XCTFail("Expected failure") }
            catch { XCTAssertNotNil(error as? LeapAPIError) }
            let count = await transport.commandCount(); XCTAssertEqual(count, 1)
            let total = await transport.count(); XCTAssertEqual(total, 2, "No retry or fallback destination")
        }
    }

    func testLogoutDiscardsInflightTelemetry() async throws {
        let transport = SuspendedTransport(); let api = client(transport)
        let task = Task { try await api.refreshStatus() }
        await transport.waitUntilSuspended()
        await api.logout(); await transport.complete(parked)
        do { _ = try await task.value; XCTFail("Old request must not update new session") }
        catch { XCTAssertEqual(error as? LeapAPIError, .sessionChanged) }
        let snapshot = await api.snapshot(); XCTAssertFalse(snapshot.isAuthenticated); XCTAssertEqual(snapshot.selectedVIN, "")
    }

    func testLogoutDuringRefreshPreventsControlDispatch() async throws {
        let clock = TestClock(instant)
        let transport = SuspendedTransport(firstResponse: parked)
        let api = LeapAPI(session: session(expiry: instant.addingTimeInterval(360)), transport: transport,
                          crypto: FixtureCryptography(), now: { clock.read() })
        _ = try await api.refreshStatus()
        clock.advance(70)
        let command = Task { try await api.send(command: .lock, pin: "0000") }
        await transport.waitUntilSuspended()
        await api.logout(); await transport.complete("{\"code\":0}")
        do { _ = try await command.value; XCTFail("Logout must invalidate preflight") }
        catch { XCTAssertEqual(error as? LeapAPIError, .sessionChanged) }
        let count = await transport.commandCount(); XCTAssertEqual(count, 0)
    }

    func testConcurrentOperationsFailClosed() async throws {
        let transport = SuspendedTransport(); let api = client(transport)
        let task = Task { try await api.refreshStatus() }
        await transport.waitUntilSuspended()
        do { _ = try await api.refreshStatus(); XCTFail("Should serialize") }
        catch { XCTAssertEqual(error as? LeapAPIError, .busy) }
        await transport.complete(parked); _ = try await task.value
    }
}
