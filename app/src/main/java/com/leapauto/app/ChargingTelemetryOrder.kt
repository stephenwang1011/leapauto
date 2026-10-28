package com.leapauto.app

import java.util.concurrent.atomic.AtomicLong

internal data class ChargingTelemetryRequest(val sequence: Long, val epoch: Long, val controlRevision: Long)

/** Allocate immediately before HTTP; accept only on main, together with the field update. */
internal class ChargingTelemetryOrder {
    private val sequence = AtomicLong()
    private var appliedSequence = 0L

    fun begin(epoch: Long, controlRevision: Long): ChargingTelemetryRequest =
        ChargingTelemetryRequest(sequence.incrementAndGet(), epoch, controlRevision)

    fun accept(request: ChargingTelemetryRequest, epoch: Long, controlRevision: Long): Boolean {
        if (request.epoch != epoch || request.controlRevision != controlRevision ||
            request.sequence < appliedSequence) return false
        appliedSequence = request.sequence
        return true
    }

    /** A stale full refresh still supplies non-charging fields, after their existing protection rules. */
    fun mergeFullRefresh(current: VehicleStatus?, refreshed: VehicleStatus, accepted: Boolean): VehicleStatus? =
        if (accepted) refreshed else current?.let { ChargingControlPolicy.mergeTelemetry(refreshed, it) }
}
