package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyHomeCardPolicyTest {

    @Test
    fun `home pager exposes four stable pages`() {
        assertEquals(4, EnergyHomeCardPolicy.PAGE_COUNT)
    }

    @Test
    fun `bottom padding provides compact 8dp clearance for 16dp rounded corners`() {
        assertEquals(8, EnergyHomeCardPolicy.BOTTOM_PADDING_DP)
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

    @Test
    fun `formatCumulativeEnergy rounds to integer without decimals`() {
        assertEquals("3887", EnergyHomeCardPolicy.formatCumulativeEnergy("3887.0"))
        assertEquals("3887", EnergyHomeCardPolicy.formatCumulativeEnergy("3887.0 kWh"))
        assertEquals("3888", EnergyHomeCardPolicy.formatCumulativeEnergy("3887.6"))
        assertEquals("3887", EnergyHomeCardPolicy.formatCumulativeEnergy("3887.4"))
        assertEquals("12345", EnergyHomeCardPolicy.formatCumulativeEnergy("12,345.2"))
        assertEquals("--", EnergyHomeCardPolicy.formatCumulativeEnergy("--"))
        assertEquals("--", EnergyHomeCardPolicy.formatCumulativeEnergy(null))
    }

    @Test
    fun `viewport fill height uses the card real position relative to the home viewport`() {
        assertEquals(
            350,
            EnergyHomeCardPolicy.calculateViewportFillHeightPx(
                viewportHeightPx = 900,
                viewportTopPx = 80,
                cardTopPx = 620,
                bottomPaddingPx = 10,
                minHeightPx = 135
            )
        )
    }

    @Test
    fun `viewport fill height preserves card minimum when content already exceeds viewport`() {
        assertEquals(
            135,
            EnergyHomeCardPolicy.calculateViewportFillHeightPx(
                viewportHeightPx = 900,
                viewportTopPx = 80,
                cardTopPx = 950,
                bottomPaddingPx = 10,
                minHeightPx = 135
            )
        )
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
