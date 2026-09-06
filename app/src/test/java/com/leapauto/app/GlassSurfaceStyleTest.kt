package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GlassSurfaceStyleTest {

    @Test
    fun `surface tokens keep cards layered without fuzzy borders or runtime blur`() {
        val projectDir = projectDirectory()
        val colors = File(
            projectDir,
            "app/src/main/java/com/leapauto/app/ui/theme/Color.kt"
        ).readText()
        val screen = File(
            projectDir,
            "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt"
        ).readText()
        val theme = File(
            projectDir,
            "app/src/main/java/com/leapauto/app/ui/theme/Theme.kt"
        ).readText()

        assertTrue(colors.contains("GlassSurfaceLight = Color(0xFFFFFFFF)"))
        assertTrue(colors.contains("GlassInsetSurfaceLight = Color(0xFFF5F7F8)"))
        assertTrue(colors.contains("GlassSurfaceDark = Color(0xFF1A1F28)"))
        assertTrue(colors.contains("GlassInsetSurfaceDark = Color(0xFF28303B)"))
        assertFalse(colors.contains("GlassBorderLight"))
        assertFalse(colors.contains("GlassBorderDark"))
        assertTrue(screen.contains("MaterialTheme.glassSurface"))
        assertTrue(screen.contains("MaterialTheme.glassInsetSurface"))
        assertFalse(screen.contains("AppCardOpacityCard"))
        assertFalse(theme.contains("LocalAppCardOpacity"))
        assertFalse(screen.contains("MaterialTheme.glassBorderColor"))
        assertTrue(screen.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(screen.contains("shadowElevation = 0.dp"))
        assertFalse(screen.contains("shadowElevation = 1.dp"))
        assertFalse(screen.contains("LeapBlue"))
        assertFalse(screen.contains("EnergyGreen"))
        assertFalse(screen.contains("RenderEffect"))
        assertFalse(screen.contains("Modifier.blur"))

        listOf(
            "app/src/main/res/drawable/widget_card_background.xml",
            "app/src/main/res/drawable/widget_card_background_25.xml",
            "app/src/main/res/drawable/widget_card_background_50.xml",
            "app/src/main/res/drawable/widget_card_background_75.xml",
            "app/src/main/res/drawable-night/widget_card_background_25.xml",
            "app/src/main/res/drawable-night/widget_card_background_50.xml",
            "app/src/main/res/drawable-night/widget_card_background_75.xml",
            "app/src/main/res/drawable/widget_card_background_light.xml",
            "app/src/main/res/drawable/widget_card_background_25_light.xml",
            "app/src/main/res/drawable/widget_card_background_50_light.xml",
            "app/src/main/res/drawable/widget_card_background_75_light.xml",
            "app/src/main/res/drawable/widget_card_background_dark.xml",
            "app/src/main/res/drawable/widget_card_background_25_dark.xml",
            "app/src/main/res/drawable/widget_card_background_50_dark.xml",
            "app/src/main/res/drawable/widget_card_background_75_dark.xml"
        ).forEach { relativePath ->
            val widgetBackground = File(projectDir, relativePath).readText()
            assertTrue(widgetBackground.contains("android:radius=\"20dp\""))
            assertFalse(widgetBackground.contains("<stroke"))
            assertFalse(widgetBackground.contains("<padding"))
        }
    }

    private fun projectDirectory(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
    }
}
