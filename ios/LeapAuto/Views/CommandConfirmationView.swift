import SwiftUI
import LeapCore

struct CommandConfirmationView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var pin = ""
    @FocusState private var pinFocused: Bool
    let command: LeapCommand

    private var isValidPIN: Bool {
        pin.count == 4 && pin.allSatisfy { "0123456789".contains($0) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Image(systemName: "lock.shield")
                        .font(.largeTitle)
                        .foregroundStyle(LeapStyle.accent)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 10) {
                        Text(command.title).font(.title2.bold())
                            .accessibilityIdentifier("command.title")
                        if let vehicle = model.selectedVehicle {
                            Text([vehicle.modelName, vehicle.plateNumber].compactMap { $0 }.joined(separator: " · "))
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        Text(model.mode == .demo
                             ? "当前为离线演示。确认后只展示模拟流程，不会发送真实车辆指令。"
                             : "此操作将向所选车辆发送真实指令。请确认车辆周围安全，并留意车辆实际状态。")
                            .font(.body)
                    }
                    VStack(alignment: .leading, spacing: 10) {
                        Text("四位操作密码").font(.headline)
                        SecureField("输入 4 位数字", text: $pin)
                            .keyboardType(.numberPad)
                            .textContentType(nil)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .focused($pinFocused)
                            .padding(16)
                            .frame(minHeight: 52)
                            .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                            .disabled(model.isCommandRunning)
                            .accessibilityLabel("四位操作密码")
                            .accessibilityIdentifier("control.pin")
                            .onChange(of: pin) { _, value in
                                let filtered = String(value.filter { "0123456789".contains($0) }.prefix(4))
                                if filtered != value { pin = filtered }
                            }
                        Text(model.mode == .demo ? "演示可输入任意 4 位数字。密码不会保存。" : "密码仅用于本次操作，不会保存。发送前还需本机身份验证。")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if let error = model.errorMessage {
                        Label(error, systemImage: "exclamationmark.circle")
                            .font(.subheadline)
                            .foregroundStyle(LeapStyle.dangerText)
                            .accessibilityIdentifier("command.error")
                    }
                    if let message = model.operationMessage, model.isCommandRunning {
                        Text(message).font(.subheadline).foregroundStyle(.secondary)
                            .accessibilityIdentifier("command.progress")
                    }
                    Button {
                        pinFocused = false
                        let submittedPIN = pin
                        pin = ""
                        Task { await model.executePendingCommand(pin: submittedPIN) }
                    } label: {
                        HStack(spacing: 10) {
                            if model.isCommandRunning { ProgressView().tint(.white) }
                            Text(model.isCommandRunning ? "正在等待车辆结果…" : (model.mode == .demo ? "确认演示" : "确认发送指令"))
                                .font(.headline)
                        }
                        .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!isValidPIN || model.isCommandRunning)
                    .accessibilityIdentifier("control.confirm")
                    Text("服务受理不代表车辆已执行。请以结果查询和最新车况为准；若超时，先刷新车况再决定是否重试。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(24)
                .frame(maxWidth: 600)
                .frame(maxWidth: .infinity)
            }
            .navigationTitle("操作确认")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        pin = ""
                        model.cancelPendingCommand()
                    }
                    .frame(minWidth: 44, minHeight: 44)
                    .disabled(model.isCommandRunning)
                    .accessibilityIdentifier("control.cancel")
                }
            }
            .background(Color(uiColor: .systemBackground))
        }
        .onDisappear { pin = "" }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { pin = "" }
        }
    }
}
