package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleAccessPolicyTest {
    private val vin = "TEST-VIN-A"

    @Test
    fun `both supported key types can connect only for the current vehicle`() {
        for (type in listOf(0, 1)) {
            val certificate = certificate(type)
            assertTrue(BleAccessPolicy.isSupportedKeyType(type))
            assertTrue(BleAccessPolicy.isCertificateReady(certificate, vin))
            assertFalse(BleAccessPolicy.isCertificateReady(certificate, "TEST-VIN-B"))
            assertFalse(BleAccessPolicy.isCertificateReady(certificate, ""))
            assertTrue(BleAccessPolicy.canConnect(BleConnectionPhase.SCANNING, false,
                BleAccessPolicy.isCertificateReady(certificate, vin)))
        }
        assertFalse(BleAccessPolicy.isSupportedKeyType(null))
        assertFalse(BleAccessPolicy.isSupportedKeyType(-1))
        assertFalse(BleAccessPolicy.isSupportedKeyType(2))
    }

    @Test
    fun `missing certificate permits discovery but never connection`() {
        val ready = BleAccessPolicy.isCertificateReady(null, vin)
        assertFalse(ready)
        assertTrue(BleAccessPolicy.canScan(BleConnectionPhase.IDLE, false))
        assertTrue(BleAccessPolicy.canScan(BleConnectionPhase.FAILED, false))
        BleConnectionPhase.entries.forEach { phase ->
            assertFalse(BleAccessPolicy.canConnect(phase, false, ready))
        }
    }

    @Test
    fun `certificate synchronization excludes scanning and connecting`() {
        BleConnectionPhase.entries.forEach { phase ->
            assertFalse(BleAccessPolicy.canScan(phase, true))
            assertFalse(BleAccessPolicy.canConnect(phase, true, true))
            assertFalse(BleAccessPolicy.canSyncCertificate(phase, true))
        }
        assertFalse(BleAccessPolicy.canSyncCertificate(BleConnectionPhase.SCANNING, false))
        assertTrue(BleAccessPolicy.canConnect(BleConnectionPhase.SCANNING, false, true))
    }

    @Test
    fun `ready or in flight sessions cannot be replaced by scan sync or connect callbacks`() {
        val phases = listOf(BleConnectionPhase.CONNECTING, BleConnectionPhase.DISCOVERING,
            BleConnectionPhase.SUBSCRIBING, BleConnectionPhase.AUTHENTICATING,
            BleConnectionPhase.CONFIGURING, BleConnectionPhase.READY, BleConnectionPhase.SENDING)
        phases.forEach { phase ->
            assertFalse(BleAccessPolicy.canScan(phase, false))
            assertFalse(BleAccessPolicy.canSyncCertificate(phase, false))
            assertFalse(BleAccessPolicy.canConnect(phase, false, true))
        }
        assertFalse(BleAccessPolicy.canScan(BleConnectionPhase.SCANNING, false))
    }

    @Test
    fun `cloud lock control waits for an in flight bluetooth command`() {
        assertFalse(BleAccessPolicy.canStartCloudLockControl(BleConnectionPhase.SENDING, cloudBusy = false))
        for (phase in listOf(BleConnectionPhase.IDLE, BleConnectionPhase.FAILED, BleConnectionPhase.SCANNING,
            BleConnectionPhase.CONNECTING, BleConnectionPhase.DISCOVERING, BleConnectionPhase.SUBSCRIBING,
            BleConnectionPhase.AUTHENTICATING, BleConnectionPhase.READY)) {
            assertTrue(BleAccessPolicy.canStartCloudLockControl(phase, cloudBusy = false))
        }
    }

    @Test
    fun `cloud lock control cannot overlap a busy cloud operation`() {
        BleConnectionPhase.entries.forEach { phase ->
            assertFalse(BleAccessPolicy.canStartCloudLockControl(phase, cloudBusy = true))
        }
    }

    @Test
    fun `SM2 ready message replaces unsupported wording and mismatched vehicle stays invalid`() {
        assertEquals("SM2 钥匙已就绪", BleAccessPolicy.certificateMessage(certificate(1), vin))
        assertEquals("钥匙已就绪", BleAccessPolicy.certificateMessage(certificate(0), vin))
        assertEquals("尚未同步钥匙", BleAccessPolicy.certificateMessage(null, vin))
        assertEquals("钥匙与当前车辆不匹配，请重新同步",
            BleAccessPolicy.certificateMessage(certificate(1), "TEST-VIN-B"))
    }

    @Test
    fun `repeated status text is hidden without suppressing uncertain control results`() {
        assertNull(BleConnectionState(message = "未连接").detailMessage)
        assertNull(BleConnectionState(message = "  未连接  ").detailMessage)
        assertNull(BleConnectionState().detailMessage)
        assertEquals("蓝牙已断开，操作结果未确认",
            BleConnectionState(message = "蓝牙已断开，操作结果未确认").detailMessage)
    }

    @Test
    fun `configuration changes cannot replace any in flight bluetooth operation`() {
        val certificate = certificate(1).copy(vin = "LTEST000000000001")
        val binding = binding(certificate)
        for (phase in BleConnectionPhase.entries) {
            val state = BleConnectionState(phase = phase)
            val expected = !state.isBusy && phase != BleConnectionPhase.SCANNING
            assertEquals(phase.name, expected,
                BleAccessPolicy.canApplyConfiguration(state, binding, certificate, BlePassiveConfiguration(enabled = true)))
            assertEquals(phase.name, expected,
                BleAccessPolicy.canApplyConfiguration(state, binding, certificate, BlePassiveConfiguration()))
        }
    }

    @Test
    fun `changed or missing certificate permits a pending disable but never enables an old binding`() {
        val certificate = certificate(1).copy(vin = "LTEST000000000001")
        val binding = binding(certificate)
        for (replacement in listOf(null, certificate.copy(signResult = "changed-signature"))) {
            assertTrue(BleAccessPolicy.canApplyConfiguration(BleConnectionState(), binding, replacement,
                BlePassiveConfiguration()))
            assertFalse(BleAccessPolicy.canApplyConfiguration(BleConnectionState(), binding, replacement,
                BlePassiveConfiguration(enabled = true, autoUnlock = true)))
        }
    }

    @Test
    fun `unbound devices cannot configure passive behavior`() {
        assertFalse(BleAccessPolicy.canApplyConfiguration(BleConnectionState(), null, certificate(1),
            BlePassiveConfiguration(enabled = true)))
        assertFalse(BleAccessPolicy.canApplyConfiguration(BleConnectionState(), null, null,
            BlePassiveConfiguration()))
    }

    @Test
    fun `micro switch enable requires authenticated protocol nine or later but disable remains possible`() {
        val certificate = certificate(1).copy(vin = "LTEST000000000001")
        val configuration = BlePassiveConfiguration(enabled = true, buttonEnabled = true)
        for (minor in listOf(8, 9, 10)) {
            val binding = binding(certificate).let { it.copy(device = it.device.copy(protocolMinor = minor)) }
            assertEquals(minor >= 9,
                BleAccessPolicy.canApplyConfiguration(BleConnectionState(), binding, certificate, configuration))
            assertTrue(BleAccessPolicy.canApplyConfiguration(BleConnectionState(), binding, certificate,
                configuration.copy(enabled = false)))
        }
    }

    private fun binding(certificate: BleKeyCertificate) = BleManagedKey(
        "synthetic-account", certificate.vin, BleNearbyDevice("00:11:22:33:44:55", "test", -60, 9),
        BleKeyProtocol.certificateFingerprint(certificate)
    )

    private fun certificate(keyType: Int) = BleKeyCertificate(
        ecdhPublicKey = "synthetic-public-key",
        keyType = keyType,
        passwordCard = "A".repeat(80),
        plainText = "synthetic-certificate",
        signResult = "synthetic-signature",
        vin = vin
    )
}
