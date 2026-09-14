package com.leapauto.app.bluetooth

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.leapauto.app.Session
import com.leapauto.app.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A single process owner prevents the activity and foreground service from opening competing GATTs. */
class BleKeyRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val sessions = SessionStore(appContext)
    private val keys = BleManagedKeyStore(appContext)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectionValue = MutableStateFlow(BleConnectionState())
    private val keyValue = MutableStateFlow<BleManagedKey?>(null)
    private val runningValue = MutableStateFlow(false)
    val connection = connectionValue.asStateFlow()
    val managedKey = keyValue.asStateFlow()
    val backgroundRunning = runningValue.asStateFlow()
    val controller = BluetoothKeyController(appContext, scope, ::onConnection)
    private var identity: BleSessionIdentity? = null
    private var candidate: Pair<BleNearbyDevice, BleKeyCertificate>? = null
    private var retry: Runnable? = null
    private var retryAttempt = 0
    private var syncing = false
    private var stopping = false
    private var foreground = false
    private var restoreAttempted = false
    private var blockedGeneration: Long? = null

    fun attachSession(session: Session) {
        val account = session.oldAuth?.accountId.orEmpty()
        val next = if (account.isNotBlank() && session.selectedVin.isNotBlank())
            BleSessionIdentity(account, session.selectedVin, session.generation, session.deviceId) else null
        if (identity == next) return
        if (identity != null || runningValue.value) stopBackground() else controller.disconnect()
        identity = next
        candidate = null
        keyValue.value = next?.takeIf { blockedGeneration != it.generation }?.let {
            keys.load(it.accountId, it.vin, it.generation)
        }
        if (next?.generation != blockedGeneration) blockedGeneration = null
        restoreAttempted = false
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value && !runningValue.value) controller.disconnect()
    }

    fun connectManually(device: BleNearbyDevice, certificate: BleKeyCertificate, session: Session) {
        attachSession(session)
        check(foreground && validSession() && !runningValue.value && keyValue.value?.desired?.enabled != true)
        val normalized = device.copy(protocolMinor = device.protocolMinor ?: BleKeyProtocol.DEFAULT_PROTOCOL_MINOR)
        candidate = normalized to certificate
        val current = requireNotNull(identity)
        controller.connect(normalized, certificate, current.accountId, current.deviceId,
            BlePassiveConfiguration.MANUAL, trustedBinding = false) { identity == current && validSession() }
    }

    fun applyConfiguration(configuration: BlePassiveConfiguration) {
        check(foreground && validSession())
        check(!connectionValue.value.isBusy) { "请等待当前蓝牙操作结束" }
        check(!sessions.loadOpPassword().isNullOrBlank())
        val bound = requireNotNull(keyValue.value) { "请先连接并认证车辆" }
        val certificate = sessions.loadBluetoothKeyCertificate(bound.accountId, bound.vin)
        check(BleAccessPolicy.canApplyConfiguration(connectionValue.value, bound, certificate, configuration)) {
            "请检查钥匙绑定、连接状态和车辆能力"
        }
        val requested = bound.request(configuration, requireNotNull(identity).generation)
        check(keys.save(requested)) { "无法保存钥匙设置" }
        keyValue.value = requested
        retryAttempt = 0
        startBackground()
    }

    fun startBackground() {
        check(foreground && validSession())
        check(controller.hasPermissions()) { "请先开启附近设备权限" }
        check(!sessions.loadOpPassword().isNullOrBlank()) { "请先设置操控密码" }
        if (keyValue.value?.needsBackground != true) return
        if (runningValue.value) {
            if (!controller.state.isBusy) reconnectManaged()
            return
        }
        appContext.startForegroundService(Intent(appContext, BleKeyService::class.java))
    }

    fun restoreBackground() {
        if (restoreAttempted || !foreground) return
        restoreAttempted = true
        if (!runningValue.value && keyValue.value?.needsBackground == true) startBackground()
    }

    internal fun serviceStarted(): Boolean {
        if (identity == null) attachSession(sessions.load())
        if (!validSession() || keyValue.value?.needsBackground != true || !controller.hasPermissions() ||
            sessions.loadOpPassword().isNullOrBlank()) return false
        if (runningValue.value) return true
        runningValue.value = true
        retryAttempt = 0
        reconnectManaged()
        return runningValue.value
    }

    internal fun serviceStopped() {
        val wasRunning = runningValue.value
        runningValue.value = false
        cancelRetry()
        syncing = false
        if (wasRunning) controller.disconnect()
    }

    fun stopBackground() {
        stopping = true
        runningValue.value = false
        cancelRetry()
        syncing = false
        candidate = null
        controller.disconnect()
        appContext.stopService(Intent(appContext, BleKeyService::class.java))
        stopping = false
    }

    fun endSession() {
        stopBackground()
        val suspended = keys.suspendAll()
        if (!suspended) blockedGeneration = identity?.generation
        identity = null
        keyValue.value = null
    }

    fun pauseManualConnection() {
        candidate = null
        if (!runningValue.value) controller.disconnect()
    }

    private fun validSession(): Boolean {
        val current = sessions.load()
        return identity?.matches(current.oldAuth?.accountId, current.selectedVin, current.generation, current.deviceId) == true
    }

    private fun reconnectManaged() {
        if (!runningValue.value) return
        if (!validSession() || !controller.hasPermissions() || sessions.loadOpPassword().isNullOrBlank()) {
            stopBackground()
            return
        }
        val bound = keyValue.value ?: return stopBackground()
        val certificate = sessions.loadBluetoothKeyCertificate(bound.accountId, bound.vin)
        if (!bound.needsBackground || certificate == null || !bound.matchesCertificate(certificate)) {
            stopBackground()
            return
        }
        cancelRetry()
        syncing = false
        // Disconnect notifications must not schedule a second concurrent reconnect.
        stopping = true
        controller.disconnect()
        stopping = false
        val current = requireNotNull(identity)
        controller.connect(bound.device.copy(protocolMinorSource = BleProtocolMinorSource.SAVED), certificate, bound.accountId, current.deviceId,
            bound.desired, trustedBinding = true) { identity == current && validSession() }
    }

    private fun onConnection(state: BleConnectionState) {
        connectionValue.value = state
        if (state.phase == BleConnectionPhase.READY) {
            enrollCandidate()
            if (runningValue.value && !syncing && controller.canConfigure) synchronizeConfiguration()
        } else if (state.phase == BleConnectionPhase.IDLE || state.phase == BleConnectionPhase.FAILED) {
            if (state.phase == BleConnectionPhase.FAILED) candidate = null
            syncing = false
            if (runningValue.value && !stopping) scheduleReconnect()
        }
    }

    private fun enrollCandidate() {
        val (device, certificate) = candidate ?: return
        candidate = null
        val current = identity ?: return
        if (!validSession() || certificate.vin != current.vin) return
        val existing = keyValue.value
        val fingerprint = BleKeyProtocol.certificateFingerprint(certificate)
        val bound = when {
            existing == null -> BleManagedKey(current.accountId, current.vin, device, fingerprint)
            existing.matchesCertificate(certificate) && existing.device.address == device.address -> existing.copy(device = device)
            else -> existing.copy(device = device, certificateFingerprint = fingerprint).suspended()
        }
        if (keys.save(bound)) keyValue.value = bound
    }

    private fun synchronizeConfiguration() {
        val bound = keyValue.value ?: return
        val current = identity ?: return
        if (!validSession()) return stopBackground()
        syncing = true
        controller.configure(bound.desired, bound.device.protocolMinor ?: BleKeyProtocol.DEFAULT_PROTOCOL_MINOR) {
            syncing = false
            if (identity != current || !validSession() || !runningValue.value) return@configure
            val latest = keyValue.value ?: return@configure
            if (latest.revision != bound.revision || latest.desired != bound.desired) {
                reconnectManaged()
                return@configure
            }
            val confirmed = latest.confirmed(bound.revision, bound.desired)
            if (!keys.save(confirmed)) {
                stopBackground()
                return@configure
            }
            keyValue.value = confirmed
            retryAttempt = 0
            if (!confirmed.needsBackground) stopBackground()
        }
    }

    private fun scheduleReconnect() {
        if (retry != null) return
        val delay = BleReconnectPolicy.delayMillis(retryAttempt++)
        retry = Runnable {
            retry = null
            reconnectManaged()
        }.also { handler.postDelayed(it, delay) }
    }

    private fun cancelRetry() {
        retry?.let(handler::removeCallbacks)
        retry = null
    }

    companion object {
        @Volatile private var instance: BleKeyRuntime? = null
        fun get(context: Context): BleKeyRuntime = instance ?: synchronized(this) {
            instance ?: BleKeyRuntime(context).also { instance = it }
        }
    }
}

object BleReconnectPolicy {
    fun delayMillis(attempt: Int): Long = when (attempt.coerceAtLeast(0)) {
        0 -> 2_000L
        1 -> 5_000L
        2 -> 10_000L
        3 -> 20_000L
        else -> 30_000L
    }
}
