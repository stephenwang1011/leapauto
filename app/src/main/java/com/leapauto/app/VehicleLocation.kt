package com.leapauto.app

import kotlin.math.cos
import kotlin.math.sqrt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Source of a vehicle coordinate pair in a decoded vehicle signal map.
 * The numeric signal IDs are kept here so callers do not need to guess them.
 */
enum class VehicleLocationSource {
    PRIMARY,
    VERIFIED_SIGNAL_MAP
}

/**
 * Coordinate systems are deliberately explicit because the vehicle protocol
 * source has not yet been confirmed as WGS-84, GCJ-02, or another datum.
 */
enum class VehicleCoordinateSystem {
    UNCONFIRMED,
    WGS84,
    GCJ02
}

enum class VehicleLocationFreshness {
    FRESH,
    STALE,
    EXPIRED,
    INVALID
}

enum class VehicleLocationAvailability {
    AVAILABLE,
    UNAVAILABLE
}

data class VehicleLocation(
    val latitude: Double,
    val longitude: Double,
    val source: VehicleLocationSource,
    val coordinateSystem: VehicleCoordinateSystem = VehicleCoordinateSystem.UNCONFIRMED
)

data class VehicleLocationSnapshot(
    val location: VehicleLocation,
    val queriedAtEpochMs: Long,
    /** The successful vehicle-response time is kept for freshness diagnostics only. */
    val receivedAtEpochMs: Long = queriedAtEpochMs
)

/** Safe, non-precise location state for the vehicle status UI. */
data class VehicleLocationSummary(
    val availability: VehicleLocationAvailability,
    val freshness: VehicleLocationFreshness,
    val coordinateSystem: VehicleCoordinateSystem,
    val queriedAtEpochMs: Long,
    val invalidReason: VehicleLocationValidation.InvalidReason? = null
) {
    companion object {
        fun fromSnapshot(
            snapshot: VehicleLocationSnapshot,
            nowEpochMs: Long,
            freshnessPolicy: VehicleLocationFreshnessPolicy = VehicleLocationFreshnessPolicy()
        ): VehicleLocationSummary = VehicleLocationSummary(
            availability = VehicleLocationAvailability.AVAILABLE,
            freshness = VehicleLocationDomain.freshness(snapshot, nowEpochMs, freshnessPolicy),
            coordinateSystem = snapshot.location.coordinateSystem,
            queriedAtEpochMs = snapshot.queriedAtEpochMs
        )

        fun fromValidation(
            validation: VehicleLocationValidation,
            queriedAtEpochMs: Long,
            nowEpochMs: Long = queriedAtEpochMs,
            freshnessPolicy: VehicleLocationFreshnessPolicy = VehicleLocationFreshnessPolicy()
        ): VehicleLocationSummary = when (validation) {
            is VehicleLocationValidation.Valid -> {
                val snapshot = VehicleLocationSnapshot(validation.location, queriedAtEpochMs)
                fromSnapshot(snapshot, nowEpochMs, freshnessPolicy)
            }
            is VehicleLocationValidation.Invalid -> VehicleLocationSummary(
                availability = VehicleLocationAvailability.UNAVAILABLE,
                freshness = VehicleLocationFreshness.INVALID,
                coordinateSystem = VehicleCoordinateSystem.UNCONFIRMED,
                queriedAtEpochMs = queriedAtEpochMs,
                invalidReason = validation.reason
            )
        }
    }
}

/** Text-only presentation rules shared by Compose and JVM tests. */
object VehicleLocationSummaryPresentation {
    fun refreshTimeLabel(summary: VehicleLocationSummary?, timeZoneId: String? = null): String {
        val time = summary?.let {
            SimpleDateFormat("HH:mm", Locale.CHINA).apply {
                timeZoneId?.let { id -> timeZone = java.util.TimeZone.getTimeZone(id) }
            }.format(Date(it.queriedAtEpochMs))
        } ?: "--"
        return "位置更新时间：$time"
    }
}

data class VehicleLocationFreshnessPolicy(
    val freshForMs: Long = DEFAULT_FRESH_FOR_MS,
    val staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
    val expiresAfterMs: Long = DEFAULT_EXPIRES_AFTER_MS
) {
    init {
        require(freshForMs >= 0L) { "freshForMs must be non-negative" }
        require(staleAfterMs >= freshForMs) { "staleAfterMs must be >= freshForMs" }
        require(expiresAfterMs >= staleAfterMs) { "expiresAfterMs must be >= staleAfterMs" }
    }

    companion object {
        // Product TTLs remain configurable until the vehicle timestamp contract is confirmed.
        const val DEFAULT_FRESH_FOR_MS = 5 * 60 * 1000L
        const val DEFAULT_STALE_AFTER_MS = 30 * 60 * 1000L
        const val DEFAULT_EXPIRES_AFTER_MS = 60 * 60 * 1000L
    }
}

data class VehicleLocationValidationPolicy(
    val maxJumpKm: Double = DEFAULT_MAX_JUMP_KM,
    val maxJumpIntervalMs: Long = DEFAULT_MAX_JUMP_INTERVAL_MS
) {
    init {
        require(maxJumpKm > 0.0) { "maxJumpKm must be positive" }
        require(maxJumpIntervalMs > 0L) { "maxJumpIntervalMs must be positive" }
    }

    companion object {
        // This is a fail-closed sanity guard, not a claim about vehicle dynamics.
        const val DEFAULT_MAX_JUMP_KM = 500.0
        const val DEFAULT_MAX_JUMP_INTERVAL_MS = 5 * 60 * 1000L
    }
}

sealed interface VehicleLocationValidation {
    data class Valid(val location: VehicleLocation) : VehicleLocationValidation

    enum class InvalidReason {
        MISSING_LATITUDE,
        MISSING_LONGITUDE,
        NON_NUMERIC,
        OUT_OF_RANGE,
        ZERO_COORDINATE,
        OBVIOUS_JUMP
    }

    data class Invalid(val reason: InvalidReason) : VehicleLocationValidation
}

data class CoordinatePair(
    val latitude: Any?,
    val longitude: Any?
) {
    fun hasAnyValue(): Boolean = latitude != null || longitude != null
}

object VehicleLocationDomain {
    const val PRIMARY_LATITUDE_KEY = "latitude"
    const val PRIMARY_LONGITUDE_KEY = "longitude"
    const val PRIMARY_LATITUDE_SIGNAL = "3725"
    const val PRIMARY_LONGITUDE_SIGNAL = "3724"
    const val VERIFIED_LATITUDE_SIGNAL = "3"
    const val VERIFIED_LONGITUDE_SIGNAL = "2"
    const val LOCATION_TIMESTAMP_SIGNAL = "sts"

    /** Selects only the decoded, verified primary coordinate fields. */
    fun selectCoordinatePair(values: Map<String, Any?>): SelectedCoordinatePair {
        val primary = CoordinatePair(
            latitude = values[PRIMARY_LATITUDE_KEY],
            longitude = values[PRIMARY_LONGITUDE_KEY]
        )
        return SelectedCoordinatePair(primary, VehicleLocationSource.PRIMARY)
    }

    /**
     * Reads named fields first, then the verified numeric primary signal IDs.
     * Unverified signal IDs are deliberately ignored in P0.
     */
    fun selectFromDecodedOrRaw(values: Map<String, Any?>): SelectedCoordinatePair {
        val named = selectCoordinatePair(values)
        if (named.pair.hasAnyValue()) return named

        val rawPrimary = CoordinatePair(
            latitude = values[PRIMARY_LATITUDE_SIGNAL],
            longitude = values[PRIMARY_LONGITUDE_SIGNAL]
        )
        return SelectedCoordinatePair(rawPrimary, VehicleLocationSource.PRIMARY)
    }

    fun validate(
        values: Map<String, Any?>,
        previous: VehicleLocationSnapshot? = null,
        queriedAtEpochMs: Long? = null,
        policy: VehicleLocationValidationPolicy = VehicleLocationValidationPolicy()
    ): VehicleLocationValidation {
        val selected = selectFromDecodedOrRaw(values)
        val latitude = parseCoordinate(selected.pair.latitude)
            ?: return if (isMissingCoordinateValue(selected.pair.latitude)) {
                VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.MISSING_LATITUDE)
            } else {
                VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.NON_NUMERIC)
            }
        val longitude = parseCoordinate(selected.pair.longitude)
            ?: return if (isMissingCoordinateValue(selected.pair.longitude)) {
                VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.MISSING_LONGITUDE)
            } else {
                VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.NON_NUMERIC)
            }

        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.OUT_OF_RANGE)
        }
        if (latitude == 0.0 && longitude == 0.0) {
            return VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.ZERO_COORDINATE)
        }

        if (previous != null && queriedAtEpochMs != null) {
            val elapsedMs = queriedAtEpochMs - previous.queriedAtEpochMs
            if (elapsedMs in 0..policy.maxJumpIntervalMs &&
                distanceKm(previous.location.latitude, previous.location.longitude, latitude, longitude) > policy.maxJumpKm
            ) {
                return VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.OBVIOUS_JUMP)
            }
        }

        return VehicleLocationValidation.Valid(
            VehicleLocation(
                latitude = latitude,
                longitude = longitude,
                source = selected.source,
                coordinateSystem = VehicleCoordinateSystem.UNCONFIRMED
            )
        )
    }

    /**
     * P2 contract: read only the verified raw signalMap IDs. This deliberately
     * does not fall back to the older 3725/3724 fields or named coordinates.
     */
    fun validateVerifiedSignalMap(
        values: Map<String, Any?>,
        previous: VehicleLocationSnapshot? = null,
        queriedAtEpochMs: Long? = null,
        policy: VehicleLocationValidationPolicy = VehicleLocationValidationPolicy()
    ): VehicleLocationValidation {
        val validation = validate(
            values = mapOf(
                PRIMARY_LATITUDE_KEY to values[VERIFIED_LATITUDE_SIGNAL],
                PRIMARY_LONGITUDE_KEY to values[VERIFIED_LONGITUDE_SIGNAL]
            ),
            previous = previous,
            queriedAtEpochMs = queriedAtEpochMs,
            policy = policy
        )
        return when (validation) {
            is VehicleLocationValidation.Valid ->
                VehicleLocationValidation.Valid(
                    validation.location.copy(
                        source = VehicleLocationSource.VERIFIED_SIGNAL_MAP,
                        coordinateSystem = VehicleCoordinateSystem.GCJ02
                    )
                )
            is VehicleLocationValidation.Invalid -> validation
        }
    }

    fun freshness(
        snapshot: VehicleLocationSnapshot?,
        nowEpochMs: Long,
        policy: VehicleLocationFreshnessPolicy = VehicleLocationFreshnessPolicy()
    ): VehicleLocationFreshness {
        if (snapshot == null) return VehicleLocationFreshness.INVALID
        val ageMs = nowEpochMs - snapshot.queriedAtEpochMs
        if (ageMs < 0L) return VehicleLocationFreshness.INVALID
        return when {
            ageMs <= policy.freshForMs -> VehicleLocationFreshness.FRESH
            ageMs <= policy.staleAfterMs -> VehicleLocationFreshness.STALE
            else -> VehicleLocationFreshness.EXPIRED
        }
    }

    fun snapshot(
        validation: VehicleLocationValidation,
        queriedAtEpochMs: Long,
        receivedAtEpochMs: Long = queriedAtEpochMs
    ): VehicleLocationSnapshot? = when (validation) {
        is VehicleLocationValidation.Valid ->
            VehicleLocationSnapshot(validation.location, queriedAtEpochMs, receivedAtEpochMs)
        is VehicleLocationValidation.Invalid -> null
    }

    /** A missing or rejected refresh never discards the last valid position. */
    fun retainLastTrustedSnapshot(
        previous: VehicleLocationSnapshot?,
        candidate: VehicleLocationSnapshot?
    ): VehicleLocationSnapshot? = candidate ?: previous

    fun isFutureSnapshot(snapshot: VehicleLocationSnapshot, nowEpochMs: Long): Boolean =
        snapshot.queriedAtEpochMs > nowEpochMs

    private fun parseCoordinate(value: Any?): Double? = when (value) {
        null -> null
        is Number -> value.toDouble().takeIf { it.isFinite() }
        else -> value.toString().trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    }

    private fun isMissingCoordinateValue(value: Any?): Boolean =
        value == null || (value is String && value.isBlank())

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0088
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val haversine =
            kotlin.math.sin(deltaLat / 2) * kotlin.math.sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                kotlin.math.sin(deltaLon / 2) * kotlin.math.sin(deltaLon / 2)
        return 2 * earthRadiusKm * kotlin.math.atan2(sqrt(haversine), sqrt(1 - haversine))
    }

}

data class SelectedCoordinatePair(
    val pair: CoordinatePair,
    val source: VehicleLocationSource
)

/** Converts the protocol's status-time value into the epoch used by freshness rules. */
object VehicleLocationTimestamp {
    fun resolveEpochMs(value: Any?, fallbackEpochMs: Long): Long {
        val text = when (value) {
            null -> null
            org.json.JSONObject.NULL -> null
            else -> value.toString().trim().takeIf { it.isNotEmpty() }
        }
        val numeric = text?.toLongOrNull()
        if (numeric != null) {
            return when {
                numeric in 946_684_800_000L..4_102_444_800_000L -> numeric
                numeric in 946_684_800L..4_102_444_800L -> numeric * 1_000L
                else -> fallbackEpochMs
            }
        }

        val parsed = text?.let { valueText ->
            listOf("yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
                .asSequence()
                .mapNotNull { pattern ->
                    runCatching {
                        SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }
                            .parse(valueText)?.time
                    }.getOrNull()
                }
                .firstOrNull { it > 0L }
        }
        return parsed ?: fallbackEpochMs
    }
}

enum class VehicleLocationMapState {
    NOT_REQUESTED,
    NO_DATA,
    INVALID,
    STALE,
    EXPIRED,
    COORDINATE_SYSTEM_UNCONFIRMED,
    READY,
    INITIALIZATION_FAILED
}

data class VehicleLocationMapModel(
    val state: VehicleLocationMapState,
    val showMarker: Boolean
)

/** Pure state gate for the future map SDK adapter. */
object VehicleLocationMapDomain {
    fun model(
        snapshot: VehicleLocationSnapshot?,
        summary: VehicleLocationSummary?,
        mapRequested: Boolean,
        initializationFailed: Boolean = false
    ): VehicleLocationMapModel {
        if (!mapRequested) return VehicleLocationMapModel(VehicleLocationMapState.NOT_REQUESTED, false)
        if (initializationFailed) return VehicleLocationMapModel(VehicleLocationMapState.INITIALIZATION_FAILED, false)
        if (snapshot == null || summary == null) {
            return VehicleLocationMapModel(VehicleLocationMapState.NO_DATA, false)
        }
        if (summary.availability == VehicleLocationAvailability.UNAVAILABLE ||
            summary.freshness == VehicleLocationFreshness.INVALID
        ) {
            return VehicleLocationMapModel(VehicleLocationMapState.INVALID, false)
        }
        if (summary.coordinateSystem == VehicleCoordinateSystem.UNCONFIRMED ||
            snapshot.location.coordinateSystem == VehicleCoordinateSystem.UNCONFIRMED
        ) {
            return VehicleLocationMapModel(VehicleLocationMapState.COORDINATE_SYSTEM_UNCONFIRMED, false)
        }
        if (summary.freshness == VehicleLocationFreshness.STALE) {
            return VehicleLocationMapModel(VehicleLocationMapState.STALE, true)
        }
        if (summary.freshness == VehicleLocationFreshness.EXPIRED) {
            return VehicleLocationMapModel(VehicleLocationMapState.EXPIRED, true)
        }
        return VehicleLocationMapModel(VehicleLocationMapState.READY, true)
    }
}
