package com.leapauto.app

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class BackgroundSessionTest {
    private fun session() = Session("device", oldAuth = OldAuth("account", "old", "refresh", "3600", 0),
        newAuth = NewAuth("account", "access", "refresh", "key", 100), selectedVin = "vehicle", generation = 2)

    @Test fun `request changes cannot mutate live session and merge only credentials`() {
        val live = session()
        val snapshot = BackgroundSession(live, 1)
        snapshot.request.newAuth = requireNotNull(snapshot.request.newAuth).copy(accessToken = "renewed")
        snapshot.request.selectedNickname = "stale nickname"
        snapshot.request.route = RouteData("stale", "stale")
        live.selectedNickname = "current nickname"
        assertEquals("access", live.newAuth?.accessToken)
        assertTrue(snapshot.mergeCredentials(live))
        assertEquals("renewed", live.newAuth?.accessToken)
        assertEquals("current nickname", live.selectedNickname)
        assertNull(live.route)
    }

    @Test fun `logout switch away and back and changed account invalidate result`() {
        val live = session()
        val snapshot = BackgroundSession(live, 3)
        assertFalse(snapshot.matches(live, 4))
        assertFalse(snapshot.canMergeCredentials(Session("device", generation = 3)))
        live.selectedVin = "other"
        assertFalse(snapshot.canMergeCredentials(live))
        live.selectedVin = "vehicle"
        live.oldAuth = requireNotNull(live.oldAuth).copy(accountId = "other")
        assertFalse(snapshot.canMergeCredentials(live))
    }

    @Test fun `concurrent refreshes from same auth have only one CAS winner`() {
        val live = session()
        val snapshots = (1..2).map { index -> BackgroundSession(live, 0).also {
            it.request.newAuth = requireNotNull(it.request.newAuth).copy(accessToken = "refresh-$index")
        } }
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val winners = AtomicInteger()
        snapshots.forEach { snapshot -> thread {
            start.await()
            if (snapshot.mergeCredentials(live)) winners.incrementAndGet()
            done.countDown()
        } }
        start.countDown()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(1, winners.get())
    }

    @Test fun `new old auth or device binding rejects background writeback`() {
        val live = session()
        val snapshot = BackgroundSession(live, 0)
        live.oldAuth = requireNotNull(live.oldAuth).copy(token = "newer")
        assertFalse(snapshot.mergeCredentials(live))
        val next = BackgroundSession(live, 0)
        live.deviceId = "rebound"
        assertFalse(next.mergeCredentials(live))
    }
}
