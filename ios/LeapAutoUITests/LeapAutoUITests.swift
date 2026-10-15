import XCTest

final class LeapAutoUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    @MainActor
    func testOfflineDemoAndCancelControl() {
        let app = XCUIApplication()
        app.launchArguments = ["--demo"]
        app.launch()
        XCTAssertTrue(app.otherElements["vehicle.home"].waitForExistence(timeout: 5))
        for _ in 0..<5 where !app.buttons["control.unlock"].isHittable { app.swipeUp() }
        app.buttons["control.unlock"].tap()
        XCTAssertTrue(app.buttons["control.cancel"].waitForExistence(timeout: 2))
        app.buttons["control.cancel"].tap()
        XCTAssertFalse(app.buttons["control.confirm"].exists)
        app.buttons["control.unlock"].tap()
        XCTAssertTrue(app.secureTextFields["control.pin"].waitForExistence(timeout: 2))
        app.buttons["control.cancel"].tap()
    }
}
