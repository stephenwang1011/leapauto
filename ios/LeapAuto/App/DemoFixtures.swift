import Foundation
import LeapCore

/// Synthetic offline data. No real account, VIN, coordinates or network access.
enum DemoFixtures {
    static let vehicle = Vehicle(vin: "DEMO-LOCAL-ONLY", modelName: "零跑 C16 · 演示", plateNumber: nil)

    static func status(now: Date = Date()) -> VehicleStatus {
        VehicleStatus(raw: .object(["data": .object(["signalMap": .object([
            "sts": .number(now.timeIntervalSince1970),
            "1204": .number(76), "3257": .number(202),
            "3235": .number(65), "3256": .number(485),
            "3258": .number(687), "3262": .number(0),
            "1298": .number(1), "1938": .number(0),
            "1349": .number(24), "2183": .number(24),
            "1318": .number(12860), "1319": .number(0),
            "1010": .string("P"), "1149": .number(0),
            "2646": .number(2.50), "2653": .number(2.50),
            "2660": .number(2.55), "2667": .number(2.55)
        ])])]), receivedAt: now)
    }
}
