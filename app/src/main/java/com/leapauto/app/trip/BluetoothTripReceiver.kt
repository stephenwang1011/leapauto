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

        val tripStore = TripStore(context)
        if (!tripStore.isTripRecordEnabled()) return

        val store = SessionStore(context)
        val session = store.load()
        if (session.selectedVin.isBlank()) return

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
        val devAddr = runCatching { device.address }.getOrNull() ?: ""
        Log.i(TAG, "检测到车载蓝牙${if (isDisconnect) "断开 (准备结算)" else "连接 (锁定起点)"}: $devName [$devAddr]")

        TripSyncWorker.enqueueSync(context, isDisconnect = isDisconnect, isConnect = isConnect)
    }

    companion object {
        private const val TAG = "BluetoothTripReceiver"
    }
}
