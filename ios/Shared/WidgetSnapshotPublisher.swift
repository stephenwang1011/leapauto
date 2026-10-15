import Foundation
import LeapCore
import WidgetKit

/// Compile in the main app only. Call only after checking isOfflineTestRun.
/// Synchronous main-actor transactions serialize saves and logout, while the
/// random generation rejects asynchronous results from an older session/vehicle.
@MainActor
final class WidgetSnapshotPublisher {
    private let store = WidgetSnapshotStore()
    private var current: WidgetSnapshot?

    func beginSession(generation: UUID, source: WidgetSnapshot.Source) throws {
        let snapshot = WidgetSnapshot(generation: generation, source: source)
        current = snapshot
        defer { reload() }
        do { try store.save(snapshot) }
        catch {
            // Do not leave the former vehicle visible when replacement fails.
            try? store.clear()
            throw error
        }
    }

    func publish(status: VehicleStatus, generation: UUID) throws {
        guard let current else { throw WidgetSnapshotError.obsoleteGeneration }
        let snapshot = try current.replacing(status: status, generation: generation)
        defer { reload() }
        try store.save(snapshot)
        self.current = snapshot
    }

    func markRefreshFailed(generation: UUID) throws {
        guard let current else { throw WidgetSnapshotError.obsoleteGeneration }
        let snapshot = try current.markingRefreshFailed(generation: generation)
        defer { reload() }
        try store.save(snapshot)
        self.current = snapshot
    }

    func clear() throws {
        current = nil
        defer { reload() }
        try store.clear()
    }

    private func reload() {
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetSnapshotStore.widgetKind)
    }
}
