package com.leapauto.app.bluetooth

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class BlePresencePolicyTest {
    @Test fun acceptsOnlyObservedBoundDeviceWhileEnabled() {
        assertTrue(acceptsPresence("AA", "aa", "Aa", false))
        assertFalse(acceptsPresence("AA", "AA", "BB", false))
        assertFalse(acceptsPresence("AA", "BB", "AA", false))
        assertFalse(acceptsPresence(null, "AA", "AA", false))
        assertFalse(acceptsPresence("AA", "AA", "AA", true))
    }
}
