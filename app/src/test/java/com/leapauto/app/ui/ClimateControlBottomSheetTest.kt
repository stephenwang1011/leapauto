package com.leapauto.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClimateControlBottomSheetTest {

    @Test
    fun `climate control adopts level 2 full page pattern with fully expanded seat comfort and outlets`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt")
            .readText()
            .replace("\r\n", "\n")

        assertTrue(screenSource.contains("ScreenDestination.CLIMATE_CONTROL"))
        assertTrue(screenSource.contains("ClimateControlContent("))
        assertTrue(screenSource.contains("设定温度"))
        assertTrue(screenSource.contains("QuickClimateCircleAction("))
        assertTrue(screenSource.contains("风量调节"))
        assertTrue(screenSource.contains("出风方向"))
        assertTrue(screenSource.contains("SeatComfortControlCard("))

        // 验证出风方向与座椅舒适度已完全展开，不再需要折叠与更多设置切换按钮
        assertFalse("不应再包含折叠开关状态", screenSource.contains("showExtendedControls"))
        assertFalse("不应再包含展开折叠按钮文字", screenSource.contains("展开出风与座椅舒适"))
        assertFalse("不应再包含收起按钮文字", screenSource.contains("收起更多设置"))

        assertTrue(screenSource.contains("SeatComfortTogglePill("))
        assertTrue(screenSource.contains("driverSeatHeating_2"))
        assertTrue(screenSource.contains("driverSeatVentilation_2"))
        assertTrue(screenSource.contains("passengerSeatHeating_2"))
        assertTrue(screenSource.contains("passengerSeatVentilation_2"))
        assertTrue(screenSource.contains("leftRearSeatHeating_2"))
        assertTrue(screenSource.contains("leftRearSeatVentilation_2"))
        assertTrue(screenSource.contains("rightRearSeatHeating_2"))
        assertTrue(screenSource.contains("rightRearSeatVentilation_2"))
        assertTrue(screenSource.contains("text = \"二排左座\""))
        assertTrue(screenSource.contains("text = \"主驾后\""))
        assertTrue(screenSource.contains("text = \"二排右座\""))
        assertTrue(screenSource.contains("text = \"副驾后\""))
    }

    @Test
    fun `climate screen passes liveWeather and keeps standby controls neutral when ac is off`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt")
            .readText()
            .replace("\r\n", "\n")

        assertTrue("ClimateControlContent must accept and forward liveWeather", screenSource.contains("liveWeather = liveWeather,"))
        assertTrue("Recirculation should only highlight active green when AC is on", screenSource.contains("isActive = isAcOn && isInner"))
        assertTrue("Wind slider must accept dynamic activeColor", screenSource.contains("activeColor = windAccentColor"))
        assertTrue("Wind slider should support tap-to-jump", screenSource.contains("detectTapGestures"))
        assertTrue("Temperature steppers should use sculpted ClimateTempStepButton", screenSource.contains("ClimateTempStepButton("))
        assertTrue("Outlet selector should use unified micro-crystal segments", screenSource.contains("ClimateOutletSegmentItem("))
        assertTrue("Rearview mirror heating should use defrost wave icon instead of sun", screenSource.contains("icon = R.drawable.ic_windshield_defrost,\n                            label = \"后视镜加热\""))
        assertTrue("Wind slider should skip redundant submit when unchanged and AC is already on", screenSource.contains("if (!windChanged && isAcOn) return@ThinClimateSlider"))
        assertTrue("Seat comfort active count must be gated by hardware availability", screenSource.contains("hasLeftRearHeating && leftRearHeating"))
    }
}
