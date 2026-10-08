package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleChargePlanTest {

    @Test
    fun fromConfigParsesOfficialZeroRunChargePlanFormat() {
        val json = JSONObject("""
            {
                "3": {
                    "isEnable": 1,
                    "beginTime": "23:00",
                    "endTime": "07:00",
                    "cycles": "1,2,3,4,5,6,7",
                    "circulation": 1,
                    "recharge": 1,
                    "percent": 90
                },
                "4": {
                    "mac": "00:11:22:33:44:55",
                    "version": "2.0"
                }
            }
        """.trimIndent())

        val plan = VehicleChargePlan.fromConfig(json)
        assertNotNull(plan)
        assertTrue(plan!!.isEnable)
        assertEquals("23:00", plan.beginTime)
        assertEquals("07:00", plan.endTime)
        assertEquals("1,2,3,4,5,6,7", plan.cycles)
        assertEquals(1, plan.circulation)
        assertTrue(plan.recharge)
        assertEquals(90, plan.percent)
    }

    @Test
    fun fromConfigHandlesDisabledChargePlan() {
        val json = JSONObject("""
            {
                "3": {
                    "isEnable": 0,
                    "beginTime": "22:30",
                    "endTime": "06:00",
                    "cycles": "1,1,1,1,1,0,0",
                    "circulation": 0,
                    "recharge": 0,
                    "percent": 80
                }
            }
        """.trimIndent())

        val plan = VehicleChargePlan.fromConfig(json)
        assertNotNull(plan)
        assertFalse(plan!!.isEnable)
        assertEquals("22:30", plan.beginTime)
        assertEquals("06:00", plan.endTime)
        assertEquals("1,1,1,1,1,0,0", plan.cycles)
        assertEquals(0, plan.circulation)
        assertFalse(plan.recharge)
        assertEquals(80, plan.percent)
    }

    @Test
    fun fromConfigReturnsNullWhenPlanThreeMissingOrNull() {
        assertNull(VehicleChargePlan.fromConfig(null))
        assertNull(VehicleChargePlan.fromConfig(JSONObject()))
        assertNull(VehicleChargePlan.fromConfig(JSONObject("""{"4": {"mac": "..."}}""")))
    }
}
