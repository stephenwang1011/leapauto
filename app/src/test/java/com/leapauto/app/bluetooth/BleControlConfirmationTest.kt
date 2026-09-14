package com.leapauto.app.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleControlConfirmationTest {
    private val identity = BleSessionIdentity("ACCOUNT-A", "VIN-A", 3L, "DEVICE-A")

    @Test
    fun `both bluetooth lock actions require the management page to remain visible`() {
        for (action in BleLockAction.entries) {
            val confirmation = BleControlConfirmation(action, identity, 8L)
            assertTrue(confirmation.isCurrent(identity, 8L, true, true, managementVisible = true))
            assertFalse(confirmation.isCurrent(identity, 8L, true, true, managementVisible = false))
        }
    }

    @Test
    fun `identity changes invalidate existing confirmation`() {
        val confirmation = BleControlConfirmation(BleLockAction.UNLOCK, identity, 8L)
        assertTrue(confirmation.isCurrent(identity, 8L, true, true, managementVisible = true))
        assertFalse(confirmation.isCurrent(identity.copy(accountId = "ACCOUNT-B"), 8L, true, true, managementVisible = true))
        assertFalse(confirmation.isCurrent(identity.copy(vin = "VIN-B"), 8L, true, true, managementVisible = true))
        assertFalse(confirmation.isCurrent(identity.copy(generation = 4L), 8L, true, true, managementVisible = true))
        assertFalse(confirmation.isCurrent(identity.copy(deviceId = "DEVICE-B"), 8L, true, true, managementVisible = true))
        assertFalse(confirmation.isCurrent(null, 8L, true, true, managementVisible = true))
    }

    @Test
    fun `background reconnect and loss of authentication require a fresh confirmation`() {
        val confirmation = BleControlConfirmation(BleLockAction.LOCK, identity, 8L)
        assertFalse(confirmation.isCurrent(identity, 9L, true, true, managementVisible = true))
        assertFalse(confirmation.isCurrent(identity, 8L, false, true, managementVisible = true))
        assertFalse(confirmation.isCurrent(identity, 8L, true, false, managementVisible = true))
    }
}
