package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeStatusTest {

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
        assertEquals("已充满", ChargeStatus.remainingTime(0))
        assertTrue(!ChargeStatus.completed(status))
        assertTrue(ChargeStatus.completed(mapOf("chargeState" to 2)))
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
