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
    private const val KEY_PREFIX_3D_MODEL = "pic_3d_model_"

    private val memoryCache = object : LruCache<String, Bitmap>(10) {}

    internal enum class WidgetImageSource {
        CUSTOM,
        THREE_D_SNAPSHOT,
        OFFICIAL_2D,
        NONE
    }

    internal fun resolveWidgetImageSource(
        hasCustomImage: Boolean,
        has3DSnapshot: Boolean,
        hasOfficial2DImage: Boolean
    ): WidgetImageSource = when {
        hasCustomImage -> WidgetImageSource.CUSTOM
        has3DSnapshot -> WidgetImageSource.THREE_D_SNAPSHOT
        hasOfficial2DImage -> WidgetImageSource.OFFICIAL_2D
        else -> WidgetImageSource.NONE
    }

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

    fun get3DSnapshotFile(context: Context, vin: String): File {
        return File(getCacheDir(context), "${vin}_3d.png")
    }

    fun has3DSnapshot(context: Context, vin: String): Boolean {
        if (vin.isBlank()) return false
        val file = get3DSnapshotFile(context, vin)
        return file.exists() && file.length() > 0
    }

    fun save3DSnapshot(context: Context, vin: String, bitmap: Bitmap, modelKey: String? = null) {
        if (vin.isBlank()) return
        val file = get3DSnapshotFile(context, vin)
        val tmpFile = File(getCacheDir(context), "${vin}_3d.tmp")
        FileOutputStream(tmpFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (file.exists()) file.delete()
        if (!tmpFile.renameTo(file)) {
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
        // 3D 快照独立保存，绝不覆盖 official 2D 或 custom 缓存，防止污染桌面小组件
        synchronized(memoryCache) {
            memoryCache.put("3d_$vin", bitmap)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_PREFIX_3D_MODEL$vin", modelKey.orEmpty())
            .apply()
    }

    fun has3DSnapshotForModel(context: Context, vin: String, modelKey: String): Boolean {
        if (modelKey.isBlank() || !has3DSnapshot(context, vin)) return false
        val snapshotModelKey = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_PREFIX_3D_MODEL$vin", null)
        // Older snapshots have no sidecar key; keep them usable until a new frame is generated.
        return snapshotModelKey.isNullOrBlank() || snapshotModelKey == modelKey
    }

    fun remove3DSnapshot(context: Context, vin: String) {
        if (vin.isBlank()) return
        val file = get3DSnapshotFile(context, vin)
        if (file.exists()) file.delete()
        synchronized(memoryCache) {
            memoryCache.remove("3d_$vin")
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_PREFIX_3D_MODEL$vin")
            .apply()
    }

    fun cropTransparentPixels(src: Bitmap): Bitmap? {
        val width = src.width
        val height = src.height
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        var visiblePixelCount = 0
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val alpha = (pixels[rowOffset + x] ushr 24) and 0xff
                if (alpha > 15) {
                    visiblePixelCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // 必须含有足够的非透明有效像素且非空，否则判定为纯透明/无效截帧，保护桌面小组件回退官方2D图
        if (visiblePixelCount < 100 || maxX <= minX || maxY <= minY) return null

        val cropWidth = maxX - minX + 1
        val cropHeight = maxY - minY + 1
        if (cropWidth < 30 || cropHeight < 20) return null

        val padX = (cropWidth * 0.04f).toInt()
        val padY = (cropHeight * 0.04f).toInt()
        val finalMinX = maxOf(0, minX - padX)
        val finalMinY = maxOf(0, minY - padY)
        val finalMaxX = minOf(width - 1, maxX + padX)
        val finalMaxY = minOf(height - 1, maxY + padY)

        val finalCropWidth = finalMaxX - finalMinX + 1
        val finalCropHeight = finalMaxY - finalMinY + 1

        return Bitmap.createBitmap(src, finalMinX, finalMinY, finalCropWidth, finalCropHeight)
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
            memoryCache.remove(vin)
            memoryCache.remove("official_$vin")
            memoryCache.put("custom_$vin", bitmap)
        }
    }

    fun removeCustomImage(context: Context, vin: String) {
        if (vin.isBlank()) return
        val file = getCustomFile(context, vin)
        if (file.exists()) file.delete()
        synchronized(memoryCache) {
            memoryCache.remove(vin)
            memoryCache.remove("custom_$vin")
            memoryCache.remove("official_$vin")
        }
    }

    fun loadCachedBitmap(context: Context, vin: String): Bitmap? {
        if (vin.isBlank()) return null
        val customFile = getCustomFile(context, vin)
        val hasCustom = customFile.exists() && customFile.length() > 0
        val cacheKey = if (hasCustom) "custom_$vin" else "official_$vin"

        synchronized(memoryCache) {
            memoryCache.get(cacheKey)?.let { return it }
        }
        // 主页等非桌面插件场景保留既有语义：车主自定义图优先，否则使用官方 2D 离线精修图。
        val targetFile = if (hasCustom) customFile else getCacheFile(context, vin)
        if (!targetFile.exists() || targetFile.length() <= 0) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
            if (bitmap != null) {
                synchronized(memoryCache) {
                    memoryCache.put(cacheKey, bitmap)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun loadWidgetBitmap(context: Context, vin: String): Bitmap? {
        if (vin.isBlank()) return null

        val candidates = mapOf(
            WidgetImageSource.CUSTOM to ("custom_$vin" to getCustomFile(context, vin)),
            WidgetImageSource.THREE_D_SNAPSHOT to ("3d_$vin" to get3DSnapshotFile(context, vin)),
            WidgetImageSource.OFFICIAL_2D to ("official_$vin" to getCacheFile(context, vin))
        )
        val meta = getCachedMeta(context, vin)
        val currentModelKey = meta?.h5Key
        val hasCurrent3DModel = currentModelKey != null &&
            CarModel3DManager.isModelReady(context, currentModelKey, meta.srcKey)
        val hasCurrent3DSnapshot = hasCurrent3DModel &&
            VehicleImageCache.has3DSnapshotForModel(context, vin, currentModelKey!!)
        val preferredSource = resolveWidgetImageSource(
            hasCustomImage = candidates.getValue(WidgetImageSource.CUSTOM).second.isUsableImageFile(),
            has3DSnapshot = hasCurrent3DSnapshot &&
                candidates.getValue(WidgetImageSource.THREE_D_SNAPSHOT).second.isUsable3DSnapshotFile(),
            hasOfficial2DImage = candidates.getValue(WidgetImageSource.OFFICIAL_2D).second.isUsableImageFile()
        )
        val sourceOrder = when (preferredSource) {
            WidgetImageSource.CUSTOM -> listOf(
                WidgetImageSource.CUSTOM,
                if (hasCurrent3DSnapshot) WidgetImageSource.THREE_D_SNAPSHOT else WidgetImageSource.OFFICIAL_2D,
                WidgetImageSource.OFFICIAL_2D
            )
            WidgetImageSource.THREE_D_SNAPSHOT -> listOf(
                WidgetImageSource.THREE_D_SNAPSHOT,
                WidgetImageSource.OFFICIAL_2D
            )
            WidgetImageSource.OFFICIAL_2D -> listOf(WidgetImageSource.OFFICIAL_2D)
            WidgetImageSource.NONE -> emptyList()
        }
        for (source in sourceOrder) {
            val (cacheKey, file) = candidates.getValue(source)
            if (!file.exists() || file.length() <= 0) continue
            if (source == WidgetImageSource.THREE_D_SNAPSHOT && file.length() < 4096) continue
            synchronized(memoryCache) {
                memoryCache.get(cacheKey)?.let { return it }
            }
            val bitmap = try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (_: Exception) {
                null
            }
            if (bitmap != null) {
                synchronized(memoryCache) {
                    memoryCache.put(cacheKey, bitmap)
                }
                return bitmap
            }
        }
        return null
    }

    private fun File.isUsableImageFile(): Boolean = exists() && length() > 0
    private fun File.isUsable3DSnapshotFile(): Boolean = exists() && length() >= 4096

    fun loadCachedImageBitmap(context: Context, vin: String): ImageBitmap? {
        return loadCachedBitmap(context, vin)?.asImageBitmap()
    }

    fun clearCache(context: Context, vin: String) {
        if (vin.isBlank()) return
        synchronized(memoryCache) {
            memoryCache.remove(vin)
            memoryCache.remove("custom_$vin")
            memoryCache.remove("official_$vin")
            memoryCache.remove("3d_$vin")
        }
        val file = getCacheFile(context, vin)
        if (file.exists()) file.delete()
        val customFile = getCustomFile(context, vin)
        if (customFile.exists()) customFile.delete()
        val snapshotFile = get3DSnapshotFile(context, vin)
        if (snapshotFile.exists()) snapshotFile.delete()
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
                if (!CarModel3DManager.isModelReady(context, h5Key, meta.srcKey)) {
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
