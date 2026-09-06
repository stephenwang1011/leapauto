package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyAnalyticsTest {

    @Test
    fun `uses the verified legacy mileage energy endpoint`() {
        assertEquals(
            "/carownerservice/v3/api/drivingrecord/mileage/energy/detail",
            LeapmotorApi.MILEAGE_ENERGY_DETAIL_PATH
        )
        assertTrue(LeapmotorApi.isMileageEnergyDetailEndpoint("/mileage/energy/detail"))
        assertFalse(LeapmotorApi.isMileageEnergyDetailEndpoint("/getLastweekEC"))
        assertEquals(
            listOf(LeapmotorApi.DRIVING_RECORD_HOST),
            LeapmotorApi.mileageEnergyHosts()
        )
    }

    @Test
    fun `exposes the weekly energy rank endpoint as a separate manual probe`() {
        assertEquals(
            "/carownerservice/v3/api/drivingrecord/getLastNweeks100kmECAndRank",
            LeapmotorApi.LAST_N_WEEKS_100KM_EC_RANK_PATH
        )
        assertEquals(
            "carvin",
            LeapmotorApi.drivingRecordVinParameterKey("/getLastNweeks100kmECAndRank")
        )
        assertEquals(
            "vin",
            LeapmotorApi.drivingRecordVinParameterKey("/mileage/energy/detail")
        )
        val response = VehicleListRawResponse(200, "{\"data\":{\"rank\":35}}")
        assertTrue(response.isHttpSuccessful)
        assertTrue(JSONObject(response.rawBody).getJSONObject("data").has("rank"))
    }

    @Test
    fun `parses structured metrics charts and keeps unknown business values`() {
        val response = JSONObject(
            """
            {
              "result": 0,
              "data": {
                "totalMileage": 19632,
                "cumulativeEnergy": 3349,
                "pickupDays": 402,
                "recentMileage": 334,
                "averageEnergyConsumption": 17.4,
                "energyTrend": [
                  {"date": "07/06", "energy": 17.6},
                  {"date": "07/13", "energy": 0}
                ],
                "energyComposition": [
                  {"name": "驾驶", "energy": 35.2},
                  {"name": "空调", "energy": 1.5}
                ],
                "mileageTrend": [
                  {"date": "07/06", "mileage": 12},
                  {"date": "07/13", "mileage": 34}
                ],
                "serverBusinessFlag": "available",
                "vin": "must-not-display",
                "token": "must-not-display"
              }
            }
            """.trimIndent()
        )

        val data = EnergyAnalyticsParser.parse(response, capturedAt = 123L)

        assertEquals("17.4", data.overallConsumption?.value)
        assertEquals("综合能耗", data.overallConsumption?.label)
        assertEquals("19632", data.totalMileage?.value)
        assertEquals("总里程", data.totalMileage?.label)
        assertEquals("3349", data.cumulativeEnergy?.value)
        assertEquals("402", data.ownershipDays?.value)
        assertEquals("334", data.recentMileage?.value)
        assertEquals(2, data.trend.size)
        assertEquals(2, data.mileageTrend.size)
        assertEquals(0.0, data.trend[1].value, 0.0)
        assertEquals(2, data.composition.size)
        assertTrue(data.otherFields.any { it.first.endsWith("serverBusinessFlag") && it.second == "available" })
        assertFalse(data.otherFields.any { it.first.contains("energyTrend", ignoreCase = true) })
        assertFalse(data.otherFields.any { it.first.contains("mileageTrend", ignoreCase = true) })
        assertFalse(data.otherFields.any { it.first.contains("vin", ignoreCase = true) })
        assertFalse(data.otherFields.any { it.first.contains("token", ignoreCase = true) })
    }

    @Test
    fun `parses documented detail rows as mileage trend without duplicating raw leaves`() {
        val data = EnergyAnalyticsParser.parse(
            JSONObject(
                """
                {
                  "result": 0,
                  "data": {
                    "detail": [
                      {"day": "2026-08-13", "accumulatedMileage": 12.5},
                      {"day": "2026-08-14", "accumulatedMileage": 0}
                    ],
                    "deliveryDays": 402
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(
            listOf("2026-08-13", "2026-08-14"),
            data.mileageTrend.map { it.label }
        )
        assertEquals(listOf(12.5, 0.0), data.mileageTrend.map { it.value })
        assertFalse(data.otherFields.any { it.first.contains("detail", ignoreCase = true) })
    }

    @Test
    fun `parses the verified weekly energy rank response`() {
        val data = EnergyRankAnalyticsParser.parse(
            JSONObject(
                """
                {
                  "result": 0,
                  "code": 0,
                  "data": {
                    "rankResult": {"result": 0, "rank": "35%", "hundredKmEC": 17.4},
                    "weeklyEC": [
                      {"weekStart": "2026-07-06", "hundredKmEC": 17.6, "weekEnd": "2026-07-12"},
                      {"weekStart": "2026-07-13", "hundredKmEC": 15.6, "weekEnd": "2026-07-19"},
                      {"weekStart": "2026-08-10", "hundredKmEC": 0, "weekEnd": "2026-08-16"}
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals("17.4", data.overallConsumption?.value)
        assertEquals("kWh/100km", data.overallConsumption?.unit)
        assertEquals("35%", data.rankLabel)
        assertEquals(
            listOf("2026-07-06", "2026-07-13", "2026-08-10"),
            data.weeklyTrend.map { it.label }
        )
        assertEquals(
            listOf("2026-07-12", "2026-07-19", "2026-08-16"),
            data.weeklyTrend.map { it.endLabel }
        )
        assertEquals(0.0, data.weeklyTrend.last().value, 0.0)
    }

    @Test
    fun `parses verified previous week energy composition including zero values`() {
        val categories = LastWeekEnergyCompositionParser.parse(
            JSONObject(
                """
                {"result":0,"code":0,"data":{"driverEC":"35.2","acEC":"1.5","otherEC":"0"}}
                """.trimIndent()
            )
        )

        assertEquals(
            listOf(
                EnergyCategory("驾驶", 35.2),
                EnergyCategory("空调", 1.5),
                EnergyCategory("其他", 0.0)
            ),
            categories
        )
    }

    @Test
    fun `presents verified weekly energy categories as percentages`() {
        assertEquals("行车能耗", EnergyCompositionPresentation.displayLabel("驾驶"))
        assertEquals("空调能耗", EnergyCompositionPresentation.displayLabel("空调"))
        assertEquals("其他能耗", EnergyCompositionPresentation.displayLabel("其他"))
        assertEquals("88%", EnergyCompositionPresentation.displayPercent(35.2, 40.0))
        assertEquals("3.75%", EnergyCompositionPresentation.displayPercent(1.5, 40.0))
        assertEquals("0%", EnergyCompositionPresentation.displayPercent(0.0, 0.0))
    }

    @Test
    fun `formats weekly dates without zero padding or year`() {
        assertEquals(
            "7/6-7/12",
            EnergyWeekPeriodFormatter.format("2026-07-06", "2026-07-12")
        )
        assertEquals(
            "8/10-8/16",
            EnergyWeekPeriodFormatter.format("2026-08-10", "2026-08-16")
        )
        assertEquals("custom-label", EnergyWeekPeriodFormatter.format("custom-label", null))
    }

    @Test(expected = EnergyAnalyticsBusinessException::class)
    fun `previous week composition business failure is rejected`() {
        LastWeekEnergyCompositionParser.parse(JSONObject("""{"result":2,"data":{}}"""))
    }

    @Test(expected = EnergyAnalyticsBusinessException::class)
    fun `weekly rank business failure is rejected`() {
        EnergyRankAnalyticsParser.parse(JSONObject("""{"result":39,"data":{}}"""))
    }

    @Test
    fun `maps verified delivery days to ownership days`() {
        val data = EnergyAnalyticsParser.parse(
            JSONObject("""{"result":0,"data":{"totalMileage":19639,"deliveryDays":402}}""")
        )

        assertEquals("402", data.ownershipDays?.value)
        assertEquals("提车天数", data.ownershipDays?.label)
        assertFalse(data.otherFields.any { it.first.endsWith("deliveryDays") })
    }

    @Test
    fun `numeric energy trend remains drawable without inventing dates`() {
        val data = EnergyAnalyticsParser.parse(
            JSONObject("""{"result":0,"data":{"energyTrend":[17.4,0,-1]}}""")
        )
        assertEquals(listOf("#1", "#2"), data.trend.map { it.label })
        assertEquals(listOf(17.4, 0.0), data.trend.map { it.value })
    }

    @Test
    fun `ignores invalid negative chart values without discarding valid zero values`() {
        val data = EnergyAnalyticsParser.parse(
            JSONObject(
                """
                {
                  "result": 0,
                  "data": {
                    "energyTrend": [
                      {"date": "08/01", "energy": -1},
                      {"date": "08/08", "energy": 0}
                    ],
                    "energyComposition": [
                      {"name": "驾驶", "energy": -3},
                      {"name": "空调", "energy": 0}
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf(EnergySeriesPoint("08/08", 0.0)), data.trend)
        assertEquals(listOf(EnergyCategory("空调", 0.0)), data.composition)
    }

    @Test(expected = EnergyAnalyticsParseException::class)
    fun `business failure does not become a success model`() {
        EnergyAnalyticsParser.parse(JSONObject("""{"result":39,"msg":"token expired"}"""))
    }

    @Test(expected = EnergyAnalyticsEmptyDataException::class)
    fun `empty business payload is not displayed as a successful report`() {
        EnergyAnalyticsParser.parse(JSONObject("""{"result":0,"data":{}}"""))
    }

    @Test
    fun `refresh policy reuses memory for fifteen minutes unless forced`() {
        val startedAt = 1_000_000L
        assertFalse(EnergyRefreshPolicy.shouldRefresh(startedAt, startedAt + 14 * 60 * 1000L, false))
        assertTrue(EnergyRefreshPolicy.shouldRefresh(startedAt, startedAt + 15 * 60 * 1000L, false))
        assertTrue(EnergyRefreshPolicy.shouldRefresh(startedAt, startedAt + 1L, true))
        assertTrue(EnergyRefreshPolicy.shouldRefresh(0L, startedAt, false))
    }

    @Test
    fun `home ignores cached analytics without any displayable metrics`() {
        val emptyHomeData = EnergyAnalyticsData(
            overallConsumption = null,
            totalMileage = null,
            cumulativeEnergy = null,
            ownershipDays = null,
            recentMileage = null,
            trend = emptyList(),
            mileageTrend = emptyList(),
            composition = emptyList(),
            otherFields = listOf("data.serverFlag" to "available"),
            capturedAt = 1L
        )

        assertFalse(EnergyRefreshPolicy.hasHomeDisplayableData(emptyHomeData))
        assertTrue(
            EnergyRefreshPolicy.hasHomeDisplayableData(
                emptyHomeData.copy(totalMileage = EnergyMetric("总里程", "20110", "km"))
            )
        )
        assertTrue(
            EnergyRefreshPolicy.hasHomeDisplayableData(
                emptyHomeData.copy(lastWeekComposition = listOf(EnergyCategory("驾驶", 12.0)))
            )
        )
    }

    @Test
    fun `energy cache accepts only non future data within twenty four hours`() {
        val now = 1_000_000_000L
        assertTrue(EnergyRefreshPolicy.canUseCachedData(now - 24 * 60 * 60 * 1000L, now))
        assertFalse(EnergyRefreshPolicy.canUseCachedData(now - 24 * 60 * 60 * 1000L - 1L, now))
        assertFalse(EnergyRefreshPolicy.canUseCachedData(now + 1L, now))
        assertFalse(EnergyRefreshPolicy.canUseCachedData(0L, now))
    }
}
