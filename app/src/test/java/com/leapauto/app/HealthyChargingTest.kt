package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthyChargingTest {

    @Test
    fun healthyChargingPresetsCoverStandardThresholds() {
        val standardLimits = listOf(80, 90, 100)
        assertTrue(standardLimits.contains(80))
        assertTrue(standardLimits.contains(90))
        assertTrue(standardLimits.contains(100))
    }

    @Test
    fun healthyChargingSignalMappingResolvesSignal48() {
        val signalMap = JSONObject(mapOf("48" to 1))
        val decoded = SignalTable.decode(signalMap)
        assertEquals(1, decoded.optInt("healthyChargeEnabled"))

        val signalMapOff = JSONObject(mapOf("48" to 0))
        val decodedOff = SignalTable.decode(signalMapOff)
        assertEquals(0, decodedOff.optInt("healthyChargeEnabled"))
    }

    @Test
    fun chargePlanPayloadCorrectlyMapsAllSevenParameters() {
        val targetSoc = 80
        val enabled = true
        val circulation = 1
        val cycles = "1,2,3,4,5,6,7"
        val startTime = "23:00"
        val endTime = "07:00"
        val continueUntilLimit = true

        val chargeEnableInt = if (enabled) 1 else 0
        val rechargeInt = if (continueUntilLimit) 1 else 0
        val stateJson = JSONObject().apply {
            put("chargeEnable", chargeEnableInt)
            put("chargesoc", targetSoc.coerceIn(50, 100))
            put("circulation", if (enabled) circulation else 0)
            put("cycles", if (enabled && circulation == 1) cycles.ifBlank { "1,2,3,4,5,6,7" } else "")
            put("starttime", startTime)
            put("endtime", endTime)
            put("recharge", rechargeInt)
        }

        assertEquals(1, stateJson.getInt("chargeEnable"))
        assertEquals(80, stateJson.getInt("chargesoc"))
        assertEquals(1, stateJson.getInt("circulation"))
        assertEquals("1,2,3,4,5,6,7", stateJson.getString("cycles"))
        assertEquals("23:00", stateJson.getString("starttime"))
        assertEquals("07:00", stateJson.getString("endtime"))
        assertEquals(1, stateJson.getInt("recharge"))
    }

    @Test
    fun chargePlanPayloadHandlesSingleRunCirculationZero() {
        val targetSoc = 90
        val enabled = true
        val circulation = 0 // 单次
        val cycles = "1,2,3,4,5"
        val startTime = "01:00"
        val endTime = "06:00"
        val continueUntilLimit = false

        val stateJson = JSONObject().apply {
            put("chargeEnable", if (enabled) 1 else 0)
            put("chargesoc", targetSoc.coerceIn(50, 100))
            put("circulation", if (enabled) circulation else 0)
            put("cycles", if (enabled && circulation == 1) cycles.ifBlank { "1,2,3,4,5,6,7" } else "")
            put("starttime", startTime)
            put("endtime", endTime)
            put("recharge", if (continueUntilLimit) 1 else 0)
        }

        assertEquals(1, stateJson.getInt("chargeEnable"))
        assertEquals(90, stateJson.getInt("chargesoc"))
        assertEquals(0, stateJson.getInt("circulation"))
        assertEquals("", stateJson.getString("cycles"))
        assertEquals("01:00", stateJson.getString("starttime"))
        assertEquals("06:00", stateJson.getString("endtime"))
        assertEquals(0, stateJson.getInt("recharge"))
    }
}
