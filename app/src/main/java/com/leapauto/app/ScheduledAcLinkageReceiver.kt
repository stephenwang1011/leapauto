package com.leapauto.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class ScheduledAcLinkageReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SCHEDULED_AC_LINKAGE = "com.leapauto.app.action.SCHEDULED_AC_LINKAGE"
        const val EXTRA_VIN = "extra_vin"
        private const val CHANNEL_ID = "scheduled_ac_linkage"
        private const val NOTIFICATION_ID = 1711
        private const val TAG = "ScheduledAcLinkage"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SCHEDULED_AC_LINKAGE) return
        val targetVin = intent.getStringExtra(EXTRA_VIN).orEmpty()
        val pendingResult = goAsync()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "leapauto:scheduled_ac_linkage")
        try {
            wakeLock?.acquire(60_000L)
        } catch (_: Exception) {}

        Thread {
            try {
                val appContext = context.applicationContext
                val store = SessionStore(appContext)
                val session = store.load()
                val vin = targetVin.ifBlank { session.selectedVin }
                if (vin.isBlank() || session.oldAuth == null || session.newAuth == null) {
                    Log.w(TAG, "会话无效或未登录，略过定时空调联动")
                    return@Thread
                }
                val plan = loadPlan(store, vin)
                if (!plan.enabled) {
                    Log.i(TAG, "定时空调已关闭，取消联动调度")
                    ScheduledAcScheduler.cancel(appContext, vin)
                    return@Thread
                }

                // 验证今天是否在计划周期内
                if (!ScheduledAcLinkagePolicy.isTodayScheduled(plan.days)) {
                    Log.i(TAG, "今天不在定时空调重复周期内，调度下一次")
                    ScheduledAcScheduler.schedule(appContext, vin, plan)
                    return@Thread
                }

                val savedPin = store.loadOpPassword().orEmpty()
                val api = LeapmotorApi(session)
                val actionsDone = mutableListOf<String>()

                // 1. 到点联动关闭车窗
                if (plan.closeWindows) {
                    try {
                        api.sendControl(Commands.build("windowClose"), savedPin)
                        actionsDone.add("已关闭车窗")
                        Log.i(TAG, "定时到点：已成功下发一键关窗")
                    } catch (e: Exception) {
                        Log.w(TAG, "定时到点：联动关窗失败: ${e.message}")
                    }
                }

                // 2. 到点联动关闭遮阳帘 (间隔1.2秒避免并发被拒)
                if (plan.closeSunshade) {
                    try {
                        Thread.sleep(1200)
                        api.sendControl(Commands.build("sunshadeClose"), savedPin)
                        actionsDone.add("已关闭遮阳帘")
                        Log.i(TAG, "定时到点：已成功下发关闭遮阳帘")
                    } catch (e: Exception) {
                        Log.w(TAG, "定时到点：联动关闭遮阳帘失败: ${e.message}")
                    }
                }

                // 3. 发送本地通知提示车主
                if (actionsDone.isNotEmpty()) {
                    showNotification(appContext, "定时空调到点联动", actionsDone.joinToString("，"))
                }

                // 4. 自动注册下一次触发时间
                ScheduledAcScheduler.schedule(appContext, vin, plan)
            } catch (e: Exception) {
                Log.e(TAG, "执行定时空调到点联动异常: ${e.message}", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }.start()
    }

    private fun loadPlan(store: SessionStore, vin: String): ScheduledAcPlan = ScheduledAcPlan(
        enabled = store.loadScheduledAcEnabled(vin),
        startTime = store.loadScheduledAcStartTime(vin),
        days = store.loadScheduledAcDays(vin),
        temperatureC = store.loadScheduledAcTemperature(vin),
        windLevel = store.loadScheduledAcWindLevel(vin),
        mode = ScheduledAcMode.fromName(store.loadScheduledAcMode(vin)),
        circle = AirCircle.entries.firstOrNull { it.name == store.loadScheduledAcCircle(vin) } ?: AirCircle.INNER,
        windshieldDefogging = store.loadScheduledAcDefrost(vin),
        closeWindows = store.loadScheduledAcCloseWindows(vin),
        closeSunshade = store.loadScheduledAcCloseSunshade(vin)
    )

    private fun showNotification(context: Context, title: String, content: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "定时空调联动提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "定时空调到点时联动关闭车窗与遮阳帘的状态提醒"
            }
            nm.createNotificationChannel(channel)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }
}
