package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleThemePolicyTest {
    @Test
    fun `non 3d defaults to original and ignores remote theme`() {
        listOf(null, "", "  ").forEach { local ->
            listOf(null, "4", "7", "5", "8").forEach { remote ->
                assertEquals("4", VehicleThemePolicy.activeThemeId(false, local, remote))
            }
        }
    }

    @Test
    fun `local choices win and legacy unsupported ids retain snow appearance`() {
        listOf("4", "7", "5", "8", "unknown").forEach { local ->
            val expected = if (local == "4") "4" else "7"
            assertEquals(expected, VehicleThemePolicy.activeThemeId(false, local, "4"))
            assertEquals(local, VehicleThemePolicy.activeThemeId(true, local, "7"))
        }
    }

    @Test
    fun `3d cloud and default resolution remain unchanged`() {
        assertEquals("7", VehicleThemePolicy.activeThemeId(true, null))
        assertEquals("5", VehicleThemePolicy.activeThemeId(true, "", "5"))
        assertEquals("4", VehicleThemePolicy.activeThemeId(true, "4", "7"))
        assertEquals("7", VehicleThemePolicy.activeThemeId(true, null, ""))
    }

    @Test
    fun `non 3d list is two offline choices regardless of cached catalog`() {
        val catalog = ThemeManager.PRESET_THEMES
        listOf(emptyList(), catalog, catalog.reversed()).forEach { source ->
            val choices = VehicleThemePolicy.availableThemes(false, source)
            assertEquals(listOf("4", "7"), choices.map { it.themeId })
            assertEquals(listOf("原厂默认", "雾川雪境"), choices.map { it.name })
            assertEquals(listOf(true, false), choices.map { it.isDefault })
            assertTrue(choices.all { it.resourcePkg == null && it.resourceBg == null })
        }
        assertEquals(catalog, VehicleThemePolicy.availableThemes(true, catalog))
        assertEquals("层峦叠嶂", VehicleThemePolicy.displayName(true, "4", catalog))
        assertEquals("原厂默认", VehicleThemePolicy.displayName(false, "4", catalog))
        assertEquals("雾川雪境", VehicleThemePolicy.displayName(false, "8", catalog))
    }

    @Test
    fun `home and widgets select original or built in snow without downloads`() {
        assertEquals(VehicleThemePolicy.Background.ORIGINAL, VehicleThemePolicy.background(false, "4"))
        assertEquals(VehicleThemePolicy.Background.ORIGINAL, VehicleThemePolicy.background(false, ""))
        listOf("7", "5", "8", "unknown").forEach {
            assertEquals(VehicleThemePolicy.Background.BUILT_IN_SNOW, VehicleThemePolicy.background(false, it))
        }
        assertEquals(VehicleThemePolicy.Background.ORIGINAL, VehicleThemePolicy.background(true, "4"))
        listOf("7", "5", "8").forEach {
            assertEquals(VehicleThemePolicy.Background.DOWNLOADED, VehicleThemePolicy.background(true, it))
        }
    }

    @Test
    fun `metadata not rendering success determines capability`() {
        assertTrue(VehicleThemePolicy.supports3d(null, false, null))
        listOf(null, "", "  ").forEach {
            assertFalse(VehicleThemePolicy.supports3d(null, true, it))
        }
        assertTrue(VehicleThemePolicy.supports3d(null, true, "fixture-runtime"))
        assertFalse(VehicleThemePolicy.supports3d(false, false, null))
        assertTrue(VehicleThemePolicy.supports3d(true, false, null))
    }

    @Test
    fun `late downloads cannot replace a local choice or another vehicle`() {
        assertTrue(VehicleThemePolicy.canApplyDownloadResult("a", "a", "7", "7", true))
        assertFalse(VehicleThemePolicy.canApplyDownloadResult("a", "a", "7", "4", true))
        assertFalse(VehicleThemePolicy.canApplyDownloadResult("a", "b", "7", "7", true))
        assertFalse(VehicleThemePolicy.canApplyDownloadResult("a", "a", "7", "7", false))
        assertFalse(VehicleThemePolicy.canApplyDownloadResult("", "", "7", "7", true))
    }
}
