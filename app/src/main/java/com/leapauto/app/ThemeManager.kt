package com.leapauto.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object ThemeManager {
    private const val TAG = "ThemeManager"
    const val ORIGINAL_SKYBOX_THEME_ID = VehicleThemePolicy.ORIGINAL_THEME_ID
    const val DEFAULT_THEME_ID = VehicleThemePolicy.SNOW_THEME_ID

    /** 当前车型是否提供 3D 车模；无 3D 车辆仍支持本地主题。 */
    @Volatile
    var isCurrentVehicle3DSupported: Boolean = true

    private const val PREFS_3D_SUPPORT = "vehicle_3d_support"
    private const val KEY_3D_SUPPORTED_PREFIX = "supported_"

    /**
     * 标记指定车辆是否支持 3D 车模。
     * 后台资源任务仅保存 VIN 级结论；当前车辆的全局状态由校验后的主线程回调更新。
     */
    fun setVehicle3DSupported(context: Context?, vin: String?, supported: Boolean, updateCurrent: Boolean = true) {
        if (updateCurrent) isCurrentVehicle3DSupported = supported
        if (context != null && !vin.isNullOrBlank()) {
            context.getSharedPreferences(PREFS_3D_SUPPORT, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("$KEY_3D_SUPPORTED_PREFIX$vin", supported)
                .apply()
        }
    }

    /**
     * 查询指定车辆是否支持 3D 车模（只读，无任何写入副作用）。
     * 优先读取同步元数据时持久化的结论；无记录时读取缓存元数据，未知则保留 3D 路径。
     */
    fun isVehicle3DSupported(context: Context?, vin: String?): Boolean {
        if (context == null || vin.isNullOrBlank()) {
            return isCurrentVehicle3DSupported
        }
        val sp = context.getSharedPreferences(PREFS_3D_SUPPORT, Context.MODE_PRIVATE)
        val key = "$KEY_3D_SUPPORTED_PREFIX$vin"
        if (sp.contains(key)) {
            return VehicleThemePolicy.supports3d(sp.getBoolean(key, true), false, null)
        }
        val meta = VehicleImageCache.getCachedMeta(context, vin)
        return VehicleThemePolicy.supports3d(null, meta != null, meta?.h5Key)
    }

    /**
     * 切换当前车辆时同步全局变量，供无 VIN 上下文的调用方（主题色调判定）使用。
     */
    fun updateCurrentVehicle3DSupported(context: Context?, vin: String?) {
        isCurrentVehicle3DSupported = isVehicle3DSupported(context, vin)
    }

    /**
     * 加载内置兜底 3D 展台背景图 (lpcar_pic_3d_bg.webp)。
     * 用于无 3D 车辆选用「雾川雪境」时的首页背景。
     */
    fun loadFallback3dBgBitmap(context: Context): Bitmap? {
        return try {
            context.assets.open("fallback_theme/lpcar_pic_3d_bg.webp").use { inputStream ->
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPremultiplied = true
                }
                BitmapFactory.decodeStream(inputStream, null, opts)
            }
        } catch (e: Exception) {
            Log.w(TAG, "加载内置兜底 3D 展台背景图失败", e)
            null
        }
    }

    /**
     * 加载内置兜底小组件背景图。
     * 无 3D 车辆选用「雾川雪境」时，2x2 小组件加载 lpcar_pic_widget_2x2.webp，
     * 4x2 小组件加载 lpcar_pic_widget_4x2.webp。
     */
    fun loadFallbackWidgetBgBitmap(context: Context, is4x2: Boolean, darkTheme: Boolean = false): Bitmap? {
        val assetName = if (is4x2) "fallback_theme/lpcar_pic_widget_4x2.webp" else "fallback_theme/lpcar_pic_widget_2x2.webp"
        return try {
            val raw = context.assets.open(assetName).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return null
            val refWidthDp = if (is4x2) 340f else 160f
            val cornerRadius = (20f * (raw.width / refWidthDp)).coerceIn(24f, 80f)
            val strokeColor = if (darkTheme) 0x4DFFFFFF.toInt() else 0x26000000
            val rounded = createRoundedWidgetBitmap(raw, cornerRadius, strokeColor)
            if (rounded !== raw) {
                raw.recycle()
            }
            rounded
        } catch (e: Exception) {
            Log.w(TAG, "加载内置兜底小组件背景图失败: is4x2=$is4x2", e)
            null
        }
    }

    // 官方 4 套预设主题内置兜底清单（弱网/离线即可呈现）
    val PRESET_THEMES: List<VehicleTheme> by lazy {
        listOf(
            VehicleTheme(
                themeId = "7",
                themeCode = "20260920004",
                name = "雾川雪境",
                description = "云雾缭绕的巍峨雪山与静谧冰河相映，宛如一幅空灵悠远的冬日水墨画卷。",
                isDefault = true,
                tags = listOf("官方默认"),
                resourceBg = null,
                resourcePkg = "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/leapmotor-app-theme-prod/7/resource_pkg/resource%20-%20%E9%9B%BE%E5%B7%9D%E9%9B%AA%E5%A2%83.zip",
                resourcePkgName = "resource - 雾川雪境.zip",
                fileSize = 2199406L,
                fileMd5 = "e3a513f907c4df15b942d177dcf04547"
            ),
            VehicleTheme(
                themeId = "4",
                themeCode = "20260920001",
                name = "层峦叠嶂",
                description = "车模型 3D 原厂天空盒背景",
                isDefault = false
            ),
            VehicleTheme(
                themeId = "5",
                themeCode = "20260920002",
                name = "余晖石筑",
                description = "沐浴在柔和余晖下的现代石筑，于空旷原野中诉说静谧与沉稳的高级感。",
                tags = listOf("主题上新"),
                resourceBg = "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/leapmotor-app-theme-prod/5/resource_bg/bg.png?Expires=4943522277&OSSAccessKeyId=LTAI4Fmo2WXExVH9PecXNnpy&Signature=o3RFgGR0LOB9SDP2c6w2MQGe2PY%3D",
                resourcePkg = "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/leapmotor-app-theme-prod/5/resource_pkg/resource%20-%20%E4%BD%99%E6%99%96%E7%9F%B3%E7%AD%91.zip",
                resourcePkgName = "resource - 余晖石筑.zip",
                fileSize = 1415005L,
                fileMd5 = "acfe744b0d89a42b9bbd8a209c7ae3a6"
            ),
            VehicleTheme(
                themeId = "8",
                themeCode = "20260920005",
                name = "晴空广场",
                description = "湛蓝天空下的一片开阔空地，树木静静伫立，尽显极简纯粹的留白之美。",
                tags = listOf("主题上新"),
                resourceBg = "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/leapmotor-app-theme-prod/8/resource_bg/bg.png?Expires=4943521186&OSSAccessKeyId=LTAI4Fmo2WXExVH9PecXNnpy&Signature=oSHuFRtrtnneiQZcGAMlTL9XMs4%3D",
                resourcePkg = "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/leapmotor-app-theme-prod/8/resource_pkg/resource%20-%20%E6%99%B4%E7%A9%BA%E5%B9%BF%E5%9C%BA.zip",
                resourcePkgName = "resource - 晴空广场.zip",
                fileSize = 1495987L,
                fileMd5 = "ad42b2013c19f0741f6750906e950964"
            )
        )
    }

    fun getBaseDir(context: Context): File {
        val persistent = File(context.filesDir, "CarThemes").apply {
            if (!exists()) mkdirs()
        }
        // 自动从旧版 cacheDir 迁移已有主题文件到永久 filesDir，确保无缝平滑过渡
        val legacy = File(context.cacheDir, "CarThemes")
        if (legacy.exists() && legacy.isDirectory) {
            legacy.listFiles()?.forEach { item ->
                val dest = File(persistent, item.name)
                if (!dest.exists()) {
                    runCatching {
                        if (item.isDirectory) {
                            item.copyRecursively(dest, overwrite = true)
                        } else {
                            item.copyTo(dest, overwrite = true)
                        }
                        Log.i(TAG, "已成功自动迁移主题资源: ${item.name} -> filesDir")
                    }
                }
            }
        }
        return persistent
    }

    fun sanitizeKey(key: String): String {
        return key.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    }

    fun getThemeDir(baseDir: File, themeId: String): File {
        return File(baseDir, sanitizeKey(themeId))
    }

    fun getThemeDir(context: Context, themeId: String): File {
        return getThemeDir(getBaseDir(context), themeId)
    }

    fun isThemeReady(baseDir: File, themeId: String): Boolean {
        if (themeId == ORIGINAL_SKYBOX_THEME_ID || themeId.isBlank()) return true
        val dir = getThemeDir(baseDir, themeId)
        if (!File(dir, ".ready").exists()) return false
        val bgFile = findThemeFile(dir, "lpcar_pic_3d_bg.png")
        return bgFile != null && bgFile.exists() && bgFile.length() > 0L
    }

    fun isThemeReady(context: Context, themeId: String): Boolean {
        return isThemeReady(getBaseDir(context), themeId)
    }

    /** 智能兼容 ZIP 根目录或带有 resource/ 子目录的资源文件寻址（优先寻址解压后高清实景大图） */
    fun findThemeFile(themeDir: File, fileName: String): File? {
        val inSub = File(File(themeDir, "resource"), fileName)
        if (inSub.exists() && inSub.isFile && inSub.length() > 0L) return inSub
        val direct = File(themeDir, fileName)
        if (direct.exists() && direct.isFile && direct.length() > 0L) {
            // 根目录下若存在小于 300KB 的旧版缩略图且子目录有高清图，优先使用子目录高清图
            if (fileName == "lpcar_pic_3d_bg.png" && direct.length() < 300_000L && inSub.exists() && inSub.length() > direct.length()) {
                return inSub
            }
            return direct
        }
        if (fileName == "lpcar_pic_3d_bg.png") {
            // 兜底寻址：寻找解压出来的真实背景全景大图，杜绝误读带有黑色演示车的预览图
            val candidate = themeDir.walkTopDown().filter {
                it.isFile && it.extension.equals("png", ignoreCase = true) &&
                    it.length() > 300_000L && !it.name.contains("preview", ignoreCase = true)
            }.maxByOrNull { it.length() }
            if (candidate != null && candidate.exists()) return candidate
        }
        return null
    }

    fun get3dBgFile(context: Context, themeId: String): File? {
        if (themeId == ORIGINAL_SKYBOX_THEME_ID || themeId.isBlank()) return null
        val dir = getThemeDir(context, themeId)
        return findThemeFile(dir, "lpcar_pic_3d_bg.png")
    }

    fun getWidget4x2BgFile(context: Context, themeId: String): File? {
        if (themeId == ORIGINAL_SKYBOX_THEME_ID || themeId.isBlank()) return null
        val dir = getThemeDir(context, themeId)
        return findThemeFile(dir, "lpcar_pic_widget_4x2.png")
    }

    fun getWidget2x2BgFile(context: Context, themeId: String): File? {
        if (themeId == ORIGINAL_SKYBOX_THEME_ID || themeId.isBlank()) return null
        val dir = getThemeDir(context, themeId)
        return findThemeFile(dir, "lpcar_pic_widget_2x2.png")
    }

    fun load3dBgBitmap(context: Context, themeId: String, vin: String? = null): Bitmap? {
        val supported = if (!vin.isNullOrBlank()) isVehicle3DSupported(context, vin) else isCurrentVehicle3DSupported
        when (VehicleThemePolicy.background(supported, themeId)) {
            VehicleThemePolicy.Background.ORIGINAL -> return null
            VehicleThemePolicy.Background.BUILT_IN_SNOW -> return loadFallback3dBgBitmap(context)
            VehicleThemePolicy.Background.DOWNLOADED -> Unit
        }
        val file = get3dBgFile(context, themeId)
        if (file != null && file.exists() && file.length() > 0L) {
            return try {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPremultiplied = true
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (e: Exception) {
                Log.w(TAG, "加载 3D 展台主题背景图失败: $themeId", e)
                null
            }
        }
        return null
    }

    /** 安全清理历史误存的官方模糊遮罩图，确保纯净拉取高清 4K 实景大图 */
    fun cleanInvalidBlurredCache(context: Context) {
        val base = getBaseDir(context)
        val cacheFile = File(base, "theme_list.json")
        if (cacheFile.exists()) {
            val content = cacheFile.readText(Charsets.UTF_8)
            if (content.contains("默认主题") || content.contains("原厂天幕")) {
                cacheFile.delete()
                Log.i(TAG, "已清理包含旧默认名称的主题缓存，恢复新命名")
            }
        }
        base.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val rootBg = File(dir, "lpcar_pic_3d_bg.png")
                val inSubBg = File(File(dir, "resource"), "lpcar_pic_3d_bg.png")
                // 若存在旧版 148KB 蓝灰蒙版图或 780KB 带黑车的预览图，自动清理
                if (rootBg.exists() && (rootBg.length() in 140_000L..160_000L || rootBg.length() in 750_000L..820_000L)) {
                    rootBg.delete()
                    File(dir, ".ready").delete()
                    Log.i(TAG, "已清理历史误存的非纯净底图缓存，准备拉取原厂纯净实景大图")
                }
                // 仅当子目录存在解压后的高清图且根目录下为旧版缩略图时清理
                if (rootBg.exists() && rootBg.length() < 300_000L && inSubBg.exists() && inSubBg.length() > 300_000L) {
                    rootBg.delete()
                    Log.i(TAG, "已清理旧版模糊 3D 展台图缓存: ${rootBg.absolutePath}")
                }
                val hasValidBg = (rootBg.exists() && rootBg.length() > 0L) ||
                                 (inSubBg.exists() && inSubBg.length() > 0L)
                if (!hasValidBg) {
                    File(dir, ".ready").delete()
                }
            }
        }
    }

    /** 快速同步 3D 展台高清大图包 */
    fun ensure3dBgDownloaded(context: Context, theme: VehicleTheme): Boolean {
        return syncThemePackage(context, null, theme)
    }

    enum class ThemeTone {
        LIGHT, // 亮色背景主题（如雾川雪镜 7、晴空广场 8、余晖石筑 5），强制使用高对比度深色文字
        DARK,  // 暗色背景主题，强制使用纯白高对比度文字
        AUTO   // 默认主题（4），跟随系统原生深浅模式自适应
    }

    fun getThemeTone(themeId: String, context: Context? = null, vin: String? = null): ThemeTone {
        val supported = if (context != null && !vin.isNullOrBlank()) {
            isVehicle3DSupported(context, vin)
        } else {
            isCurrentVehicle3DSupported
        }
        val effectiveId = if (supported) themeId else VehicleThemePolicy.activeThemeId(false, themeId)
        return when (effectiveId) {
            "7" -> ThemeTone.LIGHT // 雾川雪镜：雪山冰河高亮底
            "8" -> ThemeTone.LIGHT // 晴空广场：湛蓝天空留白高光底
            "5" -> ThemeTone.LIGHT // 余晖石筑：浅色暮色原野石筑，高光浅底
            else -> ThemeTone.AUTO // 默认主题跟随系统
        }
    }

    /** 判断在给定的主题与系统外观下，首卡展台是否为暗色背景（用于决定顶部数据区文字是偏白还是偏黑） */
    fun isHeroBackgroundDark(themeId: String, isSystemDark: Boolean, context: Context? = null, vin: String? = null): Boolean {
        return when (getThemeTone(themeId, context, vin)) {
            ThemeTone.DARK -> true   // 余晖石筑：背景天生为深色暮色，无论系统深浅，展台皆为暗底
            ThemeTone.LIGHT -> false // 晴空/雪镜：背景天生为高亮白天，展台皆为亮底
            ThemeTone.AUTO -> isSystemDark // 默认主题：跟随系统深浅外观
        }
    }

    /** 是否需要在深色模式下为浅色高光主题（如晴空广场/雾川雪镜）施加微弱夜巡沉浸滤镜，避免夜晚强光刺眼 */
    fun shouldApplyNightMoodFilter(themeId: String, isSystemDark: Boolean, context: Context? = null, vin: String? = null): Boolean {
        return isSystemDark && getThemeTone(themeId, context, vin) == ThemeTone.LIGHT
    }

    /** 按照 20dp 标准座舱 R 角与微晶描边对位图进行平滑抗锯齿物理裁剪，杜绝尖角直角 */
    fun createRoundedWidgetBitmap(src: Bitmap, cornerRadiusPx: Float, strokeColor: Int? = null): Bitmap {
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rectF = android.graphics.RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())

        // 1. 绘制抗锯齿 R 角遮罩
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)

        // 2. 以 SRC_IN 模式合成背景图
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // 3. 绘制外层 0.8dp 微晶质感切边描边
        if (strokeColor != null) {
            paint.xfermode = null
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = strokeColor
            canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
        }
        return output
    }

    fun loadWidgetBgBitmap(
        context: Context,
        themeId: String,
        is4x2: Boolean,
        darkTheme: Boolean = false,
        vin: String? = null
    ): Bitmap? {
        val supported = if (!vin.isNullOrBlank()) isVehicle3DSupported(context, vin) else isCurrentVehicle3DSupported
        when (VehicleThemePolicy.background(supported, themeId)) {
            VehicleThemePolicy.Background.ORIGINAL -> return null
            VehicleThemePolicy.Background.BUILT_IN_SNOW -> return loadFallbackWidgetBgBitmap(context, is4x2, darkTheme)
            VehicleThemePolicy.Background.DOWNLOADED -> Unit
        }
        val file = if (is4x2) getWidget4x2BgFile(context, themeId) else getWidget2x2BgFile(context, themeId)
        val targetFile = if (file != null && file.exists() && file.length() > 0L) {
            file
        } else {
            // 老款车若未解压专属小组件切片，直接以实景大图作为小组件皮肤底图
            get3dBgFile(context, themeId)
        }
        if (targetFile == null || !targetFile.exists() || targetFile.length() == 0L) return null
        return try {
            val raw = BitmapFactory.decodeFile(targetFile.absolutePath) ?: return null
            val refWidthDp = if (is4x2) 340f else 160f
            val cornerRadius = (20f * (raw.width / refWidthDp)).coerceIn(24f, 80f)
            val strokeColor = if (darkTheme) 0x4DFFFFFF.toInt() else 0x26000000
            val rounded = createRoundedWidgetBitmap(raw, cornerRadius, strokeColor)
            if (rounded !== raw) {
                raw.recycle()
            }
            rounded
        } catch (e: Exception) {
            Log.w(TAG, "加载小组件主题背景失败: $themeId", e)
            null
        }
    }

    /**
     * 自包含的安全 ZIP 解压方法
     */
    fun downloadAndUnzipPackage(pkgUrl: String, destDir: File, expectedMd5: String? = null): Boolean {
        if (pkgUrl.isBlank()) return false
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalFile
        val request = okhttp3.Request.Builder()
            .url(pkgUrl)
            .get()
            .build()
        val client = NetworkDebugController.httpClient()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val bytes = response.body?.bytes() ?: return@use false
                if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) {
                    return@use false
                }
                if (!expectedMd5.isNullOrBlank()) {
                    val md5Digest = java.security.MessageDigest.getInstance("MD5")
                    val actualMd5 = md5Digest.digest(bytes).joinToString("") { "%02x".format(it) }
                    if (!actualMd5.equals(expectedMd5.trim(), ignoreCase = true)) {
                        Log.w(TAG, "MD5 校验不匹配: 期望=$expectedMd5, 实际=$actualMd5")
                        return@use false
                    }
                }
                java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val targetFile = File(canonicalDest, entry.name).canonicalFile
                        if (!targetFile.path.startsWith(canonicalDest.path + File.separator)) {
                            throw java.io.IOException("Zip traversal rejected: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            java.io.FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                    }
                }
                true
            }
        }.getOrDefault(false)
    }

    /**
     * 同步并安全解压主题包。
     * [theme] 包含主题信息、下载 URL 与 MD5 校验码。
     */
    fun syncThemePackage(context: Context, api: LeapmotorApi?, theme: VehicleTheme): Boolean {
        val themeId = theme.themeId
        if (themeId == ORIGINAL_SKYBOX_THEME_ID) {
            return true
        }
        val targetDir = getThemeDir(context, themeId)
        if (isThemeReady(context, themeId)) {
            Log.d(TAG, "主题资源已就绪: id=$themeId, name=${theme.name}")
            return true
        }

        val pkgUrl = theme.resourcePkg
        if (!pkgUrl.isNullOrBlank()) {
            Log.i(TAG, "开始下载并解压高清实景主题包: id=$themeId, name=${theme.name}, url=$pkgUrl")
            val success = if (api != null) {
                api.downloadThemePackage(pkgUrl, targetDir, theme.fileMd5)
            } else {
                downloadAndUnzipPackage(pkgUrl, targetDir, theme.fileMd5)
            }
            if (success) {
                val bgFile = findThemeFile(targetDir, "lpcar_pic_3d_bg.png")
                if (bgFile != null && bgFile.exists() && bgFile.length() > 0L) {
                    File(targetDir, ".ready").createNewFile()
                    Log.i(TAG, "高清 3D 实景主题包部署就绪: id=$themeId, name=${theme.name} (${bgFile.length()} bytes)")
                    return true
                }
            }
        }

        // 兜底通道：若全量 ZIP 包网络不可达或老款车云端无动态签名，使用单张官方原厂实景大图直通下载，保障全系老款车型 100% 成功换装
        val bgUrl = theme.resourceBg
        if (!bgUrl.isNullOrBlank()) {
            val targetFile = File(targetDir, "lpcar_pic_3d_bg.png")
            val ok = runCatching {
                val req = okhttp3.Request.Builder().url(bgUrl).get().build()
                val client = NetworkDebugController.httpClient()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            targetFile.writeBytes(bytes)
                            File(targetDir, ".ready").createNewFile()
                            Log.i(TAG, "老款车兜底实景图部署就绪: id=$themeId (${bytes.size} bytes)")
                            true
                        } else false
                    } else false
                }
            }.getOrDefault(false)
            if (ok) return true
        }

        Log.w(TAG, "主题包下载解压失败或缺少高清 3D 背景图: id=$themeId")
        return false
    }

    /** 获取车辆当前激活的主题 ID（优先读取本地自选设置，未设置时读取官方云端下发或默认） */
    fun getActiveThemeId(context: Context, vin: String, remoteTheme: VehicleTheme? = null): String {
        val store = SessionStore(context)
        val localSelected = store.loadSelectedThemeId(vin)
        return VehicleThemePolicy.activeThemeId(
            isVehicle3DSupported(context, vin), localSelected, remoteTheme?.themeId
        )
    }

    /** 显式主题 ID（包括 4/7）均保存；仅 null/空白清除偏好。 */
    fun setActiveThemeId(context: Context, vin: String, themeId: String?) {
        val store = SessionStore(context)
        store.saveSelectedThemeId(vin, themeId)
    }

    /** 保存主题列表缓存到磁盘，供离线时读取 */
    fun saveThemeListCache(context: Context, themes: List<VehicleTheme>) {
        if (themes.isEmpty()) return
        try {
            val jsonArray = JSONArray()
            themes.forEach { t ->
                val obj = JSONObject().apply {
                    put("themeId", t.themeId)
                    put("themeCode", t.themeCode)
                    put("name", t.name)
                    put("description", t.description)
                    put("isDefault", t.isDefault)
                    val res = JSONObject().apply {
                        put("resourceBg", t.resourceBg)
                        put("resourcePkg", t.resourcePkg)
                        put("resourcePkgName", t.resourcePkgName)
                        put("fileSize", t.fileSize)
                        put("fileMd5", t.fileMd5)
                        val aff = JSONArray().apply {
                            put(JSONObject().apply {
                                put("name", "首页")
                                put("preview", t.homePreview)
                                put("thumbnail", t.homeThumbnail)
                            })
                            put(JSONObject().apply {
                                put("name", "小组件")
                                put("preview", t.widgetPreview)
                                put("thumbnail", t.widgetThumbnail)
                            })
                        }
                        put("affiliatedResource", aff)
                    }
                    put("resource", res)
                    if (t.tags.isNotEmpty()) {
                        put("tags", JSONArray(t.tags))
                    }
                }
                jsonArray.put(obj)
            }
            val cacheFile = File(getBaseDir(context), "theme_list.json")
            cacheFile.writeText(jsonArray.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "保存主题列表缓存失败", e)
        }
    }

    /** 从本地缓存读取主题列表，若无缓存返回预设兜底清单 */
    fun loadCachedThemeList(context: Context): List<VehicleTheme> {
        val cacheFile = File(getBaseDir(context), "theme_list.json")
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            try {
                val text = cacheFile.readText(Charsets.UTF_8)
                val jsonArray = JSONArray(text)
                val list = ArrayList<VehicleTheme>(jsonArray.length())
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    list.add(VehicleTheme.fromJson(obj))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                Log.w(TAG, "解析主题列表缓存失败，使用预设兜底", e)
            }
        }
        return PRESET_THEMES
    }
}
