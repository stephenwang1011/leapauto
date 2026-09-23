package com.leapauto.app.trip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.leapauto.app.LeapmotorApi
import com.leapauto.app.MainActivity
import com.leapauto.app.R
import com.leapauto.app.SessionStore
import com.leapauto.app.VehicleStatusMapper
import java.util.concurrent.TimeUnit

/** 蓝牙连接与断开触发的后台行程静默同步工作者 */
class TripSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext
        val isDisconnectTrigger = inputData.getBoolean(KEY_IS_DISCONNECT, false)
        val isConnectTrigger = inputData.getBoolean(KEY_IS_CONNECT, false)

        val store = SessionStore(context)
        val tripStore = TripStore(context)
        if (!tripStore.isTripRecordEnabled()) return Result.success()

        val session = store.load()
        if (session.oldAuth == null || session.newAuth == null || session.selectedVin.isBlank()) {
            return Result.success()
        }

        return try {
            val api = LeapmotorApi(session)
            // 尝试静默拉取云端蓝牙 MAC 缓存并保存
            runCatching {
                if (store.loadVehicleBluetoothMac(session.selectedVin) == null) {
                    val meta = api.getBluetoothVehicleMetadata()
                    meta?.address?.let { mac ->
                        store.saveVehicleBluetoothMac(session.selectedVin, mac)
                    }
                }
            }

            val status = api.getVehicleState()
            store.save(session)

            val totalMileage = status.opt("totalMileage")?.toString()
            val preciseSoc = VehicleStatusMapper.displayPreciseSoc(status.opt("preciseSoc"))
                ?: status.opt("preciseSoc")?.toString()
                ?: status.opt("soc")?.toString()
            val speed = status.opt("speed")?.toString()
            val gearStatus = status.opt("gearStatus")?.toString()
            val speedValue = speed?.toDoubleOrNull() ?: 0.0
            val isDriving = (status.opt("isDriving") as? Boolean) ?: (speedValue > 1.0)
            val isShutDown = if (isConnectTrigger) false else (isDisconnectTrigger || status.optInt("vehicleState", -1) == 0)
            val currentAddress = TripLocationHelper.resolveAddressFromStatus(status)

            val completed = tripStore.processTelemetry(
                vin = session.selectedVin,
                totalMileageStr = totalMileage,
                socStr = preciseSoc,
                speedStr = speed,
                gearStatus = gearStatus,
                isDriving = if (isDisconnectTrigger) false else isDriving,
                isShutDown = isShutDown,
                isBluetoothConnectTrigger = isConnectTrigger,
                currentAddress = currentAddress,
                nowEpochMs = System.currentTimeMillis()
            )

            if (completed != null) {
                notifyTripSummary(context, completed)
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "后台静默行程同步异常: ${e.message}")
            Result.success()
        }
    }

    private fun notifyTripSummary(context: Context, trip: TripRecord) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "trip_summary_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "行程记录报告", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "自驾行程结束时发送里程与能耗汇总通知"
                }
                nm.createNotificationChannel(channel)
            }

            val energyStr = if (trip.energyConsumptionKwhPer100Km != null) {
                " · 能耗 ${trip.energyConsumptionKwhPer100Km}kWh/100km"
            } else ""

            val content = "行驶 ${trip.distanceKm}km · 耗时 ${trip.formattedDuration} · 均速 ${trip.avgSpeedKmh}km/h$energyStr"

            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_TRIP_JOURNAL, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                trip.id.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_trip_route)
                .setContentTitle("自驾行程已记录")
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(trip.id.hashCode(), notification)
        }
    }

    companion object {
        private const val TAG = "TripSyncWorker"
        const val EXTRA_OPEN_TRIP_JOURNAL = "open_trip_journal"
        private const val KEY_IS_DISCONNECT = "key_is_disconnect"
        private const val KEY_IS_CONNECT = "key_is_connect"

        fun enqueueSync(context: Context, isDisconnect: Boolean, isConnect: Boolean = !isDisconnect) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<TripSyncWorker>()
                .setInputData(workDataOf(
                    KEY_IS_DISCONNECT to isDisconnect,
                    KEY_IS_CONNECT to isConnect
                ))
                .setConstraints(constraints)
                .apply {
                    if (isDisconnect) {
                        // 熄火断开连接延迟 15 秒执行，防偶发瞬间重连抖动
                        setInitialDelay(15, TimeUnit.SECONDS)
                    }
                }
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
