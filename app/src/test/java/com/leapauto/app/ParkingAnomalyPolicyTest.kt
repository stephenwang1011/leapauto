package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ParkingAnomalyPolicyTest {

    @Test
    fun `post lock check waits five seconds`() {
        assertEquals(5_000L, ParkingAnomalyPolicy.POST_LOCK_CHECK_DELAY_MS)
    }

    @Test
    fun `only an accepted lock command is eligible`() {
        assertTrue(ParkingAnomalyPolicy.shouldCheck("lock", commandAccepted = true))
        assertTrue(ParkingAnomalyPolicy.shouldCheck("LOCK", commandAccepted = true))
        assertFalse(ParkingAnomalyPolicy.shouldCheck("lock", commandAccepted = false))
        assertFalse(ParkingAnomalyPolicy.shouldCheck("unlock", commandAccepted = true))
    }
}
