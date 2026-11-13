package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetVehicleAppearanceTest {

    @Test
    fun `widget uses configured model and color only for display appearance`() {
        val appearance = ControlWidget.resolveWidgetAppearance(
            config = SessionStore.VehicleConfig(
                modelYear = "2026",
                powerType = SessionStore.VehiclePowerType.PURE_ELECTRIC,
                model = "C10",
                color = "metal_black"
            ),
            reportedCarType = "C16 纯电"
        )

        assertEquals("C10", appearance.model)
        assertEquals("metal_black", appearance.color.id)
    }

    @Test
    fun `widget normalizes B05 to shared Lafa5 appearance`() {
        val appearance = ControlWidget.resolveWidgetAppearance(
            config = SessionStore.VehicleConfig(
                model = "B05",
                color = "speed_orange"
            ),
            reportedCarType = "B05 纯电"
        )

        assertEquals("Lafa5", appearance.model)
        assertEquals("speed_orange", appearance.color.id)
    }

    @Test
    fun `widget falls back to reported model when configuration has not been saved`() {
        val appearance = ControlWidget.resolveWidgetAppearance(
            config = SessionStore.VehicleConfig(),
            reportedCarType = "C16 纯电"
        )

        assertEquals("C16", appearance.model)
        assertEquals("liquid_silver", appearance.color.id)
    }

    @Test
    fun `widget vehicle image scaling uses retina resolution and smooth filtering`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(java.io.File(workingDirectory)) { it.parentFile }
            .first { java.io.File(it, "app").isDirectory }
        val widgetSource = java.io.File(projectDir, "app/src/main/java/com/leapauto/app/ControlWidget.kt").readText()
        val compactSource = java.io.File(projectDir, "app/src/main/java/com/leapauto/app/CompactControlWidget.kt").readText()

        org.junit.Assert.assertTrue(widgetSource.contains("targetWidth: Int = 680"))
        org.junit.Assert.assertTrue(widgetSource.contains("scaleRatio: Float = 0.94f"))
        org.junit.Assert.assertTrue(widgetSource.contains("FILTER_BITMAP_FLAG"))
        org.junit.Assert.assertTrue(widgetSource.contains("isDither = true"))
        org.junit.Assert.assertTrue(widgetSource.contains("scaleBitmapForWidget(remoteBitmap, 680, 0.94f)"))
        org.junit.Assert.assertTrue(compactSource.contains("scaleBitmapForWidget(remoteBitmap, 520, 0.94f)"))
    }

    @Test
    fun `widgetTitle prioritizes user nickname and session nickname`() {
        val appearance = VehicleAppearance("C16", VehicleAppearanceCatalog.colorOption("C16", null))
        val customConfig = SessionStore.VehicleConfig(nickname = "大白")
        assertEquals("大白", ControlWidget.widgetTitle(customConfig, appearance))

        // config.nickname 为空时回退到 sessionNickname
        val emptyConfig = SessionStore.VehicleConfig(nickname = "")
        assertEquals("🐷哥's C16", ControlWidget.widgetTitle(emptyConfig, appearance, sessionNickname = "🐷哥's C16"))

        // sessionNickname 也为空时回退到 vehicleNickname
        assertEquals("小灰灰", ControlWidget.widgetTitle(emptyConfig, appearance, vehicleNickname = "小灰灰"))

        // 全部为空时回退到车型
        assertEquals("零跑 C16", ControlWidget.widgetTitle(emptyConfig, appearance))
    }

    @Test
    fun `widgetTitle filters out Lafa5 and B05 and falls back to clean brand title`() {
        val lafaAppearance = VehicleAppearance("Lafa5", VehicleAppearanceCatalog.colorOption("Lafa5", null))
        val configWithLafa = SessionStore.VehicleConfig(nickname = "Lafa5")
        // 如果曾因历史 bug 保存过 Lafa5 昵称，严格过滤并显示车型品牌
        assertEquals("零跑", ControlWidget.widgetTitle(configWithLafa, lafaAppearance))

        val emptyConfig = SessionStore.VehicleConfig(nickname = "")
        assertEquals("零跑", ControlWidget.widgetTitle(emptyConfig, lafaAppearance))
    }

    @Test
    fun `widget canonicalModel falls back to C16 instead of Lafa5 when model is unknown or blank`() {
        val blankAppearance = VehicleAppearanceCatalog.resolveAppearance("", null)
        assertEquals("C16", blankAppearance.model)

        val unknownAppearance = VehicleAppearanceCatalog.resolveAppearance("UNKNOWN_MODEL", null)
        assertEquals("C16", unknownAppearance.model)
    }

    @Test
    fun `custom vehicle image update and reset trigger full appearance refresh on widgets`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(java.io.File(workingDirectory)) { it.parentFile }
            .first { java.io.File(it, "app").isDirectory }
        val mainActivity = java.io.File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()

        val saveSection = mainActivity.substringAfter("VehicleImageCache.saveCustomImage")
            .substringBefore("toast(\"保存主图失败")
        val resetSection = mainActivity.substringAfter("VehicleImageCache.removeCustomImage")
            .substringBefore("syncVehicleImage(vin)")

        org.junit.Assert.assertTrue(
            "保存自定义主图后必须全量刷新外观以同步新车图到桌面小组件",
            saveSection.contains("ControlWidget.refreshAppearance(applicationContext)")
        )
        org.junit.Assert.assertTrue(
            "恢复官方默认车模后必须全量刷新外观以同步恢复桌面小组件车模",
            resetSection.contains("ControlWidget.refreshAppearance(applicationContext)")
        )
    }
}
