package com.leapauto.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.leapauto.app.ui.theme.ErrorDark
import com.leapauto.app.ui.theme.OnSurfaceDark
import com.leapauto.app.ui.theme.OnSurfaceVariantDark
import com.leapauto.app.ui.theme.PrimaryDark
import com.leapauto.app.ui.theme.StatusGoodDark
import com.leapauto.app.ui.theme.StatusWarnDark
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNightStyleTest {
    @Test
    fun `home warning foreground is scoped and does not recolor dialogs or warning materials`() {
        val screen = source("ui/LeapAutoScreen")
        val quick = screen.substringAfter("private fun QuickVehicleButton(").substringBefore("private fun showUpperToast")
        assertTrue(quick.contains("val warningContentColor = homeAlertContentColor(onHero = true)"))
        assertTrue(quick.contains("val isWarning = warning || iconTint == MaterialTheme.colorScheme.error"))
        assertTrue(quick.contains("tint = if (isWarning) warningContentColor else iconTint"))
        assertTrue(quick.contains("else if (isWarning) warningContentColor else labelTint"))
        assertTrue(quick.contains("color = if (inProgress) MaterialTheme.colorScheme.primary"))
        assertTrue(Regex("if \\(inProgress\\) \\{\\s*CircularProgressIndicator\\(").containsMatchIn(quick))
        assertTrue(quick.contains("fontWeight = if (isWarning || inProgress) FontWeight.Bold"))
        assertTrue(quick.contains("MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)"))
        assertTrue(quick.contains("MaterialTheme.colorScheme.error.copy(alpha = 0.85f)"))
        val card = screen.substringAfter("fun VehicleStatusCard(").substringBefore("fun VehicleStatusCell(")
        assertEquals(2, Regex("warningColor = warningContentColor").findAll(card).count())
        assertFalse(card.substringAfter("if (showWindowDetails)").contains("warningContentColor"))
        val cell = screen.substringAfter("fun VehicleStatusCell(").substringBefore("fun chargeProgress(")
        assertTrue(cell.contains("warningColor: Color = MaterialTheme.colorScheme.error"))
        val dialogStart = screen.indexOf("fun WindowStatusDialog(")
        val dialogEnd = screen.indexOf("private fun QuickVehicleActions(")
        assertTrue("WindowStatusDialog declaration must exist", dialogStart >= 0)
        assertTrue("Dialog section must end before quick actions", dialogEnd > dialogStart)
        val dialog = screen.substring(dialogStart, dialogEnd)
        assertTrue(dialog.isNotBlank())
        assertTrue(dialog.startsWith("fun WindowStatusDialog("))
        assertTrue(dialog.contains("AlertDialog("))
        assertTrue(dialog.contains("containerColor = MaterialTheme.colorScheme.surface"))
        assertFalse(dialog.contains("fun VehicleHero("))
        assertFalse(dialog.contains("private fun QuickVehicleActions("))
        assertFalse(dialog.contains("homeAlertContentColor"))
        assertFalse(source("ui/theme/Theme").contains("HomeAlert"))
        val actions = screen.substringAfter("private fun QuickVehicleActions(").substringBefore("private fun QuickVehicleButton(")
        listOf("windowOpen", "sunroofOpen", "trunkOpen", "isWarningCmd").forEach { condition ->
            assertTrue(actions.contains("warning = $condition"))
        }
        val tires = screen.substringAfter("private fun HomeTirePressureCard(").substringBefore("fun VehicleStatusCard(")
        assertTrue(tires.contains("val warningColor = MaterialTheme.colorScheme.error"))
        assertTrue(tires.contains("val auraColor = if (hasWarning) warningColor.copy(alpha = 0.14f)"))
        assertTrue(tires.contains("val textColor = if (warning) warningContentColor"))
        assertTrue(tires.contains("val unitColor = if (warning) warningContentColor"))
        assertTrue(tires.contains("val wheelWarningColor = homeAlertContentColor()"))
        val hero = screen.substringAfter("fun VehicleHero(").substringBefore("private fun HeroControlHudPill(")
        assertTrue(hero.contains("VehicleHomeStatus.SocBand.CRITICAL -> warningContentColor"))
        assertTrue(hero.contains("fuelSocValue <= 10 -> warningContentColor"))
        assertTrue(hero.contains("contentColor = warningContentColor"))
        assertTrue(hero.contains("MaterialTheme.colorScheme.error.copy(alpha = 0.50f)"))
    }

    @Test
    fun `graphite cards preserve normal secondary and warning text contrast with aura`() {
        val bases = listOf(HomeNightStyle.cardStart, HomeNightStyle.cardMiddle, HomeNightStyle.cardEnd)
        for (base in bases) {
            val background = base.compositeOver(HomeNightStyle.page)
            for (aura in listOf(
                Color.Transparent,
                ErrorDark.copy(alpha = 0.14f),
                StatusWarnDark.copy(alpha = 0.16f),
                PrimaryDark.copy(alpha = 0.16f),
                StatusGoodDark.copy(alpha = 0.10f)
            )) {
                val lit = aura.copy(alpha = aura.alpha * HomeNightStyle.auraScale).compositeOver(background)
                for (text in listOf(OnSurfaceDark, OnSurfaceVariantDark, ErrorDark, StatusWarnDark)) {
                    val contrast = (text.luminance() + 0.05f) / (lit.luminance() + 0.05f)
                    assertTrue("text=$text background=$lit contrast=$contrast", contrast >= 4.5f)
                }
            }
        }
    }

    @Test
    fun `warning label has no rectangular backing and bottom controls follow the page horizon`() {
        val screen = source("ui/LeapAutoScreen")
        val quick = screen.substringAfter("private fun QuickVehicleButton(").substringBefore("private fun showUpperToast")
        assertFalse(quick.contains("isWarning && isHeroDark"))
        val warningLabel = Regex("Text\\(\\s*label,[\\s\\S]*?\\n\\s*\\)").find(quick)?.value.orEmpty()
        assertTrue(warningLabel.isNotEmpty())
        assertFalse(warningLabel.contains("background"))
        assertTrue(screen.contains("isHeroDark = isDark || isHeroDark"))
        assertTrue((ErrorDark.luminance() + 0.05f) / (HomeNightStyle.page.luminance() + 0.05f) >= 4.5f)
    }

    @Test
    fun `home borders remain three step and softer than the global dark border`() {
        assertTrue(HomeNightStyle.borderStart.alpha < 0.32f)
        assertTrue(HomeNightStyle.borderStart.alpha > HomeNightStyle.borderMiddle.alpha)
        assertTrue(HomeNightStyle.borderMiddle.alpha > HomeNightStyle.borderEnd.alpha)
    }

    @Test
    fun `filter is background only and home card provider excludes home sheets`() {
        val screen = source("ui/LeapAutoScreen")
        val hero = screen.substringAfter("fun VehicleHero(").substringBefore("private fun HeroControlHudPill(")
        assertEquals(1, Regex("colorFilter = backgroundFilter").findAll(hero).count())
        val backdrop = hero.substringAfter("contentDescription = \"主题展台背景\"").substringBefore("} else if (backgroundSource == NightThemePolicy.BackgroundSource.NATIVE)")
        assertTrue(backdrop.contains("colorFilter = backgroundFilter"))
        assertFalse(hero.contains("shouldApplyNightMoodFilter"))
        assertTrue(hero.contains("HomeHeroTransition(heroNight.transitionHeightDp, isDark)"))
        assertFalse(hero.contains("HomeHeroTransition(pageBg"))
        assertTrue(hero.contains("hudGradientColor = when"))
        assertTrue(hero.contains("isDark -> HomeNightStyle.page"))
        assertTrue(hero.indexOf("heroNight.transitionPlacement == NightThemePolicy.TransitionPlacement.BEHIND_VEHICLE") < hero.indexOf("CarModel3DView("))
        assertTrue(hero.contains("heroNight.transitionPlacement == NightThemePolicy.TransitionPlacement.BEHIND_VEHICLE"))
        assertTrue(hero.contains("heroNight.transitionPlacement == NightThemePolicy.TransitionPlacement.FOREGROUND"))
        assertTrue(hero.contains("heroNight.useNightSkybox"))
        assertTrue(hero.contains("originalHeroDark = ThemeManager.isHeroBackgroundDark(renderedThemeId, isDark, context, vehicleVin)"))
        assertTrue(hero.contains("0.0f to hudGradientColor.copy("))
        assertTrue(hero.contains("neutralContentColor = heroSecondaryTextColor"))
        assertTrue(hero.contains("val mileageDisplayColor = heroPrimaryTextColor"))
        assertTrue(hero.contains("val unitDisplayColor = heroSecondaryTextColor"))
        val home = screen.substringAfter("private fun HomeContent(").substringBefore("private fun MyContent(")
        assertEquals(1, Regex("LocalHomeNightCards provides homeNight").findAll(home).count())
        assertFalse(home.substringAfter("if (showParkingDetailDialog").contains("LocalHomeNightCards"))
        assertFalse(source("ui/theme/Theme").contains("HomeNightStyle"))
        listOf("ControlWidget", "CompactControlWidget", "ThemeManager").forEach {
            assertFalse(source(it).contains("NightThemePolicy"))
        }
        val view = source("ui/CarModel3DView")
        assertTrue(view.contains("skyBox.setDayOrNight(isDark)"))
        assertTrue(view.contains("skyBox.setDayOrNightImmediately(isDark ? 1.0 : 0.0)"))
        val transition = source("ui/HomeNightStyle")
        assertTrue(transition.contains("homeHeroTransitionPage"))
        assertFalse(transition.contains("page: Color"))
    }

    @Test
    fun `snow night update text uses black while other themes keep the secondary color`() {
        val hero = source("ui/LeapAutoScreen").substringAfter("fun VehicleHero(")
            .substringBefore("private fun HeroControlHudPill(")
        val updateText = Regex("Text\\(\\s*text = statusTextWithWeather,[\\s\\S]*?\\n\\s*\\)")
            .find(hero)?.value.orEmpty()
        assertTrue(updateText.isNotEmpty())
        assertTrue(updateText.contains("color = if (isDark && renderedThemeId == VehicleThemePolicy.SNOW_THEME_ID)"))
        assertTrue(updateText.contains("Color.Black"))
        assertTrue(updateText.contains("else heroSecondaryTextColor"))
    }

    @Test
    fun `hero transition uses the home night page only for dark mode`() {
        val lightPage = Color(0xFFF3F4F6)
        assertEquals(HomeNightStyle.page, homeHeroTransitionPage(true, lightPage))
        assertEquals(lightPage, homeHeroTransitionPage(false, lightPage))
    }

    private fun source(name: String): String {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app").isDirectory }
        return File(root, "app/src/main/java/com/leapauto/app/$name.kt").readText()
    }
}
