import Foundation

/// Non-sensitive local tombstone only. Credentials, PINs and telemetry never
/// enter preferences. A failed Keychain deletion must not silently restore a
/// logged-out session after the next launch.
struct SessionLifecycleStore {
    private let defaults: UserDefaults
    init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    private let key = "leapauto.logoutRequested"

    var logoutRequested: Bool { defaults.bool(forKey: key) }
    func markLogoutRequested() { defaults.set(true, forKey: key) }
    func clearLogoutRequest() { defaults.removeObject(forKey: key) }
}
