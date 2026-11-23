package com.leapauto.app.bluetooth

import org.junit.Assert.*
import org.junit.Test

class BleSessionAttemptTest {
    @Test
    fun `retain only quiescent managed link and discard link with unfinished callbacks`() {
        assertTrue(BleManagedConnectionPolicy.retainOnDisconnect(BleConnectionPhase.READY, false))
        assertTrue(BleManagedConnectionPolicy.retainOnDisconnect(BleConnectionPhase.IDLE, false))
        assertTrue(BleManagedConnectionPolicy.retainOnDisconnect(BleConnectionPhase.CONNECTING, false))
        for (phase in listOf(BleConnectionPhase.DISCOVERING, BleConnectionPhase.SUBSCRIBING,
            BleConnectionPhase.AUTHENTICATING, BleConnectionPhase.CONFIGURING, BleConnectionPhase.SENDING)) {
            assertFalse(BleManagedConnectionPolicy.retainOnDisconnect(phase, false))
        }
        assertFalse(BleManagedConnectionPolicy.retainOnDisconnect(BleConnectionPhase.READY, true))
    }
    @Test
    fun `duplicate subscription cannot start two derivations`() {
        val attempts = BleSessionAttempt()
        val ticket = requireNotNull(attempts.begin())
        assertNull(attempts.begin())
        assertTrue(attempts.complete(ticket))
        assertFalse(attempts.complete(ticket))
    }

    @Test
    fun `disconnect during derivation rejects old result on same retained gatt`() {
        val attempts = BleSessionAttempt()
        val old = requireNotNull(attempts.begin())
        attempts.invalidate()
        val next = requireNotNull(attempts.begin())
        assertFalse(attempts.accepts(old))
        assertFalse(attempts.complete(old))
        assertTrue(attempts.accepts(next))
        assertTrue(attempts.complete(next))
    }

    @Test
    fun `logout and relink cancel pending authentication`() {
        val attempts = BleSessionAttempt()
        val old = requireNotNull(attempts.begin())
        attempts.invalidate()
        attempts.invalidate()
        assertFalse(attempts.accepts(old))
        assertNotNull(attempts.begin())
    }
}
