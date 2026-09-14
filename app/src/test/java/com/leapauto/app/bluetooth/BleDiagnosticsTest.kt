package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDiagnosticsTest {
    @Test
    fun protocolSourceAndRejectionStageDisambiguateDefaultMinorAndVehicleCode() {
        val explicit = BleDiagnosticEntry(0L, BleDiagnosticEvent.PROTOCOL_SELECTED, 8,
            BleProtocolMinorSource.ADVERTISED.diagnosticCode)
        val fallback = BleDiagnosticEntry(0L, BleDiagnosticEvent.PROTOCOL_SELECTED, 8,
            BleProtocolMinorSource.DEFAULT.diagnosticCode)
        assertTrue(explicit.description.contains("车辆广播"))
        assertTrue(fallback.description.contains("默认回退"))
        val rejected = BleDiagnosticEntry(100L, BleDiagnosticEvent.VEHICLE_REJECTED, 9,
            BleConnectionPhase.AUTHENTICATING.ordinal)
        assertTrue(rejected.description.contains("正在认证"))
        assertTrue(rejected.description.contains("code=9"))
        assertFalse(rejected.description.contains("证书过期"))
    }

    @Test
    fun authenticationTimeoutAndDecryptionFailureExplainTheirStage() {
        val timeout = BleDiagnosticEntry(12_000L, BleDiagnosticEvent.TIMEOUT,
            BleConnectionPhase.AUTHENTICATING.ordinal, 12_000)
        assertTrue(timeout.description.contains("正在认证"))
        val error = BleDiagnosticEntry(500L, BleDiagnosticEvent.COMMUNICATION_FAILED,
            BleProtocolFailure.CIPHERTEXT.code, BleConnectionPhase.AUTHENTICATING.ordinal)
        assertTrue(error.description.contains("蓝牙回包解密失败"))
        assertTrue(error.description.contains("正在认证"))
    }

    @Test
    fun boundedHistoryRetainsOrderedRecentEventsAndDetachedSnapshots() {
        var now = 100L
        val diagnostics = BleDiagnostics(2) { now }
        diagnostics.record(BleDiagnosticEvent.SCAN_STARTED)
        val snapshot = diagnostics.snapshot()
        now = 110L
        diagnostics.record(BleDiagnosticEvent.SCAN_DEVICE_FOUND, 9, -65)
        now = 150L
        diagnostics.record(BleDiagnosticEvent.SCAN_FINISHED, detail = 1)
        assertEquals(listOf(10L, 50L), diagnostics.snapshot().map { it.elapsedMillis })
        assertEquals(listOf(BleDiagnosticEvent.SCAN_STARTED), snapshot.map { it.event })
        assertEquals(-65, diagnostics.snapshot().first().detail)
    }

    @Test
    fun clearingRemovesPriorAccountHistoryAndResetsTimeOrigin() {
        var now = 100L
        val diagnostics = BleDiagnostics { now }
        diagnostics.record(BleDiagnosticEvent.COMMAND_STARTED, 1)
        now = 1_000L
        diagnostics.clear()
        assertTrue(diagnostics.snapshot().isEmpty())
        now = 1_030L
        diagnostics.record(BleDiagnosticEvent.CERTIFICATE_SYNCED, 1)
        assertEquals(30L, diagnostics.snapshot().single().elapsedMillis)
    }

    @Test
    fun exportedReportIncludesFailureStageAndSystemCodeWithoutDeviceIdentityFields() {
        val diagnostics = BleDiagnostics { 0L }
        diagnostics.record(BleDiagnosticEvent.GATT_STATE, 133, 0)
        diagnostics.record(BleDiagnosticEvent.COMMAND_UNCONFIRMED)
        val report = BleDiagnostics.formatReport(diagnostics.snapshot(), "3.3.26", BleConnectionPhase.FAILED)
        assertTrue(report.contains("Phase: FAILED"))
        assertTrue(report.contains("GATT_STATE"))
        assertTrue(report.contains("code=133"))
        assertTrue(report.contains("COMMAND_UNCONFIRMED"))
        for (field in listOf("VIN:", "MAC:", "passwordCard", "ecdhPublicKey", "token=")) {
            assertFalse(report.contains(field))
        }
    }
}
