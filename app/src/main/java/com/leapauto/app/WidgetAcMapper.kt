package com.leapauto.app

import org.json.JSONObject

/** Maps air-conditioner telemetry to the widget icon and its safe control action. */
object WidgetAcMapper {

    data class Presentation(
        val showEnabledIcon: Boolean,
        val command: String?,
        val contentDescription: String
    )

    fun state(status: JSONObject): Boolean? = state(status.opt("acSwitch"))

    fun state(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> when (value.toDouble()) {
            0.0 -> false
            1.0 -> true
            else -> null
        }
        else -> null
    }

    fun presentation(state: Boolean?): Presentation = when (state) {
        true -> Presentation(
            showEnabledIcon = true,
            command = "acOff",
            contentDescription = "关闭空调"
        )
        false -> Presentation(
            showEnabledIcon = false,
            command = "acOn",
            contentDescription = "开启空调"
        )
        null -> Presentation(
            showEnabledIcon = false,
            command = null,
            contentDescription = "空调状态未知，打开 App 查看"
        )
    }

    /** Returns a local optimistic state only after the command has been confirmed successful. */
    fun confirmedStateForCommand(command: String, executionConfirmed: Boolean): Boolean? =
        if (!executionConfirmed) {
            null
        } else {
            when (command) {
                "acOn" -> true
                "acOff" -> false
                else -> null
            }
        }
}
