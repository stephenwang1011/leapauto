package com.leapauto.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader

data class AlphaCropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1
}

data class VehiclePlacement(
    val carWidth: Int,
    val carHeight: Int,
    val carLeft: Float,
    val carTop: Float,
    val shadowCenterX: Float,
    val shadowCenterY: Float,
    val shadowRadiusX: Float,
    val shadowRadiusY: Float
)

object VehicleImageProcessor {

    const val TARGET_WIDTH = 1200
    const val TARGET_HEIGHT = 522
    const val DEFAULT_ALPHA_THRESHOLD = 15

    /**
     * 计算位图非透明有效像素的外接矩形边界。
     * 纯算法，使用 getAlpha 回调以支持跨平台/脱机单元测试。
     */
    fun computeAlphaBounds(
        width: Int,
        height: Int,
        threshold: Int = DEFAULT_ALPHA_THRESHOLD,
        getAlpha: (x: Int, y: Int) -> Int
    ): AlphaCropRect? {
        if (width <= 0 || height <= 0) return null

        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (getAlpha(x, y) > threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        return if (minX <= maxX && minY <= maxY) {
            AlphaCropRect(minX, minY, maxX, maxY)
        } else {
            null
        }
    }

    /**
     * 计算车模在标准 2.3:1 展厅画布中的黄金居中比例与车轮接地暗影参数。
     */
    fun computePlacement(
        carWidth: Int,
        carHeight: Int,
        canvasWidth: Int = TARGET_WIDTH,
        canvasHeight: Int = TARGET_HEIGHT
    ): VehiclePlacement {
        val safeCarW = carWidth.coerceAtLeast(1)
        val safeCarH = carHeight.coerceAtLeast(1)

        val maxAllowedW = canvasWidth * 0.90f
        val maxAllowedH = canvasHeight * 0.82f

        val scale = minOf(maxAllowedW / safeCarW, maxAllowedH / safeCarH)
        val finalCarW = (safeCarW * scale).toInt().coerceAtLeast(1)
        val finalCarH = (safeCarH * scale).toInt().coerceAtLeast(1)

        val carLeft = (canvasWidth - finalCarW) / 2f
        // 放置在略微偏上（约 40% 垂直间距处），让轮胎自然贴合下方接地地平线
        val carTop = (canvasHeight - finalCarH) * 0.40f

        val shadowCenterX = canvasWidth * 0.5f
        val shadowCenterY = carTop + finalCarH * 0.93f
        val shadowRadiusX = finalCarW * 0.44f
        val shadowRadiusY = finalCarH * 0.08f

        return VehiclePlacement(
            carWidth = finalCarW,
            carHeight = finalCarH,
            carLeft = carLeft,
            carTop = carTop,
            shadowCenterX = shadowCenterX,
            shadowCenterY = shadowCenterY,
            shadowRadiusX = shadowRadiusX,
            shadowRadiusY = shadowRadiusY
        )
    }

    fun calculateInSampleSize(
        outWidth: Int,
        outHeight: Int,
        reqWidth: Int = 1920,
        reqHeight: Int = 1920
    ): Int {
        var sampleSize = 1
        if (outHeight > reqHeight || outWidth > reqWidth) {
            val halfHeight = outHeight / 2
            val halfWidth = outWidth / 2
            while (halfHeight / sampleSize >= reqHeight || halfWidth / sampleSize >= reqWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    /**
     * 安全高效地从 Uri 解码用户选取的图片，包含三重降级策略：
     * 1. Android 9+ (API 28+) 优先使用系统原生 ImageDecoder（支持各种格式，自动处理 EXIF 旋转与色彩空间）；
     * 2. 单次读取字节流并通过 BitmapFactory.decodeByteArray 解码（避免 Stream 重复开启或不可 seek 问题）；
     * 3. 降级使用 FileDescriptor 解码。
     */
    fun decodeBitmapFromUri(
        contentResolver: android.content.ContentResolver,
        uri: android.net.Uri,
        reqWidth: Int = 1920,
        reqHeight: Int = 1920
    ): Bitmap? {
        // 方案 1: Android 9+ 优先使用系统 ImageDecoder
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    val sampleSize = calculateInSampleSize(info.size.width, info.size.height, reqWidth, reqHeight)
                    if (sampleSize > 1) {
                        decoder.setTargetSampleSize(sampleSize)
                    }
                }
                if (bitmap.width > 0 && bitmap.height > 0) {
                    return bitmap
                }
            } catch (e: Throwable) {
                android.util.Log.w("VehicleImageProcessor", "ImageDecoder 解码失败，尝试 ByteArray 解码: ${e.message}")
            }
        }

        // 方案 2: 单次读取字节流到内存中解码
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null && bytes.isNotEmpty()) {
                val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
                if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
                    val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, reqWidth, reqHeight)
                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    if (bitmap != null) {
                        return bitmap
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("VehicleImageProcessor", "ByteArray 解码失败，尝试 FileDescriptor 解码: ${e.message}")
        }

        // 方案 3: 使用 FileDescriptor 解码
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fd = pfd.fileDescriptor
                val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFileDescriptor(fd, null, boundsOptions)
                if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
                    val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, reqWidth, reqHeight)
                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(fd, null, decodeOptions)
                    if (bitmap != null) {
                        return bitmap
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("VehicleImageProcessor", "FileDescriptor 解码失败: ${e.message}")
        }

        return null
    }

    /**
     * 安全高效地从输入流解码用户选取的图片，针对大尺寸照片按需降采样以防止 OOM。
     */
    fun decodeSampledBitmap(
        openStream: () -> java.io.InputStream?,
        reqWidth: Int = 1920,
        reqHeight: Int = 1920
    ): Bitmap? {
        val bytes = try {
            openStream()?.use { it.readBytes() }
        } catch (_: Throwable) {
            null
        } ?: return null

        if (bytes.isEmpty()) return null

        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    /**
     * 检测位图是否包含透明背景通道（用于区分已抠图的透明车模与普通矩形全景照片）。
     */
    fun hasTransparency(
        width: Int,
        height: Int,
        threshold: Int = DEFAULT_ALPHA_THRESHOLD,
        sampleStep: Int = 10,
        getAlpha: (x: Int, y: Int) -> Int
    ): Boolean {
        var transparentSamples = 0
        var totalSamples = 0
        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                totalSamples++
                if (getAlpha(x, y) <= threshold) {
                    transparentSamples++
                }
            }
        }
        // 只要有超过 12% 的采样点是透明的，即判定为已抠图背景
        return totalSamples > 0 && (transparentSamples.toFloat() / totalSamples) > 0.12f
    }

    /**
     * 高性能扫描位图非透明像素外接矩形（单次 JNI 批量读取每行像素）。
     */
    fun computeAlphaBoundsFromBitmap(
        bitmap: Bitmap,
        threshold: Int = DEFAULT_ALPHA_THRESHOLD
    ): AlphaCropRect? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        var minX = w
        var maxX = -1
        var minY = h
        var maxY = -1

        val row = IntArray(w)
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val alpha = (row[x] ushr 24) and 0xFF
                if (alpha > threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        return if (minX <= maxX && minY <= maxY) {
            AlphaCropRect(minX, minY, maxX, maxY)
        } else {
            null
        }
    }

    /**
     * 处理用户上传的爱车图片：
     * 1. 若为透明背景（如系统长按抠图成果）：自动做紧凑边界修剪、展厅比例标准化并生成柔和着地暗影；
     * 2. 若为带背景实景照片：自动居中等比裁切至标准展厅 2.3:1 比例。
     */
    fun processUserVehicleImage(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source

        val softwareBitmap = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, true) ?: source
        } else {
            source
        }

        val swW = softwareBitmap.width
        val swH = softwareBitmap.height

        val isTransparent = hasTransparency(swW, swH) { x, y ->
            (softwareBitmap.getPixel(x, y) ushr 24) and 0xFF
        }

        val result = if (isTransparent) {
            processTransparentCutout(softwareBitmap)
        } else {
            processOpaquePhoto(softwareBitmap)
        }

        if (softwareBitmap !== source && softwareBitmap !== result) {
            softwareBitmap.recycle()
        }

        return result
    }

    private fun processTransparentCutout(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height

        val bounds = computeAlphaBoundsFromBitmap(source)

        val croppedCar = if (bounds != null && bounds.width > 10 && bounds.height > 10) {
            Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width, bounds.height)
        } else {
            source
        }

        val output = Bitmap.createBitmap(TARGET_WIDTH, TARGET_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val placement = computePlacement(croppedCar.width, croppedCar.height, TARGET_WIDTH, TARGET_HEIGHT)

        // 1. 绘制智能轮胎接触暗影（Ground Contact Shadow）
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                placement.shadowCenterX,
                placement.shadowCenterY,
                placement.shadowRadiusX,
                intArrayOf(
                    Color.argb(120, 15, 20, 30),
                    Color.argb(45, 15, 20, 30),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        canvas.save()
        // 将径向渐变压扁为椭圆
        canvas.scale(1f, placement.shadowRadiusY / placement.shadowRadiusX, placement.shadowCenterX, placement.shadowCenterY)
        canvas.drawCircle(placement.shadowCenterX, placement.shadowCenterY, placement.shadowRadiusX, shadowPaint)
        canvas.restore()

        // 2. 绘制车身主体
        val destRect = RectF(
            placement.carLeft,
            placement.carTop,
            placement.carLeft + placement.carWidth,
            placement.carTop + placement.carHeight
        )
        val carPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(croppedCar, null, destRect, carPaint)

        if (croppedCar !== source) {
            croppedCar.recycle()
        }

        return output
    }

    private fun processOpaquePhoto(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(TARGET_WIDTH, TARGET_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val targetRatio = TARGET_WIDTH.toFloat() / TARGET_HEIGHT.toFloat()
        val sourceRatio = source.width.toFloat() / source.height.toFloat()

        val srcRect = if (sourceRatio > targetRatio) {
            // 来源图更宽：裁去左右
            val newW = (source.height * targetRatio).toInt()
            val left = (source.width - newW) / 2
            Rect(left, 0, left + newW, source.height)
        } else {
            // 来源图更高：裁去上下
            val newH = (source.width / targetRatio).toInt()
            val top = (source.height - newH) / 2
            Rect(0, top, source.width, top + newH)
        }

        val destRect = Rect(0, 0, TARGET_WIDTH, TARGET_HEIGHT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, srcRect, destRect, paint)

        return output
    }
}
