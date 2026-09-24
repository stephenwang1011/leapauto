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
    VEHICLE_METADATA_STARTED("同步车辆蓝牙信息"),
    VEHICLE_METADATA_RESULT("车辆蓝牙信息结果"),
    TARGET_MATCH("连接目标核对"),
    AUTH_MODE("认证方式"),
    AUTH_PROFILE("认证文本格式"),
    TRANSPORT_PROFILE("蓝牙传输策略"),
    RECONNECT_CREDENTIAL_RECEIVED("收到快速认证凭证"),
    RECONNECT_CREDENTIAL_STORE("快速认证凭证存储"),
    RECONNECT_FALLBACK("快速认证回退完整认证"),
    AUTH_IDENTITY_PLACEHOLDERS("证书身份占位"),
    CALIBRATION_SOURCE("标定参数来源"),
    CALIBRATION_SAVED("本机标定已保存"),
    CONFIGURATION_BACKGROUND_PAUSED("配置已保存，后台连接尚未启动"),
    CLOUD_SAVE_STARTED("开始保存云端偏好"),
    CLOUD_SAVE_RESULT("云端偏好保存结果"),
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
    AUTH_CERTIFICATE_STRUCTURE("认证证书结构"),
    AUTH_TEXT_STRUCTURE("认证文本结构"),
    AUTH_CIPHER_STRUCTURE("认证加密结构"),
    AUTH_CONFIGURATION("认证配置结构"),
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
        when (event) {
            BleDiagnosticEvent.TARGET_MATCH -> {
                append(" · 云端=").append(targetLabel(code))
                append(" · 绑定=").append(targetLabel(detail))
            }
            BleDiagnosticEvent.AUTH_MODE -> append(when (code) {
                0 -> " · 完整认证 AAAE"
                1 -> " · 快速认证 AAEE"
                else -> " · 方式未知"
            })
            BleDiagnosticEvent.AUTH_PROFILE -> append(
                if (code == BleCompatibilityProfile.ONE_PAO_V010.diagnosticCode)
                    " · 1PAO 0.10 固定字段" else " · 旧版动态字段"
            )
            BleDiagnosticEvent.TRANSPORT_PROFILE -> append(
                if (code == BleCompatibilityProfile.ONE_PAO_V010.diagnosticCode)
                    " · MTU优先 · 分片上限197" else " · 旧版分片上限160"
            )
            BleDiagnosticEvent.RECONNECT_CREDENTIAL_STORE -> append(when (code) {
                1 -> if (detail == 1) " · 已加密保存" else " · 保存失败"
                2 -> if (detail == 1) " · 已清除" else " · 清除失败"
                else -> " · 未知操作"
            })
            BleDiagnosticEvent.AUTH_IDENTITY_PLACEHOLDERS -> {
                append(" · 空字段位=").append(code)
                append("（1=原账号为空，2=原设备为空；空占位不代表身份错误）")
            }
            BleDiagnosticEvent.CALIBRATION_SOURCE ->
                append(if (code == 1) " · 本机自定义" else " · 应用默认")
            BleDiagnosticEvent.CLOUD_SAVE_STARTED, BleDiagnosticEvent.CLOUD_SAVE_RESULT -> {
                append(if (code == 1) " · 标定" else " · 开关")
                if (event == BleDiagnosticEvent.CLOUD_SAVE_RESULT) {
                    append(if (detail == 1) " · 云端已保存（不代表车辆已应用）" else " · 未确认保存")
                }
            }
            BleDiagnosticEvent.AUTH_CERTIFICATE_STRUCTURE -> {
                append(" · 原字段数=").append(code).append(" · 身份匹配位=").append(detail)
                append("（1=原账号相同，2=原设备相同；仅字段比较，不代表证书有效性）")
            }
            BleDiagnosticEvent.AUTH_TEXT_STRUCTURE ->
                append(" · UTF8文本字节=").append(code).append(" · 签名字节=").append(detail)
            BleDiagnosticEvent.AUTH_CIPHER_STRUCTURE ->
                append(" · 加密前字节=").append(code).append(" · 密文字节=").append(detail)
            BleDiagnosticEvent.AUTH_CONFIGURATION -> {
                append(" · 协议版本=").append(code).append(" · 发送标志位=").append(detail)
                append("（按发送顺序，第1项为最低位）")
            }
            else -> Unit
        }
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

    fun recordAuthentication(structure: BleAuthenticationStructure) {
        record(BleDiagnosticEvent.AUTH_CERTIFICATE_STRUCTURE, structure.certificateFieldCount, structure.identityMatchMask)
        record(BleDiagnosticEvent.AUTH_TEXT_STRUCTURE, structure.textByteCount, structure.signatureByteCount)
        record(BleDiagnosticEvent.AUTH_CIPHER_STRUCTURE, structure.plaintextByteCount, structure.ciphertextByteCount)
        record(BleDiagnosticEvent.AUTH_CONFIGURATION, structure.protocolMinor, structure.flagsMask)
        if (structure.identityEmptyMask >= 0) {
            record(BleDiagnosticEvent.AUTH_IDENTITY_PLACEHOLDERS, structure.identityEmptyMask)
        }
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

internal fun targetMatchCode(address: String, expected: String?): Int = when {
    expected == null -> 0
    address.equals(expected, ignoreCase = true) -> 1
    else -> 2
}

private fun targetLabel(code: Int?): String = when (code) {
    1 -> "一致"
    2 -> "不一致"
    else -> "未知"
}
