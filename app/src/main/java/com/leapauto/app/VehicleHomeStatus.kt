package com.leapauto.app

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure presentation rules for the vehicle-home status summary. */
object VehicleHomeStatus {
    enum class SocBand { NORMAL, WARNING, CRITICAL }

    enum class IndoorTemperatureBand { UNKNOWN, COLD, COMFORTABLE, HOT }

    data class LockButtonPresentation(
        val unlockActive: Boolean,
        val lockActive: Boolean
    )

    data class DrivingPresentation(
        val label: String?,
        val speed: String? = null
    )

    data class WindowItemPresentation(
        val positionKey: String,
        val positionLabel: String,
        val roleLabel: String,
        val isOpen: Boolean,
        val percent: Int?,
        val statusText: String
    )

    fun lockButtonPresentation(locked: Boolean?): LockButtonPresentation =
        LockButtonPresentation(
            unlockActive = locked == false,
            lockActive = locked == true
        )

    fun windowWarningVisible(available: Boolean, openWindows: List<String>): Boolean =
        available && openWindows.isNotEmpty()

    fun windowSummary(available: Boolean, openWindows: List<String>): String =
        if (windowWarningVisible(available, openWindows)) "车窗未关闭" else ""

    fun hasAnyWindowOpen(
        available: Boolean,
        openWindows: List<String>,
        percents: List<Int?> = emptyList()
    ): Boolean {
        if (!available) return false
        if (openWindows.isNotEmpty()) return true
        return percents.any { it != null && it > 0 }
    }

    fun resolveWindowItem(
        positionKey: String,
        positionLabel: String,
        roleLabel: String,
        openWindows: List<String>,
        percent: Int?
    ): WindowItemPresentation {
        val isOpen = openWindows.contains(positionLabel) || (percent != null && percent > 0)
        val statusText = when {
            !isOpen -> "已完全关闭"
            percent != null && percent in 1..25 -> "微开通风 ${percent}%"
            percent != null && percent >= 100 -> "已全开 100%"
            percent != null && percent > 25 -> "已开启 ${percent}%"
            else -> "车窗未关"
        }
        return WindowItemPresentation(
            positionKey = positionKey,
            positionLabel = positionLabel,
            roleLabel = roleLabel,
            isOpen = isOpen,
            percent = percent,
            statusText = statusText
        )
    }

    fun roofOpeningSummary(percent: Int?): String = when {
        percent == null || percent <= 0 -> "天窗已关闭"
        percent in 1..25 -> "天窗微开 ${percent}%"
        else -> "天窗开启 ${percent}%"
    }

    /** preciseSoc (100003) is preferred for high-precision battery percentage; retain 1204/soc as fallback. */
    fun resolvedSoc(preciseSoc: String?, soc: String?): String? =
        listOf(preciseSoc, soc).firstOrNull { VehicleStatusMapper.displayPreciseSoc(it) != null }

    fun resolvedSocLabel(preciseSoc: String?, soc: String?): String =
        resolvedSoc(preciseSoc, soc)?.let { VehicleStatusMapper.displayPreciseSoc(it) } ?: "--"

    /** Applies the confirmed pure-electric percentage thresholds. */
    fun socBand(soc: String?): SocBand {
        if (VehicleStatusMapper.displayPreciseSoc(soc) == null) return SocBand.NORMAL
        val fraction = VehicleStatusMapper.socFraction(soc)
        return when {
            fraction <= 0.20f -> SocBand.CRITICAL
            fraction <= 0.40f -> SocBand.WARNING
            else -> SocBand.NORMAL
        }
    }

    fun lockSummary(locked: Boolean?): String = when (locked) {
        true -> "车门已锁"
        false -> "车辆未锁"
        null -> "门锁状态未知"
    }

    /** Preserves the established cabin-temperature thresholds across home surfaces. */
    fun indoorTemperatureBand(temperature: String?): IndoorTemperatureBand {
        val value = temperature
            ?.replace("°C", "", ignoreCase = true)
            ?.replace("℃", "", ignoreCase = true)
            ?.trim()
            ?.toDoubleOrNull()
            ?.takeIf(Double::isFinite)
            ?: return IndoorTemperatureBand.UNKNOWN
        return when {
            value < 16.0 -> IndoorTemperatureBand.COLD
            value <= 26.0 -> IndoorTemperatureBand.COMFORTABLE
            else -> IndoorTemperatureBand.HOT
        }
    }

    /** Shows movement only when a positive vehicle speed is explicitly available. */
    fun drivingPresentation(
        speed: String?,
        gearStatus: String?,
        locked: Boolean?
    ): DrivingPresentation {
        val speedValue = parseSpeed(speed)
        return when {
            speedValue != null && speedValue > 0.0 ->
                DrivingPresentation("行驶中", formatSpeed(speedValue))
            else -> DrivingPresentation(null)
        }
    }

    data class DetailedDrivingState(
        val label: String,
        val isMoving: Boolean = false
    )

    /**
     * 判断车辆是否处于已下电、已熄火或离车闭锁驻车状态：
     * 1. 车辆处于闭锁状态（locked == true）说明车主已锁车离开；
     * 2. BCM电源状态为OFF（bcmKeyPositionOn3 == 0 / false）；
     * 3. 整车状态为非运行中（vehicleState in 0, 1, 3）；
     * 4. 电子手刹/驻车制动已拉起（parkingBrakeState == 1）。
     */
    fun isVehicleShutDown(
        bcmKeyPositionOn3: Any? = null,
        vehicleState: Any? = null,
        parkingBrakeState: Any? = null,
        locked: Boolean? = null
    ): Boolean {
        if (locked == true) return true

        if (bcmKeyPositionOn3 != null && bcmKeyPositionOn3 != JSONObject.NULL) {
            val raw = bcmKeyPositionOn3.toString().trim()
            if (raw == "0" || raw.equals("false", ignoreCase = true)) {
                return true
            }
            if (raw == "1" || raw.equals("true", ignoreCase = true)) {
                return false
            }
        }

        if (vehicleState != null && vehicleState != JSONObject.NULL) {
            val state = vehicleState.toString().trim().toIntOrNull()
            if (state != null) {
                if (state in setOf(0, 1, 3)) return true
                if (state == 2) return false
            }
        }

        if (parkingBrakeState != null && parkingBrakeState != JSONObject.NULL) {
            val epb = parkingBrakeState.toString().trim()
            if (epb == "1" || epb.equals("true", ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    /**
     * 解析位置下方的详细行车挡位与速度状态：
     * - D挡：前进显示 "D挡 · 70km/h"，无速度显示 "D挡 · 0km/h"
     * - R挡：倒车显示 "R挡 · 4km/h"，无速度显示 "R挡 · 0km/h"
     * - N挡：上电且空挡显示 "N挡"；已下电/熄火时显示 "已驻车"
     * - P挡：显示 "已驻车"（不显示速度）
     */
    fun resolveDetailedDrivingState(
        gearStatus: String?,
        speed: String?,
        isDriving: Boolean? = null,
        isShutDown: Boolean = false
    ): DetailedDrivingState? {
        val speedValue = parseSpeed(speed)
        val speedText = if (speedValue != null && speedValue > 0.0) {
            "${if (speedValue % 1.0 == 0.0) speedValue.toInt().toString() else "%.1f".format(speedValue)}km/h"
        } else {
            "0km/h"
        }

        val normalizedGear = when (gearStatus?.trim()?.uppercase()) {
            "D", "D挡", "DRIVE", "前进", "3" -> "D"
            "R", "R挡", "REVERSE", "倒车", "1" -> "R"
            "N", "N挡", "NEUTRAL", "空挡", "2" -> "N"
            "P", "P挡", "PARK", "驻车", "停车", "0" -> "P"
            else -> null
        }

        return when (normalizedGear) {
            "D" -> DetailedDrivingState(
                label = "D挡 · $speedText",
                isMoving = speedValue != null && speedValue > 0.0
            )
            "R" -> DetailedDrivingState(
                label = "R挡 · $speedText",
                isMoving = speedValue != null && speedValue > 0.0
            )
            "N" -> DetailedDrivingState(
                label = if (isShutDown) "已驻车" else "N挡",
                isMoving = false
            )
            "P" -> DetailedDrivingState(
                label = "已驻车",
                isMoving = false
            )
            else -> {
                if (isDriving == true || (speedValue != null && speedValue > 0.0)) {
                    DetailedDrivingState(
                        label = "行驶中 · $speedText",
                        isMoving = true
                    )
                } else {
                    null
                }
            }
        }
    }

    /** Selects charging or driving battery power without inferring engine output. */
    fun powerSummary(
        chargeState: Int?,
        speed: String?,
        isDriving: Boolean?,
        batteryVoltage: String?,
        batteryCurrent: String?
    ): String {
        val speedValue = parseSpeed(speed)
        val power = ChargeStatus.electricalPower(batteryVoltage, batteryCurrent)

        return when {
            chargeState == 1 -> power ?: "待同步"
            speedValue != null && speedValue > 0.0 -> power ?: "待同步"
            else -> "未充电"
        }
    }

    fun sentrySummary(enabled: Boolean?): String = when (enabled) {
        true -> "哨兵已开"
        false -> "哨兵未开启"
        null -> "哨兵状态未知"
    }

    fun updatedLabel(
        updatedAtEpochMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        omitTodayPrefix: Boolean = false
    ): String {
        if (updatedAtEpochMs <= 0L) return "状态待同步"
        val updated = Instant.ofEpochMilli(updatedAtEpochMs).atZone(zoneId)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val timeStr = updated.format(TIME_FORMATTER)
        val prefix = when (updated.toLocalDate()) {
            today -> if (omitTodayPrefix) "" else "今天"
            today.minusDays(1) -> "昨天"
            else -> "${updated.format(DateTimeFormatter.ofPattern("M/d", Locale.CHINA))} "
        }
        return "${prefix}${timeStr}更新"
    }

    fun isStatusFresh(
        updatedAtEpochMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
        freshThresholdMs: Long = 30 * 60 * 1000L
    ): Boolean = updatedAtEpochMs > 0L && (nowEpochMs - updatedAtEpochMs) in 0..freshThresholdMs

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    private fun parseSpeed(value: String?): Double? = value
        ?.trim()
        ?.removeSuffix("km/h")
        ?.trim()
        ?.toDoubleOrNull()

    private fun formatSpeed(value: Double): String {
        val displayValue = if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", value)
        } else {
            String.format(Locale.US, "%.1f", value)
        }
        return "$displayValue km/h"
    }
}
