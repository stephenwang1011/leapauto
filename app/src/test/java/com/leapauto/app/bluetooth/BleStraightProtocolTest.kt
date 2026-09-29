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
            // 官方标准帧头校验 0xAA 0xAE
            assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xAE.toByte()), frame.copyOfRange(0, 2))
            // 长度在 2..3 (uint16 LE)
            val length = (frame[2].toInt() and 0xFF) or ((frame[3].toInt() and 0xFF) shl 8)
            assertEquals(frame.size - 4, length)
            // sessionId 在 4..7
            assertEquals(0x78.toByte(), frame[4])
            assertEquals(0x56.toByte(), frame[5])
            assertEquals(0x34.toByte(), frame[6])
            assertEquals(0x12.toByte(), frame[7])
            // 临时公钥在 8..72 (65字节)
            assertArrayEquals(session.publicKey, frame.copyOfRange(8, 73))
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
    fun `straight remote entry is exclusively authorized for vin LFZ63AZ55SH023503`() {
        assertEquals("LFZ63AZ55SH023503", BleStraightProtocol.AUTHORIZED_VIN)

        // 授权白名单通过
        assertTrue(BleStraightProtocol.isAuthorized("LFZ63AZ55SH023503"))
        assertTrue(BleStraightProtocol.isAuthorized("lfz63az55sh023503"))
        assertTrue(BleStraightProtocol.isAuthorized("  LFZ63AZ55SH023503  "))

        // 其他任何车辆严格拒绝与隐藏
        org.junit.Assert.assertFalse(BleStraightProtocol.isAuthorized("LFZ63AZ55SH000000"))
        org.junit.Assert.assertFalse(BleStraightProtocol.isAuthorized("LFZ63AZ55SH999999"))
        org.junit.Assert.assertFalse(BleStraightProtocol.isAuthorized("TESTVIN0000000001"))
        org.junit.Assert.assertFalse(BleStraightProtocol.isAuthorized(""))
        org.junit.Assert.assertFalse(BleStraightProtocol.isAuthorized("   "))
        org.junit.Assert.assertFalse(BleStraightProtocol.isAuthorized(null))
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
