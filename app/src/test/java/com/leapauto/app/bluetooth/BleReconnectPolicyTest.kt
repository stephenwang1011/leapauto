package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class BleReconnectPolicyTest {
    @Test
    fun `reconnection backs off and stays capped during extended absence`() {
        assertEquals(listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L, 30_000L),
            (0..5).map(BleReconnectPolicy::delayMillis))
        assertEquals(30_000L, BleReconnectPolicy.delayMillis(Int.MAX_VALUE))
        assertEquals(2_000L, BleReconnectPolicy.delayMillis(-1))
    }
}
