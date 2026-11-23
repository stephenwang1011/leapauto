// [FEATURE: TRIP_LOG] - 行程模型与路线展示单元测试
package com.leapauto.app.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripModelsTest {

    private fun sampleTrip(startLocation: String?, endLocation: String?, distanceKm: Double = 43.0): TripRecord {
        return TripRecord(
            id = "test-1",
            vin = "LFZ63AZ55SH023503",
            startTimeEpochMs = 1700000000000L,
            endTimeEpochMs = 1700003600000L,
            startMileage = 10000.0,
            endMileage = 10000.0 + distanceKm,
            distanceKm = distanceKm,
            startSoc = 80.0,
            endSoc = 70.0,
            consumedKwh = 6.3,
            avgKwhPer100Km = 14.7,
            estimatedCostYuan = 2.71,
            startLocation = startLocation,
            endLocation = endLocation
        )
    }

    @Test
    fun `routeDisplayPair preserves both departure and destination even when locations are identical`() {
        val trip = sampleTrip("吾悦华府A区", "吾悦华府A区", distanceKm = 43.0)
        val route = trip.routeDisplayPair()
        assertEquals(Pair("吾悦华府A区", "吾悦华府A区"), route)
    }

    @Test
    fun `routeDisplayPair presents distinct start and end locations`() {
        val trip = sampleTrip("吾悦华府A区", "江汉路步行街", distanceKm = 25.0)
        val route = trip.routeDisplayPair()
        assertEquals(Pair("吾悦华府A区", "江汉路步行街"), route)
    }

    @Test
    fun `routeDisplayPair falls back gracefully when only start location is known`() {
        val trip = sampleTrip("吾悦华府A区", null, distanceKm = 10.0)
        val route = trip.routeDisplayPair()
        assertEquals(Pair("吾悦华府A区", "目的地"), route)
    }

    @Test
    fun `routeDisplayPair falls back gracefully when only end location is known`() {
        val trip = sampleTrip(null, "吾悦华府A区", distanceKm = 10.0)
        val route = trip.routeDisplayPair()
        assertEquals(Pair("起始地", "吾悦华府A区"), route)
    }

    @Test
    fun `routeDisplayPair returns null when both locations are null or blank`() {
        val tripNull = sampleTrip(null, null, distanceKm = 5.0)
        assertNull(tripNull.routeDisplayPair())

        val tripBlank = sampleTrip("   ", "", distanceKm = 5.0)
        assertNull(tripBlank.routeDisplayPair())
    }
}
