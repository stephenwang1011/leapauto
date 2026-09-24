package com.leapauto.app.bluetooth

import java.math.BigInteger
import java.nio.charset.CharacterCodingException
import java.security.Security
import java.util.Base64
import java.util.Date
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.gm.GMObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BleSm2ProtocolTest {
    private val vin = "TESTVIN0000000001"
    private val timestamp = 1_800_000_000L
    private val peerPoint = hex("0456cefd60d7c87c000d58ef57fa73ba4d9c0dfa08c08a7331495c2e1da3f2bd523" +
        "1b7e7e6cc8189f668535ce0f8eaf1bd6de84c182f6c8e716f780d3a970a23c3")
    private val localPoint = hex("0432c4ae2c1f1981195f9904466a39c9948fe30bbff2660be1715a4589334c74c7" +
        "bc3736a2f4f6779c59bdcee36b692153d0a9877cc62a474002df32e52139f0a0")

    @Test
    fun sm3MatchesStandardDigest() {
        assertArrayEquals(hex("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0"),
            BleSm2Crypto.hash("abc".toByteArray()))
    }

    @Test
    fun agreementMatchesIndependentOpenSslPointAndKdfVector() {
        BleSm2Crypto.agree(certificate().ecdhPublicKey, BigInteger.ONE).use { result ->
            assertArrayEquals(localPoint, result.publicKey)
            assertArrayEquals(hex("5f86e6b17708f60dfb5d8e3f3ce2b35d3d29744d51c46fc14cdb7bb95fdaf48b"), result.sharedSecret)
        }
    }

    @Test
    fun agreementBuffersAreClearedOnClose() {
        val agreement = BleSm2Crypto.agree(certificate().ecdhPublicKey, BigInteger.ONE)
        agreement.close()
        assertTrue(agreement.publicKey.all { it == 0.toByte() })
        assertTrue(agreement.sharedSecret.all { it == 0.toByte() })
    }

    @Test
    fun certificateEmptyPlaceholdersAreReportedBeforeIdentityReplacement() {
        for ((text, expected) in listOf(";;;001;1234567890000;1" to 3,
                ";;device;001;1234567890000;1" to 1, ";account;;001;1234567890000;1" to 2)) {
            val certificate = certificate().copy(plainText = text)
            session(certificate).use { session ->
                var structure: BleAuthenticationStructure? = null
                session.buildAuthentication(certificate, "test-account", "test-device", 9, timestamp,
                    onPrepared = { structure = it })
                assertEquals(expected, requireNotNull(structure).identityEmptyMask)
                assertEquals(0, requireNotNull(structure).identityMatchMask)
            }
        }
    }

    // Expected frames come from test/resources/bluetooth/generate-sm2-vectors.cjs (Node/OpenSSL).
    @Test
    fun sm2FullAuthenticationMinorEightMatchesIndependentFrame() {
        val certificate = certificate()
        session(certificate).use { session ->
            val summaries = mutableListOf<BleAuthenticationStructure>()
            assertArrayEquals(Base64.getDecoder().decode(AUTHENTICATION_8),
                session.buildAuthentication(certificate, "test-account", "test-device", 8, timestamp,
                    onPrepared = { summaries += it }))
            assertEquals(BleAuthenticationStructure(6, 0, 97, 16, 117, 128, 8, 0, 0), summaries.single())
        }
    }

    @Test
    fun sm2FullAuthenticationMinorNineKeepsPassiveActionsDisabled() {
        val certificate = certificate()
        session(certificate).use { session ->
            val summaries = mutableListOf<BleAuthenticationStructure>()
            assertArrayEquals(Base64.getDecoder().decode(AUTHENTICATION_9),
                session.buildAuthentication(certificate, "test-account", "test-device", 9, timestamp,
                    onPrepared = { summaries += it }))
            assertEquals(BleAuthenticationStructure(6, 0, 101, 16, 121, 128, 9, 1, 0), summaries.single())
        }
    }

    @Test
    fun sm2LockAndUnlockMatchIndependentSm4Frames() {
        session().use { session ->
            assertArrayEquals(hex("aaab1800000065447542464750772f3477547866484335386b3336773d3d"),
                session.buildCommand(BleLockAction.UNLOCK, timestamp))
            assertArrayEquals(hex("aaab18000000594c565a4144765a4155776d6433354a394855584e773d3d"),
                session.buildCommand(BleLockAction.LOCK, timestamp))
        }
    }

    @Test
    fun pemAndWhitespaceBase64CertificateProduceSameAuthentication() {
        val base = certificate()
        for (encoded in listOf(base.ecdhPublicKey.chunked(48).joinToString("\n"),
            "-----BEGIN CERTIFICATE-----\r\n${base.ecdhPublicKey.chunked(64).joinToString("\r\n")}\r\n-----END CERTIFICATE-----")) {
            val wrapped = base.copy(ecdhPublicKey = encoded)
            session(wrapped).use { session ->
                assertArrayEquals(Base64.getDecoder().decode(AUTHENTICATION_9),
                    session.buildAuthentication(wrapped, "test-account", "test-device", 9, timestamp))
            }
        }
    }

    @Test
    fun compressedPointIsAcceptedOnDeclaredSm2Curve() {
        val compressed = byteArrayOf(3) + peerPoint.copyOfRange(1, 33)
        val certificate = certificate(derCertificate(point = compressed))
        session(certificate).use { session ->
            assertArrayEquals(Base64.getDecoder().decode(AUTHENTICATION_9),
                session.buildAuthentication(certificate, "test-account", "test-device", 9, timestamp))
        }
    }

    @Test
    fun realSessionFactorySupportsSm2AndCreatesFreshEphemeralKeys() {
        val certificate = certificate()
        BleKeyProtocol.createSession(certificate, vin).use { first ->
            BleKeyProtocol.createSession(certificate, vin).use { second ->
                val one = first.buildAuthentication(certificate, "test-account", "test-device", 9, timestamp)
                val two = second.buildAuthentication(certificate, "test-account", "test-device", 9, timestamp)
                assertFalse(one.copyOfRange(4, 73).contentEquals(two.copyOfRange(4, 73)))
                assertEquals(BleFrameType.AUTHENTICATION, BleFrameDecoder().append(one).single().type)
            }
        }
    }

    @Test
    fun malformedCertificatesAndPublicKeyContainersAreRejected() {
        val base = certificate()
        val spki = SubjectPublicKeyInfo(sm2Algorithm(), peerPoint).encoded
        val invalid = listOf("not%base64", "-----BEGIN CERTIFICATE-----\n${base.ecdhPublicKey}",
            "-----BEGIN PUBLIC KEY-----\n${base.ecdhPublicKey}\n-----END PUBLIC KEY-----",
            base.ecdhPublicKey + "%", Base64.getEncoder().encodeToString(peerPoint),
            Base64.getEncoder().encodeToString(spki), Base64.getEncoder().encodeToString(derCertificate() + byteArrayOf(0)),
            Base64.getEncoder().encodeToString(derCertificate().copyOf(20)), "A".repeat(131_073))
        for (encoded in invalid) {
            assertThrows(IllegalArgumentException::class.java) { session(base.copy(ecdhPublicKey = encoded)) }
        }
    }

    @Test
    fun wrongAlgorithmCurveAndMalformedPointCannotNegotiate() {
        val wrongCurve = AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey,
            ASN1ObjectIdentifier("1.2.840.10045.3.1.7"))
        val noCurve = AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey)
        val wrongAlgorithm = AlgorithmIdentifier(ASN1ObjectIdentifier("1.2.840.113549.1.1.1"))
        for (algorithm in listOf(wrongCurve, noCurve, wrongAlgorithm)) {
            assertThrows(IllegalArgumentException::class.java) { session(certificate(derCertificate(algorithm = algorithm))) }
        }
        val p256Point = hex("046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
            "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
        for (point in listOf(byteArrayOf(0), byteArrayOf(4) + ByteArray(64), p256Point,
            byteArrayOf(4) + ByteArray(64) { 0xFF.toByte() }, byteArrayOf(6) + peerPoint.copyOfRange(1, 65))) {
            assertThrows(IllegalArgumentException::class.java) { session(certificate(derCertificate(point = point))) }
        }
    }

    @Test
    fun invalidPrivateScalarAndSessionIdAreRejected() {
        val certificate = certificate()
        for (scalar in listOf(BigInteger.ZERO, BigInteger.valueOf(-1),
            BigInteger("fffffffeffffffffffffffffffffffff7203df6b21c6052b53bbf40939d54123", 16))) {
            assertThrows(IllegalArgumentException::class.java) {
                BleKeyProtocol.createSm2Session(certificate, vin, scalar, 0x78563412)
            }
        }
        for (identifier in listOf(-1L, 0x100000000L)) {
            assertThrows(IllegalArgumentException::class.java) {
                BleKeyProtocol.createSm2Session(certificate, vin, BigInteger.ONE, identifier)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleKeyProtocol.createSm2Session(certificate.copy(keyType = 0), vin, BigInteger.ONE, 0x78563412)
        }
    }

    @Test
    fun sm2CannotChangeVehicleOrCertificateDuringAuthentication() {
        val certificate = certificate()
        assertThrows(IllegalArgumentException::class.java) { BleKeyProtocol.createSession(certificate, "OTHER000000000001") }
        session(certificate).use { session ->
            for (changed in listOf(certificate.copy(vin = "OTHER000000000001"), certificate.copy(keyType = 0),
                certificate.copy(passwordCard = "f".repeat(80)), certificate.copy(signResult = "YWJj"))) {
                assertThrows(IllegalArgumentException::class.java) {
                    session.buildAuthentication(changed, "test-account", "test-device", 9, timestamp)
                }
            }
        }
    }

    @Test
    fun sm2ResponseDecodeRequiresValidCiphertextAndKnownText() {
        session().use { session ->
            val authenticated = session.decodeResponse(frame("aaab180000005a6a6a2b4c4b686d627a487135704b74454d643633673d3d"))
            assertEquals("locked", (authenticated as BleResponse.Authenticated).lockState)
            val command = session.decodeResponse(frame("aaab18000000574e444634653936644c36785a554e69672b634131513d3d"))
            assertEquals("00", (command as BleResponse.CommandResult).result)
            assertEquals(BleResponse.Unknown,
                session.decodeResponse(frame("aaab18000000726e6b436878633845583564777443465767634e52673d3d")))
            assertTrue(session.decodeResponse(
                frame("aaab18000000523555335a59674c6961396c477564472b577a3070773d3d")) is
                BleResponse.ReconnectCredential)
            assertThrows(CharacterCodingException::class.java) {
                session.decodeResponse(frame("aaab1800000042366f694b4c7473546a6744764231564770684141773d3d"))
            }
            assertThrows(IllegalArgumentException::class.java) { session.decodeResponse(frame("aaab0400000059513d3d")) }
        }
    }

    @Test
    fun invalidSm4PaddingAndKeyLengthsAreRejected() {
        val key = "6fa340d39f4141fe".toByteArray()
        val iv = "437410132db0dd25".toByteArray()
        assertThrows(IllegalArgumentException::class.java) { BleSm2Crypto.crypt(ByteArray(16), key, iv, false) }
        assertThrows(IllegalArgumentException::class.java) { BleSm2Crypto.crypt(byteArrayOf(1), key, iv, false) }
        assertThrows(IllegalArgumentException::class.java) { BleSm2Crypto.crypt(byteArrayOf(1), ByteArray(15), iv, true) }
        assertThrows(IllegalArgumentException::class.java) { BleSm2Crypto.crypt(byteArrayOf(1), key, ByteArray(15), true) }
    }

    @Test
    fun closedSm2SessionRejectsEncryptionAndDecryption() {
        val session = session()
        session.close()
        assertThrows(IllegalStateException::class.java) { session.buildCommand(BleLockAction.LOCK, timestamp) }
        assertThrows(IllegalStateException::class.java) {
            session.decodeResponse(frame("aaab180000005a6a6a2b4c4b686d627a487135704b74454d643633673d3d"))
        }
    }

    @Test
    fun sm2ImplementationDoesNotRegisterGlobalProvider() {
        val providers = Security.getProviders().map { it.name }
        session().use { it.buildCommand(BleLockAction.LOCK, timestamp) }
        assertEquals(providers, Security.getProviders().map { it.name })
    }

    private fun session(certificate: BleKeyCertificate = certificate()): BleKeySession =
        BleKeyProtocol.createSm2Session(certificate, vin, BigInteger.ONE, 0x78563412)

    private fun certificate(der: ByteArray = derCertificate()): BleKeyCertificate = BleKeyCertificate(
        ecdhPublicKey = Base64.getEncoder().encodeToString(der), keyType = 1,
        passwordCard = "0123456789abcdef".repeat(5),
        plainText = "v1;old-account;old-device;$vin;1700000000;1900000000",
        signResult = Base64.getEncoder().encodeToString(ByteArray(16) { (it + 1).toByte() }), vin = vin
    )

    private fun sm2Algorithm(): AlgorithmIdentifier = AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey,
        GMObjectIdentifiers.sm2p256v1)

    // Synthetic ASN.1 certificate: signatures are placeholders; no CA trust validation is claimed by the protocol.
    private fun derCertificate(point: ByteArray = peerPoint, algorithm: AlgorithmIdentifier = sm2Algorithm()): ByteArray {
        val signature = AlgorithmIdentifier(GMObjectIdentifiers.sm2sign_with_sm3)
        val name = X500Name("CN=BLE Offline Test")
        val validity = DERSequence(arrayOf<ASN1Encodable>(Time(Date(1_700_000_000_000L)), Time(Date(1_900_000_000_000L))))
        val tbs = DERSequence(arrayOf<ASN1Encodable>(DERTaggedObject(true, 0, ASN1Integer(2)), ASN1Integer(1),
            signature, name, validity, name, SubjectPublicKeyInfo(algorithm, point)))
        return DERSequence(arrayOf<ASN1Encodable>(tbs, signature, DERBitString(byteArrayOf(1)))).encoded
    }

    private fun frame(value: String): BleFrame = BleFrameDecoder().append(hex(value)).single()
    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val AUTHENTICATION_8 = "qq7zABI0VngEMsSuLB8ZgRlfmQRGajnJlI/jC7/yZgvhcVpFiTNMdMe8Nzai9PZ3nFm9zuNraSFT0KmHfMYqR0AC3zLlITnwoHk4SVRiLzFHUUFNVzhyOHh1MWYxWDNpeStOUWxNZ2Z4M2loMjhTRlZwTkZWT0tEZnYxbVFlRjd4ajJhTWg3MDkzVDRMRXZmbDEzcGgzek14SEFEQ1d0TDVTOXQyNHV6eWtOb0ZRNEd3dUluVlRid0hKdHpId2t5b0Y0cFBnRjhTNzhzVFNIVXpmaW5DS2JNc0FMa2l3aUJLNmllelNUaGJDUTZnZ2IxK0EzOD0BCQ=="
        private const val AUTHENTICATION_9 = "qq7zABI0VngEMsSuLB8ZgRlfmQRGajnJlI/jC7/yZgvhcVpFiTNMdMe8Nzai9PZ3nFm9zuNraSFT0KmHfMYqR0AC3zLlITnwoFVzVEJGcTJZRkxla3JyT1I4K2gwNXI4V2ozTXRhdjFlWkl5c2RXY3FrN3Z1bzNWeUVHMWhhWGF2YWt4bGdZTytmbklndC9nWVZRTnZiMkR2Q0t2U2lRWERnVDEyNkI5V0YwVzNpUUE1L1N5bjZCeCtDeG44L2pGUndjNTdudFRCdWU5dE1pNDhTalN6YXpXNUVMSkc5YXpzRzdrOHhGQUtkM2dDV0x5aVV1RT0BCQ=="
    }
}
