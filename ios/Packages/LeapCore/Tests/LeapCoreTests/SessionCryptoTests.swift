import XCTest
@testable import LeapCore

final class SessionCryptoTests: XCTestCase {
    private func importJSON(platform: String? = "android", device: String? = String(repeating: "d", count: 32)) throws -> String {
        var root: [String: JSONValue] = ["data": .object(["appLoginVO": .object([
            "accountId": .string("fixture-account"), "token": .string(String(repeating: "x", count: 32))
        ])])]
        if let platform { root["clientPlatform"] = .string(platform) }
        if let device { root["deviceID"] = .string(device) }
        return String(decoding: try JSONValue.object(root).encodedData(), as: UTF8.self)
    }
    func testLegacyImportRequiresExplicitExistingDeviceAndPlatform() throws {
        let state = try SessionImporter.parseLegacyJSON(importJSON())
        XCTAssertEqual(state.clientPlatform, .android); XCTAssertNotNil(state.oldAuth)
        XCTAssertNil(state.newAuth); XCTAssertFalse(state.isAuthenticated)
        XCTAssertNil(state.oldAuth?.expiresAt, "Import must not restart unknown token lifetime")
        XCTAssertThrowsError(try SessionImporter.parseLegacyJSON(importJSON(platform: nil))) {
            XCTAssertEqual($0 as? SessionImportError, .missingPlatform)
        }
        XCTAssertThrowsError(try SessionImporter.parseLegacyJSON(importJSON(device: nil))) {
            XCTAssertEqual($0 as? SessionImportError, .missingDeviceIdentity)
        }
        XCTAssertThrowsError(try SessionImporter.parseLegacyJSON(importJSON(platform: "pretend")))
    }
    func testImportDoesNotAcceptArbitraryPastedTextOrHugeFiles() throws {
        XCTAssertThrowsError(try SessionImporter.parseLegacyJSON("arbitrary captured request"))
        XCTAssertThrowsError(try SessionImporter.parseLegacyJSON(String(repeating: " ", count: 128 * 1024 + 1))) {
            XCTAssertEqual($0 as? SessionImportError, .tooLarge)
        }
    }
    func testSessionCodableHasNoPINOrTelemetryAndDescriptionRedacts() throws {
        let state = try SessionImporter.parseLegacyJSON(importJSON())
        let data = try JSONEncoder().encode(state)
        XCTAssertEqual(try JSONDecoder().decode(Session.self, from: data), state)
        let object = try JSONValue(data: data)
        for field in ["pin", "operationPIN", "latitude", "longitude", "rawResponse", "smDeviceId"] { XCTAssertNil(object[field]) }
        XCTAssertFalse(String(describing: state).contains(String(repeating: "x", count: 32)))
        XCTAssertFalse(String(reflecting: state.oldAuth!).contains(String(repeating: "x", count: 32)))
    }
    func testSignKeyXORUsesShortestLengthAndRejectsEmpty() throws {
        func b64url(_ data: Data) -> String {
            data.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        }
        let token = "header.payload." + b64url(Data([1, 2, 255, 4]))
        let key = try TokenClaims.deriveSignKey(accessToken: token, r2: Data([4, 5, 6]).base64EncodedString(), r3: Data([7, 8]).base64EncodedString())
        XCTAssertEqual(key, Data([2, 15]))
        XCTAssertThrowsError(try TokenClaims.deriveSignKey(accessToken: "bad", r2: "", r3: ""))
        XCTAssertThrowsError(try TokenClaims.deriveSignKey(accessToken: token, r2: "", r3: ""))
    }
    func testJWTMetadataIsParsedWithoutClaimingAuthentication() throws {
        let payload = try JSONValue.object(["exp": .number(1_800_000_000), "user_name": .string("account,deviceId:fixture_device_123456")]).encodedData()
        let encoded = payload.base64EncodedString().replacingOccurrences(of: "=", with: "").replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
        let token = "header.\(encoded).AQID"
        XCTAssertEqual(TokenClaims.expiry(token), Date(timeIntervalSince1970: 1_800_000_000))
        XCTAssertEqual(TokenClaims.deviceID(token), "fixture_device_123456")
        XCTAssertNil(TokenClaims.expiry("not-a-jwt"))
        XCTAssertThrowsError(try TokenClaims.base64URLDecode("%%%"))
    }
    func testPINAndPhoneValidationFailsBeforeCryptography() {
        let crypto = SystemProtocolCryptography()
        for pin in ["", "123", "12345", "12a4", "１２３４"] {
            XCTAssertThrowsError(try crypto.encryptOperationPIN(pin, token: String(repeating: "t", count: 64))) {
                XCTAssertEqual($0 as? ProtocolCryptoError, .invalidPIN)
            }
        }
        XCTAssertThrowsError(try crypto.encryptOperationPIN("1234", token: "short")) {
            XCTAssertEqual($0 as? ProtocolCryptoError, .invalidToken)
        }
        XCTAssertThrowsError(try crypto.rsaEncryptPhone("invalid")) {
            XCTAssertEqual($0 as? ProtocolCryptoError, .invalidPhone)
        }
    }
    func testAppleHashKnownAnswerVectors() throws {
        #if canImport(CryptoKit)
        let crypto = SystemProtocolCryptography()
        XCTAssertEqual(try crypto.md5Short("abc"), "3cd24fb0d6963f7d")
        XCTAssertEqual(try crypto.sha256Hex("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        XCTAssertEqual(try crypto.hmacSHA256Hex(key: Data("key".utf8), value: "The quick brown fox jumps over the lazy dog"), "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8")
        #else
        throw XCTSkip("Requires Apple's CryptoKit; Linux fixtures do not certify production cryptography.")
        #endif
    }
    func testAppleAESKnownAnswerAndLegacyTokenDoubling() throws {
        #if canImport(CommonCrypto) && canImport(CryptoKit)
        let crypto = SystemProtocolCryptography()
        let half = String(repeating: "0123456789abcdef", count: 2)
        XCTAssertEqual(try crypto.encryptOperationPIN("2468", token: half + half), "oBMLF/0vlmGSFf8cbm3EsA==")
        XCTAssertEqual(try crypto.encryptOperationPIN("2468", token: half), "oBMLF/0vlmGSFf8cbm3EsA==")
        #else
        throw XCTSkip("Requires Apple's CommonCrypto and CryptoKit; no substitute encryption is used.")
        #endif
    }
    func testAppleRSAProducesUnpaddedURLSafeRandomizedCiphertext() throws {
        #if canImport(Security)
        let crypto = SystemProtocolCryptography()
        // Synthetic plaintext, never submitted to a service.
        let first = try crypto.rsaEncryptPhone(String(repeating: "0", count: 11))
        let second = try crypto.rsaEncryptPhone(String(repeating: "0", count: 11))
        XCTAssertEqual(try TokenClaims.base64URLDecode(first).count, 128)
        XCTAssertFalse(first.contains("=")); XCTAssertFalse(first.contains("+")); XCTAssertFalse(first.contains("/"))
        XCTAssertNotEqual(first, second)
        #else
        throw XCTSkip("Requires Apple's Security RSA implementation; this fixture makes no network requests.")
        #endif
    }
    func testUnsupportedProductionCryptoFailsClosed() throws {
        #if !canImport(CryptoKit)
        XCTAssertThrowsError(try SystemProtocolCryptography().sha256Hex("abc")) {
            XCTAssertEqual($0 as? ProtocolCryptoError, .unavailable)
        }
        #endif
    }
}
