package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RearSeatComfortPolicyTest {

    @Test
    fun `supportsRearSeats returns true for C16 and 6-seater variants`() {
        assertTrue(RearSeatComfortPolicy.supportsRearSeats("C16"))
        assertTrue(RearSeatComfortPolicy.supportsRearSeats("零跑 C16"))
        assertTrue(RearSeatComfortPolicy.supportsRearSeats("C16 增程"))
        assertTrue(RearSeatComfortPolicy.supportsRearSeats("C16 630激光雷达智尊版 6座"))
        assertTrue(RearSeatComfortPolicy.supportsRearSeats("c16"))
        assertTrue(RearSeatComfortPolicy.supportsRearSeats("豪华 6座 旗舰版"))
        assertTrue(RearSeatComfortPolicy.supportsRearSeats("MPV 7座"))
        assertTrue(RearSeatComfortPolicy.supportsRearSeats("6-seat luxury"))
    }

    @Test
    fun `supportsRearSeats returns false for 5-seater and compact models`() {
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("C11"))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("零跑 C11"))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("C11 增程 300智享版"))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("C10"))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("零跑 C10"))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("C01"))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("零跑 C01"))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("T03"))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("零跑 T03"))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("B10"))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats(null))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats(""))
        assertFalse(RearSeatComfortPolicy.supportsRearSeats("   "))
    }
}
