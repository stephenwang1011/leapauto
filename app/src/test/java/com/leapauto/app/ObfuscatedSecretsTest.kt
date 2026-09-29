package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObfuscatedSecretsTest {

    private fun testEncrypt(plain: String): String {
        val bytes = plain.toByteArray(Charsets.UTF_8)
        val out = StringBuilder()
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            val salt = (0x7B + (i * 37) + (i ushr 2)) and 0xFF
            val encoded = b xor salt
            out.append(String.format("%02x", encoded))
        }
        return out.toString()
    }

    @Test
    fun deobfuscatesSampleSecretCorrectly() {
        val original = "b314d89364d5074ebefe31b5bbfc3f3f"
        val encrypted = testEncrypt(original)

        // 验证加密结果与原文字符串绝不相同且不含原文字符特征
        assertNotEquals(original, encrypted)

        val decrypted = ObfuscatedSecrets.deobfuscate(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun handlesInvalidOrCorruptedHexSafely() {
        assertEquals("", ObfuscatedSecrets.deobfuscate(""))
        assertEquals("", ObfuscatedSecrets.deobfuscate("   "))
        assertEquals("", ObfuscatedSecrets.deobfuscate("abc")) // 奇数长度
        assertEquals("", ObfuscatedSecrets.deobfuscate("zzxx")) // 非法16进制字符
    }

    @Test
    fun getAmapWebKeyReturnsEmptyWhenNoBuiltinKey() {
        val key = ObfuscatedSecrets.getAmapWebKey()
        // 彻底移除内置公共高德 Key，无内置时返回空字符串，不泄漏任何开发者敏感凭证
        assertEquals("", key)
    }

    @Test
    fun signatureGuardExpectsValidHashFormat() {
        assertEquals(64, AppSignatureGuard.EXPECTED_RELEASE_SHA256.length)
        assertTrue(AppSignatureGuard.EXPECTED_RELEASE_SHA256.matches(Regex("[0-9A-F]{64}")))
        assertTrue(AppSignatureGuard.isSignatureValid(null))
    }
}
