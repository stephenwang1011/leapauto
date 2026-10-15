import Foundation
import Security

/// This-device-only, unlocked-device Keychain storage. No iCloud synchronization,
/// shared access group, plain-text preferences, raw telemetry, or credential logs.
struct KeychainStore {
    private let service = "com.leapauto.ios.session"

    func load<T: Decodable>(_ type: T.Type, account: String) throws -> T? {
        var query = baseQuery(account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data else {
            throw StoreError.unavailable
        }
        do { return try JSONDecoder().decode(type, from: data) }
        catch { throw StoreError.invalidData }
    }

    func save<T: Encodable>(_ value: T, account: String) throws {
        let data = try JSONEncoder().encode(value)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        let status = SecItemUpdate(baseQuery(account) as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var item = baseQuery(account)
            attributes.forEach { item[$0.key] = $0.value }
            guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else { throw StoreError.unavailable }
        } else if status != errSecSuccess {
            throw StoreError.unavailable
        }
    }

    func remove(account: String) throws {
        let status = SecItemDelete(baseQuery(account) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw StoreError.unavailable }
    }

    private func baseQuery(_ account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: account,
         kSecAttrSynchronizable as String: false]
    }

    enum StoreError: LocalizedError {
        case unavailable, invalidData
        var errorDescription: String? {
            switch self {
            case .unavailable: return "无法访问本机钥匙串。请解锁设备后重试。"
            case .invalidData: return "本机保存的会话格式无效，请退出后重新导入。"
            }
        }
    }
}
