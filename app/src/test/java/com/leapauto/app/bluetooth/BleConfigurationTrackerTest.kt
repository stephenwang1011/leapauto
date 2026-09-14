package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BleConfigurationTrackerTest {
    private val configuration = BlePassiveConfiguration(enabled = true, autoUnlock = true)
    private val startedAt = 12_345L
    private val deadline = startedAt + BleConfigurationTracker.CONFIRMATION_TIMEOUT_MS

    @Test
    fun connectionAllowsOnlyOneConfigurationEvenAfterConfirmation() {
        val tracker = BleConfigurationTracker()
        assertTrue(tracker.canBegin)
        assertTrue(tracker.begin(configuration, startedAt))
        assertFalse(tracker.canBegin)
        assertFalse(tracker.begin(BlePassiveConfiguration(), startedAt + 1))
        assertEquals(configuration, tracker.pending)
        tracker.markWriteStarted()
        assertEquals(BleConfigurationReply.ACCEPTED, tracker.receive(reply("3", "00"), startedAt + 2))
        assertEquals(configuration, tracker.confirm(true, startedAt + 3))
        assertNull(tracker.pending)
        assertNull(tracker.confirm(true, startedAt + 4))
        assertEquals(BleConfigurationReply.IGNORED, tracker.receive(reply("3", "00"), startedAt + 4))
        assertFalse(tracker.begin(configuration, startedAt + 5))
    }

    @Test
    fun successfulReplyCanPrecedeLastWriteCallbackButCannotConfirmUntilWritingFinishes() {
        val tracker = started()
        assertEquals(BleConfigurationReply.ACCEPTED, tracker.receive(reply("3", "0"), startedAt + 1))
        assertNull(tracker.confirm(false, startedAt + 2))
        assertEquals(BleConfigurationReply.IGNORED, tracker.receive(reply("3", "0"), startedAt + 3))
        assertEquals(configuration, tracker.confirm(true, startedAt + 4))
    }

    @Test
    fun completedWritesWithoutAReplyCannotConfirmConfiguration() {
        val tracker = started()
        assertNull(tracker.confirm(true, startedAt + 1))
        assertEquals(configuration, tracker.pending)
    }

    @Test
    fun repliesBeforeBeginOrBeforeFirstWriteAreDiscarded() {
        val tracker = BleConfigurationTracker()
        tracker.markWriteStarted()
        assertEquals(BleConfigurationReply.IGNORED, tracker.receive(reply("3", "00"), startedAt))
        assertTrue(tracker.begin(configuration, startedAt))
        assertEquals(BleConfigurationReply.IGNORED, tracker.receive(reply("3", "00"), startedAt + 1))
        assertEquals(BleConfigurationReply.IGNORED, tracker.receive(reply("3", "1"), startedAt + 1))
        tracker.markWriteStarted()
        assertNull(tracker.confirm(true, startedAt + 2))
        assertEquals(BleConfigurationReply.ACCEPTED, tracker.receive(reply("3", "00"), startedAt + 3))
        assertEquals(configuration, tracker.confirm(true, startedAt + 4))
    }

    @Test
    fun wrongCommandIdentifiersCannotConsumeTheExpectedReply() {
        val tracker = started()
        for (identifier in listOf("1", "2", "03", "3 ", " 3", "")) {
            assertEquals(BleConfigurationReply.IGNORED, tracker.receive(reply(identifier, "0"), startedAt + 1))
        }
        assertNull(tracker.confirm(true, startedAt + 2))
        assertEquals(BleConfigurationReply.ACCEPTED, tracker.receive(reply("3", "00"), startedAt + 3))
        assertEquals(configuration, tracker.confirm(true, startedAt + 4))
    }

    @Test
    fun exactFailureResultsEndConfigurationAndCannotBeReplacedByLaterSuccess() {
        for (result in listOf(null, "", "1", "000", "0 ", " 00", "+0", "0.0")) {
            val tracker = started()
            assertEquals(BleConfigurationReply.REJECTED, tracker.receive(reply("3", result), startedAt + 1))
            assertNull(tracker.pending)
            assertEquals(BleConfigurationReply.IGNORED, tracker.receive(reply("3", "0"), startedAt + 2))
            assertNull(tracker.confirm(true, startedAt + 3))
            assertFalse(tracker.canBegin)
        }
    }

    @Test
    fun deadlineIsExclusiveForBothReplyArrivalAndFinalWriteCompletion() {
        for (arrival in listOf(deadline, deadline + 1, startedAt - 1)) {
            val tracker = started()
            assertEquals(BleConfigurationReply.IGNORED, tracker.receive(reply("3", "0"), arrival))
            assertNull(tracker.confirm(true, arrival))
        }
        for (completion in listOf(deadline, deadline + 1, startedAt - 1)) {
            val tracker = started()
            assertEquals(BleConfigurationReply.ACCEPTED, tracker.receive(reply("3", "0"), startedAt + 1))
            assertNull(tracker.confirm(true, completion))
        }
        val tracker = started()
        assertEquals(BleConfigurationReply.ACCEPTED, tracker.receive(reply("3", "00"), deadline - 1))
        assertEquals(configuration, tracker.confirm(true, deadline - 1))
    }

    @Test
    fun disconnectDiscardsPendingReplyAndDoesNotReuseTheConnection() {
        val tracker = started()
        tracker.receive(reply("3", "0"), startedAt + 1)
        tracker.end()
        assertNull(tracker.confirm(true, startedAt + 2))
        assertNull(tracker.pending)
        assertFalse(tracker.begin(configuration, startedAt + 3))
        assertTrue(BleConfigurationTracker().begin(configuration, startedAt + 3))
    }

    @Test
    fun invalidClockCannotOverflowTheDeadlineOrConsumeTheConnection() {
        val tracker = BleConfigurationTracker()
        for (now in listOf(-1L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { tracker.begin(configuration, now) }
            assertTrue(tracker.canBegin)
        }
        assertTrue(tracker.begin(configuration, 0))
    }

    private fun started(): BleConfigurationTracker = BleConfigurationTracker().apply {
        check(begin(configuration, startedAt))
        markWriteStarted()
    }

    private fun reply(identifier: String, result: String?): BleResponse.CommandResult =
        BleResponse.CommandResult(identifier, result)
}
