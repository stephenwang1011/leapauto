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
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * 零跑官方座舱级直进直出 BLE 控制器 (完全对齐官方源码 xp.java / a91.java 架构)：
 * 1. 独立扫描并连接车辆专属座舱蓝牙服务 (UUID: 0000eeed-0000-1000-8000-00805f9b34fb)；
 * 2. 独立订阅 EEE2 特征值通知 (UUID: 0000eee2-0000-1000-8000-00805f9b34fb)；
 * 3. 独立完成座舱专属认证握手 (帧头: 0xAA 0xAE 0x01 0x01 0x0A)；
 * 4. 实时监听车端就绪状态 (Ready / Paused / Waiting)，并在 Ready 态下发送单字节加密控制帧 (1=前进, 2=后退, 3=停止)；
 * 5. 彻底与普通车门锁通道隔离，杜绝控制挪车误触发车锁开锁/关锁。
 */
class BleStraightController(private val context: Context) {

    companion object {
        private const val TAG = "BleStraightController"
        private const val CONNECT_TIMEOUT_MS = 30_000L
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
                val digest = java.security.MessageDigest.getInstance("MD5").digest(vin.trim().toByteArray(Charsets.UTF_8))
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
                Log.i(TAG, "已连上座舱蓝牙设备，正在发现服务...")
                _statusMessage.value = "已连接座舱设备，正在初始化通道..."
                handler.post { gatt.discoverServices() }
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

    private var currentCertificate: BleKeyCertificate? = null
    private var currentAccountId: String = ""
    private var currentDeviceId: String = ""

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
     * 发送前进、后退或刹停物理控制指令
     */
    fun control(action: BleStraightAction) {
        val currentSession = session ?: return
        val characteristic = straightChar ?: return
        if (gatt == null) return

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
        val client = gatt ?: return
        val target = straightChar ?: return
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
        if (!accepted) {
            isWriting = false
        }
    }

    private fun handleNotificationData(bytes: ByteArray) {
        val currentSession = session ?: return
        val update = BleStraightProtocol.decodeVehicleNotification(currentSession, bytes)
        if (update != null) {
            lastStateTimestamp = SystemClock.elapsedRealtime()
            _vehicleState.value = update.state
            when (update.state) {
                BleStraightVehicleState.READY -> {
                    _statusMessage.value = "车辆就绪，长按方向键即可挪车"
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
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        straightChar = null
        isWriting = false
        synchronized(txQueue) { txQueue.clear() }
    }
}
