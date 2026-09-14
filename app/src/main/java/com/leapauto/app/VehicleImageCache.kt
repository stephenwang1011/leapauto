package com.leapauto.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object VehicleImageCache {
    private const val TAG = "LeapVehiclePic"
    private const val PREFS_NAME = "leap_vehicle_image_cache"
    private const val KEY_PREFIX_URL = "pic_url_"
    private const val KEY_PREFIX_META = "pic_meta_"

    private val memoryCache = object : LruCache<String, Bitmap>(10) {}

    fun getCachedMeta(context: Context, vin: String): VehiclePictureMeta? {
        if (vin.isBlank()) return null
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_PREFIX_META$vin", null) ?: return null
        return try {
            val json = org.json.JSONObject(raw)
            VehiclePictureMeta(
                pictureKey = json.optString("pictureKey"),
                shareBindUrl = json.optString("shareBindUrl"),
                sourceUrl = json.optString("sourceUrl"),
                rawData = json.optJSONObject("rawData")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getCacheDir(context: Context): File {
        return File(context.filesDir, "vehicle_images").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getCacheFile(context: Context, vin: String): File {
        return File(getCacheDir(context), "${vin}.png")
    }

    fun getCustomFile(context: Context, vin: String): File {
        return File(getCacheDir(context), "${vin}_custom.png")
    }

    fun hasCustomImage(context: Context, vin: String): Boolean {
        if (vin.isBlank()) return false
        val file = getCustomFile(context, vin)
        return file.exists() && file.length() > 0
    }

    fun saveCustomImage(context: Context, vin: String, bitmap: Bitmap) {
        if (vin.isBlank()) return
        val file = getCustomFile(context, vin)
        val tmpFile = File(getCacheDir(context), "${vin}_custom.tmp")
        FileOutputStream(tmpFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (file.exists()) file.delete()
        if (!tmpFile.renameTo(file)) {
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
        synchronized(memoryCache) {
            memoryCache.put(vin, bitmap)
        }
    }

    fun removeCustomImage(context: Context, vin: String) {
        if (vin.isBlank()) return
        val file = getCustomFile(context, vin)
        if (file.exists()) file.delete()
        synchronized(memoryCache) {
            memoryCache.remove(vin)
        }
    }

    fun loadCachedBitmap(context: Context, vin: String): Bitmap? {
        if (vin.isBlank()) return null
        val customFile = getCustomFile(context, vin)
        val hasCustom = customFile.exists() && customFile.length() > 0
        synchronized(memoryCache) {
            memoryCache.get(vin)?.let { return it }
        }
        val targetFile = if (hasCustom) {
            customFile
        } else {
            getCacheFile(context, vin)
        }
        if (!targetFile.exists() || targetFile.length() <= 0) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
            if (bitmap != null) {
                synchronized(memoryCache) {
                    memoryCache.put(vin, bitmap)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun loadCachedImageBitmap(context: Context, vin: String): ImageBitmap? {
        return loadCachedBitmap(context, vin)?.asImageBitmap()
    }

    fun clearCache(context: Context, vin: String) {
        if (vin.isBlank()) return
        synchronized(memoryCache) {
            memoryCache.remove(vin)
        }
        val file = getCacheFile(context, vin)
        if (file.exists()) file.delete()
        val customFile = getCustomFile(context, vin)
        if (customFile.exists()) customFile.delete()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_PREFIX_URL$vin")
            .remove("$KEY_PREFIX_META$vin")
            .apply()
    }

    /**
     * 同步远端车辆图片及 3D 模型资产。
     * @return 如果成功下载并保存了新的车辆图片或 3D 资产，返回 true；若图片未变化或下载失败，返回 false。
     */
    fun sync(context: Context, api: LeapmotorApi, vin: String): Boolean {
        if (vin.isBlank()) return false
        val diagnostics = mutableListOf<String>()
        val meta = try {
            api.getVehiclePictureMeta(vin, diagnostics)
        } catch (e: Exception) {
            diagnostics.add("异常: ${e.message}")
            null
        }

        if (meta == null) {
            val reason = if (diagnostics.isNotEmpty()) diagnostics.joinToString("\n\n") else "全部候选接口未返回车图"
            Log.w(TAG, "车图元数据获取失败:\n$reason")
            return false
        }

        val rawUrl = meta.shareBindUrl
        val url = if (rawUrl.startsWith("http://", ignoreCase = true)) {
            "https://" + rawUrl.substring(7)
        } else {
            rawUrl
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUrl = prefs.getString("$KEY_PREFIX_URL$vin", null)
        val file = getCacheFile(context, vin)

        // 保存/更新元数据缓存
        val metaJson = org.json.JSONObject().apply {
            put("pictureKey", meta.pictureKey)
            put("shareBindUrl", url)
            put("sourceUrl", meta.sourceUrl)
            if (meta.rawData != null) put("rawData", meta.rawData)
        }
        prefs.edit().putString("$KEY_PREFIX_META$vin", metaJson.toString()).apply()

        // 尝试拉取 3D 模型双包（若元数据包含 h5Key，协同拉取并合并 srcKey）
        var modelPackageUpdated = false
        meta.h5Key?.let { h5Key ->
            try {
                if (!CarModel3DManager.isModelReady(context, h5Key)) {
                    modelPackageUpdated = CarModel3DManager.syncModelPackage(context, api, h5Key, meta.srcKey)
                }
            } catch (e: Exception) {
                Log.w(TAG, "3D 模型同步跳过或异常: ${e.message}")
            }
        }

        if (cachedUrl == url && file.exists() && file.length() > 0) {
            Log.d(TAG, "车图 URL 未变化且本地已有缓存: $url")
            return modelPackageUpdated
        }

        return try {
            Log.i(TAG, "开始从 CDN 下载车辆图片: $url")
            val candidateUrls = listOf(url, rawUrl).distinct()
            var response: okhttp3.Response? = null
            for (u in candidateUrls) {
                val req = Request.Builder().url(u).build()
                val resp = NetworkDebugController.httpClient().newCall(req).execute()
                if (resp.isSuccessful) {
                    response = resp
                    break
                }
                resp.close()
            }
            if (response == null || !response.isSuccessful) {
                val errorMsg = "CDN 下载失败: HTTP ${response?.code ?: "-"}"
                Log.e(TAG, errorMsg)
                return false
            }

            val body = response.body ?: return false
            val bytes = body.bytes()
            if (bytes.isEmpty()) {
                Log.e(TAG, "CDN 返回图片内容为空")
                return false
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "下载的字节不是有效图像")
                return false
            }

            val tmpFile = File(getCacheDir(context), "${vin}.tmp")
            FileOutputStream(tmpFile).use { it.write(bytes) }
            if (file.exists()) file.delete()
            if (!tmpFile.renameTo(file)) {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            }

            prefs.edit().putString("$KEY_PREFIX_URL$vin", url).apply()
            synchronized(memoryCache) {
                memoryCache.remove(vin)
            }
            val successMsg = buildString {
                appendLine("车图同步成功: ${options.outWidth}x${options.outHeight}")
                appendLine("来源接口: ${meta.sourceUrl}")
                appendLine("图片地址: $url")
                if (meta.rawData != null) {
                    appendLine("接口返回原始数据: ${meta.rawData}")
                }
            }
            Log.i(TAG, successMsg)
            true
        } catch (e: Exception) {
            val err = "车图下载异常: ${e.message}"
            Log.e(TAG, err, e)
            false
        }
    }
}
