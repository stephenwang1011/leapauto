package com.leapauto.app

enum class PostLoginPrompt {
    VEHICLE_CONFIG,
    NONE
}

/** Shared per-VIN preference keys for the non-sensitive vehicle display configuration. */
object VehicleConfigStorageKeys {
    fun prefix(vin: String): String? =
        vin.trim().takeIf { it.isNotEmpty() }?.let { "vehicle_config_${it}_" }

    fun field(vin: String, name: String): String? =
        prefix(vin)?.plus(name)
}

/** Pure policy for the per-vehicle model and power configuration confirmation. */
object VehicleConfigConfirmationPolicy {
    /**
     * Schema 2 remains compatible with existing persisted vehicle configuration.
     */
    const val SCHEMA_VERSION = 2

    fun confirmationPreferenceKey(vin: String): String? =
        VehicleConfigStorageKeys.field(vin, "confirmation_schema")

    fun normalizeModel(model: String): String? {
        val candidate = model.trim()
        if (candidate.isEmpty()) return null
        return SUPPORTED_VEHICLE_MODELS.firstOrNull { option ->
            candidate.equals(option, ignoreCase = true) || candidate.contains(option, ignoreCase = true)
        }
    }

    fun isConfirmed(storedSchemaVersion: Int): Boolean =
        storedSchemaVersion >= SCHEMA_VERSION

    fun isValid(
        model: String,
        modelYear: String,
        powerType: SessionStore.VehiclePowerType?,
        color: String? = null
    ): Boolean = normalizeModel(model) != null &&
        modelYear.trim().matches(Regex("\\d{4}")) &&
        powerType != null

    fun schemaToPersist(
        model: String,
        modelYear: String,
        powerType: SessionStore.VehiclePowerType?,
        color: String? = null
    ): Int? = if (
        color != null &&
        isValid(model, modelYear, powerType, color)
    ) {
        SCHEMA_VERSION
    } else {
        null
    }

    fun initialPrompt(vehicleConfigConfirmed: Boolean): PostLoginPrompt =
        if (vehicleConfigConfirmed) PostLoginPrompt.NONE else PostLoginPrompt.VEHICLE_CONFIG
}

object PostLoginPromptTiming {
    const val AUTHOR_SUPPORT_IDLE_MS = 10_000L
}

data class AuthorSupportPromptContext(
    val activityAlive: Boolean,
    val foreground: Boolean,
    val loggedIn: Boolean,
    val busy: Boolean,
    val pinPromptVisible: Boolean,
    val pinSetupInProgress: Boolean,
    val vehicleConfigPromptVisible: Boolean,
    val sessionExpiredPromptVisible: Boolean,
    val authorSupportPromptVisible: Boolean,
    val authorSupportScreenOpening: Boolean,
    val reminderEligibleToday: Boolean
)

object AuthorSupportIdlePolicy {
    fun canArm(context: AuthorSupportPromptContext): Boolean =
        context.activityAlive &&
            context.foreground &&
            context.loggedIn &&
            !context.busy &&
            !context.pinPromptVisible &&
            !context.pinSetupInProgress &&
            !context.vehicleConfigPromptVisible &&
            !context.sessionExpiredPromptVisible &&
            !context.authorSupportPromptVisible &&
            !context.authorSupportScreenOpening &&
            context.reminderEligibleToday
}
