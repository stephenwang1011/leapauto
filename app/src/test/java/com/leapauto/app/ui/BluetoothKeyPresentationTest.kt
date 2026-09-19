package com.leapauto.app.ui

import com.leapauto.app.bluetooth.BleCloudSaveStatus
import com.leapauto.app.bluetooth.BleCloudSyncState
import com.leapauto.app.bluetooth.BleNearbyDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothKeyPresentationTest {
    private val target = "00:11:22:33:AA:BB"

    @Test
    fun matchingTargetIsPrioritizedWithoutFilteringUnknownCandidates() {
        val strongest = device("00:11:22:33:AA:CC", -35)
        val match = device(target, -90)
        val middle = device("00:11:22:33:AA:DD", -55)
        val candidates = listOf(strongest, match, middle)
        assertEquals(listOf(match, strongest, middle), BluetoothKeyPresentation.sortedCandidates(candidates, target.lowercase()))
        assertEquals(listOf(strongest, match, middle), candidates)
    }

    @Test
    fun absentOrInvalidTargetKeepsCandidatesAndDoesNotClaimAnotherVehicle() {
        val weak = device(target, -80)
        val strong = device("00:11:22:33:AA:CC", -40)
        for (unknown in listOf(null, "", "00:11:22", "not-an-address", "00:00:00:00:00:00", "FF:FF:FF:FF:FF:FF")) {
            assertFalse(BluetoothKeyPresentation.matchesTarget(target, unknown))
            assertEquals(listOf(strong, weak), BluetoothKeyPresentation.sortedCandidates(listOf(weak, strong), unknown))
        }
        assertEquals("身份待核对", BluetoothKeyPresentation.candidateLabel(false))
        assertEquals("与车辆配置一致", BluetoothKeyPresentation.candidateLabel(true))
        assertFalse(BluetoothKeyPresentation.matchesTarget("00:11:22:33:AA:CC", target))
        assertTrue(BluetoothKeyPresentation.matchesTarget(target, "00112233aabb"))
    }

    @Test
    fun cloudPersistenceNeverClaimsVehicleApplication() {
        val labels = mapOf(
            BleCloudSaveStatus.UNSAVED to "未保存",
            BleCloudSaveStatus.PENDING to "待上传",
            BleCloudSaveStatus.SAVING to "保存中",
            BleCloudSaveStatus.SAVED to "已保存",
            BleCloudSaveStatus.FAILED to "保存失败"
        )
        labels.forEach { (status, label) -> assertEquals(label, BluetoothKeyPresentation.cloudLabel(status)) }
        labels.keys.forEach { assertFalse(BluetoothKeyPresentation.cloudLabel(it).contains("车辆")) }
    }

    @Test
    fun retryIsAvailableOnlyForPendingOrFailedCloudWorkWhenNoUploadIsRunning() {
        for (configuration in BleCloudSaveStatus.entries) {
            for (calibration in BleCloudSaveStatus.entries) {
                val statuses = listOf(configuration, calibration)
                val expected = BleCloudSaveStatus.SAVING !in statuses && statuses.any {
                    it == BleCloudSaveStatus.FAILED || it == BleCloudSaveStatus.PENDING
                }
                assertEquals(expected, BluetoothKeyPresentation.canRetryCloud(
                    BleCloudSyncState(configuration = configuration, calibration = calibration)))
            }
        }
        assertTrue(BluetoothKeyPresentation.canRetryCloud(BleCloudSyncState(configuration = BleCloudSaveStatus.FAILED)))
    }

    private fun device(address: String, rssi: Int) = BleNearbyDevice(address, "synthetic", rssi, 8)
}
