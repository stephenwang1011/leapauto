package com.leapauto.app

/**
 * Keeps a driving widget refresh loop alive through transient failures.
 *
 * A rapid request is created only after a confirmed driving status. If that
 * request fails before a new snapshot can be saved, it must retry at the same
 * cadence instead of silently falling back to the parked 30-minute schedule.
 */
object WidgetSyncCadencePolicy {
    fun shouldContinueDrivingCadence(
        requestWasDriving: Boolean,
        lastKnownDriving: Boolean?
    ): Boolean = requestWasDriving || lastKnownDriving == true
}
