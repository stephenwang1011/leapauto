package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetControlSecurityTest {

    @Test
    fun `missing preference defaults to verification for sensitive widget actions`() {
        assertTrue(WidgetControlSecurity.DEFAULT_VERIFICATION_ENABLED)
        assertTrue(WidgetControlSecurity.requiresVerification("unlock", true))
        assertTrue(WidgetControlSecurity.requiresVerification("trunkOpen", true))
    }

    @Test
    fun `explicitly disabled preference sends only sensitive actions directly`() {
        assertFalse(WidgetControlSecurity.requiresVerification("unlock", false))
        assertFalse(WidgetControlSecurity.requiresVerification("trunkOpen", false))
    }

    @Test
    fun `other widget controls never use this verification gate`() {
        listOf("lock", "acOn", "acOff", "trunkClose").forEach { command ->
            assertFalse(WidgetControlSecurity.requiresVerification(command, true))
            assertFalse(WidgetControlSecurity.requiresVerification(command, false))
        }
    }
}
