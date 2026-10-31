package com.leapauto.app

import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightThemePolicyTest {
    private val themeIds = listOf("4", "5", "7", "8", "future", "")

    @Test
    fun `light artwork is unchanged for every theme and fallback`() {
        for (theme in themeIds) {
            for (hasBackground in listOf(false, true)) {
                val result = NightThemePolicy.resolve(theme, false, hasBackground, theme == "4")
                assertTrue(result.background.isIdentity)
                assertEquals(theme == "5" && hasBackground, result.isHeroDark)
                assertFalse(result.useNightSkybox)
                assertEquals(24, result.transitionHeightDp)
            }
        }
    }

    @Test
    fun `light stone uses light foreground while snow and plaza keep dark foreground`() {
        assertTrue(NightThemePolicy.resolve("5", false, originalHeroDark = false).isHeroDark)
        for (theme in listOf("7", "8")) {
            assertFalse(NightThemePolicy.resolve(theme, false, originalHeroDark = false).isHeroDark)
        }
    }

    @Test
    fun `light missing artwork follows native fallback but original 3d preserves its tone`() {
        for (theme in themeIds) {
            assertFalse(NightThemePolicy.resolve(theme, false, false, false, true).isHeroDark)
        }
        for (tone in listOf(false, true)) {
            val sky = NightThemePolicy.resolve("4", false, false, true, tone)
            assertEquals(tone, sky.isHeroDark)
            assertFalse(sky.useNightSkybox)
            assertTrue(sky.background.isIdentity)
        }
    }

    @Test
    fun `unknown light artwork preserves supplied manager tone and night uses resolved tone`() {
        for (theme in listOf("future", "")) {
            for (tone in listOf(false, true)) {
                assertEquals(tone, NightThemePolicy.resolve(theme, false, originalHeroDark = tone).isHeroDark)
                assertFalse(NightThemePolicy.resolve(theme, true, originalHeroDark = tone).isHeroDark)
            }
        }
    }

    @Test
    fun `snow and plaza retain detail with distinct dusk treatments`() {
        val snow = NightThemePolicy.resolve("7", true)
        val plaza = NightThemePolicy.resolve("8", true)
        assertFalse(snow.background.isIdentity)
        assertFalse(plaza.background.isIdentity)
        assertEquals(0.45f, snow.background.gain, 0f)
        assertEquals(0.45f, plaza.background.gain, 0f)
        assertTrue(snow.background.saturation < plaza.background.saturation)
        for (result in listOf(snow, plaza)) {
            assertTrue(result.isHeroDark)
            val shadow = transform(result.background, 0f, 0f, 0f)
            val midtone = transform(result.background, 0.5f, 0.5f, 0.5f)
            val highlight = transform(result.background, 1f, 1f, 1f)
            assertTrue(shadow[0] > 0f)
            assertTrue(shadow[0] < midtone[0] && midtone[0] < highlight[0])
            assertTrue(highlight[0] < 0.5f)
        }
    }

    @Test
    fun `stone and original sky default to half brightness in the night environment`() {
        val stone = NightThemePolicy.resolve("5", true)
        assertEquals(0.5f, stone.background.gain, 0f)
        assertTrue(stone.isHeroDark)
        assertFalse(stone.useNightSkybox)
        val sky = NightThemePolicy.resolve("4", true, false, true)
        assertEquals(0.5f, sky.background.gain, 0f)
        assertTrue(sky.useNightSkybox)
        assertTrue(sky.isHeroDark)
    }

    @Test
    fun `missing background and no 3d use native dark fallback with theme brightness`() {
        for (theme in themeIds) {
            val result = NightThemePolicy.resolve(theme, true, false, false)
            val expectedGain = when (theme) {
                "7" -> 45f / 44f
                "8" -> 45f / 46f
                "4", "5" -> 0.5f
                else -> 1f
            }
            assertEquals(expectedGain, result.background.gain, 0.00001f)
            assertTrue(result.isHeroDark)
            assertEquals(NightThemePolicy.BackgroundSource.NATIVE, result.source)
            assertEquals(NightThemePolicy.TransitionPlacement.BEHIND_VEHICLE, result.transitionPlacement)
            assertFalse(result.useNightSkybox)
            assertTrue(result.transitionHeightDp >= 100)
        }
    }

    @Test
    fun `legacy unsupported stone selection is treated as its actual built in snow artwork`() {
        val actualTheme = VehicleThemePolicy.activeThemeId(false, "5")
        assertEquals("7", actualTheme)
        assertFalse(NightThemePolicy.resolve(actualTheme, true).background.isIdentity)
        assertFalse(NightThemePolicy.resolve(actualTheme, false).isHeroDark)
        assertTrue(NightThemePolicy.resolve(actualTheme, true).isHeroDark)
        val original = VehicleThemePolicy.activeThemeId(false, "4")
        assertEquals(0.5f, NightThemePolicy.resolve(original, true, false).background.gain, 0f)
    }

    @Test
    fun `unknown artwork retains neutral default and does not assume white text`() {
        for (theme in listOf("future", "")) {
            assertTrue(NightThemePolicy.resolve(theme, true).background.isIdentity)
            assertFalse(NightThemePolicy.resolve(theme, true).isHeroDark)
        }
    }

    @Test
    fun `resolved foreground meets AA over protected text area across brightness range`() {
        for (theme in themeIds) for (percent in listOf(10f, 44f, 46f, 100f, 150f)) {
            val result = NightThemePolicy.resolve(theme, true, brightnessPercent = percent)
            val treatment = result.background
            for (r in listOf(0f, 0.5f, 1f)) {
                for (g in listOf(0f, 0.5f, 1f)) {
                    for (b in listOf(0f, 0.5f, 1f)) {
                        val backing = if (result.isHeroDark) floatArrayOf(27f/255f, 29f/255f, 32f/255f) else floatArrayOf(1f, 1f, 1f)
                        val raw = transform(treatment, r, g, b)
                        val background = FloatArray(3) { backing[it] * 0.94f + raw[it].coerceIn(0f, 1f) * 0.06f }
                        for (alpha in listOf(1f, 0.90f)) {
                            val text = if (result.isHeroDark) floatArrayOf(1f, 1f, 1f) else floatArrayOf(74f/255f, 85f/255f, 104f/255f)
                            val foreground = FloatArray(3) { text[it] * alpha + background[it] * (1f - alpha) }
                            val a = luminance(foreground)
                            val bLum = luminance(background)
                            val contrast = (maxOf(a, bLum) + 0.05) / (minOf(a, bLum) + 0.05)
                            assertTrue("theme=$theme contrast=$contrast", contrast >= 4.5)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `snow theme in dark mode resolves to dark hero with white foreground`() {
        val nightSnow = NightThemePolicy.resolve("7", isDark = true)
        assertTrue("雪境在深色模式下必须判定为暗底", nightSnow.isHeroDark)
        val lightSnow = NightThemePolicy.resolve("7", isDark = false)
        assertFalse("雪境在浅色模式下保持亮底", lightSnow.isHeroDark)
    }

    @Test
    fun `color matrix preserves alpha and light identity`() {
        for (theme in themeIds) {
            val matrix = NightThemePolicy.resolve(theme, true).background.colorMatrix()
            assertEquals(listOf(0f, 0f, 0f, 1f, 0f), matrix.drop(15))
        }
        val input = floatArrayOf(0.2f, 0.4f, 0.8f)
        val output = transform(NightThemePolicy.resolve("7", false).background, input[0], input[1], input[2])
        input.indices.forEach { assertEquals(input[it], output[it], 0.00001f) }
    }

    private fun transform(treatment: NightThemePolicy.BackgroundTreatment, r: Float, g: Float, b: Float): FloatArray {
        val matrix = treatment.colorMatrix()
        return FloatArray(3) { row ->
            val offset = row * 5
            matrix[offset] * r + matrix[offset + 1] * g + matrix[offset + 2] * b + matrix[offset + 4] / 255f
        }
    }

    private fun luminance(rgb: FloatArray): Double {
        val linear = rgb.map { if (it <= 0.04045f) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722
    }
}
