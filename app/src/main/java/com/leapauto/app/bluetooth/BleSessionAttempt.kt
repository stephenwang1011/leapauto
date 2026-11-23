package com.leapauto.app.bluetooth

/** Rejects duplicate derivations and results belonging to a disconnected physical link. */
internal class BleSessionAttempt {
    private var revision = 0L
    private var pending: Long? = null

    fun begin(): Long? {
        if (pending != null) return null
        return revision.also { pending = it }
    }

    fun accepts(ticket: Long): Boolean = pending == ticket && revision == ticket

    fun complete(ticket: Long): Boolean {
        if (!accepts(ticket)) return false
        pending = null
        return true
    }

    fun invalidate() {
        revision++
        pending = null
    }
}

internal object BleManagedConnectionPolicy {
    fun retainOnDisconnect(phase: BleConnectionPhase, writePending: Boolean): Boolean =
        !writePending && phase in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.CONNECTING, BleConnectionPhase.READY)
}
