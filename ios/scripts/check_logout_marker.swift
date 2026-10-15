import Foundation

/// Uses the production Foundation-only store with an isolated, disposable suite.
/// Does not read the application's preferences, Keychain or any network service.
@main struct LogoutMarkerCheck {
    static func main() {
        let name = "leapauto-lifecycle-smoke-" + UUID().uuidString
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = SessionLifecycleStore(defaults: defaults)
        precondition(!store.logoutRequested)
        store.markLogoutRequested()
        precondition(SessionLifecycleStore(defaults: defaults).logoutRequested)
        store.clearLogoutRequest()
        precondition(!store.logoutRequested)
        print("PASS: non-sensitive logout tombstone lifecycle")
    }
}
