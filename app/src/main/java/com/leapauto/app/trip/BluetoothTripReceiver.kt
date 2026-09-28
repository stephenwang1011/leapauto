package com.leapauto.app.trip

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.leapauto.app.SessionStore

/** 监听系统车载蓝牙连接与断开，实现后台 0 触碰自动行程闭环 */
class BluetoothTripReceiver : BroadcastReceiver() {

    @android.annotation.SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED && action != BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            return
        }

        val device = runCatching {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        }.getOrNull() ?: return

        val store = SessionStore(context)
        val session = store.load()
        if (session.selectedVin.isBlank()) return

        val devAddr = runCatching { device.address }.getOrNull().orEmpty()
        val boundKey = com.leapauto.app.bluetooth.BleManagedKeyStore(context)
            .load(session.oldAuth?.accountId.orEmpty(), session.selectedVin, session.generation, session.deviceId)
        val keyAddress = boundKey?.device?.address

        // 1. 系统级蓝牙钥匙硬件唤醒：即使 App 曾被划掉，一旦手机底层与已配对钥匙建立物理接触，立即自愈拉起钥匙服务
        val isKeyDevice = keyAddress?.isNotBlank() == true && devAddr.equals(keyAddress, ignoreCase = true)
        if (action == BluetoothDevice.ACTION_ACL_CONNECTED && isKeyDevice) {
            if (boundKey?.needsBackground == true && !store.loadOpPassword().isNullOrBlank()) {
                Log.i(TAG, "系统底层硬件检测到已配对车辆蓝牙钥匙接触 [$devAddr]，立即自愈拉起无感连接服务")
                runCatching {
                    context.startForegroundService(Intent(context, com.leapauto.app.bluetooth.BleKeyService::class.java))
                }
            }
        }

        // 2. 车载多媒体连接与自动行程结算 (需车主开启行程记录开关)
        val tripStore = TripStore(context)
        if (!tripStore.isTripRecordEnabled()) return

        val targetMac = store.loadVehicleBluetoothMac(session.selectedVin)
        val carType = session.selectedCarType

        val isVehicleMatched = BluetoothVehicleTripMatcher.isMatchingVehicle(
            device = device,
            targetVinMac = targetMac,
            selectedCarType = carType
        )

        if (!isVehicleMatched) {
            return
        }

        val isDisconnect = action == BluetoothDevice.ACTION_ACL_DISCONNECTED
        val isConnect = action == BluetoothDevice.ACTION_ACL_CONNECTED
        val devName = runCatching { device.name }.getOrNull() ?: "车载设备"
        Log.i(TAG, "检测到车载蓝牙${if (isDisconnect) "断开 (准备结算)" else "连接 (锁定起点)"}: $devName [$devAddr]")

        TripSyncWorker.enqueueSync(context, isDisconnect = isDisconnect, isConnect = isConnect)

        // 车载多媒体连接联动钥匙静默避让：连上音乐时让出射频信道，断开时立即复原灵敏感应
        runCatching {
            com.leapauto.app.bluetooth.BleKeyRuntime.get(context).setInCarMediaActive(isConnect)
        }
    }

    companion object {
        private const val TAG = "BluetoothTripReceiver"
    }
}
