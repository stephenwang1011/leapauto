import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var isConfirmingLogout = false

    var body: some View {
        DetailPage("我的") {
            GlassCard(aura: LeapStyle.accent) {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 14) {
                        Image(systemName: model.mode == .demo ? "play.circle" : "person.crop.circle")
                            .font(.largeTitle)
                            .foregroundStyle(LeapStyle.accent)
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 5) {
                            Text(model.mode == .demo ? "离线演示" : "开发者会话")
                                .font(.title3.bold())
                            Text(model.mode == .demo ? "示例车况 · 无网络请求" : "已载入本人账号会话")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    Divider()
                    Text(model.mode == .demo
                         ? "当前操作只演示流程，不会连接账号或控制真实车辆。"
                         : "车辆信息通过零跑接口读取。iOS 接口兼容性尚未验证，请核对数据来源与上报时间。")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    MetricRow(title: "车辆数量", value: model.vehicles.count.formatted())
                }
            }

            GlassCard {
                VStack(alignment: .leading, spacing: 16) {
                    SectionHeading(title: "隐私与安全")
                    Label("登录会话保存在本机钥匙串", systemImage: "key.horizontal")
                    Label("操作密码每次输入，不持久保存", systemImage: "lock.shield")
                    Label("真实控车需本机身份验证", systemImage: "faceid")
                    Label("位置与完整车况仅保留在会话内存", systemImage: "location.slash")
                    Label("小组件仅共享匿名电量、续航与充电快照", systemImage: "square.grid.2x2")
                    Text("会话文件包含凭据。请勿发给他人、提交到代码仓库或粘贴到聊天中。")
                        .font(.caption).foregroundStyle(.secondary)
                }
                .font(.subheadline)
            }

            if model.mode == .demo {
                GlassCard {
                    VStack(alignment: .leading, spacing: 16) {
                        SectionHeading(title: "接入本人账号", caption: "仅供开发者验证")
                        Text("导入后，将账号会话发送到零跑接口。仅导入你自己的有效 JSON 会话文件；凭据保存在本机钥匙串。iOS 接口兼容性尚未验证。")
                            .font(.subheadline).foregroundStyle(.secondary)
                        SessionImportButton().buttonStyle(.bordered)
                    }
                }
            }

            GlassCard {
                VStack(alignment: .leading, spacing: 6) {
                    SectionHeading(title: "当前版本")
                        .padding(.bottom, 8)
                    MetricRow(title: "外观", value: "跟随系统深浅色", symbol: "circle.lefthalf.filled")
                    Divider()
                    UnavailableFeature(title: "短信登录", detail: "使用开发者会话文件接入。", symbol: "message")
                    Divider()
                    UnavailableFeature(title: "蓝牙钥匙", detail: "已有离线协议和禁用的传输适配；完整 SM2、配对及实车未验收，保持关闭。", symbol: "key")
                    Divider()
                    VStack(alignment: .leading, spacing: 8) {
                        Label("只读状态小组件", systemImage: "square.grid.2x2")
                            .font(.headline)
                        Text("支持小/中尺寸。主 App 更新本机匿名快照，iOS 决定刷新时机；不会后台联网或直接控车。需先在 Xcode 配置两 target 的同一 App Group。")
                            .font(.caption).foregroundStyle(.secondary)
                        Button("更新本机小组件快照") { model.refreshWidgetSnapshot() }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("widget.refreshLocal")
                    }
                    .padding(.vertical, 10)
                    Divider()
                    UnavailableFeature(title: "充电通知", detail: "尚未包含后台充电状态通知。", symbol: "bell")
                }
            }

            Button(role: .destructive) {
                isConfirmingLogout = true
            } label: {
                Label(model.mode == .demo ? "退出演示" : "退出并清除会话", systemImage: "rectangle.portrait.and.arrow.right")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.bordered)
            .disabled(model.isBusy || model.isCommandRunning)
            .accessibilityIdentifier("session.logout")
            .confirmationDialog("退出并清除本机会话？", isPresented: $isConfirmingLogout, titleVisibility: .visible) {
                Button("退出并清除", role: .destructive) {
                    Task { await model.logout() }
                }
                .accessibilityIdentifier("session.confirmLogout")
                Button("取消", role: .cancel) { }
            } message: {
                Text("将清除登录会话、内存车况与位置，以及共享小组件快照。系统缓存的卡片可能需等待刷新才消失。不会向车辆发送控制指令。")
            }

            Text("零跑智控 · iOS 原生预览\n非官方个人客户端，与零跑汽车无隶属关系。")
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
                .multilineTextAlignment(.center)
                .padding(.vertical, 8)
        }
        .accessibilityIdentifier("screen.settings")
    }
}
