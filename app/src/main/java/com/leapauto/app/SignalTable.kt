package com.leapauto.app

import org.json.JSONObject

/**
 * 数字信号 ID → 命名字段 对照表。
 * 来源：已验证车况信号与用户提供的车辆样本；续航/百分比字段按当前确认口径维护。
 * C16 车况接口返回的 signalMap 键是数字 ID（如 "1182":29），需用此表解码。
 */
object SignalTable {

    private const val VERIFIED_LONGITUDE_SIGNAL = "2"
    private const val VERIFIED_LATITUDE_SIGNAL = "3"

    val MAP: Map<String, String> = mapOf(
        // 电池 / 充电
        "47" to "acInputSlowCharge",
        "1204" to "soc",
        "100003" to "preciseSoc",
        "3235" to "fuelSoc",
        "1200" to "chargeRemainTime",
        "1178" to "batteryCurrent",
        "1177" to "batteryVoltage",
        "1197" to "dcInputFastCharge",
        "1149" to "chargeState",
        "1182" to "minBatteryTemp",
        "1186" to "batteryThermalRequest",
        "3736" to "chargeCompleted",
        "48" to "healthyChargeEnabled",
        "3737" to "chargeScheduleCancelledOnce",
        // 续航（用户确认：以下六个信号都是对应模式下的剩余续航值）
        "3260" to "expectedMileage",
        "2188" to "liveRemainingRange",
        "3257" to "electricRangeStandard",
        "3262" to "rangeMode",
        "3256" to "fuelRangeStandard",
        "3258" to "combinedRangeStandard",
        "3259" to "fuelRangeDynamic",
        "3261" to "combinedRangeDynamic",
        // 行驶
        "1319" to "speed",
        "1318" to "totalMileage",
        "1010" to "gearStatus",
        "1944" to "vehicleState",
        "1480" to "parkingBrakeState",
        "6048" to "speedLimit",
        "6047" to "speedLimitUnit",
        "12054" to "speedLimitActive",
        // 位置
        "3725" to "latitude",
        "3724" to "longitude",
        // 空调
        "1938" to "acSwitch",
        "2183" to "acSetting",
        "2184" to "acSettingRight",
        "1349" to "interiorTemp",
        "1943" to "recirculationMode",
        "1945" to "windshieldDefrost",
        "1946" to "rearWindowHeating",
        "3713" to "climateMode",
        "2669" to "rapidCooling",
        "2681" to "rapidHeating",
        "1939" to "acOperateMode",
        "1941" to "acAirVolume",
        // 车窗（开关布尔）
        "1693" to "driverWindowStatus",
        "1694" to "rightFrontWindowStatus",
        "1695" to "leftRearWindowStatus",
        "1696" to "rightRearWindowStatus",
        // 车门 / 门锁 / 前后舱
        "1298" to "driverDoorLockStatus",
        "1277" to "lbcmDriverDoorStatus",
        "1278" to "rbcmDriverDoorStatus",
        "1279" to "lbcmLeftRearDoorStatus",
        "1280" to "rbcmRightRearDoorStatus",
        "1281" to "bbcmBackDoorStatus",
        "1282" to "hoodStatus",
        "1276" to "fbcmHoodStatus",
        // 胎压
        "2646" to "leftFrontTirePressure",
        "2653" to "rightFrontTirePressure",
        // C16 实车核验：2667 与 2646 分别对应右后与左前
        "2667" to "rightRearTirePressure",
        "2660" to "leftRearTirePressure",
        "2641" to "leftFrontTirePressureState",
        "2648" to "rightFrontTirePressureState",
        "2655" to "rightRearTirePressureState",
        "2662" to "leftRearTirePressureState",
        // 电源档位
        "1256" to "bcmKeyPositionOn1",
        "1257" to "bcmKeyPositionOn2",
        "1258" to "bcmKeyPositionOn3",
        // 座椅舒适（实车抓包核验）
        "2100" to "driverSeatHeating",
        "2101" to "driverSeatVentilation",
        "2118" to "passengerSeatHeating",
        "2119" to "passengerSeatVentilation",
        "1879" to "leftRearSeatHeating",
        "3727" to "leftRearSeatVentilation",
        "1880" to "rightRearSeatHeating",
        "3728" to "rightRearSeatVentilation",
        "1816" to "steeringWheelHeating",
        "1624" to "steeringWheelHeaterMinutes",
        // 安防 / 外设
        "1255" to "vehicleSecurityActive",
        "3636" to "sentryMode",
        "49" to "leftMirrorHeating",
        "50" to "rightMirrorHeating",
        "1724" to "roofOpening",
        // 车载冰箱（C16 实车核验）
        "10707" to "fridgeTargetTemp",
        "10708" to "fridgeMode",
        "10709" to "fridgeSwitch",
        "10711" to "fridgeStyle",
        "10712" to "fridgeFault",
        "11189" to "fridgeParkDurationHours",
        "11190" to "fridgeParkSwitch",
        "11191" to "fridgeParkCycles",
        "11260" to "fridgeParkEndTime"
    )

    /**
     * 把数字信号 map 解码为命名字段。
     * 命名字段（如 sts/vin）直接透传；数字 ID 查表转换；已存在的命名字段优先。
     */
    fun decode(signalMap: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = signalMap.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = signalMap.opt(key)
            val name = MAP[key]
            if (name != null) {
                if (!out.has(name)) out.put(name, value)
            } else if (!key.all { it.isDigit() } ||
                key == VERIFIED_LONGITUDE_SIGNAL || key == VERIFIED_LATITUDE_SIGNAL
            ) {
                out.put(key, value)
            }
        }
        return out
    }
}
