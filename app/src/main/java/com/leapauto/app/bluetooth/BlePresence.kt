package com.leapauto.app.bluetooth

import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

internal class PresenceSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("ble_presence", Context.MODE_PRIVATE)
    var address: String?
        get() = prefs.getString("address", null)
        set(value) { prefs.edit().putString("address", value).apply() }
    var paused: Boolean
        get() = prefs.getBoolean("paused", false)
        set(value) { prefs.edit().putBoolean("paused", value).apply() }

    fun observe(enable: Boolean) {
        val target = address ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = requireNotNull(context.getSystemService(CompanionDeviceManager::class.java))
            if (enable) manager.startObservingDevicePresence(target)
            else manager.stopObservingDevicePresence(target)
        }
    }
}

internal fun acceptsPresence(target: String?, event: String, bound: String?, paused: Boolean): Boolean =
    !paused && target != null && target.equals(event, true) && target.equals(bound, true)

@RequiresApi(31)
class BlePresenceService : CompanionDeviceService() {
    override fun onDeviceAppeared(address: String) = BleKeyRuntime.get(this).presenceEvent(address, true)
    override fun onDeviceDisappeared(address: String) = BleKeyRuntime.get(this).presenceEvent(address, false)
}
