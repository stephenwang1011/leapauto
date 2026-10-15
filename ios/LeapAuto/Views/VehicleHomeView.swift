import SwiftUI
import LeapCore

struct VehicleHomeView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dynamicTypeSize) private var typeSize

    private var columns: [GridItem] {
        [GridItem(.adaptive(minimum: typeSize.isAccessibilitySize ? 145 : 90), spacing: 10)]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                vehicleSelector
                if model.selectedVehicle != nil {
                    vehicleHero
                    energySummary
                    quickControls
                    climateSummary
                    locationSummary
                    TelemetryFreshnessView(status: model.status)
                    Text("未收到的信号显示“未知”。车辆图标为示意，不代表具体车型外观。")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                } else {
                    ContentUnavailableView {
                        Label("暂无可用车辆", systemImage: "car.side")
                    } description: {
                        Text("请刷新车辆列表，或在“我的”中检查会话。")
                    } actions: {
                        Button("重新读取") { Task { await model.refresh() } }
                            .buttonStyle(.bordered)
                            .frame(minHeight: 44)
                            .disabled(model.isBusy)
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("vehicle.home")
        }
        .background { PageCanvas() }
        .navigationTitle("我的座驾")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { Task { await model.refresh() } } label: {
                    Group {
                        if model.isBusy { ProgressView() }
                        else { Image(systemName: "arrow.clockwise") }
                    }
                    .frame(width: 44, height: 44)
                }
                .disabled(model.isBusy || model.isCommandRunning)
                .accessibilityLabel("刷新车况")
                .accessibilityIdentifier("vehicle.refresh")
            }
        }
        .refreshable { await model.refresh() }
    }

    private var vehicleSelector: some View {
        Menu {
            ForEach(Array(model.vehicles.enumerated()), id: \.element.vin) { index, vehicle in
                Button {
                    Task { await model.selectVehicle(vehicle) }
                } label: {
                    if vehicle.vin == model.selectedVehicle?.vin {
                        Label(vehicleTitle(vehicle, index: index), systemImage: "checkmark")
                    } else {
                        Text(vehicleTitle(vehicle, index: index))
                    }
                }
            }
        } label: {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(model.selectedVehicle?.modelName ?? "选择车辆")
                        .font(.title2.bold())
                        .foregroundStyle(.primary)
                    Text(model.selectedVehicle?.plateNumber ?? "未提供车牌")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .disabled(model.vehicles.isEmpty || model.isBusy || model.isCommandRunning)
        .accessibilityLabel("选择车辆，\(model.selectedVehicle?.modelName ?? "尚未选择")")
        .accessibilityIdentifier("vehicle.selector")
    }

    private func vehicleTitle(_ vehicle: Vehicle, index: Int) -> String {
        [vehicle.modelName ?? "车辆 \(index + 1)", vehicle.plateNumber].compactMap { $0 }.joined(separator: " · ")
    }

    private var vehicleHero: some View {
        NavigationLink { VehicleStatusView() } label: {
            GlassCard(aura: LeapStyle.accent) {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Text("车辆概览").font(.headline)
                        Spacer()
                        Image(systemName: "arrow.up.right").font(.subheadline)
                    }
                    Image(systemName: "car.side")
                        .resizable()
                        .scaledToFit()
                        .foregroundStyle(LinearGradient(colors: [Color.primary.opacity(0.85), Color.primary.opacity(0.48)], startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(maxWidth: .infinity)
                        .frame(height: 112)
                        .padding(.vertical, 10)
                        .accessibilityHidden(true)
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 8) { vehiclePills }
                        VStack(alignment: .leading, spacing: 8) { vehiclePills }
                    }
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityHint("查看车辆状态与四轮胎压")
        .accessibilityIdentifier("vehicle.status")
    }

    @ViewBuilder private var vehiclePills: some View {
        StatusPill(title: TelemetryText.flag(model.status?.locked, yes: "已锁车", no: "未锁车"),
                   symbol: model.status?.locked == true ? "lock" : "lock.open",
                   tint: model.status?.locked == false ? LeapStyle.danger : LeapStyle.accent)
        StatusPill(title: TelemetryText.driving(model.status?.drivingState), symbol: "parkingsign.circle", tint: .secondary)
    }

    private var energySummary: some View {
        NavigationLink { ChargingView() } label: {
            GlassCard(aura: LeapStyle.energy) {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Label("充电与续航", systemImage: "bolt.battery").font(.headline)
                        Spacer()
                        Image(systemName: "chevron.right").font(.caption.weight(.semibold))
                    }
                    EnergyReadout(title: "综合续航", value: model.status?.combinedRange, unit: "km")
                    Divider()
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .top, spacing: 24) {
                            electricReadout
                            Spacer(minLength: 0)
                            fuelReadout
                        }
                        VStack(alignment: .leading, spacing: 18) {
                            electricReadout
                            fuelReadout
                        }
                    }
                    if let percent = model.status?.batterySOC, percent.isFinite, (0...100).contains(percent) {
                        ProgressView(value: percent, total: 100)
                            .tint(LeapStyle.energy)
                            .accessibilityLabel("电池电量")
                            .accessibilityValue(TelemetryText.number(percent, decimals: 1, unit: "%"))
                    }
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("vehicle.charging")
    }

    private var electricReadout: some View {
        VStack(alignment: .leading, spacing: 7) {
            Label("纯电", systemImage: "bolt.fill").font(.caption).foregroundStyle(.secondary)
            Text(TelemetryText.number(model.status?.electricRange, unit: "km")).font(.title3.bold()).monospacedDigit()
            Text("电量 \(TelemetryText.number(model.status?.batterySOC, decimals: 1, unit: "%"))")
                .font(.caption).foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    private var fuelReadout: some View {
        VStack(alignment: .leading, spacing: 7) {
            Label("燃油", systemImage: "fuelpump").font(.caption).foregroundStyle(.secondary)
            Text(TelemetryText.number(model.status?.fuelRange, unit: "km")).font(.title3.bold()).monospacedDigit()
            Text("油量 \(TelemetryText.number(model.status?.fuelSOC, decimals: 1, unit: "%"))")
                .font(.caption).foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    private var quickControls: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 16) {
                SectionHeading(title: "快捷控制", caption: "每次操作均需确认与四位操作密码")
                LazyVGrid(columns: columns, spacing: 10) {
                    CommandButton(command: .lock, symbol: "lock", identifier: "control.lock")
                    CommandButton(command: .unlock, symbol: "lock.open", identifier: "control.unlock")
                    CommandButton(command: .horn, symbol: "speaker.wave.2", identifier: "control.horn")
                    CommandButton(command: .windowVent, symbol: "wind", identifier: "control.windowVent")
                    CommandButton(command: .windowClose, symbol: "rectangle", identifier: "control.windowClose")
                    CommandButton(command: .trunkOpen, symbol: "car.side.rear.open", identifier: "control.trunkOpen")
                    CommandButton(command: .trunkClose, symbol: "car.side", identifier: "control.trunkClose")
                }
                if !model.canControl {
                    Label("需取得新鲜、完整的驻车状态后才能控车。请先刷新。", systemImage: "lock.shield")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("control.unavailable")
                }
            }
        }
    }

    private var climateSummary: some View {
        NavigationLink { ClimateView() } label: {
            GlassCard(aura: LeapStyle.accent) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Label("空调", systemImage: "fan").font(.headline)
                        Spacer()
                        Text(TelemetryText.flag(model.status?.acOn, yes: "已开启", no: "已关闭"))
                            .font(.subheadline).foregroundStyle(.secondary)
                        Image(systemName: "chevron.right").font(.caption.weight(.semibold))
                    }
                    MetricRow(title: "车内温度", value: TelemetryText.number(model.status?.cabinTemperature, decimals: 1, unit: "°C"))
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("vehicle.climate")
    }

    private var locationSummary: some View {
        NavigationLink { VehicleLocationView() } label: {
            GlassCard {
                HStack(spacing: 14) {
                    Image(systemName: "location.circle").font(.title)
                        .foregroundStyle(LeapStyle.accent)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 5) {
                        Text("车辆位置").font(.headline)
                        Text(model.status?.location == nil ? "暂无可用位置" : "查看位置数据时效")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right").font(.caption.weight(.semibold))
                }
                .frame(minHeight: 52)
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("vehicle.location")
    }
}

struct EnergyReadout: View {
    @ScaledMetric(relativeTo: .title2) private var numberSize: CGFloat = 26
    let title: String
    let value: Double?
    let unit: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline).foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(TelemetryText.number(value))
                    .font(.system(size: numberSize, weight: .bold, design: .rounded))
                    .monospacedDigit()
                if let value, value.isFinite {
                    Text(unit).font(.caption.bold()).foregroundStyle(.secondary)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}
