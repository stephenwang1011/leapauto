import Foundation
import XCTest
@testable import LeapCore

/// Synthetic offline fixtures only. Replay providers do not test EC arithmetic,
/// X.509 validation, real credentials, Bluetooth, or network behavior.
final class BLECryptoTests: XCTestCase {
    func testSM3StandardKnownAnswers() {
        XCTAssertEqual(BLEOfflineSM3.hash(Data("abc".utf8)), hex("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0"))
        XCTAssertEqual(BLEOfflineSM3.hash(Data(String(repeating: "abcd", count: 16).utf8)), hex("debe9ff92275b8a138604889c18e5a4d6fdb70e5387e5765293dcba39c0c5732"))
        XCTAssertEqual(BLEOfflineSM3.hash(Data()), hex("1ab21d8355cfa17f8e61194831e81a8f22bec8c728fefb747ed035eb5082aa2b"))
    }

    func testSM3OpenSSLBoundaryVectors() {
        // Node crypto.createHash("sm3"), bytes[i] = i & 255, generated offline.
        let vectors: [(Int, String)] = [
            (1, "2daef60e7a0b8f5e024c81cd2ab3109f2b4f155cf83adeb2ae5532f74a157fdf"),
            (55, "a79cf9dcee3404abf7f769698201647fd9d3ff61d629d0f58bb4b5579a427db8"),
            (56, "62f7363b15f4de76dd925c493b9d6d00d4ba0ef2a1f334c1d0f13b293aeb40d1"),
            (63, "6165e4cbb15cde01c6226e0015a47f710f8f8e1f2c296700033bb34d9212109c"),
            (64, "93566f236d157aae078d1ddb5cebdbba1520b5142e22a8915564345ba2ae1d63"),
            (65, "c886e6814be748285a10b28ae62ddacd85db830cd2cf3a2bfa2f729c15f63618"),
            (127, "bca3436d828517a6a6893a9e309e06e7b7b29c6e3f78b4814b23efe149962980"),
            (128, "a9e7985473ca09df1510d83b572f72375430756c4a661b00724afeb8b75dd0a5"),
            (129, "2783a0e9b3767a694f90027806e392ae959d919baed7ceca40c7c8077711cb7b")]
        for (count, digest) in vectors { XCTAssertEqual(BLEOfflineSM3.hash(sequence(count)), hex(digest), "Length \(count)") }
    }

    func testSM3NonzeroBasedDataSlices() {
        let bytes = Data([255]) + Data("abc".utf8)
        XCTAssertEqual(BLEOfflineSM3.hash(bytes.dropFirst()), BLEOfflineSM3.hash(Data("abc".utf8)))
    }

    func testSM4StandardSingleBlockVector() throws {
        let bytes = hex("0123456789abcdeffedcba9876543210")
        XCTAssertEqual(try BLEOfflineSM4.encryptBlock(bytes, key: bytes), hex("681edf34d206965e86b3e94f536e4246"))
    }

    func testSM4CBCOpenSSLBoundaryVectorsBothDirections() throws {
        // Node/OpenSSL createCipheriv("sm4-cbc", key, iv), PKCS#7 enabled.
        let key = hex("0123456789abcdeffedcba9876543210"), iv = hex("000102030405060708090a0b0c0d0e0f")
        let vectors: [(Int, String)] = [
            (1, "1959f0f91c5cde355e10f33c074317a7"),
            (15, "4d950697534850c92dca2e02785d5ef4"),
            (16, "2677f46b09c122cc975533105bd4a22a3b880e6867772522ae55d2f0ae7478ae"),
            (17, "2677f46b09c122cc975533105bd4a22afa0d4b95a5e96da824f52031975c7efc"),
            (31, "2677f46b09c122cc975533105bd4a22a1bb67ca6b6a7a8b99bec2690a9f3366d"),
            (32, "2677f46b09c122cc975533105bd4a22ad9ee98830e69745c9827f934a19621f80b38553051d4a47aec8e4315d21f0c07"),
            (33, "2677f46b09c122cc975533105bd4a22ad9ee98830e69745c9827f934a19621f8db9222b553b1f80c13b9d8005f6f5a4d"),
            (64, "2677f46b09c122cc975533105bd4a22ad9ee98830e69745c9827f934a19621f8db45a48645909eefda6bae89a72e659ba6394a4e05bd7cfe514852a2ab9a2d808353584072d9dd785989717ba40cfed1")]
        for (count, cipher) in vectors {
            XCTAssertEqual(try BLEOfflineSM4.encrypt(sequence(count), key: key, iv: iv), hex(cipher), "Length \(count)")
            XCTAssertEqual(try BLEOfflineSM4.decrypt(hex(cipher), key: key, iv: iv), sequence(count), "Length \(count)")
        }
    }

    func testSM4EveryPaddingWidthAndFullBlock() throws {
        for count in 1...65 {
            let plain = sequence(count), cipher = try BLEOfflineSM4.encrypt(sequence(count), key: Fixture.key, iv: Fixture.iv)
            XCTAssertEqual(cipher.count, (count / 16 + 1) * 16)
            XCTAssertEqual(try BLEOfflineSM4.decrypt(cipher, key: Fixture.key, iv: Fixture.iv), plain)
        }
    }

    func testSM4RejectsInvalidInputLengths() {
        for count in [0, 1, 15, 17, 31, 32] {
            assertError(.invalidKeyLength) { try BLEOfflineSM4.encrypt(Data([1]), key: sequence(count), iv: Fixture.iv) }
            assertError(.invalidKeyLength) { try BLEOfflineSM4.decrypt(sequence(16), key: sequence(count), iv: Fixture.iv) }
            assertError(.invalidIVLength) { try BLEOfflineSM4.encrypt(Data([1]), key: Fixture.key, iv: sequence(count)) }
            assertError(.invalidIVLength) { try BLEOfflineSM4.decrypt(sequence(16), key: Fixture.key, iv: sequence(count)) }
        }
        assertError(.emptyInput) { try BLEOfflineSM4.encrypt(Data(), key: Fixture.key, iv: Fixture.iv) }
        assertError(.emptyInput) { try BLEOfflineSM4.decrypt(Data(), key: Fixture.key, iv: Fixture.iv) }
        for count in [1, 15, 17, 31] {
            assertError(.invalidCiphertextLength) { try BLEOfflineSM4.decrypt(sequence(count), key: Fixture.key, iv: Fixture.iv) }
            assertError(.invalidBlockLength) { try BLEOfflineSM4.encryptBlock(sequence(count), key: Fixture.key) }
        }
    }

    func testSM4RejectsZeroOversizedAndInconsistentPadding() throws {
        let cipher = try BLEOfflineSM4.encrypt(Data([1]), key: Fixture.key, iv: Fixture.iv)
        // Fifteen 0x0f padding bytes. IV changes deliberately alter decrypted
        // padding without relying on random ciphertext corruption.
        let changes: [(Int, UInt8)] = [(15, 15), (15, 15 ^ 17), (14, 1), (1, 1)]
        for (index, xor) in changes {
            var iv = Fixture.iv; iv[index] ^= xor
            assertError(.invalidPadding) { try BLEOfflineSM4.decrypt(cipher, key: Fixture.key, iv: iv) }
        }
        assertError(.invalidPadding) { try BLEOfflineSM4.decrypt(Data(repeating: 0, count: 16), key: Fixture.key, iv: Fixture.iv) }
    }

    func testSM4SlicedDataAndUnchangedInputs() throws {
        let key = Data([255]) + Fixture.key, iv = Data([255]) + Fixture.iv, plain = Data([255]) + sequence(33)
        let cipher = try BLEOfflineSM4.encrypt(plain.dropFirst(), key: key.dropFirst(), iv: iv.dropFirst())
        XCTAssertEqual(try BLEOfflineSM4.decrypt((Data([255]) + cipher).dropFirst(), key: key.dropFirst(), iv: iv.dropFirst()), sequence(33))
        XCTAssertEqual(plain, Data([255]) + sequence(33)); XCTAssertEqual(key, Data([255]) + Fixture.key)
        XCTAssertEqual(iv, Data([255]) + Fixture.iv)
    }

    func testSM4UpstreamSyntheticResponseVectors() throws {
        let pairs = [("0;locked;", "Zjj+LKhmbzHq5pKtEMd63g=="),
                     ("2;3;00;", "WNDF4e96dL6xZUNig+cA1Q=="), ("0;;", "rnkChxc8EX5dwtCFWgcNRg=="),
                     ("1;1234abcd;", "R5U3ZYgLia9lGudG+Wz0pw==")]
        for (text, encoded) in pairs {
            let cipher = try XCTUnwrap(Data(base64Encoded: encoded))
            XCTAssertEqual(try BLEOfflineSM4.encrypt(Data(text.utf8), key: Fixture.key, iv: Fixture.iv), cipher)
            XCTAssertEqual(try BLEOfflineSM4.decrypt(cipher, key: Fixture.key, iv: Fixture.iv), Data(text.utf8))
        }
    }

    func testSM4UpstreamLongAuthenticationCiphertextVectors() throws {
        // Compare only encrypted payloads; frame parsing belongs to BLEProtocol.
        // Constants are copied verbatim from upstream BleSm2ProtocolTest.kt.
        let frames: [(Int, String)] = [
            (8, "qq7zABI0VngEMsSuLB8ZgRlfmQRGajnJlI/jC7/yZgvhcVpFiTNMdMe8Nzai9PZ3nFm9zuNraSFT0KmHfMYqR0AC3zLlITnwoHk4SVRiLzFHUUFNVzhyOHh1MWYxWDNpeStOUWxNZ2Z4M2loMjhTRlZwTkZWT0tEZnYxbVFlRjd4ajJhTWg3MDkzVDRMRXZmbDEzcGgzek14SEFEQ1d0TDVTOXQyNHV6eWtOb0ZRNEd3dUluVlRid0hKdHpId2t5b0Y0cFBnRjhTNzhzVFNIVXpmaW5DS2JNc0FMa2l3aUJLNmllelNUaGJDUTZnZ2IxK0EzOD0BCQ=="),
            (9, "qq7zABI0VngEMsSuLB8ZgRlfmQRGajnJlI/jC7/yZgvhcVpFiTNMdMe8Nzai9PZ3nFm9zuNraSFT0KmHfMYqR0AC3zLlITnwoFVzVEJGcTJZRkxla3JyT1I4K2gwNXI4V2ozTXRhdjFlWkl5c2RXY3FrN3Z1bzNWeUVHMWhhWGF2YWt4bGdZTytmbklndC9nWVZRTnZiMkR2Q0t2U2lRWERnVDEyNkI5V0YwVzNpUUE1L1N5bjZCeCtDeG44L2pGUndjNTdudFRCdWU5dE1pNDhTalN6YXpXNUVMSkc5YXpzRzdrOHhGQUtkM2dDV0x5aVV1RT0BCQ==" )]
        for (minor, encodedFrame) in frames {
            let settings = minor >= 9 ? "56;2.00;08;16;1;0;0;0;" : "56;2.00;08;16;0;0;"
            let text = Data("1800000000;v1;test-account;test-device;TESTVIN0000000001;1700000000;1900000000;\(settings)".utf8)
            let length = Data((0..<4).map { UInt8(truncatingIfNeeded: text.count >> ($0 * 8)) })
            let signature = Data((1...16).map(UInt8.init))
            let plaintext = length + text + signature
            let frame = try XCTUnwrap(Data(base64Encoded: encodedFrame))
            let payload = Data(frame.dropFirst(4 + 4 + 65).dropLast(2))
            let expected = try XCTUnwrap(Data(base64Encoded: payload))
            XCTAssertEqual(expected.count, 128)
            XCTAssertEqual(try BLEOfflineSM4.encrypt(plaintext, key: Fixture.key, iv: Fixture.iv), expected)
            XCTAssertEqual(try BLEOfflineSM4.decrypt(expected, key: Fixture.key, iv: Fixture.iv), plaintext)
        }
    }

    func testSM2IdentityDigestsFromUpstreamIndependentFixture() throws {
        XCTAssertEqual(try BLEOfflineSM2.identityDigest(normalizedPoint: Fixture.local), hex("5b32bfe35482899b195d72c09d33ccdb465b2ded883240ff91f120a68bc91de8"))
        XCTAssertEqual(try BLEOfflineSM2.identityDigest(normalizedPoint: Fixture.peer), hex("60371fc8ee7c0ce0aa65d8837b874cd744602ad5ac009830fa40ba371984526a"))
    }

    func testSM2KDFMatchesUpstreamAndBindsIdentityOrder() throws {
        XCTAssertEqual(try BLEOfflineSM2.agreementDigest(sharedPoint: Fixture.shared, localPoint: Fixture.local, peerPoint: Fixture.peer), Fixture.secret)
        XCTAssertNotEqual(try BLEOfflineSM2.agreementDigest(sharedPoint: Fixture.shared, localPoint: Fixture.peer, peerPoint: Fixture.local), Fixture.secret)
    }

    func testSM2XBarPlusOneAndCarry() throws {
        XCTAssertEqual(try BLEOfflineSM2.xBarPlusOne(Fixture.local), Fixture.localFactor)
        XCTAssertEqual(try BLEOfflineSM2.xBarPlusOne(Fixture.peer), Fixture.peerFactor)
        // Structural arithmetic fixture only, not asserted to be on the curve.
        var point = Data(repeating: 0, count: 65); point[0] = 4; point[64] = 1
        point.replaceSubrange(17..<33, with: repeatElement(UInt8(255), count: 16))
        var expected = Data(repeating: 0, count: 32); expected[15] = 1
        XCTAssertEqual(try BLEOfflineSM2.xBarPlusOne(point), expected)
    }

    func testSM2OrchestrationReplaysProviderFixture() throws {
        let provider = FixtureProvider()
        let result = try BLEOfflineSM2.agree(encodedCertificate: Fixture.certificate, privateScalar: Fixture.scalar, provider: provider)
        XCTAssertEqual(result.publicKey, Fixture.local); XCTAssertEqual(result.sharedSecret, Fixture.secret)
        XCTAssertEqual(provider.calls, ["certificate", "public", "scalar", "peer-product", "shared"])
    }

    func testSM2DefaultProviderFailsClosed() {
        assertError(.sm2BackendUnavailable) { try BLEOfflineSM2.agree(encodedCertificate: Fixture.certificate, privateScalar: Fixture.scalar) }
        let provider = BLEUnavailableSM2Provider()
        assertError(.sm2BackendUnavailable) { try provider.publicPoint(certificateDER: Data()) }
        assertError(.sm2BackendUnavailable) { try provider.publicPoint(privateScalar: Fixture.scalar) }
        assertError(.sm2BackendUnavailable) { try provider.multiply(point: Fixture.local, scalar: Fixture.scalar) }
        assertError(.sm2BackendUnavailable) { try provider.multiplyScalarsModuloOrder(Fixture.scalar, Fixture.scalar) }
    }

    func testSM2InvalidScalarsFailBeforeProviderCalls() {
        let n = hex("fffffffeffffffffffffffffffffffff7203df6b21c6052b53bbf40939d54123")
        for scalar in [Data(), Data([1]), Data(repeating: 0, count: 32), Data(repeating: 255, count: 32), n, Data(repeating: 1, count: 33)] {
            let provider = FixtureProvider()
            assertError(.invalidPrivateScalar) { try BLEOfflineSM2.agree(encodedCertificate: Fixture.certificate, privateScalar: scalar, provider: provider) }
            XCTAssertTrue(provider.calls.isEmpty)
        }
        var lastValid = n; lastValid[31] -= 1
        XCTAssertNoThrow(try BLEOfflineSM2.validateScalar(lastValid))
    }

    func testSM2RejectsMalformedProviderResults() {
        for point in [Data(), Data([0]), Data([4]) + Data(repeating: 0, count: 64), Data([4]) + Data(repeating: 255, count: 64), Data([2]) + Data(Fixture.local.dropFirst().prefix(32))] {
            let provider = FixtureProvider(); provider.peerOverride = point
            assertError(.invalidPointEncoding) { try BLEOfflineSM2.agree(encodedCertificate: Fixture.certificate, privateScalar: Fixture.scalar, provider: provider) }
        }
        for scalar in [Data(), Data(repeating: 0, count: 32), Data(repeating: 255, count: 32)] {
            let provider = FixtureProvider(); provider.scalarOverride = scalar
            assertError(.invalidProviderResult) { try BLEOfflineSM2.agree(encodedCertificate: Fixture.certificate, privateScalar: Fixture.scalar, provider: provider) }
        }
    }

    func testSM2ProviderCertificateFailureCannotDowngrade() {
        let provider = FixtureProvider(); provider.rejectCertificate = true
        assertError(.invalidCertificateEncoding) { try BLEOfflineSM2.agree(encodedCertificate: Fixture.certificate, privateScalar: Fixture.scalar, provider: provider) }
        XCTAssertEqual(provider.calls, ["certificate"])
    }

    func testSM2CertificateContainerVariants() throws {
        let expected = try XCTUnwrap(Data(base64Encoded: Fixture.certificate))
        let multiline = Fixture.certificate.enumerated().map { ($0.offset % 48 == 0 ? "\r\n" : "") + String($0.element) }.joined()
        for encoded in [Fixture.certificate, multiline, " \t\n" + Fixture.certificate + "\r\n", Fixture.certificate.replacingOccurrences(of: "=", with: ""), "-----BEGIN CERTIFICATE-----\r\n" + multiline + "\r\n-----END CERTIFICATE-----"] {
            XCTAssertEqual(try BLEOfflineSM2.certificateDER(encoded), expected)
            XCTAssertEqual(try BLEOfflineSM2.agree(encodedCertificate: encoded, privateScalar: Fixture.scalar, provider: FixtureProvider()).sharedSecret, Fixture.secret)
        }
    }

    func testSM2RejectsMalformedCertificateTextBeforeProvider() {
        for value in ["", " ", "not%base64", "A", "AA=", "AAAA=", "AA===", "AA==AA==", "-_==", "éé", "-----BEGIN CERTIFICATE-----\n" + Fixture.certificate, "-----BEGIN PUBLIC KEY-----\n" + Fixture.certificate + "\n-----END PUBLIC KEY-----", Fixture.certificate + "%", String(repeating: "A", count: 131_073)] {
            let provider = FixtureProvider()
            assertError(.invalidCertificateEncoding) { try BLEOfflineSM2.agree(encodedCertificate: value, privateScalar: Fixture.scalar, provider: provider) }
            XCTAssertTrue(provider.calls.isEmpty)
        }
    }

    func testSM2PointHelperEncodingAndRangeChecks() {
        let prime = hex("fffffffeffffffffffffffffffffffffffffffff00000000ffffffffffffffff")
        let badPrefix = Data([6]) + Data(Fixture.local.dropFirst())
        let badX = Data([4]) + prime + Data(Fixture.local.suffix(32))
        let badY = Data([4]) + Data(Fixture.local.dropFirst().prefix(32)) + prime
        let invalid: [Data] = [Data(), Data([0]), badPrefix, badX, badY]
        for point in invalid {
            assertError(.invalidPointEncoding) { try BLEOfflineSM2.identityDigest(normalizedPoint: point) }
            assertError(.invalidPointEncoding) { try BLEOfflineSM2.agreementDigest(sharedPoint: point, localPoint: Fixture.local, peerPoint: Fixture.peer) }
        }
    }

    func testSM2SessionDerivesASCIIKeyAndIV() throws {
        let result = try BLEOfflineSessionDerivation.sm2(sessionID: 0x78563412, vin: Fixture.vin, passwordCard: Fixture.card, sharedSecret: Fixture.secret)
        XCTAssertEqual(result.key, Fixture.key); XCTAssertEqual(result.iv, Fixture.iv)
        XCTAssertEqual(result.key.count, 16); XCTAssertEqual(result.iv.count, 16)
        XCTAssertNotEqual(result.key, hex("6fa340d39f4141fe"))
    }

    func testSM2SessionUnsignedIdentifierBoundaryVectors() throws {
        let vectors: [(UInt32, String, String)] = [(0, "ced816f6a1f95ca4", "37097246ba6cb81b"), (UInt32.max, "7cddfa6410f75bcc", "85b1efaff33aa306")]
        for (id, key, iv) in vectors {
            let result = try BLEOfflineSessionDerivation.sm2(sessionID: id, vin: Fixture.vin, passwordCard: Fixture.card, sharedSecret: Fixture.secret)
            XCTAssertEqual(result.key, Data(key.utf8)); XCTAssertEqual(result.iv, Data(iv.utf8))
        }
    }

    func testSM2SessionRejectsMalformedInputs() {
        for vin in ["", " ", "  \n", "非ASCII", "TEST;VIN", "TEST VIN"] {
            assertError(.invalidVIN) { try BLEOfflineSessionDerivation.sm2(sessionID: 0, vin: vin, passwordCard: Fixture.card, sharedSecret: Fixture.secret) }
        }
        for card in ["", String(repeating: "x", count: 79), String(repeating: "é", count: 80)] {
            assertError(.invalidPasswordCard) { try BLEOfflineSessionDerivation.sm2(sessionID: 0, vin: Fixture.vin, passwordCard: card, sharedSecret: Fixture.secret) }
        }
        for count in [0, 1, 31, 33, 64] {
            assertError(.invalidSharedSecretLength) { try BLEOfflineSessionDerivation.sm2(sessionID: 0, vin: Fixture.vin, passwordCard: Fixture.card, sharedSecret: sequence(count)) }
        }
    }

    func testSM2SessionBindsDerivationInputs() throws {
        func derive(id: UInt32 = 0x78563412, vin: String = Fixture.vin, card: String = Fixture.card, secret: Data = Fixture.secret) throws -> Data {
            try BLEOfflineSessionDerivation.sm2(sessionID: id, vin: vin, passwordCard: card, sharedSecret: secret).key
        }
        XCTAssertNotEqual(try derive(id: 0), Fixture.key); XCTAssertNotEqual(try derive(vin: "OTHER000000000001"), Fixture.key)
        XCTAssertNotEqual(try derive(card: String(repeating: "f", count: 80)), Fixture.key); XCTAssertNotEqual(try derive(secret: sequence(32)), Fixture.key)
    }

    func testSensitiveResultsHaveRedactedDescriptionsAndMirrors() throws {
        let agreement = try BLEOfflineSM2.agree(encodedCertificate: Fixture.certificate, privateScalar: Fixture.scalar, provider: FixtureProvider())
        let session = try BLEOfflineSessionDerivation.sm2(sessionID: 0x78563412, vin: Fixture.vin, passwordCard: Fixture.card, sharedSecret: Fixture.secret)
        XCTAssertEqual(String(describing: session), "BLEOfflineSessionMaterial(redacted, offline-only)")
        XCTAssertEqual(String(reflecting: session), session.description); XCTAssertEqual(String(reflecting: agreement), agreement.description)
        XCTAssertTrue(Mirror(reflecting: agreement).children.isEmpty); XCTAssertTrue(Mirror(reflecting: session).children.isEmpty)
    }

    private func assertError<T>(_ expected: BLECryptoError, file: StaticString = #filePath, line: UInt = #line, _ body: () throws -> T) {
        XCTAssertThrowsError(try body(), file: file, line: line) { XCTAssertEqual($0 as? BLECryptoError, expected, file: file, line: line) }
    }
}

/// Test-only strict replay. No EC arithmetic or arbitrary certificate acceptance.
private final class FixtureProvider: BLESM2Provider {
    var calls: [String] = []
    var peerOverride: Data?
    var scalarOverride: Data?
    var rejectCertificate = false
    func publicPoint(certificateDER: Data) throws -> Data {
        calls.append("certificate")
        guard !rejectCertificate, certificateDER == Data(base64Encoded: Fixture.certificate) else { throw BLECryptoError.invalidCertificateEncoding }
        return peerOverride ?? Fixture.peer
    }
    func publicPoint(privateScalar: Data) throws -> Data {
        calls.append("public")
        guard privateScalar == Fixture.scalar else { throw BLECryptoError.invalidPrivateScalar }
        return Fixture.local
    }
    func multiplyScalarsModuloOrder(_ lhs: Data, _ rhs: Data) throws -> Data {
        calls.append("scalar")
        guard lhs == Fixture.localFactor, rhs == Fixture.scalar else { throw BLECryptoError.invalidProviderResult }
        return scalarOverride ?? Fixture.localFactor
    }
    func multiply(point: Data, scalar: Data) throws -> Data {
        if point == Fixture.peer, scalar == Fixture.peerFactor { calls.append("peer-product"); return Fixture.peerProduct }
        if point == Fixture.peerProduct, scalar == Fixture.localFactor { calls.append("shared"); return Fixture.shared }
        throw BLECryptoError.invalidProviderResult
    }
}

private enum Fixture {
    // Source: app/src/test/resources/bluetooth/generate-sm2-vectors.cjs,
    // independently generated with Node/OpenSSL. Scalars 1 and 2 are PUBLIC test values.
    static let local = hex("0432c4ae2c1f1981195f9904466a39c9948fe30bbff2660be1715a4589334c74c7bc3736a2f4f6779c59bdcee36b692153d0a9877cc62a474002df32e52139f0a0")
    static let peer = hex("0456cefd60d7c87c000d58ef57fa73ba4d9c0dfa08c08a7331495c2e1da3f2bd5231b7e7e6cc8189f668535ce0f8eaf1bd6de84c182f6c8e716f780d3a970a23c3")
    static let peerProduct = hex("04fbb85d6b85a3acc548169d33bb35d0ca46c3ea89dbeb1bbfd2c9d635d1f7c16569f24f6fac6e72d91d383f1d500f8658a8e8bee17e008b1cdc84ade6470f8d96")
    static let shared = hex("0484bc6a2aa48bb01a485d83f235d53780e1dff6f8d24cef22601959b93c48555372314b7108a7dc87d3a9cf66f946aa744cab7c3c3928abe9024ce6aa5a27b08b")
    static let scalar = Data(repeating: 0, count: 31) + Data([1])
    static let localFactor = hex("000000000000000000000000000000008fe30bbff2660be1715a4589334c74c8")
    static let peerFactor = hex("000000000000000000000000000000009c0dfa08c08a7331495c2e1da3f2bd53")
    static let secret = hex("5f86e6b17708f60dfb5d8e3f3ce2b35d3d29744d51c46fc14cdb7bb95fdaf48b")
    static let key = Data("6fa340d39f4141fe".utf8), iv = Data("437410132db0dd25".utf8)
    static let vin = "TESTVIN0000000001", card = String(repeating: "0123456789abcdef", count: 5)
    // Synthetic ASN.1 matching upstream tests; placeholder signature, NO CA trust claim.
    static let certificate = "MIHcMIHJoAMCAQICAQEwCgYIKoEcz1UBg3UwGzEZMBcGA1UEAwwQQkxFIE9mZmxpbmUgVGVzdDAeFw0yMzExMTQyMjEzMjBaFw0zMDAzMTcxNzQ2NDBaMBsxGTAXBgNVBAMMEEJMRSBPZmZsaW5lIFRlc3QwWTATBgcqhkjOPQIBBggqgRzPVQGCLQNCAARWzv1g18h8AA1Y71f6c7pNnA36CMCKczFJXC4do/K9UjG35+bMgYn2aFNc4Pjq8b1t6EwYL2yOcW94DTqXCiPDMAoGCCqBHM9VAYN1AwIAAQ=="
}

private func hex(_ value: String) -> Data {
    var bytes = [UInt8](), index = value.startIndex
    while index < value.endIndex {
        let next = value.index(index, offsetBy: 2)
        bytes.append(UInt8(value[index..<next], radix: 16)!); index = next
    }
    return Data(bytes)
}
private func sequence(_ count: Int) -> Data { Data((0..<count).map { UInt8(truncatingIfNeeded: $0) }) }
