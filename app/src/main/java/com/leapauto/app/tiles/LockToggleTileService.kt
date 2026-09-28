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
 * 下拉控制中心快捷磁贴：车锁（上锁 / 解锁）。
 * 具备锁闭状态自适应反馈：已上锁时点击安全解锁，未锁时点击一键上锁。
 */
@RequiresApi(Build.VERSION_CODES.N)
class LockToggleTileService : TileService() {

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
        // 智能车锁状态判定：快照有值以快照为准；快照无值时参考 Tile 自身激活态 (已激活代表已解锁，需触发上锁)
        val isLocked = snapshot?.locked ?: (qsTile?.state != Tile.STATE_ACTIVE)

        if (isLocked) {
            // 已上锁状态 -> 点击执行解锁
            if (isLockedState()) {
                unlockAndRun {
                    executeLockCommand("unlock")
                }
            } else {
                executeLockCommand("unlock")
            }
        } else {
            // 未上锁/已解锁状态 -> 点击执行上锁
            executeLockCommand("lock")
        }
    }

    private fun isLockedState(): Boolean = try {
        isLocked
    } catch (_: Exception) {
        false
    }

    private fun executeLockCommand(command: String) {
        val isUnlock = command == "unlock"
        val targetLockState = !isUnlock
        val store = SessionStore(applicationContext)
        val session = store.load()
        if (session.selectedVin.isNotBlank()) {
            store.updateWidgetLockState(session.selectedVin, targetLockState)
        }
        updateTileState(inProgress = true, customSubtitle = if (isUnlock) "正在解锁..." else "正在上锁...", forceTargetLock = targetLockState)

        val intent = Intent(applicationContext, ControlService::class.java).apply {
            putExtra(ControlService.EXTRA_COMMAND, command)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动门锁服务异常: ${e.message}")
        }

        mainHandler.postDelayed({
            updateTileState(inProgress = false, forceTargetLock = targetLockState)
        }, 1500L)
    }

    private fun updateTileState(
        inProgress: Boolean,
        customSubtitle: String? = null,
        forceTargetLock: Boolean? = null
    ) {
        val tile = qsTile ?: return
        val snapshot = SessionStore(applicationContext).loadSelectedWidgetSnapshot()
        val isLocked = forceTargetLock ?: snapshot?.locked ?: (tile.state != Tile.STATE_ACTIVE)

        if (inProgress) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "车锁"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = customSubtitle ?: "正在发送..."
            }
            tile.icon = Icon.createWithResource(this, if (isLocked) R.drawable.ic_phosphor_lock else R.drawable.ic_phosphor_lock_open)
        } else {
            if (isLocked) {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "车锁"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = customSubtitle ?: "已上锁 (点击解锁)"
                }
                tile.icon = Icon.createWithResource(this, R.drawable.ic_phosphor_lock)
            } else {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "车锁"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = customSubtitle ?: "已解锁 (点击上锁)"
                }
                tile.icon = Icon.createWithResource(this, R.drawable.ic_phosphor_lock_open)
            }
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
        private const val TAG = "LockToggleTileService"
    }
}
