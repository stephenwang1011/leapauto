package com.leapauto.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeutralThemeTest {
    @Test
    fun `glass layers remain neutral and distinguishable`() {
        val colors = listOf(GlassSurfaceLight, GlassInsetSurfaceLight, GlassSurfaceDark, GlassInsetSurfaceDark)
        for (color in colors) {
            assertEquals(color.red, color.green, 0.001f)
            assertEquals(color.green, color.blue, 0.001f)
        }
        assertTrue(GlassSurfaceLight.luminance() > GlassInsetSurfaceLight.luminance())
        assertTrue(GlassInsetSurfaceDark.luminance() > GlassSurfaceDark.luminance())
    }

    @Test
    fun `neutral action and surface colors have no blue tint in either theme`() {
        for (scheme in listOf(LightColors, DarkColors)) {
            val colors = listOf(
                scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer,
                scheme.secondary, scheme.onSecondary, scheme.secondaryContainer, scheme.onSecondaryContainer,
                scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer,
                scheme.background, scheme.onBackground, scheme.surface, scheme.onSurface,
                scheme.surfaceVariant, scheme.onSurfaceVariant, scheme.surfaceTint,
                scheme.surfaceContainerLowest, scheme.surfaceContainerLow, scheme.surfaceContainer,
                scheme.surfaceContainerHigh, scheme.surfaceContainerHighest, scheme.surfaceDim,
                scheme.surfaceBright, scheme.outline, scheme.outlineVariant,
                scheme.inverseSurface, scheme.inverseOnSurface, scheme.inversePrimary
            )
            for (color in colors) {
                assertEquals("red and green differ: $color", color.red, color.green, 0.001f)
                assertEquals("green and blue differ: $color", color.green, color.blue, 0.001f)
            }
        }
    }

    @Test
    fun `action labels and surface text meet AA contrast in both themes`() {
        for (scheme in listOf(LightColors, DarkColors)) {
            val pairs = listOf(
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondary to scheme.secondary,
                scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onTertiary to scheme.tertiary,
                scheme.onTertiaryContainer to scheme.tertiaryContainer,
                scheme.onSurface to scheme.surface,
                scheme.onSurfaceVariant to scheme.surfaceVariant,
                scheme.onBackground to scheme.background,
                scheme.inverseOnSurface to scheme.inverseSurface,
                scheme.primary to scheme.background,
                scheme.onSurface to scheme.surfaceContainerHighest
            )
            for ((foreground, background) in pairs) {
                val luminances = listOf(foreground.luminance(), background.luminance())
                val contrast = (luminances.max() + 0.05f) / (luminances.min() + 0.05f)
                assertTrue("foreground=$foreground background=$background contrast=$contrast", contrast >= 4.5f)
            }
        }
    }

    @Test
    fun `vehicle status colors retain green healthy red alert and orange warning semantics`() {
        assertEquals(Color(0xFF00C853), EnergyGreen)
        assertEquals(Color(0xFFFF3B30), AlertRed)
        for (color in listOf(StatusGoodLight, StatusGoodDark)) {
            assertTrue(color.green > color.red && color.green > color.blue)
        }
        for (color in listOf(StatusBadLight, StatusBadDark)) {
            assertTrue(color.red > color.green && color.red > color.blue)
        }
        for (color in listOf(StatusWarnLight, StatusWarnDark)) {
            assertTrue(color.red > color.green && color.green > color.blue)
        }
    }
}
