package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightBackgroundSelectionTest {
    private fun select(theme: String, show3D: Boolean, hasImage: Boolean) = NightThemePolicy.selectBackground(
        themeId = theme, show3D = show3D, hasThemeBackground = hasImage
    )

    @Test
    fun `images serve 2d and custom 3d while missing images always get a native backdrop`() {
        for (theme in listOf("7", "8", "5", "future")) {
            for (show3D in listOf(false, true)) {
                assertEquals(NightThemePolicy.BackgroundSource.IMAGE, select(theme, show3D, true))
                assertEquals(NightThemePolicy.BackgroundSource.NATIVE, select(theme, show3D, false))
            }
        }
    }

    @Test
    fun `only an actual original skybox replaces the native or image backdrop`() {
        assertEquals(NightThemePolicy.BackgroundSource.ORIGINAL_SKYBOX, select("4", true, false))
        assertEquals(NightThemePolicy.BackgroundSource.ORIGINAL_SKYBOX, select("4", true, true))
        assertEquals(NightThemePolicy.BackgroundSource.NATIVE, select("4", false, false))
        assertEquals(NightThemePolicy.BackgroundSource.IMAGE, select("4", false, true))
    }

    @Test
    fun `missing custom 3d artwork shares native treatment foreground and transition with 2d preview`() {
        for (theme in listOf("7", "8", "5", "future")) {
            val home = select(theme, true, false)
            val preview = select(theme, false, false)
            for (dark in listOf(false, true)) {
                for (percent in listOf(10f, NightBrightnessPolicy.defaultPercent(theme), 150f)) {
                    val result = NightThemePolicy.resolve(theme, dark, home, brightnessPercent = percent)
                    assertEquals(result, NightThemePolicy.resolve(theme, dark, preview, brightnessPercent = percent))
                    assertEquals(NightThemePolicy.resolve(theme, dark, false, false, brightnessPercent = percent), result)
                    assertEquals(dark, home.transitionBehindVehicle(dark))
                    assertFalse(result.useNightSkybox)
                    assertEquals(if (dark) 120 else 24, result.transitionHeightDp)
                    if (!dark) assertTrue(result.background.isIdentity)
                }
            }
            val low = NightThemePolicy.resolve(theme, true, home, brightnessPercent = 10f)
            val high = NightThemePolicy.resolve(theme, true, home, brightnessPercent = 150f)
            assertTrue(low.background.gain < high.background.gain)
            assertTrue(low.isHeroDark)
            assertTrue(high.isHeroDark)
        }
    }

    @Test
    fun `image arrival and original 3d fallback recompute presentation from selected background`() {
        val missing = NightThemePolicy.resolve("7", true, select("7", true, false))
        val loaded = NightThemePolicy.resolve("7", true, select("7", true, true))
        assertEquals(45f / 44f, missing.background.gain, 0.00001f)
        assertEquals(NightThemePolicy.BackgroundSource.NATIVE, missing.source)
        assertEquals(0.45f, loaded.background.gain, 0.00001f)
        assertEquals(NightThemePolicy.BackgroundSource.IMAGE, loaded.source)
        assertTrue(select("7", true, true).transitionBehindVehicle(true))
        val sky = select("4", true, false)
        val native = select("4", false, false)
        val skyPresentation = NightThemePolicy.resolve("4", true, sky)
        val nativePresentation = NightThemePolicy.resolve("4", true, native)
        assertTrue(skyPresentation.useNightSkybox)
        assertFalse(nativePresentation.useNightSkybox)
        assertEquals(NightThemePolicy.TransitionPlacement.FOREGROUND, skyPresentation.transitionPlacement)
        assertEquals(NightThemePolicy.TransitionPlacement.BEHIND_VEHICLE, nativePresentation.transitionPlacement)
        assertFalse(sky.transitionBehindVehicle(true))
        assertTrue(native.transitionBehindVehicle(true))
        for (source in NightThemePolicy.BackgroundSource.entries) {
            assertFalse(source.transitionBehindVehicle(false))
        }
    }

    @Test
    fun `all supported home themes use an explicit dark source without a light fallback`() {
        for (theme in listOf("7", "8", "5", "4")) {
            for (show3D in listOf(false, true)) {
                for (hasImage in listOf(false, true)) {
                    val source = select(theme, show3D, hasImage)
                    val presentation = NightThemePolicy.resolve(theme, true, source)
                    assertEquals(source, presentation.source)
                    if (source == NightThemePolicy.BackgroundSource.NATIVE) {
                        assertTrue("theme=$theme source=$source", presentation.isHeroDark)
                        val expectedGain = when (theme) {
                            "7" -> 45f / 44f
                            "8" -> 45f / 46f
                            else -> 0.5f
                        }
                        assertEquals(expectedGain, presentation.background.gain, 0.00001f)
                        assertEquals(
                            NightThemePolicy.TransitionPlacement.BEHIND_VEHICLE,
                            presentation.transitionPlacement
                        )
                    } else {
                        assertEquals(
                            if (source == NightThemePolicy.BackgroundSource.ORIGINAL_SKYBOX) {
                                NightThemePolicy.TransitionPlacement.FOREGROUND
                            } else {
                                NightThemePolicy.TransitionPlacement.BEHIND_VEHICLE
                            },
                            presentation.transitionPlacement
                        )
                    }
                }
            }
        }
    }
}
