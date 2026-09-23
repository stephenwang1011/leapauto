package com.leapauto.app

import android.content.Context
import android.util.Log

/**
 * 敏感 API Key 原生动态掩码混淆与保护模块：
 * 1. 杜绝在 DEX 常量池中保留明文字符串；
 * 2. 运行时动态执行变长盐值反向解混淆；
 * 3. 联动官方应用签名防篡改校验，阻断重打包劫持。
 */
object ObfuscatedSecrets {

    private const val TAG = "ObfuscatedSecrets"

    @Volatile
    private var cachedAmapWebKey: String? = null

    @Volatile
    private var cachedPgyerApiKey: String? = null

    /**
     * 获取高德 Web API Key（内存动态解混淆）
     */
    fun getAmapWebKey(context: Context? = null): String {
        cachedAmapWebKey?.let { return it }

        if (context != null && !AppSignatureGuard.isSignatureValid(context)) {
            Log.e(TAG, "安全告警：签名校验失败，拒绝解密高德 API Key")
            return ""
        }

        val decrypted = deobfuscate(BuildConfig.AMAP_WEB_KEY_ENCRYPTED)
        cachedAmapWebKey = decrypted
        return decrypted
    }

    /**
     * 获取蒲公英 API Key（内存动态解混淆）
     */
    fun getPgyerApiKey(context: Context? = null): String {
        cachedPgyerApiKey?.let { return it }

        if (context != null && !AppSignatureGuard.isSignatureValid(context)) {
            Log.e(TAG, "安全告警：签名校验失败，拒绝解密蒲公英 API Key")
            return ""
        }

        val decrypted = deobfuscate(BuildConfig.PGYER_API_KEY_ENCRYPTED)
        cachedPgyerApiKey = decrypted
        return decrypted
    }

    /**
     * 变长盐值反混淆解码算法（与 build.gradle.kts 中的混淆算法严格对称）
     */
    fun deobfuscate(hex: String): String {
        if (hex.isBlank() || hex.length % 2 != 0) return ""
        return try {
            val len = hex.length / 2
            val bytes = ByteArray(len)
            for (i in 0 until len) {
                val encoded = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return ""
                val salt = (0x7B + (i * 37) + (i ushr 2)) and 0xFF
                bytes[i] = (encoded xor salt).toByte()
            }
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
