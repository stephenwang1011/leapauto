package com.leapauto.app.bluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.leapauto.app.SessionStore

/**
 * 手机开机自启动与应用升级恢复策略。
 * 纯业务逻辑提取，方便进行严格的 JVM 单元测试覆盖。
 */
object BleKeyBootPolicy {
    fun shouldAutoStart(
        action: String?,
        accountId: String?,
        selectedVin: String?,
        hasOpPassword: Boolean,
        needsBackground: Boolean
    ): Boolean {
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return false
        }
        if (accountId.isNullOrBlank() || selectedVin.isNullOrBlank()) {
            return false
        }
        if (!hasOpPassword) {
            return false
        }
        return needsBackground
    }
}

/**
 * 手机开机自启动与应用升级覆盖广播接收器。
 * 当手机开机重启或 App 更新完成时，若用户已启用后台蓝牙钥匙，自动在后台拉起服务，实现真正免打开 App 的无感进入。
 */
class BleKeyBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val sessions = SessionStore(appContext)
        val session = sessions.load()
        val accountId = session.oldAuth?.accountId
        val vin = session.selectedVin
        val hasOpPassword = !sessions.loadOpPassword().isNullOrBlank()

        val keys = BleManagedKeyStore(appContext)
        val key = if (!accountId.isNullOrBlank() && vin.isNotBlank()) {
            keys.load(accountId, vin, session.generation, session.deviceId)
        } else null
        val needsBackground = key?.needsBackground == true

        if (!BleKeyBootPolicy.shouldAutoStart(action, accountId, vin, hasOpPassword, needsBackground)) {
            return
        }

        runCatching {
            val serviceIntent = Intent(appContext, BleKeyService::class.java)
            appContext.startForegroundService(serviceIntent)
            Log.i(TAG, "成功自启动后台蓝牙钥匙服务")
        }.onFailure { e ->
            Log.e(TAG, "自启动后台蓝牙钥匙服务失败: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BleKeyBootReceiver"
    }
}
