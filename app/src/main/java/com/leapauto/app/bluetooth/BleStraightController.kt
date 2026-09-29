package com.leapauto.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Base64
import javax.crypto.Cipher

/**
 * 零跑官方座舱级直进直出 BLE 控制器 (完全对齐官方源码 xp.java / a91.java / th1.java 架构)：
 * 1. 独立扫描并连接车辆专属座舱蓝牙服务 (UUID: 0000eeed-0000-1000-8000-00805f9b34fb)；
 * 2. 独立订阅 EEE2 特征值通知 (UUID: 0000eee2-0000-1000-8000-00805f9b34fb)；
 * 3. 独立完成座舱专属认证握手 (帧头: 0xAA 0xAE 0x01 0x01 0x0A)；
 * 4. 内置基于官方 th1.java 的流式接收分片拼装器，100% 完整解析车端 0xAA 0xAC 状态报文；
 * 5. 实时监听车端就绪状态 (Ready / Paused / Waiting)，并在 Ready 态下发送单字节加密控制帧 (1=前进, 2=后退, 3=停止)；
 * 6. 彻底与普通车门锁通道隔离，杜绝控制挪车误触发车锁开锁/关锁。
 */
class BleStraightController(private val context: Context) {

    companion object {
        private const val TAG = "BleStraightController"
        private const val CONNECT_TIMEOUT_MS = 35_000L
        private const val WRITE_RETRY_DELAY_MS = 50L
        private const val MAX_PENDING_CHUNKS = 64
    }

    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var straightChar: BluetoothGattCharacteristic? = null
    private var session: BleKeySession? = null

    private val _vehicleState = MutableStateFlow(BleStraightVehicleState.WAITING)
    val vehicleState: StateFlow<BleStraightVehicleState> = _vehicleState.asStateFlow()

    private val _statusMessage = MutableStateFlow("正在启动座舱蓝牙扫描...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _canMove = MutableStateFlow(false)
    val canMove: StateFlow<Boolean> = _canMove.asStateFlow()

    private var isWriting = false
    private val txQueue = ArrayDeque<ByteArray>()
    private var isConnectingOrActive = false
    private var lastStateTimestamp = 0L

    // 官方 th1.d 分片流拼装缓冲区
    private var rxBuffer = byteArrayOf()

    private var currentCertificate: BleKeyCertificate? = null
    private var currentAccountId: String = ""
    private var currentDeviceId: String = ""
    private var preferredDeviceAddress: String? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val vin = currentCertificate?.vin.orEmpty()
            if (isMatchingDevice(result, vin, preferredDeviceAddress)) {
                Log.i(TAG, "发现并成功匹配座舱直进直出设备: ${device.name ?: "未知"}, ${device.address}, RSSI: ${result.rssi}")
                stopScan()
                connectDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "座舱扫描失败: errorCode=$errorCode")
            _statusMessage.value = "座舱蓝牙扫描失败 (错误码 $errorCode)"
            _vehicleState.value = BleStraightVehicleState.FAILED
        }
    }

    private fun isMatchingDevice(result: ScanResult, vin: String, preferredAddress: String?): Boolean {
        val record = result.scanRecord

        // 1. Service UUID 包含 0000eeed
        val uuids = record?.serviceUuids.orEmpty().map { it.uuid }
        if (BleStraightProtocol.SERVICE_UUID in uuids) {
            return true
        }

        // 2. 官方 oj.java 算法：ManufacturerData 匹配 MD5(VIN)[8..16]
        val manufacturerData = record?.manufacturerSpecificData
        if (manufacturerData != null && vin.isNotBlank()) {
            val vinHash = runCatching {
                val digest = MessageDigest.getInstance("MD5").digest(vin.trim().toByteArray(Charsets.UTF_8))
                digest.joinToString("") { "%02x".format(it) }
            }.getOrNull()

            if (vinHash != null && vinHash.length >= 16) {
                val targetSlice = vinHash.substring(8, 16)
                val targetBytes = targetSlice.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

                for (i in 0 until manufacturerData.size()) {
                    val key = manufacturerData.keyAt(i)
                    val value = manufacturerData.valueAt(i) ?: continue
                    val combined = byteArrayOf((key and 0xFF).toByte(), ((key shr 8) and 0xFF).toByte()) + value
                    if (combined.size >= 14) {
                        val slice = combined.copyOfRange(6, 14)
                        if (slice.contentEquals(targetBytes)) {
                            return true
                        }
                    }
                }
            }
        }

        // 3. 设备地址匹配已绑定的钥匙地址且名称符合零跑座舱规范
        if (!preferredAddress.isNullOrBlank() && result.device.address.equals(preferredAddress, ignoreCase = true)) {
            return true
        }

        return false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "已连上座舱蓝牙设备，延迟100ms等待协商参数后发现服务...")
                _statusMessage.value = "已连接座舱设备，正在初始化通道..."
                handler.postDelayed({
                    if (this@BleStraightController.gatt === gatt) {
                        gatt.discoverServices()
                    }
                }, 100L)
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.w(TAG, "座舱蓝牙连接断开: status=$status")
                _statusMessage.value = "座舱蓝牙连接已断开"
                _canMove.value = false
                _vehicleState.value = BleStraightVehicleState.WAITING
                cleanupGatt()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _statusMessage.value = "座舱服务发现失败"
                return
            }
            val service = gatt.getService(BleStraightProtocol.SERVICE_UUID)
            val characteristic = service?.getCharacteristic(BleStraightProtocol.CHARACTERISTIC_UUID)
            val descriptor = characteristic?.getDescriptor(BleStraightProtocol.CCCD_UUID)

            if (characteristic == null || descriptor == null) {
                Log.e(TAG, "未找到直进直出专属服务 EEED / EEE2")
                _statusMessage.value = "车辆未提供直进直出服务，请确认车辆已上电"
                _vehicleState.value = BleStraightVehicleState.UNAVAILABLE
                return
            }

            straightChar = characteristic
            Log.i(TAG, "成功获取直进直出专属特征值 EEE2，正在开启通知...")
            gatt.setCharacteristicNotification(characteristic, true)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == BleStraightProtocol.CCCD_UUID) {
                Log.i(TAG, "直进直出通知已开启，正在发送座舱认证帧...")
                _statusMessage.value = "正在进行座舱认证..."
                sendAuthentication()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            synchronized(txQueue) {
                isWriting = false
                writeNextChunk()
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            characteristic.value?.let { handleNotificationData(it) }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleNotificationData(value)
        }
    }

    /**
     * 建连尚未开始 (身份或证书缺失) 时写入可见原因，
     * 避免抽屉停留在初始扫描文案造成"已就绪"错觉。
     */
    fun markNotReady(reason: String) {
        handler.removeCallbacks(retryWriteRunnable)
        _canMove.value = false
        _vehicleState.value = BleStraightVehicleState.WAITING
        _statusMessage.value = reason
    }

    /**
     * 启动座舱直进直出建连流程
     */
    fun start(
        certificate: BleKeyCertificate,
        accountId: String,
        deviceId: String,
        preferredAddress: String? = null
    ) {
        stop()
        currentCertificate = certificate
        currentAccountId = accountId
        currentDeviceId = deviceId
        preferredDeviceAddress = preferredAddress
        isConnectingOrActive = true

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _statusMessage.value = "请打开手机蓝牙"
            _vehicleState.value = BleStraightVehicleState.FAILED
            return
        }

        // 派生直进直出会话密钥
        try {
            session = BleKeyProtocol.createSession(certificate, certificate.vin)
        } catch (e: Exception) {
            Log.e(TAG, "直进直出会话密钥派生失败", e)
            _statusMessage.value = "座舱认证材料派生失败"
            _vehicleState.value = BleStraightVehicleState.FAILED
            return
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _statusMessage.value = "蓝牙扫描器不可用"
            return
        }

        _statusMessage.value = "正在搜索车辆座舱直进直出广播..."
        _vehicleState.value = BleStraightVehicleState.WAITING

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // 采用空过滤进行全局扫描，结合内部多重特征精确匹配，彻底避免各手机厂商驱动对 128 位 UUID 的过滤 Bug
            scanner?.startScan(emptyList(), settings, scanCallback)
            handler.postDelayed({
                if (isConnectingOrActive && gatt == null) {
                    stopScan()
                    _statusMessage.value = "搜索座舱超时，请确认车辆已进入直进直出就绪状态"
                    _vehicleState.value = BleStraightVehicleState.FAILED
                }
            }, CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "启动扫描异常", e)
            _statusMessage.value = "扫描失败，请检查蓝牙与定位权限"
        }
    }

    private fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
    }

    private fun connectDevice(device: BluetoothDevice) {
        _statusMessage.value = "正在连接座舱设备 (${device.name ?: device.address})..."
        gatt = if (Build.VERSION.SDK_INT >= 23) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private fun sendAuthentication() {
        val currentSession = session ?: return
        val cert = currentCertificate ?: return
        try {
            val authFrame = BleStraightProtocol.buildAuthenticationFrame(
                session = currentSession,
                certificate = cert,
                accountId = currentAccountId,
                deviceId = currentDeviceId
            )
            val chunks = BleStraightProtocol.chunkFrame(authFrame, BleStraightProtocol.CHUNK_SIZE)
            synchronized(txQueue) {
                txQueue.clear()
                chunks.forEach(txQueue::addLast)
                writeNextChunk()
            }
        } catch (e: Exception) {
            Log.e(TAG, "构造座舱认证帧异常", e)
            _statusMessage.value = "座舱认证异常: ${e.message}"
        }
    }

    /**
     * 发送前进、后退或刹停物理控制指令 (严格按官方单字节封装)
     *
     * 通道未建立时严禁静默丢弃：必须写入可见状态并撤销就绪态，
     * 否则抽屉会伪造"正在前进"而实际一帧都未发出。
     */
    fun control(action: BleStraightAction) {
        val currentSession = session
        if (currentSession == null || straightChar == null || gatt == null) {
            _canMove.value = false
            if (action != BleStraightAction.STOP) {
                _statusMessage.value = "座舱通道未就绪，请等待连接完成后再按住方向键"
                Log.w(
                    TAG,
                    "control($action) 被拒绝: session=${currentSession != null}, " +
                        "char=${straightChar != null}, gatt=${gatt != null}"
                )
            }
            return
        }

        val frame = BleStraightProtocol.buildControlFrame(currentSession, action)
        val chunks = BleStraightProtocol.chunkFrame(frame, BleStraightProtocol.CHUNK_SIZE)

        synchronized(txQueue) {
            if (action == BleStraightAction.STOP) {
                // STOP 刹停具有最高抢占优先级，立刻清空此前可能积压的前进/后退报文
                txQueue.clear()
            }
            chunks.forEach(txQueue::addLast)
            if (!isWriting) {
                writeNextChunk()
            }
        }
    }

    private fun writeNextChunk() {
        synchronized(txQueue) {
            val client = gatt
            val target = straightChar
            if (client == null || target == null) {
                // GATT 已释放：清空残余报文，避免 isWriting 永久卡死
                isWriting = false
                txQueue.clear()
                return
            }
            if (txQueue.isEmpty()) {
                isWriting = false
                return
            }
            isWriting = true
            val chunk = txQueue.removeFirst()
            val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val accepted = if (Build.VERSION.SDK_INT >= 33) {
                client.writeCharacteristic(target, chunk, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                target.value = chunk
                target.writeType = type
                @Suppress("DEPRECATION")
                client.writeCharacteristic(target)
            }
            if (accepted) return
            // 写入被 GATT 拒绝 (常见于刚建连时的 BUSY)：不得丢帧，回退队首并延迟重试，
            // 否则首帧丢失且队列停摆，表现为"长按方向键车不动"。
            if (txQueue.size < MAX_PENDING_CHUNKS) txQueue.addFirst(chunk)
            isWriting = false
            handler.postDelayed(retryWriteRunnable, WRITE_RETRY_DELAY_MS)
        }
    }

    private val retryWriteRunnable = Runnable {
        synchronized(txQueue) {
            if (gatt != null && straightChar != null && txQueue.isNotEmpty() && !isWriting) {
                writeNextChunk()
            }
        }
    }

    /**
     * 基于官方 th1.d 的流式分片拼接重组器与状态解码
     */
    private fun handleNotificationData(chunk: ByteArray) {
        val currentSession = session ?: return
        synchronized(this) {
            rxBuffer += chunk

            while (rxBuffer.size >= 2) {
                // 1. 模式 A: 官方 0xAA 0xAC 封装帧 (th1.d 规范)
                val headerIndex = (0 until rxBuffer.size - 1).firstOrNull {
                    rxBuffer[it] == 0xAA.toByte() && rxBuffer[it + 1] == 0xAC.toByte()
                }
                if (headerIndex != null) {
                    if (headerIndex > 0) {
                        rxBuffer = rxBuffer.copyOfRange(headerIndex, rxBuffer.size)
                    }
                    if (rxBuffer.size < 9) {
                        // 帧头已对齐，等待后续载荷到达
                        break
                    }
                    var payloadLen = 0L
                    for (i in 0 until 4) {
                        payloadLen = payloadLen or ((rxBuffer[5 + i].toLong() and 0xFF) shl (i * 8))
                    }
                    val intLen = payloadLen.toInt()
                    if (intLen <= 0 || intLen > 65536) {
                        rxBuffer = rxBuffer.copyOfRange(2, rxBuffer.size)
                        continue
                    }
                    val totalFrameLen = 11 + intLen
                    if (rxBuffer.size < totalFrameLen) {
                        // 后续分片未接收完整，继续等待
                        break
                    }

                    val payloadBytes = rxBuffer.copyOfRange(9, 9 + intLen)
                    rxBuffer = rxBuffer.copyOfRange(totalFrameLen, rxBuffer.size)

                    val decryptedText = runCatching {
                        val base64Str = String(payloadBytes, Charsets.US_ASCII).trim()
                        val ciphertext = Base64.getDecoder().decode(base64Str)
                        val plain = currentSession.crypt(ciphertext, Cipher.DECRYPT_MODE)
                        String(plain, Charsets.UTF_8).trim()
                    }.getOrNull()

                    if (!decryptedText.isNullOrBlank()) {
                        val update = BleStraightProtocol.parseVehicleState(decryptedText)
                        if (update != null) {
                            onVehicleStateUpdate(update)
                        }
                    }
                    continue
                }

                // 2. 模式 B: 尝试自适应直接解析单片载荷 (如整片 Base64 或明文)
                val singleUpdate = BleStraightProtocol.decodeVehicleNotification(currentSession, chunk)
                if (singleUpdate != null) {
                    rxBuffer = byteArrayOf()
                    onVehicleStateUpdate(singleUpdate)
                    break
                }

                // 缓冲区未包含有效帧头且无法解析，丢弃多余前缀字节
                if (rxBuffer.size > 256) {
                    rxBuffer = byteArrayOf()
                }
                break
            }
        }
    }

    private fun onVehicleStateUpdate(update: BleStraightStateUpdate) {
        lastStateTimestamp = SystemClock.elapsedRealtime()
        _vehicleState.value = update.state
        when (update.state) {
            BleStraightVehicleState.READY -> {
                _statusMessage.value = "座舱就绪，长按方向键即可挪车"
                _canMove.value = true
            }
            BleStraightVehicleState.ACTIVATE -> {
                _statusMessage.value = "车辆直进直出激活中..."
                _canMove.value = false
            }
            BleStraightVehicleState.WAITING -> {
                _statusMessage.value = "车辆准备中，请稍候..."
                _canMove.value = false
            }
            BleStraightVehicleState.PAUSED -> {
                _statusMessage.value = "车辆已暂停 (原因码 ${update.reasonCode})，松手后重新按住"
                _canMove.value = false
            }
            BleStraightVehicleState.FAILED -> {
                _statusMessage.value = "直进直出操作失败 (错误码 ${update.reasonCode})"
                _canMove.value = false
            }
            BleStraightVehicleState.UNAVAILABLE -> {
                _statusMessage.value = "车辆暂不可用，请检查车门、P 挡和周围环境 (原因码 ${update.reasonCode})"
                _canMove.value = false
            }
        }
    }

    fun stop() {
        isConnectingOrActive = false
        stopScan()
        try {
            // 松手或退出时全力下发 STOP
            session?.let { s ->
                straightChar?.let { c ->
                    gatt?.let { g ->
                        val stopFrame = BleStraightProtocol.buildControlFrame(s, BleStraightAction.STOP)
                        val chunks = BleStraightProtocol.chunkFrame(stopFrame, BleStraightProtocol.CHUNK_SIZE)
                        for (chunk in chunks) {
                            if (Build.VERSION.SDK_INT >= 33) {
                                g.writeCharacteristic(c, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                            } else {
                                @Suppress("DEPRECATION")
                                c.value = chunk
                                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                @Suppress("DEPRECATION")
                                g.writeCharacteristic(c)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        cleanupGatt()
        session?.close()
        session = null
        _canMove.value = false
        _vehicleState.value = BleStraightVehicleState.WAITING
        _statusMessage.value = "未连接"
    }

    private fun cleanupGatt() {
        handler.removeCallbacks(retryWriteRunnable)
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        straightChar = null
        isWriting = false
        rxBuffer = byteArrayOf()
        synchronized(txQueue) { txQueue.clear() }
    }
}
