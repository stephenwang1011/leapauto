package com.leapauto.app.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuickSettingsTileTest {

    private fun projectDirectory(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile ?: return@generateSequence null }
            .first { File(it, "app").isDirectory }
    }

    @Test
    fun `tile helper exposes expected tile definitions`() {
        val types = TilePromptHelper.TileType.values()
        assertEquals(3, types.size)

        val horn = types.first { it == TilePromptHelper.TileType.HORN }
        assertEquals("寻车", horn.title)
        assertEquals(HornTileService::class.java, horn.serviceClass)

        val lock = types.first { it == TilePromptHelper.TileType.LOCK }
        assertEquals("车锁", lock.title)
        assertEquals(LockToggleTileService::class.java, lock.serviceClass)

        val climate = types.first { it == TilePromptHelper.TileType.CLIMATE }
        assertEquals("空调", climate.title)
        assertEquals(ClimateTileService::class.java, climate.serviceClass)
    }

    @Test
    fun `manifest registers all three quick settings tile services with proper permissions`() {
        val projectDir = projectDirectory()
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml").readText()

        listOf(
            ".tiles.HornTileService",
            ".tiles.LockToggleTileService",
            ".tiles.ClimateTileService"
        ).forEach { serviceName ->
            assertTrue("Manifest must declare $serviceName", manifest.contains("android:name=\"$serviceName\""))
        }

        assertTrue(manifest.contains("android:permission=\"android.permission.BIND_QUICK_SETTINGS_TILE\""))
        assertTrue(manifest.contains("<action android:name=\"android.service.quicksettings.action.QS_TILE\" />"))
    }

    @Test
    fun `settings screen provides quick settings tiles prompt card`() {
        val projectDir = projectDirectory()
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("QuickSettingsTileCard()"))
        assertTrue(screenSource.contains("下拉控制中心快捷开关"))
        assertTrue(screenSource.contains("TilePromptHelper.requestAddTile"))
    }
}
