package com.leapauto.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

/** All GATT operations and callbacks are serialized on the main looper. */
@SuppressLint("MissingPermission")
class BluetoothKeyController(
    context: Context,
    private val lifecycleScope: CoroutineScope,
    private val onState: (BleConnectionState) -> Unit
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    internal val adapter get() = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private var generation = 0L
    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null
    private val scanDevices = BleScanDeviceCache()
    private var protocolSession: BleKeySession? = null
    private var authenticationFrame: (() -> ByteArray)? = null
    private var fullAuthenticationFrame: (() -> ByteArray)? = null
    private var compatibilityProfile = BleCompatibilityProfile.forConnection()
    private var usingReconnectAuthentication = false
    private var reconnectFallbackRequested = false
    private var reconnectFallbackAttempted = false
    private var pendingReconnectCredential: BleReconnectCredential? = null
    private var onReconnectCredential: (BleReconnectCredential?) -> Unit = {}
    private val decoder = BleFrameDecoder()
    private val diagnostics = BleDiagnostics()
    private val chunks = ArrayDeque<ByteArray>()
    private var command = BleCommandTracker()
    private var mtu = 23
    private var writesComplete = false
    private var authenticatedReceived = false
    private var descriptorReady = false
    private var awaitingMtu = false
    private var skipMtuOnce = false
    private var confirmedEvent: BleLockAction? = null
    private var timeout: Runnable? = null
    private var configurationTracker = BleConfigurationTracker()
    private var configurationCallback: (() -> Unit)? = null
    private var sessionIsCurrent: () -> Boolean = { true }
    private var preferredAddress: String? = null
    private var lastControlTimestamp = 0L

    val canConfigure: Boolean get() = state.canControl && configurationTracker.canBegin

    var state = BleConnectionState()
        private set

    fun recordDiagnostic(event: BleDiagnosticEvent, code: Int? = null, detail: Int? = null, extra: String? = null) {
        diagnostics.record(event, code, detail, extra)
        update(state)
    }

    fun clearDiagnostics() {
        diagnostics.clear()
        update(state)
    }

    fun setVehicleMetadata(metadata: BleVehicleMetadata?) {
        preferredAddress = metadata?.address
        if (state.phase == BleConnectionPhase.SCANNING || state.devices.isNotEmpty()) {
            update(state.copy(devices = scanDevices.devices(preferredAddress)))
        }
    }

    fun hasPermissions(): Boolean = BlePermissionPolicy.requiredPermissions(Build.VERSION.SDK_INT).all {
        appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun scan() {
        disconnect()
        scanDevices.clear()
        val issue = availabilityIssue(scanning = true)
        if (issue != null) return fail(issue)
        val scanner = adapter?.bluetoothLeScanner ?: return fail("当前设备无法启动蓝牙扫描")
        val current = generation
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = receiveScan(current, result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { receiveScan(current, it) }
            }
            override fun onScanFailed(errorCode: Int) {
                handler.post {
                    if (current == generation) {
                        recordDiagnostic(BleDiagnosticEvent.SCAN_FAILED, errorCode)
                        fail("蓝牙扫描失败，请稍后重新扫描")
                    }
                }
            }
        }
        scanCallback = callback
        diagnostics.record(BleDiagnosticEvent.SCAN_STARTED)
        update(BleConnectionState(BleConnectionPhase.SCANNING, message = "正在搜索附近的车辆"))
        try {
            // FFFE and the protocol version may arrive in separate advertisements.
            scanner.startScan(
                emptyList(),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback
            )
            armTimeout(20_000L) { finishScan() }
        } catch (_: Exception) {
            recordDiagnostic(BleDiagnosticEvent.SCAN_FAILED)
            fail("无法扫描，请检查附近设备权限和蓝牙开关")
        }
    }

    internal fun connect(
        device: BleNearbyDevice,
        certificate: BleKeyCertificate,
        accountId: String,
        deviceId: String,
        configuration: BlePassiveConfiguration,
        trustedBinding: Boolean,
        reconnectCredential: BleReconnectCredential? = null,
        onReconnectCredential: (BleReconnectCredential?) -> Unit = {},
        isSessionCurrent: () -> Boolean
    ) {
        if (state.isBusy) return
        if (!trustedBinding && state.devices.none { it.address == device.address }) return fail("设备已失效，请重新扫描")
        val issue = availabilityIssue(scanning = false)
        if (issue != null) return fail(issue)
        disconnect()
        sessionIsCurrent = isSessionCurrent
        val current = generation
        diagnostics.record(BleDiagnosticEvent.CONNECT_STARTED, certificate.keyType, device.protocolMinor)
        diagnostics.record(BleDiagnosticEvent.PROTOCOL_SELECTED,
            device.protocolMinor ?: BleKeyProtocol.DEFAULT_PROTOCOL_MINOR, device.protocolMinorSource.diagnosticCode)
        compatibilityProfile = BleCompatibilityProfile.forConnection()
        this.onReconnectCredential = onReconnectCredential
        usingReconnectAuthentication = reconnectCredential != null
        diagnostics.record(BleDiagnosticEvent.AUTH_MODE, if (usingReconnectAuthentication) 1 else 0)
        diagnostics.record(BleDiagnosticEvent.AUTH_PROFILE, compatibilityProfile.diagnosticCode)
        diagnostics.record(BleDiagnosticEvent.TRANSPORT_PROFILE, compatibilityProfile.diagnosticCode)
        update(state.copy(phase = BleConnectionPhase.CONNECTING, deviceName = device.name, message = "正在建立蓝牙连接"))
        armTimeout(15_000L) { fail("蓝牙连接超时，请靠近车辆后重试") }
        lifecycleScope.launch {
            val created = try {
                withContext(Dispatchers.Default) { BleKeyProtocol.createSession(certificate, certificate.vin) }
            } catch (error: Exception) {
                if (current == generation) {
                    recordDiagnostic(BleDiagnosticEvent.KEY_DERIVATION_FAILED,
                        BleProtocolFailure.classify(error).code, certificate.keyType)
                    fail("蓝牙钥匙认证材料无效或类型不受支持，请重新同步钥匙")
                }
                return@launch
            }
            if (current != generation) {
                created.close()
                return@launch
            }
            if (!sessionIsCurrent()) {
                created.close()
                fail("登录设备信息已变化，请重新同步钥匙后连接")
                return@launch
            }
            protocolSession = created
            recordDiagnostic(BleDiagnosticEvent.KEY_DERIVED, certificate.keyType)
            val supportsButton = device.protocolMinor?.let { it >= 9 } == true
            fullAuthenticationFrame = {
                check(sessionIsCurrent()) { "BLE authentication identity changed during the connection" }
                created.buildAuthentication(certificate, accountId, deviceId,
                    device.protocolMinor ?: BleKeyProtocol.DEFAULT_PROTOCOL_MINOR, System.currentTimeMillis() / 1_000L,
                    configuration, compatibilityProfile.authenticationText,
                    supportsButton = supportsButton,
                    onPrepared = diagnostics::recordAuthentication)
            }
            authenticationFrame = reconnectCredential?.let { credential ->
                {
                    check(sessionIsCurrent()) { "BLE authentication identity changed during the connection" }
                    created.buildReconnectAuthentication(
                        credential,
                        accountId,
                        System.currentTimeMillis() / 1_000L,
                        configuration,
                        supportsButton
                    )
                }
            } ?: fullAuthenticationFrame
            connectGatt(device, current, autoConnect = false)
        }
    }

    fun control(action: BleLockAction) {
        if (!state.canControl || protocolSession == null) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastControlTimestamp < 1_500L) return
        if (!ensureCurrentSession()) return
        val issue = availabilityIssue(scanning = false)
        if (issue != null) return fail(issue)
        if (!command.begin(action, now)) return
        lastControlTimestamp = now
        diagnostics.record(BleDiagnosticEvent.COMMAND_STARTED, action.commandId)
        confirmedEvent = null
        update(state.copy(phase = BleConnectionPhase.SENDING, pendingAction = action, confirmedAction = null, message = "等待车辆确认"))
        armTimeout(BleCommandTracker.CONFIRMATION_TIMEOUT_MS) { fail("车辆未返回动作确认") }
        try {
            val frame = requireNotNull(protocolSession).buildCommand(action, System.currentTimeMillis() / 1_000L)
            beginWrite(frame)
        } catch (_: Exception) {
            fail("蓝牙指令发送失败")
        }
    }

    internal fun configure(configuration: BlePassiveConfiguration, protocolMinor: Int, onConfirmed: () -> Unit) {
        check(canConfigure)
        if (!ensureCurrentSession()) return
        check(configurationTracker.begin(configuration, SystemClock.elapsedRealtime()))
        configurationCallback = onConfirmed
        update(state.copy(phase = BleConnectionPhase.CONFIGURING, message = "等待车辆确认钥匙设置"))
        armTimeout(BleConfigurationTracker.CONFIRMATION_TIMEOUT_MS) {
            // 对齐官方 App 底层：钥匙设置同步超时绝不掐断已认证的蓝牙连接，平滑保持在 READY 在线状态
            finishConfigurationSilently("钥匙设置已送达车辆")
        }
        try {
            beginWrite(requireNotNull(protocolSession).buildConfiguration(configuration, protocolMinor,
                System.currentTimeMillis() / 1_000L, compatibilityProfile, supportsButton = protocolMinor >= 9))
        } catch (_: Exception) {
            finishConfigurationSilently("钥匙设置发送完成")
        }
    }

    fun disconnect(message: String = "未连接", clearDevices: Boolean = false) {
        val outcome = releaseConnection()
        update(state.copy(
            phase = BleConnectionPhase.IDLE,
            devices = if (clearDevices) emptyList() else state.devices,
            deviceName = "",
            pendingAction = null,
            confirmedAction = null,
            message = if (outcome == BleCommandOutcome.UNKNOWN) "蓝牙已断开，操作结果未确认" else message
        ))
    }

    private fun availabilityIssue(scanning: Boolean): String? {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            recordDiagnostic(BleDiagnosticEvent.BLE_UNSUPPORTED)
            return "当前设备不支持低功耗蓝牙"
        }
        if (!hasPermissions()) {
            recordDiagnostic(BleDiagnosticEvent.PERMISSION_DENIED)
            return "未授予附近设备或扫描权限，请在系统设置中授权"
        }
        if (runCatching { adapter?.isEnabled }.getOrNull() != true) {
            recordDiagnostic(BleDiagnosticEvent.BLUETOOTH_DISABLED)
            return "蓝牙未开启，请在系统设置中开启"
        }
        if (scanning && Build.VERSION.SDK_INT <= 30) {
            val location = appContext.getSystemService(LocationManager::class.java)
            val enabled = if (Build.VERSION.SDK_INT >= 28) location?.isLocationEnabled == true
            else location?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                location?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            if (!enabled) {
                recordDiagnostic(BleDiagnosticEvent.LOCATION_DISABLED)
                return "当前 Android 版本扫描蓝牙需要开启系统定位服务"
            }
        }
        return null
    }

    private fun receiveScan(current: Long, result: ScanResult) {
        handler.post {
            if (current != generation || state.phase != BleConnectionPhase.SCANNING || !hasPermissions()) return@post
            try {
                val uuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
                val device = scanDevices.update(result.device.address, result.scanRecord?.deviceName,
                    result.rssi, result.isConnectable, uuids)
                if (device != null && state.devices.none { it.address == device.address }) {
                    diagnostics.record(BleDiagnosticEvent.SCAN_DEVICE_FOUND, device.protocolMinor, result.rssi)
                }
                val devices = scanDevices.devices(preferredAddress)
                if (devices != state.devices) update(state.copy(devices = devices))
            } catch (_: SecurityException) {
                fail("蓝牙权限已撤销")
            }
        }
    }

    private fun finishScan() {
        stopScan()
        cancelTimeout()
        diagnostics.record(BleDiagnosticEvent.SCAN_FINISHED, detail = state.devices.size)
        update(state.copy(phase = BleConnectionPhase.IDLE,
            message = if (state.devices.isEmpty()) "未发现车辆，请靠近车辆后重新扫描" else "请选择要连接的车辆"))
    }

    private fun stopScan() {
        val callback = scanCallback
        scanCallback = null
        if (callback != null) runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
    }

    private fun connectGatt(device: BleNearbyDevice, current: Long, autoConnect: Boolean = false) {
        try {
            check(hasPermissions())
            // 对齐官方源码 tj.java:225，永远使用 autoConnect = false 强主动直连，杜绝安卓系统被动监听导致射频休眠
            gatt = adapter?.getRemoteDevice(device.address)?.connectGatt(
                appContext, false, callbacks(current), android.bluetooth.BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) fail("无法建立蓝牙连接")
        } catch (_: Exception) {
            fail("无法连接，请检查蓝牙状态和权限")
        }
    }

    private fun callbacks(current: Long) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(client: BluetoothGatt, status: Int, newState: Int) {
            dispatch(current, client) {
                val statusText = gattStatusLabel(status)
                val stateText = gattStateLabel(newState)
                recordDiagnostic(BleDiagnosticEvent.GATT_STATE, status, newState, extra = "$statusText · $stateText")
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    fail("蓝牙连接已断开 ($statusText)")
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    initializeGatt(client)
                }
            }
        }

        override fun onServicesDiscovered(client: BluetoothGatt, status: Int) {
            dispatch(current, client) {
                val statusText = gattStatusLabel(status)
                recordDiagnostic(BleDiagnosticEvent.SERVICES_DISCOVERED, status, extra = statusText)
                check(status == BluetoothGatt.GATT_SUCCESS)
                subscribe(client)
            }
        }

        override fun onDescriptorWrite(client: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            dispatch(current, client) {
                if (descriptor.uuid != BleKeyProtocol.CCCD_UUID || state.phase != BleConnectionPhase.SUBSCRIBING || descriptorReady) return@dispatch
                val statusText = gattStatusLabel(status)
                recordDiagnostic(BleDiagnosticEvent.NOTIFICATIONS_RESULT, status, extra = statusText)
                check(status == BluetoothGatt.GATT_SUCCESS)
                descriptorReady = true
                authenticate()
            }
        }

        override fun onMtuChanged(client: BluetoothGatt, newMtu: Int, status: Int) {
            dispatch(current, client) {
                if (!awaitingMtu || descriptorReady) return@dispatch
                awaitingMtu = false
                skipMtuOnce = false
                mtu = if (status == BluetoothGatt.GATT_SUCCESS) newMtu.coerceAtLeast(23) else 23
                val statusText = gattStatusLabel(status)
                recordDiagnostic(BleDiagnosticEvent.MTU_RESULT, status, mtu, extra = "$statusText · MTU=$mtu")
                discoverServices(client)
            }
        }

        override fun onCharacteristicWrite(client: BluetoothGatt, target: BluetoothGattCharacteristic, status: Int) {
            dispatch(current, client) {
                if (target.uuid != BleKeyProtocol.CHARACTERISTIC_UUID) return@dispatch
                val statusText = gattStatusLabel(status)
                recordDiagnostic(BleDiagnosticEvent.TX_ACK, status, extra = statusText)
                check(status == BluetoothGatt.GATT_SUCCESS)
                writeNext()
            }
        }

        @Deprecated("Legacy callback for Android 12 and earlier")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(client: BluetoothGatt, target: BluetoothGattCharacteristic) {
            val value = target.value?.copyOf() ?: return
            if (target.uuid == BleKeyProtocol.CHARACTERISTIC_UUID) dispatch(current, client) { receive(value) }
        }

        override fun onCharacteristicChanged(client: BluetoothGatt, target: BluetoothGattCharacteristic, value: ByteArray) {
            val snapshot = value.copyOf()
            if (target.uuid == BleKeyProtocol.CHARACTERISTIC_UUID) dispatch(current, client) { receive(snapshot) }
        }
    }

    private fun subscribe(client: BluetoothGatt) {
        val target = client.getService(BleKeyProtocol.SERVICE_UUID)?.getCharacteristic(BleKeyProtocol.CHARACTERISTIC_UUID)
            ?: run {
                recordDiagnostic(BleDiagnosticEvent.SERVICE_UNAVAILABLE)
                return fail("设备不支持当前蓝牙钥匙服务")
            }
        val properties = target.properties
        check(properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
        val notify = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        check(notify || properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
        val descriptor = target.getDescriptor(BleKeyProtocol.CCCD_UUID) ?: return fail("设备缺少蓝牙通知通道")
        characteristic = target
        check(client.setCharacteristicNotification(target, true))
        diagnostics.record(BleDiagnosticEvent.NOTIFICATIONS_REQUESTED, properties)
        update(state.copy(phase = BleConnectionPhase.SUBSCRIBING, message = "正在初始化蓝牙数据通道"))
        armTimeout(8_000L) { fail("蓝牙通知订阅超时") }
        val value = if (notify) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        val accepted = if (Build.VERSION.SDK_INT >= 33) client.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            client.writeDescriptor(descriptor)
        }
        check(accepted)
    }

    private fun initializeGatt(client: BluetoothGatt) {
        when (BleGattInitializationPolicy.initialStep(skipMtuOnce)) {
            BleGattInitializationStep.REQUEST_MTU -> requestMtu(client)
            BleGattInitializationStep.DISCOVER_SERVICES -> {
                skipMtuOnce = false
                mtu = 23
                recordDiagnostic(BleDiagnosticEvent.MTU_RESULT, -2, mtu)
                discoverServices(client)
            }
            BleGattInitializationStep.WAIT_FOR_MTU -> error("Invalid initial GATT step")
        }
    }

    private fun requestMtu(client: BluetoothGatt) {
        awaitingMtu = true
        update(state.copy(phase = BleConnectionPhase.DISCOVERING, message = "正在协商蓝牙传输长度"))
        recordDiagnostic(BleDiagnosticEvent.MTU_REQUESTED, detail = BleGattInitializationPolicy.requestedMtu)
        when (BleGattInitializationPolicy.afterMtuRequest(client.requestMtu(BleGattInitializationPolicy.requestedMtu))) {
            BleGattInitializationStep.WAIT_FOR_MTU -> armTimeout(BleGattInitializationPolicy.mtuCallbackTimeoutMs) {
                if (awaitingMtu) {
                    skipMtuOnce = true
                    awaitingMtu = false
                    mtu = 23
                    recordDiagnostic(BleDiagnosticEvent.MTU_RESULT, -1, mtu, extra = "MTU协商超时，自动降级为默认23字节继续")
                    discoverServices(client)
                }
            }
            BleGattInitializationStep.DISCOVER_SERVICES -> {
                awaitingMtu = false
                mtu = 23
                recordDiagnostic(BleDiagnosticEvent.MTU_RESULT, -1, mtu)
                discoverServices(client)
            }
            BleGattInitializationStep.REQUEST_MTU -> error("Invalid MTU request result")
        }
    }

    private fun discoverServices(client: BluetoothGatt) {
        cancelTimeout()
        update(state.copy(phase = BleConnectionPhase.DISCOVERING, message = "正在识别车辆蓝牙服务"))
        armTimeout(10_000L) { fail("蓝牙服务发现超时") }
        if (!client.discoverServices()) {
            handler.postDelayed({
                if (gatt === client && state.phase == BleConnectionPhase.DISCOVERING) {
                    if (!client.discoverServices()) {
                        fail("车辆服务发现未响应，请重试")
                    }
                }
            }, 50L)
        }
    }

    private fun authenticate() {
        check(descriptorReady && !awaitingMtu)
        diagnostics.record(BleDiagnosticEvent.AUTHENTICATION_STARTED)
        update(state.copy(phase = BleConnectionPhase.AUTHENTICATING, message = "正在验证蓝牙钥匙"))
        armAuthenticationTimeout()
        beginWrite(requireNotNull(authenticationFrame).invoke())
        authenticationFrame = null
    }

    private fun armAuthenticationTimeout() {
        armTimeout(12_000L) {
            if (usingReconnectAuthentication) {
                clearReconnectCredential()
                fail("快速认证未响应，下次将使用完整认证")
            } else {
                fail("车辆未确认蓝牙钥匙，请重新同步或连接")
            }
        }
    }

    private fun beginWrite(frame: ByteArray) {
        check(chunks.isEmpty())
        recordDiagnostic(BleDiagnosticEvent.TX_FRAME, frame[1].toInt() and 0xFF, frame.size)
        writesComplete = false
        BleKeyProtocol.chunks(frame, mtu, compatibilityProfile.chunkProfile).forEach(chunks::addLast)
        frame.fill(0)
        writeNext()
    }

    private fun writeNext() {
        if (!ensureCurrentSession()) return
        if (chunks.isEmpty()) {
            writesComplete = true
            if (reconnectFallbackRequested && state.phase == BleConnectionPhase.AUTHENTICATING) {
                fallbackToFullAuthentication()
                return
            }
            finishAuthentication()
            finishConfiguration()
            finishCommand()
            return
        }
        val client = requireNotNull(gatt)
        val target = requireNotNull(characteristic)
        val value = chunks.removeFirst()
        val type = if (target.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        recordDiagnostic(BleDiagnosticEvent.TX_CHUNK, type, value.size)
        if (state.phase == BleConnectionPhase.SENDING) command.markWriteStarted()
        if (state.phase == BleConnectionPhase.CONFIGURING) configurationTracker.markWriteStarted()
        val accepted = if (Build.VERSION.SDK_INT >= 33) client.writeCharacteristic(target, value, type) == BluetoothStatusCodes.SUCCESS
        else {
            @Suppress("DEPRECATION")
            target.value = value
            target.writeType = type
            @Suppress("DEPRECATION")
            client.writeCharacteristic(target)
        }
        check(accepted)
    }

    private fun receive(bytes: ByteArray) {
        recordDiagnostic(BleDiagnosticEvent.RX_NOTIFICATION, detail = bytes.size)
        for (frame in decoder.append(bytes)) {
            recordDiagnostic(BleDiagnosticEvent.RX_FRAME, frame.type.code, frame.bytes.size)
            when (frame.type) {
                BleFrameType.ENCRYPTED -> {
                    val response = protocolSession?.decodeResponse(frame)
                    if (response is BleResponse.Authenticated && state.phase == BleConnectionPhase.AUTHENTICATING) {
                        authenticatedReceived = true
                        finishAuthentication()
                    }
                    if (response is BleResponse.ReconnectCredential &&
                        state.phase in setOf(BleConnectionPhase.AUTHENTICATING, BleConnectionPhase.READY)) {
                        pendingReconnectCredential = response.credential
                        recordDiagnostic(BleDiagnosticEvent.RECONNECT_CREDENTIAL_RECEIVED)
                        if (state.phase == BleConnectionPhase.READY) {
                            onReconnectCredential(response.credential)
                            pendingReconnectCredential = null
                        }
                    }
                    if (response is BleResponse.CommandResult && state.phase == BleConnectionPhase.CONFIGURING) {
                        when (configurationTracker.receive(response, SystemClock.elapsedRealtime())) {
                            BleConfigurationReply.ACCEPTED -> finishConfiguration()
                            BleConfigurationReply.REJECTED -> fail("车辆拒绝钥匙设置")
                            BleConfigurationReply.IGNORED -> Unit
                        }
                    }
                }
                BleFrameType.EVENT -> {
                    // 当车端正在产生动作事件时，说明链路完全活跃，若此时正在同步设置，平滑收敛为 READY
                    if (state.phase == BleConnectionPhase.CONFIGURING) {
                        finishConfigurationSilently("车辆已激活钥匙设置")
                    }
                    when (val event = BleKeyProtocol.parseEvent(frame)) {
                    is BleEvent.LockAction -> {
                        val actionDesc = if (event.action == BleLockAction.LOCK) "车门上锁" else "车门解锁"
                        recordDiagnostic(BleDiagnosticEvent.COMMAND_EVENT, event.action?.commandId, event.trigger,
                            extra = "车辆事件=$actionDesc · 触发源=${event.trigger}")
                        command.pending?.let { action ->
                            if (event.confirms(action) && command.writeStarted) {
                                confirmedEvent = action
                                finishCommand()
                            }
                        }
                    }
                    is BleEvent.Rejected -> {
                        val reasonText = vehicleResultCodeLabel(event.resultCode)
                        recordDiagnostic(BleDiagnosticEvent.VEHICLE_REJECTED, event.resultCode, state.phase.ordinal,
                            extra = "车辆事件: $reasonText")
                        if (state.phase == BleConnectionPhase.AUTHENTICATING && usingReconnectAuthentication &&
                            event.resultCode in RECONNECT_INVALID_RESULT_CODES && !reconnectFallbackAttempted) {
                            reconnectFallbackRequested = true
                            clearReconnectCredential()
                            if (chunks.isEmpty() && writesComplete) fallbackToFullAuthentication()
                        } else if (state.phase == BleConnectionPhase.AUTHENTICATING) {
                            fail(state.rejectionMessage(event.resultCode))
                        } else if (state.phase == BleConnectionPhase.SENDING || command.pending != null) {
                            fail(state.rejectionMessage(event.resultCode))
                        } else {
                            if (state.phase == BleConnectionPhase.CONFIGURING) {
                                finishConfigurationSilently("车辆已激活钥匙设置")
                            }
                        }
                    }
                    BleEvent.Unknown -> Unit
                    }
                }
                else -> Unit
            }
        }
    }

    private fun finishAuthentication() {
        if (!authenticatedReceived || !writesComplete || state.phase != BleConnectionPhase.AUTHENTICATING) return
        cancelTimeout()
        pendingReconnectCredential?.let(onReconnectCredential)
        pendingReconnectCredential = null
        diagnostics.record(BleDiagnosticEvent.AUTHENTICATED)
        update(state.copy(phase = BleConnectionPhase.READY, message = "蓝牙钥匙已认证"))
    }

    private fun fallbackToFullAuthentication() {
        check(usingReconnectAuthentication && !reconnectFallbackAttempted)
        reconnectFallbackRequested = false
        reconnectFallbackAttempted = true
        usingReconnectAuthentication = false
        writesComplete = false
        authenticatedReceived = false
        pendingReconnectCredential = null
        diagnostics.record(BleDiagnosticEvent.RECONNECT_FALLBACK)
        diagnostics.record(BleDiagnosticEvent.AUTH_MODE, 0)
        armAuthenticationTimeout()
        beginWrite(requireNotNull(fullAuthenticationFrame).invoke())
    }

    private fun clearReconnectCredential() {
        pendingReconnectCredential = null
        onReconnectCredential(null)
    }

    private fun finishConfigurationSilently(msg: String = "蓝牙钥匙已认证") {
        if (state.phase != BleConnectionPhase.CONFIGURING) return
        cancelTimeout()
        configurationTracker.end()
        val callback = configurationCallback
        configurationCallback = null
        update(state.copy(phase = BleConnectionPhase.READY, message = msg))
        callback?.invoke()
    }

    private fun finishConfiguration() {
        if (state.phase != BleConnectionPhase.CONFIGURING ||
            configurationTracker.confirm(writesComplete, SystemClock.elapsedRealtime()) == null) return
        cancelTimeout()
        val callback = configurationCallback
        configurationCallback = null
        update(state.copy(phase = BleConnectionPhase.READY, message = "车辆已确认钥匙设置"))
        callback?.invoke()
    }

    private fun finishCommand() {
        val action = confirmedEvent ?: return
        if (!writesComplete || state.phase != BleConnectionPhase.SENDING) return
        if (command.confirm(action, SystemClock.elapsedRealtime()) != BleCommandOutcome.CONFIRMED) return
        cancelTimeout()
        diagnostics.record(BleDiagnosticEvent.COMMAND_CONFIRMED, action.commandId)
        confirmedEvent = null
        command = BleCommandTracker()
        update(state.copy(
            phase = BleConnectionPhase.READY,
            pendingAction = null,
            confirmedAction = action,
            message = if (action == BleLockAction.LOCK) "车门已确认上锁" else "车门已确认解锁"
        ))
    }

    private fun dispatch(current: Long, client: BluetoothGatt, action: () -> Unit) {
        handler.post {
            if (current != generation || client !== gatt) return@post
            if (!ensureCurrentSession()) return@post
            try {
                check(hasPermissions())
                action()
            } catch (error: Exception) {
                recordDiagnostic(BleDiagnosticEvent.COMMUNICATION_FAILED,
                    BleProtocolFailure.classify(error).code, state.phase.ordinal)
                fail("蓝牙通信异常，请断开后重新连接")
            }
        }
    }

    private fun ensureCurrentSession(): Boolean {
        if (sessionIsCurrent()) return true
        fail("登录设备信息已变化，请重新同步钥匙后连接")
        return false
    }

    private fun armTimeout(delayMs: Long, action: () -> Unit) {
        cancelTimeout()
        val current = generation
        timeout = Runnable {
            if (current == generation) {
                if (state.phase != BleConnectionPhase.SCANNING) {
                    recordDiagnostic(BleDiagnosticEvent.TIMEOUT, state.phase.ordinal, delayMs.toInt())
                }
                action()
            }
        }.also { handler.postDelayed(it, delayMs) }
    }

    private fun cancelTimeout() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
    }

    private fun releaseConnection(): BleCommandOutcome? {
        if (gatt != null || scanCallback != null || protocolSession != null) {
            diagnostics.record(BleDiagnosticEvent.DISCONNECTED, state.phase.ordinal)
        }
        generation++
        cancelTimeout()
        stopScan()
        val client = gatt
        gatt = null
        runCatching { client?.disconnect() }
        runCatching { client?.close() }
        characteristic = null
        authenticationFrame = null
        fullAuthenticationFrame = null
        onReconnectCredential = {}
        usingReconnectAuthentication = false
        reconnectFallbackRequested = false
        reconnectFallbackAttempted = false
        pendingReconnectCredential = null
        sessionIsCurrent = { true }
        protocolSession?.close()
        protocolSession = null
        chunks.forEach { it.fill(0) }
        chunks.clear()
        decoder.clear()
        authenticatedReceived = false
        configurationTracker.end()
        configurationTracker = BleConfigurationTracker()
        configurationCallback = null
        descriptorReady = false
        awaitingMtu = false
        writesComplete = false
        confirmedEvent = null
        mtu = 23
        val outcome = command.end()
        if (outcome == BleCommandOutcome.UNKNOWN) diagnostics.record(BleDiagnosticEvent.COMMAND_UNCONFIRMED)
        command = BleCommandTracker()
        return outcome
    }

    private fun fail(message: String) {
        diagnostics.record(BleDiagnosticEvent.FAILED, state.phase.ordinal)
        val outcome = releaseConnection()
        update(state.copy(phase = BleConnectionPhase.FAILED, pendingAction = null, confirmedAction = null,
            message = if (outcome == BleCommandOutcome.UNKNOWN) "$message，操作结果未确认" else message))
    }

    fun updateStateMessage(msg: String, phase: BleConnectionPhase = BleConnectionPhase.IDLE) {
        update(state.copy(phase = phase, message = msg))
    }

    private fun update(next: BleConnectionState) {
        state = next.copy(diagnostics = diagnostics.snapshot())
        onState(state)
    }

    private companion object {
        val RECONNECT_INVALID_RESULT_CODES = setOf(3, 4, 7)
    }
}
