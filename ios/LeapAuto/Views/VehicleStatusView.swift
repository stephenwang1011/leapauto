import SwiftUI
import LeapCore

struct VehicleStatusView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        DetailPage("车况") {
            GlassCard(aura: LeapStyle.accent) {
                VStack(alignment: .leading, spacing: 8) {
                    SectionHeading(title: "车辆状态", caption: "以车辆最近一次上报为准")
                    MetricRow(title: "车门锁", value: TelemetryText.flag(model.status?.locked, yes: "已锁车", no: "未锁车"), symbol: "lock")
                    Divider()
                    MetricRow(title: "空调", value: TelemetryText.flag(model.status?.acOn, yes: "已开启", no: "已关闭"), symbol: "fan")
                    Divider()
                    MetricRow(title: "行驶状态", value: TelemetryText.driving(model.status?.drivingState), symbol: "steeringwheel")
                    Divider()
                    MetricRow(title: "车速", value: TelemetryText.number(model.status?.speed, unit: "km/h"), symbol: "speedometer")
                    Divider()
                    MetricRow(title: "总里程", value: TelemetryText.number(model.status?.odometer, decimals: 1, unit: "km"), symbol: "road.lanes")
                    Divider()
                    MetricRow(title: "车内温度", value: TelemetryText.number(model.status?.cabinTemperature, decimals: 1, unit: "°C"), symbol: "thermometer.medium")
                }
            }
            GlassCard {
                VStack(alignment: .leading, spacing: 18) {
                    SectionHeading(title: "四轮胎压", caption: "单位 bar · 报警状态来自车辆信号")
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: typeSize.isAccessibilitySize ? 200 : 120), spacing: 12)], spacing: 12) {
                        TirePressureTile(title: "左前轮", pressure: model.status?.tirePressures.leftFront, state: model.status?.tirePressureStates.leftFront)
                        TirePressureTile(title: "右前轮", pressure: model.status?.tirePressures.rightFront, state: model.status?.tirePressureStates.rightFront)
                        TirePressureTile(title: "左后轮", pressure: model.status?.tirePressures.leftRear, state: model.status?.tirePressureStates.leftRear)
                        TirePressureTile(title: "右后轮", pressure: model.status?.tirePressures.rightRear, state: model.status?.tirePressureStates.rightRear)
                    }
                    Text("缺少数值或报警信号时分别显示“未知”，不会据此判断轮胎正常。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            TelemetryFreshnessView(status: model.status)
        }
        .accessibilityIdentifier("screen.status")
        .refreshable { await model.refresh() }
    }
}

private struct TirePressureTile: View {
    let title: String
    let pressure: Double?
    let state: TirePressureState?

    private var statusTitle: String {
        switch state {
        case .normal: return "信号正常"
        case .low: return "低压警告"
        case .high: return "高压警告"
        case .none: return "报警状态未知"
        }
    }

    private var isWarning: Bool { state == .low || state == .high }

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(title).font(.subheadline).foregroundStyle(.secondary)
            Text(TelemetryText.number(pressure, decimals: 2, unit: "bar"))
                .font(.title3.bold()).monospacedDigit()
            Label(statusTitle, systemImage: isWarning ? "exclamationmark.triangle" : "info.circle")
                .font(.caption)
                .foregroundStyle(isWarning ? LeapStyle.dangerText : Color.secondary)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background((isWarning ? LeapStyle.warning : Color.secondary).opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .combine)
    }
}
