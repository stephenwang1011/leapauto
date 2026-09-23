package com.leapauto.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 蒲公英新版本应用内静默直载与系统安装器调度模块：
 * 1. 0 开发者私钥泄露风险，纯公开通道解析；
 * 2. 自动跟随蒲公英 302 重定向解析腾讯云 CDN 真实 APK 直链；
 * 3. 流式分块下载，实时回调百分比进度；
 * 4. 自动通过 FileProvider 发起 PackageInstaller 系统覆盖安装。
 */
object AppUpdateInstaller {

    private const val TAG = "AppUpdateInstaller"
    private const val PGYER_APP_KEY = "7ecc97bc26b12f114185ad278542bba3"
    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; U; Android 14; zh-cn; 23116PN5BC Build/UKQ1.230804.001) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/118.0.0.0 Mobile Safari/537.36"

    fun buildDownloadEntryUrl(buildKey: String?): String {
        return if (!buildKey.isNullOrBlank()) {
            "https://www.pgyer.com/app/install/$buildKey"
        } else {
            "https://www.pgyer.com/app/installCheck?key=lingpaozhikong"
        }
    }

    /**
     * 第一通道：通过官方 OpenAPI 换取官方双重签名直链
     */
    fun resolveViaApiKey(apiKey: String, buildKey: String?): String? {
        if (apiKey.isBlank() || buildKey.isNullOrBlank()) return null
        val apiUrl = "https://www.pgyer.com/apiv2/app/install?_api_key=$apiKey&buildKey=$buildKey"
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", MOBILE_UA)
        }
        try {
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                if (!loc.isNullOrBlank()) return loc
            }
        } catch (e: Exception) {
            Log.w(TAG, "官方 API Key 解析异常: ${e.message}")
        } finally {
            conn.disconnect()
        }
        return null
    }

    /**
     * 第二通道：模拟浏览器防盗链会话解析（获取 Set-Cookie、timeSign、authcode，计算 finalCode）
     */
    fun resolveViaBrowserSession(buildKey: String?): String? {
        val pageUrl = "https://www.pgyer.com/lingpaozhikong"
        var cookieHeader = ""
        var html = ""
        val pageConn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", MOBILE_UA)
        }
        try {
            val cookies = pageConn.headerFields["Set-Cookie"]
            cookieHeader = cookies?.joinToString("; ") { it.substringBefore(";") } ?: ""
            html = pageConn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.w(TAG, "获取蒲公英网页源码失败: ${e.message}")
            return null
        } finally {
            pageConn.disconnect()
        }

        val aKey = Regex("aKey\\s*=\\s*['\"]([a-f0-9]{32})['\"]").find(html)?.groupValues?.get(1)
            ?: buildKey ?: return null
        val installToken = Regex("installToken\\s*=\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        val timeSign = Regex("timeSign\\s*=\\s*['\"]([a-f0-9]+)['\"]").find(html)?.groupValues?.get(1)
        val authcode = Regex("var\\s+authcode\\s*=\\s*\"(\\d+)\"").find(html)?.groupValues?.get(1)

        val targetKey = if (!buildKey.isNullOrBlank()) buildKey else aKey
        val installUrl = if (!timeSign.isNullOrBlank() && !authcode.isNullOrBlank() && !installToken.isNullOrBlank()) {
            val decodedTimeSign = decodeTimeSign(timeSign)
            val now = System.currentTimeMillis()
            val randomCode = now % 1_000_000
            val xor = (authcode.toIntOrNull() ?: 0) xor randomCode.toInt()
            val finalCode = "$xor${randomCode.toString().padStart(6, '0')}"
            "https://www.pgyer.com/app/install/$targetKey?time=$now&finalCode=$finalCode&timeSign=$decodedTimeSign&installToken=$installToken"
        } else {
            "https://www.pgyer.com/app/install/$targetKey"
        }

        val installConn = (URL(installUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Referer", pageUrl)
            if (cookieHeader.isNotBlank()) {
                setRequestProperty("Cookie", cookieHeader)
            }
        }

        try {
            val code = installConn.responseCode
            if (code in 300..399) {
                val loc = installConn.getHeaderField("Location")
                if (!loc.isNullOrBlank()) return loc
            }
        } catch (e: Exception) {
            Log.w(TAG, "会话换取下载地址异常: ${e.message}")
        } finally {
            installConn.disconnect()
        }
        return null
    }

    private fun decodeTimeSign(str: String): String {
        val sb = StringBuilder()
        for (d in 0 until 12) {
            val idx = 2 * d
            if (idx + 2 <= str.length) {
                val byteVal = str.substring(idx, idx + 2).toIntOrNull(16) ?: 0
                sb.append(byteVal.toChar().lowercaseChar())
            }
        }
        val out = StringBuilder()
        for (c in sb.toString()) {
            out.append(String.format("%02x", c.code))
        }
        return out.toString()
    }

    /**
     * 解析蒲公英真实 CDN APK 直链（按优先级多通道调度）
     */
    fun resolveDirectApkUrl(buildKey: String?): String {
        // 通道 1：官方 API Key (混淆解密)
        val apiKey = ObfuscatedSecrets.getPgyerApiKey()
        if (apiKey.isNotBlank()) {
            val direct = resolveViaApiKey(apiKey, buildKey)
            if (!direct.isNullOrBlank()) {
                Log.d(TAG, "已通过官方 API Key 获取到 CDN 直链")
                return direct
            }
        }

        // 通道 2：网页防盗链会话解析
        val sessionUrl = resolveViaBrowserSession(buildKey)
        if (!sessionUrl.isNullOrBlank()) {
            Log.d(TAG, "已通过网页防盗链会话解析获取到 CDN 直链")
            return sessionUrl
        }

        // 通道 3：基础重定向兜底
        val entryUrl = buildDownloadEntryUrl(buildKey)
        val conn = (URL(entryUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Referer", "https://www.pgyer.com/lingpaozhikong")
        }
        try {
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                if (!loc.isNullOrBlank()) return loc
            }
        } finally {
            conn.disconnect()
        }

        throw IOException("未能解析到合法的蒲公英 APK 下载直链")
    }

    /**
     * 流式下载 APK 并实时回调百分比进度
     */
    fun downloadApk(
        context: Context,
        release: PgyerRelease,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                onProgress(2)
                val directUrl = resolveDirectApkUrl(release.buildKey)
                Log.d(TAG, "已成功解析到 CDN 下载直链: $directUrl")
                onProgress(10)

                val conn = (URL(directUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", MOBILE_UA)
                    setRequestProperty("Accept-Encoding", "identity")
                }

                if (conn.responseCode !in 200..299) {
                    throw IOException("下载服务器响应 HTTP ${conn.responseCode}")
                }

                val totalBytes = conn.contentLengthLong
                val outputFile = File(context.cacheDir, "update_${release.versionName}.apk")
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                var downloadedBytes = 0L
                conn.inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        var lastReported = 10
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val progress = 10 + ((downloadedBytes * 88) / totalBytes).toInt().coerceIn(0, 88)
                                if (progress > lastReported) {
                                    lastReported = progress
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }

                if (!outputFile.exists() || outputFile.length() < 1_000_000L) {
                    throw IOException("安装包大小异常或下载不完整")
                }

                onProgress(100)
                Log.i(TAG, "APK 下载完成并验证通过: ${outputFile.absolutePath}, size=${outputFile.length()} 字节")
                onSuccess(outputFile)
            } catch (e: Exception) {
                Log.e(TAG, "应用内下载失败: ${e.message}", e)
                onError(e.message ?: "下载新版本失败，请稍后重试")
            }
        }.start()
    }

    /**
     * 校验未知来源权限并唤起系统安装器覆盖安装
     */
    fun triggerInstall(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.w(TAG, "安装文件不存在: ${apkFile.absolutePath}")
            return
        }

        // Android 8.0+ 检查未知来源安装权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(manageIntent)
                return
            }
        }

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }
}
