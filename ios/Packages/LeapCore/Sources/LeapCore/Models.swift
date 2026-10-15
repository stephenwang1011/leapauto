import Foundation

public struct Vehicle: Codable, Equatable, Identifiable, Sendable {
    public var vin: String
    public var modelName: String?
    public var plateNumber: String?
    public var id: String { vin }
    public var carType: String? { modelName }

    public init(vin: String, modelName: String? = nil, plateNumber: String? = nil) {
        self.vin = vin
        self.modelName = modelName
        self.plateNumber = plateNumber
    }

    public init(json: JSONValue) {
        self.init(
            vin: json["vin"]?.scalarString ?? "",
            modelName: json["carType"]?.stringValue ?? json["modelName"]?.stringValue,
            plateNumber: json["licensePlate"]?.stringValue ?? json["plateNumber"]?.stringValue
        )
    }

    public init(from decoder: Decoder) throws { self.init(json: try JSONValue(from: decoder)) }

    public func encode(to encoder: Encoder) throws {
        var values: [String: JSONValue] = ["vin": .string(vin)]
        if let modelName { values["modelName"] = .string(modelName) }
        if let plateNumber { values["plateNumber"] = .string(plateNumber) }
        try JSONValue.object(values).encode(to: encoder)
    }
}

public enum ChargingState: Int, Codable, Sendable {
    case notCharging = 0
    case charging = 1
    case completed = 2
    case fault = 3
    case scheduled = 4
    case paused = 6

    public var title: String {
        switch self {
        case .notCharging: return "未充电"
        case .charging: return "充电中"
        case .completed: return "充电完成"
        case .fault: return "充电故障"
        case .scheduled: return "预约等待"
        case .paused: return "充电暂停"
        }
    }
}

public enum DrivingState: String, Sendable { case parked, moving, unknown }
public enum CoordinateSystem: String, Sendable { case gcj02, wgs84, unconfirmed }
public enum LocationFreshness: String, Sendable { case fresh, stale, expired, unavailable, invalid }

public struct TirePressures: Equatable, Sendable {
    public let leftFront: Double?
    public let rightFront: Double?
    public let leftRear: Double?
    public let rightRear: Double?

    public init(leftFront: Double? = nil, rightFront: Double? = nil, leftRear: Double? = nil, rightRear: Double? = nil) {
        self.leftFront = leftFront
        self.rightFront = rightFront
        self.leftRear = leftRear
        self.rightRear = rightRear
    }
}

public enum TirePressureState: Int, Sendable { case normal = 0, low = 1, high = 2 }

public struct TirePressureStates: Equatable, Sendable {
    public let leftFront: TirePressureState?
    public let rightFront: TirePressureState?
    public let leftRear: TirePressureState?
    public let rightRear: TirePressureState?

    public init(leftFront: TirePressureState? = nil, rightFront: TirePressureState? = nil, leftRear: TirePressureState? = nil, rightRear: TirePressureState? = nil) {
        self.leftFront = leftFront
        self.rightFront = rightFront
        self.leftRear = leftRear
        self.rightRear = rightRear
    }
}

/// Deliberately not Codable. Coordinates belong only to the current in-memory
/// session, never to account storage, widget snapshots, or debug logs.
public struct VehicleLocation: Equatable, Sendable, CustomStringConvertible {
    public let latitude: Double
    public let longitude: Double
    public let timestamp: Date
    public let receivedAt: Date
    public let coordinateSystem: CoordinateSystem
    public var description: String { "VehicleLocation(coordinates: redacted, datum: \(coordinateSystem.rawValue))" }

    public init?(latitude: Double, longitude: Double, timestamp: Date, receivedAt: Date? = nil, coordinateSystem: CoordinateSystem = .gcj02) {
        guard latitude.isFinite, longitude.isFinite,
              (-90...90).contains(latitude), (-180...180).contains(longitude),
              latitude != 0 || longitude != 0 else { return nil }
        self.latitude = latitude
        self.longitude = longitude
        self.timestamp = timestamp
        self.receivedAt = receivedAt ?? timestamp
        self.coordinateSystem = coordinateSystem
    }

    public func freshness(at now: Date = Date()) -> LocationFreshness {
        let age = now.timeIntervalSince(timestamp)
        guard age.isFinite, age >= 0 else { return .invalid }
        if age <= 5 * 60 { return .fresh }
        if age <= 30 * 60 { return .stale }
        if age <= 60 * 60 { return .expired }
        return .unavailable
    }

    /// Pure authorization gate. This method neither opens a map nor transmits
    /// coordinates. The UI must ask for an explicit user navigation action.
    public func canShare(userInitiated: Bool, at now: Date = Date()) -> Bool {
        userInitiated && coordinateSystem == .gcj02 && freshness(at: now) == .fresh
    }

    public func isObviousJump(from previous: VehicleLocation) -> Bool {
        let elapsed = timestamp.timeIntervalSince(previous.timestamp)
        guard elapsed >= 0, elapsed <= 5 * 60 else { return false }
        let a = previous.latitude * .pi / 180
        let b = latitude * .pi / 180
        let deltaLatitude = b - a
        let deltaLongitude = (longitude - previous.longitude) * .pi / 180
        let h = pow(sin(deltaLatitude / 2), 2) + cos(a) * cos(b) * pow(sin(deltaLongitude / 2), 2)
        let distance = 2 * 6371.0088 * asin(sqrt(min(1, max(0, h))))
        return distance > 500
    }
}
