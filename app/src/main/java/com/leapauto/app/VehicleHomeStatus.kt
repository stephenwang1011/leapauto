package com.leapauto.app

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

    fun lockButtonPresentation(locked: Boolean?): LockButtonPresentation =
        LockButtonPresentation(
            unlockActive = locked == false,
            lockActive = locked == true
        )

    fun windowWarningVisible(available: Boolean, openWindows: List<String>): Boolean =
        available && openWindows.isNotEmpty()

    fun windowSummary(available: Boolean, openWindows: List<String>): String =
        if (windowWarningVisible(available, openWindows)) "车窗未关闭" else ""

    /** 1204/soc is the confirmed source; retain 100003 as a compatibility fallback. */
    fun resolvedSoc(preciseSoc: String?, soc: String?): String? =
        listOf(soc, preciseSoc).firstOrNull { VehicleStatusMapper.displayPreciseSoc(it) != null }

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
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (updatedAtEpochMs <= 0L) return "状态更新时间待同步"
        val updated = Instant.ofEpochMilli(updatedAtEpochMs).atZone(zoneId)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val prefix = when (updated.toLocalDate()) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> updated.format(DateTimeFormatter.ofPattern("M/d", Locale.CHINA))
        }
        return "状态更新 $prefix ${updated.format(TIME_FORMATTER)}"
    }

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
