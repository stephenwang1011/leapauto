package com.leapauto.app.bluetooth

enum class BleConfigurationReply { IGNORED, ACCEPTED, REJECTED }

/** Command 3 has no request identifier, so only one configuration is permitted per connection. */
class BleConfigurationTracker {
    var pending: BlePassiveConfiguration? = null
        private set
    private var used = false
    private var writeStarted = false
    private var acknowledged = false
    private var startedAtMs = 0L
    private var deadlineMs = 0L

    val canBegin: Boolean get() = !used

    fun begin(configuration: BlePassiveConfiguration, nowMs: Long): Boolean {
        if (used) return false
        require(nowMs in 0..(Long.MAX_VALUE - CONFIRMATION_TIMEOUT_MS)) { "Invalid BLE configuration clock" }
        used = true
        pending = configuration
        startedAtMs = nowMs
        deadlineMs = nowMs + CONFIRMATION_TIMEOUT_MS
        return true
    }

    fun markWriteStarted() {
        if (pending != null) writeStarted = true
    }

    fun receive(response: BleResponse.CommandResult, nowMs: Long): BleConfigurationReply {
        if (!eligible(nowMs) || acknowledged || response.identifier != "3") return BleConfigurationReply.IGNORED
        if (!response.confirmsConfiguration()) {
            end()
            return BleConfigurationReply.REJECTED
        }
        acknowledged = true
        return BleConfigurationReply.ACCEPTED
    }

    fun confirm(writesComplete: Boolean, nowMs: Long): BlePassiveConfiguration? {
        if (!writesComplete || !acknowledged || !eligible(nowMs)) return null
        val confirmed = pending
        end()
        return confirmed
    }

    fun end() {
        pending = null
        writeStarted = false
        acknowledged = false
        startedAtMs = 0L
        deadlineMs = 0L
    }

    private fun eligible(nowMs: Long): Boolean =
        pending != null && writeStarted && nowMs >= startedAtMs && nowMs < deadlineMs

    companion object {
        const val CONFIRMATION_TIMEOUT_MS = 8_000L
    }
}
