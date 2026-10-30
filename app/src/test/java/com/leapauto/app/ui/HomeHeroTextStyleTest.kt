package com.leapauto.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeroTextStyleTest {
    @Test
    fun `stone light artwork keeps dark labels and white range`() {
        val colors = HomeHeroTextStyle.resolve("5", isDark = false, isHeroDark = true)
        assertEquals(Color(0xFF202020), colors.primary)
        assertEquals(Color(0xFF404040), colors.secondary)
        assertEquals(Color.White, colors.mileage)
        assertEquals(Color.White, colors.unit)
    }

    @Test
    fun `stone missing artwork uses dark range on the light native fallback`() {
        val presentation = com.leapauto.app.NightThemePolicy.resolve(
            "5", false, com.leapauto.app.NightThemePolicy.BackgroundSource.NATIVE
        )
        assertOriginalColors(HomeHeroTextStyle.resolve("5", false, presentation.isHeroDark), false)
    }

    @Test
    fun `stone dark mode still follows actual artwork contrast`() {
        for (heroDark in listOf(false, true)) {
            assertOriginalColors(HomeHeroTextStyle.resolve("5", true, heroDark), heroDark)
        }
    }

    @Test
    fun `plaza light mode only changes range unit to battery green`() {
        for (heroDark in listOf(false, true)) {
            val colors = HomeHeroTextStyle.resolve("8", false, heroDark)
            val original = HomeHeroTextStyle.resolve("7", false, heroDark)
            assertEquals(original.primary, colors.primary)
            assertEquals(original.secondary, colors.secondary)
            assertEquals(original.mileage, colors.mileage)
            assertEquals(Color(0xFF00B42A), colors.unit)
            assertOriginalColors(HomeHeroTextStyle.resolve("8", true, heroDark), heroDark)
        }
    }

    @Test
    fun `other themes and fallback retain their foreground mapping in both appearances`() {
        for (themeId in listOf("4", "7", "future", "")) {
            for (isDark in listOf(false, true)) {
                for (heroDark in listOf(false, true)) {
                    assertOriginalColors(HomeHeroTextStyle.resolve(themeId, isDark, heroDark), heroDark)
                }
            }
        }
    }

    private fun assertOriginalColors(colors: HomeHeroTextStyle.Colors, heroDark: Boolean) {
        assertEquals(if (heroDark) Color.White else Color(0xFF202020), colors.primary)
        assertEquals(colors.primary, colors.mileage)
        assertEquals(if (heroDark) Color.White.copy(alpha = 0.90f) else Color(0xFF555555), colors.secondary)
        assertEquals(colors.secondary, colors.unit)
    }
}
