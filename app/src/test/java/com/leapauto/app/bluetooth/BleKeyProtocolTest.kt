package com.leapauto.app.bluetooth

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
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BleKeyProtocolTest {
    private val vin = "TESTVIN0000000001"
    private val timestamp = 1_800_000_000L
    private val certificate = BleKeyCertificate(
        ecdhPublicKey = "BHzyexiNA09+ilI4AwS1GsPAiWnid/IbNaYLSPxHZpl4B3dVENuO0EApPZrGn3Qw27p9reY86YIpngS3nSJ4c9E=",
        keyType = 0,
        passwordCard = "0123456789abcdef".repeat(5),
        plainText = "v1;old-account;old-device;$vin;1700000000;1900000000",
        signResult = Base64.getEncoder().encodeToString(ByteArray(16) { (it + 1).toByte() }),
        vin = vin
    )

    // Independent Node/OpenSSL vectors: local scalar 1, peer scalar 2, session ID 0x78563412.
    @Test
    fun p256DerivationAndFullAuthenticationMatchIndependentLegacyVector() {
        session().use { session ->
            val frame = session.buildAuthentication(certificate, "test-account", "test-device", 8, timestamp)
            assertArrayEquals(Base64.getDecoder().decode(
                "qq7zABI0VngEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li/hp/m47n60p8D54WK84zV2sxXs7LtkBoN79R9WdWcnc4bDNkZWNuYVdBZGRCQ2g2TExUQkM1OVBSTVI4aUxJYmVEcms2WUFXQXZrdFJTT1BBWFZMWlRrWngyd2hNWmhzNFVRWWZqSHlJdWhucmJja2tpOHpZMFp3SWptWUVXdUNtRmVRa3NMdGlRZjZoR1dSTkhYV25GNzdENTJ1ZFVLamNBTVBnQ3luR3VQN2ZWY3JVZ1BWOVF5NTNXTk1lNzcrLzU1bnJ2Yz0BCQ=="
            ), frame)
            assertEquals(BleFrameType.AUTHENTICATION, BleFrameDecoder().append(frame).single().type)
        }
    }

    @Test
    fun fullAuthenticationMinorNineDisablesAllPassiveActions() {
        session().use { session ->
            val frame = session.buildAuthentication(certificate, "test-account", "test-device", 9, timestamp)
            assertArrayEquals(Base64.getDecoder().decode(
                "qq7zABI0VngEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li/hp/m47n60p8D54WK84zV2sxXs7LtkBoN79R9UVIN3BvWGIwbmZLMWJ1cUNnRkkyTG1hMjhVdzJWYUNGM3JGTEpFdFE2bWk2bDBSTURDTW9LajYxM3Y2bHh4dXYxRHhQMU1pa2pQUEVwaGFYOVl4elFUWGszb1JoNDE4R0NBRkRzQm9ER0pSRTdqZnA3ckNwSnVOU1lscTJxemZDbFYzMG9ReUJVOHdIYVFFVWVDQ2NsNnV5ZjY0WGd6WDBHQUUxRGZmR3FZUT0BCQ=="
            ), frame)
        }
    }

    @Test
    fun authenticationDiagnosticsPreserveFrameForEveryIdentityMatchCombination() {
        for (mask in 0..3) {
            val accountId = if (mask and 1 != 0) "old-account" else "new-account"
            val deviceId = if (mask and 2 != 0) "old-device" else "new-device"
            session().use { session ->
                val expected = session.buildAuthentication(certificate, accountId, deviceId, 9, timestamp)
                val summaries = mutableListOf<BleAuthenticationStructure>()
                val actual = session.buildAuthentication(certificate, accountId, deviceId, 9, timestamp,
                    onPrepared = { summaries += it })
                assertArrayEquals(expected, actual)
                assertEquals(1, summaries.size)
                assertEquals(mask, summaries.single().identityMatchMask)
                assertEquals(6, summaries.single().certificateFieldCount)
            }
        }
    }

    @Test
    fun authenticationDiagnosticsCountUtf8BytesSignatureAndTrailingEmptyFields() {
        val certificate = certificate.copy(
            plainText = "v1;old-account;old-device;$vin;1700000000;1900000000;;",
            signResult = Base64.getEncoder().encodeToString(ByteArray(37) { (it + 1).toByte() })
        )
        val accountId = "new-account-\u8f66"
        val deviceId = "new-device-\u5319"
        val expectedText = "$timestamp;v1;$accountId;$deviceId;$vin;1700000000;1900000000;;;56;2.00;08;16;1;0;0;0;"
        val diagnostics = BleDiagnostics { 0L }
        session(certificate).use { session ->
            val summaries = mutableListOf<BleAuthenticationStructure>()
            val frame = session.buildAuthentication(certificate, accountId, deviceId, 9, timestamp, onPrepared = {
                summaries += it
                diagnostics.recordAuthentication(it)
            })
            val summary = summaries.single()
            val ciphertext = Base64.getDecoder().decode(frame.copyOfRange(73, frame.size - 2))
            assertEquals(8, summary.certificateFieldCount)
            assertEquals(0, summary.identityMatchMask)
            assertEquals(expectedText.toByteArray(Charsets.UTF_8).size, summary.textByteCount)
            assertTrue(summary.textByteCount > expectedText.length)
            assertEquals(37, summary.signatureByteCount)
            assertEquals(summary.textByteCount + 4 + 37, summary.plaintextByteCount)
            assertEquals((summary.plaintextByteCount / 16 + 1) * 16, summary.ciphertextByteCount)
            assertEquals(ciphertext.size, summary.ciphertextByteCount)
            assertEquals(9, summary.protocolMinor)
            assertEquals(1, summary.flagsMask)
            assertArrayEquals(session.buildAuthentication(certificate, accountId, deviceId, 9, timestamp), frame)
            val report = BleDiagnostics.formatReport(diagnostics.snapshot(), "3.3.52", BleConnectionPhase.AUTHENTICATING)
            for (secret in listOf(certificate.ecdhPublicKey, certificate.passwordCard, certificate.plainText,
                certificate.signResult, certificate.vin, accountId, deviceId, "old-account", "old-device",
                BleKeyProtocol.certificateFingerprint(certificate), Base64.getEncoder().encodeToString(frame))) {
                assertFalse(report.contains(secret))
                assertFalse(summary.toString().contains(secret))
            }
        }
    }

    @Test
    fun authenticationDiagnosticsDescribeFlagsInTheirActualWireOrder() {
        for (minor in listOf(8, 9)) {
            for (configuration in listOf(BlePassiveConfiguration(), BlePassiveConfiguration.MANUAL,
                BlePassiveConfiguration(enabled = true, autoUnlock = true, autoLock = true),
                BlePassiveConfiguration(enabled = true, autoLock = true))) {
                session().use { session ->
                    val summaries = mutableListOf<BleAuthenticationStructure>()
                    val frame = session.buildAuthentication(certificate, "test-account", "test-device", minor,
                        timestamp, configuration, onPrepared = { summaries += it })
                    val expectedMask = when {
                        !configuration.enabled -> 0
                        minor == 8 -> (if (configuration.autoUnlock) 1 else 0) or
                            (if (configuration.autoLock) 2 else 0)
                        else -> 1 or (if (configuration.autoLock) 2 else 0) or
                            (if (configuration.autoUnlock) 4 else 0)
                    }
                    assertEquals(expectedMask, summaries.single().flagsMask)
                    assertEquals(minor, summaries.single().protocolMinor)
                    assertArrayEquals(session.buildAuthentication(certificate, "test-account", "test-device", minor,
                        timestamp, configuration), frame)
                }
            }
        }
        session().use { session ->
            val summaries = mutableListOf<BleAuthenticationStructure>()
            session.buildAuthentication(certificate, "test-account", "test-device", 9, timestamp,
                BlePassiveConfiguration(true, true, true, true), onPrepared = { summaries += it })
            assertEquals(15, summaries.single().flagsMask)
        }
    }

    @Test
    fun rejectedAuthenticationDoesNotEmitPreparedDiagnostics() {
        for (invalid in listOf(certificate.copy(plainText = "a;b;c"), certificate.copy(signResult = "not%base64"))) {
            val summaries = mutableListOf<BleAuthenticationStructure>()
            session(invalid).use { session ->
                assertThrows(IllegalArgumentException::class.java) {
                    session.buildAuthentication(invalid, "account", "device", 9, timestamp,
                        onPrepared = { summaries += it })
                }
                assertTrue(summaries.isEmpty())
            }
        }
    }

    @Test
    fun lockAndUnlockCommandsMatchIndependentCrcAndEncryptionVectors() {
        session().use { session ->
            assertArrayEquals(hex("aaab180000005578394f32343073464c6f41496e704d3172506e5a413d3d"),
                session.buildCommand(BleLockAction.UNLOCK, timestamp))
            assertArrayEquals(hex("aaab18000000774465434e5443727a6d3430706a4e41534b666d54413d3d"),
                session.buildCommand(BleLockAction.LOCK, timestamp))
        }
        assertEquals(0xF4, BleKeyProtocol.crc8("123456789".toByteArray()))
    }

    @Test
    fun credentialVinAndCurveFailuresCannotProduceAuthentication() {
        assertThrows(IllegalArgumentException::class.java) { BleKeyProtocol.createSession(certificate, "OTHER000000000001") }
        assertThrows(IllegalArgumentException::class.java) {
            BleKeyProtocol.createSession(certificate.copy(keyType = 1), vin)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleKeyProtocol.createSession(certificate.copy(ecdhPublicKey = "invalid%%%"), vin)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleKeyProtocol.createSession(certificate.copy(ecdhPublicKey = Base64.getEncoder()
                .encodeToString(byteArrayOf(4) + ByteArray(64))), vin)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleKeyProtocol.createSession(certificate.copy(ecdhPublicKey = Base64.getEncoder()
                .encodeToString(byteArrayOf(4) + ByteArray(64) { 0xFF.toByte() })), vin)
        }
    }

    @Test
    fun certificateSignatureAndIdentifierValidationRejectMalformedAuth() {
        session().use { session ->
            assertThrows(IllegalArgumentException::class.java) {
                session.buildAuthentication(certificate, "bad;account", "device", 9, timestamp)
            }
            assertThrows(IllegalArgumentException::class.java) {
                session.buildAuthentication(certificate, "account", "", 9, timestamp)
            }
            assertThrows(IllegalArgumentException::class.java) {
                session.buildAuthentication(certificate, "account", "device", 256, timestamp)
            }
            assertThrows(IllegalArgumentException::class.java) { session.buildCommand(BleLockAction.LOCK, -1) }
        }
        for (invalid in listOf(certificate.copy(plainText = "a;b;c"), certificate.copy(signResult = "not%base64"))) {
            session(invalid).use { session ->
                assertThrows(IllegalArgumentException::class.java) {
                    session.buildAuthentication(invalid, "account", "device", 9, timestamp)
                }
            }
        }
    }

    @Test
    fun authenticationCannotSwitchCertificateAfterSessionDerivation() {
        session().use { session ->
            for (changed in listOf(certificate.copy(vin = "OTHER000000000001"),
                certificate.copy(passwordCard = "f".repeat(80)), certificate.copy(signResult = "YWJj"))) {
                assertThrows(IllegalArgumentException::class.java) {
                    session.buildAuthentication(changed, "account", "device", 9, timestamp)
                }
            }
        }
    }

    @Test
    fun oversizedCertificateCannotProduceTruncatedLength() {
        val oversized = certificate.copy(plainText = certificate.plainText + ";" + "x".repeat(70_000))
        session(oversized).use { session ->
            assertThrows(IllegalArgumentException::class.java) {
                session.buildAuthentication(oversized, "account", "device", 9, timestamp)
            }
        }
    }

    @Test
    fun closedSessionCannotEncryptOrDecrypt() {
        val session = session()
        session.close()
        assertThrows(IllegalStateException::class.java) { session.buildCommand(BleLockAction.LOCK, timestamp) }
        assertThrows(IllegalStateException::class.java) {
            session.decodeResponse(frame("aaab180000004676454a4669644f47712f505a72663852356a4e61673d3d"))
        }
    }

    @Test
    fun authenticationResponsePreservesUnknownLockStateInsteadOfInventingMapping() {
        session().use { session ->
            val response = session.decodeResponse(frame("aaab180000004676454a4669644f47712f505a72663852356a4e61673d3d"))
            assertTrue(response is BleResponse.Authenticated)
            assertEquals("locked", (response as BleResponse.Authenticated).lockState)
        }
    }

    @Test
    fun commandRepliesAndReconnectCredentialsAreNotAuthenticationOrActionConfirmation() {
        session().use { session ->
            val response = session.decodeResponse(frame("aaab18000000774178485a7242433552374732574954336a795554513d3d"))
            assertTrue(response is BleResponse.CommandResult)
            assertEquals("3", (response as BleResponse.CommandResult).identifier)
            assertEquals("00", response.result)
            assertEquals(BleResponse.ReconnectCredential,
                session.decodeResponse(frame("aaab18000000654d3438483268744f75494743562f474666707469513d3d")))
        }
    }

    @Test
    fun emptyAndUnknownPlaintextAreNotAuthentication() {
        session().use { session ->
            assertEquals(BleResponse.Unknown,
                session.decodeResponse(frame("aaab1800000077457055357547556361644d56764c564a4930434b773d3d")))
            assertEquals(BleResponse.Unknown,
                session.decodeResponse(frame("aaab180000004778656b4273347857725170686a454c4639567549513d3d")))
        }
    }

    @Test
    fun badBase64WrongBlockSizeAndUnencryptedFramesAreRejected() {
        session().use { session ->
            assertThrows(IllegalArgumentException::class.java) { session.decodeResponse(frame("aaab0100000025")) }
            assertThrows(Exception::class.java) { session.decodeResponse(frame("aaab0400000059513d3d")) }
            assertThrows(IllegalArgumentException::class.java) { session.decodeResponse(event(2, 0, 1, 1)) }
        }
    }

    @Test
    fun fragmentsCoalescedFramesAndHeaderNoiseAreReassembled() {
        val first = hex("aaab180000004676454a4669644f47712f505a72663852356a4e61673d3d")
        val second = hex("aaac00000002030000007c0101")
        val decoder = BleFrameDecoder()
        assertTrue(decoder.append(hex("1122aa99") + first.copyOfRange(0, 1)).isEmpty())
        assertTrue(decoder.append(first.copyOfRange(1, 9)).isEmpty())
        val received = decoder.append(first.copyOfRange(9, first.size) + second)
        assertEquals(listOf(BleFrameType.ENCRYPTED, BleFrameType.EVENT), received.map { it.type })
        assertArrayEquals(first.copyOfRange(6, first.size), received[0].payload)
        assertArrayEquals(hex("7c0101"), received[1].payload)
    }

    @Test
    fun allHeaderFormatsUseTheirExactLengthWidths() {
        val decoder = BleFrameDecoder()
        val frames = decoder.append(hex("aaae0100ff") + hex("aaee0200abcd") + hex("aaac000000010100000003"))
        assertEquals(3, frames.size)
        assertEquals(BleFrameType.AUTHENTICATION, frames[0].type)
        assertEquals(BleFrameType.RECONNECT, frames[1].type)
        assertArrayEquals(hex("abcd"), frames[1].payload)
        assertEquals(3, (BleKeyProtocol.parseEvent(frames[2]) as BleEvent.Rejected).resultCode)
    }

    @Test
    fun clearDiscardsPriorConnectionPartialFrame() {
        val decoder = BleFrameDecoder()
        assertTrue(decoder.append(hex("aaab18000000")).isEmpty())
        decoder.clear()
        assertEquals(1, decoder.append(hex("aaac00000002030000007c0101")).size)
    }

    @Test
    fun corruptLengthCannotAllocateUnboundedBufferOrHideFollowingFrame() {
        val decoder = BleFrameDecoder(64)
        val valid = hex("aaac00000002030000007c0101")
        assertEquals(1, decoder.append(hex("aaabffffffff") + valid).size)
        assertThrows(IllegalArgumentException::class.java) { decoder.append(ByteArray(65)) }
    }

    @Test
    fun onlyActiveMatchingActionConfirmsManualControl() {
        val unlock = BleKeyProtocol.parseEvent(event(2, 124, 1, 1)) as BleEvent.LockAction
        assertEquals(124, unlock.crc8)
        assertTrue(unlock.confirms(BleLockAction.UNLOCK))
        assertFalse(unlock.confirms(BleLockAction.LOCK))
        assertTrue((BleKeyProtocol.parseEvent(event(2, 117, 1, 2)) as BleEvent.LockAction).confirms(BleLockAction.LOCK))
        for (trigger in listOf(0, 2, 3, 255)) {
            val passive = BleKeyProtocol.parseEvent(event(2, 124, trigger, 1)) as BleEvent.LockAction
            assertFalse(passive.confirms(BleLockAction.UNLOCK))
        }
        assertFalse((BleKeyProtocol.parseEvent(event(2, 124, 1, 255)) as BleEvent.LockAction).confirms(BleLockAction.UNLOCK))
    }

    @Test
    fun rejectionUsesLastPayloadByteAndTruncatedEventsNeverConfirm() {
        assertEquals(7, (BleKeyProtocol.parseEvent(event(1, 0, 7)) as BleEvent.Rejected).resultCode)
        assertEquals(BleEvent.Unknown, BleKeyProtocol.parseEvent(event(1)))
        assertEquals(BleEvent.Unknown, BleKeyProtocol.parseEvent(event(2, 124, 1)))
        assertEquals(BleEvent.Unknown, BleKeyProtocol.parseEvent(event(3, 124, 1)))
    }

    @Test
    fun scanProtocolMinorAndMtuChunkingMatchStaticContract() {
        assertEquals(8, BleKeyProtocol.protocolMinor(emptyList()))
        assertEquals(null, BleKeyProtocol.advertisedProtocolMinor(emptyList()))
        assertEquals(null, BleKeyProtocol.advertisedProtocolMinor(listOf(BleKeyProtocol.SERVICE_UUID)))
        assertEquals(8, BleKeyProtocol.advertisedProtocolMinor(listOf(
            UUID.fromString("00000108-0000-1000-8000-00805f9b34fb"))))
        assertEquals(255, BleKeyProtocol.advertisedProtocolMinor(listOf(
            UUID.fromString("000001ff-0000-1000-8000-00805f9b34fb"))))
        assertEquals(null, BleKeyProtocol.advertisedProtocolMinor(listOf(
            UUID.fromString("00000109-0000-1000-8000-00805f9b34fc"))))
        assertEquals(9, BleKeyProtocol.protocolMinor(listOf(BleKeyProtocol.SERVICE_UUID,
            UUID.fromString("00000109-0000-1000-8000-00805f9b34fb"))))
        assertEquals(20, BleKeyProtocol.chunkLimit(23))
        assertEquals(20, BleKeyProtocol.chunkLimit(0))
        assertEquals(160, BleKeyProtocol.chunkLimit(200))
        assertEquals(160, BleKeyProtocol.chunkLimit(Int.MAX_VALUE))
        assertEquals(listOf(20, 20, 1), BleKeyProtocol.chunks(ByteArray(41), 23).map { it.size })
        assertEquals(listOf(160, 1), BleKeyProtocol.chunks(ByteArray(161), 200).map { it.size })
        assertTrue(BleKeyProtocol.chunks(byteArrayOf(), 23).isEmpty())
    }

    private fun session(certificate: BleKeyCertificate = this.certificate): BleKeySession {
        val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(
            BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
            BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16)
        )
        val factory = KeyFactory.getInstance("EC")
        val pair = KeyPair(factory.generatePublic(ECPublicKeySpec(point, params)),
            factory.generatePrivate(ECPrivateKeySpec(BigInteger.ONE, params)))
        return BleKeyProtocol.createSession(certificate, vin, pair, 0x78563412)
    }

    private fun event(type: Int, vararg payload: Int): BleFrame {
        val raw = byteArrayOf(0xAA.toByte(), 0xAC.toByte(), 0, 0, 0, type.toByte(), payload.size.toByte(), 0, 0, 0) +
            payload.map(Int::toByte).toByteArray()
        return BleFrameDecoder().append(raw).single()
    }

    private fun frame(value: String): BleFrame = BleFrameDecoder().append(hex(value)).single()

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
