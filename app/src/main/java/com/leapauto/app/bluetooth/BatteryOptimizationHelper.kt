package com.leapauto.app.bluetooth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    /** 检查当前应用是否已被系统加入“电池优化无限制 / 白名单” */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val packageName = context.packageName
        return powerManager?.isIgnoringBatteryOptimizations(packageName) == true
    }

    /** 一键发起系统“电池优化无限制”申请弹窗或跳转设置页 */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context) {
        val packageName = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (!launched) {
                // 降级跳转到通用电池优化设置列表
                openGeneralBatterySettings(context)
            }
        } else {
            openAppDetailsSettings(context)
        }
    }

    /** 降级跳转至系统应用详情页（可开启自启动、后台活动） */
    fun openAppDetailsSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun openGeneralBatterySettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            openAppDetailsSettings(context)
        }
    }
}
