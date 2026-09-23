package com.leapauto.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * 官方应用证书签名防篡改自校验：
 * 防止恶意第三方对 APK 进行反编译注入、二次打包重签名分发。
 */
object AppSignatureGuard {

    private const val TAG = "AppSignatureGuard"

    // 官方正式版签名证书 SHA-256 指纹 (无冒号全大写)
    const val EXPECTED_RELEASE_SHA256 =
        "4FD7A609F94AC11ABF9BB40BFD78F5448BB1BBDEBC83FBD827A0D9E85EF0A3F6"

    @Volatile
    private var verifiedCache: Boolean? = null

    /**
     * 校验当前运行中的 APK 是否拥有合法的官方 Release 签名或处于本地测试环境
     */
    fun isSignatureValid(context: Context?): Boolean {
        // 缓存判断，避免频繁反射与系统调用开销
        verifiedCache?.let { return it }

        if (context == null || BuildConfig.DEBUG) {
            return true
        }

        return try {
            val currentSha256 = getApkSignatureSha256(context)
            val isValid = currentSha256.equals(EXPECTED_RELEASE_SHA256, ignoreCase = true)
            if (!isValid) {
                Log.e(TAG, "警告：检测到应用签名与官方证书不匹配，可能存在二次打包篡改！")
            }
            verifiedCache = isValid
            isValid
        } catch (e: Exception) {
            Log.w(TAG, "签名检查异常: ${e.message}")
            // 异常时默认放行以保证基础可用性，不强阻断异常设备
            true
        }
    }

    /**
     * 提取当前 APK 签名的 SHA-256 指纹
     */
    fun getApkSignatureSha256(context: Context): String? {
        return try {
            val pm = context.packageManager
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(certBytes)
            digest.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.w(TAG, "无法提取当前签名证书: ${e.message}")
            null
        }
    }
}
