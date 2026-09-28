package com.leapauto.app.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.leapauto.app.MainActivity
import com.leapauto.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BleKeyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var runtime: BleKeyRuntime
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        runtime = BleKeyRuntime.get(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "蓝牙钥匙后台连接", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = notification("正在恢复车辆连接")
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(ID, notification)
            if (!runtime.serviceStarted()) {
                stopSelf()
                return START_NOT_STICKY
            }
            notificationJob?.cancel()
            notificationJob = scope.launch {
                combine(runtime.connection, runtime.managedKey) { state, key ->
                    when {
                        key?.pending == true && !key.desired.enabled -> "关闭待车辆确认 · ${state.phaseLabel}"
                        key?.pending == true -> "设置待车辆确认 · ${state.phaseLabel}"
                        else -> state.phaseLabel
                    }
                }.collect { text -> getSystemService(NotificationManager::class.java).notify(ID, notification(text)) }
            }
            return START_STICKY
        } catch (_: Exception) {
            runtime.serviceStopped()
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val needsBackground = runtime.managedKey.value?.needsBackground == true
        if (!needsBackground) {
            runtime.stopBackground()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        runtime.serviceStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, ID,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_KEY, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("蓝牙钥匙")
            .setContentText(text).setSmallIcon(R.drawable.ic_phosphor_key).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).build()
    }

    companion object {
        const val EXTRA_OPEN_KEY = "open_bluetooth_key_settings"
        private const val CHANNEL = "leapauto.bluetooth.key"
        private const val ID = 1033
    }
}
