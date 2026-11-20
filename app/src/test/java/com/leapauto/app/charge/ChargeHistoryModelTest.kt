package com.leapauto.app.charge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ChargeHistoryModelTest {

    @Test
    fun `parse charge statistics for recent 7 days correctly`() {
        val jsonStr = """
            {
                "code": 0,
                "result": 0,
                "message": "请求成功",
                "data": {
                    "totalChargeCounts": 1,
                    "totalChargeInEnergy": 54.3,
                    "fastChargePercent": 0.0,
                    "chargeStatisList": [
                        {
                            "vin": "LFZ63AZ55SH023503",
                            "statisDay": "2026-11-15 00:00:00",
                            "accountId": "728504145190903808",
                            "chargeInEnergy": 54.27
                        }
                    ]
                }
            }
        """.trimIndent()

        val json = JSONObject(jsonStr).getJSONObject("data")
        val result = ChargeStatisticResult.fromJson(json, ChargePeriodType.RECENT_7_DAYS)

        assertEquals(1, result.totalCounts)
        assertEquals(54.3, result.totalEnergyKwh, 0.01)
        assertEquals(0.0, result.fastChargePercent, 0.01)
        assertEquals(1, result.points.size)
        val point = result.points[0]
        assertEquals("11/15", point.dateLabel)
        assertEquals(54.27, point.energyKwh, 0.01)
    }

    @Test
    fun `parse charge statistics for year correctly`() {
        val jsonStr = """
            {
                "code": 0,
                "result": 0,
                "data": {
                    "totalChargeCounts": 81,
                    "totalChargeInEnergy": 2953.5,
                    "fastChargePercent": 25.9,
                    "chargeStatisList": [
                        { "statisDay": "2026-11-01 00:00:00", "chargeInEnergy": 54.27 },
                        { "statisDay": "2026-10-01 00:00:00", "chargeInEnergy": 306.9 }
                    ]
                }
            }
        """.trimIndent()

        val json = JSONObject(jsonStr).getJSONObject("data")
        val result = ChargeStatisticResult.fromJson(json, ChargePeriodType.YEAR)

        assertEquals(81, result.totalCounts)
        assertEquals(2953.5, result.totalEnergyKwh, 0.01)
        assertEquals(25.9, result.fastChargePercent, 0.01)
        assertEquals(2, result.points.size)
        assertEquals("11月", result.points[0].dateLabel)
        assertEquals("10月", result.points[1].dateLabel)
    }

    @Test
    fun `parse charge detail slow charge with cross-day formatting`() {
        // 2026/11/14 23:00:09 -> 2026/11/15 07:55:48 (8h 56m)
        val jsonStr = """
            {
                "chargeGunStartTs": 1794668409000,
                "chargeGunEndTs": 1794700548000,
                "chargeType": "SLOW",
                "chargeInEnergy": 54.27,
                "chargeStartLongitude": "112.12754",
                "chargeStartLatitude": "32.09452",
                "zone": "UTC+8"
            }
        """.trimIndent()

        val item = ChargeDetailItem.fromJson(JSONObject(jsonStr))

        assertFalse(item.isFastCharge)
        assertEquals(54.27, item.energyKwh, 0.01)
        assertEquals("8 h 56 min", item.durationFormatted)
        assertTrue(
            "Time range should indicate next day +1",
            item.timeRangeFormatted.contains("⁺¹")
        )
        assertTrue(item.timeRangeFormatted.startsWith("2026/11/14"))
    }

    @Test
    fun `parse charge detail fast charge on same day formatting`() {
        // 32 min fast charge on 2026/08/01
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply {
            set(2026, Calendar.AUGUST, 1, 11, 54, 0)
        }
        val startTs = cal.timeInMillis
        cal.add(Calendar.MINUTE, 32)
        val endTs = cal.timeInMillis

        val json = JSONObject().apply {
            put("chargeGunStartTs", startTs)
            put("chargeGunEndTs", endTs)
            put("chargeType", "FAST")
            put("chargeInEnergy", 30.5)
        }

        val item = ChargeDetailItem.fromJson(json)

        assertTrue(item.isFastCharge)
        assertEquals(30.5, item.energyKwh, 0.01)
        assertEquals("32 min", item.durationFormatted)
        assertFalse(
            "Same day charge should not contain +1 superscript",
            item.timeRangeFormatted.contains("⁺")
        )

        // 验证多别名智能识别：QUICK, DC
        val quickItem = item.copy(chargeType = "QUICK")
        assertTrue(quickItem.isFastCharge)

        val dcItem = item.copy(chargeType = "DC")
        assertTrue(dcItem.isFastCharge)

        // 验证物理平均功率双保险（哪怕字段写成未知，但半小时充 30 度即 57kW 依然判定为快充）
        val unknownFieldItem = item.copy(chargeType = "UNKNOWN")
        assertTrue(unknownFieldItem.isFastCharge)

        // 验证低功率且显式 SLOW 为慢充
        val slowItem = item.copy(chargeType = "SLOW", energyKwh = 3.0) // 3kW 交流
        assertFalse(slowItem.isFastCharge)
    }

    @Test
    fun `densePoints pads sparse points to full sequence`() {
        val sparse = ChargeStatisticResult(
            totalCounts = 1,
            totalEnergyKwh = 54.3,
            fastChargePercent = 0.0,
            points = listOf(
                ChargeStatisticPoint(
                    dateLabel = "11/15",
                    fullDate = "2026-11-15 00:00:00",
                    energyKwh = 54.27
                )
            )
        )

        // 1. 近7天测试：基准日 2026-11-20，应生成连续 7 天 (11/14 ~ 11/20)
        val refCal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply {
            set(2026, Calendar.NOVEMBER, 20)
        }
        val dense7 = sparse.densePoints(ChargePeriodType.RECENT_7_DAYS, 2026, 11, refCal)
        assertEquals(7, dense7.size)
        assertEquals("11/14", dense7[0].dateLabel)
        assertEquals(0.0, dense7[0].energyKwh, 0.01)
        assertEquals("11/15", dense7[1].dateLabel)
        assertEquals(54.27, dense7[1].energyKwh, 0.01)
        assertEquals("11/20", dense7[6].dateLabel)

        // 2. 月测试：2026年11月有 30 天，应补齐 30 个点
        val denseMonth = sparse.densePoints(ChargePeriodType.MONTH, 2026, 11)
        assertEquals(30, denseMonth.size)
        assertEquals("1", denseMonth[0].dateLabel)
        assertEquals("15", denseMonth[14].dateLabel)
        assertEquals(54.27, denseMonth[14].energyKwh, 0.01)

        // 3. 年测试：应补齐 12 个月 (1月 ~ 12月)
        val denseYear = sparse.densePoints(ChargePeriodType.YEAR, 2026, 11)
        assertEquals(12, denseYear.size)
        assertEquals("1月", denseYear[0].dateLabel)
        assertEquals("11月", denseYear[10].dateLabel)
        assertEquals(54.27, denseYear[10].energyKwh, 0.01)
        assertEquals("12月", denseYear[11].dateLabel)
    }

    @Test
    fun `empty json safety defaults`() {
        val emptyStat = ChargeStatisticResult.fromJson(JSONObject(), ChargePeriodType.RECENT_7_DAYS)
        assertEquals(0, emptyStat.totalCounts)
        assertEquals(0.0, emptyStat.totalEnergyKwh, 0.001)
        assertEquals(0.0, emptyStat.fastChargePercent, 0.001)
        assertTrue(emptyStat.points.isEmpty())

        val emptyDetail = ChargeDetailItem.fromJson(JSONObject())
        assertEquals(0L, emptyDetail.startTs)
        assertEquals(0L, emptyDetail.endTs)
        assertFalse(emptyDetail.isFastCharge)
        assertEquals("1 min", emptyDetail.durationFormatted)
    }
}
