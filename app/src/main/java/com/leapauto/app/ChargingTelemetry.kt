package com.leapauto.app

import org.json.JSONObject

/** Charging-only projection of parseStatus, without window preferences or configuration writes. */
internal object ChargingTelemetry {
    fun merge(current: VehicleStatus, values: JSONObject, carType: String): VehicleStatus {
        val preciseSoc = percentage(values.opt("preciseSoc")) ?: percentage(values.opt("soc"))
        val powerType = if (current.rangeExtender) VehicleStatusMapper.PowerType.RANGE_EXTENDER else VehicleStatusMapper.PowerType.PURE_ELECTRIC
        val refreshed = current.copy(
            soc = preciseSoc,
            preciseSoc = preciseSoc,
            mileage = VehicleStatusMapper.remainingRange(values, carType, powerType)?.let { "${it}km" },
            electricMileage = if (current.rangeExtender) VehicleStatusMapper.electricRange(values)?.let { "$it km" } else null,
            combinedMileage = if (current.rangeExtender) VehicleStatusMapper.combinedRange(values)?.let { "$it km" } else null,
            rangeMode = raw(values.opt("rangeMode")),
            chargeState = ChargeStatus.state(values),
            chargeLabel = ChargeStatus.label(ChargeStatus.state(values)),
            chargeGunConnected = ChargeStatus.isGunConnected(values),
            chargingPower = ChargeStatus.power(values),
            chargeRemainTime = ChargeStatus.remainingTime(values.opt("chargeRemainTime")),
            chargeSoc = ChargeStatus.socPercent(values.opt("soc")),
            chargeType = ChargeStatus.type(values),
            batteryVoltage = value(values.opt("batteryVoltage"), " V"),
            batteryCurrent = ChargeStatus.displayCurrent(values)?.let { "$it A" },
            minBatteryTemp = value(values.opt("minBatteryTemp"), " °C"),
            chargerUnlocked = current.chargerUnlocked && ChargeStatus.state(values) != 1
        )
        return ChargingControlPolicy.mergeTelemetry(current, refreshed)
    }

    private fun raw(value: Any?): String? =
        if (value == null || value == JSONObject.NULL) null else value.toString().trim().takeIf { it.isNotBlank() }

    private fun value(value: Any?, unit: String): String? =
        if (value == null || value == JSONObject.NULL) null else "$value$unit"

    private fun percentage(value: Any?): String? {
        VehicleStatusMapper.displayPreciseSoc(value)?.let { return it }
        val text = raw(value)?.removeSuffix("%")?.trim() ?: return null
        return text.toDoubleOrNull()?.let { "${Math.round(it).toInt()}%" }
            ?: text.takeIf { it.isNotBlank() }?.let { "$it%" }
    }
}
