package com.leapauto.app.lockscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.leapauto.app.MainActivity
import com.leapauto.app.R
import com.leapauto.app.SessionStore
import com.leapauto.app.SessionStore.VehiclePowerType
import com.leapauto.app.SessionStore.WidgetSnapshot

object LockscreenNotificationFormatter {

    data class ActionDescriptor(
        val command: String,
        val label: String,
        val iconRes: Int
    )

    fun formatTitle(carType: String?, locked: Boolean?): String {
        val carName = carType?.takeIf { it.isNotBlank() } ?: "零跑汽车"
        val lockDesc = when (locked) {
            true -> "已上锁"
            false -> "未上锁"
            else -> "已连接"
        }
        return "$carName · $lockDesc"
    }

    fun formatBody(
        snapshot: WidgetSnapshot?,
        statusText: String? = null
    ): String {
        if (statusText != null) {
            return if (snapshot != null) {
                "$statusText · 续航 ${snapshot.range}km (${snapshot.soc}%)"
            } else {
                statusText
            }
        }
        if (snapshot == null) {
            return "等待数据同步 · 锁屏控车已就绪"
        }
        val rangeInfo = if (snapshot.powerType == VehiclePowerType.RANGE_EXTENDER) {
            "综合续航 ${snapshot.range}km · 电量 ${snapshot.soc}%"
        } else {
            "续航 ${snapshot.range}km (${snapshot.soc}%)"
        }
        val prefix = if (snapshot.statusLabel.isNotBlank()) "${snapshot.statusLabel} · " else ""
        val suffix = if (snapshot.updated.isNotBlank()) " · ${snapshot.updated}" else ""
        return "$prefix$rangeInfo$suffix"
    }

    fun resolveLockAction(locked: Boolean?): ActionDescriptor =
        if (locked == true) {
            ActionDescriptor("unlock", "解锁", R.drawable.ic_phosphor_lock_open)
        } else {
            ActionDescriptor("lock", "落锁", R.drawable.ic_phosphor_lock)
        }

    fun resolveAcAction(acEnabled: Boolean?): ActionDescriptor =
        if (acEnabled == true) {
            ActionDescriptor("acOff", "关空调", R.drawable.ic_phosphor_fan)
        } else {
            ActionDescriptor("acOn", "开空调", R.drawable.ic_phosphor_fan)
        }

    fun resolveHornAction(): ActionDescriptor =
        ActionDescriptor("horn", "寻车", R.drawable.ic_phosphor_bell_ringing)

    fun resolveRefreshAction(): ActionDescriptor =
        ActionDescriptor("refresh", "刷新", R.drawable.ic_phosphor_arrow_clockwise)
}

object LockscreenControlNotificationManager {

    private const val TAG = "LockscreenNotif"
    const val CHANNEL_ID = "lockscreen_control"
    const val NOTIFICATION_ID = 2001
    const val ACTION_LOCKSCREEN_CONTROL = "com.leapauto.app.action.LOCKSCREEN_CONTROL"
    const val EXTRA_COMMAND = "command"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "锁屏常驻控制",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "在锁屏界面常驻显示车况并提供快捷控车按钮"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun buildNotification(context: Context, statusText: String? = null): Notification? {
        val store = SessionStore(context)
        if (!store.loadLockscreenControlEnabled()) {
            return null
        }
        val session = store.load()
        val vin = session.selectedVin
        if (vin.isBlank() || session.oldAuth == null) {
            return null
        }

        val snapshot: WidgetSnapshot? = store.loadWidgetSnapshot(vin)
        val carName = snapshot?.carType?.takeIf { it.isNotBlank() }
            ?: session.selectedCarType.takeIf { it.isNotBlank() }
            ?: "零跑汽车"

        val title = LockscreenNotificationFormatter.formatTitle(carName, snapshot?.locked)
        val body = LockscreenNotificationFormatter.formatBody(snapshot, statusText)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lockAction = LockscreenNotificationFormatter.resolveLockAction(snapshot?.locked)
        val hornAction = LockscreenNotificationFormatter.resolveHornAction()
        val acAction = LockscreenNotificationFormatter.resolveAcAction(snapshot?.acEnabled)
        val refreshAction = LockscreenNotificationFormatter.resolveRefreshAction()

        val lockPendingIntent = createActionPendingIntent(context, lockAction.command, 101)
        val hornPendingIntent = createActionPendingIntent(context, hornAction.command, 102)
        val acPendingIntent = createActionPendingIntent(context, acAction.command, 103)
        val refreshPendingIntent = createActionPendingIntent(context, refreshAction.command, 104)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phosphor_car)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(lockAction.iconRes, lockAction.label, lockPendingIntent)
            .addAction(hornAction.iconRes, hornAction.label, hornPendingIntent)
            .addAction(acAction.iconRes, acAction.label, acPendingIntent)
            .addAction(refreshAction.iconRes, refreshAction.label, refreshPendingIntent)
            .build()
    }

    private fun createActionPendingIntent(context: Context, command: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, LockscreenActionReceiver::class.java).apply {
            action = ACTION_LOCKSCREEN_CONTROL
            putExtra(EXTRA_COMMAND, command)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun updateNotification(context: Context, statusText: String? = null) {
        val store = SessionStore(context)
        if (!store.loadLockscreenControlEnabled()) {
            cancelNotification(context)
            return
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        try {
            ensureChannel(context)
            val notification = buildNotification(context, statusText)
            val nm = context.getSystemService(NotificationManager::class.java)
            if (notification != null) {
                nm?.notify(NOTIFICATION_ID, notification)
            } else {
                nm?.cancel(NOTIFICATION_ID)
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新锁屏常驻控制通知失败: ${e.message}")
        }
    }

    fun cancelNotification(context: Context) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "取消锁屏常驻控制通知失败: ${e.message}")
        }
    }
}
