package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleConfigConfirmationTest {
    @Test
    fun `missing and older schema require confirmation`() {
        assertFalse(VehicleConfigConfirmationPolicy.isConfirmed(0))
        assertFalse(VehicleConfigConfirmationPolicy.isConfirmed(1))
        assertFalse(VehicleConfigConfirmationPolicy.isConfirmed(VehicleConfigConfirmationPolicy.SCHEMA_VERSION - 1))
    }

    @Test
    fun `confirmation preference keys are isolated by vin`() {
        val firstVehicleKey = VehicleConfigConfirmationPolicy.confirmationPreferenceKey("VIN-A")
        val secondVehicleKey = VehicleConfigConfirmationPolicy.confirmationPreferenceKey("VIN-B")
        assertTrue(firstVehicleKey != secondVehicleKey)
        assertEquals(null, VehicleConfigConfirmationPolicy.confirmationPreferenceKey(" "))
    }

    @Test
    fun `current and newer schema stay confirmed`() {
        assertTrue(VehicleConfigConfirmationPolicy.isConfirmed(VehicleConfigConfirmationPolicy.SCHEMA_VERSION))
        assertTrue(VehicleConfigConfirmationPolicy.isConfirmed(VehicleConfigConfirmationPolicy.SCHEMA_VERSION + 1))
    }

    @Test
    fun `valid confirmation requires model four digit year and power type`() {
        assertTrue(
            VehicleConfigConfirmationPolicy.isValid(
                model = "C16",
                modelYear = "2026",
                powerType = SessionStore.VehiclePowerType.PURE_ELECTRIC,
                color = "photoelectric_white"
            )
        )
        assertFalse(VehicleConfigConfirmationPolicy.isValid("", "2026", SessionStore.VehiclePowerType.PURE_ELECTRIC, "photoelectric_white"))
        assertFalse(VehicleConfigConfirmationPolicy.isValid("C16", "26", SessionStore.VehiclePowerType.PURE_ELECTRIC, "photoelectric_white"))
        assertFalse(VehicleConfigConfirmationPolicy.isValid("C16", "2026", null, "photoelectric_white"))
        assertFalse(VehicleConfigConfirmationPolicy.isValid("未知车型", "2026", SessionStore.VehiclePowerType.PURE_ELECTRIC, "photoelectric_white"))
        assertTrue(VehicleConfigConfirmationPolicy.isValid("C16", "2026", SessionStore.VehiclePowerType.PURE_ELECTRIC, "metal_black_add_crown"))
    }

    @Test
    fun `reported cartype is normalized to a supported model`() {
        assertEquals("C16", VehicleConfigConfirmationPolicy.normalizeModel("零跑 C16"))
        assertEquals("C11", VehicleConfigConfirmationPolicy.normalizeModel("老款C11 纯电"))
    }

    @Test
    fun `confirmation schema is persisted only for a complete valid configuration`() {
        assertEquals(
            VehicleConfigConfirmationPolicy.SCHEMA_VERSION,
            VehicleConfigConfirmationPolicy.schemaToPersist(
                "C11",
                "2025",
                SessionStore.VehiclePowerType.PURE_ELECTRIC,
                "photoelectric_white"
            )
        )
        assertEquals(
            null,
            VehicleConfigConfirmationPolicy.schemaToPersist("C11", "2025", SessionStore.VehiclePowerType.PURE_ELECTRIC)
        )
    }

    @Test
    fun `initial prompt only requires vehicle configuration`() {
        assertEquals(
            PostLoginPrompt.VEHICLE_CONFIG,
            VehicleConfigConfirmationPolicy.initialPrompt(
                vehicleConfigConfirmed = false
            )
        )
        assertEquals(
            PostLoginPrompt.NONE,
            VehicleConfigConfirmationPolicy.initialPrompt(
                vehicleConfigConfirmed = true
            )
        )
    }

    @Test
    fun `prompt delays match product contract`() {
        assertEquals(10_000L, PostLoginPromptTiming.AUTHOR_SUPPORT_IDLE_MS)
    }

    @Test
    fun `author support idle timer only arms in an unblocked foreground session`() {
        val eligible = AuthorSupportPromptContext(
            activityAlive = true,
            foreground = true,
            loggedIn = true,
            busy = false,
            pinPromptVisible = false,
            pinSetupInProgress = false,
            vehicleConfigPromptVisible = false,
            sessionExpiredPromptVisible = false,
            authorSupportPromptVisible = false,
            authorSupportScreenOpening = false,
            reminderEligibleToday = true
        )
        assertTrue(AuthorSupportIdlePolicy.canArm(eligible))
        assertFalse(AuthorSupportIdlePolicy.canArm(eligible.copy(foreground = false)))
        assertFalse(AuthorSupportIdlePolicy.canArm(eligible.copy(busy = true)))
        assertFalse(AuthorSupportIdlePolicy.canArm(eligible.copy(pinPromptVisible = true)))
        assertFalse(AuthorSupportIdlePolicy.canArm(eligible.copy(pinSetupInProgress = true)))
        assertFalse(AuthorSupportIdlePolicy.canArm(eligible.copy(vehicleConfigPromptVisible = true)))
        assertFalse(AuthorSupportIdlePolicy.canArm(eligible.copy(sessionExpiredPromptVisible = true)))
        assertFalse(AuthorSupportIdlePolicy.canArm(eligible.copy(reminderEligibleToday = false)))
    }
}
