package com.leapauto.app.bluetooth

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.gm.GMNamedCurves
import org.bouncycastle.asn1.gm.GMObjectIdentifiers
import org.bouncycastle.asn1.x509.Certificate
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.crypto.digests.SM3Digest
import org.bouncycastle.crypto.engines.SM4Engine
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.BigIntegers

internal object BleSm2Crypto {
    private val parameters = GMNamedCurves.getByName("sm2p256v1")
    private val userId = "1234567812345678".toByteArray(Charsets.US_ASCII)
    private const val PEM_BEGIN = "-----BEGIN CERTIFICATE-----"
    private const val PEM_END = "-----END CERTIFICATE-----"
    private const val MAX_CERTIFICATE_TEXT = 131_072

    class Agreement(val publicKey: ByteArray, val sharedSecret: ByteArray) : AutoCloseable {
        override fun close() {
            publicKey.fill(0)
            sharedSecret.fill(0)
        }
    }

    fun generatePrivateScalar(): BigInteger = BigIntegers.createRandomInRange(
        BigInteger.ONE, parameters.n.subtract(BigInteger.ONE), SecureRandom()
    )

    fun agree(encodedCertificate: String, privateScalar: BigInteger): Agreement {
        require(privateScalar.signum() > 0 && privateScalar < parameters.n) { "Invalid SM2 private scalar" }
        val peer = decodeCertificate(encodedCertificate)
        val local = parameters.g.multiply(privateScalar).normalize()
        // The recovered key protocol reuses each participant's point in both agreement roles.
        val localFactor = xBar(local).add(BigInteger.ONE).multiply(privateScalar).mod(parameters.n)
        val peerFactor = xBar(peer).add(BigInteger.ONE).mod(parameters.n)
        val shared = peer.multiply(peerFactor).normalize().multiply(localFactor).normalize()
        require(!shared.isInfinity && shared.isValid) { "Invalid SM2 agreement point" }
        val material = coordinate(shared.affineXCoord.toBigInteger()) + coordinate(shared.affineYCoord.toBigInteger()) +
            identityDigest(local) + identityDigest(peer) + byteArrayOf(0, 0, 0, 1)
        return try {
            Agreement(local.getEncoded(false), hash(material))
        } finally {
            material.fill(0)
        }
    }

    fun hash(bytes: ByteArray): ByteArray {
        val digest = SM3Digest()
        digest.update(bytes, 0, bytes.size)
        return ByteArray(digest.digestSize).also { digest.doFinal(it, 0) }
    }

    fun crypt(bytes: ByteArray, key: ByteArray, iv: ByteArray, encrypt: Boolean): ByteArray {
        require(bytes.isNotEmpty() && key.size == 16 && iv.size == 16) { "Invalid SM4 input or key/IV" }
        require(encrypt || bytes.size % 16 == 0) { "Invalid SM4 ciphertext length" }
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SM4Engine()), PKCS7Padding())
        cipher.init(encrypt, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(bytes.size))
        return try {
            val count = cipher.processBytes(bytes, 0, bytes.size, output, 0)
            output.copyOf(count + cipher.doFinal(output, count))
        } catch (_: Exception) {
            throw IllegalArgumentException("SM4 BLE encryption or decryption failed")
        } finally {
            output.fill(0)
            cipher.reset()
        }
    }

    private fun decodeCertificate(encoded: String): ECPoint {
        require(encoded.length <= MAX_CERTIFICATE_TEXT) { "SM2 certificate is too large" }
        val text = encoded.trim()
        val body = if (text.startsWith(PEM_BEGIN)) {
            require(text.endsWith(PEM_END)) { "Incomplete SM2 certificate PEM" }
            text.substring(PEM_BEGIN.length, text.length - PEM_END.length)
        } else text
        val der = try {
            Base64.getDecoder().decode(body.filterNot { it in " \t\r\n" })
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid SM2 certificate Base64")
        }
        return try {
            val certificate = Certificate.getInstance(ASN1Primitive.fromByteArray(der))
            val info = certificate.subjectPublicKeyInfo
            require(info.algorithm.algorithm == X9ObjectIdentifiers.id_ecPublicKey &&
                ASN1ObjectIdentifier.getInstance(info.algorithm.parameters) == GMObjectIdentifiers.sm2p256v1) {
                "SM2 certificate must declare the sm2p256v1 curve"
            }
            require(info.publicKeyData.padBits == 0) { "Invalid SM2 public key bit string" }
            decodePoint(info.publicKeyData.bytes)
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid SM2 X.509 certificate")
        } finally {
            der.fill(0)
        }
    }

    private fun decodePoint(bytes: ByteArray): ECPoint {
        require((bytes.size == 65 && bytes[0] == 4.toByte()) ||
            (bytes.size == 33 && (bytes[0] == 2.toByte() || bytes[0] == 3.toByte()))) {
            "Invalid SM2 public key encoding"
        }
        val point = parameters.curve.decodePoint(bytes).normalize()
        require(!point.isInfinity && point.isValid) { "SM2 public key is not on the curve" }
        return point
    }

    private fun identityDigest(point: ECPoint): ByteArray {
        val generator = parameters.g.normalize()
        val bitLength = userId.size * 8
        val material = byteArrayOf((bitLength ushr 8).toByte(), bitLength.toByte()) + userId +
            coordinate(parameters.curve.a.toBigInteger()) + coordinate(parameters.curve.b.toBigInteger()) +
            coordinate(generator.affineXCoord.toBigInteger()) + coordinate(generator.affineYCoord.toBigInteger()) +
            coordinate(point.affineXCoord.toBigInteger()) + coordinate(point.affineYCoord.toBigInteger())
        return try {
            hash(material)
        } finally {
            material.fill(0)
        }
    }

    private fun xBar(point: ECPoint): BigInteger = point.affineXCoord.toBigInteger()
        .and(BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE)).setBit(127)

    private fun coordinate(value: BigInteger): ByteArray = BigIntegers.asUnsignedByteArray(32, value)
}
