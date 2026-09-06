package com.leapauto.app

data class CompactWidgetRangePresentation(
    val rangeLabel: String,
    val socLabel: String,
    val progress: Int,
    val progressKnown: Boolean,
    val tone: WidgetPureRangeTone,
    val rangeExtender: Boolean = false,
    val totalRangeLabel: String = "--km",
    val electricRangeLabel: String = "--km",
    val fuelRangeLabel: String = "--km",
    val electricSocLabel: String = "--%",
    val fuelSocLabel: String = "--%",
    val electricProgress: Int = 0,
    val fuelProgress: Int = 0,
    val electricProgressKnown: Boolean = false,
    val fuelProgressKnown: Boolean = false
)

object CompactWidgetRangePresentationMapper {
    fun fromValues(
        range: String?,
        soc: Int?,
        powerType: SessionStore.VehiclePowerType? = null,
        electricRange: String? = null,
        fuelRange: String? = null,
        electricSoc: Int? = null,
        fuelSoc: Int? = null,
        electricTotalRange: String? = null,
        fuelTotalRange: String? = null
    ): CompactWidgetRangePresentation {
        val normalizedRange = range
            ?.trim()
            ?.removeSuffix("km")
            ?.trim()
            ?.removeSuffix("k")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "--" }
        val normalizedSoc = soc?.coerceIn(0, 100)
        val normalizedElectricRange = cleanRange(electricRange)
        val normalizedFuelRange = cleanRange(fuelRange)
        // Electric-only telemetry is also present on pure-electric vehicles;
        // infer a hybrid widget only from fuel-specific data when no vehicle
        // power-type preference has been saved.
        val inferredRangeExtender = powerType == null &&
            (normalizedFuelRange != null || fuelSoc != null)
        val rangeExtender = powerType == SessionStore.VehiclePowerType.RANGE_EXTENDER || inferredRangeExtender
        val electricProgress = electricSoc?.coerceIn(0, 100)
            ?: ratioProgress(normalizedElectricRange, cleanRange(electricTotalRange))
        val fuelProgress = fuelSoc?.coerceIn(0, 100)
            ?: ratioProgress(normalizedFuelRange, cleanRange(fuelTotalRange))
        return CompactWidgetRangePresentation(
            rangeLabel = normalizedRange?.let { "$it km" } ?: "-- km",
            socLabel = normalizedSoc?.toString() ?: "--",
            progress = normalizedSoc ?: 0,
            progressKnown = normalizedSoc != null,
            tone = normalizedSoc?.let(WidgetPureRangeToneMapper::fromSoc)
                ?: WidgetPureRangeTone.NORMAL,
            rangeExtender = rangeExtender,
            totalRangeLabel = formatWidgetRangeLabel(normalizedRange),
            electricRangeLabel = formatWidgetRangeLabel(normalizedElectricRange),
            fuelRangeLabel = formatWidgetRangeLabel(normalizedFuelRange),
            electricSocLabel = electricSoc?.coerceIn(0, 100)?.let { "$it%" } ?: "--%",
            fuelSocLabel = fuelSoc?.coerceIn(0, 100)?.let { "$it%" } ?: "--%",
            electricProgress = electricProgress,
            fuelProgress = fuelProgress,
            electricProgressKnown = electricSoc != null || hasUsableRatio(normalizedElectricRange, cleanRange(electricTotalRange)),
            fuelProgressKnown = fuelSoc != null || hasUsableRatio(normalizedFuelRange, cleanRange(fuelTotalRange))
        )
    }

    private fun cleanRange(value: String?): String? = value
        ?.trim()
        ?.removeSuffix("km")
        ?.trim()
        ?.removeSuffix("k")
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "--" }

    private fun ratioProgress(value: String?, total: String?): Int {
        val number = value?.toDoubleOrNull() ?: return 0
        val denominator = total?.toDoubleOrNull() ?: return 0
        if (number < 0.0 || denominator <= 0.0) return 0
        return (number / denominator * 100.0).toInt().coerceIn(0, 100)
    }

    private fun hasUsableRatio(value: String?, total: String?): Boolean =
        value?.toDoubleOrNull()?.let { number ->
            total?.toDoubleOrNull()?.let { denominator -> number >= 0.0 && denominator > 0.0 }
        } ?: false
}

data class CompactWidgetLockPresentation(
    val label: String,
    val command: String?,
    val contentDescription: String,
    val locked: Boolean?
)

object CompactWidgetLockPresentationMapper {
    fun fromState(locked: Boolean?): CompactWidgetLockPresentation = when (locked) {
        true -> CompactWidgetLockPresentation(
            label = "已锁车",
            command = "unlock",
            contentDescription = "车辆已锁，点击解锁",
            locked = true
        )
        false -> CompactWidgetLockPresentation(
            label = "未锁车",
            command = "lock",
            contentDescription = "车辆未锁，点击上锁",
            locked = false
        )
        null -> CompactWidgetLockPresentation(
            label = "门锁",
            command = null,
            contentDescription = "门锁状态未知，打开 App 查看",
            locked = null
        )
    }
}
