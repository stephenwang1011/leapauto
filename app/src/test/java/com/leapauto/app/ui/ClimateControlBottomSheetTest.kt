package com.leapauto.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClimateControlBottomSheetTest {

    @Test
    fun `climate control adopts modal bottom sheet drawer pattern like fridge control`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("fun ClimateControlBottomSheet("))
        assertTrue(screenSource.contains("showClimateControlBottomSheet = true"))
        assertTrue(screenSource.contains("ClimateControlBottomSheet("))
        assertTrue(screenSource.contains("ModalBottomSheet("))
        assertTrue(screenSource.contains("BottomSheetDefaults.DragHandle()"))
        assertTrue(screenSource.contains("设定温度"))
        assertTrue(screenSource.contains("QuickClimateCircleAction("))
        assertTrue(screenSource.contains("skipPartiallyExpanded = false"))
        assertTrue(screenSource.contains("上拉显示完整的空调设置界面"))
        assertTrue(screenSource.contains("sheetState.expand()"))
        assertTrue(screenSource.contains("SeatComfortControlCard("))
        assertTrue(screenSource.contains("SeatComfortTogglePill("))
        assertTrue(screenSource.contains("driverSeatHeating_2"))
        assertTrue(screenSource.contains("driverSeatVentilation_2"))
        assertTrue(screenSource.contains("passengerSeatHeating_2"))
        assertTrue(screenSource.contains("passengerSeatVentilation_2"))
    }
}
