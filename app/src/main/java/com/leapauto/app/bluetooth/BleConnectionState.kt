package com.leapauto.app.bluetooth

import java.security.MessageDigest

enum class BleConnectionPhase {
    IDLE, SCANNING, CONNECTING, DISCOVERING, SUBSCRIBING, AUTHENTICATING, CONFIGURING, READY, SENDING, FAILED
}

data class BleNearbyDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val protocolMinor: Int?,
    val protocolMinorSource: BleProtocolMinorSource = BleProtocolMinorSource.UNKNOWN
) {
    override fun toString(): String = "BleNearbyDevice(redacted)"
}

data class BleConnectionState(
    val phase: BleConnectionPhase = BleConnectionPhase.IDLE,
    val devices: List<BleNearbyDevice> = emptyList(),
    val deviceName: String = "",
    val message: String = "",
    val pendingAction: BleLockAction? = null,
    val confirmedAction: BleLockAction? = null,
    val diagnostics: List<BleDiagnosticEntry> = emptyList()
) {
    val canControl: Boolean get() = phase == BleConnectionPhase.READY
    val phaseLabel: String get() = when (phase) {
        BleConnectionPhase.IDLE -> "未连接"
        BleConnectionPhase.SCANNING -> "正在扫描"
        BleConnectionPhase.CONNECTING -> "正在连接"
        BleConnectionPhase.DISCOVERING -> "正在检查车辆服务"
        BleConnectionPhase.SUBSCRIBING -> "正在建立通信"
        BleConnectionPhase.AUTHENTICATING -> "正在认证"
        BleConnectionPhase.CONFIGURING -> "正在同步钥匙设置"
        BleConnectionPhase.READY -> "已认证"
        BleConnectionPhase.SENDING -> "等待车辆确认"
        BleConnectionPhase.FAILED -> "连接不可用"
    }
    val detailMessage: String? get() = message.takeIf { it.isNotBlank() && it.trim() != phaseLabel }
    fun rejectionMessage(resultCode: Int): String {
        val request = when (phase) {
            BleConnectionPhase.AUTHENTICATING -> "钥匙认证"
            BleConnectionPhase.CONFIGURING -> "钥匙设置同步"
            BleConnectionPhase.SENDING -> "车辆操作"
            else -> "蓝牙请求"
        }
        return "车辆拒绝$request（结果码 $resultCode）"
    }

    val isBusy: Boolean get() = phase in setOf(
        BleConnectionPhase.CONNECTING,
        BleConnectionPhase.DISCOVERING,
        BleConnectionPhase.SUBSCRIBING,
        BleConnectionPhase.AUTHENTICATING,
        BleConnectionPhase.CONFIGURING,
        BleConnectionPhase.SENDING
    )
}

data class BleSessionIdentity(val accountId: String, val vin: String, val generation: Long, val deviceId: String) {
    fun matches(accountId: String?, vin: String, generation: Long, deviceId: String): Boolean =
        this.accountId.isNotBlank() && this.vin.isNotBlank() && this.deviceId.isNotBlank() &&
            this.accountId == accountId && this.vin == vin && this.generation == generation && this.deviceId == deviceId

    override fun toString(): String = "BleSessionIdentity(redacted)"
}

enum class BleCommandOutcome { CONFIRMED, NOT_SENT, UNKNOWN }

/** One explicit command per connection; a transport callback never confirms vehicle movement. */
class BleCommandTracker {
    var pending: BleLockAction? = null
        private set
    var writeStarted: Boolean = false
        private set
    private var deadlineMs = 0L
    private var used = false

    fun begin(action: BleLockAction, nowMs: Long): Boolean {
        if (used) return false
        used = true
        pending = action
        writeStarted = false
        deadlineMs = nowMs + CONFIRMATION_TIMEOUT_MS
        return true
    }

    fun markWriteStarted() {
        if (pending != null) writeStarted = true
    }

    fun confirm(action: BleLockAction, nowMs: Long): BleCommandOutcome? {
        if (pending != action || !writeStarted || nowMs >= deadlineMs) return null
        clear()
        return BleCommandOutcome.CONFIRMED
    }

    fun end(): BleCommandOutcome? {
        if (pending == null) return null
        val result = if (writeStarted) BleCommandOutcome.UNKNOWN else BleCommandOutcome.NOT_SENT
        clear()
        return result
    }

    private fun clear() {
        pending = null
        writeStarted = false
        deadlineMs = 0L
    }

    companion object {
        const val CONFIRMATION_TIMEOUT_MS = 6_000L
    }
}

object BlePermissionPolicy {
    fun requiredPermissions(sdkInt: Int): List<String> = if (sdkInt >= 31) {
        listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
    } else {
        listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")
    }
}

object BleCredentialScope {
    fun storageKey(accountId: String, vin: String): String {
        require(accountId.isNotBlank() && vin.isNotBlank())
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$accountId\u0000$vin".toByteArray(Charsets.UTF_8))
        return "ble_certificate_" + digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
