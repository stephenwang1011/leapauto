package com.leapauto.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object ScheduledAcScheduler {
    private const val TAG = "ScheduledAcScheduler"
    const val REQUEST_CODE = 1710

    fun schedule(context: Context, vin: String, plan: ScheduledAcPlan) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (!plan.enabled || (!plan.closeWindows && !plan.closeSunshade)) {
            cancel(appContext, vin)
            return
        }
        val nextTriggerMillis = ScheduledAcLinkagePolicy.nextTriggerEpochMillis(
            startTime = plan.startTime,
            daysMask = plan.days,
            nowMillis = System.currentTimeMillis()
        )
        if (nextTriggerMillis == null) {
            Log.w(TAG, "未计算到下一次定时空调联动时间，取消计划: $vin")
            cancel(appContext, vin)
            return
        }

        val intent = Intent(appContext, ScheduledAcLinkageReceiver::class.java).apply {
            action = ScheduledAcLinkageReceiver.ACTION_SCHEDULED_AC_LINKAGE
            putExtra(ScheduledAcLinkageReceiver.EXTRA_VIN, vin)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, flags)

        try {
            val showIntent = PendingIntent.getActivity(
                appContext,
                0,
                Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTriggerMillis, showIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.i(TAG, "已成功注册高优先级闹钟唤醒: vin=$vin, nextTriggerMillis=$nextTriggerMillis, time=${plan.startTime}")
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent)
                }
                Log.i(TAG, "已成功降级注册精确定时唤醒: vin=$vin, nextTriggerMillis=$nextTriggerMillis, time=${plan.startTime}")
            } catch (fallbackError: Exception) {
                Log.e(TAG, "注册定时空调联动唤醒失败: ${fallbackError.message}")
            }
        }
    }

    fun cancel(context: Context, vin: String) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, ScheduledAcLinkageReceiver::class.java).apply {
            action = ScheduledAcLinkageReceiver.ACTION_SCHEDULED_AC_LINKAGE
            putExtra(ScheduledAcLinkageReceiver.EXTRA_VIN, vin)
        }
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, flags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "已取消定时空调联动唤醒: vin=$vin")
        }
    }
}
