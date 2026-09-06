package com.leapauto.app

import org.json.JSONObject

/** Render state for the compact status line in the desktop widget. */
data class WidgetStatusPresentation(
    val label: String?,
    val showChargingIcon: Boolean = false
)

/** Maps raw vehicle signals to the single compact status line shown by the widget. */
object WidgetStatusMapper {
    fun label(status: JSONObject): String? = label { key -> status.opt(key) }

    fun label(status: JSONObject, carType: String?): String? =
        label(status)?.takeUnless {
            carType?.contains("T03", ignoreCase = true) == true && it == "车窗未关闭"
        }

    fun label(values: Map<String, Any?>): String? = label { key -> values[key] }

    /**
     * Builds the widget presentation from raw telemetry. Charging and charging
     * completion intentionally take precedence over the parked-window warning:
     * the warning is restored as soon as the vehicle reports unplugged.
     */
    fun presentation(status: JSONObject, carType: String? = null): WidgetStatusPresentation {
        val fallback = label(status, carType)
        return presentation(
            chargeState = ChargeStatus.state(status),
            chargeRemainTime = ChargeStatus.remainingTime(status.opt("chargeRemainTime")),
            fallbackLabel = fallback
        )
    }

    /** Snapshot-friendly variant that avoids retaining raw vehicle JSON. */
    fun presentation(
        chargeState: Int?,
        chargeRemainTime: String?,
        fallbackLabel: String?
    ): WidgetStatusPresentation = when {
        chargeState == 1 && fallbackLabel != "行驶中" -> {
            val remaining = chargeRemainTime
                ?.takeIf { it.isNotBlank() && it != "已充满" }
                ?: "未知"
            WidgetStatusPresentation("剩余$remaining", showChargingIcon = true)
        }
        chargeState == 2 && fallbackLabel != "行驶中" ->
            WidgetStatusPresentation("充电完成")
        else -> WidgetStatusPresentation(fallbackLabel)
    }

    fun locked(status: JSONObject): Boolean? = locked(status.opt("driverDoorLockStatus"))

    fun locked(values: Map<String, Any?>): Boolean? = locked(values["driverDoorLockStatus"])

    /** Returns open-window labels for the post-lock parking safety notification. */
    fun openWindowLabels(status: JSONObject, carType: String? = null): List<String> =
        openWindowLabels { key -> status.opt(key) }
            .takeUnless { carType?.contains("T03", ignoreCase = true) == true }
            .orEmpty()

    /** True only when this vehicle payload actually contains a verified window signal. */
    fun hasWindowTelemetry(status: JSONObject, carType: String? = null): Boolean {
        if (carType?.contains("T03", ignoreCase = true) == true) return false
        return WINDOW_SIGNAL_KEYS.any { key -> status.has(key) && status.opt(key) != JSONObject.NULL }
    }

    /**
     * The widget refresh cadence follows vehicle motion, not the door-lock
     * state. A stopped vehicle may remain locked or unlocked for a long time;
     * speed is therefore the source of truth when it is present. Older payloads
     * without a speed field can still use their explicit driving gear.
     */
    fun isDriving(status: JSONObject): Boolean = when {
        status.has("speed") -> parseSpeed(status.opt("speed"))?.let { it > 0.0 } == true
        else -> isDrivingGear(status.opt("gearStatus"))
    }

    fun isDriving(values: Map<String, Any?>): Boolean = when {
        values.containsKey("speed") -> parseSpeed(values["speed"])?.let { it > 0.0 } == true
        else -> isDrivingGear(values["gearStatus"])
    }

    private fun label(valueForKey: (String) -> Any?): String? = when {
        isDrivingGear(valueForKey("gearStatus")) -> "行驶中"
        ChargeStatus.state(mapOf("chargeState" to valueForKey("chargeState"))) == 1 -> "充电中"
        ChargeStatus.state(mapOf("chargeState" to valueForKey("chargeState"))) == 2 -> "充电完成"
        hasOpenWindow(valueForKey) -> "车窗未关闭"
        else -> null
    }

    private fun isDrivingGear(value: Any?): Boolean = when (value?.toString()?.trim()?.uppercase()) {
        "D", "DRIVE", "前进", "D挡", "3", "R", "REVERSE", "倒车", "R挡", "1" -> true
        else -> false
    }

    private fun parseSpeed(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        else -> value?.toString()?.trim()?.removeSuffix("km/h")?.trim()?.toDoubleOrNull()
    }

    private fun hasOpenWindow(valueForKey: (String) -> Any?): Boolean {
        return openWindowLabels(valueForKey).isNotEmpty()
    }

    private fun openWindowLabels(valueForKey: (String) -> Any?): List<String> {
        val percentageKeys = listOf(
            "左前" to "leftFrontWindowPercent",
            "右前" to "rightFrontWindowPercent",
            "左后" to "leftRearWindowPercent",
            "右后" to "rightRearWindowPercent"
        )
        val statusKeys = listOf(
            "左前" to "driverWindowStatus",
            "右前" to "rightFrontWindowStatus",
            "左后" to "leftRearWindowStatus",
            "右后" to "rightRearWindowStatus"
        ).toMap()
        return percentageKeys.mapNotNull { (label, key) ->
            val statusKey = statusKeys.getValue(label)
            if (windowPercentageOpen(valueForKey(key)) || windowStatusOpen(valueForKey(statusKey))) label else null
        }
    }

    private val WINDOW_SIGNAL_KEYS = listOf(
        "leftFrontWindowPercent",
        "rightFrontWindowPercent",
        "leftRearWindowPercent",
        "rightRearWindowPercent",
        "driverWindowStatus",
        "rightFrontWindowStatus",
        "leftRearWindowStatus",
        "rightRearWindowStatus"
    )

    private fun windowPercentageOpen(value: Any?): Boolean =
        value?.toString()?.trim()?.removeSuffix("%")?.toDoubleOrNull()?.let { it > 0.0 } == true

    private fun windowStatusOpen(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "true", "1", "open", "opened", "opening" -> true
            else -> false
        }
        else -> false
    }

    private fun locked(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "true", "1", "locked", "lock" -> true
            "false", "0", "unlocked", "unlock" -> false
            else -> null
        }
        else -> null
    }
}
