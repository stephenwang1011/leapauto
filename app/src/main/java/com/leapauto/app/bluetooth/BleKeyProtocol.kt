package com.leapauto.app.bluetooth

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class BleLockAction(val commandId: Int) {
    UNLOCK(1),
    LOCK(2)
}

enum class BleFrameType(val code: Int, val headerSize: Int) {
    ENCRYPTED(0xAB, 6),
    EVENT(0xAC, 10),
    AUTHENTICATION(0xAE, 4),
    RECONNECT(0xEE, 4)
}

enum class BleChunkProfile(val maximum: Int) {
    LEGACY_160(160),
    ONE_PAO_V010(197)
}

@JvmInline
value class BleReconnectCredential private constructor(val hex: String) {
    fun bytes(): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    override fun toString(): String = "BleReconnectCredential(redacted)"

    companion object {
        fun parse(value: String): BleReconnectCredential {
            require(value.isNotEmpty() && value.length % 2 == 0 && value.length <= 1_024 &&
                value.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) {
                "Invalid BLE reconnect credential"
            }
            return BleReconnectCredential(value.lowercase())
        }
    }
}

class BleFrame internal constructor(val type: BleFrameType, bytes: ByteArray) {
    internal val bytes = bytes.copyOf()
    val payload: ByteArray get() = bytes.copyOfRange(type.headerSize, bytes.size)
}

sealed interface BleResponse {
    class Authenticated(val lockState: String) : BleResponse
    data class ReconnectCredential(val credential: BleReconnectCredential) : BleResponse
    class CommandResult(val identifier: String, val result: String?) : BleResponse {
        fun confirmsConfiguration(): Boolean = identifier == "3" && (result == "0" || result == "00")
    }
    data object Unknown : BleResponse
}

sealed interface BleEvent {
    class Rejected(val resultCode: Int) : BleEvent
    class LockAction(val crc8: Int, val trigger: Int, val action: BleLockAction?) : BleEvent {
        fun confirms(expected: BleLockAction): Boolean = trigger == 1 && action == expected
    }
    data object Unknown : BleEvent
}

object BleKeyProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000fffe-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val DEFAULT_PROTOCOL_MINOR = 8
    private val PROTOCOL_MINOR_UUID_PATTERN = Regex("000001[0-9a-f]{2}-0000-1000-8000-00805f9b34fb")

    fun certificateFingerprint(certificate: BleKeyCertificate): String =
        certificateDigest(certificate).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun createSession(certificate: BleKeyCertificate, vin: String): BleKeySession {
        validateCertificate(certificate, vin)
        val sessionId = SecureRandom().nextInt().toLong() and 0xFFFFFFFFL
        if (certificate.keyType == 1) {
            return createSm2Session(certificate, vin, BleSm2Crypto.generatePrivateScalar(), sessionId)
        }
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return createSession(certificate, vin, generator.generateKeyPair(), sessionId)
    }

    internal fun createSession(
        certificate: BleKeyCertificate,
        vin: String,
        keyPair: KeyPair,
        sessionId: Long
    ): BleKeySession {
        validateCertificate(certificate, vin)
        require(certificate.keyType == 0) { "P-256 session requires a P-256 certificate" }
        require(sessionId in 0..0xFFFFFFFFL) { "Invalid BLE session identifier" }
        val peer = decodePublicKey(certificate.ecdhPublicKey)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(keyPair.private)
        agreement.doPhase(peer, true)
        val sharedSecret = agreement.generateSecret()
        require(sharedSecret.size == 32) { "Invalid P-256 shared secret" }
        val publicKey = encodePublicKey(keyPair.public as ECPublicKey)
        return deriveSession(certificate, vin, sessionId, publicKey, sharedSecret)
    }

    internal fun createSm2Session(
        certificate: BleKeyCertificate,
        vin: String,
        privateScalar: BigInteger,
        sessionId: Long
    ): BleKeySession {
        validateCertificate(certificate, vin)
        require(certificate.keyType == 1) { "SM2 session requires an SM2 certificate" }
        require(sessionId in 0..0xFFFFFFFFL) { "Invalid BLE session identifier" }
        return BleSm2Crypto.agree(certificate.ecdhPublicKey, privateScalar).use {
            deriveSession(certificate, vin, sessionId, it.publicKey, it.sharedSecret)
        }
    }

    private fun deriveSession(
        certificate: BleKeyCertificate,
        vin: String,
        sessionId: Long,
        publicKey: ByteArray,
        sharedSecret: ByteArray
    ): BleKeySession {
        val selector = sessionHash(certificate.keyType, littleEndian(sessionId, 4) + vin.reversed().toByteArray(Charsets.UTF_8))
        val offset = ((selector[0].toInt() and 0xFF) ushr 4) * 5
        val cardBytes = certificate.passwordCard.toByteArray(Charsets.US_ASCII)
        val material = cardBytes.copyOfRange(offset, offset + 5) + sharedSecret
        val digest = sessionHash(certificate.keyType, material)
        val alphabet = "0123456789abcdef"
        val hex = ByteArray(digest.size * 2) {
            alphabet[(digest[it / 2].toInt() ushr (if (it % 2 == 0) 4 else 0)) and 0x0F].code.toByte()
        }
        val key = hex.copyOfRange(0, 16)
        val iv = hex.copyOfRange(48, 64)
        val fingerprint = certificateDigest(certificate)
        return try {
            BleKeySession(sessionId, publicKey, key, iv, fingerprint, certificate.keyType)
        } finally {
            listOf(sharedSecret, selector, cardBytes, material, digest, hex, key, iv, fingerprint).forEach { it.fill(0) }
        }
    }

    fun advertisedProtocolMinor(serviceUuids: Iterable<UUID>): Int? {
        return serviceUuids.asSequence().map(UUID::toString).firstOrNull(PROTOCOL_MINOR_UUID_PATTERN::matches)
            ?.substring(6, 8)?.toInt(16)
    }

    fun protocolMinor(serviceUuids: Iterable<UUID>): Int =
        advertisedProtocolMinor(serviceUuids) ?: DEFAULT_PROTOCOL_MINOR

    fun chunkLimit(mtu: Int, profile: BleChunkProfile = BleChunkProfile.LEGACY_160): Int =
        (mtu.coerceAtLeast(23) - 3).coerceIn(20, profile.maximum)

    fun chunks(
        frame: ByteArray,
        mtu: Int,
        profile: BleChunkProfile = BleChunkProfile.LEGACY_160
    ): List<ByteArray> {
        val limit = chunkLimit(mtu, profile)
        return frame.indices.step(limit).map { frame.copyOfRange(it, minOf(it + limit, frame.size)) }
    }

    fun parseEvent(frame: BleFrame): BleEvent {
        if (frame.type != BleFrameType.EVENT) return BleEvent.Unknown
        val payload = frame.payload
        return when (frame.bytes[5].toInt() and 0xFF) {
            1 -> if (payload.isEmpty()) BleEvent.Unknown else BleEvent.Rejected(payload.last().toInt() and 0xFF)
            2 -> if (payload.size < 3) BleEvent.Unknown else BleEvent.LockAction(
                crc8 = payload[0].toInt() and 0xFF,
                trigger = payload[1].toInt() and 0xFF,
                action = BleLockAction.entries.firstOrNull { it.commandId == (payload[2].toInt() and 0xFF) }
            )
            else -> BleEvent.Unknown
        }
    }

    internal fun crc8(bytes: ByteArray): Int {
        var crc = 0
        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = ((crc shl 1) xor if (crc and 0x80 != 0) 0x07 else 0) and 0xFF
            }
        }
        return crc
    }

    private fun validateCertificate(certificate: BleKeyCertificate, vin: String) {
        require(vin.isNotBlank() && certificate.vin == vin) { "BLE certificate belongs to a different vehicle" }
        require(BleAccessPolicy.isSupportedKeyType(certificate.keyType)) { "Unsupported Bluetooth key certificate type" }
        require(certificate.passwordCard.length >= 80 && certificate.passwordCard.all { it.code <= 127 }) {
            "Invalid BLE password card"
        }
    }

    private fun decodePublicKey(encoded: String): ECPublicKey {
        val bytes = decodeBase64(encoded, "Invalid BLE P-256 public key")
        require(bytes.size == 65 && bytes[0] == 4.toByte()) { "Invalid BLE P-256 public key" }
        val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(BigInteger(1, bytes.copyOfRange(1, 33)), BigInteger(1, bytes.copyOfRange(33, 65)))
        val prime = (params.curve.field as java.security.spec.ECFieldFp).p
        require(point.affineX < prime && point.affineY < prime) { "Invalid BLE P-256 coordinates" }
        require(point.affineY.modPow(BigInteger.valueOf(2), prime) ==
            point.affineX.modPow(BigInteger.valueOf(3), prime).add(params.curve.a.multiply(point.affineX))
                .add(params.curve.b).mod(prime)) { "BLE P-256 point is not on the curve" }
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params)) as ECPublicKey
    }

    private fun encodePublicKey(publicKey: ECPublicKey): ByteArray = byteArrayOf(4) +
        unsignedCoordinate(publicKey.w.affineX) + unsignedCoordinate(publicKey.w.affineY)

    private fun unsignedCoordinate(coordinate: BigInteger): ByteArray {
        val bytes = coordinate.toByteArray()
        return if (bytes.size > 32) bytes.copyOfRange(bytes.size - 32, bytes.size)
        else ByteArray(32 - bytes.size) + bytes
    }
}

data class BleAuthenticationStructure(
    val certificateFieldCount: Int,
    val identityMatchMask: Int,
    val textByteCount: Int,
    val signatureByteCount: Int,
    val plaintextByteCount: Int,
    val ciphertextByteCount: Int,
    val protocolMinor: Int,
    val flagsMask: Int,
    val identityEmptyMask: Int = -1
)

class BleKeySession internal constructor(
    private val sessionId: Long,
    publicKey: ByteArray,
    key: ByteArray,
    iv: ByteArray,
    certificateFingerprint: ByteArray,
    private val keyType: Int
) : AutoCloseable {
    private val publicKey = publicKey.copyOf()
    private val key = key.copyOf()
    private val iv = iv.copyOf()
    private val certificateFingerprint = certificateFingerprint.copyOf()
    private var closed = false

    fun buildAuthentication(
        certificate: BleKeyCertificate,
        accountId: String,
        deviceId: String,
        protocolMinor: Int,
        epochSeconds: Long,
        configuration: BlePassiveConfiguration = BlePassiveConfiguration.MANUAL,
        textProfile: BleAuthenticationTextProfile = BleAuthenticationTextProfile.LEGACY_VERSIONED,
        supportsButton: Boolean = false,
        onPrepared: ((BleAuthenticationStructure) -> Unit)? = null
    ): ByteArray {
        check(!closed) { "BLE session is closed" }
        require(MessageDigest.isEqual(certificateFingerprint, certificateDigest(certificate))) {
            "BLE authentication certificate changed during the connection"
        }
        require(epochSeconds >= 0 && protocolMinor in 0..255) { "Invalid BLE authentication parameters" }
        require(accountId.isNotBlank() && deviceId.isNotBlank() && ';' !in accountId && ';' !in deviceId) {
            "Invalid BLE account or device identifier"
        }
        val fields = certificate.plainText.split(';').toMutableList()
        require(fields.size >= 6) { "Incomplete BLE certificate text" }
        val identityMatchMask = (if (fields[1] == accountId) 1 else 0) or
            (if (fields[2] == deviceId) 2 else 0)
        val identityEmptyMask = (if (fields[1].isEmpty()) 1 else 0) or
            (if (fields[2].isEmpty()) 2 else 0)
        val authenticationFields = when (textProfile) {
            BleAuthenticationTextProfile.LEGACY_VERSIONED -> fields.also {
                it[1] = accountId
                it[2] = deviceId
            }
            BleAuthenticationTextProfile.ONE_PAO_V010 -> mutableListOf(
                "", accountId, deviceId, fields[3], fields[4], fields[5]
            )
        }
        val settings = if (textProfile == BleAuthenticationTextProfile.ONE_PAO_V010) {
            "${configuration.calibration.toProtocolText()};${configuration.reconnectFields(supportsButton)
                .drop(5).joinToString(";")};"
        } else {
            configuration.authenticationFields(protocolMinor, textProfile)
        }
        val text = "$epochSeconds;${authenticationFields.joinToString(";")};$settings".toByteArray(Charsets.UTF_8)
        val signature = decodeBase64(certificate.signResult, "Invalid BLE certificate signature")
        require(signature.isNotEmpty()) { "Invalid BLE certificate signature" }
        val plain = littleEndian(text.size.toLong(), 4) + text + signature
        val ciphertext = crypt(plain, Cipher.ENCRYPT_MODE)
        val encrypted = Base64.getEncoder().encode(ciphertext)
        ciphertext.fill(0)
        plain.fill(0)
        val payload = littleEndian(sessionId, 4) + publicKey + encrypted + byteArrayOf(1, 9)
        require(payload.size <= 0xFFFF) { "BLE authentication payload is too large" }
        val frame = byteArrayOf(0xAA.toByte(), 0xAE.toByte()) + littleEndian(payload.size.toLong(), 2) + payload
        onPrepared?.let { prepared ->
            // Bit zero describes the first flag actually encoded after the four calibration fields.
            val flagsMask = settings.split(';').drop(4).dropLast(1).foldIndexed(0) { index, mask, value ->
                mask or (value.toInt() shl index)
            }
            prepared(BleAuthenticationStructure(authenticationFields.size, identityMatchMask, text.size, signature.size,
                plain.size, ciphertext.size, protocolMinor, flagsMask, identityEmptyMask))
        }
        return frame
    }

    fun buildCommand(action: BleLockAction, epochSeconds: Long): ByteArray =
        buildEncryptedCommand(action.commandId, byteArrayOf(), epochSeconds)

    fun buildConfiguration(
        configuration: BlePassiveConfiguration,
        protocolMinor: Int,
        epochSeconds: Long,
        compatibilityProfile: BleCompatibilityProfile = BleCompatibilityProfile.LEGACY,
        supportsButton: Boolean = false
    ): ByteArray = buildEncryptedCommand(
        3,
        compatibilityProfile.encodeConfiguration(configuration, protocolMinor, supportsButton),
        epochSeconds
    )

    fun buildReconnectAuthentication(
        credential: BleReconnectCredential,
        accountId: String,
        epochSeconds: Long,
        configuration: BlePassiveConfiguration = BlePassiveConfiguration.MANUAL,
        supportsButton: Boolean = false
    ): ByteArray {
        check(!closed) { "BLE session is closed" }
        require(epochSeconds >= 0 && accountId.isNotBlank() && ';' !in accountId) {
            "Invalid BLE reconnect parameters"
        }
        require(publicKey.size == 65 && publicKey[0] == 4.toByte()) {
            "BLE reconnect public key must be an uncompressed 65-byte point"
        }
        val credentialBytes = credential.bytes()
        val accountBytes = accountId.toByteArray(Charsets.UTF_8)
        var plain = byteArrayOf()
        var ciphertext = byteArrayOf()
        var encrypted = byteArrayOf()
        var frame = byteArrayOf()
        var digest = byteArrayOf()
        try {
            require(accountBytes.isNotEmpty() && accountBytes.size <= 0xFFFF) {
                "Invalid BLE reconnect account identifier"
            }
            plain = littleEndian(epochSeconds, 8) + credentialBytes + configuration.reconnectFields(supportsButton) +
                littleEndian(accountBytes.size.toLong(), 2) + accountBytes
            ciphertext = crypt(plain, Cipher.ENCRYPT_MODE)
            encrypted = Base64.getEncoder().encode(ciphertext)
            val payloadLength = encrypted.size + 87
            require(payloadLength <= 0xFFFF) { "BLE reconnect payload is too large" }
            frame = byteArrayOf(0xAA.toByte(), 0xEE.toByte()) + littleEndian(payloadLength.toLong(), 2) +
                littleEndian(sessionId, 4) + publicKey + encrypted + byteArrayOf(1, 9)
            digest = sessionHash(keyType, frame)
            require(digest.size >= 16) { "BLE reconnect digest is too short" }
            return frame + digest.copyOfRange(0, 16)
        } finally {
            listOf(credentialBytes, accountBytes, plain, ciphertext, encrypted, frame, digest).forEach { it.fill(0) }
        }
    }

    private fun buildEncryptedCommand(commandId: Int, parameters: ByteArray, epochSeconds: Long): ByteArray {
        require(epochSeconds >= 0) { "Invalid BLE command timestamp" }
        val command = littleEndian(epochSeconds, 8) + byteArrayOf(commandId.toByte()) + parameters
        val plain = byteArrayOf(BleKeyProtocol.crc8(command).toByte()) + command
        val payload = Base64.getEncoder().encode(crypt(plain, Cipher.ENCRYPT_MODE))
        plain.fill(0)
        return byteArrayOf(0xAA.toByte(), 0xAB.toByte()) + littleEndian(payload.size.toLong(), 4) + payload
    }

    fun decodeResponse(frame: BleFrame): BleResponse {
        require(frame.type == BleFrameType.ENCRYPTED) { "Only an encrypted BLE frame can be decoded" }
        val ciphertext = decodeBase64(String(frame.payload, Charsets.US_ASCII).trim(), "Invalid encrypted BLE frame")
        require(ciphertext.isNotEmpty()) { "Empty encrypted BLE frame" }
        val plain = crypt(ciphertext, Cipher.DECRYPT_MODE)
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plain)).toString()
        } finally {
            plain.fill(0)
        }
        val fields = text.split(';')
        if (fields.size < 2) return BleResponse.Unknown
        return when (fields[0]) {
            "0" -> if (fields[1].isNotEmpty()) BleResponse.Authenticated(fields[1]) else BleResponse.Unknown
            "1" -> fields.getOrNull(1)?.let {
                runCatching { BleResponse.ReconnectCredential(BleReconnectCredential.parse(it)) }.getOrNull()
            } ?: BleResponse.Unknown
            "2" -> BleResponse.CommandResult(fields[1], fields.getOrNull(2))
            else -> BleResponse.Unknown
        }
    }

    override fun close() {
        closed = true
        key.fill(0)
        iv.fill(0)
        publicKey.fill(0)
        certificateFingerprint.fill(0)
    }

    private fun crypt(bytes: ByteArray, mode: Int): ByteArray {
        check(!closed) { "BLE session is closed" }
        if (keyType == 1) return BleSm2Crypto.crypt(bytes, key, iv, mode == Cipher.ENCRYPT_MODE)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(bytes)
    }
}

class BleFrameDecoder(private val maxFrameSize: Int = 65_539) {
    private var pending = byteArrayOf()

    init {
        require(maxFrameSize in 10..1_048_576)
    }

    fun append(bytes: ByteArray): List<BleFrame> {
        require(bytes.size <= maxFrameSize) { "BLE notification exceeds the frame limit" }
        pending += bytes
        val frames = mutableListOf<BleFrame>()
        var offset = 0
        while (pending.size - offset >= 2) {
            val type = BleFrameType.entries.firstOrNull { it.code == (pending[offset + 1].toInt() and 0xFF) }
            if (pending[offset] != 0xAA.toByte() || type == null) {
                offset++
                continue
            }
            if (pending.size - offset < type.headerSize) break
            val lengthOffset = if (type == BleFrameType.EVENT) 6 else 2
            val lengthSize = if (type.headerSize == 4) 2 else 4
            val length = unsignedLittleEndian(pending, offset + lengthOffset, lengthSize)
            val total = type.headerSize + length
            if (total > maxFrameSize) {
                offset++
                continue
            }
            if (pending.size - offset < total) break
            frames += BleFrame(type, pending.copyOfRange(offset, offset + total.toInt()))
            offset += total.toInt()
        }
        pending = pending.copyOfRange(offset, pending.size)
        if (pending.size == 1 && pending[0] != 0xAA.toByte()) pending = byteArrayOf()
        check(pending.size <= maxFrameSize) { "BLE frame buffer limit exceeded" }
        return frames
    }

    fun clear() {
        pending.fill(0)
        pending = byteArrayOf()
    }
}

private fun littleEndian(value: Long, size: Int): ByteArray = ByteArray(size) { (value ushr (it * 8)).toByte() }

private fun unsignedLittleEndian(bytes: ByteArray, offset: Int, size: Int): Long =
    (0 until size).fold(0L) { value, index -> value or ((bytes[offset + index].toLong() and 0xFF) shl (8 * index)) }

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

private fun sessionHash(keyType: Int, bytes: ByteArray): ByteArray =
    if (keyType == 1) BleSm2Crypto.hash(bytes) else sha256(bytes)

private fun certificateDigest(certificate: BleKeyCertificate): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    for (field in listOf(certificate.ecdhPublicKey, certificate.keyType.toString(), certificate.passwordCard,
        certificate.plainText, certificate.signResult, certificate.vin)) {
        val bytes = field.toByteArray(Charsets.UTF_8)
        digest.update(littleEndian(bytes.size.toLong(), 4))
        digest.update(bytes)
        bytes.fill(0)
    }
    return digest.digest()
}

private fun decodeBase64(value: String, message: String): ByteArray = try {
    Base64.getDecoder().decode(value)
} catch (_: IllegalArgumentException) {
    throw IllegalArgumentException(message)
}
