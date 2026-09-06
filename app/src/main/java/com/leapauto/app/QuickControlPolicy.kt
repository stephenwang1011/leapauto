package com.leapauto.app

import java.util.Locale

/** Verified quick-control capability and telemetry policies. */
object VehicleQuickControlCapabilities {
    private val d19Pattern = Regex("(?i)(^|[^A-Za-z0-9])D19([^A-Za-z0-9]|$)")

    fun supportsFrunk(vehicleModel: String): Boolean =
        d19Pattern.containsMatchIn(vehicleModel)
}

/** Maps only the verified battery thermal request values to a toggle state. */
object BatteryPreheatState {
    private const val HEATING_REQUEST = "4"
    private const val IDLE_REQUEST = "0"

    fun fromRaw(raw: Any?): Boolean? {
        if (raw == null) return null
        val value = raw.toString().trim()
        return when (value.lowercase(Locale.US)) {
            HEATING_REQUEST -> true
            IDLE_REQUEST -> false
            else -> null
        }
    }
}
