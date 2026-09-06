package com.leapauto.app

import java.util.Locale
import kotlin.math.abs
import org.json.JSONObject

/** Shared formatting and interpretation for the verified charging signals. */
object ChargeStatus {
    fun state(status: JSONObject): Int? = state(status.opt("chargeState"))

    fun state(values: Map<String, Any?>): Int? = state(values["chargeState"])

    fun label(state: Int?): String = when (state) {
        0 -> "未插枪"
        1 -> "充电中"
        2 -> "充电完成"
        3 -> "充电故障"
        4 -> "预约充电等待"
        6 -> "充电暂停"
        else -> "充电状态未知"
    }

    fun power(status: JSONObject): String? {
        return power(state(status), status.opt("batteryVoltage"), status.opt("batteryCurrent"))
    }

    fun power(values: Map<String, Any?>): String? =
        power(state(values), values["batteryVoltage"], values["batteryCurrent"])

    /** Returns the absolute battery electrical power for charging or driving displays. */
    fun electricalPower(voltageValue: Any?, currentValue: Any?): String? {
        val voltage = electricalValue(voltageValue, "V") ?: return null
        val current = electricalValue(currentValue, "A") ?: return null
        return String.format(Locale.US, "%.1f kW", abs(voltage * current) / 1000.0)
    }

    /**
     * Formats the charging current for display as a non-negative value.
     *
     * The vehicle reports charging current with a negative sign in some states
     * (for example, -8.299). The sign indicates direction and is not useful in
     * the user-facing current field, so only the display value is normalized.
     */
    fun displayCurrent(status: JSONObject): String? = displayCurrent(status.opt("batteryCurrent"))

    fun displayCurrent(values: Map<String, Any?>): String? = displayCurrent(values["batteryCurrent"])

    private fun displayCurrent(value: Any?): String? {
        if (value == null || value == JSONObject.NULL) return null
        val raw = value.toString().trim()
        if (raw.isBlank()) return null

        val numericText = raw.removeSuffix("A").trim()
        val numeric = numericText.toDoubleOrNull()
        if (numeric == null || !numeric.isFinite()) return numericText
        return if (numericText.startsWith("-")) numericText.removePrefix("-") else numericText
    }

    private fun power(state: Int?, voltageValue: Any?, currentValue: Any?): String? {
        if (state != 1) return null
        return electricalPower(voltageValue, currentValue)
    }

    private fun electricalValue(value: Any?, unit: String): Double? {
        if (value == null || value == JSONObject.NULL) return null
        return value
            .toString()
            .trim()
            .removeSuffix(unit)
            .trim()
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() }
    }

    fun remainingTime(value: Any?): String? {
        if (value == null || value == JSONObject.NULL) return null
        val raw = value.toString().trim()
        if (raw.isBlank()) return null
        val numericText = raw
            .removePrefix("约")
            .removeSuffix("分钟")
            .trim()
        val minutes = numericText.toDoubleOrNull()
        if (minutes == null || !minutes.isFinite()) return raw

        val roundedMinutes = String.format(Locale.CHINA, "%.0f", minutes).toIntOrNull() ?: return raw
        if (roundedMinutes < 0) return raw
        return when {
            roundedMinutes == 0 -> "已充满"
            roundedMinutes < 60 -> "${roundedMinutes}分钟"
            else -> "${roundedMinutes / 60}时${roundedMinutes % 60}分"
        }
    }

    fun type(status: JSONObject): String? = type(status.opt("dcInputFastCharge"))

    fun type(values: Map<String, Any?>): String? = type(values["dcInputFastCharge"])

    private fun type(value: Any?): String? = when (boolean(value)) {
        true -> "直流快充"
        false -> "交流充电"
        null -> null
    }

    /**
     * `chargeState=2` is the verified service-side charging-complete state.
     *
     * Some vehicle variants can expose a non-zero `chargeCompleted` field while
     * the reported charging state is still active. It is retained as decoded
     * telemetry, but must not independently produce a "full" notification.
     */
    fun completed(status: JSONObject): Boolean = state(status) == 2

    /**
     * Determines whether the traction battery is full for the configured power type.
     *
     * Range-extender vehicles expose fuel and combined range values in the 325x/326x
     * group, but those values are not charging-completion signals. The charging
     * notification must only follow the traction-battery state (1149/3736), just as
     * it does for pure-electric vehicles.
     */
    fun completed(status: JSONObject, powerType: VehicleStatusMapper.PowerType?): Boolean =
        when (powerType) {
            VehicleStatusMapper.PowerType.RANGE_EXTENDER,
            VehicleStatusMapper.PowerType.PURE_ELECTRIC,
            null -> completed(status)
        }

    fun completed(values: Map<String, Any?>): Boolean = state(values) == 2

    private fun state(value: Any?): Int? = value?.toString()?.toIntOrNull()

    private fun boolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
        else -> null
    }
}
