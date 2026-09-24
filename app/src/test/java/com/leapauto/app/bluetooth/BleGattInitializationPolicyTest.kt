package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class BleGattInitializationPolicyTest {
    @Test
    fun `normal connection negotiates MTU before discovering services`() {
        assertEquals(BleGattInitializationStep.REQUEST_MTU,
            BleGattInitializationPolicy.initialStep(skipMtuOnce = false))
        assertEquals(BleGattInitializationStep.WAIT_FOR_MTU,
            BleGattInitializationPolicy.afterMtuRequest(accepted = true))
    }

    @Test
    fun `rejected or previously missing MTU callback falls back to service discovery`() {
        assertEquals(BleGattInitializationStep.DISCOVER_SERVICES,
            BleGattInitializationPolicy.afterMtuRequest(accepted = false))
        assertEquals(BleGattInitializationStep.DISCOVER_SERVICES,
            BleGattInitializationPolicy.initialStep(skipMtuOnce = true))
        assertEquals(200, BleGattInitializationPolicy.requestedMtu)
        assertEquals(2_500L, BleGattInitializationPolicy.mtuCallbackTimeoutMs)
    }
}
