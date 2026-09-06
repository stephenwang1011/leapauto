package com.leapauto.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Emits local notifications for meaningful charging state transitions. */
object ChargeNotificationManager {
    private const val CHANNEL_ID = "vehicle_charge_status"
    private const val COMPLETED_ID = 2101
    private const val PROBLEM_ID = 2102

    fun process(context: Context, store: SessionStore, vin: String, status: JSONObject) {
        val state = ChargeStatus.state(status)
        val previousState = store.loadLastChargeState(vin)
        val completed = ChargeStatus.completed(status)

        if (store.loadChargeNotificationsEnabled() && previousState != null) {
            when {
                ChargeNotificationPolicy.shouldNotifyCompleted(previousState, completed) ->
                    notify(context, COMPLETED_ID, "充电已完成", "动力电池充电已完成，请确认后再断开充电。")
                ChargeNotificationPolicy.shouldNotifyFault(previousState, state) ->
                    notify(context, PROBLEM_ID, "充电发生故障", "请打开零跑智控查看车辆充电状态。")
                ChargeNotificationPolicy.shouldNotifyInterrupted(previousState, state) ->
                    notify(context, PROBLEM_ID, "充电已中断", "车辆当前${ChargeStatus.label(state)}，请确认充电连接。")
            }
        }
        store.saveLastChargeState(vin, state)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "车辆充电提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "充电完成、中断和故障提醒"
            }
        )
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phosphor_battery_charging)
            .setColor(0xFF0066FF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (_: SecurityException) {
            // Android 13+ can deny POST_NOTIFICATIONS; the in-app status remains available.
        }
    }

}

/** Emits local charging alerts only for observed state transitions of one vehicle. */
internal object ChargeNotificationPolicy {
    fun shouldNotifyCompleted(previousState: Int?, completed: Boolean): Boolean =
        previousState == 1 && completed

    fun shouldNotifyFault(previousState: Int?, currentState: Int?): Boolean =
        previousState == 1 && currentState == 3

    fun shouldNotifyInterrupted(previousState: Int?, currentState: Int?): Boolean =
        previousState == 1 && currentState in setOf(0, 6)
}
