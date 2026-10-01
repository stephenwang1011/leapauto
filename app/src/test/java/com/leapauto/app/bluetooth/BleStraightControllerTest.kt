package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher

class BleStraightControllerTest {

    private val certificate = BleKeyCertificate(
        ecdhPublicKey = "BHzyexiNA09+ilI4AwS1GsPAiWnid/IbNaYLSPxHZpl4B3dVENuO0EApPZrGn3Qw27p9reY86YIpngS3nSJ4c9E=",
        keyType = 0,
        passwordCard = "0123456789abcdef".repeat(5),
        plainText = "v1;old-account;old-device;TESTVIN0000000001;1700000000;1900000000",
        signResult = Base64.getEncoder().encodeToString(ByteArray(64) { 1 }),
        vin = "TESTVIN0000000001"
    )

    private fun testSession(): BleKeySession {
        val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(
            BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
            BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16)
        )
        val factory = KeyFactory.getInstance("EC")
        val pair = KeyPair(
            factory.generatePublic(ECPublicKeySpec(point, params)),
            factory.generatePrivate(ECPrivateKeySpec(BigInteger.ONE, params))
        )
        return BleKeyProtocol.createSession(certificate, certificate.vin, pair, 0x12345678L)
    }

    @Test
    fun `official cockpit authentication frame has exact 5-byte header 0xAA 0xAE 0x01 0x01 0x0A`() {
        testSession().use { session ->
            val authFrame = BleStraightProtocol.buildAuthenticationFrame(
                session = session,
                certificate = certificate,
                accountId = "ACCOUNT_123",
                deviceId = "DEVICE_456"
            )

            // 验证官方座舱认证帧前 5 字节固定为 AA AE 01 01 0A
            assertEquals(0xAA.toByte(), authFrame[0])
            assertEquals(0xAE.toByte(), authFrame[1])
            assertEquals(0x01.toByte(), authFrame[2])
            assertEquals(0x01.toByte(), authFrame[3])
            assertEquals(0x0A.toByte(), authFrame[4])

            // 验证会话 ID 位于第 7-10 字节 (4 字节小端序)
            val expectedSessionId = session.sessionId
            val actualSessionId = (authFrame[7].toLong() and 0xFF) or
                ((authFrame[8].toLong() and 0xFF) shl 8) or
                ((authFrame[9].toLong() and 0xFF) shl 16) or
                ((authFrame[10].toLong() and 0xFF) shl 24)
            assertEquals(expectedSessionId, actualSessionId)
        }
    }

    @Test
    fun `official cockpit control frame strictly matches single byte action without timestamp or crc`() {
        testSession().use { session ->
            val frame = BleStraightProtocol.buildControlFrame(session, BleStraightAction.FORWARD)
            // 帧头 0xAA 0xAB
            assertEquals(0xAA.toByte(), frame[0])
            assertEquals(0xAB.toByte(), frame[1])

            // 载荷长度
            val len = (frame[2].toInt() and 0xFF) or
                ((frame[3].toInt() and 0xFF) shl 8) or
                ((frame[4].toInt() and 0xFF) shl 16) or
                ((frame[5].toInt() and 0xFF) shl 24)
            assertEquals(frame.size - 6, len)

            // 解密载荷，确认明文纯粹为 1 字节动作码 1 (前进)，绝无门锁协议的时间戳与 CRC8 污染
            val payloadBytes = frame.drop(6).toByteArray()
            val ciphertext = Base64.getDecoder().decode(payloadBytes)
            val decrypted = session.crypt(ciphertext, Cipher.DECRYPT_MODE)
            assertEquals(1, decrypted.size)
            assertEquals(1.toByte(), decrypted[0])
        }
    }

    @Test
    fun `stop action produces code 3 for deadman safety latch`() {
        testSession().use { session ->
            val stopFrame = BleStraightProtocol.buildControlFrame(session, BleStraightAction.STOP)
            val payloadBytes = stopFrame.drop(6).toByteArray()
            val ciphertext = Base64.getDecoder().decode(payloadBytes)
            val decrypted = session.crypt(ciphertext, Cipher.DECRYPT_MODE)
            assertEquals(1, decrypted.size)
            assertEquals(3.toByte(), decrypted[0])
        }
    }

    @Test
    fun `cockpit notification frame decoding recognizes real-time ready state`() {
        testSession().use { session ->
            val stateText = "0;2;0;0;0;"
            val plainBytes = stateText.toByteArray(Charsets.UTF_8)
            val ciphertext = session.crypt(plainBytes, Cipher.ENCRYPT_MODE)
            val base64 = Base64.getEncoder().encode(ciphertext)

            val frameHeader = byteArrayOf(
                0xAA.toByte(), 0xAC.toByte(),
                0x01, 0x00, 0x00, // 业务码与保留位
                (base64.size and 0xFF).toByte(),
                ((base64.size shr 8) and 0xFF).toByte(),
                ((base64.size shr 16) and 0xFF).toByte(),
                ((base64.size shr 24) and 0xFF).toByte()
            )
            val fullFrame = frameHeader + base64

            val update = BleStraightProtocol.decodeVehicleNotification(session, fullFrame)
            assertNotNull(update)
            assertEquals(BleStraightVehicleState.READY, update!!.state)
        }
    }

    @Test
    fun `log entries preserve timestamp and error severity`() {
        val entry = BleStraightLogEntry(
            timestamp = "16:20:00.123",
            message = "测试日志",
            isError = true,
            isSuccess = false
        )
        assertEquals("16:20:00.123", entry.timestamp)
        assertEquals("测试日志", entry.message)
        assertTrue(entry.isError)
    }
}
