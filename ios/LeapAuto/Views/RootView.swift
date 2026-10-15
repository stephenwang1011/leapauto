import SwiftUI
import LeapCore

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        VStack(spacing: 0) {
            if model.mode == .demo {
                Label("离线演示 · 示例车况，不会控制真实车辆", systemImage: "play.circle")
                    .font(.caption.weight(.medium))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(LeapStyle.accent.opacity(0.08))
                    .accessibilityIdentifier("mode.demo")
            }
            if let error = model.errorMessage {
                FeedbackBanner(text: error, isError: true) { model.errorMessage = nil }
            }
            if let message = model.operationMessage {
                FeedbackBanner(text: message, isError: false) { model.operationMessage = nil }
            }
            if let message = model.widgetMessage {
                FeedbackBanner(text: message, isError: false) { model.widgetMessage = nil }
            }
            if model.mode == .signedOut {
                WelcomeView()
            } else {
                TabView {
                    NavigationStack { VehicleHomeView() }
                        .tabItem { Label("爱车", systemImage: "car.side") }
                        .accessibilityIdentifier("tab.vehicle")
                    NavigationStack { SettingsView() }
                        .tabItem { Label("我的", systemImage: "person.crop.circle") }
                        .accessibilityIdentifier("tab.settings")
                }
            }
        }
        .tint(LeapStyle.accent)
        .background { PageCanvas() }
        .sheet(isPresented: Binding(
            get: { model.pendingCommand != nil },
            set: { if !$0 { model.cancelPendingCommand() } }
        )) {
            if let command = model.pendingCommand {
                CommandConfirmationView(command: command)
                    .environmentObject(model)
                    .presentationBackground(Color(uiColor: .systemBackground))
                    .presentationDragIndicator(.visible)
                    .interactiveDismissDisabled(model.isCommandRunning)
            }
        }
    }
}

private struct FeedbackBanner: View {
    let text: String
    let isError: Bool
    let dismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: isError ? "exclamationmark.circle" : "info.circle")
                .foregroundStyle(isError ? LeapStyle.danger : LeapStyle.accent)
                .padding(.top, 12)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 12)
                .accessibilityIdentifier(isError ? "feedback.error" : "feedback.operation")
            Button(action: dismiss) {
                Image(systemName: "xmark").frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("关闭提示")
        }
        .padding(.leading, 16)
        .background(Color(uiColor: .secondarySystemBackground))
    }
}
