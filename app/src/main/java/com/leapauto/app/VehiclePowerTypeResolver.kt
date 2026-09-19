package com.leapauto.app

import org.json.JSONObject

/** Shared display policy; telemetry and old snapshots cannot override a configured power type. */
object VehiclePowerTypeResolver {
    fun fromVehicleConfig(
        vin: String,
        configuredPowerType: SessionStore.VehiclePowerType?,
        carType: String?,
        defaultPowerType: SessionStore.VehiclePowerType?,
        vehicles: List<Vehicle>
    ): SessionStore.VehiclePowerType? {
        val vehicle = vehicles.firstOrNull { vin.isNotBlank() && it.vin == vin }
        return resolve(
            configuredPowerType = configuredPowerType,
            carType = carType?.takeIf { it.isNotBlank() } ?: vehicle?.carType,
            cachedPowerType = defaultPowerType ?: vehicle?.powerType
        )
    }

    fun resolve(
        configuredPowerType: SessionStore.VehiclePowerType?,
        carType: String?,
        cachedPowerType: SessionStore.VehiclePowerType? = null,
        hasFuelTelemetry: Boolean = false
    ): SessionStore.VehiclePowerType? = configuredPowerType
        ?: fromCarType(carType)
        ?: cachedPowerType
        ?: SessionStore.VehiclePowerType.RANGE_EXTENDER.takeIf { hasFuelTelemetry }

    fun fromStatus(
        status: JSONObject,
        configuredPowerType: SessionStore.VehiclePowerType?,
        carType: String?
    ): SessionStore.VehiclePowerType? = resolve(
        configuredPowerType = configuredPowerType,
        carType = carType,
        hasFuelTelemetry = VehicleStatusMapper.fuelSocPercent(status) != null ||
            (VehicleStatusMapper.fuelRange(status) != null && VehicleStatusMapper.combinedRange(status) != null)
    )

    fun fromCarType(carType: String?): SessionStore.VehiclePowerType? = when {
        // REEV also contains EV, so the range-extender marker must be checked first.
        carType?.contains("\u589e\u7a0b") == true ||
            carType?.contains("REEV", ignoreCase = true) == true -> SessionStore.VehiclePowerType.RANGE_EXTENDER
        carType?.contains("\u7eaf\u7535") == true ||
            carType?.contains("EV", ignoreCase = true) == true -> SessionStore.VehiclePowerType.PURE_ELECTRIC
        else -> null
    }
}

fun SessionStore.VehiclePowerType?.toStatusPowerType(): VehicleStatusMapper.PowerType? = when (this) {
    SessionStore.VehiclePowerType.PURE_ELECTRIC -> VehicleStatusMapper.PowerType.PURE_ELECTRIC
    SessionStore.VehiclePowerType.RANGE_EXTENDER -> VehicleStatusMapper.PowerType.RANGE_EXTENDER
    null -> null
}
