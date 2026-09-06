package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSyncCadencePolicyTest {

    @Test
    fun `driving task keeps the ten second cadence after a transient failure`() {
        assertTrue(
            WidgetSyncCadencePolicy.shouldContinueDrivingCadence(
                requestWasDriving = true,
                lastKnownDriving = null
            )
        )
        assertTrue(
            WidgetSyncCadencePolicy.shouldContinueDrivingCadence(
                requestWasDriving = true,
                lastKnownDriving = false
            )
        )
    }

    @Test
    fun `last known driving snapshot also keeps the ten second cadence`() {
        assertTrue(
            WidgetSyncCadencePolicy.shouldContinueDrivingCadence(
                requestWasDriving = false,
                lastKnownDriving = true
            )
        )
    }

    @Test
    fun `parked task remains on the parked cadence`() {
        assertFalse(
            WidgetSyncCadencePolicy.shouldContinueDrivingCadence(
                requestWasDriving = false,
                lastKnownDriving = false
            )
        )
    }
}
