import SwiftUI
import UniformTypeIdentifiers

struct WelcomeView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    if model.sessionDeletionFailed {
                        GlassCard(aura: LeapStyle.danger) {
                            VStack(alignment: .leading, spacing: 14) {
                                Label("本机会话尚未完全清除", systemImage: "exclamationmark.shield")
                                    .font(.headline)
                                    .foregroundStyle(LeapStyle.dangerText)
                                Text("钥匙串删除未完成。请重试清除，避免凭据留在本机。")
                                    .font(.subheadline)
                                Button("重试清除会话", role: .destructive) {
                                    Task { await model.logout() }
                                }
                                .frame(minHeight: 44)
                                .buttonStyle(.bordered)
                                .disabled(model.isBusy)
                                .accessibilityIdentifier("session.retryDeletion")
                            }
                        }
                    }
                    VStack(alignment: .leading, spacing: 16) {
                        Image(systemName: "car.side")
                            .font(.system(size: 72, weight: .light))
                            .foregroundStyle(LeapStyle.accent)
                            .padding(.vertical, 20)
                            .accessibilityHidden(true)
                        Text("零跑智控")
                            .font(.largeTitle.bold())
                        Text("你的爱车，清晰掌握。")
                            .font(.title3)
                            .foregroundStyle(.secondary)
                        Text("iOS 原生预览 · 非官方个人客户端")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.top, 12)

                    GlassCard(aura: LeapStyle.accent) {
                        VStack(alignment: .leading, spacing: 16) {
                            SectionHeading(title: "先看看你的新座舱", caption: "离线示例数据，完整体验车况与控制流程。")
                            Button { model.enterDemo() } label: {
                                Label("体验离线演示", systemImage: "play.circle")
                                    .font(.headline)
                                    .frame(maxWidth: .infinity, minHeight: 44)
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(model.isBusy)
                            .accessibilityIdentifier("welcome.demo")
                            Text("不会连接账号，也不会发送真实车辆指令。")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    GlassCard {
                        VStack(alignment: .leading, spacing: 16) {
                            SectionHeading(title: "开发者会话接入", caption: "iOS 接口兼容性尚未验证。")
                            Text("导入后，将账号会话发送到零跑接口以读取车辆信息。仅导入你自己账号的有效 JSON 会话文件；凭据保存在本机钥匙串中。")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text("会话文件包含登录凭据。请勿上传、转发或粘贴到聊天中。")
                                .font(.caption.weight(.medium))
                            SessionImportButton()
                                .buttonStyle(.bordered)
                        }
                    }

                    GlassCard {
                        VStack(alignment: .leading, spacing: 4) {
                            UnavailableFeature(title: "短信登录", detail: "当前版本仅提供开发者会话导入。", symbol: "message")
                            Divider()
                            UnavailableFeature(title: "蓝牙钥匙", detail: "未实现蓝牙配对或近场控车。", symbol: "key")
                            Divider()
                            UnavailableFeature(title: "桌面小组件", detail: "当前版本未包含 Widget 扩展。", symbol: "square.grid.2x2")
                        }
                    }
                }
                .padding(24)
                .frame(maxWidth: 600)
                .frame(maxWidth: .infinity)
            }
            .background { PageCanvas() }
            .navigationBarHidden(true)
        }
    }
}

struct SessionImportButton: View {
    @EnvironmentObject private var model: AppModel
    @State private var isImporting = false

    var body: some View {
        Button {
            isImporting = true
        } label: {
            HStack(spacing: 9) {
                if model.isBusy { ProgressView() }
                else { Image(systemName: "doc.badge.plus") }
                Text(model.isBusy ? "正在读取会话…" : "选择本人会话 JSON 文件")
            }
            .frame(maxWidth: .infinity, minHeight: 44)
        }
        .disabled(model.isBusy || model.isCommandRunning)
        .accessibilityIdentifier("session.import")
        .fileImporter(isPresented: $isImporting, allowedContentTypes: [.json], allowsMultipleSelection: false) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                Task { await model.importSession(from: url) }
            case .failure(let error):
                if (error as NSError).code != NSUserCancelledError {
                    model.errorMessage = "无法打开文件。请重试，并选择有效的 JSON 会话文件。"
                }
            }
        }
    }
}
