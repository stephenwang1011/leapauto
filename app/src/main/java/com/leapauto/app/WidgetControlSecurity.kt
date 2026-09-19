package com.leapauto.app

/** Routes only the widget's sensitive actions through the optional local verification gate. */
object WidgetControlSecurity {
    const val DEFAULT_VERIFICATION_ENABLED = true
    const val DOUBLE_CLICK_WINDOW_MS = 3_000L
    const val HINT_DOUBLE_CLICK_PREFIX = "请在3秒内再次点击以确认"

    fun isSensitiveCommand(command: String): Boolean =
        command == "trunkOpen"

    fun requiresVerification(command: String, verificationEnabled: Boolean): Boolean =
        verificationEnabled && isSensitiveCommand(command)

    fun isDoubleClickConfirmed(
        lastCommand: String?,
        lastEpochMs: Long,
        currentCommand: String,
        currentEpochMs: Long
    ): Boolean {
        if (lastCommand != currentCommand) return false
        val diff = currentEpochMs - lastEpochMs
        return diff in 0..DOUBLE_CLICK_WINDOW_MS
    }
}
