package com.leapauto.app

import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentMileageEnergyTest {
    @Test
    fun `recent mileage range uses Shanghai midnight and preserves millisecond now`() {
        val now = Instant.parse("2026-08-19T04:05:06.789Z").toEpochMilli()
        val range = DrivingRecordTimeRange.recentMileageRange(now)

        assertEquals(
            Instant.parse("2026-08-11T16:00:00Z").toEpochMilli(),
            range.beginTimeMs
        )
        assertEquals(now, range.endTimeMs)
    }

    @Test
    fun `recent mileage business parameters use milliseconds and vin`() {
        val range = RecentMileageTimestamps(1_700_000_000_123L, 1_700_000_600_456L)
        val parameters = LeapmotorApi.recentMileageEnergyDetailBusinessParameters("VIN-TEST", range)

        assertEquals(setOf("begintime", "endtime", "vin"), parameters.keys)
        assertEquals("1700000000123", parameters["begintime"])
        assertEquals("1700000600456", parameters["endtime"])
        assertEquals("VIN-TEST", parameters["vin"])
    }

    @Test
    fun `parser keeps last eight network rows, exposes last seven and sums as integer`() {
        val detail = (1..9).joinToString(",") { index ->
            "{\"day\":\"2026-08-${index.toString().padStart(2, '0')}\",\"accumulatedMileage\":$index.9}"
        }
        val parsed = RecentMileageEnergyParser.parse(
            JSONObject(
                """
                {
                  "result": 0,
                  "code": 0,
                  "data": {
                    "detail": [$detail],
                    "totalEnergy": "45.6",
                    "deliveryDays": 402
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(8, parsed.networkRows.size)
        assertEquals(listOf("2026-08-02", "2026-08-03"), parsed.networkRows.take(2).map { it.day })
        assertEquals(7, parsed.mileage.size)
        assertEquals("2026-08-03", parsed.mileage.first().day)
        assertEquals("2026-08-09", parsed.mileage.last().day)
        assertEquals((3.9 + 4.9 + 5.9 + 6.9 + 7.9 + 8.9 + 9.9).toInt(), parsed.totalMileageKm)
        assertEquals(45.6, parsed.totalEnergyKwh!!, 0.0)
        assertEquals(402, parsed.deliveryDays)
    }

    @Test
    fun `parser drops invalid rows but keeps zero mileage`() {
        val parsed = RecentMileageEnergyParser.parse(
            JSONObject(
                """
                {
                  "result": 0,
                  "data": {
                    "detail": [
                      {"day":"2026-08-17", "accumulatedMileage":-1},
                      {"day":"2026-08-18", "accumulatedMileage":"NaN"},
                      {"day":"2026-08-19", "accumulatedMileage":0},
                      {"day":"", "accumulatedMileage":12}
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf(DailyMileage("2026-08-19", 0.0)), parsed.mileage)
        assertEquals(0, parsed.totalMileageKm)
        assertTrue(parsed.totalEnergyKwh == null)
        assertTrue(parsed.deliveryDays == null)
    }

    @Test(expected = EnergyAnalyticsParseException::class)
    fun `missing detail is a protocol parse failure`() {
        RecentMileageEnergyParser.parse(JSONObject("""{"result":0,"data":{}}"""))
    }

    @Test(expected = EnergyAnalyticsEmptyDataException::class)
    fun `empty detail is an empty data state`() {
        RecentMileageEnergyParser.parse(JSONObject("""{"result":0,"data":{"detail":[]}}"""))
    }

    @Test(expected = EnergyAnalyticsBusinessException::class)
    fun `business failure is rejected before payload parsing`() {
        RecentMileageEnergyParser.parse(JSONObject("""{"result":2,"message":"failed"}"""))
    }

    @Test
    fun `parser does not reorder server rows`() {
        val parsed = RecentMileageEnergyParser.parse(
            JSONObject(
                """
                {"result":0,"data":{"detail":[
                  {"day":"2026-08-19","accumulatedMileage":3},
                  {"day":"2026-08-17","accumulatedMileage":1}
                ]}}
                """.trimIndent()
            )
        )
        assertEquals(listOf("2026-08-19", "2026-08-17"), parsed.mileage.map { it.day })
        assertFalse(parsed.mileage.zipWithNext().all { it.first.day <= it.second.day })
    }

    @Test
    fun `large valid rows do not wrap displayed integer total`() {
        val parsed = RecentMileageEnergyParser.parse(
            JSONObject(
                """
                {"result":0,"data":{"detail":[
                  {"day":"2026-08-13","accumulatedMileage":100000000000},
                  {"day":"2026-08-14","accumulatedMileage":100000000000}
                ]}}
                """.trimIndent()
            )
        )
        assertEquals(Int.MAX_VALUE, parsed.totalMileageKm)
    }

    @Test
    fun `monthDay formats iso date to month and day without leading zero`() {
        assertEquals("9/3", EnergyWeekPeriodFormatter.monthDay("2026-09-03"))
        assertEquals("8/28", EnergyWeekPeriodFormatter.monthDay("2026-08-28"))
        assertEquals("12/1", EnergyWeekPeriodFormatter.monthDay("2026-12-01"))
        assertEquals("9/3", EnergyWeekPeriodFormatter.monthDay("9/3"))
    }
}
