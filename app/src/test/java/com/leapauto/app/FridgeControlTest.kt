package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FridgeControlTest {

    @Test
    fun signalTableDecodesFridgeSignals() {
        val raw = JSONObject().apply {
            put("10707", 4)
            put("10708", 0)
            put("10709", 1)
            put("10711", 0)
            put("10712", 0)
            put("11189", 1)
            put("11190", 1)
            put("11191", 0)
            put("11260", 1789474094L)
        }

        val decoded = SignalTable.decode(raw)

        assertEquals(4, decoded.optInt("fridgeTargetTemp"))
        assertEquals(0, decoded.optInt("fridgeMode"))
        assertEquals(1, decoded.optInt("fridgeSwitch"))
        assertEquals(0, decoded.optInt("fridgeStyle"))
        assertEquals(0, decoded.optInt("fridgeFault"))
        assertEquals(1, decoded.optInt("fridgeParkDurationHours"))
        assertEquals(1, decoded.optInt("fridgeParkSwitch"))
        assertEquals(0, decoded.optInt("fridgeParkCycles"))
        assertEquals(1789474094L, decoded.optLong("fridgeParkEndTime"))
    }

    @Test
    fun fridgeCommandsPresetBuildsExpectedPayloads() {
        val onCmd = Commands.build("fridgeOn")
        val offCmd = Commands.build("fridgeOff")

        assertEquals("500", onCmd.cmdid)
        assertEquals("500", offCmd.cmdid)

        val onState = JSONObject(onCmd.stateJson)
        assertEquals(1, onState.optInt("enable"))
        assertEquals("cold", onState.optString("mode"))
        assertEquals(4, onState.optInt("temp"))
        assertEquals("normal", onState.optString("style"))

        val offState = JSONObject(offCmd.stateJson)
        assertEquals(0, offState.optInt("enable"))
        assertEquals("关闭车载冰箱", offCmd.label)
    }

    @Test
    fun buildFridgeControlHandlesHeatingModeConstraints() {
        // 制热模式下无论输入多少度，均强制设定为 50°C
        val heatingCmd = Commands.buildFridgeControl(
            FridgeControlCommand(
                enable = true,
                mode = FridgeMode.HOT,
                temp = 4,
                style = FridgeStyle.NORMAL
            )
        )

        val state = JSONObject(heatingCmd.stateJson)
        assertEquals("500", heatingCmd.cmdid)
        assertEquals(1, state.optInt("enable"))
        assertEquals("hot", state.optString("mode"))
        assertEquals(50, state.optInt("temp"))
        assertTrue(heatingCmd.label.contains("制热 50°C"))
    }

    @Test
    fun buildFridgeControlHandlesCoolingTemperatureClamp() {
        // 制冷温度范围限制在 -6..15
        val lowTempCmd = Commands.buildFridgeControl(
            FridgeControlCommand(
                enable = true,
                mode = FridgeMode.COLD,
                temp = -20
            )
        )
        val lowState = JSONObject(lowTempCmd.stateJson)
        assertEquals(-6, lowState.optInt("temp"))

        val highTempCmd = Commands.buildFridgeControl(
            FridgeControlCommand(
                enable = true,
                mode = FridgeMode.COLD,
                temp = 30
            )
        )
        val highState = JSONObject(highTempCmd.stateJson)
        assertEquals(15, highState.optInt("temp"))
    }

    @Test
    fun buildFridgeControlHandlesParkSettings() {
        val parkCmd = Commands.buildFridgeControl(
            FridgeControlCommand(
                enable = true,
                mode = FridgeMode.COLD,
                temp = 5,
                style = FridgeStyle.TURBO,
                parkEnable = true,
                durationSeconds = 7200,
                cycles = "2"
            )
        )

        val state = JSONObject(parkCmd.stateJson)
        assertEquals(1, state.optInt("parkEnable"))
        assertEquals(7200, state.optInt("duration"))
        assertEquals("2", state.optString("cycles"))
        assertEquals("turbo", state.optString("style"))
        assertEquals(5, state.optInt("temp"))
    }

    @Test
    fun quickCommandExecutionPolicyMatchesFridge() {
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("fridge", "fridgeOn"))
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("fridge", "fridgeOff"))
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("fridge", "fridgeControl"))
        assertFalse(QuickCommandExecutionPolicy.isCommandInProgress("fridge", "acOn"))
    }

    @Test
    fun fridgeStatusPropertiesCalculateCorrectly() {
        val runningCool = FridgeStatus(
            enabled = true,
            mode = FridgeMode.COLD,
            targetTemp = 4,
            style = FridgeStyle.NORMAL,
            parkEnable = false
        )
        assertTrue(runningCool.isCooling)
        assertFalse(runningCool.isHeating)
        assertFalse(runningCool.isParkRunning)

        val runningHotPark = FridgeStatus(
            enabled = true,
            mode = FridgeMode.HOT,
            targetTemp = 50,
            style = FridgeStyle.TURBO,
            parkEnable = true,
            parkDurationHours = 2,
            parkCycles = 1,
            parkEndTimeEpochSeconds = 1789474094L
        )
        assertFalse(runningHotPark.isCooling)
        assertTrue(runningHotPark.isHeating)
        assertTrue(runningHotPark.isParkRunning)

        val closed = FridgeStatus(
            enabled = false,
            mode = FridgeMode.COLD,
            targetTemp = 4,
            style = FridgeStyle.NORMAL,
            parkEnable = true
        )
        assertFalse(closed.isCooling)
        assertFalse(closed.isHeating)
        assertFalse(closed.isParkRunning)
    }

    @Test
    fun realCaptureSignalMapDecodesCorrectly() {
        // 来自抓包实车数据 [1392] 的真实信号段
        val realSignalMap = JSONObject().apply {
            put("10707", 5)
            put("10708", 0)
            put("10709", 1)
            put("10711", 1)
            put("10712", 0)
            put("11189", 1)
            put("11190", 1)
            put("11191", 0)
            put("11260", 1789474094L)
        }

        val decoded = SignalTable.decode(realSignalMap)
        val fridgeSwitch = decoded.opt("fridgeSwitch")?.toString()?.toIntOrNull()
        assertNotNull(fridgeSwitch)
        assertEquals(1, fridgeSwitch)

        val status = FridgeStatus(
            enabled = fridgeSwitch == 1,
            mode = FridgeMode.fromSignal(decoded.optInt("fridgeMode")),
            targetTemp = decoded.optInt("fridgeTargetTemp"),
            style = FridgeStyle.fromSignal(decoded.optInt("fridgeStyle")),
            fault = decoded.optInt("fridgeFault"),
            parkEnable = decoded.optInt("fridgeParkSwitch") == 1,
            parkDurationHours = decoded.optInt("fridgeParkDurationHours"),
            parkCycles = decoded.optInt("fridgeParkCycles"),
            parkEndTimeEpochSeconds = decoded.optLong("fridgeParkEndTime")
        )

        assertTrue(status.enabled)
        assertEquals(FridgeMode.COLD, status.mode)
        assertEquals(5, status.targetTemp)
        assertEquals(FridgeStyle.TURBO, status.style)
        assertTrue(status.parkEnable)
        assertEquals(1, status.parkDurationHours)
        assertEquals(1789474094L, status.parkEndTimeEpochSeconds)
        assertTrue(status.isParkRunning)
    }

    @Test
    fun vehicleWithoutFridgeSignalsYieldsNullFridgeStatus() {
        // 无车载冰箱的普通车辆（如 T03 或未选装 C11）
        val noFridgeSignalMap = JSONObject().apply {
            put("1204", 80)
            put("1318", 12000)
            put("1938", 1)
        }
        val decoded = SignalTable.decode(noFridgeSignalMap)
        val fridgeSwitch = decoded.opt("fridgeSwitch")?.toString()?.toIntOrNull()
        assertNull(fridgeSwitch)
    }
}
