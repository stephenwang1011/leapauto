import Foundation
#if canImport(CryptoKit)
import CryptoKit
#endif
#if canImport(CommonCrypto)
import CommonCrypto
#endif
#if canImport(Security)
import Security
#endif

/// This interface permits deterministic, offline request-construction fixtures.
/// Production uses only Apple system cryptography; unsupported platforms fail closed.
public protocol ProtocolCryptography: Sendable {
    func md5Short(_ value: String) throws -> String
    func sha256Hex(_ value: String) throws -> String
    func hmacSHA256Hex(key: Data, value: String) throws -> String
    func encryptOperationPIN(_ pin: String, token: String) throws -> String
    func rsaEncryptPhone(_ phone: String) throws -> String
}

public enum ProtocolCryptoError: Error, LocalizedError, Equatable {
    case unavailable, invalidToken, invalidPIN, invalidPhone, invalidKey, encryptionFailed

    public var errorDescription: String? {
        switch self {
        case .unavailable: return "当前平台缺少系统加密组件。请在 iOS 设备上运行。"
        case .invalidToken: return "会话凭据格式无效，请重新导入有效会话。"
        case .invalidPIN: return "操作密码必须为四位数字。"
        case .invalidPhone: return "手机号格式无效。"
        case .invalidKey: return "会话签名密钥无效。"
        case .encryptionFailed: return "系统加密失败，请重试。"
        }
    }
}

public struct SystemProtocolCryptography: ProtocolCryptography {
    public init() {}

    /// Matches upstream Crypto.kt: lowercase hexadecimal, positions 8..<24.
    /// MD5 is required solely for compatibility with the legacy server protocol.
    public func md5Short(_ value: String) throws -> String {
        #if canImport(CryptoKit)
        return String(Self.hex(Insecure.MD5.hash(data: Data(value.utf8))).dropFirst(8).prefix(16))
        #else
        throw ProtocolCryptoError.unavailable
        #endif
    }

    public func sha256Hex(_ value: String) throws -> String {
        #if canImport(CryptoKit)
        return Self.hex(SHA256.hash(data: Data(value.utf8)))
        #else
        throw ProtocolCryptoError.unavailable
        #endif
    }

    public func hmacSHA256Hex(key: Data, value: String) throws -> String {
        guard !key.isEmpty else { throw ProtocolCryptoError.invalidKey }
        #if canImport(CryptoKit)
        return Self.hex(HMAC<SHA256>.authenticationCode(for: Data(value.utf8), using: SymmetricKey(data: key)))
        #else
        throw ProtocolCryptoError.unavailable
        #endif
    }

    public func encryptOperationPIN(_ pin: String, token: String) throws -> String {
        let pin = pin.trimmingCharacters(in: .whitespacesAndNewlines)
        guard pin.utf8.count == 4, pin.utf8.allSatisfy({ (48...57).contains($0) }) else {
            throw ProtocolCryptoError.invalidPIN
        }
        let clean = token.trimmingCharacters(in: .whitespacesAndNewlines)
        guard clean.unicodeScalars.allSatisfy({ $0.isASCII }) else { throw ProtocolCryptoError.invalidToken }
        let effective = (32..<64).contains(clean.count) ? clean + clean : clean
        guard effective.count >= 64 else { throw ProtocolCryptoError.invalidToken }
        #if canImport(CommonCrypto)
        let key = Data(try md5Short(String(effective.prefix(32))).utf8)
        let iv = Data(try md5Short(String(effective.dropFirst(32).prefix(32))).utf8)
        let input = Data(pin.utf8)
        var output = Data(count: input.count + kCCBlockSizeAES128)
        let outputCapacity = output.count
        var written = 0
        let result: CCCryptorStatus = output.withUnsafeMutableBytes { outputBytes in
            input.withUnsafeBytes { inputBytes in
                key.withUnsafeBytes { keyBytes in
                    iv.withUnsafeBytes { ivBytes in
                        CCCrypt(CCOperation(kCCEncrypt), CCAlgorithm(kCCAlgorithmAES), CCOptions(kCCOptionPKCS7Padding),
                                keyBytes.baseAddress, key.count, ivBytes.baseAddress, inputBytes.baseAddress,
                                input.count, outputBytes.baseAddress, outputCapacity, &written)
                    }
                }
            }
        }
        guard result == kCCSuccess else { throw ProtocolCryptoError.encryptionFailed }
        output.count = written
        return output.base64EncodedString()
        #else
        throw ProtocolCryptoError.unavailable
        #endif
    }

    public func rsaEncryptPhone(_ phone: String) throws -> String {
        guard phone.utf8.count == 11, phone.utf8.allSatisfy({ (48...57).contains($0) }) else {
            throw ProtocolCryptoError.invalidPhone
        }
        #if canImport(Security)
        // Public protocol key from upstream Crypto.kt. Security expects the inner PKCS#1 RSA key.
        let spki = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDHUIQKhkwNqJFTZPe98mC1lmpbY9r/+7PEWZg8ebqYXT3sumKRaQ0zcoTx42x0iybmCRXy4CcZrgGAbwKzwqwNw0rFquJ6c7mgQA6k3lZU3p96qBlzK7DSkoFR6mO9pjcd2hlJ8wH+IwI5b8IWWZhwVN/4cM7npG0S0zeRn3soEwIDAQAB"
        guard let der = Data(base64Encoded: spki), der.count == 162 else { throw ProtocolCryptoError.invalidKey }
        let attributes: [CFString: Any] = [kSecAttrKeyType: kSecAttrKeyTypeRSA,
                                         kSecAttrKeyClass: kSecAttrKeyClassPublic,
                                         kSecAttrKeySizeInBits: 1024]
        var error: Unmanaged<CFError>?
        guard let key = SecKeyCreateWithData(Data(der.dropFirst(22)) as CFData, attributes as CFDictionary, &error),
              SecKeyIsAlgorithmSupported(key, .encrypt, .rsaEncryptionPKCS1),
              let encrypted = SecKeyCreateEncryptedData(key, .rsaEncryptionPKCS1, Data(phone.utf8) as CFData, &error)
        else { throw ProtocolCryptoError.encryptionFailed }
        return (encrypted as Data).base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        #else
        throw ProtocolCryptoError.unavailable
        #endif
    }

    private static func hex<S: Sequence>(_ bytes: S) -> String where S.Element == UInt8 {
        bytes.map { String(format: "%02x", $0) }.joined()
    }
}

public enum TokenClaims {
    /// Claims are unverified metadata, never evidence of a valid login or permission.
    public static func payload(_ token: String) -> JSONValue? {
        let parts = token.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3, let decoded = try? base64URLDecode(String(parts[1])) else { return nil }
        return try? JSONValue(data: decoded)
    }

    public static func expiry(_ token: String) -> Date? {
        guard let seconds = payload(token)?["exp"]?.doubleValue, seconds > 0 else { return nil }
        return Date(timeIntervalSince1970: seconds)
    }

    public static func deviceID(_ token: String) -> String? {
        guard let user = payload(token)?["user_name"]?.stringValue,
              let range = user.range(of: "deviceId:[a-zA-Z0-9_-]+", options: .regularExpression) else { return nil }
        return String(user[range].dropFirst("deviceId:".count))
    }

    public static func deriveSignKey(accessToken: String, r2: String, r3: String) throws -> Data {
        let parts = accessToken.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3,
              let second = Data(base64Encoded: r2), let third = Data(base64Encoded: r3) else {
            throw ProtocolCryptoError.invalidKey
        }
        let tail = try base64URLDecode(String(parts[2]))
        let count = min(tail.count, second.count, third.count)
        guard count > 0 else { throw ProtocolCryptoError.invalidKey }
        return Data((0..<count).map { tail[$0] ^ second[$0] ^ third[$0] })
    }

    public static func base64URLDecode(_ value: String) throws -> Data {
        guard !value.isEmpty, value.utf8.allSatisfy({
            (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || [45, 95, 61].contains($0)
        }) else { throw ProtocolCryptoError.invalidToken }
        var normalized = value.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        normalized += String(repeating: "=", count: (4 - normalized.count % 4) % 4)
        guard let result = Data(base64Encoded: normalized), !result.isEmpty else { throw ProtocolCryptoError.invalidToken }
        return result
    }
}
