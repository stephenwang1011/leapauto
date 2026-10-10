package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleControlConfirmationPolicyTest {

    @Test
    fun `optimistic protection window is 15 seconds`() {
        assertEquals(15_000L, VehicleControlConfirmationPolicy.OPTIMISTIC_PROTECTION_WINDOW_MS)
    }

    @Test
    fun `response delays match physical actuator characteristics`() {
        // 机械类
        assertEquals(800L, VehicleControlConfirmationPolicy.responseDelayMs("lock"))
        assertEquals(800L, VehicleControlConfirmationPolicy.responseDelayMs("unlock"))
        assertEquals(800L, VehicleControlConfirmationPolicy.responseDelayMs("trunkOpen"))
        assertEquals(800L, VehicleControlConfirmationPolicy.responseDelayMs("trunkClose"))
        assertEquals(800L, VehicleControlConfirmationPolicy.responseDelayMs("trunk"))
        assertEquals(600L, VehicleControlConfirmationPolicy.responseDelayMs("frunkOpen"))
        assertEquals(600L, VehicleControlConfirmationPolicy.responseDelayMs("frunk"))
        assertEquals(500L, VehicleControlConfirmationPolicy.responseDelayMs("windowVent"))
        assertEquals(500L, VehicleControlConfirmationPolicy.responseDelayMs("windowOpen"))
        assertEquals(500L, VehicleControlConfirmationPolicy.responseDelayMs("windowClose"))

        // 电子继电器类（0ms 纯电路即刻响应）
        assertEquals(0L, VehicleControlConfirmationPolicy.responseDelayMs("driverSeatHeating_1"))
        assertEquals(0L, VehicleControlConfirmationPolicy.responseDelayMs("driverSeatVentilation_2"))
        assertEquals(0L, VehicleControlConfirmationPolicy.responseDelayMs("passengerSeatHeating_1"))
        assertEquals(0L, VehicleControlConfirmationPolicy.responseDelayMs("steeringWheelHeating_1"))
        assertEquals(0L, VehicleControlConfirmationPolicy.responseDelayMs("rearviewMirrorHeating_on"))
        assertEquals(0L, VehicleControlConfirmationPolicy.responseDelayMs("fridgeOn"))
        assertEquals(0L, VehicleControlConfirmationPolicy.responseDelayMs("horn"))
    }

    @Test
    fun `telemetry refresh schedules provide tailored stepped polling checkpoints`() {
        // 车门锁：1.2s ➔ 1.5s ➔ 2.0s ➔ 2.5s
        assertEquals(
            listOf(1_200L, 1_500L, 2_000L, 2_500L),
            VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs("lock")
        )
        assertEquals(
            listOf(1_200L, 1_500L, 2_000L, 2_500L),
            VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs("unlock")
        )

        // 后备箱电尾门：1.5s ➔ 3.0s ➔ 5.0s
        assertEquals(
            listOf(1_500L, 3_000L, 5_000L),
            VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs("trunkOpen")
        )
        assertEquals(
            listOf(1_500L, 3_000L, 5_000L),
            VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs("trunkClose")
        )

        // 车窗电机：1.2s ➔ 2.0s ➔ 3.5s ➔ 5.0s
        assertEquals(
            listOf(1_200L, 2_000L, 3_500L, 5_000L),
            VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs("windowVent")
        )
        assertEquals(
            listOf(1_200L, 2_000L, 3_500L, 5_000L),
            VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs("windowClose")
        )

        // 前备箱：1.2s ➔ 2.0s
        assertEquals(
            listOf(1_200L, 2_000L),
            VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs("frunkOpen")
        )

        // 座舱舒适类：1.0s ➔ 1.8s ➔ 2.5s
        assertEquals(
            listOf(1_000L, 1_800L, 2_500L),
            VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs("driverSeatHeating_1")
        )
        assertEquals(
            listOf(1_000L, 1_800L, 2_500L),
            VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs("steeringWheelHeating_on")
        )
        assertEquals(
            listOf(1_000L, 1_800L, 2_500L),
            VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs("fridgeOn")
        )
    }

    @Test
    fun `category predicates accurately classify commands`() {
        assertTrue(VehicleControlConfirmationPolicy.isMechanicalCommand("lock"))
        assertTrue(VehicleControlConfirmationPolicy.isMechanicalCommand("trunkOpen"))
        assertTrue(VehicleControlConfirmationPolicy.isMechanicalCommand("windowVent"))
        assertFalse(VehicleControlConfirmationPolicy.isMechanicalCommand("driverSeatHeating_1"))

        assertTrue(VehicleControlConfirmationPolicy.isComfortOrHardwareCommand("driverSeatHeating_1"))
        assertTrue(VehicleControlConfirmationPolicy.isComfortOrHardwareCommand("steeringWheelHeating_1"))
        assertTrue(VehicleControlConfirmationPolicy.isComfortOrHardwareCommand("rearviewMirrorHeating_on"))
        assertTrue(VehicleControlConfirmationPolicy.isComfortOrHardwareCommand("fridgeOn"))
        assertFalse(VehicleControlConfirmationPolicy.isComfortOrHardwareCommand("lock"))
    }

    @Test
    fun `seat heating and ventilation are mutually exclusive on the same seat`() {
        val initial = VehicleControlConfirmationPolicy.SeatComfortState(heating = 2, ventilation = 0)
        assertEquals(2, initial.heating)
        assertEquals(0, initial.ventilation)

        // 打开通风 -> 加热自动归零
        val switchedToVent = VehicleControlConfirmationPolicy.resolveSeatComfort(
            currentHeating = initial.heating,
            currentVentilation = initial.ventilation,
            newVentilation = 1
        )
        assertEquals(0, switchedToVent.heating)
        assertEquals(1, switchedToVent.ventilation)

        // 打开加热 -> 通风自动归零
        val switchedToHeating = VehicleControlConfirmationPolicy.resolveSeatComfort(
            currentHeating = switchedToVent.heating,
            currentVentilation = switchedToVent.ventilation,
            newHeating = 2
        )
        assertEquals(2, switchedToHeating.heating)
        assertEquals(0, switchedToHeating.ventilation)

        // 关闭加热 -> 通风保持原样
        val turnedOff = VehicleControlConfirmationPolicy.resolveSeatComfort(
            currentHeating = switchedToHeating.heating,
            currentVentilation = switchedToHeating.ventilation,
            newHeating = 0
        )
        assertEquals(0, turnedOff.heating)
        assertEquals(0, turnedOff.ventilation)
    }

    @Test
    fun `main activity implements mutual exclusion for heating and ventilation`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(java.io.File(workingDirectory)) { it.parentFile }
            .first { java.io.File(it, "app").isDirectory }
        val mainActivity = java.io.File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()

        assertTrue(mainActivity.contains("if (lvl > 0) optimisticDriverSeatVentilation = 0"))
        assertTrue(mainActivity.contains("if (lvl > 0) optimisticDriverSeatHeating = 0"))
        assertTrue(mainActivity.contains("if (lvl > 0) optimisticPassengerSeatVentilation = 0"))
        assertTrue(mainActivity.contains("if (lvl > 0) optimisticPassengerSeatHeating = 0"))
    }
}
