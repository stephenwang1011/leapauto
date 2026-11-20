package com.leapauto.app

import android.net.Uri

object ShortcutControlPolicy {
    const val ACTION_SHORTCUT_CONTROL = "com.leapauto.app.action.SHORTCUT_CONTROL"
    const val EXTRA_COMMAND = "command"
    const val SCHEME_LEAPAUTO = "leapauto"
    const val HOST_CONTROL = "control"

    val SUPPORTED_COMMANDS = setOf(
        "unlock",
        "lock",
        "ac_on",
        "ac_off",
        "trunk",
        "trunk_open",
        "horn"
    )

    fun resolveCommand(
        commandExtra: String?,
        dataUri: Uri? = null,
        uriString: String? = null
    ): String? {
        // 1. 优先读取 Intent Extra 中的 command 参数
        if (!commandExtra.isNullOrBlank()) {
            val normalized = commandExtra.trim().lowercase()
            if (normalized in SUPPORTED_COMMANDS) {
                return normalized
            }
        }

        // 2. 兜底解析 DeepLink URI: leapauto://control?cmd=unlock 或 leapauto://control?command=unlock
        val targetUriStr = uriString ?: runCatching { dataUri?.toString() }.getOrNull()
        if (!targetUriStr.isNullOrBlank()) {
            val trimmed = targetUriStr.trim()
            val prefix = "$SCHEME_LEAPAUTO://$HOST_CONTROL"
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                val query = trimmed.substringAfter('?', "")
                if (query.isNotEmpty()) {
                    val pairs = query.split('&')
                    for (pair in pairs) {
                        val kv = pair.split('=')
                        val key = kv.getOrNull(0)?.trim()?.lowercase()
                        val value = kv.getOrNull(1)?.trim()?.lowercase()
                        if ((key == "command" || key == "cmd") && value != null) {
                            if (value in SUPPORTED_COMMANDS) {
                                return value
                            }
                        }
                    }
                }
            }
        }

        return null
    }

    fun mapToServiceCommand(command: String): String = when (command) {
        "ac_on" -> "acOn"
        "ac_off" -> "acOff"
        "trunk", "trunk_open" -> "trunkOpen"
        else -> command
    }

    fun commandLabel(command: String): String = when (command) {
        "unlock" -> "解锁车辆"
        "lock" -> "车辆上锁"
        "ac_on", "acOn" -> "开启空调"
        "ac_off", "acOff" -> "关闭空调"
        "trunk", "trunk_open", "trunkOpen" -> "开启后备箱"
        "horn" -> "鸣笛寻车"
        else -> "远程控车"
    }
}
