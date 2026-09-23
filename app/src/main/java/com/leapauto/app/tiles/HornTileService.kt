package com.leapauto.app.tiles

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.leapauto.app.ControlService
import com.leapauto.app.R
import com.leapauto.app.SessionStore

/**
 * 下拉控制中心快捷磁贴：寻车（鸣笛闪灯）。
 * 具备极高实用价值：支持锁屏状态下免解锁直接盲按，快速在地库寻找车辆。
 */
@RequiresApi(Build.VERSION_CODES.N)
class HornTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(inProgress = false)
    }

    override fun onClick() {
        super.onClick()
        val context = applicationContext
        val store = SessionStore(context)
        val session = store.load()

        // 基础前置凭据校验
        if (session.oldAuth == null && session.newAuth == null) {
            updateTileState(inProgress = false, customSubtitle = "请先登录")
            showAppLogin()
            return
        }
        if (session.selectedVin.isBlank()) {
            updateTileState(inProgress = false, customSubtitle = "未选择车辆")
            return
        }
        if (store.loadOpPassword().isNullOrBlank()) {
            updateTileState(inProgress = false, customSubtitle = "未设置密码")
            return
        }

        // 触发展开动效与高亮状态反馈
        updateTileState(inProgress = true, customSubtitle = "正在寻车...")

        // 下发鸣笛闪灯车控指令
        val intent = Intent(context, ControlService::class.java).apply {
            putExtra(ControlService.EXTRA_COMMAND, "horn")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动寻车服务异常: ${e.message}")
        }

        // 2 秒后复位状态
        mainHandler.postDelayed({
            updateTileState(inProgress = false)
        }, 2200L)
    }

    private fun updateTileState(inProgress: Boolean, customSubtitle: String? = null) {
        val tile = qsTile ?: return
        tile.state = if (inProgress) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "寻车"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = customSubtitle ?: if (inProgress) "正在鸣笛..." else "鸣笛闪灯"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_location_horn)
        tile.updateTile()
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun showAppLogin() {
        try {
            val appIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(this, 0, appIntent, PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(appIntent)
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "HornTileService"
    }
}
