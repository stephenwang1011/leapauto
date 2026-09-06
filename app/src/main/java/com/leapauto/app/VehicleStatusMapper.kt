package com.leapauto.app

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil

/** Shared vehicle display mapping used by the app and the home-screen widget. */
object VehicleStatusMapper {
    enum class PowerType { PURE_ELECTRIC, RANGE_EXTENDER }

    private const val MOCK_RANGE_EXTENDER_VIN = "LFZ63AZ55SH023503"
    private const val MOCK_FUEL_SOC_PERCENT = 29
    private const val MOCK_FUEL_RANGE_KM = 129

    /**
     * Supplies local-only fuel telemetry for the configured test vehicle.
     * The server response is copied and never modified in place.
     */
    fun withFuelMock(
        status: JSONObject,
        vin: String,
        powerType: SessionStore.VehiclePowerType?
    ): JSONObject {
        if (!vin.equals(MOCK_RANGE_EXTENDER_VIN, ignoreCase = true) ||
            powerType != SessionStore.VehiclePowerType.RANGE_EXTENDER
        ) {
            return status
        }

        return JSONObject(status.toString()).apply {
            put("fuelSoc", MOCK_FUEL_SOC_PERCENT)
            put("3235", MOCK_FUEL_SOC_PERCENT)
            put("fuelRangeDynamic", MOCK_FUEL_RANGE_KM)
            put("fuelRangeStandard", MOCK_FUEL_RANGE_KM)
            put("3259", MOCK_FUEL_RANGE_KM)
            put("3256", MOCK_FUEL_RANGE_KM)
            mockCombinedRange(firstRangeValue(status, "expectedMileage", "3260"))?.let { range ->
                put("combinedRangeDynamic", range)
                put("3261", range)
            }
            mockCombinedRange(firstRangeValue(status, "electricRangeStandard", "maxRange", "3257"))?.let { range ->
                put("combinedRangeStandard", range)
                put("3258", range)
            }
        }
    }

    /** Keeps the local fuel fixture consistent with the confirmed electric plus fuel range contract. */
    private fun mockCombinedRange(electricRange: String?): String? =
        electricRange
            ?.toBigDecimalOrNull()
            ?.add(BigDecimal(MOCK_FUEL_RANGE_KM))
            ?.stripTrailingZeros()
            ?.toPlainString()

    /** 手动车型配置仅影响展示标题/图片，不参与接口协议或信号解析。 */
    fun resolveDisplayModel(configuredModel: String?, reportedCarType: String?): String =
        configuredModel?.trim()?.takeIf { it.isNotBlank() } ?: reportedCarType.orEmpty().trim()

    /**
     * Legacy image lookup retained for callers outside the current Compose path.
     * Reuse the color-aware catalog so this helper cannot keep the old PNG set alive.
     */
    fun vehicleImageResource(carType: String): Int =
        VehicleAppearanceCatalog.resolveAppearance(carType, null).imageResource

    /**
     * Selects the confirmed remaining-range field for the reported range mode:
     * standard mode 0 uses 3257 and dynamic mode 1 uses 3260. Unknown modes do
     * not cross-fallback.
     */
    fun remainingRange(status: JSONObject): String? =
        remainingRange(status.opt("rangeMode")) { key -> status.opt(key) }

    /** Legacy T03 and pure-electric C11 payloads expose legacy range fields. */
    fun remainingRange(status: JSONObject, carType: String?): String? =
        if (carType?.contains("T03", ignoreCase = true) == true) {
            displayRangeValue(status.opt("expectedMileage"))
        } else if (carType?.contains("C11", ignoreCase = true) == true && !hasModernRangeSignals(status)) {
            displayRangeValue(status.opt("liveRemainingRange"))
        } else {
            remainingRange(status)
        }

    /** Explicit user-selected power type takes precedence over signal heuristics. */
    fun remainingRange(status: JSONObject, carType: String?, powerType: PowerType?): String? =
        when (powerType) {
            PowerType.PURE_ELECTRIC -> electricRange(status)
                ?: displayRangeValue(status.opt("liveRemainingRange"))
                ?: remainingRange(status, carType)
            PowerType.RANGE_EXTENDER -> combinedRange(status) ?: remainingRange(status, carType)
            null -> if (!carType.isPureElectricModel() &&
                fuelSocPercent(status) != null &&
                combinedRange(status) != null
            ) {
                combinedRange(status)
            } else {
                remainingRange(status, carType)
            }
        }

    fun widgetRange(status: JSONObject, carType: String?, powerType: PowerType?): String? =
        when (powerType) {
            PowerType.PURE_ELECTRIC -> remainingRange(status, carType, PowerType.PURE_ELECTRIC)
            PowerType.RANGE_EXTENDER -> combinedRange(status) ?: remainingRange(status, carType)
            null -> widgetRange(status, carType)
        }

    fun remainingRange(values: Map<String, Any?>): String? =
        remainingRange(values["rangeMode"]) { key -> values[key] }

    fun remainingRange(values: Map<String, Any?>, carType: String?): String? =
        if (carType?.contains("T03", ignoreCase = true) == true) {
            displayRangeValue(values["expectedMileage"])
        } else if (carType?.contains("C11", ignoreCase = true) == true && !hasModernRangeSignals(values)) {
            displayRangeValue(values["liveRemainingRange"])
        } else {
            remainingRange(values)
        }

    private fun hasModernRangeSignals(status: JSONObject): Boolean =
        status.has("electricRangeStandard") || status.has("maxRange") || status.has("expectedMileage") ||
            status.has("fuelRangeStandard") || status.has("fuelRangeDynamic") ||
            status.has("combinedRangeStandard") || status.has("combinedRangeDynamic") ||
            status.has("3256") || status.has("3257") || status.has("3258") ||
            status.has("3259") || status.has("3260") || status.has("3261")

    private fun hasModernRangeSignals(values: Map<String, Any?>): Boolean =
        values.keys.any {
            it in setOf(
                "electricRangeStandard", "maxRange", "expectedMileage",
                "fuelRangeStandard", "combinedRangeStandard", "fuelRangeDynamic",
                "combinedRangeDynamic", "3256", "3257", "3258", "3259", "3260", "3261"
            )
        }

    /** Range breakdown for vehicles exposing the confirmed hybrid range groups. */
    fun fuelRange(status: JSONObject): String? {
        rangeGroupValue(
            status,
            standardKeys = listOf("fuelRangeStandard", "3256"),
            dynamicKeys = listOf("fuelRangeDynamic", "3259")
        )?.let { return it }

        // Some payloads omit the fuel component while still returning the
        // confirmed combined and electric values. Derive only that missing
        // component; never extrapolate when the values are invalid.
        val combined = combinedRange(status)?.toBigDecimalOrNull()
        val electric = electricRange(status)?.toBigDecimalOrNull()
        return if (combined != null && electric != null && combined >= electric) {
            combined.subtract(electric).stripTrailingZeros().toPlainString()
        } else {
            null
        }
    }

    fun electricRange(status: JSONObject): String? = rangeGroupValue(
        status,
        standardKeys = listOf("electricRangeStandard", "maxRange", "3257"),
        dynamicKeys = listOf("expectedMileage", "3260")
    )

    fun combinedRange(status: JSONObject): String? = rangeGroupValue(
        status,
        standardKeys = listOf("combinedRangeStandard", "3258"),
        dynamicKeys = listOf("combinedRangeDynamic", "3261")
    )

    /** Widget snapshot carries each energy source's remaining range for its own display. */
    fun electricRemainingRange(status: JSONObject): String? = electricRange(status)

    fun electricTotalRange(status: JSONObject): String? =
        // 3257 is a standard-mode remaining value, not a full-range denominator.
        firstRangeValue(status, "maxRange")

    fun fuelRemainingRange(status: JSONObject): String? =
        fuelRange(status)

    fun fuelTotalRange(status: JSONObject): String? =
        // 3256 is a standard-mode remaining value, not a full-range denominator.
        firstRangeValue(status, "maxFuelRange")

    fun widgetRange(status: JSONObject, carType: String?): String? =
        if (!carType.isPureElectricModel() && fuelRange(status) != null && combinedRange(status) != null) {
            combinedRange(status)
        } else {
            remainingRange(status, carType)
        }

    private fun rangeGroupValue(
        status: JSONObject,
        standardKeys: List<String>,
        dynamicKeys: List<String>
    ): String? {
        val mode = status.opt("rangeMode")?.toString()?.trim()
        val keys = when (mode) {
            "0" -> standardKeys
            "1" -> dynamicKeys
            else -> when {
                dynamicKeys.any(status::has) -> dynamicKeys
                standardKeys.any(status::has) -> standardKeys
                else -> return null
            }
        }
        return keys.firstNotNullOfOrNull { key -> displayRangeValue(status.opt(key)) }
    }

    private fun firstRangeValue(status: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> displayRangeValue(status.opt(key)) }

    fun soc(status: JSONObject): Int = ceil(socFraction(status) * 100f).toInt()

    fun soc(values: Map<String, Any?>): Int = ceil(socFraction(values) * 100f).toInt()

    /** The confirmed 1204 signal is the pure-electric remaining percentage. */
    fun electricSocPercent(status: JSONObject): Int? =
        percentagePercent(status.opt("soc"))
            ?: preciseSocPercent(status.opt("preciseSoc"))

    /** The confirmed 3235 signal is the range-extender fuel remaining percentage. */
    fun fuelSocPercent(status: JSONObject): Int? = percentagePercent(status.opt("fuelSoc"))

    fun displaySoc(value: Any?): String? {
        if (value == null || value == JSONObject.NULL) return null
        val raw = value.toString().trim().removeSuffix("%").trim()
        val percentage = raw.toFloatOrNull() ?: return null
        return "${ceil(percentage).toInt().coerceIn(0, 100)}%"
    }

    fun displayPreciseSoc(value: Any?): String? {
        if (value == null || value == JSONObject.NULL) return null
        val raw = value.toString().trim().removeSuffix("%").trim()
        val percentage = runCatching { BigDecimal(raw) }.getOrNull() ?: return null
        val bounded = percentage.max(BigDecimal.ZERO).min(BigDecimal(100))
        return "${bounded.stripTrailingZeros().toPlainString()}%"
    }

    /** Normalizes the precise 100003 SOC signal for integer progress bars. */
    fun preciseSocPercent(value: Any?): Int? {
        return percentagePercent(value)
    }

    private fun percentagePercent(value: Any?): Int? {
        if (value == null || value == JSONObject.NULL) return null
        val raw = value.toString().trim().removeSuffix("%").trim()
        val percentage = runCatching { BigDecimal(raw) }.getOrNull() ?: return null
        return percentage.max(BigDecimal.ZERO)
            .min(BigDecimal(100))
            .setScale(0, RoundingMode.CEILING)
            .toInt()
    }

    fun socFraction(status: JSONObject): Float = socFraction(status.opt("soc"))

    fun socFraction(values: Map<String, Any?>): Float = socFraction(values["soc"])

    fun socFraction(value: Any?): Float {
        if (value == null || value == JSONObject.NULL) return 0f
        val raw = value.toString().trim().removeSuffix("%").trim()
        return raw.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f) ?: 0f
    }

    private fun remainingRange(
        modeValue: Any?,
        valueForKey: (String) -> Any?
    ): String? {
        val field = when (modeValue?.toString()?.trim()) {
            "0" -> "electricRangeStandard"
            "1" -> "expectedMileage"
            else -> return null
        }
        return displayRangeValue(valueForKey(field))
            ?: if (field == "electricRangeStandard") {
                displayRangeValue(valueForKey("maxRange"))
                    ?: displayRangeValue(valueForKey("3257"))
            } else {
                null
            }
    }

    private fun displayRangeValue(value: Any?): String? =
        value
            .takeUnless { it == null || it == JSONObject.NULL }
            ?.toString()
            ?.trim()
            ?.removeSuffix("km")
            ?.trim()
            ?.removeSuffix("k")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "--" }

    private fun String?.isPureElectricModel(): Boolean =
        this?.contains("纯电", ignoreCase = true) == true ||
            this?.contains("EV", ignoreCase = true) == true ||
            this?.contains("BEV", ignoreCase = true) == true

}
