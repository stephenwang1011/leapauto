package com.leapauto.app

import org.junit.Assert.*
import org.junit.Test

class NightBrightnessPolicyTest {
    private val themes = listOf("7", "8", "5", "4", "future", "")

    @Test
    fun `missing legacy or malformed preference resolves to per theme default`() {
        for (theme in themes) {
            for (raw in listOf(null, "60", 60, true, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                assertEquals(NightBrightnessPolicy.defaultPercent(theme), NightBrightnessPolicy.decode(raw, theme), 0f)
            }
        }
        assertEquals(45f, NightBrightnessPolicy.defaultPercent("7"), 0f)
        assertEquals(45f, NightBrightnessPolicy.defaultPercent("8"), 0f)
        assertEquals(50f, NightBrightnessPolicy.defaultPercent("5"), 0f)
        assertEquals(50f, NightBrightnessPolicy.defaultPercent("4"), 0f)
        assertEquals(100f, NightBrightnessPolicy.defaultPercent("future"), 0f)
    }

    @Test
    fun `finite out of range storage clamps while valid values round trip`() {
        for (theme in themes) {
            assertEquals(10f, NightBrightnessPolicy.decode(-100f, theme), 0f)
            assertEquals(150f, NightBrightnessPolicy.decode(500f, theme), 0f)
            for (value in listOf(10f, 44f, 46f, 75.5f, 100f, 150f)) {
                assertEquals(value, NightBrightnessPolicy.decode(value, theme), 0f)
            }
        }
    }

    @Test
    fun `themes are isolated and original native and skybox have separate memories`() {
        assertEquals(themes.size, themes.map { NightBrightnessPolicy.key(it, true) }.toSet().size)
        assertNotEquals(NightBrightnessPolicy.key("4", true), NightBrightnessPolicy.key("4", false))
        assertEquals(NightBrightnessPolicy.key("7", true), NightBrightnessPolicy.key("7", false))
        val stored = mutableMapOf<String, Any>("old_brightness" to 20f)
        val snow = NightBrightnessPolicy.key("7", true)
        stored[snow] = 80f
        assertEquals(80f, NightBrightnessPolicy.decode(stored[snow], "7"), 0f)
        assertEquals(45f, NightBrightnessPolicy.decode(stored[NightBrightnessPolicy.key("8", true)], "8"), 0f)
        stored.remove(snow)
        assertEquals(45f, NightBrightnessPolicy.decode(stored[snow], "7"), 0f)
    }

    @Test
    fun `every renderer responds monotonically including native and original sky`() {
        for (theme in themes) for (image in listOf(false, true)) for (sky in listOf(false, true)) {
            val dark = NightThemePolicy.resolve(theme, true, image, sky, brightnessPercent = 10f)
            val bright = NightThemePolicy.resolve(theme, true, image, sky, brightnessPercent = 150f)
            assertTrue(dark.background.gain < bright.background.gain)
            assertTrue(dark.isHeroDark)
            if (dark.source == NightThemePolicy.BackgroundSource.NATIVE) {
                assertTrue(bright.isHeroDark)
            } else {
                assertFalse(bright.isHeroDark)
            }
            for (percent in listOf(10f, 100f, 150f, Float.NaN)) {
                assertTrue(NightThemePolicy.resolve(theme, false, image, sky, brightnessPercent = percent).background.isIdentity)
            }
        }
    }

    @Test
    fun `original night background uses half brightness by default and image alpha is preserved`() {
        for (sky in listOf(false, true)) {
            val background = NightThemePolicy.resolve("4", true, false, sky).background
            assertEquals(0.5f, background.gain, 0f)
            assertEquals(0f, background.lift, 0f)
        }
        for (theme in themes) for (percent in listOf(10f, 100f, 150f)) {
            val treatment = NightThemePolicy.resolve(theme, true, brightnessPercent = percent).background
            assertEquals(listOf(0f, 0f, 0f, 1f, 0f), treatment.colorMatrix().drop(15))
        }
    }

    @Test
    fun `new defaults adjust images while light mode stays unchanged`() {
        for ((theme, expectedGain) in mapOf("7" to 0.45f, "5" to 0.5f, "8" to 0.45f, "4" to 0.5f)) {
            assertEquals(expectedGain, NightThemePolicy.resolve(theme, true).background.gain, 0.00001f)
            for (source in NightThemePolicy.BackgroundSource.entries) {
                assertTrue(NightThemePolicy.resolve(theme, false, source).background.isIdentity)
            }
        }
    }

    @Test
    fun `saved brightness retains original image native and skybox rendering semantics`() {
        for ((theme, nativeBaseline) in mapOf("7" to 44f, "8" to 46f, "5" to 100f, "4" to 100f)) {
            for (percent in listOf(10f, 44f, 45f, 46f, 50f, 100f, 150f)) {
                for (source in NightThemePolicy.BackgroundSource.entries) {
                    val image = source == NightThemePolicy.BackgroundSource.IMAGE
                    val treatment = NightThemePolicy.resolve(theme, true, source, brightnessPercent = percent).background
                    val expectedGain = if (image) percent / 100f else percent / nativeBaseline
                    val expectedLift = if (!image) (expectedGain - 1f).coerceAtLeast(0f) else when (theme) {
                        "7" -> 0.025f
                        "8" -> 0.015f
                        else -> 0f
                    }
                    assertEquals("theme=$theme source=$source percent=$percent", expectedGain, treatment.gain, 0.00001f)
                    assertEquals(expectedLift, treatment.lift, 0.00001f)
                }
            }
        }
    }
}
