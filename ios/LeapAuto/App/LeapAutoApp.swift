import SwiftUI

@main
@MainActor
struct LeapAutoApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .task {
                    if (ProcessInfo.processInfo.arguments.contains("--demo") || AppModel.isOfflineTestRun), model.mode == .signedOut {
                        model.enterDemo()
                    }
                    await model.restore()
                }
        }
    }
}
