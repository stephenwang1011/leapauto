import SwiftUI
import LeapCore

struct VehicleLocationView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        DetailPage("车辆位置") {
            TimelineView(.periodic(from: .now, by: 30)) { context in
                GlassCard(aura: LeapStyle.accent) {
                    VStack(alignment: .leading, spacing: 18) {
                        Image(systemName: "location.circle")
                            .font(.system(size: 46, weight: .light))
                            .foregroundStyle(LeapStyle.accent)
                            .accessibilityHidden(true)
                        Text(locationTitle(at: context.date)).font(.title2.bold())
                            .accessibilityIdentifier("location.freshness")
                        Text(locationDetail(at: context.date)).font(.subheadline).foregroundStyle(.secondary)
                        if let location = model.status?.location {
                            Divider()
                            MetricRow(title: "车辆上报时间", value: location.timestamp.formatted(date: .abbreviated, time: .standard))
                            MetricRow(title: "本机接收时间", value: location.receivedAt.formatted(date: .abbreviated, time: .standard))
                            MetricRow(title: "坐标标记", value: coordinateTitle(location.coordinateSystem))
                        }
                    }
                }
            }
            GlassCard {
                VStack(alignment: .leading, spacing: 16) {
                    SectionHeading(title: "位置隐私", caption: "仅保留当前会话内存中的位置快照")
                    Label("不保存坐标或地址", systemImage: "lock.shield")
                    Label("不进行后台定位或地图请求", systemImage: "location.slash")
                    Label("不会自动向地图应用分享位置", systemImage: "hand.raised")
                    Divider()
                    UnavailableFeature(title: "地图导航", detail: "iOS 地图坐标接入尚未验证，当前版本不提供导航。", symbol: "map")
                }
                .font(.subheadline)
            }
            Button { Task { await model.refresh() } } label: {
                Label(model.isBusy ? "正在刷新…" : "刷新车辆数据", systemImage: "arrow.clockwise")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
            .disabled(model.isBusy || model.isCommandRunning)
            .accessibilityIdentifier("location.refresh")
            Text("刷新车辆数据不会调用手机定位，也不会打开地图。较旧或过期的位置不能视为车辆当前位置。")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .accessibilityIdentifier("screen.location")
    }

    private func locationTitle(at now: Date) -> String {
        switch model.status?.location?.freshness(at: now) {
        case .fresh: return "位置快照较新"
        case .stale: return "位置已陈旧"
        case .expired: return "位置已过期"
        case .invalid: return "位置时间异常"
        case .unavailable, .none: return "暂无可用位置"
        }
    }

    private func locationDetail(at now: Date) -> String {
        switch model.status?.location?.freshness(at: now) {
        case .fresh: return "车辆在 5 分钟内上报的位置快照。并非持续实时追踪。"
        case .stale: return "快照已超过 5 分钟。请刷新后核对，车辆可能已经移动。"
        case .expired: return "快照已超过 30 分钟，不能用于判断车辆当前位置。"
        case .invalid: return "快照时间无法验证。请刷新车辆数据后重试。"
        case .unavailable: return "快照已超过 60 分钟，当前不可用。请刷新车辆数据。"
        case .none: return "车辆尚未提供可验证的位置快照。缺少位置不会被替换为手机位置。"
        }
    }

    private func coordinateTitle(_ system: CoordinateSystem) -> String {
        switch system {
        case .gcj02: return "GCJ-02"
        case .wgs84: return "WGS-84"
        case .unconfirmed: return "未确认"
        }
    }
}
