package com.leapauto.app

/** User-facing result text rendered in the home-screen widget after a command. */
object WidgetControlResultText {
    fun accepted(command: String): String = when (command) {
        "unlock" -> "解锁指令已发送"
        "trunkOpen" -> "后备箱开启指令已发送"
        "trunkClose" -> "后备箱关闭指令已发送"
        "sentryOn" -> "哨兵开启指令已发送"
        "sentryOff" -> "哨兵关闭指令已发送"
        else -> "控车指令已发送"
    }

    fun completed(command: String, state: String? = null): String = when (command) {
        "unlock" -> "解锁成功"
        "trunkOpen" -> "后备箱已打开"
        "trunkClose" -> "后备箱已关闭"
        "sentryOn" -> "哨兵模式已开启"
        "sentryOff" -> "哨兵模式已关闭"
        else -> state?.takeIf { it.isNotBlank() }?.let { "控车完成（$it）" } ?: "控车完成"
    }

    fun awaiting(command: String): String = when (command) {
        "trunkOpen" -> "后备箱开启指令已发送，等待车况确认"
        "trunkClose" -> "后备箱关闭指令已发送，等待车况确认"
        "sentryOn" -> "哨兵开启指令已发送，等待车况确认"
        "sentryOff" -> "哨兵关闭指令已发送，等待车况确认"
        else -> "控车指令已发送，等待车况确认"
    }
}
