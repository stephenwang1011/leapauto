import Foundation
import LeapCore
import SwiftUI
import WidgetKit

private struct StatusEntry: TimelineEntry {
    let date: Date
    let read: WidgetSnapshotRead
}

private struct StatusProvider: TimelineProvider {
    func placeholder(in context: Context) -> StatusEntry {
        // Gallery/placeholder never invent a connected car or synthetic live values.
        StatusEntry(date: Date(), read: .missing)
    }

    func getSnapshot(in context: Context, completion: @escaping (StatusEntry) -> Void) {
        completion(StatusEntry(date: Date(), read: WidgetSnapshotStore().read()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<StatusEntry>) -> Void) {
        let now = Date()
        let read = WidgetSnapshotStore().read()
        let dates = [now] + WidgetSnapshotPolicy.transitionDates(for: read, after: now)
        let entries = dates.map { StatusEntry(date: $0, read: read) }
        // This is a budgeted request, not a refresh guarantee. The extension only
        // re-reads the local file; it never polls the vehicle/backend in background.
        completion(Timeline(entries: entries, policy: .after(now.addingTimeInterval(30 * 60))))
    }
}

@main
struct LeapAutoStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetSnapshotStore.widgetKind, provider: StatusProvider()) { entry in
            StatusWidgetView(entry: entry)
                .containerBackground(for: .widget) { WidgetGlassBackground() }
                .widgetURL(URL(string: "leapauto://status"))
                .privacySensitive()
        }
        .configurationDisplayName("零跑车况")
        .description("查看 App 最近保存的电量、纯电续航和充电状态。仅显示缓存，点击打开 App 更新。")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

private struct StatusWidgetView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme
    let entry: StatusEntry

    private var presentation: WidgetSnapshotPresentation {
        WidgetSnapshotPolicy.presentation(for: entry.read, at: entry.date)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: family == .systemSmall ? 5 : 9) {
            HStack(spacing: 5) {
                Image(systemName: "car.side").foregroundStyle(WidgetPalette.accent)
                    .accessibilityHidden(true)
                Text("零跑智控").font(.caption.weight(.semibold))
                Spacer(minLength: 2)
                if presentation.source == .demo {
                    Text("演示")
                        .font(.caption2.bold())
                        .padding(.horizontal, 5).padding(.vertical, 2)
                        .background(WidgetPalette.accent.opacity(0.13), in: Capsule())
                        .accessibilityLabel("演示数据，不是真实车况")
                }
            }
            if let snapshot = presentation.visibleSnapshot {
                if family == .systemMedium {
                    HStack(alignment: .top, spacing: 20) {
                        energy(snapshot)
                        Spacer(minLength: 0)
                        VStack(alignment: .leading, spacing: 8) {
                            charging(snapshot)
                            freshness
                            timestamp(snapshot)
                        }
                    }
                } else {
                    energy(snapshot)
                    charging(snapshot)
                    freshness
                    timestamp(snapshot)
                }
            } else {
                unavailable
            }
            if family == .systemMedium {
                Text("仅为缓存 · 点击打开 App 更新")
                    .font(.caption2).foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .accessibilityElement(children: .combine)
    }

    private func energy(_ snapshot: WidgetSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("纯电续航").font(.caption2).foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(snapshot.electricRangeKM.map { String(format: "%.0f", $0) } ?? "—")
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .minimumScaleFactor(0.65)
                Text("km").font(.system(size: 12, weight: .bold))
                Text(snapshot.batterySOC.map { String(format: "· %.0f%%", $0) } ?? "· —%")
                    .font(.system(size: 13, weight: .bold)).minimumScaleFactor(0.75)
            }
            .lineLimit(1).monospacedDigit()
            .accessibilityLabel("纯电续航 \(snapshot.electricRangeKM.map { String(format: "%.0f 公里", $0) } ?? "未知")，电量 \(snapshot.batterySOC.map { String(format: "百分之 %.0f", $0) } ?? "未知")")
            ZStack(alignment: .leading) {
                Capsule().fill(WidgetPalette.energy.opacity(0.12))
                if let soc = snapshot.batterySOC {
                    Capsule().fill(WidgetPalette.energy).frame(width: 110 * soc / 100)
                }
            }
            .frame(width: 110, height: 4.5)
            .accessibilityHidden(true)
        }
    }

    private func charging(_ snapshot: WidgetSnapshot) -> some View {
        Label(snapshot.chargingState?.title ?? "充电状态未知",
              systemImage: snapshot.chargingState == .fault ? "exclamationmark.triangle" : "bolt")
            .font(.caption).lineLimit(1).minimumScaleFactor(0.8)
            .foregroundStyle(snapshot.chargingState == .fault ? WidgetPalette.danger(scheme) : .primary)
    }

    private var freshness: some View {
        Label(statusTitle, systemImage: presentation.refreshFailed ? "exclamationmark.circle" : "clock")
            .font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.8)
    }

    private func timestamp(_ snapshot: WidgetSnapshot) -> some View {
        HStack(spacing: 3) {
            Text("采样")
            if let timestamp = snapshot.sourceTimestamp { Text(timestamp, style: .time) }
        }
        .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
    }

    private var unavailable: some View {
        VStack(alignment: .leading, spacing: 7) {
            Label(statusTitle, systemImage: emptySymbol).font(.subheadline.weight(.semibold))
            Text(emptyDetail).font(.caption).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            if presentation.refreshFailed {
                Text("上次更新失败").font(.caption2).foregroundStyle(.secondary)
            }
        }
        .frame(maxHeight: .infinity, alignment: .center)
    }

    private var statusTitle: String {
        switch presentation.state {
        case .unconfigured: return "尚未配置"
        case .waiting: return "等待车况"
        case .recent: return presentation.refreshFailed ? "更新失败 · 历史缓存" : "最近缓存"
        case .stale: return presentation.refreshFailed ? "更新失败 · 缓存较旧" : "缓存较旧"
        case .expired: return "缓存已过期"
        case .unknownTimestamp: return "采样时间未知"
        case .unavailable: return "缓存暂不可用"
        }
    }

    private var emptySymbol: String {
        switch presentation.state {
        case .unconfigured: return "car.side"
        case .expired: return "clock.badge.exclamationmark"
        case .unavailable: return "lock"
        default: return "questionmark.circle"
        }
    }

    private var emptyDetail: String {
        switch presentation.state {
        case .unconfigured: return "打开 App 导入会话，或体验离线演示。"
        case .waiting: return "打开 App 更新车况。"
        case .expired: return "已隐藏旧数值，请打开 App 更新。"
        case .unknownTimestamp: return "无法确认数据时间，请打开 App 更新。"
        case .unavailable: return "请解锁设备并打开 App 检查。"
        default: return "点击打开 App。"
        }
    }
}

private enum WidgetPalette {
    static let accent = Color(red: 0, green: 0.40, blue: 1)
    static let energy = Color(red: 0, green: 0.62, blue: 0.30)
    static func danger(_ scheme: ColorScheme) -> Color {
        scheme == .dark
            ? Color(red: 1, green: 0.42, blue: 0.38)
            : Color(red: 0.70, green: 0.14, blue: 0.09)
    }
}

/// Lightweight diagonal material and three-stop rim, matching the app's glass
/// design without blur/images. System widget margins remain enabled.
private struct WidgetGlassBackground: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    private var surface: [Color] {
        scheme == .dark
            ? [Color(red: 0.15, green: 0.18, blue: 0.24), Color(red: 0.095, green: 0.12, blue: 0.17), Color(red: 0.08, green: 0.10, blue: 0.14)]
            : [.white, Color(red: 0.965, green: 0.973, blue: 0.988), .white]
    }

    var body: some View {
        RoundedRectangle(cornerRadius: 20, style: .continuous)
            .fill(LinearGradient(colors: surface, startPoint: .topLeading, endPoint: .bottomTrailing))
            .overlay {
                if !reduceTransparency {
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(RadialGradient(colors: [WidgetPalette.energy.opacity(0.10), .clear],
                                             center: .topLeading, startRadius: 0, endRadius: 220))
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(LinearGradient(colors: [.white.opacity(scheme == .dark ? 0.3 : 0.9),
                                                          .white.opacity(scheme == .dark ? 0.12 : 0.45),
                                                          .white.opacity(scheme == .dark ? 0.05 : 0.2)],
                                                 startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 0.8)
            }
    }
}
