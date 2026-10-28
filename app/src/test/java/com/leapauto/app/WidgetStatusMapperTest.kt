package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class WidgetStatusMapperTest {

    @Test
    fun `rounded display SOC cannot replace unknown or sub full charging SOC`() {
        val snapshot = SessionStore.WidgetSnapshot(
            vin = "fixture", carType = "C16", range = "100", soc = 100, updated = "",
            chargeState = 2, chargeRemainTime = "已充满"
        )
        assertEquals("充电已停止", WidgetStatusMapper.presentation(
            snapshot.chargeState, snapshot.chargeRemainTime, snapshot.statusLabel, snapshot.chargeSoc
        ).label)
        for (rawSoc in listOf(99.9, "未知", org.json.JSONObject.NULL, 101, "NaN")) {
            assertEquals("充电已停止", WidgetStatusMapper.presentation(
                JSONObject().put("chargeState", 2).put("soc", rawSoc).put("preciseSoc", 100)
            ).label)
        }
    }

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
        assertEquals("充电已停止", WidgetStatusMapper.label(mapOf("chargeState" to 2)))
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

        assertEquals("充电已停止", completed.label)
        assertFalse(completed.showChargingIcon)
        assertEquals("车窗未关闭", unpluggedWithOpenWindow.label)
    }

    @Test
    fun `raw and snapshot presentations share end state and SOC policy`() {
        for (state in listOf(2, 6)) {
            for (soc in listOf(null, 80, 90, 99, 100)) {
                val expected = if (soc == 100) "已充满" else "充电已停止"
                val raw = JSONObject().put("chargeState", state).put("soc", soc ?: JSONObject.NULL)
                    .put("chargeRemainTime", 0).put("leftFrontWindowPercent", 30)
                val live = WidgetStatusMapper.presentation(raw)
                val cached = WidgetStatusMapper.presentation(state, "已充满", "充电完成", soc)
                assertEquals(expected, live.label)
                assertEquals(live, cached)
                assertEquals(expected, WidgetStatusMapper.label(raw))
                assertFalse(live.showChargingIcon)
            }
        }
    }

    @Test
    fun `charging at full SOC keeps bolt and valid time or charging label`() {
        for (time in listOf(null, "0分钟", "已充满", "--", "NaN", "-1")) {
            val result = WidgetStatusMapper.presentation(1, time, "车窗未关闭", 100)
            assertEquals("充电中", result.label)
            assertTrue(result.showChargingIcon)
        }
        assertEquals("剩余45分钟", WidgetStatusMapper.presentation(1, "45分钟", null, 100).label)
        assertEquals("充电中", WidgetStatusMapper.presentation(JSONObject().put("chargeState", 1).put("soc", 100).put("chargeRemainTime", 0)).label)
    }

    @Test
    fun `schedule fault and driving priority remain and idle does not imply stopped`() {
        assertEquals("预约充电等待", WidgetStatusMapper.presentation(4, "0分钟", null, 100).label)
        assertEquals("充电故障", WidgetStatusMapper.presentation(3, "已充满", null, 100).label)
        assertNull(WidgetStatusMapper.presentation(0, "已充满", null, 100).label)
        assertEquals("车窗未关闭", WidgetStatusMapper.presentation(0, "0分钟", "车窗未关闭", 100).label)
        for (state in listOf(1, 2, 3, 4, 6)) {
            assertEquals("行驶中", WidgetStatusMapper.presentation(state, "0分钟", "行驶中", 100).label)
        }
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
