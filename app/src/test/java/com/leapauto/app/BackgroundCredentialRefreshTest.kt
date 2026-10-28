package com.leapauto.app

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class BackgroundCredentialRefreshTest {
    @Test fun `ordinary clients retain permission to refresh credentials`() {
        val api = LeapmotorApi(session(), { error("unused") }, { error("unused") })
        LeapmotorApi::class.java.getDeclaredMethod("requireCredentialRefreshAllowed")
            .apply { isAccessible = true }.invoke(api)
    }

    private fun session() = Session(
        deviceId = "fixture-device", selectedVin = "fixture-vehicle",
        oldAuth = OldAuth("fixture-account", "old", "refresh", "3600", System.currentTimeMillis()),
        newAuth = NewAuth("fixture-account", "access", "refresh", "key", System.currentTimeMillis() + 3_600_000),
        route = RouteData("https://unused.invalid", "https://unused.invalid")
    )

    private fun backgroundApi(session: Session) = LeapmotorApi(
        session, { error("Must not request fingerprint") },
        { error("Must not submit authentication request") }, allowCredentialRefresh = false
    )

    @Test fun `near expiry telemetry defers at existing access leeway before any request`() {
        val live = session()
        live.newAuth = requireNotNull(live.newAuth).copy(accessTokenExpiresAt = System.currentTimeMillis() + 60_000)
        val before = live.newAuth
        assertThrows(BackgroundCredentialRefreshDeferredException::class.java) {
            backgroundApi(live).getVehicleState()
        }
        assertEquals(before, live.newAuth)
    }

    @Test fun `near expiry old credential defers but fresh old credential passes existing check`() {
        val live = session()
        val api = backgroundApi(live)
        api.ensureFreshOldToken()
        live.oldAuth = requireNotNull(live.oldAuth).copy(tokenExpired = "30")
        val before = live.oldAuth
        assertThrows(BackgroundCredentialRefreshDeferredException::class.java) { api.ensureFreshOldToken() }
        assertEquals(before, live.oldAuth)
    }

    @Test fun `all rotation entrypoints including auth failure fallback reject before network`() {
        val live = session()
        val beforeOld = live.oldAuth
        val beforeNew = live.newAuth
        val api = backgroundApi(live)
        // Exercise the same entrypoint used after an auth rejection without sending a real request.
        listOf("refreshAccessTokenWithFallback", "refreshGatewayToken", "refreshOldToken", "exchangeNewGateway")
            .forEach { name ->
                val method = LeapmotorApi::class.java.getDeclaredMethod(name).apply { isAccessible = true }
                val error = assertThrows(InvocationTargetException::class.java) { method.invoke(api) }
                assertTrue(name, error.cause is BackgroundCredentialRefreshDeferredException)
            }
        assertEquals(beforeOld, live.oldAuth)
        assertEquals(beforeNew, live.newAuth)
    }
}
