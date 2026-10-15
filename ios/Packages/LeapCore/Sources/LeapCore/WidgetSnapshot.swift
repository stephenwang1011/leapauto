import Foundation

/// The entire widget persistence allowlist. Never add account IDs, vehicle IDs,
/// credentials, operation PINs, location, or raw telemetry to this value.
public struct WidgetSnapshot: Codable, Equatable, Sendable {
    public enum Source: String, Codable, Sendable { case vehicle, demo }

    public let schemaVersion: Int
    public let generation: UUID?
    public let source: Source?
    public let batterySOC: Double?
    public let electricRangeKM: Double?
    public let chargingState: ChargingState?
    public let sourceTimestamp: Date?
    public let savedAt: Date
    public let refreshFailed: Bool

    public init(generation: UUID, source: Source, status: VehicleStatus? = nil, at date: Date = Date()) {
        self.init(generation: generation, source: source,
                  batterySOC: status?.batterySOC, electricRangeKM: status?.electricRange,
                  chargingState: status?.chargingState, sourceTimestamp: status?.sourceTimestamp,
                  savedAt: date, refreshFailed: false)
    }

    private init(generation: UUID?, source: Source?, batterySOC: Double?, electricRangeKM: Double?,
                 chargingState: ChargingState?, sourceTimestamp: Date?, savedAt: Date, refreshFailed: Bool) {
        schemaVersion = 1
        self.generation = generation
        self.source = source
        self.batterySOC = batterySOC
        self.electricRangeKM = electricRangeKM
        self.chargingState = chargingState
        self.sourceTimestamp = sourceTimestamp
        self.savedAt = savedAt
        self.refreshFailed = refreshFailed
    }

    /// A persisted logout tombstone contains no old generation or telemetry.
    public static func unconfigured(at date: Date = Date()) -> WidgetSnapshot {
        WidgetSnapshot(generation: nil, source: nil, batterySOC: nil, electricRangeKM: nil,
                       chargingState: nil, sourceTimestamp: nil, savedAt: date, refreshFailed: false)
    }

    /// An obsolete refresh may not re-populate a widget after logout/vehicle change.
    public func replacing(status: VehicleStatus, generation expected: UUID, at date: Date = Date()) throws -> WidgetSnapshot {
        guard generation == expected, let source else { throw WidgetSnapshotError.obsoleteGeneration }
        return WidgetSnapshot(generation: expected, source: source, status: status, at: date)
    }

    /// Keep the original source/save times. A failed refresh never makes data newer.
    public func markingRefreshFailed(generation expected: UUID) throws -> WidgetSnapshot {
        guard generation == expected, source != nil else { throw WidgetSnapshotError.obsoleteGeneration }
        return WidgetSnapshot(generation: generation, source: source, batterySOC: batterySOC,
                              electricRangeKM: electricRangeKM, chargingState: chargingState,
                              sourceTimestamp: sourceTimestamp, savedAt: savedAt, refreshFailed: true)
    }

    public func validate() throws {
        guard schemaVersion == 1, savedAt.timeIntervalSince1970.isFinite,
              sourceTimestamp?.timeIntervalSince1970.isFinite != false,
              batterySOC.map({ $0.isFinite && (0...100).contains($0) }) ?? true,
              electricRangeKM.map({ $0.isFinite && $0 >= 0 }) ?? true else {
            throw WidgetSnapshotError.invalidData
        }
        guard (generation == nil) == (source == nil) else { throw WidgetSnapshotError.invalidData }
        if generation == nil {
            guard batterySOC == nil, electricRangeKM == nil, chargingState == nil,
                  sourceTimestamp == nil, !refreshFailed else { throw WidgetSnapshotError.invalidData }
        }
    }
}

public enum WidgetSnapshotError: Error, Equatable, Sendable {
    case invalidData, tooLarge, obsoleteGeneration
}

public enum WidgetSnapshotCodec {
    public static let maximumBytes = 16_384

    public static func encode(_ snapshot: WidgetSnapshot) throws -> Data {
        try snapshot.validate()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(snapshot)
        guard data.count <= maximumBytes else { throw WidgetSnapshotError.tooLarge }
        return data
    }

    public static func decode(_ data: Data) throws -> WidgetSnapshot {
        guard data.count <= maximumBytes else { throw WidgetSnapshotError.tooLarge }
        let snapshot: WidgetSnapshot
        do { snapshot = try JSONDecoder().decode(WidgetSnapshot.self, from: data) }
        catch { throw WidgetSnapshotError.invalidData }
        try snapshot.validate()
        return snapshot
    }
}

public enum WidgetSnapshotRead: Equatable, Sendable {
    case missing
    case available(WidgetSnapshot)
    /// Includes corrupt data, missing App Group entitlement, and device protection.
    case unavailable
}

public struct WidgetSnapshotPresentation: Equatable, Sendable {
    public enum State: Equatable, Sendable {
        case unconfigured, waiting, recent, stale, expired, unknownTimestamp, unavailable
    }

    public let state: State
    public let source: WidgetSnapshot.Source?
    /// Nil for expired/unknown/unavailable states, so renderers cannot reuse numbers.
    public let visibleSnapshot: WidgetSnapshot?
    public let refreshFailed: Bool
}

/// Widget-only display policy, not a vehicle-control freshness gate. All values
/// are explicitly cached; nothing in this policy claims current vehicle state.
public enum WidgetSnapshotPolicy {
    public static let recentLifetime: TimeInterval = 5 * 60
    public static let maximumLifetime: TimeInterval = 30 * 60

    public static func presentation(for read: WidgetSnapshotRead, at now: Date = Date()) -> WidgetSnapshotPresentation {
        func result(_ state: WidgetSnapshotPresentation.State, _ snapshot: WidgetSnapshot? = nil,
                    visible: Bool = false) -> WidgetSnapshotPresentation {
            WidgetSnapshotPresentation(state: state, source: snapshot?.source,
                                       visibleSnapshot: visible ? snapshot : nil,
                                       refreshFailed: snapshot?.refreshFailed ?? false)
        }
        switch read {
        case .missing: return result(.unconfigured)
        case .unavailable: return result(.unavailable)
        case .available(let snapshot):
            guard (try? snapshot.validate()) != nil else { return result(.unavailable) }
            guard snapshot.generation != nil else { return result(.unconfigured) }
            guard now.timeIntervalSince1970.isFinite,
                  now >= snapshot.savedAt else { return result(.unknownTimestamp, snapshot) }
            guard let timestamp = snapshot.sourceTimestamp else {
                let hasValues = snapshot.batterySOC != nil || snapshot.electricRangeKM != nil || snapshot.chargingState != nil
                return result(hasValues ? .unknownTimestamp : .waiting, snapshot)
            }
            // Never substitute receivedAt/savedAt for a missing source timestamp.
            let age = now.timeIntervalSince(timestamp)
            guard age.isFinite, age >= 0, timestamp <= snapshot.savedAt else {
                return result(.unknownTimestamp, snapshot)
            }
            guard age < maximumLifetime else { return result(.expired, snapshot) }
            return result(age < recentLifetime ? .recent : .stale, snapshot, visible: true)
        }
    }

    /// Future local timeline entries age out cached numbers even without network
    /// access. WidgetKit decides when these entries and reload requests run.
    public static func transitionDates(for read: WidgetSnapshotRead, after now: Date) -> [Date] {
        guard case .available(let snapshot) = read,
              let timestamp = snapshot.sourceTimestamp,
              [.recent, .stale].contains(presentation(for: read, at: now).state) else { return [] }
        return [timestamp.addingTimeInterval(recentLifetime), timestamp.addingTimeInterval(maximumLifetime)]
            .filter { $0 > now }
    }
}
