import SwiftUI
import LeapCore

struct ChargingView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        DetailPage("充电与续航") {
            GlassCard(aura: LeapStyle.energy) {
                VStack(alignment: .leading, spacing: 16) {
                    SectionHeading(title: "续航与能量")
                    EnergyReadout(title: "综合续航", value: model.status?.combinedRange, unit: "km")
                    Divider()
                    MetricRow(title: "纯电续航", value: TelemetryText.number(model.status?.electricRange, unit: "km"), symbol: "bolt.fill")
                    MetricRow(title: "电池电量", value: TelemetryText.number(model.status?.batterySOC, decimals: 1, unit: "%"), symbol: "battery.75percent")
                    Divider()
                    MetricRow(title: "燃油续航", value: TelemetryText.number(model.status?.fuelRange, unit: "km"), symbol: "fuelpump")
                    MetricRow(title: "燃油余量", value: TelemetryText.number(model.status?.fuelSOC, decimals: 1, unit: "%"))
                    MetricRow(title: "续航模式", value: rangeMode)
                    Text("未上报或车型不支持的项目显示“未知”。续航仅作参考，实际受路况与环境影响。")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }

            GlassCard(aura: LeapStyle.energy) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(alignment: .firstTextBaseline) {
                        Text("充电状态").font(.headline)
                        Spacer(minLength: 10)
                        Text(model.status?.chargingState?.title ?? "未知")
                            .font(.subheadline.weight(.semibold))
                    }
                    .padding(.bottom, 8)
                    MetricRow(title: "充电功率", value: TelemetryText.number(model.status?.chargePowerKW, decimals: 1, unit: "kW"), symbol: "bolt.circle")
                    Divider()
                    MetricRow(title: "剩余时间", value: TelemetryText.number(model.status?.chargeRemainingMinutes, unit: "分钟"), symbol: "clock")
                    Divider()
                    MetricRow(title: "电池电压", value: TelemetryText.number(model.status?.batteryVoltage, decimals: 1, unit: "V"))
                    MetricRow(title: "电池电流", value: TelemetryText.number(model.status?.batteryCurrent, decimals: 1, unit: "A"))
                    MetricRow(title: "最低电池温度", value: TelemetryText.number(model.status?.minimumBatteryTemperature, decimals: 1, unit: "°C"))
                    Divider()
                    MetricRow(title: "直流快充信号", value: TelemetryText.flag(model.status?.fastCharging, yes: "是", no: "否"))
                    MetricRow(title: "健康充电开关", value: TelemetryText.flag(model.status?.healthyCharging, yes: "已开启", no: "已关闭"))
                    MetricRow(title: "充电完成", value: TelemetryText.flag(model.status?.chargeCompleted, yes: "是", no: "否"))
                    Text("功率仅在明确充电中且电压、电流齐全时计算。未知状态不会被视为“未充电”。")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }

            GlassCard {
                VStack(alignment: .leading, spacing: 16) {
                    SectionHeading(title: "充电控制", caption: "操作前请核对充电设备与车辆周围情况")
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: typeSize.isAccessibilitySize ? 160 : 120), spacing: 12)], spacing: 12) {
                        CommandButton(command: .startCharging, symbol: "bolt.fill", identifier: "charging.start")
                        CommandButton(command: .stopCharging, symbol: "stop.circle", identifier: "charging.stop")
                        CommandButton(command: .unlockCharger, symbol: "lock.open", identifier: "charging.unlock")
                    }
                    Text("车型与服务端可能不支持部分指令。每次操作均需确认，服务受理后仍需核对车辆结果。")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            TelemetryFreshnessView(status: model.status)
        }
        .accessibilityIdentifier("screen.charging")
        .refreshable { await model.refresh() }
    }

    private var rangeMode: String {
        switch model.status?.rangeMode {
        case 0: return "标准"
        case 1: return "动态"
        default: return "未知"
        }
    }
}
