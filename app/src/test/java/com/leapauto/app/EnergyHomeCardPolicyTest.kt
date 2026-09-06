package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyHomeCardPolicyTest {

    @Test
    fun `home pager exposes four stable pages`() {
        assertEquals(4, EnergyHomeCardPolicy.PAGE_COUNT)
    }

    @Test
    fun `home pager keeps only the newest six weekly points`() {
        val data = energyData(
            trend = (1..8).map { EnergySeriesPoint("week-$it", it.toDouble()) }
        )

        assertEquals(
            listOf("week-3", "week-4", "week-5", "week-6", "week-7", "week-8"),
            EnergyHomeCardPolicy.recentTrend(data).map { it.label }
        )
    }

    @Test
    fun `home pager keeps only the newest seven mileage points`() {
        val data = energyData(
            mileageTrend = (1..9).map { EnergySeriesPoint("day-$it", it.toDouble()) }
        )

        assertEquals(
            listOf("day-3", "day-4", "day-5", "day-6", "day-7", "day-8", "day-9"),
            EnergyHomeCardPolicy.recentMileage(data).map { it.label }
        )
    }

    @Test
    fun `home pager returns empty lists for missing chart data`() {
        val data = energyData()

        assertEquals(emptyList<EnergySeriesPoint>(), EnergyHomeCardPolicy.recentTrend(data))
        assertEquals(emptyList<EnergySeriesPoint>(), EnergyHomeCardPolicy.recentMileage(data))
    }

    @Test
    fun `todayMileage formats latest point without space`() {
        val data = energyData(
            mileageTrend = listOf(
                EnergySeriesPoint("2026-09-04", 50.0),
                EnergySeriesPoint("2026-09-05", 112.0)
            )
        )
        assertEquals("112km", EnergyHomeCardPolicy.todayMileage(data))
    }

    @Test
    fun `todayMileage formats decimal correctly`() {
        val data = energyData(
            mileageTrend = listOf(
                EnergySeriesPoint("2026-09-05", 23.4)
            )
        )
        assertEquals("23.4km", EnergyHomeCardPolicy.todayMileage(data))
    }

    @Test
    fun `todayMileage returns double dash for missing data`() {
        assertEquals("--", EnergyHomeCardPolicy.todayMileage(null))
        assertEquals("--", EnergyHomeCardPolicy.todayMileage(energyData()))
    }

    private fun energyData(
        trend: List<EnergySeriesPoint> = emptyList(),
        mileageTrend: List<EnergySeriesPoint> = emptyList()
    ) = EnergyAnalyticsData(
        overallConsumption = null,
        totalMileage = null,
        cumulativeEnergy = null,
        ownershipDays = null,
        recentMileage = null,
        trend = trend,
        mileageTrend = mileageTrend,
        composition = emptyList(),
        otherFields = emptyList(),
        capturedAt = 1L
    )
}
