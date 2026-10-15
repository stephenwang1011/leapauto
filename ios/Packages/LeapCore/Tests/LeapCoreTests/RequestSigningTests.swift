import XCTest
@testable import LeapCore

private struct RecordingShapeCrypto: ProtocolCryptography {
    func md5Short(_ value: String) throws -> String { "md5(\(value))" }
    func sha256Hex(_ value: String) throws -> String { "sha256(\(value))" }
    func hmacSHA256Hex(key: Data, value: String) throws -> String { "hmac(\(value))" }
    func encryptOperationPIN(_ pin: String, token: String) throws -> String { "encrypted" }
    func rsaEncryptPhone(_ phone: String) throws -> String { "encrypted-phone" }
}

final class RequestSigningTests: XCTestCase {
    func testSigningConcatenatesSortedValuesOnlyAndPreservesEmpty() {
        XCTAssertEqual(LeapRequestBuilder.sortedValues(["z": "last", "a": "first", "b": "", "n": nil]), "firstlast")
        XCTAssertEqual(LeapRequestBuilder.sortedValues(["a": "first"], ["a": "override"]), "override")
    }
    func testLegacySignatureRemovesCredentialFromOutgoingParameters() throws {
        let state = Session(deviceID: "device", oldAuth: LegacyAuth(accountID: "account", token: "secret-value"))
        let result = try LeapRequestBuilder(crypto: RecordingShapeCrypto()).oldSignedParameters(
            ["carvin": "vehicle"], session: state, context: SigningContext(timestampMilliseconds: 123, nonce: "456"))
        XCTAssertNil(result["token"]); XCTAssertNil(result["refreshtoken"])
        XCTAssertEqual(result["signStr"], "md5(vehicledevice456123secret-value)")
    }
    func testGatewaySigningUsesExplicitSessionPlatform() throws {
        let state = Session(deviceID: "device", clientPlatform: .ios)
        let headers = try LeapRequestBuilder(crypto: RecordingShapeCrypto()).gatewayHeaders(parameters: ["vin": "vehicle"],
            session: state, context: SigningContext(timestampMilliseconds: 123, nonce: "456"), authenticated: false)
        XCTAssertEqual(headers["deviceType"], "ios")
        XCTAssertEqual(headers["sign"], "sha256(zh-CN1deviceios456leapmotor1231.22.96vehicle)")
        XCTAssertNil(headers["token"])
    }
    func testRouteBoundaryRejectsSpoofedHostsAndUnsafeURLs() throws {
        let bad = ["http://appgateway.leapmotor.com", "https://appgateway.leapmotor.com.attacker.invalid",
                   "https://attacker.invalid", "https://user@appgateway.leapmotor.com", "https://appgateway.leapmotor.com:444",
                   "https://appgateway.leapmotor.com/path", "https://appgateway.leapmotor.com?token=x",
                   "https://appgateway.leapmotor.com#fragment", "https://appgateway.leapmotor.com.",
                   "https://nested.app-gw-china.leapmotor.com", "https://127.0.0.1"]
        for url in bad { XCTAssertThrowsError(try TrustedEndpoints.validateBase(url), url) }
        XCTAssertNoThrow(try TrustedEndpoints.validateBase(TrustedEndpoints.global, regionalOnly: true))
        XCTAssertThrowsError(try TrustedEndpoints.validateBase(TrustedEndpoints.appUser, regionalOnly: true))
    }
    func testQueryAndFormEncodingProtectsReservedCharacters() {
        XCTAssertEqual(LeapRequestBuilder.formEncoded(["a": "+/=& ?中文"]), "a=%2B%2F%3D%26%20%3F%E4%B8%AD%E6%96%87")
    }
    func testRequestRejectsHeaderInjectionAndRelativePathEscape() {
        let builder = LeapRequestBuilder(crypto: RecordingShapeCrypto())
        XCTAssertThrowsError(try builder.request(base: TrustedEndpoints.global, path: "/test", headers: ["token": "a\r\nb"]))
        XCTAssertThrowsError(try builder.request(base: TrustedEndpoints.global, path: "//attacker.invalid"))
        XCTAssertThrowsError(try builder.request(base: TrustedEndpoints.global, path: "/../other"))
    }
}
