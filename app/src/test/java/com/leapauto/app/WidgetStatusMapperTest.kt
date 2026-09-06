package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class WidgetStatusMapperTest {

    @Test
    fun `driving gear wins over every other widget status`() {
        assertEquals(
            "行驶中",
            WidgetStatusMapper.label(
                mapOf(
                    "gearStatus" to "D",
                    "chargeState" to 1,
                    "driverDoorLockStatus" to true,
                    "leftFrontWindowPercent" to 30
                )
            )
        )
    }

    @Test
    fun `charging and completed states are displayed`() {
        assertEquals("充电中", WidgetStatusMapper.label(mapOf("chargeState" to 1)))
        assertEquals("充电完成", WidgetStatusMapper.label(mapOf("chargeState" to 2)))
    }

    @Test
    fun `charging presentation overrides open window and formats remaining time`() {
        val status = JSONObject().apply {
            put("chargeState", 1)
            put("chargeRemainTime", 108)
            put("driverDoorLockStatus", true)
            put("leftFrontWindowPercent", 30)
        }

        val presentation = WidgetStatusMapper.presentation(status, "C16")

        assertEquals("剩余1时48分", presentation.label)
        assertTrue(presentation.showChargingIcon)
    }

    @Test
    fun `completed presentation remains visible until the state reports unplugged`() {
        val completed = WidgetStatusMapper.presentation(
            chargeState = 2,
            chargeRemainTime = "已充满",
            fallbackLabel = "充电完成"
        )
        val unpluggedWithOpenWindow = WidgetStatusMapper.presentation(
            chargeState = 0,
            chargeRemainTime = null,
            fallbackLabel = "车窗未关闭"
        )

        assertEquals("充电完成", completed.label)
        assertFalse(completed.showChargingIcon)
        assertEquals("车窗未关闭", unpluggedWithOpenWindow.label)
    }

    @Test
    fun `locked car with an open window shows safety status`() {
        assertEquals(
            "车窗未关闭",
            WidgetStatusMapper.label(
                mapOf("driverDoorLockStatus" to true, "rightRearWindowPercent" to "15")
            )
        )
        assertEquals(
            "车窗未关闭",
            WidgetStatusMapper.label(
                mapOf("driverDoorLockStatus" to "1", "driverWindowStatus" to true)
            )
        )
    }

    @Test
    fun `open window warning does not depend on lock telemetry`() {
        assertEquals(
            "车窗未关闭",
            WidgetStatusMapper.label(
                mapOf("driverDoorLockStatus" to false, "leftFrontWindowPercent" to 30)
            )
        )
        assertEquals(
            "车窗未关闭",
            WidgetStatusMapper.label(
                mapOf("rightRearWindowPercent" to 15)
            )
        )
    }

    @Test
    fun `open window labels are ordered and T03 is excluded`() {
        val status = JSONObject().apply {
            put("rightRearWindowPercent", 15)
            put("driverWindowStatus", true)
            put("rightFrontWindowStatus", true)
        }

        assertEquals(
            listOf("左前", "右前", "右后"),
            WidgetStatusMapper.openWindowLabels(status, "C16")
        )
        assertEquals(emptyList<String>(), WidgetStatusMapper.openWindowLabels(status, "T03"))
        assertTrue(WidgetStatusMapper.hasWindowTelemetry(status, "C16"))
        assertFalse(WidgetStatusMapper.hasWindowTelemetry(status, "T03"))
        assertFalse(WidgetStatusMapper.hasWindowTelemetry(JSONObject(), "C16"))
    }

    @Test
    fun `normal unplugged parked vehicle has no widget status text`() {
        assertNull(
            WidgetStatusMapper.label(
                mapOf(
                    "chargeState" to 0,
                    "driverDoorLockStatus" to true,
                    "leftFrontWindowPercent" to 0,
                    "rightFrontWindowPercent" to 0,
                    "leftRearWindowPercent" to 0,
                    "rightRearWindowPercent" to 0
                )
            )
        )
    }

    @Test
    fun `widget refresh treats positive speed as driving regardless of door lock`() {
        assertTrue(
            WidgetStatusMapper.isDriving(
                JSONObject().apply {
                    put("speed", 12.0)
                    put("driverDoorLockStatus", true)
                }
            )
        )
        assertFalse(
            WidgetStatusMapper.isDriving(
                JSONObject().apply {
                    put("speed", 0)
                    put("driverDoorLockStatus", false)
                }
            )
        )
    }

    @Test
    fun `widget refresh falls back to explicit driving gear only when speed is absent`() {
        assertTrue(WidgetStatusMapper.isDriving(JSONObject().put("gearStatus", "D")))
        assertFalse(
            WidgetStatusMapper.isDriving(
                JSONObject().apply {
                    put("speed", 0)
                    put("gearStatus", "D")
                }
            )
        )
    }
}
