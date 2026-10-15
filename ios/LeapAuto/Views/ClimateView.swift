import SwiftUI
import LeapCore

struct ClimateView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var targetTemperature = 24.0
    @ScaledMetric(relativeTo: .largeTitle) private var temperatureSize: CGFloat = 48

    private var controlsDisabled: Bool { !model.canControl || model.isBusy || model.isCommandRunning }
    private var columns: [GridItem] {
        [GridItem(.adaptive(minimum: typeSize.isAccessibilitySize ? 150 : 120), spacing: 12)]
    }

    var body: some View {
        DetailPage("空调") {
            GlassCard(aura: LeapStyle.accent) {
                VStack(alignment: .leading, spacing: 10) {
                    SectionHeading(title: "车内环境")
                    MetricRow(title: "空调状态", value: TelemetryText.flag(model.status?.acOn, yes: "已开启", no: "已关闭"), symbol: "fan")
                    Divider()
                    MetricRow(title: "车内温度", value: TelemetryText.number(model.status?.cabinTemperature, decimals: 1, unit: "°C"))
                    Divider()
                    MetricRow(title: "车辆上报设定", value: TelemetryText.number(model.status?.acTargetTemperature, unit: "°C"))
                }
            }

            GlassCard(aura: LeapStyle.accent) {
                VStack(alignment: .leading, spacing: 20) {
                    SectionHeading(title: "本次温度设定", caption: "16–32°C · 确认发送后才会请求车辆调整")
                    HStack(alignment: .firstTextBaseline, spacing: 5) {
                        Text(Int(targetTemperature).formatted())
                            .font(.system(size: temperatureSize, weight: .light, design: .rounded))
                            .monospacedDigit()
                        Text("°C").font(.title2).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("待发送温度，\(Int(targetTemperature)) 摄氏度")
                    .accessibilityIdentifier("climate.temperature")
                    Slider(value: $targetTemperature, in: 16...32, step: 1) {
                        Text("目标温度")
                    } minimumValueLabel: {
                        Text("16°").font(.caption)
                    } maximumValueLabel: {
                        Text("32°").font(.caption)
                    }
                    .frame(minHeight: 44)
                    .accessibilityLabel("目标温度")
                    .accessibilityValue("\(Int(targetTemperature)) 摄氏度")
                    .accessibilityIdentifier("climate.slider")
                    Stepper("微调温度", value: $targetTemperature, in: 16...32, step: 1)
                        .frame(minHeight: 44)
                        .accessibilityIdentifier("climate.stepper")
                    Button {
                        model.request(.setACTemperature(Int(targetTemperature)))
                    } label: {
                        Label("确认温度设定", systemImage: "thermometer.medium")
                            .font(.headline)
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(controlsDisabled)
                    .accessibilityIdentifier("climate.confirmTemperature")
                    Text("此处为待发送值。空调是否执行与当前设定，以刷新后的车辆信号为准。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            GlassCard {
                VStack(alignment: .leading, spacing: 16) {
                    SectionHeading(title: "空调控制")
                    LazyVGrid(columns: columns, spacing: 12) {
                        CommandButton(command: .acOn, symbol: "power", identifier: "climate.on")
                        CommandButton(command: .acOff, symbol: "power.circle", identifier: "climate.off")
                        CommandButton(command: .quickCool, symbol: "snowflake", identifier: "climate.quickCool")
                        CommandButton(command: .quickHeat, symbol: "sun.max", identifier: "climate.quickHeat")
                        CommandButton(command: .defrost, symbol: "windshield.front.and.heat.waves", identifier: "climate.defrost")
                    }
                    if !model.canControl {
                        Text("控制暂不可用。请刷新并确认车辆已驻车，且车况在 5 分钟内更新。")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            TelemetryFreshnessView(status: model.status)
        }
        .accessibilityIdentifier("screen.climate")
        .onAppear {
            if let current = model.status?.acTargetTemperature, current.isFinite, (16...32).contains(current) {
                targetTemperature = current.rounded()
            }
        }
        .refreshable { await model.refresh() }
    }
}
