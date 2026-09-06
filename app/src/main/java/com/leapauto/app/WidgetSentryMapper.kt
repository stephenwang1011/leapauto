package com.leapauto.app

import org.json.JSONObject

/** Maps sentry telemetry to the widget icon and toggle command. */
object WidgetSentryMapper {

    data class Presentation(
        val showEnabledIcon: Boolean,
        val command: String,
        val contentDescription: String
    )

    fun state(status: JSONObject): Boolean? = state(status.opt("sentryMode"))

    fun state(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> when (value.toDouble()) {
            0.0 -> false
            1.0 -> true
            else -> null
        }
        is String -> when (value.trim().lowercase()) {
            "0", "false" -> false
            "1", "true" -> true
            else -> null
        }
        else -> null
    }

    fun presentation(state: Boolean?): Presentation = Presentation(
        showEnabledIcon = state == true,
        command = SentryModeControlPolicy.commandName(state),
        contentDescription = when (state) {
            true -> "关闭哨兵模式"
            false -> "开启哨兵模式"
            null -> "开启哨兵模式，当前状态待同步"
        }
    )
}
