package com.leapauto.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EnergyHomeSummaryTest {

    private fun readScreenSource(): String {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        return File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()
    }

    @Test
    fun `summary page uses precision gauge columns with uppercase telemetry codes and removes dot`() {
        val screenSource = readScreenSource()
        val summaryBlock = screenSource.substringAfter("fun EnergyHomeSummaryPage(").substringBefore("fun EnergyHomeWeeklyPage(")

        assertTrue("Summary page must use PrecisionGaugeColumn", summaryBlock.contains("PrecisionGaugeColumn("))
        assertTrue("Summary page must include ACTIVE DAYS code", summaryBlock.contains("ACTIVE DAYS"))
        assertTrue("Summary page must include ODOMETER code", summaryBlock.contains("ODOMETER"))
        assertTrue("Summary page must include TOTAL ENERGY code", summaryBlock.contains("TOTAL ENERGY"))
        assertFalse("ODOMETER must not have asymmetrical active indicator dot", summaryBlock.contains("hasActiveIndicator = true"))
    }

    @Test
    fun `summary page coordinates colors for cyan active days, white odometer and green energy`() {
        val screenSource = readScreenSource()
        val summaryBlock = screenSource.substringAfter("fun EnergyHomeSummaryPage(").substringBefore("fun EnergyHomeWeeklyPage(")

        assertTrue("Must wire cyan color for active days and daily average", summaryBlock.contains("0xFF38BDF8"))
        assertTrue("Must wire statusGood green color for total energy", summaryBlock.contains("energyColor = MaterialTheme.statusGood"))
    }

    @Test
    fun `summary page embeds telemetry status bar for overall consumption and daily average`() {
        val screenSource = readScreenSource()
        val summaryBlock = screenSource.substringAfter("fun EnergyHomeSummaryPage(").substringBefore("fun EnergyHomeWeeklyPage(")

        assertTrue("Must include comprehensive telemetry bar", summaryBlock.contains("综合能耗"))
        assertTrue("Must include daily average telemetry", summaryBlock.contains("每日平均"))
        assertTrue("Must format kWh per 100km unit", summaryBlock.contains("kWh/100km"))
    }

    @Test
    fun `telemetry status bar eliminates battery and car icons for pure text precision`() {
        val screenSource = readScreenSource()
        val summaryBlock = screenSource.substringAfter("fun EnergyHomeSummaryPage(").substringBefore("fun EnergyHomeWeeklyPage(")
        val telemetryBar = summaryBlock.substringAfter("// ====== 2. 下半部分：贯通式微晶能效状态条 ======")

        assertFalse("Telemetry bar must not have battery icon", telemetryBar.contains("ic_phosphor_battery_charging"))
        assertFalse("Telemetry bar must not have car icon", telemetryBar.contains("ic_phosphor_car"))
    }

    @Test
    fun `summary page eliminates marketing slogans and nested plastic containers`() {
        val screenSource = readScreenSource()
        val summaryBlock = screenSource.substringAfter("fun EnergyHomeSummaryPage(").substringBefore("fun EnergyHomeWeeklyPage(")

        assertFalse("Must eliminate cheesy marketing slogan 提车起点", summaryBlock.contains("提车起点"))
        assertFalse("Must eliminate cheesy marketing slogan 持续奔赴中", summaryBlock.contains("持续奔赴中"))
        assertTrue("Must use subtle optical groove dividers", summaryBlock.contains("width(0.8.dp)"))
    }

    @Test
    fun `summary metrics eliminate alarmist warning color from total mileage`() {
        val screenSource = readScreenSource()
        val summaryBlock = screenSource.substringAfter("fun EnergyHomeSummaryPage(").substringBefore("fun EnergyHomeWeeklyPage(")

        assertFalse("Total mileage must not use warning color statusWarn", summaryBlock.contains("MaterialTheme.statusWarn"))
        assertTrue("Values should use dignified onSurface token", summaryBlock.contains("MaterialTheme.colorScheme.onSurface"))
    }
}
