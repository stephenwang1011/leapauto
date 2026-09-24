package com.leapauto.app.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.leapauto.app.ControlService
import com.leapauto.app.ControlWidget
import com.leapauto.app.SessionStore

class LockscreenActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LockscreenAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(LockscreenControlNotificationManager.EXTRA_COMMAND) ?: return
        Log.d(TAG, "收到锁屏快捷操作: $command")

        if (command == "refresh") {
            LockscreenControlNotificationManager.updateNotification(context, statusText = "正在刷新车况...")
            ControlWidget.enqueueCommandSync(context)
            return
        }

        val store = SessionStore(context)
        val session = store.load()
        if (session.oldAuth == null || session.newAuth == null) {
            LockscreenControlNotificationManager.updateNotification(context, statusText = "请打开 App 登录")
            return
        }
        val pin = store.loadOpPassword()
        if (pin.isNullOrBlank()) {
            LockscreenControlNotificationManager.updateNotification(context, statusText = "未设置操作密码")
            return
        }

        val hint = when (command) {
            "lock" -> "正在落锁..."
            "unlock" -> "正在解锁..."
            "horn" -> "正在寻车鸣笛..."
            "acOn" -> "正在开空调..."
            "acOff" -> "正在关空调..."
            else -> "正在发送控车指令..."
        }
        LockscreenControlNotificationManager.updateNotification(context, statusText = hint)

        val serviceIntent = Intent(context, ControlService::class.java).apply {
            putExtra(ControlService.EXTRA_COMMAND, command)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动控车服务异常: ${e.message}")
            LockscreenControlNotificationManager.updateNotification(context, statusText = "启动服务失败")
        }
    }
}
