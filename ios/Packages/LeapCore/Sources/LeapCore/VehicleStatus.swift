import Foundation

/// A session-only snapshot. Never serialize telemetry or coordinates into the
/// credential store. Every optional field preserves absent/invalid as unknown.
public struct VehicleStatus: Equatable, Sendable {
    public let batterySOC: Double?
    public let fuelSOC: Double?
    public let rangeMode: Int?
    public let electricRange: Double?
    public let fuelRange: Double?
    public let combinedRange: Double?
    public let liveRemainingRange: Double?
    public let chargingState: ChargingState?
    public let chargeRemainingMinutes: Double?
    public let batteryVoltage: Double?
    public let batteryCurrent: Double?
    public let minimumBatteryTemperature: Double?
    public let fastCharging: Bool?
    public let healthyCharging: Bool?
    public let chargeCompleted: Bool?
    public let locked: Bool?
    public let acOn: Bool?
    public let cabinTemperature: Double?
    public let acTargetTemperature: Double?
    public let odometer: Double?
    public let speed: Double?
    public let gear: String?
    public let tirePressures: TirePressures
    public let tirePressureStates: TirePressureStates
    public let location: VehicleLocation?
    public let receivedAt: Date
    public let sourceTimestamp: Date?
    public var updatedAt: Date { sourceTimestamp ?? receivedAt }

    public var chargePowerKW: Double? {
        guard chargingState == .charging, let batteryVoltage, let batteryCurrent else { return nil }
        let power = abs(batteryVoltage * batteryCurrent) / 1000
        return power.isFinite ? power : nil
    }

    /// Gear and speed must agree before controls can treat the vehicle as parked.
    /// Do not infer parking from the ignition/Ready flag or an absent speed.
    public var drivingState: DrivingState {
        if let speed, speed > 0 { return .moving }
        if ["D", "R", "DRIVE", "REVERSE", "D挡", "R挡", "前进", "倒车", "3", "1"].contains(gear ?? "") {
            return .moving
        }
        if gear == "P", speed == 0 { return .parked }
        return .unknown
    }

    public var isDriving: Bool? {
        switch drivingState { case .parked: return false; case .moving: return true; case .unknown: return nil }
    }

    public func isFreshForControl(at now: Date = Date()) -> Bool {
        guard let sourceTimestamp else { return false }
        let age = now.timeIntervalSince(sourceTimestamp)
        return age.isFinite && age >= 0 && age <= 5 * 60
    }

    /// Accepts the full service response, its data object, or the raw signalMap.
    /// Only verified IDs below are consumed. Named telemetry takes precedence.
    public init(raw: JSONValue, receivedAt: Date = Date(), previousLocation: VehicleLocation? = nil) {
        let payload = raw["data"]?.objectValue != nil ? raw["data"]! : raw
        let signals = payload["signalMap"]?.objectValue ?? payload.objectValue ?? [:]
        func value(_ name: String, _ id: String) -> JSONValue? { signals[name] ?? signals[id] }
        func number(_ name: String, _ id: String, suffix: String? = nil, nonnegative: Bool = false) -> Double? {
            var input = value(name, id)
            if let suffix, let string = input?.stringValue {
                var trimmed = string.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.hasSuffix(suffix) { trimmed.removeLast(suffix.count) }
                input = .string(trimmed)
            }
            guard let result = input?.doubleValue, !nonnegative || result >= 0 else { return nil }
            return result
        }
        func percent(_ name: String, _ id: String) -> Double? {
            number(name, id, suffix: "%").flatMap { (0...100).contains($0) ? $0 : nil }
        }
        func range(_ name: String, _ id: String) -> Double? { number(name, id, suffix: "km", nonnegative: true) }

        self.receivedAt = receivedAt
        sourceTimestamp = Self.timestamp(signals["sts"] ?? payload["sts"] ?? raw["sts"])
        batterySOC = percent("preciseSoc", "100003") ?? percent("soc", "1204")
        fuelSOC = percent("fuelSoc", "3235")
        let mode = value("rangeMode", "3262")?.intValue
        rangeMode = mode == 0 || mode == 1 ? mode : nil
        // Unknown mode must not silently switch between standard/dynamic ranges.
        electricRange = mode == 0 ? range("electricRangeStandard", "3257") : mode == 1 ? range("expectedMileage", "3260") : nil
        combinedRange = mode == 0 ? range("combinedRangeStandard", "3258") : mode == 1 ? range("combinedRangeDynamic", "3261") : nil
        let directFuel = mode == 0 ? range("fuelRangeStandard", "3256") : mode == 1 ? range("fuelRangeDynamic", "3259") : nil
        if let directFuel { fuelRange = directFuel }
        else if let combinedRange, let electricRange, combinedRange >= electricRange, (fuelSOC ?? 1) > 0 {
            fuelRange = combinedRange - electricRange
        } else { fuelRange = nil }
        liveRemainingRange = range("liveRemainingRange", "2188")
        chargingState = value("chargeState", "1149")?.intValue.flatMap(ChargingState.init(rawValue:))
        chargeRemainingMinutes = number("chargeRemainTime", "1200", nonnegative: true)
        batteryVoltage = number("batteryVoltage", "1177", suffix: "V", nonnegative: true)
        batteryCurrent = number("batteryCurrent", "1178", suffix: "A")
        minimumBatteryTemperature = number("minBatteryTemp", "1182")
        fastCharging = value("dcInputFastCharge", "1197")?.boolValue
        healthyCharging = value("healthyChargeEnabled", "48")?.boolValue
        // 3736 alone is not a verified charging-completion signal.
        chargeCompleted = chargingState.map { $0 == .completed }
        locked = value("driverDoorLockStatus", "1298")?.boolValue
        acOn = value("acSwitch", "1938")?.boolValue
        cabinTemperature = number("interiorTemp", "1349")
        acTargetTemperature = number("acSetting", "2183")
        odometer = number("totalMileage", "1318", suffix: "km", nonnegative: true)
        speed = number("speed", "1319", nonnegative: true)
        gear = value("gearStatus", "1010")?.scalarString?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        tirePressures = TirePressures(
            leftFront: number("leftFrontTirePressure", "2646", suffix: "bar", nonnegative: true),
            rightFront: number("rightFrontTirePressure", "2653", suffix: "bar", nonnegative: true),
            leftRear: number("leftRearTirePressure", "2660", suffix: "bar", nonnegative: true),
            rightRear: number("rightRearTirePressure", "2667", suffix: "bar", nonnegative: true)
        )
        tirePressureStates = TirePressureStates(
            leftFront: value("leftFrontTirePressureState", "2641")?.intValue.flatMap(TirePressureState.init(rawValue:)),
            rightFront: value("rightFrontTirePressureState", "2648")?.intValue.flatMap(TirePressureState.init(rawValue:)),
            leftRear: value("leftRearTirePressureState", "2662")?.intValue.flatMap(TirePressureState.init(rawValue:)),
            rightRear: value("rightRearTirePressureState", "2655")?.intValue.flatMap(TirePressureState.init(rawValue:))
        )
        // Upstream P2 contract: only IDs 2 and 3; no fallback to 3724/3725.
        if let sourceTimestamp, let latitude = signals["3"]?.doubleValue, let longitude = signals["2"]?.doubleValue,
           let candidate = VehicleLocation(latitude: latitude, longitude: longitude, timestamp: sourceTimestamp, receivedAt: receivedAt),
           candidate.timestamp <= receivedAt,
           previousLocation.map({ !candidate.isObviousJump(from: $0) && candidate.timestamp >= $0.timestamp }) ?? true {
            location = candidate
        } else {
            location = previousLocation
        }
    }

    /// Service time accepts seconds, milliseconds, and the verified text forms.
    /// Text without an offset is interpreted in China Standard Time explicitly.
    public static func timestamp(_ value: JSONValue?) -> Date? {
        if let number = value?.doubleValue {
            if (946_684_800...4_102_444_800).contains(number) { return Date(timeIntervalSince1970: number) }
            if (946_684_800_000...4_102_444_800_000).contains(number) { return Date(timeIntervalSince1970: number / 1000) }
            return nil
        }
        guard let text = value?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { return nil }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = iso.date(from: text) { return date }
        iso.formatOptions = [.withInternetDateTime]
        if let date = iso.date(from: text) { return date }
        for pattern in ["yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss"] {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.calendar = Calendar(identifier: .gregorian)
            formatter.timeZone = TimeZone(secondsFromGMT: 8 * 60 * 60)
            formatter.dateFormat = pattern
            formatter.isLenient = false
            if let date = formatter.date(from: text), formatter.string(from: date) == text { return date }
        }
        return nil
    }
}
