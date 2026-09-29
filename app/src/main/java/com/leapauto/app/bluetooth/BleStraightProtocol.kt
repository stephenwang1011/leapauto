package com.leapauto.app.bluetooth

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher

enum class BleStraightAction(val code: Int, val label: String) {
    FORWARD(1, "前进"),
    BACKWARD(2, "后退"),
    STOP(3, "停止"),
    RESUME(4, "恢复控制"),
    ACTIVATE(5, "激活")
}

enum class BleStraightVehicleState(val code: Int, val label: String) {
    WAITING(0, "车辆准备中"),
    ACTIVATE(1, "激活就绪中"),
    READY(2, "车辆就绪 (可控制)"),
    PAUSED(3, "已暂停 (需手动恢复)"),
    FAILED(4, "操作失败"),
    UNAVAILABLE(5, "车辆暂不可用 (请检查挡位/车门/踏板)")
}

data class BleStraightStateUpdate(
    val state: BleStraightVehicleState,
    val reasonCode: Int,
    val rawText: String
)

object BleStraightProtocol {
    // 独占授权实验白名单 VIN：仅对特定车辆开放直进直出入口，其他车辆一律隐藏不开放
    const val AUTHORIZED_VIN = "LFZ63AZ55SH023503"

    fun isAuthorized(vin: String?): Boolean =
        vin.orEmpty().trim().equals(AUTHORIZED_VIN, ignoreCase = true)

    val SERVICE_UUID: UUID = UUID.fromString("0000eeed-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000eee2-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val CHUNK_SIZE = 20

    /**
     * 构造直进直出认证帧 (严格对齐官方源码 j91.b 规范)
     * 帧头: 0xAA 0xAE 0x01 0x01 0x0A (5字节)
     * 长度: 2字节小端序 uint16 (payload.size + 69)
     * 会话ID: 4字节小端序 sessionId
     * 公钥材料: 65字节临时公钥
     * 加密载荷: Base64( SM4/AES_Encrypt([4字节明文长度 + 明文 + 签名]) )
     */
    /**
     * 构造直进直出认证帧 (严格复用经过实车验证的官方标准认证帧算法)
     */
    fun buildAuthenticationFrame(
        session: BleKeySession,
        certificate: BleKeyCertificate,
        accountId: String,
        deviceId: String,
        epochSeconds: Long = System.currentTimeMillis() / 1_000L
    ): ByteArray {
        return session.buildAuthentication(
            certificate = certificate,
            accountId = accountId,
            deviceId = deviceId,
            protocolMinor = BleKeyProtocol.LEAP3_PROTOCOL_MINOR,
            epochSeconds = epochSeconds,
            configuration = BlePassiveConfiguration.MANUAL.copy(calibration = BleCalibration.C16_DEFAULT),
            textProfile = BleAuthenticationTextProfile.ONE_PAO_V010,
            supportsButton = true
        )
    }

    /**
     * 构造直进直出控制帧 (严格对齐官方源码 xp.java:414 / a91.java:59 规范)
     * 明文为单字节动作状态字: 1=前进, 2=后退, 3=停止, 4=恢复控制
     * 帧头: 0xAA 0xAB + 4字节小端序长度 + Base64(Cipher(plain))
     */
    fun buildControlFrame(
        session: BleKeySession,
        action: BleStraightAction,
        epochSeconds: Long = System.currentTimeMillis() / 1_000L
    ): ByteArray {
        val plain = byteArrayOf(action.code.toByte())
        val ciphertext = session.crypt(plain, Cipher.ENCRYPT_MODE)
        val payload = Base64.getEncoder().encode(ciphertext)
        plain.fill(0)
        return byteArrayOf(0xAA.toByte(), 0xAB.toByte()) + littleEndian(payload.size.toLong(), 4) + payload
    }

    private fun littleEndian(value: Long, width: Int): ByteArray =
        ByteArray(width) { ((value ushr (it * 8)) and 0xFF).toByte() }

    /**
     * 将数据帧规整切分为 20 字节分片 (对齐官方源码 cq.java:369 规范)
     */
    fun chunkFrame(frame: ByteArray, chunkSize: Int = CHUNK_SIZE): List<ByteArray> {
        return frame.indices.step(chunkSize).map {
            frame.copyOfRange(it, minOf(it + chunkSize, frame.size))
        }
    }

    /**
     * 解析车端状态明文 (严格对齐官方源码 k60.O:442 规范)
     * 格式如: "0;2;0;0;0;"
     * numbers[1]: 状态字 (2或6=Ready, 1=Activate, 0=Waiting)
     * numbers[2]: unavailable 异常码
     * numbers[3]: pause 原因码
     * numbers[4]: failed 原因码
     */
    fun parseVehicleState(text: String): BleStraightStateUpdate? {
        val parts = text.split(';').filter { it.isNotBlank() }
        if (parts.size < 5) return null
        val numbers = parts.take(5).mapNotNull { it.trim().toIntOrNull() }
        if (numbers.size < 5 || numbers.any { it < 0 }) return null

        val statusInt = numbers[1]
        val failReason = numbers[4]
        val pauseReason = numbers[3]
        val unavailReason = numbers[2]

        return when {
            statusInt == 5 -> {
                if (unavailReason == 1) BleStraightStateUpdate(BleStraightVehicleState.ACTIVATE, 0, text)
                else BleStraightStateUpdate(BleStraightVehicleState.UNAVAILABLE, unavailReason, text)
            }
            failReason != 0 -> BleStraightStateUpdate(BleStraightVehicleState.FAILED, failReason, text)
            pauseReason != 0 -> BleStraightStateUpdate(BleStraightVehicleState.PAUSED, pauseReason, text)
            unavailReason != 0 -> BleStraightStateUpdate(BleStraightVehicleState.UNAVAILABLE, unavailReason, text)
            statusInt == 1 -> BleStraightStateUpdate(BleStraightVehicleState.WAITING, 0, text)
            statusInt == 2 || statusInt == 6 -> BleStraightStateUpdate(BleStraightVehicleState.READY, 0, text)
            else -> BleStraightStateUpdate(BleStraightVehicleState.WAITING, 0, text)
        }
    }

    /**
     * 解密车端 EEE2 接收通知帧
     * 官方格式: 0xAA 0xAC 业务码(1) 状态(2字节) 长度(4字节LE) + 密文载荷
     */
    fun decodeVehicleNotification(
        session: BleKeySession,
        frameBytes: ByteArray
    ): BleStraightStateUpdate? {
        if (frameBytes.size < 11) return null
        // 校验 0xAA 0xAC 头
        if (frameBytes[0] != 0xAA.toByte() || frameBytes[1] != 0xAC.toByte()) return null

        var payloadLen = 0L
        for (i in 0 until 4) {
            payloadLen = payloadLen or ((frameBytes[5 + i].toLong() and 0xFF) shl (i * 8))
        }
        val intLen = payloadLen.toInt()
        if (intLen <= 0 || frameBytes.size < 9 + intLen) return null

        val rawPayload = frameBytes.copyOfRange(9, 9 + intLen)
        val ciphertext = runCatching {
            val str = String(rawPayload, Charsets.US_ASCII).trim()
            Base64.getDecoder().decode(str)
        }.getOrElse { rawPayload }

        val plainBytes = runCatching {
            session.crypt(ciphertext, Cipher.DECRYPT_MODE)
        }.getOrNull() ?: return null

        val plainText = String(plainBytes, Charsets.UTF_8).trim()
        return parseVehicleState(plainText)
    }
}
