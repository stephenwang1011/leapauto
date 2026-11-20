// [FEATURE: TRIP_LOG] - 零跑全系车型官方标称动力电池容量配置表
package com.leapauto.app.trip

/**
 * 零跑汽车全系车型官方标称动力电池容量标准库 (单位: kWh)
 * 来源：零跑官方各车型规格配置表（涵盖纯电与增程各大版型）
 */
object VehicleBatteryCapacityTable {

    fun resolve(carType: String, carTypeCode: String = ""): Double {
        val type = carType.trim().uppercase()
        val code = carTypeCode.trim().uppercase()
        val isReev = type.contains("增程") || type.contains("REV") || type.contains("EREV") ||
            code.contains("增程") || code.contains("REV") || code.contains("EREV")

        return when {
            // C16 (中大型 6座 SUV)
            type.contains("C16") -> when {
                isReev || code.contains("280") -> 28.4  // C16 增程 280版 (28.4 kWh)
                code.contains("81") || code.contains("82") -> 81.9 // C16 超长续航定制版
                else -> 67.7 // C16 纯电 630 / 520 智尊/尊享标准版 (67.7 kWh)
            }
            // C10 (中型 5座 SUV)
            type.contains("C10") -> when {
                isReev || code.contains("210") -> 28.4  // C10 增程 210版 (28.4 kWh)
                code.contains("410") -> 52.9           // C10 纯电 410舒享 (52.9 kWh)
                else -> 69.9                           // C10 纯电 530智享/智尊 (69.9 kWh)
            }
            // C11 (中型 SUV)
            type.contains("C11") -> when {
                isReev -> when {
                    code.contains("180") -> 30.1       // C11 增程 180舒享 (30.1 kWh)
                    else -> 43.7                      // C11 增程 285 / 300智享/尊享 (43.7 kWh)
                }
                code.contains("650") || code.contains("四驱") || code.contains("高性能") -> 89.55 // C11 纯电 650三元锂 (89.55 kWh)
                code.contains("510") || code.contains("515") -> 69.2                           // C11 纯电 510/515 (69.2 kWh)
                code.contains("81") || code.contains("82") -> 81.9
                else -> 78.5                                                                 // C11 纯电 580主流智享版 (78.5 kWh)
            }
            // C01 (中大型轿车)
            type.contains("C01") -> when {
                isReev -> when {
                    code.contains("216") -> 30.1       // C01 增程 216舒享 (30.1 kWh)
                    else -> 43.7                      // C01 增程 316智享/尊享 (43.7 kWh)
                }
                code.contains("717") || code.contains("四驱") -> 89.55 // C01 纯电 717 (89.55 kWh)
                code.contains("525") -> 62.8                           // C01 纯电 525 (62.8 kWh)
                else -> 78.5                                         // C01 纯电 625 (78.5 kWh)
            }
            // T03 (微型纯电小车)
            type.contains("T03") -> when {
                code.contains("200") -> 21.6           // T03 200轻享 (21.6 kWh)
                code.contains("310") -> 31.9           // T03 310舒享 (31.9 kWh)
                else -> 41.3                           // T03 403智享/豪华 (41.3 kWh)
            }
            // B10 (2025/2026款全新紧凑型 SUV)
            type.contains("B10") -> when {
                isReev -> 28.4
                code.contains("600") -> 67.2
                else -> 54.5
            }
            // S01 (纯电轿跑)
            type.contains("S01") -> when {
                code.contains("460") -> 48.0
                else -> 35.6
            }
            else -> 67.7
        }
    }

    fun isReev(carType: String, carTypeCode: String = ""): Boolean {
        val type = carType.trim().uppercase()
        val code = carTypeCode.trim().uppercase()
        return type.contains("增程") || type.contains("REV") || type.contains("EREV") ||
            code.contains("增程") || code.contains("REV") || code.contains("EREV")
    }

    /**
     * 零跑增程车型官方标称油箱容积 (单位: 升 L)
     * - C16 增程版: 50.0 L
     * - C10 增程版: 50.0 L
     * - C11 增程版: 47.0 L
     * - C01 增程版: 47.0 L
     * - 纯电车型: 0.0 L
     */
    fun resolveFuelTankCapacityLiters(carType: String, carTypeCode: String = ""): Double {
        val type = carType.trim().uppercase()
        val code = carTypeCode.trim().uppercase()
        if (!isReev(carType, carTypeCode)) return 0.0

        return when {
            type.contains("C16") -> 50.0
            type.contains("C10") -> 50.0
            type.contains("C11") -> 47.0
            type.contains("C01") -> 47.0
            type.contains("B10") -> 50.0
            else -> 50.0
        }
    }
}
