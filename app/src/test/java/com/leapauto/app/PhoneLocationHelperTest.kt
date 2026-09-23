package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLocationHelperTest {

    @Test
    fun `format distance handles meters and kilometers correctly`() {
        assertEquals("0m", PhoneLocationHelper.formatDistance(0.0))
        assertEquals("350m", PhoneLocationHelper.formatDistance(350.0))
        assertEquals("999m", PhoneLocationHelper.formatDistance(999.0))
        assertEquals("1.0km", PhoneLocationHelper.formatDistance(1000.0))
        assertEquals("2.4km", PhoneLocationHelper.formatDistance(2410.0))
        assertEquals("15.6km", PhoneLocationHelper.formatDistance(15600.0))
        assertEquals("120km", PhoneLocationHelper.formatDistance(120000.0))
    }

    @Test
    fun `calculate distance meters returns positive distance for separated points`() {
        // Hangzhou West Lake to Hangzhou East Railway Station (~8-10 km)
        val distance = PhoneLocationHelper.calculateDistanceMeters(
            lat1 = 30.2458, lon1 = 120.1472,
            lat2 = 30.2905, lon2 = 120.2131
        )
        assertTrue(distance > 5000.0)
        assertTrue(distance < 15000.0)
    }
}
