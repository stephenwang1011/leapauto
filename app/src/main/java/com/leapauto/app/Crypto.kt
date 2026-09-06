package com.leapauto.app

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 与 leap-cn-mcp SDK 完全一致的加密算法（RSA/AES/MD5/HMAC-SHA256），
 * 移植自 node:crypto 对应实现。
 */
object Crypto {

    const val DEFAULT_PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDHUIQKhkwNqJFTZPe98mC1lmpbY9r/+7PEWZg8ebqYXT3sumKRaQ0zcoTx42x0iybmCRXy4CcZrgGAbwKzwqwNw0rFquJ6c7mgQA6k3lZU3p96qBlzK7DSkoFR6mO9pjcd2hlJ8wH+IwI5b8IWWZhwVN/4cM7npG0S0zeRn3soEwIDAQAB"

    fun randomDeviceId(): String = UUID.randomUUID().toString().replace("-", "")

    fun randomNonce(): String = (10000 + (Math.random() * 10_000_000).toInt()).toString()

    fun md5Short(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)).toHex().substring(8, 24)

    fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).toHex()

    fun hmacSha256Hex(key: ByteArray, input: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(input.toByteArray(Charsets.UTF_8)).toHex()
    }

    /** 短信登录：手机号 RSA(PKCS1) 加密后 base64url 无填充。 */
    fun rsaEncryptPhone(phone: String): String {
        val der = Base64.decode(
            DEFAULT_PUBLIC_KEY.replace("\n", ""),
            Base64.DEFAULT
        )
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val enc = cipher.doFinal(phone.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(enc, Base64.URL_SAFE or Base64.NO_WRAP).trimEnd('=')
    }

    /** 控车操作密码：AES-128-CBC，key/iv 来自旧 token 的两段 md5Short。 */
    fun encryptOperationPassword(password: String, oldToken: String): String {
        require(oldToken.length >= 64) { "旧 token 长度不足，无法加密操作密码" }
        val key = md5Short(oldToken.substring(0, 32)).toByteArray(Charsets.UTF_8)
        val iv = md5Short(oldToken.substring(32, 64)).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return Base64.encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)), Base64.DEFAULT)
    }

    /** 新网关 signKey = token 第三段(64url) XOR r2 XOR r3，取最短长度。 */
    fun deriveSignKey(accessToken: String, r2: String, r3: String): ByteArray {
        val parts = accessToken.split(".")
        require(parts.size == 3) { "accessToken 不是三段式 token" }
        val tokenTail = base64UrlDecode(parts[2])
        val r2b = Base64.decode(r2, Base64.DEFAULT)
        val r3b = Base64.decode(r3, Base64.DEFAULT)
        val len = minOf(tokenTail.size, r2b.size, r3b.size)
        val out = ByteArray(len)
        for (i in 0 until len) out[i] = (tokenTail[i].toInt() xor r2b[i].toInt() xor r3b[i].toInt()).toByte()
        return out
    }

    /** JWT exp（秒）→ 毫秒时间戳；不是 JWT 或解析失败返回 0。 */
    fun jwtExpiryMs(token: String): Long {
        val parts = token.split(".")
        if (parts.size != 3) return 0L
        return try {
            val payload = String(base64UrlDecode(parts[1]), Charsets.UTF_8)
            val exp = JSONObject(payload).optLong("exp")
            if (exp > 0) exp * 1000 else 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun base64UrlDecode(input: String): ByteArray {
        var normalized = input.replace('-', '+').replace('_', '/')
        while (normalized.length % 4 != 0) normalized += "="
        return Base64.decode(normalized, Base64.DEFAULT)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
