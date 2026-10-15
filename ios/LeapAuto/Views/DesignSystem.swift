import SwiftUI
import UIKit
import LeapCore

enum LeapStyle {
    static let accent = Color(red: 0, green: 0.40, blue: 1)
    static let energy = Color(red: 0, green: 0.62, blue: 0.30)
    static let warning = Color.orange
    static let danger = Color(red: 1, green: 0.23, blue: 0.19)
    // The brand warning red remains an aura/icon color. Body-sized warning
    // text uses a deeper light-mode red to preserve readable contrast.
    static let dangerText = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 1, green: 0.42, blue: 0.38, alpha: 1)
            : UIColor(red: 0.70, green: 0.14, blue: 0.09, alpha: 1)
    })

    static func canvas(_ scheme: ColorScheme) -> Color {
        scheme == .dark
            ? Color(red: 0.071, green: 0.086, blue: 0.122)
            : Color(red: 0.953, green: 0.957, blue: 0.965)
    }
}

struct PageCanvas: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        LeapStyle.canvas(scheme).ignoresSafeArea()
    }
}

/// A lightweight vector material: diagonal gradient, restrained aura and a
/// three-stop specular rim. No blur, image assets, or offscreen effects.
struct GlassCard<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    let aura: Color
    let content: Content

    init(aura: Color = .clear, @ViewBuilder content: () -> Content) {
        self.aura = aura
        self.content = content()
    }

    private var surface: [Color] {
        if scheme == .dark {
            return [Color(red: 0.133, green: 0.161, blue: 0.220),
                    Color(red: 0.094, green: 0.118, blue: 0.165),
                    Color(red: 0.075, green: 0.090, blue: 0.133)]
        }
        return [.white, Color(red: 0.965, green: 0.973, blue: 0.988), .white]
    }

    var body: some View {
        content
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(LinearGradient(colors: surface, startPoint: .topLeading, endPoint: .bottomTrailing))
                    .overlay {
                        if !reduceTransparency {
                            RoundedRectangle(cornerRadius: 22, style: .continuous)
                                .fill(RadialGradient(colors: [aura.opacity(0.12), .clear], center: .topLeading, startRadius: 0, endRadius: 280))
                        }
                    }
            }
            .overlay {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(LinearGradient(
                        colors: [.white.opacity(scheme == .dark ? 0.30 : 0.95),
                                 .white.opacity(scheme == .dark ? 0.11 : 0.44),
                                 .white.opacity(scheme == .dark ? 0.05 : 0.22)],
                        startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 0.8)
                    .allowsHitTesting(false)
            }
    }
}

struct SectionHeading: View {
    let title: String
    var caption: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.headline)
            if let caption { Text(caption).font(.subheadline).foregroundStyle(.secondary) }
        }
        .accessibilityElement(children: .combine)
    }
}

struct StatusPill: View {
    let title: String
    let symbol: String
    var tint: Color = LeapStyle.accent

    var body: some View {
        Label(title, systemImage: symbol)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.primary)
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(tint.opacity(0.12), in: Capsule())
            .accessibilityElement(children: .combine)
    }
}

struct MetricRow: View {
    let title: String
    let value: String
    var symbol: String? = nil

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 18) {
                titleView
                Spacer(minLength: 8)
                Text(value).multilineTextAlignment(.trailing).monospacedDigit()
            }
            VStack(alignment: .leading, spacing: 7) {
                titleView
                Text(value).monospacedDigit()
            }
        }
        .font(.body)
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
    }

    private var titleView: some View {
        Group {
            if let symbol { Label(title, systemImage: symbol) }
            else { Text(title) }
        }
        .foregroundStyle(.secondary)
    }
}

struct CommandButton: View {
    @EnvironmentObject private var model: AppModel
    let command: LeapCommand
    let symbol: String
    let identifier: String

    var body: some View {
        Button {
            model.request(command)
        } label: {
            VStack(spacing: 12) {
                Image(systemName: symbol).font(.title2.weight(.medium))
                    .foregroundStyle(LeapStyle.accent)
                    .accessibilityHidden(true)
                Text(command.title).font(.subheadline.weight(.medium))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.primary)
            }
            .padding(.vertical, 18)
            .padding(.horizontal, 6)
            .frame(maxWidth: .infinity, minHeight: 88)
            .background(LeapStyle.accent.opacity(0.055), in: RoundedRectangle(cornerRadius: 16))
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .disabled(!model.canControl || model.isCommandRunning || model.isBusy)
        .opacity(model.canControl ? 1 : 0.5)
        .accessibilityLabel(command.title)
        .accessibilityHint("打开操作确认，需要四位操作密码")
        .accessibilityIdentifier(identifier)
    }
}

struct UnavailableFeature: View {
    let title: String
    let detail: String
    let symbol: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol).font(.title3).frame(width: 26).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 5) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Text("暂不可用").font(.caption).foregroundStyle(.secondary)
        }
        .padding(.vertical, 9)
        .accessibilityElement(children: .combine)
    }
}

enum TelemetryText {
    static func number(_ value: Double?, decimals: Int = 0, unit: String = "") -> String {
        guard let value, value.isFinite else { return "未知" }
        return value.formatted(.number.precision(.fractionLength(decimals))) + (unit.isEmpty ? "" : " " + unit)
    }

    static func flag(_ value: Bool?, yes: String, no: String) -> String {
        guard let value else { return "未知" }
        return value ? yes : no
    }

    static func driving(_ state: DrivingState?) -> String {
        switch state {
        case .parked: return "已驻车"
        case .moving: return "行驶中"
        case .unknown, .none: return "未知"
        }
    }

    static func freshness(_ timestamp: Date?, now: Date) -> String {
        guard let timestamp else { return "源时间未知" }
        let age = now.timeIntervalSince(timestamp)
        guard age >= 0 else { return "源时间异常" }
        if age <= 300 { return "5 分钟内更新" }
        if age <= 1_800 { return "数据较旧，请刷新" }
        return "数据已过期，请刷新"
    }
}

struct TelemetryFreshnessView: View {
    let status: VehicleStatus?

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { context in
            VStack(alignment: .leading, spacing: 5) {
                Label(TelemetryText.freshness(status?.sourceTimestamp, now: context.date), systemImage: "clock")
                    .font(.caption.weight(.medium))
                if let timestamp = status?.sourceTimestamp {
                    Text("车辆上报：\(timestamp.formatted(date: .abbreviated, time: .standard))")
                        .font(.caption2)
                }
                if let received = status?.receivedAt {
                    Text("本机接收：\(received.formatted(date: .abbreviated, time: .standard))")
                        .font(.caption2)
                }
            }
            .foregroundStyle(.secondary)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("telemetry.freshness")
        }
    }
}

struct DetailPage<Content: View>: View {
    let title: String
    let content: Content

    init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) { content }
                .padding(20)
                .frame(maxWidth: 680)
                .frame(maxWidth: .infinity)
        }
        .background { PageCanvas() }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}
