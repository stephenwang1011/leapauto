package com.leapauto.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.leapauto.app.ui.theme.ErrorContainerDark
import com.leapauto.app.ui.theme.ErrorDark
import com.leapauto.app.ui.theme.ErrorLight
import com.leapauto.app.ui.theme.GlassInsetSurfaceDark
import com.leapauto.app.ui.theme.PrimaryDark
import com.leapauto.app.ui.theme.StatusGoodDark
import com.leapauto.app.ui.theme.StatusWarnDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAlertStyleTest {
    @Test
    fun `light mode preserves the caller error color on cards and hero`() {
        for (fallback in listOf(ErrorLight, Color(0xFFB3261E))) {
            assertEquals(fallback, HomeAlertStyle.contentColor(false, fallback))
            assertEquals(fallback, HomeAlertStyle.contentColor(false, fallback, onHero = true))
        }
    }

    @Test
    fun `dark home warnings use more saturated red without changing global error`() {
        val card = HomeAlertStyle.contentColor(true, ErrorDark)
        val hero = HomeAlertStyle.contentColor(true, ErrorDark, onHero = true)
        assertEquals(Color(0xFFFF8A80), card)
        assertEquals(Color(0xFFFF3B30), hero)
        assertEquals(Color(0xFFFFB4AB), ErrorDark)
        listOf(card, hero).forEach {
            assertEquals(1f, it.alpha)
            assertEquals(ErrorDark.red, it.red)
            assertTrue(it.green < ErrorDark.green)
            assertTrue(it.blue < ErrorDark.blue)
        }
    }

    @Test
    fun `card warning text meets AA over graphite and existing aura and inset materials`() {
        val foreground = HomeAlertStyle.contentColor(true, ErrorDark)
        val bases = listOf(HomeNightStyle.cardStart, HomeNightStyle.cardMiddle, HomeNightStyle.cardEnd)
        val auras = listOf(
            Color.Transparent,
            ErrorDark.copy(alpha = 0.14f),
            StatusWarnDark.copy(alpha = 0.16f),
            PrimaryDark.copy(alpha = 0.16f),
            StatusGoodDark.copy(alpha = 0.10f)
        )
        for (base in bases) {
            for (aura in auras) {
                val background = base.compositeOver(HomeNightStyle.page)
                val lit = aura.copy(alpha = aura.alpha * HomeNightStyle.auraScale).compositeOver(background)
                assertContrast(foreground, lit, 4.5f)
                assertContrast(foreground, GlassInsetSurfaceDark.copy(alpha = 0.68f).compositeOver(lit), 4.5f)
            }
        }
    }

    @Test
    fun `hero alert text meets AA on night horizon and icons meet non text contrast`() {
        val foreground = HomeAlertStyle.contentColor(true, ErrorDark, onHero = true)
        assertContrast(foreground, HomeNightStyle.page, 4.5f)
        val warningButton = ErrorContainerDark.copy(alpha = 0.35f).compositeOver(HomeNightStyle.page)
        assertContrast(foreground, warningButton, 3f)
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float) {
        val contrast = (foreground.luminance() + 0.05f) / (background.luminance() + 0.05f)
        assertTrue("foreground=$foreground background=$background contrast=$contrast", contrast >= minimum)
    }
}
