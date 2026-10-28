package com.leapauto.app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source contracts supplement behavior tests; these do not prove device rendering. */
class NightBrightnessWiringTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app").isDirectory }
    private fun source(name: String) = File(root, "app/src/main/java/com/leapauto/app/$name.kt").readText()

    @Test
    fun `brightness controls use theme text colors without a preview backing`() {
        val control = source("ui/NightBrightnessControl")
        val header = control.substringAfter("Column(verticalArrangement").substringBefore("Box(Modifier.fillMaxWidth()")
        assertEquals(2, Regex("color = MaterialTheme\\.colorScheme\\.onSurface\\b").findAll(header).count())
        val footer = control.substringAfterLast("Row(Modifier.fillMaxWidth()")
        assertEquals(2, Regex("color = MaterialTheme\\.colorScheme\\.onSurfaceVariant\\b").findAll(footer).count())
        assertFalse(control.contains("nightTextProtection"))
        assertEquals(1, control.split("\${draft.roundToInt()}%").size - 1)
    }

    @Test
    fun `brightness slider uses a thin track while retaining its touch target`() {
        val slider = source("ui/NightBrightnessControl").substringAfter("        Slider(")
            .substringBefore("        Row(Modifier.fillMaxWidth()")
        assertTrue(slider.contains("heightIn(min = 48.dp)"))
        assertTrue(slider.contains("SliderDefaults.Track("))
        assertTrue(slider.contains("modifier = Modifier.height(4.dp)"))
    }

    @Test
    fun `dark only setting saves at gesture completion and observes preferences`() {
        val sheet = source("ui/ThemeSelectorBottomSheet")
        assertTrue(sheet.substringAfter("if (isDark) {").contains("NightBrightnessControl("))
        val control = source("ui/NightBrightnessControl")
        assertTrue(control.contains("DragInteraction.Cancel -> { draft = saved; dragging = false }"))
        assertTrue(control.contains("is DragInteraction.Stop"))
        assertTrue(control.contains("remember(vin, themeId, supports3d, saved, enabled)"))
        assertTrue(control.contains("onDispose { unsubscribe() }"))
        assertFalse(control.contains("LeapmotorApi"))
        assertFalse(control.contains("syncModelPackage"))
        assertTrue(source("NightBrightnessStore").contains("registerOnSharedPreferenceChangeListener"))
    }

    @Test
    fun `all home backdrops are wired without filtering the vehicle`() {
        val home = source("ui/LeapAutoScreen")
        assertTrue(home.contains("brightnessPercent = nightBrightness"))
        assertTrue(home.contains("heroNight.background.applyTo(HomeNightStyle.cardStart)"))
        assertTrue(home.contains("colorFilter = backgroundFilter"))
        assertTrue(home.contains("backgroundTreatment = if (finalBackgroundSource == NightThemePolicy.BackgroundSource.ORIGINAL_SKYBOX)"))
        assertFalse(home.contains("nightTextProtection(isHeroDark)"))
        val view = source("ui/CarModel3DView")
        assertTrue(view.contains("webView.updateBackground(backgroundTreatment)"))
        assertTrue(view.contains("if (hasAppliedInitialState && unchanged && !forceImmediately) return"))
        assertTrue(view.contains("if (previewOnly) return"))
        assertTrue(view.contains("onRelease = { it.release() }"))
        val js = File(root, "app/src/main/assets/night-background.js").readText()
        assertTrue(js.contains("v.frameState.skyBox"))
        assertFalse(js.contains("toneMappingExposure"))
        assertFalse(js.contains("scene.traverse"))
    }
}
