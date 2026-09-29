package com.leapauto.app.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

class BleStraightProtocolTest {

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
    fun `authentication frame begins with standard header and contains public key`() {
        testSession().use { session ->
            val frame = BleStraightProtocol.buildAuthenticationFrame(
                session = session,
                certificate = certificate,
                accountId = "acc-123",
                deviceId = "dev-456",
                epochSeconds = 1700000000L
            )
            // 官方座舱标准帧头校验 0xAA 0xAE 0x01 0x01 0x0A (5字节)
            assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xAE.toByte(), 0x01, 0x01, 0x0A), frame.copyOfRange(0, 5))
            // 长度在 5..6 (uint16 LE)
            val length = (frame[5].toInt() and 0xFF) or ((frame[6].toInt() and 0xFF) shl 8)
            assertEquals(frame.size - 7, length)
            // sessionId 在 7..10
            assertEquals(0x78.toByte(), frame[7])
            assertEquals(0x56.toByte(), frame[8])
            assertEquals(0x34.toByte(), frame[9])
            assertEquals(0x12.toByte(), frame[10])
            // 临时公钥在 11..75 (65字节)
            assertArrayEquals(session.publicKey, frame.copyOfRange(11, 76))
        }
    }

    @Test
    fun `control frame begins with 0xAA 0xAB and 4 byte length`() {
        testSession().use { session ->
            listOf(
                BleStraightAction.FORWARD,
                BleStraightAction.BACKWARD,
                BleStraightAction.STOP,
                BleStraightAction.RESUME
            ).forEach { action ->
                val frame = BleStraightProtocol.buildControlFrame(session, action)
                assertEquals(0xAA.toByte(), frame[0])
                assertEquals(0xAB.toByte(), frame[1])
                val len = (frame[2].toInt() and 0xFF) or
                    ((frame[3].toInt() and 0xFF) shl 8) or
                    ((frame[4].toInt() and 0xFF) shl 16) or
                    ((frame[5].toInt() and 0xFF) shl 24)
                assertEquals(frame.size - 6, len)
            }
        }
    }

    @Test
    fun `control frame encrypts single byte action code strictly matching official app spec`() {
        testSession().use { session ->
            listOf(
                BleStraightAction.FORWARD to 1,
                BleStraightAction.BACKWARD to 2,
                BleStraightAction.STOP to 3,
                BleStraightAction.RESUME to 4
            ).forEach { (action, expectedCode) ->
                val frame = BleStraightProtocol.buildControlFrame(session, action)
                assertEquals(0xAA.toByte(), frame[0])
                assertEquals(0xAB.toByte(), frame[1])
                val payloadBytes = frame.drop(6).toByteArray()
                val ciphertext = java.util.Base64.getDecoder().decode(payloadBytes)
                val decrypted = session.crypt(ciphertext, javax.crypto.Cipher.DECRYPT_MODE)
                // 单字节明文：解密后严格为 1 字节动作状态字，且绝不包含时间戳或 CRC8，杜绝被车端误认为车锁 UNLOCK
                assertEquals(1, decrypted.size)
                assertEquals(expectedCode.toByte(), decrypted[0])
            }
        }
    }

    @Test
    fun `chunking splits long frame into exact 20 byte slices`() {
        val data = ByteArray(45) { it.toByte() }
        val chunks = BleStraightProtocol.chunkFrame(data, 20)
        assertEquals(3, chunks.size)
        assertEquals(20, chunks[0].size)
        assertEquals(20, chunks[1].size)
        assertEquals(5, chunks[2].size)
    }

    @Test
    fun `parse vehicle state recognizes all standard response codes`() {
        // Ready: numbers[1] == 2
        val ready = BleStraightProtocol.parseVehicleState("0;2;0;0;0;")
        assertNotNull(ready)
        assertEquals(BleStraightVehicleState.READY, ready!!.state)

        // Waiting: numbers[1] == 1 (Activate) or 0 (Waiting)
        val waiting = BleStraightProtocol.parseVehicleState("0;0;0;0;0;")
        assertNotNull(waiting)
        assertEquals(BleStraightVehicleState.WAITING, waiting!!.state)

        // Paused: numbers[3] != 0
        val paused = BleStraightProtocol.parseVehicleState("0;2;0;3;0;")
        assertNotNull(paused)
        assertEquals(BleStraightVehicleState.PAUSED, paused!!.state)
        assertEquals(3, paused.reasonCode)

        // Failed: numbers[4] != 0
        val failed = BleStraightProtocol.parseVehicleState("0;2;0;0;5;")
        assertNotNull(failed)
        assertEquals(BleStraightVehicleState.FAILED, failed!!.state)
        assertEquals(5, failed.reasonCode)

        // Unavailable: numbers[2] != 0
        val unavail = BleStraightProtocol.parseVehicleState("0;2;4;0;0;")
        assertNotNull(unavail)
        assertEquals(BleStraightVehicleState.UNAVAILABLE, unavail!!.state)
        assertEquals(4, unavail.reasonCode)

        // 异常文本校验
        assertNull(BleStraightProtocol.parseVehicleState(""))
        assertNull(BleStraightProtocol.parseVehicleState("1;2;3;4"))
        assertNull(BleStraightProtocol.parseVehicleState("a;b;c;d;e"))
    }

    @Test
    fun `straight remote entry is available for all vehicles with valid vin`() {
        assertEquals("ALL_VEHICLES", BleStraightProtocol.AUTHORIZED_VIN)

        // 无论何种车型或 VIN 状态，直进直出均全面开放，永不隐藏
        assertTrue(BleStraightProtocol.isAuthorized("LFZ63AZ55SH023503"))
        assertTrue(BleStraightProtocol.isAuthorized("LFZ63AZ55SH000000"))
        assertTrue(BleStraightProtocol.isAuthorized("LFZ63AZ55SH999999"))
        assertTrue(BleStraightProtocol.isAuthorized("TESTVIN0000000001"))
        assertTrue(BleStraightProtocol.isAuthorized(""))
        assertTrue(BleStraightProtocol.isAuthorized("   "))
        assertTrue(BleStraightProtocol.isAuthorized(null))
    }

    @Test
    fun `verified straight activate and deactivate commands use cmdid 410 on3 payload`() {
        val activate = com.leapauto.app.Commands.build("straightActivate")
        assertEquals("410", activate.cmdid)
        assertEquals("""{"on3":"on"}""", activate.stateJson)
        assertEquals("激活直进直出", activate.label)
        assertEquals("正在激活直进直出...", com.leapauto.app.ControlFeedbackFormatter.inProgress("straightActivate", activate.label))
        assertEquals("直进直出已激活", com.leapauto.app.ControlFeedbackFormatter.success("straightActivate", activate.label))

        val deactivate = com.leapauto.app.Commands.build("straightDeactivate")
        assertEquals("410", deactivate.cmdid)
        assertEquals("""{"on3":"off"}""", deactivate.stateJson)
        assertEquals("退出直进直出", deactivate.label)
        assertEquals("正在退出直进直出...", com.leapauto.app.ControlFeedbackFormatter.inProgress("straightDeactivate", deactivate.label))
        assertEquals("直进直出已退出", com.leapauto.app.ControlFeedbackFormatter.success("straightDeactivate", deactivate.label))
    }
}
