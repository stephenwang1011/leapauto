import Foundation
import XCTest
@testable import LeapCore

final class WidgetSnapshotTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private let generation = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!

    private func status(at date: Date?, soc: Double = 76) -> VehicleStatus {
        var signals: [String: JSONValue] = [
            "1204": .number(soc), "3257": .number(202), "3262": .number(0),
            "1149": .number(1), "2": .number(121.4), "3": .number(31.2),
            "phone": .string("SHOULD-NOT-SERIALIZE"), "vin": .string("SHOULD-NOT-SERIALIZE"),
            "accessToken": .string("SHOULD-NOT-SERIALIZE")
        ]
        if let date { signals["sts"] = .number(date.timeIntervalSince1970) }
        return VehicleStatus(raw: .object(["signalMap": .object(signals)]), receivedAt: now)
    }

    private func snapshot(source: WidgetSnapshot.Source = .vehicle, timestamp: Date? = nil) -> WidgetSnapshot {
        WidgetSnapshot(generation: generation, source: source, status: status(at: timestamp ?? now), at: now)
    }

    private func presentation(_ value: WidgetSnapshot, at date: Date? = nil) -> WidgetSnapshotPresentation {
        WidgetSnapshotPolicy.presentation(for: .available(value), at: date ?? now)
    }

    func testMissingAndLoggedOutAreUnconfiguredWithNoValues() throws {
        XCTAssertEqual(WidgetSnapshotPolicy.presentation(for: .missing, at: now).state, .unconfigured)
        let cleared = WidgetSnapshot.unconfigured(at: now)
        let restored = try WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(cleared))
        XCTAssertEqual(presentation(restored).state, .unconfigured)
        XCTAssertNil(restored.generation)
        XCTAssertNil(presentation(restored).source)
        XCTAssertNil(presentation(restored).visibleSnapshot)
    }

    func testConfiguredBeforeFirstReadingWaitsWithoutSampleValues() {
        let pending = WidgetSnapshot(generation: generation, source: .vehicle, at: now)
        XCTAssertEqual(presentation(pending).state, .waiting)
        XCTAssertNil(presentation(pending).visibleSnapshot)
    }

    func testRoundTripRestoresCachedReadingWithOriginalTime() throws {
        let cached = snapshot(timestamp: now.addingTimeInterval(-60))
        let restored = try WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(cached))
        XCTAssertEqual(restored, cached)
        XCTAssertEqual(restored.sourceTimestamp, now.addingTimeInterval(-60))
        XCTAssertEqual(presentation(restored).state, .recent)
        XCTAssertEqual(presentation(restored).visibleSnapshot?.electricRangeKM, 202)
        XCTAssertEqual(presentation(restored).visibleSnapshot?.chargingState, .charging)
        XCTAssertEqual(presentation(restored, at: now.addingTimeInterval(300)).state, .stale)
    }

    func testOnlyAllowlistedMinimalFieldsAreEncoded() throws {
        let data = try WidgetSnapshotCodec.encode(snapshot())
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(Set(object.keys), Set(["schemaVersion", "generation", "source", "batterySOC",
                                             "electricRangeKM", "chargingState", "sourceTimestamp", "savedAt", "refreshFailed"]))
        let text = try XCTUnwrap(String(data: data, encoding: .utf8))
        for forbidden in ["SHOULD-NOT-SERIALIZE", "latitude", "longitude", "phone", "vin", "accessToken", "121.4", "31.2"] {
            XCTAssertFalse(text.contains(forbidden))
        }
    }

    func testFreshnessBoundariesAndExpiredValuesAreHidden() {
        let cached = snapshot()
        XCTAssertEqual(presentation(cached, at: now.addingTimeInterval(299.999)).state, .recent)
        XCTAssertEqual(presentation(cached, at: now.addingTimeInterval(300)).state, .stale)
        XCTAssertNotNil(presentation(cached, at: now.addingTimeInterval(1_799)).visibleSnapshot)
        XCTAssertEqual(presentation(cached, at: now.addingTimeInterval(1_800)).state, .expired)
        XCTAssertNil(presentation(cached, at: now.addingTimeInterval(1_800)).visibleSnapshot)
        XCTAssertNil(presentation(cached, at: now.addingTimeInterval(86_400)).visibleSnapshot)
    }

    func testRestoreDoesNotResetFreshnessOrReviveExpiredData() throws {
        let restored = try WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(snapshot()))
        let expired = presentation(restored, at: now.addingTimeInterval(1_801))
        XCTAssertEqual(expired.state, .expired)
        XCTAssertNil(expired.visibleSnapshot)
    }

    func testMissingSourceTimeDoesNotUseReceivedOrSavedTime() {
        let unknown = WidgetSnapshot(generation: generation, source: .vehicle, status: status(at: nil), at: now)
        XCTAssertEqual(presentation(unknown).state, .unknownTimestamp)
        XCTAssertNil(presentation(unknown).visibleSnapshot)
    }

    func testFutureSourceTimeAndClockRollbackHideValues() {
        let futureSource = snapshot(timestamp: now.addingTimeInterval(1))
        XCTAssertEqual(presentation(futureSource).state, .unknownTimestamp)
        XCTAssertNil(presentation(futureSource).visibleSnapshot)
        XCTAssertEqual(presentation(snapshot(), at: now.addingTimeInterval(-1)).state, .unknownTimestamp)
        XCTAssertNil(presentation(snapshot(), at: now.addingTimeInterval(-1)).visibleSnapshot)
    }

    func testFailurePreservesOriginalTimeAndAgesToExpired() throws {
        let cached = snapshot()
        let failed = try cached.markingRefreshFailed(generation: generation)
        XCTAssertTrue(presentation(failed).refreshFailed)
        XCTAssertEqual(failed.savedAt, cached.savedAt)
        XCTAssertEqual(failed.sourceTimestamp, cached.sourceTimestamp)
        XCTAssertEqual(presentation(failed).visibleSnapshot?.batterySOC, 76)
        XCTAssertEqual(presentation(failed, at: now.addingTimeInterval(1_800)).state, .expired)
        XCTAssertNil(presentation(failed, at: now.addingTimeInterval(1_800)).visibleSnapshot)
    }

    func testSuccessfulRecoveryReplacesReadingAndClearsError() throws {
        let failed = try snapshot().markingRefreshFailed(generation: generation)
        let later = now.addingTimeInterval(10)
        let recovered = try failed.replacing(status: status(at: later, soc: 77), generation: generation, at: later)
        XCTAssertFalse(recovered.refreshFailed)
        XCTAssertEqual(recovered.batterySOC, 77)
        XCTAssertEqual(recovered.sourceTimestamp, later)
        XCTAssertEqual(presentation(recovered, at: later).state, .recent)
    }

    func testOldGenerationCannotPublishOrMarkFailureAfterVehicleSwitch() {
        let switched = WidgetSnapshot(generation: UUID(), source: .vehicle, at: now)
        XCTAssertThrowsError(try switched.replacing(status: status(at: now), generation: generation, at: now)) {
            XCTAssertEqual($0 as? WidgetSnapshotError, .obsoleteGeneration)
        }
        XCTAssertThrowsError(try switched.markingRefreshFailed(generation: generation))
        XCTAssertEqual(presentation(switched).state, .waiting)
        XCTAssertNil(presentation(switched).visibleSnapshot)
    }

    func testLogoutTombstoneRejectsDelayedPublishAndFailure() throws {
        let cleared = try WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(.unconfigured(at: now)))
        XCTAssertThrowsError(try cleared.replacing(status: status(at: now), generation: generation, at: now))
        XCTAssertThrowsError(try cleared.markingRefreshFailed(generation: generation))
        XCTAssertNil(cleared.batterySOC)
        XCTAssertNil(cleared.electricRangeKM)
        XCTAssertNil(cleared.chargingState)
        XCTAssertNil(cleared.sourceTimestamp)
    }

    func testDemoLabelSurvivesRestoreFailureAndExpiry() throws {
        let demo = try snapshot(source: .demo).markingRefreshFailed(generation: generation)
        let restored = try WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(demo))
        XCTAssertEqual(presentation(restored).source, .demo)
        XCTAssertEqual(presentation(restored, at: now.addingTimeInterval(1_800)).source, .demo)
        XCTAssertNil(presentation(restored, at: now.addingTimeInterval(1_800)).visibleSnapshot)
    }

    func testDemoWithoutSourceTimeRemainsExplicitlyDemo() {
        let pending = WidgetSnapshot(generation: generation, source: .demo, at: now)
        XCTAssertEqual(presentation(pending).state, .waiting)
        XCTAssertEqual(presentation(pending).source, .demo)
        let unknown = WidgetSnapshot(generation: generation, source: .demo, status: status(at: nil), at: now)
        XCTAssertEqual(presentation(unknown).state, .unknownTimestamp)
        XCTAssertEqual(presentation(unknown).source, .demo)
        XCTAssertNil(presentation(unknown).visibleSnapshot)
    }

    func testUnknownFieldsStayUnknownInsteadOfZero() throws {
        let empty = VehicleStatus(raw: .object(["sts": .number(now.timeIntervalSince1970)]), receivedAt: now)
        let value = WidgetSnapshot(generation: generation, source: .vehicle, status: empty, at: now)
        let restored = try WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(value))
        XCTAssertNil(restored.batterySOC)
        XCTAssertNil(restored.electricRangeKM)
        XCTAssertNil(restored.chargingState)
    }

    func testCorruptUnsupportedAndOversizedFilesAreRejected() throws {
        XCTAssertThrowsError(try WidgetSnapshotCodec.decode(Data("bad json".utf8)))
        XCTAssertThrowsError(try WidgetSnapshotCodec.decode(Data(repeating: 32, count: WidgetSnapshotCodec.maximumBytes + 1))) {
            XCTAssertEqual($0 as? WidgetSnapshotError, .tooLarge)
        }
        let data = try WidgetSnapshotCodec.encode(snapshot())
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        for invalid in [101.0, -1.0] {
            object["batterySOC"] = invalid
            XCTAssertThrowsError(try WidgetSnapshotCodec.decode(JSONSerialization.data(withJSONObject: object)))
        }
        object["batterySOC"] = 76
        object["electricRangeKM"] = -1
        XCTAssertThrowsError(try WidgetSnapshotCodec.decode(JSONSerialization.data(withJSONObject: object)))
        object["electricRangeKM"] = 202
        object["schemaVersion"] = 999
        XCTAssertThrowsError(try WidgetSnapshotCodec.decode(JSONSerialization.data(withJSONObject: object)))
    }

    func testInconsistentLogoutAndUnknownEnumAreRejected() throws {
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: WidgetSnapshotCodec.encode(snapshot())) as? [String: Any])
        object.removeValue(forKey: "generation")
        XCTAssertThrowsError(try WidgetSnapshotCodec.decode(JSONSerialization.data(withJSONObject: object)))
        object.removeValue(forKey: "source")
        XCTAssertThrowsError(try WidgetSnapshotCodec.decode(JSONSerialization.data(withJSONObject: object)))
        object = try XCTUnwrap(JSONSerialization.jsonObject(with: WidgetSnapshotCodec.encode(snapshot())) as? [String: Any])
        object["chargingState"] = 999
        XCTAssertThrowsError(try WidgetSnapshotCodec.decode(JSONSerialization.data(withJSONObject: object)))
    }

    func testUnavailableStorageNeverDisplaysPreviousNumbers() {
        let view = WidgetSnapshotPolicy.presentation(for: .unavailable, at: now)
        XCTAssertEqual(view.state, .unavailable)
        XCTAssertNil(view.visibleSnapshot)
        XCTAssertNil(view.source)
    }

    func testTimelineSchedulesOnlyFutureLocalFreshnessTransitions() {
        let read = WidgetSnapshotRead.available(snapshot())
        XCTAssertEqual(WidgetSnapshotPolicy.transitionDates(for: read, after: now),
                       [now.addingTimeInterval(300), now.addingTimeInterval(1_800)])
        XCTAssertEqual(WidgetSnapshotPolicy.transitionDates(for: read, after: now.addingTimeInterval(300)),
                       [now.addingTimeInterval(1_800)])
        XCTAssertTrue(WidgetSnapshotPolicy.transitionDates(for: read, after: now.addingTimeInterval(1_800)).isEmpty)
        XCTAssertTrue(WidgetSnapshotPolicy.transitionDates(for: .unavailable, after: now).isEmpty)
        XCTAssertTrue(WidgetSnapshotPolicy.transitionDates(for: .missing, after: now).isEmpty)
    }
}
