package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleLocationTest {

    @Test
    fun `prefers complete named primary pair`() {
        val selected = VehicleLocationDomain.selectFromDecodedOrRaw(
            mapOf(
                "latitude" to "32.094512",
                "longitude" to 112.127561
            )
        )

        assertEquals(VehicleLocationSource.PRIMARY, selected.source)
        assertEquals("32.094512", selected.pair.latitude)
        assertEquals(112.127561, selected.pair.longitude)
    }

    @Test
    fun `uses numeric primary signals when named fields are absent`() {
        val selected = VehicleLocationDomain.selectFromDecodedOrRaw(
            mapOf("3725" to 32.094512, "3724" to 112.127561)
        )

        assertEquals(VehicleLocationSource.PRIMARY, selected.source)
        assertEquals(32.094512, selected.pair.latitude)
        assertEquals(112.127561, selected.pair.longitude)
    }

    @Test
    fun `ignores unverified coordinate signals`() {
        val validation = VehicleLocationDomain.validate(
            mapOf("2190" to 32.094512, "2191" to 112.127561)
        )

        assertEquals(
            VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.MISSING_LATITUDE),
            validation
        )
    }

    @Test
    fun `primary pair missing one coordinate is invalid`() {
        val validation = VehicleLocationDomain.validate(
            mapOf("latitude" to 32.094512)
        )

        assertEquals(
            VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.MISSING_LONGITUDE),
            validation
        )
    }

    @Test
    fun `rejects missing non numeric out of range and zero coordinates`() {
        assertEquals(
            VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.MISSING_LATITUDE),
            VehicleLocationDomain.validate(mapOf("longitude" to 112.0))
        )
        assertEquals(
            VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.NON_NUMERIC),
            VehicleLocationDomain.validate(mapOf("latitude" to "unknown", "longitude" to 112.0))
        )
        assertEquals(
            VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.OUT_OF_RANGE),
            VehicleLocationDomain.validate(mapOf("latitude" to 91.0, "longitude" to 112.0))
        )
        assertEquals(
            VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.ZERO_COORDINATE),
            VehicleLocationDomain.validate(mapOf("latitude" to 0.0, "longitude" to 0.0))
        )
    }

    @Test
    fun `marks obvious jump invalid within configured interval`() {
        val previous = VehicleLocationSnapshot(
            location = VehicleLocation(32.0, 112.0, VehicleLocationSource.PRIMARY),
            queriedAtEpochMs = 1_000L
        )

        val validation = VehicleLocationDomain.validate(
            mapOf("latitude" to 35.0, "longitude" to 112.0),
            previous = previous,
            queriedAtEpochMs = 2_000L,
            policy = VehicleLocationValidationPolicy(maxJumpKm = 100.0, maxJumpIntervalMs = 60_000L)
        )

        assertEquals(
            VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.OBVIOUS_JUMP),
            validation
        )
    }

    @Test
    fun `does not apply jump guard when sample interval is outside configured window`() {
        val previous = VehicleLocationSnapshot(
            location = VehicleLocation(32.0, 112.0, VehicleLocationSource.PRIMARY),
            queriedAtEpochMs = 1_000L
        )

        val validation = VehicleLocationDomain.validate(
            mapOf("latitude" to 35.0, "longitude" to 112.0),
            previous = previous,
            queriedAtEpochMs = 120_000L,
            policy = VehicleLocationValidationPolicy(maxJumpKm = 100.0, maxJumpIntervalMs = 60_000L)
        )

        assertTrue(validation is VehicleLocationValidation.Valid)
    }

    @Test
    fun `keeps coordinate system explicitly unconfirmed`() {
        val validation = VehicleLocationDomain.validate(
            mapOf("latitude" to 32.094512, "longitude" to 112.127561)
        )

        val valid = validation as VehicleLocationValidation.Valid
        assertEquals(VehicleCoordinateSystem.UNCONFIRMED, valid.location.coordinateSystem)
    }

    @Test
    fun `classifies snapshot freshness and rejects future timestamps`() {
        val snapshot = VehicleLocationSnapshot(
            location = VehicleLocation(32.0, 112.0, VehicleLocationSource.PRIMARY),
            queriedAtEpochMs = 1_000L
        )
        val policy = VehicleLocationFreshnessPolicy(
            freshForMs = 10L,
            staleAfterMs = 20L,
            expiresAfterMs = 30L
        )

        assertEquals(VehicleLocationFreshness.FRESH, VehicleLocationDomain.freshness(snapshot, 1_010L, policy))
        assertEquals(VehicleLocationFreshness.STALE, VehicleLocationDomain.freshness(snapshot, 1_020L, policy))
        assertEquals(VehicleLocationFreshness.EXPIRED, VehicleLocationDomain.freshness(snapshot, 1_030L, policy))
        assertEquals(VehicleLocationFreshness.EXPIRED, VehicleLocationDomain.freshness(snapshot, 1_031L, policy))
        assertEquals(VehicleLocationFreshness.INVALID, VehicleLocationDomain.freshness(snapshot, 999L, policy))
        assertEquals(VehicleLocationFreshness.INVALID, VehicleLocationDomain.freshness(null, 1_000L, policy))
    }

    @Test
    fun `missing refresh retains the last trusted snapshot`() {
        val previous = VehicleLocationSnapshot(
            location = VehicleLocation(
                latitude = 32.094512,
                longitude = 112.127561,
                source = VehicleLocationSource.VERIFIED_SIGNAL_MAP,
                coordinateSystem = VehicleCoordinateSystem.GCJ02
            ),
            queriedAtEpochMs = 1_000L,
            receivedAtEpochMs = 1_000L
        )

        assertEquals(
            previous,
            VehicleLocationDomain.retainLastTrustedSnapshot(previous, candidate = null)
        )
    }

    @Test
    fun `snapshot is created only from valid validation`() {
        val valid = VehicleLocationDomain.validate(
            mapOf("latitude" to 32.094512, "longitude" to 112.127561)
        )
        val invalid = VehicleLocationDomain.validate(
            mapOf("latitude" to 0.0, "longitude" to 0.0)
        )

        assertEquals(2_000L, VehicleLocationDomain.snapshot(valid, 2_000L)?.queriedAtEpochMs)
        assertEquals(null, VehicleLocationDomain.snapshot(invalid, 2_000L))
    }
    @Test
    fun invalidRefreshSummaryIsUnavailableAndCannotReusePreviousLocation() {
        val valid = VehicleLocationDomain.validate(
            mapOf("latitude" to 32.094512, "longitude" to 112.127561)
        )
        val previousSnapshot = VehicleLocationDomain.snapshot(valid, 1_000L)
        val invalid = VehicleLocationDomain.validate(
            mapOf("3725" to 0.0, "3724" to 0.0),
            previous = previousSnapshot,
            queriedAtEpochMs = 2_000L
        )

        assertEquals(null, VehicleLocationDomain.snapshot(invalid, 2_000L))
        val summary = VehicleLocationSummary.fromValidation(invalid, 2_000L)
        assertEquals(VehicleLocationAvailability.UNAVAILABLE, summary.availability)
        assertEquals(VehicleLocationFreshness.INVALID, summary.freshness)
    }

    @Test
    fun `p2 validates only the verified raw signalMap pair`() {
        val validation = VehicleLocationDomain.validateVerifiedSignalMap(
            mapOf("2" to 112.127561, "3" to 32.094512)
        )

        val valid = validation as VehicleLocationValidation.Valid
        assertEquals(VehicleLocationSource.VERIFIED_SIGNAL_MAP, valid.location.source)
        assertEquals(VehicleCoordinateSystem.GCJ02, valid.location.coordinateSystem)
        assertEquals(32.094512, valid.location.latitude, 0.000001)
        assertEquals(112.127561, valid.location.longitude, 0.000001)

        assertEquals(
            VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.MISSING_LATITUDE),
            VehicleLocationDomain.validateVerifiedSignalMap(
                mapOf("3725" to 32.094512, "3724" to 112.127561)
            )
        )
    }

    @Test
    fun `resolves sts seconds and milliseconds without trusting invalid values`() {
        assertEquals(
            1_700_000_000_000L,
            VehicleLocationTimestamp.resolveEpochMs(1_700_000_000L, 9_000L)
        )
        assertEquals(
            1_700_000_000_000L,
            VehicleLocationTimestamp.resolveEpochMs(1_700_000_000_000L, 9_000L)
        )
        assertEquals(9_000L, VehicleLocationTimestamp.resolveEpochMs("unknown", 9_000L))
    }

    @Test
    fun `map marker requires user request fresh data and confirmed coordinate system`() {
        val validation = VehicleLocationDomain.validateVerifiedSignalMap(
            mapOf("2" to 112.127561, "3" to 32.094512)
        )
        val snapshot = VehicleLocationDomain.snapshot(validation, 1_000L)!!
        val summary = VehicleLocationSummary.fromValidation(validation, 1_000L)

        assertEquals(
            VehicleLocationMapState.NOT_REQUESTED,
            VehicleLocationMapDomain.model(snapshot, summary, mapRequested = false).state
        )
        assertEquals(
            VehicleLocationMapState.READY,
            VehicleLocationMapDomain.model(snapshot, summary, mapRequested = true).state
        )
        assertTrue(
            VehicleLocationMapDomain.model(snapshot, summary, mapRequested = true).showMarker
        )
        assertEquals(
            VehicleLocationMapState.EXPIRED,
            VehicleLocationMapDomain.model(
                snapshot,
                summary.copy(
                    freshness = VehicleLocationFreshness.EXPIRED
                ),
                mapRequested = true
            ).state
        )
        assertTrue(
            VehicleLocationMapDomain.model(
                snapshot,
                summary.copy(freshness = VehicleLocationFreshness.EXPIRED),
                mapRequested = true
            ).showMarker
        )
    }

}
