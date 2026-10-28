package com.leapauto.app

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class BoundedTaskLaneTest {
    @Test fun `resource work executes on its dedicated thread rather than submitting thread`() {
        val caller = Thread.currentThread()
        val observed = AtomicReference<Thread>()
        val finished = CountDownLatch(1)
        val lane = BoundedTaskLane(1, "test-resources")
        try {
            assertTrue(lane.submit("reset-custom-image") {
                observed.set(Thread.currentThread())
                finished.countDown()
            })
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertNotSame(caller, observed.get())
            assertEquals("test-resources", observed.get().name)
        } finally {
            lane.close()
        }
    }

    @Test fun `one flight absorbs repeated ticks and stale completion cannot finish next flight`() {
        val gate = TelemetryRefreshGate()
        val first = requireNotNull(gate.begin(0))
        repeat(100) { assertNull(gate.begin(5_000)) }
        gate.cancel()
        val second = requireNotNull(gate.begin(5_000))
        assertFalse(gate.isCurrent(first))
        gate.finish(first)
        assertNull(gate.begin(7_000))
        gate.finish(second)
        assertNull(gate.begin(5_999))
        assertNotNull(gate.begin(6_000))
    }

    @Test fun `lane deduplicates and rejects overflow without running on caller thread`() {
        val lane = BoundedTaskLane(1, "test-resources")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            assertTrue(lane.submit("running") { started.countDown(); release.await() })
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertFalse(lane.submit("running") { fail("duplicate executed") })
            assertTrue(lane.submit("queued") { })
            assertFalse(lane.submit("overflow") { fail("overflow executed") })
            lane.cancelAll()
            val completed = CountDownLatch(1)
            assertTrue(lane.submit("queued") { completed.countDown() })
            assertTrue(completed.await(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            lane.close()
        }
        assertFalse(lane.submit("closed") { fail("closed lane executed") })
    }
}
