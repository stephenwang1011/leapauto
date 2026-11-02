package com.leapauto.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.leapauto.app.ui.theme.DarkColors
import com.leapauto.app.ui.theme.LightColors
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIconStyleTest {
    @Test
    fun `category icons retain non text contrast over tinted circles in both themes`() {
        for (isDark in listOf(false, true)) {
            val scheme = if (isDark) DarkColors else LightColors
            val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceContainerHighest)
            for (tone in SettingsIconTone.entries) {
                val foreground = tone.color(isDark)
                for (surface in surfaces) {
                    val background = foreground.copy(alpha = 0.12f).compositeOver(surface)
                    val contrast = contrast(foreground, background)
                    assertTrue("$tone dark=$isDark contrast=$contrast", contrast >= 3f)
                }
            }
        }
    }

    @Test
    fun `dark mode category accents are lighter than their light mode variants`() {
        for (tone in SettingsIconTone.entries) {
            assertTrue(tone.color(true).luminance() > tone.color(false).luminance())
        }
    }

    @Test
    fun `settings cards wire subtle upper right aura per module icon tone`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(java.io.File(workingDirectory)) { it.parentFile }
            .first { java.io.File(it, "app").isDirectory }
        val screenSource = java.io.File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("settingsGlassCard(tone = SettingsIconTone.KEY)"))
        assertTrue(screenSource.contains("settingsGlassCard(tone = SettingsIconTone.THEME)"))
        assertTrue(screenSource.contains("settingsGlassCard(tone = SettingsIconTone.SUPPORT)"))
        assertTrue(screenSource.contains("settingsGlassCard(tone = SettingsIconTone.CODE)"))
        assertTrue(screenSource.contains("settingsGlassCard(auraColor = statusColor.copy"))
        assertTrue(screenSource.contains("settingsGlassCard(auraColor = amapAura)"))
    }

    private fun contrast(first: Color, second: Color): Float =
        (maxOf(first.luminance(), second.luminance()) + 0.05f) /
            (minOf(first.luminance(), second.luminance()) + 0.05f)
}
