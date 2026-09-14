package com.leapauto.app.bluetooth

enum class BleDiagnosticEvent(val label: String) {
    CERTIFICATE_SYNC_STARTED("开始同步钥匙"),
    CERTIFICATE_SYNCED("钥匙同步完成"),
    CERTIFICATE_SYNC_FAILED("钥匙同步失败"),
    PERMISSION_REQUESTED("请求蓝牙权限"),
    PERMISSION_GRANTED("蓝牙权限已授予"),
    PERMISSION_DENIED("蓝牙权限未授予"),
    BLE_UNSUPPORTED("手机不支持低功耗蓝牙"),
    BLUETOOTH_DISABLED("手机蓝牙未开启"),
    LOCATION_DISABLED("系统定位服务未开启"),
    SCAN_STARTED("开始扫描车辆"),
    SCAN_DEVICE_FOUND("发现可连接车辆"),
    SCAN_FINISHED("车辆扫描结束"),
    SCAN_FAILED("系统扫描失败"),
    CONNECT_STARTED("开始连接车辆"),
    PROTOCOL_SELECTED("认证协议版本"),
    CERTIFICATE_IDENTITY_UPDATED("证书同步更新了设备身份"),
    KEY_DERIVED("会话密钥派生完成"),
    KEY_DERIVATION_FAILED("钥匙解析或派生失败"),
    GATT_STATE("系统蓝牙连接回调"),
    SERVICES_DISCOVERED("车辆服务发现回调"),
    SERVICE_UNAVAILABLE("车辆钥匙服务不匹配"),
    NOTIFICATIONS_REQUESTED("订阅车辆通知"),
    NOTIFICATIONS_RESULT("通知订阅回调"),
    MTU_REQUESTED("协商传输长度"),
    MTU_RESULT("传输长度协商结果"),
    AUTHENTICATION_STARTED("开始钥匙认证"),
    TX_FRAME("开始写入报文"),
    TX_CHUNK("写入报文分片"),
    TX_ACK("系统写入回调"),
    RX_NOTIFICATION("收到车辆通知"),
    RX_FRAME("完整车辆报文"),
    AUTHENTICATED("车辆已确认认证"),
    COMMAND_STARTED("开始车辆操作"),
    COMMAND_EVENT("收到车辆动作事件"),
    COMMAND_CONFIRMED("车辆已确认操作"),
    COMMAND_UNCONFIRMED("操作结果未确认"),
    VEHICLE_REJECTED("车辆拒绝请求"),
    TIMEOUT("等待车辆或系统回调超时"),
    COMMUNICATION_FAILED("蓝牙通信或报文处理失败"),
    FAILED("当前步骤失败"),
    DISCONNECTED("蓝牙连接已释放")
}

data class BleDiagnosticEntry(
    val elapsedMillis: Long,
    val event: BleDiagnosticEvent,
    val code: Int? = null,
    val detail: Int? = null
) {
    val description: String get() = buildString {
        append(event.label)
        if (event == BleDiagnosticEvent.PROTOCOL_SELECTED) {
            val source = when (detail) {
                BleProtocolMinorSource.ADVERTISED.diagnosticCode -> "车辆广播"
                BleProtocolMinorSource.DEFAULT.diagnosticCode -> "默认回退"
                BleProtocolMinorSource.SAVED.diagnosticCode -> "已保存绑定"
                else -> "来源未知"
            }
            append(" · ").append(source)
        }
        if (event == BleDiagnosticEvent.KEY_DERIVATION_FAILED || event == BleDiagnosticEvent.COMMUNICATION_FAILED) {
            BleProtocolFailure.entries.firstOrNull { it.code == code }?.let { append(" · ").append(it.label) }
        }
        val phaseCode = when (event) {
            BleDiagnosticEvent.TIMEOUT, BleDiagnosticEvent.FAILED, BleDiagnosticEvent.DISCONNECTED -> code
            BleDiagnosticEvent.COMMUNICATION_FAILED, BleDiagnosticEvent.VEHICLE_REJECTED -> detail
            else -> null
        }
        BleConnectionPhase.entries.getOrNull(phaseCode ?: -1)?.let {
            append(" · ").append(BleConnectionState(phase = it).phaseLabel)
        }
        code?.let { append(" · code=").append(it) }
        detail?.let { append(" · detail=").append(it) }
    }
}

// Structured events intentionally cannot carry certificate text, addresses or raw packets.
class BleDiagnostics(
    private val capacity: Int = 120,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private val entries = ArrayDeque<BleDiagnosticEntry>()
    private var startedAt = clockMillis()

    init {
        require(capacity in 1..1_000)
    }

    fun record(event: BleDiagnosticEvent, code: Int? = null, detail: Int? = null) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(BleDiagnosticEntry((clockMillis() - startedAt).coerceAtLeast(0), event, code, detail))
    }

    fun snapshot(): List<BleDiagnosticEntry> = entries.toList()

    fun clear() {
        entries.clear()
        startedAt = clockMillis()
    }

    companion object {
        fun formatReport(entries: List<BleDiagnosticEntry>, appVersion: String, phase: BleConnectionPhase): String =
            buildString {
                appendLine("LeapAuto Bluetooth Diagnostics")
                appendLine("Version: $appVersion")
                appendLine("Phase: ${phase.name}")
                appendLine("Elapsed times in milliseconds; structured codes only.")
                entries.forEach { entry ->
                    append('+').append(entry.elapsedMillis).append(" ms ")
                    append(entry.event.name).append(" | ").appendLine(entry.description)
                }
            }
    }
}
