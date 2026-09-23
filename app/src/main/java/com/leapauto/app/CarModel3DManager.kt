package com.leapauto.app

import android.content.Context
import android.util.Log
import java.io.File

object CarModel3DManager {
    private const val TAG = "CarModel3DManager"

    fun getBaseDir(context: Context): File {
        return File(context.cacheDir, "CarModel3D").apply {
            if (!exists()) mkdirs()
        }
    }

    fun sanitizeKey(key: String): String {
        return key.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    }

    fun getModelDir(baseDir: File, key: String): File {
        return File(baseDir, sanitizeKey(key))
    }

    fun getModelDir(context: Context, key: String): File {
        return getModelDir(getBaseDir(context), key)
    }

    fun isModelReady(baseDir: File, key: String, srcKey: String? = null): Boolean {
        if (key.isBlank()) return false
        val dir = getModelDir(baseDir, key)
        val hasIndexHtml = File(dir, "index.html").exists() && File(dir, "index.html").length() > 0L
        val hasReady = File(dir, ".ready").exists()
        if (!hasReady || !hasIndexHtml) return false
        if (!srcKey.isNullOrBlank()) {
            val hasSrcReady = File(dir, ".ready_${sanitizeKey(srcKey)}").exists()
            if (!hasSrcReady) return false
        }
        return true
    }

    fun isModelReady(context: Context, key: String, srcKey: String? = null): Boolean {
        return isModelReady(getBaseDir(context), key, srcKey)
    }

    fun cleanModelPackage(context: Context, key: String) {
        if (key.isBlank()) return
        val dir = getModelDir(context, key)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun getFirstReadyKey(context: Context): String? {
        val base = getBaseDir(context)
        return base.listFiles()?.firstOrNull {
            it.isDirectory && File(it, ".ready").exists() && File(it, "index.html").exists()
        }?.name
    }

    /**
     * 同步 3D 车模双包架构：
     * [h5Key]：提供 H5 运行时、Three.js 核心引擎与 index.html 入口；
     * [srcKey]：提供 3D 车体结构网格、材质贴图、车轮等实际三维资源。
     * 双包解压合并至同一模型沙箱目录后打标就绪。
     */
    fun syncModelPackage(context: Context, api: LeapmotorApi, h5Key: String, srcKey: String? = null): Boolean {
        if (h5Key.isBlank()) return false
        if (isModelReady(context, h5Key, srcKey)) {
            Log.d(TAG, "3D 车模资源已就绪: h5Key=$h5Key, srcKey=$srcKey")
            return true
        }
        val targetDir = getModelDir(context, h5Key)
        Log.i(TAG, "开始同步 3D 车模资源: h5Key=$h5Key, srcKey=$srcKey")

        // 1. 若 H5 网页交互运行时包未就绪，下载并解压 H5 包
        val indexHtml = File(targetDir, "index.html")
        val h5Ready = File(targetDir, ".ready").exists() && indexHtml.exists() && indexHtml.length() > 0L
        if (!h5Ready) {
            val h5Ok = api.downloadCarModelPackage(h5Key, targetDir)
            if (!h5Ok) {
                Log.w(TAG, "H5 交互网页包下载失败: $h5Key")
                return false
            }
            if (File(targetDir, "index.html").exists() && File(targetDir, "index.html").length() > 0L) {
                File(targetDir, ".ready").createNewFile()
                Log.i(TAG, "H5 运行时包解压就绪: $h5Key")
            } else {
                Log.w(TAG, "3D 车模解压后缺少 index.html，标记失败: $h5Key")
                return false
            }
        }

        // 2. 若存在 srcKey，且该专属车型 3D 模型资产包未解压，下载并合并解压
        if (!srcKey.isNullOrBlank()) {
            val srcMarker = File(targetDir, ".ready_${sanitizeKey(srcKey)}")
            if (!srcMarker.exists()) {
                Log.i(TAG, "开始下载并解压车型专属 3D 模型资产包: srcKey=$srcKey")
                val srcOk = api.downloadCarModelPackage(srcKey, targetDir)
                if (srcOk) {
                    srcMarker.createNewFile()
                    Log.i(TAG, "车型专属 3D 模型资产包解压就绪并打标: $srcKey")
                } else {
                    Log.w(TAG, "3D 车体三维模型包下载失败: $srcKey")
                    return false
                }
            }
        }

        val ready = isModelReady(context, h5Key, srcKey)
        Log.i(TAG, "3D 车模同步结果: $ready (h5Key=$h5Key, srcKey=$srcKey)")
        return ready
    }
}
