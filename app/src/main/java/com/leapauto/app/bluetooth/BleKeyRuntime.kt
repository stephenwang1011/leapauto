package com.leapauto.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
    private val reconnectCredentials = BleReconnectCredentialStore(appContext)
    private val profiles = BleVehicleProfileStore(appContext)
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
    private var currentCarType: String? = null
    private var candidate: Pair<BleNearbyDevice, BleKeyCertificate>? = null
    private var retry: Runnable? = null
    private var retryAttempt = 0
    private var probeCallback: ScanCallback? = null
    private var consecutiveMissCount = 0
    private var syncing = false
    private var stopping = false
    private var foreground = false
    private var restoreAttempted = false
    private var blockedGeneration: Long? = null
    private var inCarMediaActive = false

    fun setInCarMediaActive(active: Boolean) {
        inCarMediaActive = active
        if (active && connectionValue.value.phase == BleConnectionPhase.IDLE) {
            controller.updateStateMessage("人在车内 · 车载蓝牙静默待命中")
        }
    }

    fun attachSession(session: Session) {
        val account = session.oldAuth?.accountId.orEmpty()
        currentCarType = session.selectedCarType
        val next = if (account.isNotBlank() && session.selectedVin.isNotBlank())
            BleSessionIdentity(account, session.selectedVin, session.generation, session.deviceId) else null
        if (identity == next) return
        if (identity != null || runningValue.value) stopBackground() else controller.disconnect()
        identity = next
        candidate = null
        keyValue.value = next?.takeIf { blockedGeneration != it.generation }?.let {
            keys.load(it.accountId, it.vin, it.generation, it.deviceId)
        }
        controller.setVehicleMetadata(next?.let { BleVehicleProfile.freshMetadata(profiles.load(it).metadata, System.currentTimeMillis()) })
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
        cancelRetry()
        cancelProbe()
        consecutiveMissCount = 0
        val current = requireNotNull(identity)
        val normalized = BleProtocolSelection.forManualConnection(
            device, controller.state.devices, keyValue.value, current, certificate, currentCarType
        )
        val profile = profiles.load(current)
        val effectiveCalib = profile.effectiveCalibration(currentCarType)
        recordTarget(normalized, profile)
        candidate = normalized to certificate
        val credentialScope = reconnectScope(current, certificate)
        controller.connect(normalized, certificate, current.accountId, current.deviceId,
            BlePassiveConfiguration.MANUAL.copy(calibration = effectiveCalib), trustedBinding = false,
            reconnectCredential = reconnectCredentials.load(credentialScope),
            onReconnectCredential = reconnectCredentialHandler(current, credentialScope)
        ) { identity == current && validSession() }
    }

    fun applyConfiguration(configuration: BlePassiveConfiguration) {
        check(foreground && validSession())
        check(!connectionValue.value.isBusy) { "请等待当前蓝牙操作结束" }
        check(!sessions.loadOpPassword().isNullOrBlank())
        check(controller.hasPermissions()) { "请先开启附近设备权限" }
        val bound = requireNotNull(keyValue.value) { "请先连接并认证车辆" }
        val certificate = sessions.loadBluetoothKeyCertificate(bound.accountId, bound.vin)
        check(BleAccessPolicy.canApplyConfiguration(connectionValue.value, bound, certificate, configuration)) {
            "请检查钥匙绑定、连接状态和车辆能力"
        }
        val requested = bound.request(configuration, requireNotNull(identity).generation)
        check(keys.save(requested)) { "无法保存钥匙设置" }
        keyValue.value = requested
        retryAttempt = 0
        if (connectionValue.value.phase == BleConnectionPhase.READY && controller.canConfigure && !syncing) {
            synchronizeConfiguration()
        } else {
            // The saved request remains pending if Android refuses to start the service.
            runCatching { startBackground() }.onFailure {
                controller.recordDiagnostic(BleDiagnosticEvent.CONFIGURATION_BACKGROUND_PAUSED)
            }
        }
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
        val credentialsCleared = reconnectCredentials.clearAll()
        if (!suspended) blockedGeneration = identity?.generation
        if (!credentialsCleared) blockedGeneration = identity?.generation
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
        // 关键保护：若当前连接已经处于就绪待命 (READY) 状态，严禁自杀重连掐断已活跃的连接
        if (controller.state.phase == BleConnectionPhase.READY || controller.state.phase == BleConnectionPhase.CONFIGURING) {
            return
        }
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
        recordTarget(bound.device, profiles.load(current))
        val credentialScope = reconnectScope(current, certificate)
        val isLeap3 = currentCarType.orEmpty().uppercase().let { it.contains("C16") || it.contains("C10") }
        val targetDevice = if (isLeap3 && (bound.device.protocolMinor ?: 0) < BleKeyProtocol.LEAP3_PROTOCOL_MINOR) {
            bound.device.copy(protocolMinor = BleKeyProtocol.LEAP3_PROTOCOL_MINOR, protocolMinorSource = BleProtocolMinorSource.SAVED)
        } else {
            bound.device.copy(protocolMinorSource = BleProtocolMinorSource.SAVED)
        }
        val targetDesired = if (isLeap3) {
            val calib = if (bound.desired.calibration == BleCalibration.DEFAULT || bound.desired.calibration.distanceCalibration == 56) {
                BleCalibration.C16_DEFAULT
            } else {
                bound.desired.calibration
            }
            bound.desired.copy(calibration = calib, buttonEnabled = true)
        } else {
            bound.desired
        }
        // 见车才连：先通过低功耗单次定向探测（3秒）确认车辆在附近，杜绝在楼上/远距离时无脑强连耗电与超时报错
        probeTargetAndConnect(bound, targetDevice, certificate, targetDesired, credentialScope, current)
    }

    @SuppressLint("MissingPermission")
    private fun probeTargetAndConnect(
        bound: BleManagedKey,
        targetDevice: BleNearbyDevice,
        certificate: BleKeyCertificate,
        targetDesired: BlePassiveConfiguration,
        credentialScope: BleReconnectCredentialScope,
        current: BleSessionIdentity
    ) {
        val scanner = controller.adapter?.bluetoothLeScanner
        if (scanner == null || !controller.hasPermissions() || controller.adapter?.isEnabled != true) {
            connectDirect(targetDevice, certificate, bound.accountId, targetDesired, credentialScope, current)
            return
        }

        val scanMode = if (consecutiveMissCount <= 2) {
            ScanSettings.SCAN_MODE_BALANCED // 黄金恢复期 (刚断开的前2次)：采用平衡扫描模式，确保 100% 秒级捕获车机广播
        } else {
            ScanSettings.SCAN_MODE_LOW_POWER // 确认远离车辆后：进入超低功耗模式省电
        }
        val probeDuration = if (consecutiveMissCount <= 2) 5_000L else 3_000L

        controller.recordDiagnostic(BleDiagnosticEvent.PROBE_STARTED, extra = "正在探测车辆广播(${probeDuration / 1000}秒)...")
        controller.updateStateMessage("正在探查附近车辆...")

        var targetDiscovered = false
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (targetDiscovered) return
                val address = result.device.address
                if (address.equals(bound.device.address, ignoreCase = true)) {
                    targetDiscovered = true
                    cancelProbe()
                    consecutiveMissCount = 0
                    val rssi = result.rssi
                    controller.recordDiagnostic(BleDiagnosticEvent.PROBE_SUCCESS, extra = "发现车辆广播 (RSSI=$rssi dBm)，立即发起极速物理直连")
                    connectDirect(targetDevice, certificate, bound.accountId, targetDesired, credentialScope, current)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                cancelProbe()
                connectDirect(targetDevice, certificate, bound.accountId, targetDesired, credentialScope, current)
            }
        }
        probeCallback = callback
        val filter = ScanFilter.Builder().setDeviceAddress(bound.device.address).build()
        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
            handler.postDelayed({
                if (probeCallback === callback && !targetDiscovered) {
                    cancelProbe()
                    consecutiveMissCount++
                    val extraDesc = "未检测到车辆广播 (远离车辆第${consecutiveMissCount}次) · 进入静默休眠"
                    controller.recordDiagnostic(BleDiagnosticEvent.PROBE_MISSED, extra = extraDesc)
                    controller.updateStateMessage("未在车辆附近（待命中）")
                    scheduleProbeRetry()
                }
            }, probeDuration)
        } catch (_: Exception) {
            cancelProbe()
            connectDirect(targetDevice, certificate, bound.accountId, targetDesired, credentialScope, current)
        }
    }

    private fun connectDirect(
        targetDevice: BleNearbyDevice,
        certificate: BleKeyCertificate,
        accountId: String,
        targetDesired: BlePassiveConfiguration,
        credentialScope: BleReconnectCredentialScope,
        current: BleSessionIdentity
    ) {
        controller.connect(targetDevice, certificate, accountId, current.deviceId,
            targetDesired, trustedBinding = true,
            reconnectCredential = reconnectCredentials.load(credentialScope),
            onReconnectCredential = reconnectCredentialHandler(current, credentialScope)
        ) { identity == current && validSession() }
    }

    private fun scheduleProbeRetry() {
        if (retry != null || !runningValue.value) return
        val delayMs = when {
            inCarMediaActive -> 60_000L // 车载音频连接中：人在车内，延长至 60 秒低频嗅探，纯净让出信道
            consecutiveMissCount == 0 -> 1_500L  // 刚断开第0次：1.5 秒后极速重探
            consecutiveMissCount == 1 -> 3_000L  // 第1次未中：3 秒后再次探查
            consecutiveMissCount == 2 -> 5_000L  // 第2次未中：5 秒后再次探查
            consecutiveMissCount in 3..5 -> 15_000L // 车主正在走开：15 秒探查
            else -> 45_000L // 确认远离：45 秒深度休眠
        }
        retry = Runnable {
            retry = null
            reconnectManaged()
        }.also { handler.postDelayed(it, delayMs) }
    }

    private fun onConnection(state: BleConnectionState) {
        connectionValue.value = state
        if (state.phase == BleConnectionPhase.READY) {
            cancelRetry()
            retryAttempt = 0
            consecutiveMissCount = 0
            enrollCandidate()
            val bound = keyValue.value
            if (runningValue.value && !syncing && controller.canConfigure && bound?.pending == true) {
                synchronizeConfiguration()
            }
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
        val isLeap3 = currentCarType.orEmpty().uppercase().let { it.contains("C16") || it.contains("C10") } ||
            (device.protocolMinor ?: 0) >= 9
        val initialConfig = BlePassiveConfiguration(
            enabled = true,
            autoUnlock = false,
            autoLock = false,
            buttonEnabled = isLeap3,
            calibration = profiles.load(current).effectiveCalibration(currentCarType)
        )
        val bound = when {
            existing == null -> BleManagedKey(current.accountId, current.vin, device, fingerprint,
                desired = initialConfig, deviceId = current.deviceId)
            existing.matchesCertificate(certificate) && existing.device.address == device.address -> existing.copy(device = device)
            else -> existing.copy(device = device, certificateFingerprint = fingerprint).suspended()
        }
        if (keys.save(bound)) keyValue.value = bound
    }

    private fun recordTarget(device: BleNearbyDevice, profile: BleVehicleProfile) {
        val metadata = BleVehicleProfile.freshMetadata(profile.metadata, System.currentTimeMillis())
        controller.setVehicleMetadata(metadata)
        val calib = keyValue.value?.desired?.calibration ?: profile.effectiveCalibration(currentCarType)
        val detailInfo = "目标=${maskAddress(device.address)} · 车型=${currentCarType.takeUnless { it.isNullOrBlank() } ?: "默认"} · 标定=${calib.toProtocolText()}"
        controller.recordDiagnostic(BleDiagnosticEvent.TARGET_MATCH,
            targetMatchCode(device.address, metadata?.address), targetMatchCode(device.address, keyValue.value?.device?.address),
            extra = detailInfo
        )
        controller.recordDiagnostic(BleDiagnosticEvent.CALIBRATION_SOURCE, if (profile.calibration == null) 0 else 1)
    }

    private fun synchronizeConfiguration() {
        val bound = keyValue.value ?: return
        val current = identity ?: return
        if (!validSession()) return stopBackground()
        syncing = true
        val isLeap3 = currentCarType.orEmpty().uppercase().let { it.contains("C16") || it.contains("C10") }
        val configureMinor = if (isLeap3 && (bound.device.protocolMinor ?: 0) < BleKeyProtocol.LEAP3_PROTOCOL_MINOR) {
            BleKeyProtocol.LEAP3_PROTOCOL_MINOR
        } else {
            bound.device.protocolMinor ?: BleKeyProtocol.defaultProtocolMinor(currentCarType)
        }
        controller.configure(bound.desired, configureMinor) {
            syncing = false
            if (identity != current || !validSession() || !runningValue.value) return@configure
            val latest = keyValue.value ?: return@configure
            if (latest.revision != bound.revision || latest.desired != bound.desired) {
                if (controller.canConfigure) synchronizeConfiguration()
                return@configure
            }
            val confirmed = latest.confirmed(bound.revision, bound.desired)
            if (!keys.save(confirmed)) {
                return@configure
            }
            keyValue.value = confirmed
            retryAttempt = 0
            if (!confirmed.needsBackground && !foreground) stopBackground()
        }
    }

    private fun scheduleReconnect() {
        if (retry != null) return
        val delay = BleReconnectPolicy.delayMillis(retryAttempt++)
        retry = Runnable {
            retry = null
            if (controller.state.phase != BleConnectionPhase.READY && controller.state.phase != BleConnectionPhase.CONFIGURING) {
                reconnectManaged()
            }
        }.also { handler.postDelayed(it, delay) }
    }

    private fun cancelRetry() {
        retry?.let(handler::removeCallbacks)
        retry = null
        cancelProbe()
    }

    @SuppressLint("MissingPermission")
    private fun cancelProbe() {
        val cb = probeCallback
        probeCallback = null
        if (cb != null) {
            runCatching { controller.adapter?.bluetoothLeScanner?.stopScan(cb) }
        }
    }

    private fun reconnectScope(
        current: BleSessionIdentity,
        certificate: BleKeyCertificate
    ): BleReconnectCredentialScope = BleReconnectCredentialScope(
        current.accountId,
        current.vin,
        current.deviceId,
        BleKeyProtocol.certificateFingerprint(certificate),
        BleCompatibilityProfile.forConnection()
    )

    private fun reconnectCredentialHandler(
        current: BleSessionIdentity,
        scope: BleReconnectCredentialScope
    ): (BleReconnectCredential?) -> Unit = { credential ->
        if (identity == current && validSession()) {
            val saved = if (credential == null) reconnectCredentials.clear(scope)
            else reconnectCredentials.save(scope, credential)
            controller.recordDiagnostic(
                BleDiagnosticEvent.RECONNECT_CREDENTIAL_STORE,
                if (credential == null) 2 else 1,
                if (saved) 1 else 0
            )
        }
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
