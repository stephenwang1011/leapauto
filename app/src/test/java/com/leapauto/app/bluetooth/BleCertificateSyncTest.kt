package com.leapauto.app.bluetooth

import com.leapauto.app.NewAuth
import com.leapauto.app.OldAuth
import com.leapauto.app.Session
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleCertificateSyncTest {
    private fun session(deviceId: String = "device-before", generation: Long = 3L) = Session(
        deviceId = deviceId,
        oldAuth = OldAuth("account", "old-token", "old-refresh", "3600", 1L),
        newAuth = NewAuth("account", "access-token", "refresh-token", "key", 100L),
        selectedVin = "SYNTHETIC-VIN", generation = generation
    )

    private val certificate = BleKeyCertificate("public-key", 1, "0123456789".repeat(8),
        "text", "signature", "SYNTHETIC-VIN")

    @Test
    fun `a request can commit credentials refreshed on its isolated session`() {
        val original = session()
        val sync = BleCertificateSync(original)
        val refreshed = session("device-after").apply {
            oldAuth = oldAuth?.copy(token = "renewed-old")
            newAuth = newAuth?.copy(accessToken = "renewed-access")
        }
        assertTrue(sync.canCommit(original, refreshed, certificate))
        assertFalse(sync.canCommit(session("different-device"), refreshed, certificate))
        assertFalse(sync.toString().contains("device-before"))
        assertFalse(sync.toString().contains("old-token"))
    }

    @Test
    fun `concurrent refresh logout vehicle or device change discards downloaded certificate`() {
        val sync = BleCertificateSync(session())
        val changed = listOf(
            session(generation = 4L), session("other-device"),
            session().apply { selectedVin = "OTHER-VIN" },
            session().apply { oldAuth = null },
            session().apply { oldAuth = oldAuth?.copy(token = "other-old-token") },
            session().apply { newAuth = newAuth?.copy(accessToken = "other-access-token") }
        )
        changed.forEach { assertFalse(sync.canCommit(it, session(), certificate)) }
    }

    @Test
    fun `refresh cannot move the result to another account vehicle or login generation`() {
        val original = session()
        val sync = BleCertificateSync(original)
        val changed = listOf(
            session(generation = 4L), session(""),
            session().apply { oldAuth = oldAuth?.copy(accountId = "other-account") },
            session().apply { selectedVin = "OTHER-VIN" }
        )
        changed.forEach { assertFalse(sync.canCommit(original, it, certificate)) }
        assertFalse(sync.canCommit(original, session(), certificate.copy(vin = "OTHER-VIN")))
    }
}
