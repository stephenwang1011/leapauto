package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExpiryTest {
    @Test
    fun sessionExpiredDialogOnlyLogsOutAfterExplicitReLoginConfirmation() {
        assertFalse(
            SessionExpiredDialogPolicy.shouldLogout(SessionExpiredDialogAction.DISMISS_REQUESTED)
        )
        assertTrue(
            SessionExpiredDialogPolicy.shouldLogout(SessionExpiredDialogAction.CONFIRM_RELOGIN)
        )
    }

    @Test
    fun recognizesRefreshTokenFailureInVehicleError() {
        assertTrue(SessionExpiry.isRefreshTokenInvalid("车况获取失败：旧token续期失败：refresh token失效"))
        assertTrue(SessionExpiry.isRefreshTokenInvalid("旧token续期失败：refresh token失效"))
        assertTrue(SessionExpiry.isRefreshTokenInvalid("旧 token 续期失败：refresh token失效"))
        assertTrue(SessionExpiry.isRefreshTokenInvalid("refresh_token expired"))
    }

    @Test
    fun ignoresOrdinaryNetworkAndBusinessFailures() {
        assertFalse(SessionExpiry.isRefreshTokenInvalid("网络异常，请稍后重试"))
        assertFalse(SessionExpiry.isRefreshTokenInvalid("车况接口未返回 signalMap"))
        assertFalse(SessionExpiry.isRefreshTokenInvalid("access token失效"))
    }
}
