package com.leapauto.app.tiles

import android.app.PendingIntent
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
 * 下拉控制中心快捷磁贴：座舱空调（一键自动恒温 24℃ / 关空调）。
 */
@RequiresApi(Build.VERSION_CODES.N)
class ClimateTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(inProgress = false)
        val snapshot = SessionStore(applicationContext).loadSelectedWidgetSnapshot()
        if (snapshot == null || System.currentTimeMillis() - snapshot.lastSuccessAt > 60_000L) {
            com.leapauto.app.ControlWidget.enqueueSync(applicationContext)
        }
    }

    override fun onClick() {
        super.onClick()
        val context = applicationContext
        val store = SessionStore(context)
        val session = store.load()

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

        val snapshot = store.loadSelectedWidgetSnapshot()
        val isAcOn = snapshot?.acEnabled ?: false
        val targetCommand = if (isAcOn) "acOff" else "acOn"

        updateTileState(inProgress = true, customSubtitle = if (isAcOn) "正在关闭空调..." else "正在开启24℃空调...")

        val intent = Intent(context, ControlService::class.java).apply {
            putExtra(ControlService.EXTRA_COMMAND, targetCommand)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动空调服务异常: ${e.message}")
        }

        mainHandler.postDelayed({
            updateTileState(inProgress = false)
        }, 2500L)
    }

    private fun updateTileState(inProgress: Boolean, customSubtitle: String? = null) {
        val tile = qsTile ?: return
        val snapshot = SessionStore(applicationContext).loadSelectedWidgetSnapshot()
        val isAcOn = snapshot?.acEnabled == true

        if (inProgress) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "空调"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = customSubtitle ?: "正在发送..."
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_phosphor_fan)
        } else {
            tile.state = if (isAcOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "空调"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = customSubtitle ?: if (isAcOn) "运行中 (点击关闭)" else "已关闭 (点击开启)"
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_phosphor_fan)
        }
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
        private const val TAG = "ClimateTileService"
    }
}
