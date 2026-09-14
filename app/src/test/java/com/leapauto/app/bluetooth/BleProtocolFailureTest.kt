package com.leapauto.app.bluetooth

import java.math.BigInteger
import java.nio.charset.MalformedInputException
import javax.crypto.BadPaddingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BleProtocolFailureTest {
    @Test
    fun actualCertificateAndCipherFailuresAreClassifiedWithoutRawDetails() {
        val certificate = assertThrows(IllegalArgumentException::class.java) {
            BleSm2Crypto.agree("not%base64", BigInteger.ONE)
        }
        assertEquals(BleProtocolFailure.CERTIFICATE_FORMAT, BleProtocolFailure.classify(certificate))
        val scalar = assertThrows(IllegalArgumentException::class.java) {
            BleSm2Crypto.agree("not%base64", BigInteger.ZERO)
        }
        assertEquals(BleProtocolFailure.KEY_AGREEMENT, BleProtocolFailure.classify(scalar))
        val cipher = assertThrows(IllegalArgumentException::class.java) {
            BleSm2Crypto.crypt(byteArrayOf(1), ByteArray(16), ByteArray(16), false)
        }
        assertEquals(BleProtocolFailure.CIPHERTEXT, BleProtocolFailure.classify(cipher))
    }

    @Test
    fun curvePointAndP256ErrorsRemainDistinct() {
        assertEquals(BleProtocolFailure.CERTIFICATE_CURVE,
            classify("SM2 certificate must declare the sm2p256v1 curve"))
        assertEquals(BleProtocolFailure.CERTIFICATE_POINT, classify("Invalid SM2 public key encoding"))
        assertEquals(BleProtocolFailure.P256_PUBLIC_KEY, classify("BLE P-256 point is not on the curve"))
    }

    @Test
    fun frameAuthenticationAndSessionFailuresRemainDistinct() {
        val frame = assertThrows(IllegalArgumentException::class.java) { BleFrameDecoder(16).append(ByteArray(17)) }
        assertEquals(BleProtocolFailure.FRAME, BleProtocolFailure.classify(frame))
        assertEquals(BleProtocolFailure.AUTHENTICATION, classify("Invalid BLE certificate signature"))
        assertEquals(BleProtocolFailure.SESSION, classify("BLE session is closed"))
        assertEquals(BleProtocolFailure.CERTIFICATE_BINDING,
            classify("BLE authentication certificate changed during the connection"))
    }

    @Test
    fun standardCipherAndUtf8ExceptionsDoNotExposeProviderText() {
        assertEquals(BleProtocolFailure.CIPHERTEXT, BleProtocolFailure.classify(BadPaddingException("synthetic-private-material")))
        assertEquals(BleProtocolFailure.FRAME, BleProtocolFailure.classify(MalformedInputException(1)))
    }

    @Test
    fun unknownMessagesCausesAndPartialMatchesReturnOnlyFixedGeneralLabel() {
        for (error in listOf(RuntimeException(), RuntimeException("synthetic-private-material"),
            RuntimeException("Invalid SM2 public key encoding: synthetic-private-material"),
            RuntimeException("provider failure", IllegalArgumentException("Invalid SM2 public key encoding")))) {
            val failure = BleProtocolFailure.classify(error)
            assertEquals(BleProtocolFailure.GENERAL, failure)
            assertEquals(1000, failure.code)
            assertFalse(failure.label.contains("synthetic-private-material"))
            assertFalse(failure.toString().contains("provider failure"))
        }
    }

    @Test
    fun publishedDiagnosticCodesAreUniqueAndStable() {
        assertEquals((1000..1010).toList(), BleProtocolFailure.entries.map { it.code })
        assertEquals(BleProtocolFailure.entries.size, BleProtocolFailure.entries.map { it.label }.distinct().size)
    }

    private fun classify(message: String): BleProtocolFailure =
        BleProtocolFailure.classify(IllegalArgumentException(message))
}
