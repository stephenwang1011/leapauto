package com.leapauto.app.bluetooth

import java.security.MessageDigest
import java.util.UUID

/**
 * 零跑座舱直进直出（遥控挪车）协议常量、数据包缓冲区与状态模型。
 *
 * 协议特点：
 * 1. 使用座舱专用 BLE Service (0xEEED) 与 Characteristic (0xEEE2)，独立于车锁服务 (0xFFFE)；
 * 2. 扫描时通过广播包内的 ManufacturerData 匹配 MD5(VIN) 的第 8~16 位，实现精准防串车识别；
 * 3. 复用蓝牙钥匙证书与动态会话加密，采用 5 段式座舱状态机流转。
 */
object CallCarProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000eeed-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000eee2-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * 校验广播包 ManufacturerData 是否匹配当前车辆 VIN。
     * 车机广播规范：前 2 字节为厂商 ID，后续数据第 6~14 字节为 MD5(VIN) 小写 32 字符的 [8..16) 截取。
     */
    fun matchesManufacturerData(data: ByteArray, vin: String): Boolean {
        if (data.size < 14 || vin.isBlank()) return false
        val expectedHash = MessageDigest.getInstance("MD5")
            .digest(vin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .substring(8, 16)
            .toByteArray(Charsets.UTF_8)
        val actualSlice = data.copyOfRange(6, 14)
        return actualSlice.contentEquals(expectedHash)
    }
}

/**
 * 直进直出移动方向。
 */
enum class StraightMoveDirection(val orderCode: Int, val label: String) {
    FORWARD(1, "向前直进"),
    BACKWARD(2, "向后直出");
}

/**
 * 直进直出控制指令代码 (Order)。
 */
enum class StraightMoveOrder(val code: Int) {
    FORWARD(1),
    BACKWARD(2),
    STOP(3),
    RESUME(4),
    ACTIVATE(5),
    EXIT(6);
}

/**
 * 直进直出协议回包。
 */
data class CallCarPacket(val type: Int, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CallCarPacket
        if (type != other.type) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * 直进直出数据帧重组缓冲区。
 *
 * 帧格式：
 * [0..1]  0xAA 0xAC (帧头)
 * [2]     0x01 (座舱业务模块标志)
 * [3..4]  Type (2字节小端)
 * [5..8]  Length (4字节小端)
 * [9..9+Length-1] Payload (密文数据)
 * [9+Length..10+Length] 校验/尾部 (2字节)
 */
class CallCarPacketBuffer {
    private var buffer = byteArrayOf()

    fun append(bytes: ByteArray): List<CallCarPacket> {
        buffer += bytes
        val packets = mutableListOf<CallCarPacket>()
        while (buffer.size >= 2) {
            if (buffer[0] != 0xAA.toByte() || buffer[1] != 0xAC.toByte()) {
                buffer = buffer.drop(1).toByteArray()
                continue
            }
            if (buffer.size < 9) break
            val length = (0..3).fold(0L) { value, index ->
                value or ((buffer[5 + index].toLong() and 0xFF) shl (index * 8))
            }
            if (length > 65536) {
                buffer = buffer.drop(2).toByteArray()
                continue
            }
            if (buffer.size < length + 11) break
            if (buffer[2] == 1.toByte()) {
                val type = (buffer[3].toInt() and 0xFF) or ((buffer[4].toInt() and 0xFF) shl 8)
                packets += CallCarPacket(type, buffer.copyOfRange(9, 9 + length.toInt()))
            }
            buffer = buffer.copyOfRange(11 + length.toInt(), buffer.size)
        }
        return packets
    }

    fun clear() {
        buffer = byteArrayOf()
    }
}

/**
 * 座舱直进直出状态机模型。
 *
 * 车机回包为分号分隔文本：`v0;v1;v2;v3;v4;...`
 * - v1 为主状态码；
 * - v2 为激活/不可用原因；
 * - v3 为暂停原因；
 * - v4 为失败原因。
 */
data class CallCarStatus(val kind: Kind, val reason: Int = 0) {
    enum class Kind {
        Waiting,
        Activate,
        Ready,
        Paused,
        Failed,
        Unavailable
    }

    fun reasonDescription(): String = when (reason) {
        0 -> "正常"
        1 -> "车门或车窗未完全关闭"
        2 -> "挡位不在P挡"
        3 -> "安全带未解开或座椅有人"
        4 -> "制动踏板或电门被踩下"
        5 -> "周围存在障碍物，雷达触发避障"
        6 -> "遥控距离超出安全范围"
        7 -> "电量过低或系统未上电"
        8 -> "坡度过大或路面不平整"
        else -> "车辆限制 (代码 $reason)"
    }

    companion object {
        fun parse(text: String): CallCarStatus? {
            val parts = text.split(';')
            if (parts.size < 5) return null
            val values = parts.take(5).map { it.toIntOrNull() ?: return null }
            if (values.any { it < 0 }) return null
            val status = values[1]
            return when {
                values.all { it == 0 } -> CallCarStatus(Kind.Waiting)
                status == 5 -> if (values[2] == 1) CallCarStatus(Kind.Activate) else CallCarStatus(Kind.Unavailable, values[2])
                values[4] != 0 -> CallCarStatus(Kind.Failed, values[4])
                values[3] != 0 -> CallCarStatus(Kind.Paused, values[3])
                values[2] != 0 -> CallCarStatus(Kind.Unavailable, values[2])
                status == 1 -> CallCarStatus(Kind.Waiting)
                status == 2 || status == 6 -> CallCarStatus(Kind.Ready)
                status == 4 -> CallCarStatus(Kind.Paused)
                else -> CallCarStatus(Kind.Failed, status)
            }
        }
    }
}
