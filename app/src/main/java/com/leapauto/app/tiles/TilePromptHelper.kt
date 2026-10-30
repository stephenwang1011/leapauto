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
 * 引导用户一键将「鸣笛寻车」、「车辆门锁」、「车辆空调」添加至下拉通知栏控制中心。
 */
object TilePromptHelper {

    enum class TileType(
        val title: String,
        val iconRes: Int,
        val serviceClass: Class<*>
    ) {
        HORN("鸣笛寻车", R.drawable.ic_location_horn, HornTileService::class.java),
        LOCK("车辆门锁", R.drawable.ic_phosphor_lock, LockToggleTileService::class.java),
        CLIMATE("车辆空调", R.drawable.ic_phosphor_fan, ClimateTileService::class.java)
    }

    private const val PREFS_NAME = "quick_tiles_state"
    private const val KEY_TILE_ADDED_PREFIX = "tile_added_"

    fun isTileAdded(context: Context, type: TileType): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TILE_ADDED_PREFIX + type.name, false)
    }

    fun setTileAdded(context: Context, type: TileType, added: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TILE_ADDED_PREFIX + type.name, added).apply()
    }

    fun promptRemoveTile(context: Context, type: TileType, onStateChanged: (() -> Unit)? = null) {
        setTileAdded(context, type, false)
        onStateChanged?.invoke()
        Toast.makeText(
            context,
            "请下拉通知栏控制中心 -> 点击「编辑」即可移出【${type.title}】",
            Toast.LENGTH_LONG
        ).show()
    }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun requestTilesUpdate(context: Context) {
        try {
            TileService.requestListeningState(context, ComponentName(context, LockToggleTileService::class.java))
            TileService.requestListeningState(context, ComponentName(context, ClimateTileService::class.java))
        } catch (_: Exception) {}
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
                    if (success || alreadyAdded) {
                        setTileAdded(context, type, true)
                    }
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
