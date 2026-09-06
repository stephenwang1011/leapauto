package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceModeTest {
    @Test
    fun defaultsToSystemForMissingOrUnknownPreference() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromPreference(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromPreference("legacy"))
    }

    @Test
    fun resolvesForcedAndSystemAppearanceModes() {
        assertEquals(true, AppearanceMode.SYSTEM.resolvesToDark(true))
        assertEquals(false, AppearanceMode.SYSTEM.resolvesToDark(false))
        assertEquals(false, AppearanceMode.LIGHT.resolvesToDark(true))
        assertEquals(true, AppearanceMode.DARK.resolvesToDark(false))
    }
}
