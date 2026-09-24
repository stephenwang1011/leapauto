package com.leapauto.app.tiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.leapauto.app.R

/**
 * 快捷设置磁贴系统级引导助手。
 * 在 Android 13 (API 33) 及以上系统上，支持在应用内直接弹出系统级底部弹窗，
 * 引导用户一键将「寻车」、「车锁」、「空调」添加至下拉通知栏控制中心。
 */
object TilePromptHelper {

    enum class TileType(
        val title: String,
        val iconRes: Int,
        val serviceClass: Class<*>
    ) {
        HORN("寻车", R.drawable.ic_location_horn, HornTileService::class.java),
        LOCK("车锁", R.drawable.ic_phosphor_lock, LockToggleTileService::class.java),
        CLIMATE("空调", R.drawable.ic_phosphor_fan, ClimateTileService::class.java)
    }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun requestTilesUpdate(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(context, ComponentName(context, LockToggleTileService::class.java))
                TileService.requestListeningState(context, ComponentName(context, ClimateTileService::class.java))
            } catch (_: Exception) {}
        }
    }

    fun requestAddTile(context: Context, type: TileType, onResult: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val sbm = context.getSystemService(StatusBarManager::class.java) ?: run {
                    onResult?.invoke(false)
                    return
                }
                val component = ComponentName(context, type.serviceClass)
                val icon = Icon.createWithResource(context, type.iconRes)
                val executor = ContextCompat.getMainExecutor(context)

                sbm.requestAddTileService(component, type.title, icon, executor) { statusCode ->
                    val success = statusCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                    val alreadyAdded = statusCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                    if (success) {
                        Toast.makeText(context, "已成功添加【${type.title}】至下拉控制中心", Toast.LENGTH_SHORT).show()
                    } else if (alreadyAdded) {
                        Toast.makeText(context, "【${type.title}】已存在于控制中心", Toast.LENGTH_SHORT).show()
                    }
                    onResult?.invoke(success || alreadyAdded)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
                onResult?.invoke(false)
            }
        } else {
            Toast.makeText(context, "请下拉手机控制中心，点击右上角「编辑」即可手动添加", Toast.LENGTH_LONG).show()
            onResult?.invoke(false)
        }
    }
}
