package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledAcVisibilityPolicyTest {
    @Test
    fun `unknown capability keeps scheduled ac hidden`() {
        assertFalse(ScheduledAcVisibilityPolicy.shouldShow(ScheduledAcCapability.UNKNOWN))
    }

    @Test
    fun `confirmed supported capability shows scheduled ac`() {
        assertTrue(ScheduledAcVisibilityPolicy.shouldShow(ScheduledAcCapability.SUPPORTED))
    }
}
