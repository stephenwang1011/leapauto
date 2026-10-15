import Foundation

// OFFLINE COMPATIBILITY REFERENCE ONLY.
// These handwritten implementations have not received an independent security audit.
// SM4 uses secret-indexed tables and is not constant-time. Swift value semantics do
// not guarantee secure erasure of intermediate copies. Do not enable these types in
// production vehicle control. No networking, Bluetooth, key storage, RNG, or trusted
// SM2/X.509 implementation is supplied here. P-256 must never substitute for SM2.
// Protocol source: Android bluetooth/BleSm2Crypto.kt and BleKeyProtocol.kt.

public enum BLECryptoError: Error, Equatable, Sendable {
    case emptyInput
    case invalidKeyLength
    case invalidIVLength
    case invalidBlockLength
    case invalidCiphertextLength
    case invalidPadding
    case invalidPrivateScalar
    case invalidPointEncoding
    case invalidCertificateEncoding
    case invalidProviderResult
    case sm2BackendUnavailable
    case invalidPasswordCard
    case invalidVIN
    case invalidSharedSecretLength
}

/// Offline SM3 reference, including the empty-message digest.
public enum BLEOfflineSM3 {
    public static func hash(_ input: Data) -> Data {
        var bytes = Array(input)
        let bitCount = UInt64(bytes.count) &* 8
        bytes.append(0x80)
        while bytes.count % 64 != 56 { bytes.append(0) }
        for shift in stride(from: 56, through: 0, by: -8) {
            bytes.append(UInt8(truncatingIfNeeded: bitCount >> shift))
        }
        var state: [UInt32] = [0x7380166f, 0x4914b2b9, 0x172442d7, 0xda8a0600,
                               0xa96f30bc, 0x163138aa, 0xe38dee4d, 0xb0fb0e4e]
        for offset in stride(from: 0, to: bytes.count, by: 64) {
            var words = [UInt32](repeating: 0, count: 68)
            for index in 0..<16 { words[index] = BLECryptoBytes.word(bytes, offset + index * 4) }
            for index in 16..<68 {
                let value = words[index - 16] ^ words[index - 9] ^ rotate(words[index - 3], 15)
                words[index] = value ^ rotate(value, 15) ^ rotate(value, 23) ^
                    rotate(words[index - 13], 7) ^ words[index - 6]
            }
            var a = state[0], b = state[1], c = state[2], d = state[3]
            var e = state[4], f = state[5], g = state[6], h = state[7]
            for index in 0..<64 {
                let a12 = rotate(a, 12)
                let ss1 = rotate(a12 &+ e &+ rotate(index < 16 ? 0x79cc4519 : 0x7a879d8a, index), 7)
                let ss2 = ss1 ^ a12
                let ff = index < 16 ? a ^ b ^ c : (a & b) | (a & c) | (b & c)
                let gg = index < 16 ? e ^ f ^ g : (e & f) | (~e & g)
                let tt1 = ff &+ d &+ ss2 &+ (words[index] ^ words[index + 4])
                let tt2 = gg &+ h &+ ss1 &+ words[index]
                d = c; c = rotate(b, 9); b = a; a = tt1
                h = g; g = rotate(f, 19); f = e
                e = tt2 ^ rotate(tt2, 9) ^ rotate(tt2, 17)
            }
            let compressed = [a, b, c, d, e, f, g, h]
            for index in 0..<8 { state[index] ^= compressed[index] }
        }
        return Data(state.flatMap(BLECryptoBytes.bytes))
    }

    private static func rotate(_ value: UInt32, _ count: Int) -> UInt32 {
        BLECryptoBytes.rotate(value, count)
    }
}

/// Offline SM4-CBC with PKCS#7. Empty inputs are rejected to match upstream BLE.
/// CBC supplies no authenticity. This reference must not become a production oracle.
public enum BLEOfflineSM4 {
    public static func encrypt(_ plaintext: Data, key: Data, iv: Data) throws -> Data {
        try validate(key: key, iv: iv)
        guard !plaintext.isEmpty else { throw BLECryptoError.emptyInput }
        var input = Array(plaintext)
        let padding = 16 - input.count % 16
        input.append(contentsOf: repeatElement(UInt8(padding), count: padding))
        let keys = roundKeys(Array(key))
        var previous = Array(iv)
        var result = [UInt8]()
        result.reserveCapacity(input.count)
        for offset in stride(from: 0, to: input.count, by: 16) {
            let block = (0..<16).map { input[offset + $0] ^ previous[$0] }
            previous = transform(block, keys: keys)
            result.append(contentsOf: previous)
        }
        return Data(result)
    }

    public static func decrypt(_ ciphertext: Data, key: Data, iv: Data) throws -> Data {
        try validate(key: key, iv: iv)
        guard !ciphertext.isEmpty else { throw BLECryptoError.emptyInput }
        guard ciphertext.count % 16 == 0 else { throw BLECryptoError.invalidCiphertextLength }
        let input = Array(ciphertext)
        let keys = Array(roundKeys(Array(key)).reversed())
        var previous = Array(iv)
        var result = [UInt8]()
        result.reserveCapacity(input.count)
        for offset in stride(from: 0, to: input.count, by: 16) {
            let block = Array(input[offset..<(offset + 16)])
            let decrypted = transform(block, keys: keys)
            result.append(contentsOf: (0..<16).map { decrypted[$0] ^ previous[$0] })
            previous = block
        }
        let padding = Int(result[result.count - 1])
        // Examine the whole final block. This is not a constant-time guarantee.
        var invalid = padding == 0 || padding > 16
        for distance in 1...16 {
            if distance <= padding && result[result.count - distance] != UInt8(padding) { invalid = true }
        }
        guard !invalid else { throw BLECryptoError.invalidPadding }
        result.removeLast(padding)
        return Data(result)
    }

    /// Single-block primitive for known-answer tests; not a public encryption mode.
    static func encryptBlock(_ block: Data, key: Data) throws -> Data {
        guard key.count == 16 else { throw BLECryptoError.invalidKeyLength }
        guard block.count == 16 else { throw BLECryptoError.invalidBlockLength }
        return Data(transform(Array(block), keys: roundKeys(Array(key))))
    }

    private static func validate(key: Data, iv: Data) throws {
        guard key.count == 16 else { throw BLECryptoError.invalidKeyLength }
        guard iv.count == 16 else { throw BLECryptoError.invalidIVLength }
    }

    private static func substitute(_ value: UInt32) -> UInt32 {
        UInt32(sbox[Int(value >> 24)]) << 24 |
        UInt32(sbox[Int((value >> 16) & 255)]) << 16 |
        UInt32(sbox[Int((value >> 8) & 255)]) << 8 |
        UInt32(sbox[Int(value & 255)])
    }

    private static func roundKeys(_ key: [UInt8]) -> [UInt32] {
        var words = (0..<4).map { BLECryptoBytes.word(key, $0 * 4) ^ fk[$0] }
        for index in 0..<32 {
            let value = substitute(words[index + 1] ^ words[index + 2] ^ words[index + 3] ^ ck[index])
            words.append(words[index] ^ value ^ BLECryptoBytes.rotate(value, 13) ^ BLECryptoBytes.rotate(value, 23))
        }
        return Array(words[4..<36])
    }

    private static func transform(_ block: [UInt8], keys: [UInt32]) -> [UInt8] {
        var words = (0..<4).map { BLECryptoBytes.word(block, $0 * 4) }
        for index in 0..<32 {
            let value = substitute(words[index + 1] ^ words[index + 2] ^ words[index + 3] ^ keys[index])
            let transformed = value ^ BLECryptoBytes.rotate(value, 2) ^ BLECryptoBytes.rotate(value, 10) ^
                BLECryptoBytes.rotate(value, 18) ^ BLECryptoBytes.rotate(value, 24)
            words.append(words[index] ^ transformed)
        }
        return [words[35], words[34], words[33], words[32]].flatMap(BLECryptoBytes.bytes)
    }

    private static let fk: [UInt32] = [0xa3b1bac6, 0x56aa3350, 0x677d9197, 0xb27022dc]
    private static let ck: [UInt32] = (0..<32).map { round in
        (0..<4).reduce(UInt32(0)) { word, byte in
            word << 8 | UInt32(((round * 4 + byte) * 7) & 255)
        }
    }
    private static let sbox: [UInt8] = [
        0xd6,0x90,0xe9,0xfe,0xcc,0xe1,0x3d,0xb7,0x16,0xb6,0x14,0xc2,0x28,0xfb,0x2c,0x05,
        0x2b,0x67,0x9a,0x76,0x2a,0xbe,0x04,0xc3,0xaa,0x44,0x13,0x26,0x49,0x86,0x06,0x99,
        0x9c,0x42,0x50,0xf4,0x91,0xef,0x98,0x7a,0x33,0x54,0x0b,0x43,0xed,0xcf,0xac,0x62,
        0xe4,0xb3,0x1c,0xa9,0xc9,0x08,0xe8,0x95,0x80,0xdf,0x94,0xfa,0x75,0x8f,0x3f,0xa6,
        0x47,0x07,0xa7,0xfc,0xf3,0x73,0x17,0xba,0x83,0x59,0x3c,0x19,0xe6,0x85,0x4f,0xa8,
        0x68,0x6b,0x81,0xb2,0x71,0x64,0xda,0x8b,0xf8,0xeb,0x0f,0x4b,0x70,0x56,0x9d,0x35,
        0x1e,0x24,0x0e,0x5e,0x63,0x58,0xd1,0xa2,0x25,0x22,0x7c,0x3b,0x01,0x21,0x78,0x87,
        0xd4,0x00,0x46,0x57,0x9f,0xd3,0x27,0x52,0x4c,0x36,0x02,0xe7,0xa0,0xc4,0xc8,0x9e,
        0xea,0xbf,0x8a,0xd2,0x40,0xc7,0x38,0xb5,0xa3,0xf7,0xf2,0xce,0xf9,0x61,0x15,0xa1,
        0xe0,0xae,0x5d,0xa4,0x9b,0x34,0x1a,0x55,0xad,0x93,0x32,0x30,0xf5,0x8c,0xb1,0xe3,
        0x1d,0xf6,0xe2,0x2e,0x82,0x66,0xca,0x60,0xc0,0x29,0x23,0xab,0x0d,0x53,0x4e,0x6f,
        0xd5,0xdb,0x37,0x45,0xde,0xfd,0x8e,0x2f,0x03,0xff,0x6a,0x72,0x6d,0x6c,0x5b,0x51,
        0x8d,0x1b,0xaf,0x92,0xbb,0xdd,0xbc,0x7f,0x11,0xd9,0x5c,0x41,0x1f,0x10,0x5a,0xd8,
        0x0a,0xc1,0x31,0x88,0xa5,0xcd,0x7b,0xbd,0x2d,0x74,0xd0,0x12,0xb8,0xe5,0xb4,0xb0,
        0x89,0x69,0x97,0x4a,0x0c,0x96,0x77,0x7e,0x65,0xb9,0xf1,0x09,0xc5,0x6e,0xc6,0x84,
        0x18,0xf0,0x7d,0xec,0x3a,0xdc,0x4d,0x20,0x79,0xee,0x5f,0x3e,0xd7,0xcb,0x39,0x48
    ]
}

/// Missing native boundary, deliberately explicit rather than silently using P-256.
/// A future audited provider must implement only sm2p256v1. It must fully parse one
/// X.509 DER certificate (no trailing bytes), enforce id-ecPublicKey and the exact
/// SM2 curve OID 1.2.156.10197.1.301, and validate all points on that curve, rejecting
/// infinity. It must normalize compressed/uncompressed input to 65-byte 04|X|Y.
/// Parsing a public-key certificate does not by itself establish CA trust.
public protocol BLESM2Provider {
    func publicPoint(certificateDER: Data) throws -> Data
    func publicPoint(privateScalar: Data) throws -> Data
    func multiply(point: Data, scalar: Data) throws -> Data
    func multiplyScalarsModuloOrder(_ lhs: Data, _ rhs: Data) throws -> Data
}

public struct BLEUnavailableSM2Provider: BLESM2Provider, Sendable {
    public init() {}
    public func publicPoint(certificateDER: Data) throws -> Data { throw BLECryptoError.sm2BackendUnavailable }
    public func publicPoint(privateScalar: Data) throws -> Data { throw BLECryptoError.sm2BackendUnavailable }
    public func multiply(point: Data, scalar: Data) throws -> Data { throw BLECryptoError.sm2BackendUnavailable }
    public func multiplyScalarsModuloOrder(_ lhs: Data, _ rhs: Data) throws -> Data { throw BLECryptoError.sm2BackendUnavailable }
}

public struct BLEOfflineSM2Agreement: Sendable, CustomStringConvertible, CustomDebugStringConvertible, CustomReflectable {
    public let publicKey: Data
    public let sharedSecret: Data
    public var description: String { "BLEOfflineSM2Agreement(redacted, offline-only)" }
    public var debugDescription: String { description }
    public var customMirror: Mirror { Mirror(self, children: EmptyCollection<Mirror.Child>()) }
}

/// Deterministic protocol orchestration, NOT a complete SM2 implementation.
/// Every elliptic-curve operation and X.509 validation stays behind the provider.
public enum BLEOfflineSM2 {
    public static func agree(encodedCertificate: String, privateScalar: Data,
                             provider: any BLESM2Provider = BLEUnavailableSM2Provider()) throws -> BLEOfflineSM2Agreement {
        try validateScalar(privateScalar)
        let der = try certificateDER(encodedCertificate)
        let peer = try provider.publicPoint(certificateDER: der)
        let local = try provider.publicPoint(privateScalar: privateScalar)
        try validatePointEncoding(peer)
        try validatePointEncoding(local)
        // Upstream reuses each participant's point in both agreement roles:
        // localFactor = ((xBar(local) + 1) * d) mod n
        // shared = ((xBar(peer) + 1) * peer) * localFactor
        let localFactor = try provider.multiplyScalarsModuloOrder(xBarPlusOne(local), privateScalar)
        do { try validateScalar(localFactor) } catch { throw BLECryptoError.invalidProviderResult }
        let peerProduct = try provider.multiply(point: peer, scalar: xBarPlusOne(peer))
        try validatePointEncoding(peerProduct)
        let shared = try provider.multiply(point: peerProduct, scalar: localFactor)
        try validatePointEncoding(shared)
        return BLEOfflineSM2Agreement(publicKey: local,
            sharedSecret: try agreementDigest(sharedPoint: shared, localPoint: local, peerPoint: peer))
    }

    /// Point encodings must already have been curve-validated by a real provider.
    /// This offline helper checks structure/range only; it cannot prove curve membership.
    public static func identityDigest(normalizedPoint: Data) throws -> Data {
        try validatePointEncoding(normalizedPoint)
        let material = Data([0, 128]) + Data("1234567812345678".utf8) + curveA + curveB +
            Data(generator.dropFirst()) + Data(normalizedPoint.dropFirst())
        return BLEOfflineSM3.hash(material)
    }

    /// The BLE protocol uses one SM3 KDF block, counter 1, with local identity first.
    public static func agreementDigest(sharedPoint: Data, localPoint: Data, peerPoint: Data) throws -> Data {
        try validatePointEncoding(sharedPoint)
        return BLEOfflineSM3.hash(Data(sharedPoint.dropFirst()) +
            (try identityDigest(normalizedPoint: localPoint)) +
            (try identityDigest(normalizedPoint: peerPoint)) + Data([0, 0, 0, 1]))
    }

    static func xBarPlusOne(_ normalizedPoint: Data) throws -> Data {
        try validatePointEncoding(normalizedPoint)
        let point = Array(normalizedPoint)
        var scalar = [UInt8](repeating: 0, count: 32)
        scalar.replaceSubrange(16..<32, with: point[17..<33])
        scalar[16] |= 128
        for index in stride(from: 31, through: 0, by: -1) {
            scalar[index] &+= 1
            if scalar[index] != 0 { break }
        }
        return Data(scalar)
    }

    static func validateScalar(_ value: Data) throws {
        guard value.count == 32, value.contains(where: { $0 != 0 }),
              value.lexicographicallyPrecedes(order) else { throw BLECryptoError.invalidPrivateScalar }
    }

    static func validatePointEncoding(_ value: Data) throws {
        let bytes = Array(value)
        guard bytes.count == 65, bytes[0] == 4,
              bytes[1..<33].lexicographicallyPrecedes(prime),
              bytes[33..<65].lexicographicallyPrecedes(prime),
              bytes[1..<65].contains(where: { $0 != 0 }) else { throw BLECryptoError.invalidPointEncoding }
    }

    /// Decode the textual container only; actual X.509 and curve checks are the provider's job.
    static func certificateDER(_ encoded: String) throws -> Data {
        guard encoded.utf8.count <= 131_072 else { throw BLECryptoError.invalidCertificateEncoding }
        let begin = "-----BEGIN CERTIFICATE-----", end = "-----END CERTIFICATE-----"
        var text = encoded.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix(begin) {
            guard text.hasSuffix(end) else { throw BLECryptoError.invalidCertificateEncoding }
            text = String(text.dropFirst(begin.count).dropLast(end.count))
        }
        let compact = text.utf8.filter { ![9, 10, 13, 32].contains($0) }
        let body = Array(compact.prefix { $0 != 61 })
        let padding = compact.count - body.count
        guard !body.isEmpty, body.count % 4 != 1, padding <= 2,
              compact.suffix(padding).allSatisfy({ $0 == 61 }),
              body.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || $0 == 43 || $0 == 47 })
        else { throw BLECryptoError.invalidCertificateEncoding }
        let neededPadding = (4 - body.count % 4) % 4
        guard padding == 0 || padding == neededPadding,
              let der = Data(base64Encoded: Data(body + Array(repeating: UInt8(61), count: neededPadding))),
              !der.isEmpty else { throw BLECryptoError.invalidCertificateEncoding }
        return der
    }

    private static let prime = BLECryptoBytes.hex("fffffffeffffffffffffffffffffffffffffffff00000000ffffffffffffffff")
    private static let order = BLECryptoBytes.hex("fffffffeffffffffffffffffffffffff7203df6b21c6052b53bbf40939d54123")
    private static let curveA = BLECryptoBytes.hex("fffffffeffffffffffffffffffffffffffffffff00000000fffffffffffffffc")
    private static let curveB = BLECryptoBytes.hex("28e9fa9e9d9f5e344d5a9e4bcf6509a7f39789f515ab8f92ddbcbd414d940e93")
    private static let generator = BLECryptoBytes.hex("0432c4ae2c1f1981195f9904466a39c9948fe30bbff2660be1715a4589334c74c7bc3736a2f4f6779c59bdcee36b692153d0a9877cc62a474002df32e52139f0a0")
}

public struct BLEOfflineSessionMaterial: Sendable, CustomStringConvertible, CustomDebugStringConvertible, CustomReflectable {
    public let key: Data
    public let iv: Data
    public var description: String { "BLEOfflineSessionMaterial(redacted, offline-only)" }
    public var debugDescription: String { description }
    public var customMirror: Mirror { Mirror(self, children: EmptyCollection<Mirror.Child>()) }
}

public enum BLEOfflineSessionDerivation {
    /// SM2/SM3 branch only. Certificate identity and key type must be checked by
    /// the session owner; this helper does not authorize or authenticate a vehicle.
    /// Key/IV are ASCII hexadecimal slices, NOT decoded hexadecimal bytes.
    public static func sm2(sessionID: UInt32, vin: String, passwordCard: String,
                           sharedSecret: Data) throws -> BLEOfflineSessionMaterial {
        // VIN is ASCII in the vehicle protocol; rejecting Unicode avoids ambiguous reversal.
        guard !vin.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              vin.utf8.allSatisfy({ (33...126).contains($0) && $0 != 59 }) else { throw BLECryptoError.invalidVIN }
        let card = Array(passwordCard.utf8)
        guard card.count >= 80, card.allSatisfy({ $0 <= 127 }) else { throw BLECryptoError.invalidPasswordCard }
        guard sharedSecret.count == 32 else { throw BLECryptoError.invalidSharedSecretLength }
        let identifier = (0..<4).map { UInt8(truncatingIfNeeded: sessionID >> ($0 * 8)) }
        let selector = BLEOfflineSM3.hash(Data(identifier) + Data(vin.utf8.reversed()))
        let offset = Int(selector[selector.startIndex] >> 4) * 5
        let digest = BLEOfflineSM3.hash(Data(card[offset..<(offset + 5)]) + sharedSecret)
        let alphabet = Array("0123456789abcdef".utf8)
        let hex = digest.flatMap { [alphabet[Int($0 >> 4)], alphabet[Int($0 & 15)]] }
        return BLEOfflineSessionMaterial(key: Data(hex[0..<16]), iv: Data(hex[48..<64]))
    }
}

private enum BLECryptoBytes {
    static func rotate(_ value: UInt32, _ count: Int) -> UInt32 {
        let amount = count & 31
        return amount == 0 ? value : value << amount | value >> (32 - amount)
    }
    static func word(_ bytes: [UInt8], _ offset: Int) -> UInt32 {
        UInt32(bytes[offset]) << 24 | UInt32(bytes[offset + 1]) << 16 |
        UInt32(bytes[offset + 2]) << 8 | UInt32(bytes[offset + 3])
    }
    static func bytes(_ word: UInt32) -> [UInt8] {
        [UInt8(truncatingIfNeeded: word >> 24), UInt8(truncatingIfNeeded: word >> 16),
         UInt8(truncatingIfNeeded: word >> 8), UInt8(truncatingIfNeeded: word)]
    }
    /// Only compile-time constants call this helper.
    static func hex(_ value: String) -> Data {
        let bytes = Array(value.utf8)
        func nibble(_ byte: UInt8) -> UInt8 { byte <= 57 ? byte - 48 : byte - 87 }
        return Data(stride(from: 0, to: bytes.count, by: 2).map { nibble(bytes[$0]) << 4 | nibble(bytes[$0 + 1]) })
    }
}
