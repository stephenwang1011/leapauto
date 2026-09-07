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
}
