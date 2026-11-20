package com.leapauto.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsLayoutTest {

    private fun readScreenSource(): String {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        return File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()
    }

    @Test
    fun `settings layout groups items into 4 structured sections`() {
        val screenSource = readScreenSource()
        val myContent = screenSource.substringAfter("private fun MyContent(")

        val section1 = myContent.indexOf("SettingsSectionHeader(\"爱车与个性化\")")
        val section2 = myContent.indexOf("SettingsSectionHeader(\"桌面与快捷控制\")")
        val section3 = myContent.indexOf("SettingsSectionHeader(\"数据与互联服务\")")
        val section4 = myContent.indexOf("SettingsSectionHeader(\"软件与关于\")")

        assertTrue("Section 1 (爱车与个性化) must be present", section1 >= 0)
        assertTrue("Section 2 (桌面与快捷控制) must be present", section2 >= 0)
        assertTrue("Section 3 (数据与互联服务) must be present", section3 >= 0)
        assertTrue("Section 4 (软件与关于) must be present", section4 >= 0)

        assertTrue("Sections must be ordered sequentially", section1 < section2 && section2 < section3 && section3 < section4)
    }

    @Test
    fun `custom vehicle image card includes car icon and structured tags for baseline alignment`() {
        val screenSource = readScreenSource()
        val cardSource = screenSource.substringAfter("private fun VehicleCustomImageCard(").substringBefore("if (showResetConfirmDialog)")

        assertTrue("VehicleCustomImageCard must use ic_phosphor_car for visual balance", cardSource.contains("R.drawable.ic_phosphor_car"))
        assertTrue("Tags must align with title baseline (start = 48.dp)", cardSource.contains(".padding(start = 48.dp)"))
    }

    @Test
    fun `trip log config card renders informative subtitle with capacity and pricing`() {
        val screenSource = readScreenSource()
        val tripCardSource = screenSource.substringAfter("private fun TripLogConfigCard(").substringBefore("// 展开内容")

        assertTrue("Trip card must include capacity in subtitle", tripCardSource.contains("batteryCapacity"))
        assertTrue("Trip card must include electricity price in subtitle", tripCardSource.contains("electricityPrice"))
    }

    @Test
    fun `settings page enforces comfortable screen horizontal padding`() {
        val screenSource = readScreenSource()
        val myContent = screenSource.substringAfter("private fun MyContent(")
        val columnPadding = myContent.substringAfter("Column(").substringBefore("verticalArrangement")

        assertTrue("Settings page must use 14.dp horizontal padding for breathing room", columnPadding.contains("horizontal = 14.dp"))
    }
}
