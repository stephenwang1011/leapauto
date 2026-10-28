package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeStatusTest {

    @Test
    fun `charging SOC is nullable validated and never rounded up to full`() {
        for (raw in listOf(null, org.json.JSONObject.NULL, "未知", "--", "NaN", "Infinity", -1, 100.1, 101)) {
            assertNull("value=$raw", ChargeStatus.socPercent(raw))
        }
        assertEquals(100, ChargeStatus.socPercent(" 100 % "))
        assertEquals(99, ChargeStatus.socPercent(99.9))
        assertEquals(80, ChargeStatus.socPercent("80%"))
        assertEquals(0, ChargeStatus.socPercent(0))
    }

    @Test
    fun `formats verified charging state and electrical values`() {
        val status = mapOf(
            "chargeState" to 1,
            "batteryVoltage" to 400,
            "batteryCurrent" to 25,
            "dcInputFastCharge" to true
        )

        assertEquals("充电中", ChargeStatus.label(ChargeStatus.state(status)))
        assertEquals("10.0 kW", ChargeStatus.power(status))
        assertEquals("直流快充", ChargeStatus.type(status))
    }

    @Test
    fun `calculates absolute electrical power from raw or formatted values`() {
        assertEquals("3.3 kW", ChargeStatus.electricalPower("379.2 V", "-8.8 A"))
        assertEquals("10.0 kW", ChargeStatus.electricalPower(400, 25))
        assertNull(ChargeStatus.electricalPower("--", "25 A"))
        assertNull(ChargeStatus.electricalPower("400 V", null))
    }

    @Test
    fun `normalizes charging current to a non-negative display value`() {
        assertEquals("8.299", ChargeStatus.displayCurrent(mapOf("batteryCurrent" to -8.299)))
        assertEquals("8.8", ChargeStatus.displayCurrent(mapOf("batteryCurrent" to "-8.8 A")))
        assertEquals("8", ChargeStatus.displayCurrent(mapOf("batteryCurrent" to 8)))
    }

    @Test
    fun `keeps missing or non-numeric current safe for display`() {
        assertNull(ChargeStatus.displayCurrent(mapOf("batteryCurrent" to null)))
        assertEquals("unknown", ChargeStatus.displayCurrent(mapOf("batteryCurrent" to "unknown")))
    }

    @Test
    fun `does not calculate power outside charging state`() {
        val status = mapOf(
            "chargeState" to 2,
            "batteryVoltage" to 400,
            "batteryCurrent" to 25
        )

        assertNull(ChargeStatus.power(status))
    }

    @Test
    fun `formats remaining minutes and completion`() {
        val status = mapOf("chargeState" to 1, "chargeCompleted" to false)

        assertEquals("45分钟", ChargeStatus.remainingTime(45))
        assertEquals("1时48分", ChargeStatus.remainingTime(108))
        assertEquals("2时3分", ChargeStatus.remainingTime("约 123 分钟"))
        assertEquals("0分钟", ChargeStatus.remainingTime(0))
        assertTrue(!ChargeStatus.completed(status))
        assertTrue(ChargeStatus.completed(mapOf("chargeState" to 2)))
    }

    @Test
    fun `only ended or paused sessions with SOC 100 display full`() {
        for (state in listOf(2, 6)) {
            for (soc in listOf(null, 0, 80, 90, 99, -1, 101)) {
                val result = ChargeStatus.presentation(state, soc, "0分钟")
                assertEquals(ChargeStatus.PresentationState.STOPPED, result.state)
                assertEquals("充电已停止", result.label)
                assertNull(result.remainingTime)
            }
            assertEquals("已充满", ChargeStatus.presentation(state, 100, "30分钟").label)
        }
    }

    @Test
    fun `active charging never becomes full from SOC or zero or legacy time text`() {
        for (soc in listOf(null, 80, 99, 100)) {
            for (time in listOf(null, "0", "0分钟", "0时0分", "已充满", "--", "未知", "-1", "NaN")) {
                val result = ChargeStatus.presentation(1, soc, time)
                assertEquals(ChargeStatus.PresentationState.CHARGING, result.state)
                assertEquals("充电中", result.label)
                assertNull(result.remainingTime)
            }
            assertEquals("1时20分", ChargeStatus.presentation(1, soc, "约1小时20分钟").remainingTime)
        }
    }

    @Test
    fun `schedule fault idle and unknown are not overridden by full SOC or remaining time`() {
        for (soc in listOf(null, 80, 99, 100)) {
            assertEquals("预约充电等待", ChargeStatus.presentation(4, soc, "0分钟").label)
            assertEquals("充电故障", ChargeStatus.presentation(3, soc, "已充满").label)
            assertEquals("未充电", ChargeStatus.presentation(0, soc, "已充满").label)
            for (state in listOf(null, -1, 99)) {
                assertEquals("充电状态未知", ChargeStatus.presentation(state, soc, "0分钟").label)
            }
        }
    }

    @Test
    fun `remaining time accepts only valid durations and never emits a state`() {
        for (value in listOf(null, org.json.JSONObject.NULL, "", "--", "未知", "已充满", -1, "-0.1", "NaN", "Infinity", "1时99分", "abc12", "999999999999999")) {
            assertNull("value=$value", ChargeStatus.remainingTime(value))
        }
        assertEquals("0分钟", ChargeStatus.remainingTime("0时0分"))
        assertEquals("1时0分", ChargeStatus.remainingTime(60))
        assertEquals("2时0分", ChargeStatus.remainingTime("2小时"))
        assertEquals("45分钟", ChargeStatus.remainingTime("45分"))
    }

    @Test
    fun `completion transition remains independent from full battery presentation`() {
        val ended = mapOf("chargeState" to 2, "soc" to 80)
        assertTrue(ChargeStatus.completed(ended))
        assertTrue(ChargeNotificationPolicy.shouldNotifyCompleted(1, ChargeStatus.completed(ended)))
        assertEquals("充电已停止", ChargeStatus.presentation(2, 80).label)
        assertTrue(ChargeNotificationPolicy.shouldNotifyInterrupted(1, 6))
        assertTrue(ChargeNotificationPolicy.shouldNotifyFault(1, 3))
    }

    @Test
    fun `range extender completion follows traction battery signals only`() {
        val notFull = org.json.JSONObject("""
            {
              "chargeState": 1,
              "chargeCompleted": false,
              "3259": 71,
              "3260": 137,
              "3261": 208
            }
        """)
        val full = org.json.JSONObject("""
            {
              "chargeState": 2,
              "chargeCompleted": false,
              "3259": 0,
              "3260": 137,
              "3261": 137
            }
        """)

        assertTrue(
            !ChargeStatus.completed(
                notFull,
                VehicleStatusMapper.PowerType.RANGE_EXTENDER
            )
        )
        assertTrue(
            ChargeStatus.completed(
                full,
                VehicleStatusMapper.PowerType.RANGE_EXTENDER
            )
        )
    }

    @Test
    fun `unconfirmed completion flag cannot mark a battery as full`() {
        val stillCharging = org.json.JSONObject("""
            {
              "chargeState": 1,
              "chargeCompleted": true,
              "3259": 896,
              "3260": 291,
              "3261": 1187
            }
        """)

        assertTrue(!ChargeStatus.completed(stillCharging, VehicleStatusMapper.PowerType.PURE_ELECTRIC))
    }

    @Test
    fun `completion notification requires an observed charging transition`() {
        assertTrue(ChargeNotificationPolicy.shouldNotifyCompleted(previousState = 1, completed = true))
        assertTrue(!ChargeNotificationPolicy.shouldNotifyCompleted(previousState = 0, completed = true))
        assertTrue(!ChargeNotificationPolicy.shouldNotifyCompleted(previousState = 1, completed = false))
        assertTrue(!ChargeNotificationPolicy.shouldNotifyCompleted(previousState = 2, completed = true))
    }

    @Test
    fun `isGunConnected correctly detects slow charge, fast charge and active charge states`() {
        // 未插枪且未充电
        assertEquals(false, ChargeStatus.isGunConnected(mapOf("acInputSlowCharge" to 0, "dcInputFastCharge" to 0, "chargeState" to 0)))
        assertEquals(false, ChargeStatus.isGunConnected(null as Map<String, Any?>?))

        // 慢充枪插入
        assertEquals(true, ChargeStatus.isGunConnected(mapOf("acInputSlowCharge" to 1, "chargeState" to 0)))

        // 快充枪插入
        assertEquals(true, ChargeStatus.isGunConnected(mapOf("dcInputFastCharge" to 1, "chargeState" to 0)))

        // 充电中或已充满
        assertEquals(true, ChargeStatus.isGunConnected(mapOf("chargeState" to 1)))
        assertEquals(true, ChargeStatus.isGunConnected(mapOf("chargeState" to 2)))
    }
}
