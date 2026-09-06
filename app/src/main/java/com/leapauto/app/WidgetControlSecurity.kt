package com.leapauto.app

/** Routes only the widget's sensitive actions through the optional local verification gate. */
object WidgetControlSecurity {
    const val DEFAULT_VERIFICATION_ENABLED = true

    fun isSensitiveCommand(command: String): Boolean =
        command == "unlock" || command == "trunkOpen"

    fun requiresVerification(command: String, verificationEnabled: Boolean): Boolean =
        verificationEnabled && isSensitiveCommand(command)
}
