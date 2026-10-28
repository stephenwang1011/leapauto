package com.leapauto.app

import org.junit.Assert.*
import org.junit.Test

class ChargingTelemetryOrderTest {
    @Test fun `late silent response cannot overwrite later full refresh or control readback`() {
        val order = ChargingTelemetryOrder()
        val silent = order.begin(1, 5)
        val full = order.begin(1, 5)
        val controlReadback = order.begin(1, 5)
        assertTrue(order.accept(full, 1, 5))
        assertFalse(order.accept(silent, 1, 5))
        assertTrue(order.accept(controlReadback, 1, 5))
        assertFalse(order.accept(full, 1, 5))
    }

    @Test fun `starting a later request does not discard an earlier success until later response applies`() {
        val order = ChargingTelemetryOrder()
        val a = order.begin(1, 0)
        val b = order.begin(1, 0)
        assertTrue(order.accept(a, 1, 0))
        assertTrue(order.accept(b, 1, 0))
        assertTrue("Equal watermark is not older", order.accept(b, 1, 0))
        assertFalse(order.accept(a, 1, 0))
    }

    @Test fun `new control revision rejects pre command request even before new telemetry arrives`() {
        val order = ChargingTelemetryOrder()
        val beforeCommand = order.begin(1, 4)
        assertFalse(order.accept(beforeCommand, 1, 5))
        val afterCommand = order.begin(1, 5)
        assertTrue(order.accept(afterCommand, 1, 5))
        assertFalse(order.accept(beforeCommand, 1, 5))
    }

    @Test fun `invalid context cannot advance watermark and starve current request`() {
        val order = ChargingTelemetryOrder()
        val current = order.begin(3, 7)
        val oldTaskStartedLate = order.begin(2, 6)
        assertFalse(order.accept(oldTaskStartedLate, 3, 7))
        assertTrue(order.accept(current, 3, 7))
        val nextVehicle = order.begin(4, 8)
        assertFalse(order.accept(current, 4, 8))
        assertTrue(order.accept(nextVehicle, 4, 8))
    }
}
