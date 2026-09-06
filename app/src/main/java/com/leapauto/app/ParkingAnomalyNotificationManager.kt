package com.leapauto.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** One-shot local notification emitted after an accepted lock command. */
object ParkingAnomalyNotificationManager {
    private const val CHANNEL_ID = "parking_anomaly"
    private const val NOTIFICATION_ID = 2201

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "停车异常提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "锁车后检查车窗是否关闭"
            }
        )
    }

    fun notifyIfNeeded(
        context: Context,
        store: SessionStore,
        carType: String,
        status: JSONObject
    ) {
        if (!store.loadParkingAnomalyNotificationsEnabled()) return
        val openWindows = WidgetStatusMapper.openWindowLabels(status, carType)
        if (openWindows.isEmpty()) return

        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phosphor_warning)
            .setColor(0xFFFF3B30.toInt())
            .setContentTitle("停车异常")
            .setContentText("${openWindows.joinToString("、")}窗未关")
            .setStyle(Notification.BigTextStyle().bigText("锁车后检测到${openWindows.joinToString("、")}窗未关，请及时检查。"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android 13+ can deny POST_NOTIFICATIONS; the in-app status remains available.
        }
    }
}
