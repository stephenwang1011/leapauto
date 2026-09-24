package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDiagnosticsTest {
    @Test
    fun targetChecksAndCloudConfirmationRemainSeparateFromVehicleAuthentication() {
        assertEquals(0, targetMatchCode("00:11:22:33:AA:BB", null))
        assertEquals(1, targetMatchCode("00:11:22:33:AA:BB", "00:11:22:33:aa:bb"))
        assertEquals(2, targetMatchCode("00:11:22:33:AA:BB", "00:11:22:33:AA:CC"))
        val entries = listOf(
            BleDiagnosticEntry(0, BleDiagnosticEvent.TARGET_MATCH, 2, 1),
            BleDiagnosticEntry(1, BleDiagnosticEvent.AUTH_MODE, 0),
            BleDiagnosticEntry(2, BleDiagnosticEvent.AUTH_PROFILE, 1),
            BleDiagnosticEntry(3, BleDiagnosticEvent.TRANSPORT_PROFILE, 1),
            BleDiagnosticEntry(4, BleDiagnosticEvent.AUTH_IDENTITY_PLACEHOLDERS, 3),
            BleDiagnosticEntry(5, BleDiagnosticEvent.CALIBRATION_SOURCE, 1),
            BleDiagnosticEntry(6, BleDiagnosticEvent.CLOUD_SAVE_RESULT, 1, 1)
        )
        val report = BleDiagnostics.formatReport(entries, "test", BleConnectionPhase.FAILED)
        for (meaning in listOf("云端=不一致", "绑定=一致", "完整认证 AAAE", "1PAO 0.10 固定字段",
                "MTU优先", "分片上限197", "空占位不代表身份错误", "本机自定义", "不代表车辆已应用")) {
            assertTrue(report.contains(meaning))
        }
        assertFalse(report.contains("00:11:22"))
        assertTrue(entries.none { it.event == BleDiagnosticEvent.AUTHENTICATED })
    }

    @Test
    fun authenticationStructureExportsOnlyNumbersWithFieldComparisonMeaning() {
        val diagnostics = BleDiagnostics { 0L }
        val structure = BleAuthenticationStructure(8, 3, 120, 64, 188, 192, 9, 15)
        diagnostics.recordAuthentication(structure)
        val entries = diagnostics.snapshot()
        assertEquals(listOf(BleDiagnosticEvent.AUTH_CERTIFICATE_STRUCTURE, BleDiagnosticEvent.AUTH_TEXT_STRUCTURE,
            BleDiagnosticEvent.AUTH_CIPHER_STRUCTURE, BleDiagnosticEvent.AUTH_CONFIGURATION), entries.map { it.event })
        assertEquals(listOf(8, 120, 188, 9), entries.map { it.code })
        assertEquals(listOf(3, 64, 192, 15), entries.map { it.detail })
        assertTrue(BleAuthenticationStructure::class.java.declaredFields.all { it.type == Int::class.javaPrimitiveType })
        val report = BleDiagnostics.formatReport(entries, "3.3.52", BleConnectionPhase.AUTHENTICATING)
        for (meaning in listOf("原字段数=8", "身份匹配位=3", "1=原账号相同", "2=原设备相同", "不代表证书有效性",
            "UTF8文本字节=120", "签名字节=64", "加密前字节=188", "密文字节=192", "协议版本=9", "发送标志位=15")) {
            assertTrue(report.contains(meaning))
        }
    }

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
