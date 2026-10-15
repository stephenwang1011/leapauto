import Foundation
import XCTest
@testable import LeapCore

final class CommandTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func status(gear: String? = "P", speed: Double? = 0, age: TimeInterval? = 0) -> VehicleStatus {
        var signals: [String: JSONValue] = [:]
        if let gear { signals["1010"] = .string(gear) }
        if let speed { signals["1319"] = .number(speed) }
        if let age { signals["sts"] = .number(now.timeIntervalSince1970 - age) }
        return VehicleStatus(raw: .object(signals), receivedAt: now)
    }

    func testBodyCommandsMatchVerifiedWirePayloads() throws {
        let fixtures: [(LeapCommand, String, String)] = [
            (.lock, "110", "lock"), (.unlock, "110", "unlock"),
            (.trunkOpen, "130", "true"), (.trunkClose, "130", "false"),
            (.frunkOpen, "131", "100"), (.frunkClose, "131", "0"),
            (.windowOpen, "230", "5"), (.windowVent, "230", "2"), (.windowClose, "230", "0"),
            (.sunshadeOpen, "240", "10"), (.sunshadeClose, "240", "0"),
            (.sunroofOpen, "300", "1"), (.sunroofClose, "300", "0"),
            (.horn, "120", "true"), (.batteryPreheat, "160", "ptcon"), (.batteryPreheatOff, "160", "ptcoff"),
            (.startCharging, "193", "start"), (.stopCharging, "193", "stop")
        ]
        for (command, expectedID, value) in fixtures {
            let payload = try command.build()
            XCTAssertEqual(payload.commandID, expectedID)
            XCTAssertEqual(payload.state, ["value": .string(value)])
            XCTAssertEqual(try JSONValue(data: Data(payload.stateJSON.utf8)), .object(payload.state))
        }
        XCTAssertEqual(try LeapCommand.unlockCharger.build().state, ["operation": .string("unlock")])
        XCTAssertEqual(try LeapCommand.unlockCharger.build().commandID, "192")
    }

    func testClimatePresetsMatchExecutableUpstream() throws {
        let on = try LeapCommand.acOn.build()
        XCTAssertEqual(on.commandID, "170")
        XCTAssertEqual(on.state, ["operate": .string("auto"), "temperature": .string("24"), "windlevel": .string("3"), "mode": .string("cold"), "circle": .string("in"), "wshld": .string("1"), "position": .string("all")])
        XCTAssertEqual(try LeapCommand.acOff.build().state["operate"], .string("off"))
        XCTAssertEqual(try LeapCommand.acOff.build().state["mode"], .string("nohotcold"))
        XCTAssertEqual(try LeapCommand.quickCool.build().state["temperature"], .string("18"))
        XCTAssertEqual(try LeapCommand.quickCool.build().state["wshld"], .string("1"))
        XCTAssertEqual(try LeapCommand.quickHeat.build().state["temperature"], .string("32"))
        XCTAssertEqual(try LeapCommand.quickHeat.build().state["wshld"], .string("1"))
        XCTAssertEqual(try LeapCommand.defrost.build().state["position"], .string("wshld"))
        XCTAssertEqual(try LeapCommand.defrost.build().state["circle"], .string("out"))
        XCTAssertEqual(try LeapCommand.deodorize.build().state["wshld"], .string("0"))
    }

    func testCustomACTemperatureBoundsAndModeSwitch() throws {
        XCTAssertThrowsError(try LeapCommand.setACTemperature(15).build())
        XCTAssertThrowsError(try LeapCommand.setACTemperature(33).build())
        for temperature in 16...32 {
            let payload = try LeapCommand.setACTemperature(temperature).build()
            XCTAssertEqual(payload.state["temperature"], .string(String(temperature)))
            XCTAssertEqual(payload.state["mode"], .string(temperature <= 26 ? "cold" : "hot"))
            XCTAssertEqual(payload.state["windlevel"], .string("7"))
            XCTAssertEqual(payload.state["wshld"], .string("0"))
        }
    }

    func testAllCommandsRequireFreshConfirmedParking() throws {
        for command in [LeapCommand.unlock, .trunkOpen, .lock, .acOn, .acOff, .horn, .startCharging] {
            XCTAssertNoThrow(try command.validate(status: status(), now: now))
            XCTAssertThrowsError(try command.validate(status: nil, now: now))
            XCTAssertThrowsError(try command.validate(status: status(gear: nil), now: now))
            XCTAssertThrowsError(try command.validate(status: status(speed: nil), now: now))
            XCTAssertThrowsError(try command.validate(status: status(gear: "D"), now: now))
            XCTAssertThrowsError(try command.validate(status: status(speed: 1), now: now))
            XCTAssertThrowsError(try command.validate(status: status(age: nil), now: now))
            XCTAssertThrowsError(try command.validate(status: status(age: 301), now: now))
            XCTAssertThrowsError(try command.validate(status: status(age: -1), now: now))
            XCTAssertNoThrow(try command.validate(status: status(age: 300), now: now))
        }
        XCTAssertTrue(LeapCommand.unlock.isSensitive)
        XCTAssertTrue(LeapCommand.trunkOpen.isSensitive)
    }

    func testPollingBusinessSuccessIsExplicitAndResultTakesPriority() {
        XCTAssertEqual(ControlPollResult(raw: .object(["result": .number(0)])), .succeeded)
        XCTAssertEqual(ControlPollResult(raw: .object(["code": .string("0")])), .succeeded)
        XCTAssertEqual(ControlPollResult(raw: .object(["result": .null, "code": .number(0)])), .succeeded)
        XCTAssertEqual(ControlPollResult(raw: .object(["result": .number(1), "code": .number(0)])), .pending)
        XCTAssertEqual(ControlPollResult(raw: .object(["code": .number(200)])), .pending)
        XCTAssertEqual(ControlPollResult(raw: .object([:])), .pending)
        XCTAssertEqual(ControlPollResult(raw: .object(["result": .bool(false)])), .pending)
    }

    func testPollingTimingMatchesCurrentExecutablePolicy() {
        XCTAssertEqual(ControlPollingPolicy.app.initialDelay, 0.5)
        XCTAssertEqual(ControlPollingPolicy.app.interval, 0.5)
        XCTAssertEqual(ControlPollingPolicy.app.attempts, 48)
        XCTAssertEqual(ControlPollingPolicy.widget.attempts, 24)
        XCTAssertNil(ControlPollingPolicy.app.delayBeforeAttempt(-1))
        XCTAssertNil(ControlPollingPolicy.app.delayBeforeAttempt(48))
        XCTAssertEqual(ControlPollingPolicy.app.delayBeforeAttempt(0), 0.5)
        XCTAssertFalse(ControlPollingPolicy.app.hasTimedOut(elapsed: 23.9))
        XCTAssertTrue(ControlPollingPolicy.app.hasTimedOut(elapsed: 24))
        XCTAssertTrue(ControlPollingPolicy.app.hasTimedOut(elapsed: .infinity))
    }

    func testAcceptedIsNotExecutionAndNoIDCompatibilityNeverClaimsSuccess() {
        var progress = ControlProgress(messageID: nil, acceptedAt: now)
        XCTAssertEqual(progress.outcome, .accepted(messageID: nil))
        progress.receive(.succeeded, at: now.addingTimeInterval(1))
        XCTAssertEqual(progress.outcome, .accepted(messageID: nil))
        progress.checkTimeout(at: now.addingTimeInterval(25))
        XCTAssertEqual(progress.outcome, .accepted(messageID: nil))
    }

    func testVerifiedPollingAndTerminalTimeoutAreDistinct() {
        var success = ControlProgress(messageID: "message-id", acceptedAt: now)
        success.receive(.pending, at: now.addingTimeInterval(1))
        XCTAssertEqual(success.outcome, .accepted(messageID: "message-id"))
        success.receive(.succeeded, at: now.addingTimeInterval(2))
        XCTAssertEqual(success.outcome, .verified)
        success.checkTimeout(at: now.addingTimeInterval(30))
        XCTAssertEqual(success.outcome, .verified)

        var timeout = ControlProgress(messageID: "message-id", acceptedAt: now)
        timeout.checkTimeout(at: now.addingTimeInterval(24))
        XCTAssertEqual(timeout.outcome, .timedOut)
        timeout.receive(.succeeded, at: now.addingTimeInterval(25))
        XCTAssertEqual(timeout.outcome, .timedOut)
    }

    func testLateResultDoesNotBypassTimeoutAndEarlyClockDoesNotVerify() {
        var late = ControlProgress(messageID: "id", acceptedAt: now)
        late.receive(.succeeded, at: now.addingTimeInterval(25))
        XCTAssertEqual(late.outcome, .timedOut)
        var early = ControlProgress(messageID: "id", acceptedAt: now)
        early.receive(.succeeded, at: now.addingTimeInterval(-1))
        XCTAssertEqual(early.outcome, .accepted(messageID: "id"))
    }
}
