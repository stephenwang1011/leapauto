package com.leapauto.app.ui

object LoginUiPolicy {
    fun filterPhoneNumber(raw: String): String = raw.filter(Char::isDigit).take(11)

    fun filterSmsCode(raw: String): String = raw.filter(Char::isDigit).take(6)

    fun canSendSms(phone: String, smsCountdownSeconds: Int, busy: Boolean): Boolean =
        smsCountdownSeconds == 0 && phone.length == 11 && !busy

    fun canSubmitLogin(phone: String, code: String, busy: Boolean): Boolean =
        phone.length == 11 && code.length >= 4 && !busy

    inline fun handleSendSmsClick(
        performHaptic: () -> Unit,
        requestCodeFocus: () -> Unit,
        showKeyboard: () -> Unit,
        onSendSms: () -> Unit
    ) {
        performHaptic()
        requestCodeFocus()
        showKeyboard()
        onSendSms()
    }
}
