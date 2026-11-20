package com.leapauto.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 直进直出（遥控挪车）座舱连接状态。
 */
sealed interface CallCarState {
    data object Idle : CallCarState
    data object Scanning : CallCarState
    data class Connecting(val deviceAddress: String) : CallCarState
    data object Preparing : CallCarState
    data object Ready : CallCarState
    data class Moving(val direction: StraightMoveDirection) : CallCarState
    data class Paused(val reason: String = "") : CallCarState
    data class Failed(val message: String) : CallCarState
}

/**
 * 零跑座舱直进直出（遥控挪车）控制器。
 *
 * 核心机制：
 * 1. 独立连接 Service 0xEEED / Characteristic 0xEEE2；
 * 2. 扫描匹配广播 ManufacturerData 中的 MD5(VIN) 防串车截取；
 * 3. 复用现有蓝牙钥匙证书派生会话密钥并完成座舱专属鉴权；
 * 4. 100ms 脉冲心跳（按住即走，松手即刹）与 350ms/1000ms 双重超时看门狗急停熔断。
 */
@SuppressLint("MissingPermission")
class CallCarController(
    private val context: Context,
    private val onStateChanged: (CallCarState) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    var state: CallCarState = CallCarState.Idle
        private set(value) {
            field = value
            onStateChanged(value)
        }

    private var targetVin: String = ""
    private var certificate: BleKeyCertificate? = null
    private var accountId: String = ""
    private var deviceId: String = ""

    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var scanner: BluetoothLeScanner? = null
    private var protocolSession: BleKeySession? = null

    private var packetBuffer = CallCarPacketBuffer()
    private var currentOrder = StraightMoveOrder.STOP.code
    private var pendingOrder: Int? = null
    private val writeChunks = ArrayDeque<ByteArray>()
    private var isWriting = false
    private var lastWriteTimestamp = 0L
    private var lastResponseTimestamp = 0L

    private var generation = 0
    private var isClosing = false
    private var isApplicationActive = true

    private var heartbeatJob: Job? = null
    private var stopRepeatsJob: Job? = null
    private var prepareTimeoutRunnable: Runnable? = null

    private fun cancelPrepareTimeout() {
        prepareTimeoutRunnable?.let(handler::removeCallbacks)
        prepareTimeoutRunnable = null
    }

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun setApplicationActive(active: Boolean) {
        isApplicationActive = active
        if (!active) {
            close()
        }
    }

    /**
     * 准备直进直出连接：扫描、识别并建立 0xEEED 通道完成鉴权。
     */
    fun prepare(
        vin: String,
        cert: BleKeyCertificate,
        accId: String,
        devId: String,
        timeoutMs: Long = 50_000L
    ) {
        if (!isApplicationActive) {
            state = CallCarState.Failed("请保持应用在前台运行")
            return
        }
        if (!hasPermissions()) {
            state = CallCarState.Failed("未授予蓝牙扫描或附近设备权限")
            return
        }
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            state = CallCarState.Failed("手机蓝牙未开启，请先打开蓝牙")
            return
        }

        disconnect()
        generation++
        val epoch = generation
        isClosing = false
        targetVin = vin
        certificate = cert
        accountId = accId
        deviceId = devId
        state = CallCarState.Scanning

        val leScanner = bluetoothAdapter.bluetoothLeScanner
        if (leScanner == null) {
            state = CallCarState.Failed("系统蓝牙扫描器不可用")
            return
        }
        scanner = leScanner

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(CallCarProtocol.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            leScanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: Exception) {
            state = CallCarState.Failed("启动座舱蓝牙扫描失败：${e.message}")
            return
        }

        // 超时看门狗
        cancelPrepareTimeout()
        prepareTimeoutRunnable = Runnable {
            prepareTimeoutRunnable = null
            if (epoch == generation && (state == CallCarState.Scanning || state is CallCarState.Connecting || state == CallCarState.Preparing)) {
                fail("车辆遥控连接超时，请检查车辆是否上电且在附近")
            }
        }.also { handler.postDelayed(it, timeoutMs) }
    }

    /**
     * 按住持续移动：下发前进 (1) 或后退 (2)，并启动 100ms 心跳。
     */
    fun startMove(direction: StraightMoveDirection) {
        if (!isApplicationActive || isClosing || protocolSession == null || state != CallCarState.Ready) {
            return
        }
        if (SystemClock.elapsedRealtime() - lastResponseTimestamp > 1_500L) {
            fail("车辆状态心跳中断，请等待重新就绪")
            return
        }
        stopRepeatsJob?.cancel()
        stopRepeatsJob = null
        currentOrder = direction.orderCode
        state = CallCarState.Moving(direction)
        sendOrder()

        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isApplicationActive && !isClosing) {
                delay(100L)
                if (state !is CallCarState.Moving) break
                val now = SystemClock.elapsedRealtime()
                // 350ms 写入超时 或 1000ms 车辆回包超时 -> 熔断
                if ((isWriting && now - lastWriteTimestamp > 350L) || (now - lastResponseTimestamp > 1_000L)) {
                    fail("遥控挪车通信中断，已紧急制动")
                    break
                }
                sendOrder()
            }
        }
    }

    /**
     * 松手即刹：下发停止 (3)，并在 70ms 间隔补发 2 次确认停止。
     */
    fun stopMove() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (protocolSession == null || isClosing) return
        currentOrder = StraightMoveOrder.STOP.code
        state = CallCarState.Paused("已停止")
        if (isWriting) {
            pendingOrder = StraightMoveOrder.STOP.code
        } else {
            sendOrder()
        }

        stopRepeatsJob?.cancel()
        stopRepeatsJob = scope.launch {
            repeat(2) {
                delay(70L)
                sendOrder()
            }
        }
    }

    /**
     * 暂停后恢复就绪。
     */
    fun resumeMove() {
        if (protocolSession == null || isClosing || state !is CallCarState.Paused) return
        currentOrder = StraightMoveOrder.RESUME.code
        state = CallCarState.Preparing
        sendOrder()
    }

    /**
     * 退出直进直出模式，下发退出码并断开。
     */
    fun close() {
        generation++
        val epoch = generation
        isClosing = true
        heartbeatJob?.cancel()
        heartbeatJob = null
        stopRepeatsJob?.cancel()
        stopRepeatsJob = null
        currentOrder = StraightMoveOrder.STOP.code
        state = CallCarState.Idle

        if (protocolSession != null) {
            sendOrder()
            scope.launch {
                delay(150L)
                if (epoch != generation) return@launch
                currentOrder = StraightMoveOrder.EXIT.code
                sendOrder()
                delay(150L)
                if (epoch == generation) disconnect()
            }
        } else {
            disconnect()
        }
    }

    private fun fail(message: String) {
        close()
        state = CallCarState.Failed(message)
    }

    private fun disconnect() {
        cancelPrepareTimeout()
        heartbeatJob?.cancel()
        heartbeatJob = null
        stopRepeatsJob?.cancel()
        stopRepeatsJob = null
        runCatching { scanner?.stopScan(scanCallback) }
        scanner = null
        val client = gatt
        gatt = null
        runCatching {
            client?.disconnect()
            client?.close()
        }
        characteristic = null
        protocolSession?.close()
        protocolSession = null
        packetBuffer.clear()
        writeChunks.clear()
        isWriting = false
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scope.launch {
                if (gatt != null || isClosing || state != CallCarState.Scanning) return@launch
                val vin = targetVin
                val items = result.scanRecord?.manufacturerSpecificData ?: return@launch
                var matched = false
                for (i in 0 until items.size()) {
                    val company = items.keyAt(i)
                    val raw = byteArrayOf(company.toByte(), (company shr 8).toByte()) + items.valueAt(i)
                    if (CallCarProtocol.matchesManufacturerData(raw, vin)) {
                        matched = true
                        break
                    }
                }
                if (!matched) return@launch
                runCatching { scanner?.stopScan(scanCallback) }
                val address = result.device.address
                state = CallCarState.Connecting(address)
                gatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scope.launch {
                fail("座舱蓝牙扫描失败 (代码 $errorCode)")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            scope.launch {
                if (g !== gatt) return@launch
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    fail("座舱蓝牙连接已断开")
                    return@launch
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    state = CallCarState.Preparing
                    if (!g.discoverServices()) {
                        fail("座舱服务发现请求失败")
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            scope.launch {
                if (g !== gatt) return@launch
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("座舱服务发现失败 (状态 $status)")
                    return@launch
                }
                val targetService = g.getService(CallCarProtocol.SERVICE_UUID)
                val targetCharacteristic = targetService?.getCharacteristic(CallCarProtocol.CHARACTERISTIC_UUID)
                val descriptor = targetCharacteristic?.getDescriptor(CallCarProtocol.CCCD_UUID)

                if (targetCharacteristic == null || descriptor == null ||
                    (targetCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0)
                ) {
                    fail("当前车辆未提供直进直出服务，请确认车型配置")
                    return@launch
                }

                characteristic = targetCharacteristic
                if (!g.setCharacteristicNotification(targetCharacteristic, true)) {
                    fail("座舱蓝牙通知订阅注册失败")
                    return@launch
                }

                val accepted = if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == 0
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
                if (!accepted) {
                    fail("座舱蓝牙订阅请求被系统拒绝")
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            scope.launch {
                if (g !== gatt || descriptor.uuid != CallCarProtocol.CCCD_UUID) return@launch
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("座舱通知写入失败 (状态 $status)")
                    return@launch
                }
                try {
                    val cert = certificate ?: run {
                        fail("蓝牙钥匙证书未同步")
                        return@launch
                    }
                    val session = BleKeyProtocol.createSession(cert, targetVin)
                    protocolSession = session
                    val frame = session.buildCallCarAuthentication(cert, accountId, deviceId)
                    write(frame)
                } catch (e: Exception) {
                    fail("座舱会话密钥协商失败：${e.message}")
                }
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            scope.launch {
                if (g !== gatt) return@launch
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("座舱数据写入失败 (状态 $status)")
                } else {
                    nextChunk()
                }
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf()
            scope.launch { receive(g, copy) }
        }

        @Deprecated("Legacy characteristic changed callback")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                val copy = c.value?.copyOf() ?: return
                scope.launch { receive(g, copy) }
            }
        }
    }

    private fun write(bytes: ByteArray) {
        if (isWriting || gatt == null || characteristic == null) return
        writeChunks.clear()
        bytes.toList().chunked(20).map { it.toByteArray() }.forEach(writeChunks::add)
        isWriting = true
        lastWriteTimestamp = SystemClock.elapsedRealtime()
        nextChunk()
    }

    private fun nextChunk() {
        if (writeChunks.isEmpty()) {
            isWriting = false
            val queued = pendingOrder
            if (queued != null) {
                pendingOrder = null
                currentOrder = queued
                sendOrder()
            }
            return
        }
        val target = characteristic ?: return
        val g = gatt ?: return
        val chunk = writeChunks.removeFirst()
        target.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val success = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(target, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0
        } else {
            @Suppress("DEPRECATION")
            target.value = chunk
            @Suppress("DEPRECATION")
            g.writeCharacteristic(target)
        }
        if (!success) {
            fail("座舱分片写入系统拒绝")
        }
    }

    private fun sendOrder() {
        if (protocolSession == null || isWriting) return
        try {
            val frame = requireNotNull(protocolSession).buildCallCarOrder(currentOrder)
            write(frame)
        } catch (e: Exception) {
            fail("座舱指令加密失败：${e.message}")
        }
    }

    private fun receive(link: BluetoothGatt, data: ByteArray) {
        if (link !== gatt || isClosing) return
        try {
            val session = protocolSession ?: return
            for (packet in packetBuffer.append(data)) {
                if (isClosing) return
                if (packet.type == 0) {
                    fail("座舱认证未通过，请重新同步蓝牙证书")
                    return
                }
                val plainText = session.decodeCallCarPayload(packet.payload)
                if (plainText.isNullOrBlank()) continue
                val status = CallCarStatus.parse(plainText) ?: continue
                lastResponseTimestamp = SystemClock.elapsedRealtime()

                when (status.kind) {
                    CallCarStatus.Kind.Waiting -> {
                        if (state == CallCarState.Ready || state is CallCarState.Moving) {
                            currentOrder = StraightMoveOrder.STOP.code
                            state = CallCarState.Preparing
                        }
                    }
                    CallCarStatus.Kind.Activate -> {
                        currentOrder = StraightMoveOrder.ACTIVATE.code
                        state = CallCarState.Preparing
                        sendOrder()
                    }
                    CallCarStatus.Kind.Ready -> {
                        if (state !is CallCarState.Moving) {
                            currentOrder = StraightMoveOrder.STOP.code
                            state = CallCarState.Ready
                        }
                    }
                    CallCarStatus.Kind.Paused -> {
                        currentOrder = StraightMoveOrder.STOP.code
                        state = CallCarState.Paused(status.reasonDescription())
                    }
                    CallCarStatus.Kind.Failed -> {
                        fail("车辆退出遥控挪车：${status.reasonDescription()}")
                    }
                    CallCarStatus.Kind.Unavailable -> {
                        fail("车辆暂不可用：${status.reasonDescription()}")
                    }
                }
            }
        } catch (e: Exception) {
            fail("解析座舱回包异常：${e.message}")
        }
    }
}
