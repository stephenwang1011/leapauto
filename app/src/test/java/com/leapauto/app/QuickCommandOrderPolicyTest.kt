package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCommandOrderPolicyTest {
    @Test
    fun legacySunshadeActionsBecomeOneGroupAtTheirFirstSavedPosition() {
        assertEquals(
            listOf("unlock", "sunshadeGroup", "lock", "horn"),
            QuickCommandOrderPolicy.migrateSunshadeGroup(
                listOf("unlock", "sunshadeOpen", "lock", "sunshadeClose", "horn")
            )
        )
        assertEquals(
            listOf("lock", "sunshadeGroup", "horn"),
            QuickCommandOrderPolicy.migrateSunshadeGroup(
                listOf("lock", "sunshadeGroup", "sunshadeOpen", "horn")
            )
        )
        assertEquals(null, QuickCommandOrderPolicy.migrateSunshadeGroup(null))
    }

    @Test
    fun savedOrderIsFilteredAndNewCapabilitiesAreAppended() {
        assertEquals(
            listOf("lock", "sentry", "unlock", "windowOpen"),
            QuickCommandOrderPolicy.resolve(
                saved = listOf("lock", "unknown", "lock", "sentry"),
                available = listOf("unlock", "lock", "sentry", "windowOpen")
            )
        )
    }

    @Test
    fun moveKeepsAllCommandIdsWhenMovingAcrossMultiplePositions() {
        assertEquals(
            listOf("unlock", "sentry", "lock"),
            QuickCommandOrderPolicy.move(listOf("unlock", "lock", "sentry"), 2, 1)
        )
        assertEquals(
            listOf("lock", "sentry", "unlock", "windowOpen"),
            QuickCommandOrderPolicy.move(
                listOf("unlock", "lock", "sentry", "windowOpen"),
                from = 0,
                to = 2
            )
        )
        assertEquals(
            listOf("unlock", "lock", "sentry"),
            QuickCommandOrderPolicy.move(listOf("unlock", "lock", "sentry"), -1, 1)
        )
    }

    @Test
    fun targetIndexUsesAccumulatedOffsetAndRowExtent() {
        assertEquals(1, QuickCommandOrderPolicy.targetIndex(1, 20f, 54f, 4))
        assertEquals(2, QuickCommandOrderPolicy.targetIndex(1, 55f, 54f, 4))
        assertEquals(3, QuickCommandOrderPolicy.targetIndex(1, 54f * 2.6f, 54f, 4))
        assertEquals(0, QuickCommandOrderPolicy.targetIndex(2, -54f * 4f, 54f, 4))
        assertEquals(3, QuickCommandOrderPolicy.targetIndex(1, 54f * 4f, 54f, 4))
    }

    @Test
    fun targetIndexSafelyHandlesInvalidExtentAndChangingListBounds() {
        assertEquals(2, QuickCommandOrderPolicy.targetIndex(2, 100f, 0f, 5))
        assertEquals(0, QuickCommandOrderPolicy.targetIndex(8, 100f, 54f, 1))
        assertEquals(0, QuickCommandOrderPolicy.targetIndex(-2, -100f, 54f, 0))
        assertEquals(1, QuickCommandOrderPolicy.targetIndex(1, Float.NaN, 54f, 3))
    }

    @Test
    fun quickCommandExecutionPolicyMatchesActiveCommands() {
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("unlock", "unlock"))
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("lock", "lock"))
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("trunk", "trunkOpen"))
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("trunk", "trunkClose"))
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("windowGroup", "windowVent"))
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("windowGroup", "windowOpen"))
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("sunshadeGroup", "sunshadeOpen"))
        assertTrue(QuickCommandExecutionPolicy.isCommandInProgress("sentry", "sentryOn"))

        assertFalse(QuickCommandExecutionPolicy.isCommandInProgress("unlock", "lock"))
        assertFalse(QuickCommandExecutionPolicy.isCommandInProgress("trunk", "unlock"))
        assertFalse(QuickCommandExecutionPolicy.isCommandInProgress("unlock", null))
        assertFalse(QuickCommandExecutionPolicy.isCommandInProgress("unlock", ""))
    }
}
