package com.leapauto.app.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

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
        assertEquals("鸣笛寻车", horn.title)
        assertEquals(HornTileService::class.java, horn.serviceClass)

        val lock = types.first { it == TilePromptHelper.TileType.LOCK }
        assertEquals("车辆门锁", lock.title)
        assertEquals(LockToggleTileService::class.java, lock.serviceClass)

        val climate = types.first { it == TilePromptHelper.TileType.CLIMATE }
        assertEquals("车辆空调", climate.title)
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

        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(projectDir, "app/src/main/AndroidManifest.xml"))
        val services = document.getElementsByTagName("service")
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        for (type in TilePromptHelper.TileType.values()) {
            val serviceName = type.serviceClass.name.removePrefix("com.leapauto.app")
            val service = (0 until services.length).map { services.item(it) as Element }
                .single { it.getAttributeNS(androidNamespace, "name") == serviceName }
            assertEquals(type.title, service.getAttributeNS(androidNamespace, "label"))
        }
    }

    @Test
    fun `settings screen provides quick settings tiles prompt card`() {
        val projectDir = projectDirectory()
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("QuickSettingsTileCard()"))
        assertTrue(screenSource.contains("下拉控制中心快捷开关"))
        assertTrue(screenSource.contains("TilePromptHelper.requestAddTile"))
    }

    @Test
    fun `quick settings tiles protect against concurrent command execution and eliminate premature recovery`() {
        val projectDir = projectDirectory()
        val lockTile = File(projectDir, "app/src/main/java/com/leapauto/app/tiles/LockToggleTileService.kt").readText()
        val hornTile = File(projectDir, "app/src/main/java/com/leapauto/app/tiles/HornTileService.kt").readText()
        val climateTile = File(projectDir, "app/src/main/java/com/leapauto/app/tiles/ClimateTileService.kt").readText()
        val controlService = File(projectDir, "app/src/main/java/com/leapauto/app/ControlService.kt").readText()

        assertTrue("ControlService 必须暴露 isCommandInFlight 查询方法", controlService.contains("fun isCommandInFlight(): Boolean = commandInFlight.get()"))
        assertTrue("车锁磁贴必须拦截指令执行中的二次并发点击", lockTile.contains("if (ControlService.isCommandInFlight())"))
        assertTrue("寻车磁贴必须拦截指令执行中的二次并发点击", hornTile.contains("if (ControlService.isCommandInFlight())"))
        assertTrue("空调磁贴必须拦截指令执行中的二次并发点击", climateTile.contains("if (ControlService.isCommandInFlight())"))
        assertTrue("车锁磁贴兜底超时必须延长至 10 秒以上，杜绝 1.5 秒过早恢复导致后续指令被吞", lockTile.contains("10_000L"))
        assertTrue("车锁磁贴必须移除旧的 1.5 秒短时恢复定时器", !lockTile.contains("1500L"))
        assertTrue("车锁磁贴必须使用 isCarLocked 区分本地门锁状态与手机锁屏 isLockedState", lockTile.contains("isCarLocked"))
    }
}
