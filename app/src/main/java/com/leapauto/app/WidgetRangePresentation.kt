package com.leapauto.app

/** Pure widget range presentation model. Hybrid bars use confirmed energy percentages when available. */
data class WidgetRangePresentation(
    val rangeExtender: Boolean,
    val totalRange: String,
    val electricRange: String?,
    val fuelRange: String?,
    val electricProgress: Int,
    val fuelProgress: Int,
    val electricProgressKnown: Boolean = false,
    val fuelProgressKnown: Boolean = false
)

enum class WidgetPureRangeTone { NORMAL, WARNING, CRITICAL }

object WidgetPureRangeToneMapper {
    fun fromSoc(soc: Int): WidgetPureRangeTone = when {
        soc.coerceIn(0, 100) <= 20 -> WidgetPureRangeTone.CRITICAL
        soc <= 40 -> WidgetPureRangeTone.WARNING
        else -> WidgetPureRangeTone.NORMAL
    }
}

object WidgetRangePresentationMapper {
    fun fromValues(
        totalRange: String?,
        electricRange: String?,
        fuelRange: String?,
        powerType: SessionStore.VehiclePowerType?,
        electricTotalRange: String? = null,
        fuelTotalRange: String? = null,
        electricSocPercent: Int? = null,
        fuelSocPercent: Int? = null
    ): WidgetRangePresentation {
        val total = totalRange.cleanRange() ?: "--"
        val electric = electricRange.cleanRange()
        val fuel = fuelRange.cleanRange()
        val hasHybridComponents = electric != null || fuel != null
        // A confirmed fuel percentage is exclusive to range-extender telemetry.
        // It lets the widget render a split range before a VIN power-type
        // preference has been saved.
        val inferredRangeExtender = powerType == null && fuelSocPercent != null
        return if (
            (powerType == SessionStore.VehiclePowerType.RANGE_EXTENDER || inferredRangeExtender) &&
            hasHybridComponents
        ) {
            WidgetRangePresentation(
                rangeExtender = true,
                totalRange = total,
                electricRange = electric ?: "--",
                fuelRange = fuel ?: "--",
                electricProgress = electricSocPercent?.coerceIn(0, 100)
                    ?: progress(electric, electricTotalRange),
                fuelProgress = fuelSocPercent?.coerceIn(0, 100)
                    ?: progress(fuel, fuelTotalRange),
                electricProgressKnown = electricSocPercent != null || hasUsableRatio(electric, electricTotalRange),
                fuelProgressKnown = fuelSocPercent != null || hasUsableRatio(fuel, fuelTotalRange)
            )
        } else {
            WidgetRangePresentation(
                rangeExtender = false,
                totalRange = total,
                electricRange = null,
                fuelRange = null,
                electricProgress = 0,
                fuelProgress = 0
            )
        }
    }

    private fun progress(value: String?, total: String?): Int {
        val number = value?.toDoubleOrNull() ?: return 0
        val denominator = total?.toDoubleOrNull() ?: return 0
        if (denominator <= 0.0 || number <= 0.0) return 0
        return (number / denominator * 100.0).toInt().coerceIn(0, 100)
    }

    private fun hasUsableRatio(value: String?, total: String?): Boolean {
        val number = value?.toDoubleOrNull() ?: return false
        val denominator = total?.toDoubleOrNull() ?: return false
        return number >= 0.0 && denominator > 0.0
    }

    private fun String?.cleanRange(): String? = this
        ?.trim()
        ?.removeSuffix("km")
        ?.trim()
        ?.removeSuffix("k")
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "--" }
}

/** Formats a widget component range with one stable unit suffix. */
fun formatWidgetRangeLabel(value: String?): String {
    val normalized = value
        ?.trim()
        ?.removeSuffix("km")
        ?.trim()
        ?.removeSuffix("k")
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "--" }
    return if (normalized == null) "--km" else "${normalized}km"
}
