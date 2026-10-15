import Foundation

public struct CommandPayload: Equatable, Sendable {
    public let commandID: String
    public let state: [String: JSONValue]
    public let stateJSON: String
    public var cmdID: String { commandID }

    public init(commandID: String, state: [String: JSONValue]) throws {
        self.commandID = commandID
        self.state = state
        stateJSON = String(decoding: try JSONValue.object(state).encodedData(), as: UTF8.self)
    }
}

public enum CommandValidationError: Error, Equatable, LocalizedError, Sendable {
    case invalidTemperature
    case noVehicleStatus
    case staleOrUnknownStatusTime
    case vehicleMoving
    case unknownDrivingState

    public var errorDescription: String? {
        switch self {
        case .invalidTemperature: return "空调温度需在 16–32°C 之间。"
        case .noVehicleStatus: return "请先刷新车况，确认车辆已停稳。"
        case .staleOrUnknownStatusTime: return "车况时间缺失或已过期，请刷新后再操作。"
        case .vehicleMoving: return "车辆正在行驶或处于前进／倒车挡，请停车后再操作。"
        case .unknownDrivingState: return "尚不能确认车辆已停稳并挂 P 挡，暂不能远程控制。"
        }
    }
}

/// Presets ported from Android Commands.build/buildAc at the repository commit.
/// Building a payload never sends it; dispatch also requires PIN + explicit UI
/// confirmation and a fresh, positively parked telemetry snapshot.
public enum LeapCommand: Hashable, Identifiable, Sendable {
    case lock, unlock, trunkOpen, trunkClose, frunkOpen, frunkClose
    case windowOpen, windowVent, windowClose, sunshadeOpen, sunshadeClose
    case sunroofOpen, sunroofClose, horn, batteryPreheat, batteryPreheatOff
    case acOn, acOff, quickCool, quickHeat, defrost, deodorize
    case startCharging, stopCharging, unlockCharger
    case setACTemperature(Int)

    public var id: String {
        switch self {
        case .setACTemperature(let temperature): return "setACTemperature_\(temperature)"
        default: return String(describing: self)
        }
    }

    public var title: String {
        switch self {
        case .lock: return "上锁"
        case .unlock: return "解锁"
        case .trunkOpen: return "打开后备箱"
        case .trunkClose: return "关闭后备箱"
        case .frunkOpen: return "打开前备箱"
        case .frunkClose: return "关闭前备箱"
        case .windowOpen: return "车窗半开"
        case .windowVent: return "车窗通风"
        case .windowClose: return "车窗全关"
        case .sunshadeOpen: return "打开遮阳帘"
        case .sunshadeClose: return "关闭遮阳帘"
        case .sunroofOpen: return "打开天窗"
        case .sunroofClose: return "关闭天窗"
        case .horn: return "鸣笛寻车"
        case .batteryPreheat: return "开启电池预热"
        case .batteryPreheatOff: return "关闭电池预热"
        case .acOn: return "开启空调"
        case .acOff: return "关闭空调"
        case .quickCool: return "极速降温"
        case .quickHeat: return "极速升温"
        case .defrost: return "前挡除雾"
        case .deodorize: return "快速除味"
        case .startCharging: return "开始充电"
        case .stopCharging: return "停止充电"
        case .unlockCharger: return "解锁充电枪"
        case .setACTemperature(let temperature): return "设置空调 \(temperature)°C"
        }
    }

    /// The UI adds device-owner authentication to access-opening operations.
    /// All other commands still require PIN and explicit user confirmation.
    public var isSensitive: Bool {
        switch self {
        case .unlock, .trunkOpen, .frunkOpen, .windowOpen, .windowVent, .sunroofOpen: return true
        default: return false
        }
    }

    public var isClimate: Bool {
        switch self {
        case .acOn, .acOff, .quickCool, .quickHeat, .defrost, .deodorize, .setACTemperature: return true
        default: return false
        }
    }

    public func validate(status: VehicleStatus?, now: Date = Date()) throws {
        if case .setACTemperature(let temperature) = self, !(16...32).contains(temperature) {
            throw CommandValidationError.invalidTemperature
        }
        guard let status else { throw CommandValidationError.noVehicleStatus }
        guard status.isFreshForControl(at: now) else { throw CommandValidationError.staleOrUnknownStatusTime }
        switch status.drivingState {
        case .parked: break
        case .moving: throw CommandValidationError.vehicleMoving
        case .unknown: throw CommandValidationError.unknownDrivingState
        }
    }

    public func build() throws -> CommandPayload {
        func simple(_ commandID: String, _ value: String) throws -> CommandPayload {
            try CommandPayload(commandID: commandID, state: ["value": .string(value)])
        }
        func climate(operate: String, temperature: Int, wind: Int, mode: String, circle: String = "in", windshield: String = "1", position: String = "all") throws -> CommandPayload {
            try CommandPayload(commandID: "170", state: [
                "operate": .string(operate), "temperature": .string(String(temperature)),
                "windlevel": .string(String(wind)), "mode": .string(mode),
                "circle": .string(circle), "wshld": .string(windshield), "position": .string(position)
            ])
        }
        switch self {
        case .lock: return try simple("110", "lock")
        case .unlock: return try simple("110", "unlock")
        case .trunkOpen: return try simple("130", "true")
        case .trunkClose: return try simple("130", "false")
        case .frunkOpen: return try simple("131", "100")
        case .frunkClose: return try simple("131", "0")
        case .windowOpen: return try simple("230", "5")
        case .windowVent: return try simple("230", "2")
        case .windowClose: return try simple("230", "0")
        case .sunshadeOpen: return try simple("240", "10")
        case .sunshadeClose: return try simple("240", "0")
        case .sunroofOpen: return try simple("300", "1")
        case .sunroofClose: return try simple("300", "0")
        case .horn: return try simple("120", "true")
        case .batteryPreheat: return try simple("160", "ptcon")
        case .batteryPreheatOff: return try simple("160", "ptcoff")
        case .startCharging: return try simple("193", "start")
        case .stopCharging: return try simple("193", "stop")
        case .unlockCharger: return try CommandPayload(commandID: "192", state: ["operation": .string("unlock")])
        case .acOn: return try climate(operate: "auto", temperature: 24, wind: 3, mode: "cold")
        case .acOff: return try climate(operate: "off", temperature: 24, wind: 3, mode: "nohotcold")
        // Current Kotlin executable presets use wshld=1 here. API/API.md's
        // older quickCool/quickHeat table says 0; port the executable behavior.
        case .quickCool: return try climate(operate: "manual", temperature: 18, wind: 7, mode: "cold")
        case .quickHeat: return try climate(operate: "manual", temperature: 32, wind: 7, mode: "hot")
        case .defrost: return try climate(operate: "manual", temperature: 24, wind: 5, mode: "cold", circle: "out", position: "wshld")
        case .deodorize: return try climate(operate: "manual", temperature: 24, wind: 7, mode: "nohotcold", circle: "out", windshield: "0")
        case .setACTemperature(let temperature):
            guard (16...32).contains(temperature) else { throw CommandValidationError.invalidTemperature }
            return try climate(operate: "manual", temperature: temperature, wind: 7, mode: temperature <= 26 ? "cold" : "hot", windshield: "0")
        }
    }
}
