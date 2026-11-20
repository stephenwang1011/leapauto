package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class CallCarProtocolTest {

    private val testVin = "LB377ACD123456789"

    @Test
    fun `matchesManufacturerData succeeds only when slice matches md5 of vin`() {
        val md5Hex = MessageDigest.getInstance("MD5")
            .digest(testVin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val hashSlice = md5Hex.substring(8, 16).toByteArray(Charsets.UTF_8)

        val validData = byteArrayOf(0x01, 0x02, 0x00, 0x00, 0x00, 0x00) + hashSlice + byteArrayOf(0x09, 0x0A)
        assertTrue(CallCarProtocol.matchesManufacturerData(validData, testVin))

        // Mismatched VIN
        assertFalse(CallCarProtocol.matchesManufacturerData(validData, "LB377ACD999999999"))

        // Corrupted slice byte
        val corrupted = validData.copyOf()
        corrupted[6] = (corrupted[6] + 1).toByte()
        assertFalse(CallCarProtocol.matchesManufacturerData(corrupted, testVin))

        // Too short data (< 14 bytes)
        assertFalse(CallCarProtocol.matchesManufacturerData(byteArrayOf(1, 2, 3), testVin))

        // Empty VIN
        assertFalse(CallCarProtocol.matchesManufacturerData(validData, ""))
    }

    @Test
    fun `CallCarPacketBuffer reassembles packets correctly across multiple chunks`() {
        val buffer = CallCarPacketBuffer()
        val payload = "test_payload_12345".toByteArray(Charsets.UTF_8)
        val len = payload.size

        // Build raw frame:
        // [0..1] 0xAA 0xAC
        // [2] 0x01
        // [3..4] type = 2 (0x02, 0x00)
        // [5..8] length (little-endian)
        // [9..9+len-1] payload
        // [9+len..10+len] checksum (2 bytes: 0x00, 0x00)
        val frame = byteArrayOf(
            0xAA.toByte(), 0xAC.toByte(),
            0x01,
            0x02, 0x00,
            (len and 0xFF).toByte(), ((len ushr 8) and 0xFF).toByte(), 0x00, 0x00
        ) + payload + byteArrayOf(0x55, 0xAA.toByte())

        // Split into 3 chunks and feed into buffer
        val chunk1 = frame.copyOfRange(0, 5)
        val chunk2 = frame.copyOfRange(5, 12)
        val chunk3 = frame.copyOfRange(12, frame.size)

        assertEquals(0, buffer.append(chunk1).size)
        assertEquals(0, buffer.append(chunk2).size)

        val packets = buffer.append(chunk3)
        assertEquals(1, packets.size)
        assertEquals(2, packets[0].type)
        assertEquals("test_payload_12345", String(packets[0].payload, Charsets.UTF_8))
    }

    @Test
    fun `CallCarStatus parses 5-part semicolon text accurately`() {
        // Waiting states
        val s0 = CallCarStatus.parse("0;0;0;0;0")
        assertNotNull(s0)
        assertEquals(CallCarStatus.Kind.Waiting, s0?.kind)

        val s1 = CallCarStatus.parse("0;1;0;0;0")
        assertNotNull(s1)
        assertEquals(CallCarStatus.Kind.Waiting, s1?.kind)

        // Activate handshake request
        val sActivate = CallCarStatus.parse("0;5;1;0;0")
        assertNotNull(sActivate)
        assertEquals(CallCarStatus.Kind.Activate, sActivate?.kind)

        // Ready state
        val sReady2 = CallCarStatus.parse("0;2;0;0;0")
        assertNotNull(sReady2)
        assertEquals(CallCarStatus.Kind.Ready, sReady2?.kind)

        val sReady6 = CallCarStatus.parse("0;6;0;0;0")
        assertNotNull(sReady6)
        assertEquals(CallCarStatus.Kind.Ready, sReady6?.kind)

        // Paused state
        val sPaused4 = CallCarStatus.parse("0;4;0;0;0")
        assertNotNull(sPaused4)
        assertEquals(CallCarStatus.Kind.Paused, sPaused4?.kind)

        // Obstacle detected pause (values[3] != 0)
        val sObstacle = CallCarStatus.parse("0;2;0;5;0")
        assertNotNull(sObstacle)
        assertEquals(CallCarStatus.Kind.Paused, sObstacle?.kind)
        assertEquals(5, sObstacle?.reason)
        assertEquals("周围存在障碍物，雷达触发避障", sObstacle?.reasonDescription())

        // Failed state (values[4] != 0)
        val sFailed = CallCarStatus.parse("0;2;0;0;1")
        assertNotNull(sFailed)
        assertEquals(CallCarStatus.Kind.Failed, sFailed?.kind)
        assertEquals(1, sFailed?.reason)
        assertEquals("车门或车窗未完全关闭", sFailed?.reasonDescription())

        // Invalid text formats
        assertNull(CallCarStatus.parse("invalid"))
        assertNull(CallCarStatus.parse("1;2;3"))
        assertNull(CallCarStatus.parse("a;b;c;d;e"))
    }

    @Test
    fun `StraightMoveDirection and StraightMoveOrder values are valid`() {
        assertEquals(1, StraightMoveDirection.FORWARD.orderCode)
        assertEquals(2, StraightMoveDirection.BACKWARD.orderCode)

        assertEquals(1, StraightMoveOrder.FORWARD.code)
        assertEquals(2, StraightMoveOrder.BACKWARD.code)
        assertEquals(3, StraightMoveOrder.STOP.code)
        assertEquals(4, StraightMoveOrder.RESUME.code)
        assertEquals(5, StraightMoveOrder.ACTIVATE.code)
        assertEquals(6, StraightMoveOrder.EXIT.code)
    }

    @Test
    fun `BleKeySession buildCallCarOrder produces valid AA AB frame`() {
        val cert = BleKeyCertificate(
            ecdhPublicKey = "BHzyexiNA09+ilI4AwS1GsPAiWnid/IbNaYLSPxHZpl4B3dVENuO0EApPZrGn3Qw27p9reY86YIpngS3nSJ4c9E=",
            keyType = 0,
            passwordCard = "A".repeat(80),
            plainText = "12345678;acc;dev;f3;f4;f5",
            signResult = "c2lnbmF0dXJl",
            vin = testVin
        )
        val keyPair = java.security.KeyPairGenerator.getInstance("EC").apply {
            initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        // Create session via internal derivation
        val session = BleKeyProtocol.createSession(cert, testVin, keyPair, 12345L)

        // Build order frame
        val orderFrame = session.buildCallCarOrder(StraightMoveOrder.FORWARD.code)
        assertEquals(0xAA.toByte(), orderFrame[0])
        assertEquals(0xAB.toByte(), orderFrame[1])
        assertTrue(orderFrame.size > 6)

        // Verify decodeCallCarPayload
        val payload = orderFrame.copyOfRange(6, orderFrame.size)
        val decrypted = session.decodeCallCarPayload(payload)
        assertNotNull(decrypted)
        assertEquals(1, decrypted?.toByteArray(Charsets.UTF_8)?.get(0)?.toInt())

        session.close()
    }
}
