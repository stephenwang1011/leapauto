import XCTest
@testable import LeapAuto
import LeapCore

@MainActor
final class AppModelTests: XCTestCase {
    func testDemoNeverNeedsCredentialsAndDoesNotChangeVehicleState() async {
        let model = AppModel()
        model.enterDemo()
        XCTAssertEqual(model.mode, .demo)
        XCTAssertTrue(model.canControl)
        let locked = model.status?.locked
        model.request(.unlock)
        XCTAssertNotNil(model.pendingCommand)
        await model.executePendingCommand(pin: "0000")
        XCTAssertNil(model.pendingCommand)
        XCTAssertFalse(model.isCommandRunning)
        XCTAssertEqual(model.status?.locked, locked)
        XCTAssertTrue(model.operationMessage?.contains("没有发送指令") == true)
    }

    func testInvalidPINNeverSubmitsAndLeavesConfirmationOpen() async {
        let model = AppModel()
        model.enterDemo()
        model.request(.lock)
        await model.executePendingCommand(pin: "１２３４")
        XCTAssertNotNil(model.pendingCommand)
        XCTAssertFalse(model.isCommandRunning)
        XCTAssertNotNil(model.errorMessage)
        model.cancelPendingCommand()
        XCTAssertNil(model.pendingCommand)
    }

    func testRepeatedRequestsCannotReplacePendingTarget() {
        let model = AppModel()
        model.enterDemo()
        model.request(.lock)
        model.request(.unlock)
        XCTAssertEqual(model.pendingCommand?.title, LeapCommand.lock.title)
    }

    func testAutomationCannotRestoreOrImportRealSessions() async {
        XCTAssertTrue(AppModel.isOfflineTestRun, "The test scheme must force offline mode")
        let model = AppModel()
        await model.restore()
        XCTAssertEqual(model.mode, .signedOut)
        await model.importSession(from: URL(fileURLWithPath: "/nonexistent/never-read.json"))
        XCTAssertEqual(model.mode, .signedOut)
        XCTAssertEqual(model.errorMessage, "自动化测试模式仅允许离线演示。")
    }

    func testLogoutTombstonePersistsUntilSuccessfulCleanup() {
        let suite = "LeapAutoTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let first = SessionLifecycleStore(defaults: defaults)
        XCTAssertFalse(first.logoutRequested)
        first.markLogoutRequested()
        let restored = SessionLifecycleStore(defaults: defaults)
        XCTAssertTrue(restored.logoutRequested)
        restored.clearLogoutRequest()
        XCTAssertFalse(first.logoutRequested)
    }

    func testSignedOutBlocksControl() {
        let model = AppModel()
        XCTAssertFalse(model.canControl)
        model.request(.unlock)
        XCTAssertNil(model.pendingCommand)
        XCTAssertNotNil(model.errorMessage)
    }
}
