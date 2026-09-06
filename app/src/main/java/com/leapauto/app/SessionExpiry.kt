package com.leapauto.app

/** Identifies terminal refresh-token failures without treating ordinary errors as logout. */
object SessionExpiry {
    fun isRefreshTokenInvalid(message: String?): Boolean {
        val value = message.orEmpty().lowercase()
        val refreshTokenFailure = value.contains("refresh token") ||
            value.contains("refresh_token") || value.contains("refreshtoken") || value.contains("续期")
        val invalid = listOf("失效", "无效", "过期", "expired", "invalid", "failed", "failure", "失败")
            .any(value::contains)
        return refreshTokenFailure && invalid
    }
}

/** User actions for the blocking session-expired dialog. */
enum class SessionExpiredDialogAction {
    DISMISS_REQUESTED,
    CONFIRM_RELOGIN,
}

/** Keeps logout behind the explicit re-login confirmation button. */
object SessionExpiredDialogPolicy {
    fun shouldLogout(action: SessionExpiredDialogAction): Boolean =
        action == SessionExpiredDialogAction.CONFIRM_RELOGIN
}
