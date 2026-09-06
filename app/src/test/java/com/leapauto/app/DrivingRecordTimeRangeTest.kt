package com.leapauto.app

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingRecordTimeRangeTest {
    @Test
    fun `previous week is monday through sunday in milliseconds`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-08-18T04:00:00Z").toEpochMilli()
        val range = DrivingRecordTimeRange.previousWeek(now, zone)

        assertEquals(Instant.parse("2026-08-09T16:00:00Z").epochSecond, range.beginTime)
        assertEquals(Instant.parse("2026-08-16T15:59:59Z").epochSecond, range.endTime)
        assertTrue(range.beginTime < range.endTime)
    }

    @Test
    fun `only last week endpoint receives date parameters`() {
        assertTrue(LeapmotorApi.isLastWeekEnergyEndpoint("/getLastweekEC"))
        assertTrue(LeapmotorApi.isLastWeekEnergyEndpoint("getLastweekEC"))
        assertTrue(LeapmotorApi.isLastNWeeksEnergyEndpoint("/getLastNweeks100kmECAndRank"))
        assertFalse(LeapmotorApi.isLastNWeeksEnergyEndpoint("/getLastweekEC"))
        assertEquals(false, LeapmotorApi.isLastWeekEnergyEndpoint("/getLastNweeks100kmECAndRank"))
    }

    @Test
    fun `purchase to today uses purchase day start and current second`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val purchase = Instant.parse("2025-01-15T08:30:00Z").toEpochMilli()
        val now = Instant.parse("2026-08-19T04:05:06Z").toEpochMilli()
        val range = DrivingRecordTimeRange.purchaseToToday(purchase, now, zone)

        assertEquals(Instant.parse("2025-01-14T16:00:00Z").epochSecond, range.beginTime)
        assertEquals(Instant.parse("2026-08-19T04:05:06Z").epochSecond, range.endTime)
    }

    @Test
    fun `purchase to today falls back to today's local start without a purchase timestamp`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-08-19T04:05:06Z").toEpochMilli()
        val range = DrivingRecordTimeRange.purchaseToToday(null, now, zone)

        assertEquals(Instant.parse("2026-08-18T16:00:00Z").epochSecond, range.beginTime)
        assertEquals(now / 1000L, range.endTime)
    }

    @Test
    fun `detail endpoint uses begin and end seconds instead of timespan`() {
        val range = PurchaseToTodayTimestamps(1_700_000_000L, 1_700_000_600L)
        val parameters = LeapmotorApi.mileageEnergyDetailBusinessParameters("VIN-TEST", range)
        assertEquals(setOf("begintime", "endtime", "vin"), parameters.keys)
        assertEquals("1700000000", parameters["begintime"])
        assertEquals("1700000600", parameters["endtime"])
        assertEquals("VIN-TEST", parameters["vin"])
        assertFalse(parameters.containsKey("timespan"))
        assertTrue(LeapmotorApi.isMileageEnergyDetailEndpoint("/mileage/energy/detail"))
    }
}
