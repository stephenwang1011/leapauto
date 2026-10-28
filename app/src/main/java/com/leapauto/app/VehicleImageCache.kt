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

/**
 * 纯像素算法：剥离官方 2D 车图底部的半透明烘焙杂影。
 *
 * 判定只依据「该像素位于车身轮廓之下」这一几何事实，
 * 不再按颜色粗筛，因此绝不会误删车漆暗部、深色轮毂或半透明车窗。
 */
internal object GroundShadowStripper {
    /** 车图下缘起算的地面阴影带（占图片高度比例）。 */
    const val GROUND_BAND_RATIO = 0.65f

    /** 视为「实心车身」的 alpha 下限。 */
    const val OPAQUE_ALPHA = 240

    /**
     * @return 清理后的新像素数组；若未发生任何修改则原样返回入参（引用相同）。
     */
    fun strip(pixels: IntArray, width: Int, height: Int): IntArray {
        if (width <= 0 || height <= 0) return pixels

        val bandStartRow = (height * GROUND_BAND_RATIO).toInt()

        // 每列中最底部的实心像素行号（轮胎底缘/车身包围边缘），-1 表示该列完全没有实心结构。
        val lowestOpaqueY = IntArray(width) { -1 }
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                val alpha = (pixels[y * width + x] ushr 24) and 0xFF
                if (alpha >= OPAQUE_ALPHA) {
                    lowestOpaqueY[x] = y
                    break
                }
            }
        }

        val cleared = BooleanArray(width * height)
        var modified = false
        for (y in bandStartRow until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val color = pixels[rowOffset + x]
                val alpha = (color ushr 24) and 0xFF
                if (alpha == 0) continue

                val bottom = lowestOpaqueY[x]
                // 仅清除「位于车身轮廓之下」或「该列完全没有实心结构」的半透明像素。
                // 车身轮廓之内（含半透明车窗与暗色车漆）的像素一律保留。
                val belowBody = bottom != -1 && y > bottom
                val outsideBody = bottom == -1
                if ((belowBody || outsideBody) && alpha < OPAQUE_ALPHA) {
                    cleared[rowOffset + x] = true
                    modified = true
                }
            }
        }

        if (!modified) return pixels

        val result = pixels.copyOf()
        for (index in cleared.indices) {
            if (cleared[index]) result[index] = 0
        }
        return result
    }
}

object VehicleImageCache {
    private const val TAG = "LeapVehiclePic"
    private const val PREFS_NAME = "leap_vehicle_image_cache"
    private const val KEY_PREFIX_URL = "pic_url_"
    private const val KEY_PREFIX_META = "pic_meta_"
    private const val KEY_PREFIX_3D_MODEL = "pic_3d_model_"
    private const val CURRENT_SHADOW_VERSION = 3
    private const val KEY_PREFIX_SHADOW_VERSION = "shadow_v_"

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

    /**
     * 智能剥离官方 2D 车图自带的车底半透明黑色烘焙杂影（Ambient Occlusion 假阴影）。
     * 针对车模中下部区域（从 height * 0.65 开始到最底部），识别车体下方与车轮周围近黑灰半透明杂影并将其清零，
     * 实心不透明车胎、轮毂、车体外包围结构 100% 完整保留，彻底消除实景展台巨幕与小组件上的暗黑脏影。
     */
    fun stripGroundShadow(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        if (width <= 0 || height <= 0) return src

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val stripped = GroundShadowStripper.strip(pixels, width, height)
        if (stripped === pixels) return src

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(stripped, 0, width, 0, 0, width, height)
        return result
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
            memoryCache.remove("official_$vin")
            memoryCache.put("custom_$vin", bitmap)
        }
    }

    fun removeCustomImage(context: Context, vin: String) {
        if (vin.isBlank()) return
        val file = getCustomFile(context, vin)
        if (file.exists()) file.delete()
        synchronized(memoryCache) {
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
            val raw = BitmapFactory.decodeFile(targetFile.absolutePath) ?: return null

            // 阴影清理仅用于展示；旧版本缓存交由 sync() 按版本强制重下原图后重剥离，
            // 避免在已被旧算法误删像素的缓存图上回写并盖章新版本。
            val processed = if (!hasCustom) stripGroundShadow(raw) else raw

            synchronized(memoryCache) {
                memoryCache.put(cacheKey, processed)
            }
            processed
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
                val processed = if (source == WidgetImageSource.OFFICIAL_2D) stripGroundShadow(bitmap) else bitmap
                synchronized(memoryCache) {
                    memoryCache.put(cacheKey, processed)
                }
                return processed
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
    /**
     * 判断官方 2D 原图是否需要重新下载。
     *
     * 除 URL 变化/文件缺失外，阴影算法版本落后也必须重新下载原图：
     * 旧版缓存可能已被旧算法误删像素，重新剥离已处理的缓存无法还原。
     */
    internal fun shouldRefreshOfficialImage(
        cachedUrl: String?,
        url: String,
        fileExists: Boolean,
        fileLength: Long,
        shadowVersion: Int
    ): Boolean = cachedUrl != url || !fileExists || fileLength == 0L ||
        shadowVersion < CURRENT_SHADOW_VERSION

    fun sync(context: Context, api: LeapmotorApi, vin: String, isCurrent: () -> Boolean = { true }): Boolean {
        if (vin.isBlank() || !isCurrent()) return false
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
            // 网络/接口异常不代表该车型不支持 3D，不得据此持久化降级结论，避免偶发失败导致永久回退 2D。
            return false
        }

        if (!isCurrent()) return false
        val is3dSupported = !meta.h5Key.isNullOrBlank()
        // VIN-scoped cache writes must never change another vehicle's global presentation.
        ThemeManager.setVehicle3DSupported(context, vin, is3dSupported, updateCurrent = false)

        val rawUrl = meta.shareBindUrl
        val url = if (rawUrl.startsWith("http://", ignoreCase = true)) {
            "https://" + rawUrl.substring(7)
        } else {
            rawUrl
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUrl = prefs.getString("$KEY_PREFIX_URL$vin", null)
        val cachedShadowVersion = prefs.getInt("$KEY_PREFIX_SHADOW_VERSION$vin", 0)
        val file = getCacheFile(context, vin)

        // 保存/更新元数据缓存
        val metaJson = org.json.JSONObject().apply {
            put("pictureKey", meta.pictureKey)
            put("shareBindUrl", url)
            put("sourceUrl", meta.sourceUrl)
            if (meta.rawData != null) put("rawData", meta.rawData)
        }
        prefs.edit().putString("$KEY_PREFIX_META$vin", metaJson.toString()).apply()

        // 1. 优先确保轻量 2D 原厂定妆照（约 290KB）极速立现，让首卡在毫秒级呈现完整爱车
        var imageUpdated = false
        if (isCurrent() &&
            shouldRefreshOfficialImage(
                cachedUrl = cachedUrl,
                url = url,
                fileExists = file.exists(),
                fileLength = file.length(),
                shadowVersion = cachedShadowVersion
            )
        ) {
            try {
                Log.i(TAG, "优先从 CDN 下载 2D 车辆定妆照: $url")
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
                if (response != null && response.isSuccessful) {
                    val body = response.body
                    val bytes = body?.bytes()
                    if (bytes != null && bytes.isNotEmpty() && isCurrent()) {
                        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (decoded != null && decoded.width > 0 && decoded.height > 0) {
                            val stripped = stripGroundShadow(decoded)
                            val tmpFile = File(getCacheDir(context), "${vin}.tmp")
                            FileOutputStream(tmpFile).use { out ->
                                stripped.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            if (!isCurrent()) {
                                tmpFile.delete()
                                response.close()
                                return false
                            }
                            if (file.exists()) file.delete()
                            if (!tmpFile.renameTo(file)) {
                                tmpFile.copyTo(file, overwrite = true)
                                tmpFile.delete()
                            }
                            prefs.edit()
                                .putString("$KEY_PREFIX_URL$vin", url)
                                .putInt("$KEY_PREFIX_SHADOW_VERSION$vin", CURRENT_SHADOW_VERSION)
                                .apply()
                            synchronized(memoryCache) {
                                memoryCache.put("official_$vin", stripped)
                            }
                            imageUpdated = true
                            Log.i(TAG, "2D 车图极速同步成功 (已去除车底阴影): ${stripped.width}x${stripped.height}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "2D 车图下载跳过或异常", e)
            }
        }

        // 2. 在 2D 车图就绪保障下，平稳异步拉取 12MB 的 3D 模型资产双包
        var modelPackageUpdated = false
        meta.h5Key?.let { h5Key ->
            try {
                if (isCurrent() && !CarModel3DManager.isModelReady(context, h5Key, meta.srcKey)) {
                    modelPackageUpdated = CarModel3DManager.syncModelPackage(context, api, h5Key, meta.srcKey)
                }
            } catch (e: Exception) {
                Log.w(TAG, "3D 模型同步跳过或异常: ${e.message}")
            }
        }

        return imageUpdated || modelPackageUpdated
    }
}
