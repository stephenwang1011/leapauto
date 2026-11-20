package com.leapauto.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Android Shortcuts 及小爱同学语音指令透明中转 Activity。
 * 解析快捷方式与 DeepLink 参数后，安全校验会话并提交给 ControlService 前台服务后台执行，
 * 自身立即退出，不遮挡用户当前屏幕。
 */
class ShortcutControlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawCommand = intent.getStringExtra(ShortcutControlPolicy.EXTRA_COMMAND)
        val dataUri = intent.data
        val resolved = ShortcutControlPolicy.resolveCommand(rawCommand, dataUri)

        if (resolved == null) {
            Toast.makeText(this, "未识别的控车指令", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val store = SessionStore(this)
        val session = store.load()

        if (session.oldAuth == null || session.newAuth == null) {
            Toast.makeText(this, "请先打开零跑智控登录账号", Toast.LENGTH_LONG).show()
            val loginIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(loginIntent)
            finish()
            return
        }

        if (session.selectedVin.isBlank()) {
            Toast.makeText(this, "未选择车辆，请先打开 App 选择车辆", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val pin = store.loadOpPassword()
        if (pin.isNullOrBlank()) {
            Toast.makeText(this, "未设置操作密码，请先在 App 内设置", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val serviceCommand = ShortcutControlPolicy.mapToServiceCommand(resolved)
        val label = ShortcutControlPolicy.commandLabel(resolved)
        Toast.makeText(this, "正在${label}…", Toast.LENGTH_SHORT).show()

        val serviceIntent = Intent(this, ControlService::class.java).apply {
            putExtra(ControlService.EXTRA_COMMAND, serviceCommand)
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "启动控车服务失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
