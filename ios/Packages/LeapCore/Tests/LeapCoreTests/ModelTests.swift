import Foundation
import XCTest
@testable import LeapCore

final class ModelTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func status(_ signals: [String: JSONValue], previous: VehicleLocation? = nil) -> VehicleStatus {
        VehicleStatus(raw: .object(["data": .object(["signalMap": .object(signals)])]), receivedAt: now, previousLocation: previous)
    }

    func testJSONRoundTripPreservesTypesAndUnknownFields() throws {
        let raw = Data(#"{"bool":true,"zero":0,"text":"0","nil":null,"array":[1,{"unknown":9}]}"#.utf8)
        let value = try JSONValue(data: raw)
        XCTAssertEqual(value["bool"], .bool(true))
        XCTAssertEqual(value["zero"], .number(0))
        XCTAssertEqual(value["text"], .string("0"))
        XCTAssertEqual(value["nil"], .null)
        XCTAssertEqual(try JSONValue(data: value.encodedData()), value)
        XCTAssertNil(value["absent"])
    }

    func testScalarHelpersRejectUnknownBooleansAndNonfiniteNumbers() {
        XCTAssertEqual(JSONValue.string(" true ").boolValue, true)
        XCTAssertEqual(JSONValue.number(0).boolValue, false)
        XCTAssertNil(JSONValue.number(2).boolValue)
        XCTAssertNil(JSONValue.string("2").boolValue)
        XCTAssertNil(JSONValue.null.boolValue)
        XCTAssertNil(JSONValue.bool(true).doubleValue)
        XCTAssertNil(JSONValue.string("NaN").doubleValue)
        XCTAssertNil(JSONValue.number(.infinity).doubleValue)
        XCTAssertNil(JSONValue.number(2.2).intValue)
        XCTAssertEqual(JSONValue.string("24.0").intValue, 24)
        XCTAssertEqual(JSONValue.number(24).scalarString, "24")
        XCTAssertNil(JSONValue.number(24).stringValue)
    }

    func testVehicleDecodesServiceKeysAndRoundTrips() throws {
        let data = Data(#"{"vin":"TEST-VIN","carType":"C16","licensePlate":"测试车牌"}"#.utf8)
        let vehicle = try JSONDecoder().decode(Vehicle.self, from: data)
        XCTAssertEqual(vehicle.id, "TEST-VIN")
        XCTAssertEqual(vehicle.modelName, "C16")
        XCTAssertEqual(vehicle.plateNumber, "测试车牌")
        XCTAssertEqual(try JSONDecoder().decode(Vehicle.self, from: JSONEncoder().encode(vehicle)), vehicle)
    }

    func testEmptyStatusPreservesUnknownValues() {
        let value = status([:])
        XCTAssertNil(value.batterySOC)
        XCTAssertNil(value.locked)
        XCTAssertNil(value.acOn)
        XCTAssertNil(value.chargingState)
        XCTAssertNil(value.chargeCompleted)
        XCTAssertNil(value.speed)
        XCTAssertNil(value.isDriving)
        XCTAssertEqual(value.drivingState, .unknown)
        XCTAssertNil(value.location)
        XCTAssertFalse(value.isFreshForControl(at: now))
        XCTAssertEqual(value.updatedAt, now)
    }

    func testVerifiedBatteryRangeAndNamedFieldPrecedence() {
        let value = status([
            "1204": .string("84"), "100003": .string("85.2"), "3235": .number(45),
            "3262": .string("0"), "3257": .string("530"), "3260": .number(410),
            "3256": .number(500), "3258": .number(1_030), "soc": .number(82),
            "1298": .number(1), "driverDoorLockStatus": .number(0)
        ])
        XCTAssertEqual(value.batterySOC, 85.2)
        XCTAssertEqual(value.fuelSOC, 45)
        XCTAssertEqual(value.electricRange, 530)
        XCTAssertEqual(value.fuelRange, 500)
        XCTAssertEqual(value.combinedRange, 1_030)
        XCTAssertEqual(value.locked, false)
    }

    func testInvalidPercentagesStayUnknownAndPreciseFallsBackToValidSOC() {
        XCTAssertNil(status(["1204": .number(999)]).batterySOC)
        XCTAssertNil(status(["1204": .number(-1)]).batterySOC)
        XCTAssertNil(status(["3235": .string("unknown")]).fuelSOC)
        XCTAssertEqual(status(["100003": .number(999), "1204": .string("80%")]).batterySOC, 80)
        XCTAssertNil(status(["soc": .null, "1204": .number(80)]).batterySOC)
    }

    func testRangeNeverCrossFallsBackForUnknownOrMissingMode() {
        for mode in [JSONValue.null, .number(2), .string("invalid")] {
            let value = status(["3262": mode, "3257": .number(530), "3260": .number(420)])
            XCTAssertNil(value.electricRange)
        }
        XCTAssertNil(status(["3262": .number(0), "3260": .number(420)]).electricRange)
        XCTAssertEqual(status(["3262": .number(1), "3260": .number(420), "3257": .number(530)]).electricRange, 420)
        XCTAssertEqual(status(["3262": .number(1), "3260": .number(200), "3261": .number(600)]).fuelRange, 400)
        XCTAssertNil(status(["3262": .number(1), "3260": .number(200), "3261": .number(100)]).fuelRange)
    }

    func testChargingPowerAndCompletionUseVerifiedState() {
        let signals: [String: JSONValue] = ["1149": .number(1), "1177": .string("400V"), "1178": .string("-10A"), "3736": .number(1)]
        let value = status(signals)
        XCTAssertEqual(value.chargingState, .charging)
        XCTAssertEqual(value.chargePowerKW, 4)
        XCTAssertEqual(value.batteryCurrent, -10)
        XCTAssertEqual(value.chargeCompleted, false)
        XCTAssertNil(status(["3736": .number(1)]).chargeCompleted)
        XCTAssertEqual(status(["1149": .number(2)]).chargeCompleted, true)
        XCTAssertNil(status(["1149": .number(0), "1177": .number(400), "1178": .number(-10)]).chargePowerKW)
        XCTAssertNil(status(["1149": .number(99)]).chargingState)
    }

    func testTirePositionsAndClimateDoNotBecomeSwapped() {
        let value = status([
            "2646": .number(2.1), "2653": .number(2.2), "2660": .number(2.3), "2667": .number(2.4),
            "2662": .number(1), "2655": .number(2), "1349": .number(22.5), "2183": .number(24),
            "1938": .number(1), "1318": .string("12845.6"), "99999": .number(100)
        ])
        XCTAssertEqual(value.tirePressures.leftFront, 2.1)
        XCTAssertEqual(value.tirePressures.rightFront, 2.2)
        XCTAssertEqual(value.tirePressures.leftRear, 2.3)
        XCTAssertEqual(value.tirePressures.rightRear, 2.4)
        XCTAssertEqual(value.tirePressureStates.leftRear, .low)
        XCTAssertEqual(value.tirePressureStates.rightRear, .high)
        XCTAssertEqual(value.cabinTemperature, 22.5)
        XCTAssertEqual(value.acTargetTemperature, 24)
        XCTAssertEqual(value.acOn, true)
        XCTAssertEqual(value.odometer, 12845.6)
    }

    func testDrivingRequiresPositiveParkedEvidenceAndRejectsConflicts() {
        XCTAssertEqual(status(["1010": .string("P"), "1319": .number(0)]).drivingState, .parked)
        XCTAssertEqual(status(["1010": .string("P"), "1319": .number(1)]).drivingState, .moving)
        XCTAssertEqual(status(["1010": .string("D"), "1319": .number(0)]).drivingState, .moving)
        XCTAssertEqual(status(["1010": .string("R")]).drivingState, .moving)
        XCTAssertEqual(status(["1010": .string("P")]).drivingState, .unknown)
        XCTAssertEqual(status(["1319": .number(0), "1944": .number(0)]).drivingState, .unknown)
        XCTAssertEqual(status(["1010": .string("N"), "1319": .number(0)]).drivingState, .unknown)
    }

    func testTimestampSupportsSecondsMillisecondsAndExplicitChinaTime() {
        XCTAssertEqual(VehicleStatus.timestamp(.number(1_800_000_000)), now)
        XCTAssertEqual(VehicleStatus.timestamp(.string("1800000000000")), now)
        XCTAssertEqual(VehicleStatus.timestamp(.string("2026-01-01 08:00:00")), VehicleStatus.timestamp(.string("2026-01-01T00:00:00Z")))
        XCTAssertNil(VehicleStatus.timestamp(.string("2026-02-30 08:00:00")))
        XCTAssertNil(VehicleStatus.timestamp(.number(42)))
        XCTAssertNil(VehicleStatus.timestamp(.string("unknown")))
    }

    func testRawLocationsUseOnlyVerifiedPairAndKnownSourceTime() {
        let timestamp = JSONValue.number(now.timeIntervalSince1970)
        let valid = status(["2": .number(116.4), "3": .number(39.9), "sts": timestamp])
        XCTAssertEqual(valid.location?.coordinateSystem, .gcj02)
        XCTAssertEqual(valid.location?.latitude, 39.9)
        XCTAssertEqual(valid.location?.longitude, 116.4)
        XCTAssertNil(status(["3724": .number(116.4), "3725": .number(39.9), "sts": timestamp]).location)
        XCTAssertNil(status(["2": .number(116.4), "3": .number(39.9)]).location)
        XCTAssertNil(status(["2": .number(116.4), "3": .number(39.9), "sts": .string("unknown")]).location)
        XCTAssertNil(status(["2": .number(0), "3": .number(0), "sts": timestamp]).location)
    }

    func testLocationFreshnessBoundariesAndSharingGate() throws {
        let location = try XCTUnwrap(VehicleLocation(latitude: 39.9, longitude: 116.4, timestamp: now))
        XCTAssertEqual(location.freshness(at: now.addingTimeInterval(300)), .fresh)
        XCTAssertEqual(location.freshness(at: now.addingTimeInterval(301)), .stale)
        XCTAssertEqual(location.freshness(at: now.addingTimeInterval(1_800)), .stale)
        XCTAssertEqual(location.freshness(at: now.addingTimeInterval(1_801)), .expired)
        XCTAssertEqual(location.freshness(at: now.addingTimeInterval(3_600)), .expired)
        XCTAssertEqual(location.freshness(at: now.addingTimeInterval(3_601)), .unavailable)
        XCTAssertEqual(location.freshness(at: now.addingTimeInterval(-1)), .invalid)
        XCTAssertTrue(location.canShare(userInitiated: true, at: now))
        XCTAssertFalse(location.canShare(userInitiated: false, at: now))
        XCTAssertFalse(location.canShare(userInitiated: true, at: now.addingTimeInterval(301)))
        let unconfirmed = try XCTUnwrap(VehicleLocation(latitude: 39.9, longitude: 116.4, timestamp: now, coordinateSystem: .unconfirmed))
        XCTAssertFalse(unconfirmed.canShare(userInitiated: true, at: now))
        XCTAssertFalse(location.description.contains("39.9"))
    }

    func testRejectedLocationRetainsOldTimestampWithoutRejuvenatingIt() throws {
        let previous = try XCTUnwrap(VehicleLocation(latitude: 39.9, longitude: 116.4, timestamp: now.addingTimeInterval(-60)))
        let jump = status(["2": .number(121.5), "3": .number(31.2), "sts": .number(now.timeIntervalSince1970)], previous: previous)
        XCTAssertEqual(jump.location, previous)
        XCTAssertEqual(status([:], previous: previous).location, previous)
        let future = status(["2": .number(116.4), "3": .number(39.9), "sts": .number(now.timeIntervalSince1970 + 1)], previous: previous)
        XCTAssertEqual(future.location, previous)
    }

    func testLocationsRejectOutOfRangeAndNonfiniteCoordinates() {
        XCTAssertNil(VehicleLocation(latitude: 91, longitude: 116, timestamp: now))
        XCTAssertNil(VehicleLocation(latitude: 30, longitude: 181, timestamp: now))
        XCTAssertNil(VehicleLocation(latitude: .nan, longitude: 116, timestamp: now))
        XCTAssertNil(VehicleLocation(latitude: 0, longitude: 0, timestamp: now))
    }
}
