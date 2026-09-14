package com.leapauto.app.bluetooth

import org.junit.Assert.*
import org.junit.Test

class BleConnectionStateTest {
    @Test
    fun `vehicle rejection names its phase without guessing the meaning of code nine`() {
        assertEquals("车辆拒绝钥匙认证（结果码 9）",
            BleConnectionState(BleConnectionPhase.AUTHENTICATING).rejectionMessage(9))
        assertEquals("车辆拒绝钥匙设置同步（结果码 9）",
            BleConnectionState(BleConnectionPhase.CONFIGURING).rejectionMessage(9))
        assertEquals("车辆拒绝车辆操作（结果码 9）",
            BleConnectionState(BleConnectionPhase.SENDING).rejectionMessage(9))
        assertEquals("车辆拒绝蓝牙请求（结果码 255）",
            BleConnectionState(BleConnectionPhase.READY).rejectionMessage(255))
    }

    @Test
    fun `connection and authentication transitions cannot enable controls early`() {
        BleConnectionPhase.entries.forEach { phase ->
            assertEquals(phase == BleConnectionPhase.READY, BleConnectionState(phase).canControl)
        }
    }

    @Test
    fun `command requires current matching action after write and before timeout`() {
        val tracker = BleCommandTracker()
        assertTrue(tracker.begin(BleLockAction.LOCK, 100L))
        assertFalse(tracker.begin(BleLockAction.UNLOCK, 101L))
        assertNull(tracker.confirm(BleLockAction.LOCK, 102L))
        tracker.markWriteStarted()
        assertNull(tracker.confirm(BleLockAction.UNLOCK, 103L))
        assertEquals(BleCommandOutcome.CONFIRMED, tracker.confirm(BleLockAction.LOCK, 104L))
        assertNull(tracker.confirm(BleLockAction.LOCK, 105L))
        assertFalse(tracker.begin(BleLockAction.LOCK, 106L))
    }

    @Test
    fun `disconnect and timeout after writing remain unknown`() {
        val tracker = BleCommandTracker()
        tracker.begin(BleLockAction.UNLOCK, 1L)
        tracker.markWriteStarted()
        assertNull(tracker.confirm(BleLockAction.UNLOCK, 6_001L))
        assertEquals(BleCommandOutcome.UNKNOWN, tracker.end())
        assertNull(tracker.confirm(BleLockAction.UNLOCK, 6_002L))
        assertNull(tracker.end())
    }

    @Test
    fun `cancelling before any write is distinguishable from an uncertain result`() {
        val tracker = BleCommandTracker()
        tracker.begin(BleLockAction.LOCK, 1L)
        assertEquals(BleCommandOutcome.NOT_SENT, tracker.end())
    }

    @Test
    fun `account vehicle generation and device changes invalidate callbacks`() {
        val identity = BleSessionIdentity("account-a", "TEST-VIN-A", 5L, "device-a")
        assertTrue(identity.matches("account-a", "TEST-VIN-A", 5L, "device-a"))
        assertFalse(identity.matches("account-b", "TEST-VIN-A", 5L, "device-a"))
        assertFalse(identity.matches("account-a", "TEST-VIN-B", 5L, "device-a"))
        assertFalse(identity.matches("account-a", "TEST-VIN-A", 6L, "device-a"))
        assertFalse(identity.matches(null, "TEST-VIN-A", 5L, "device-a"))
        assertFalse(identity.matches("account-a", "TEST-VIN-A", 5L, "device-b"))
        assertNotEquals(identity, identity.copy(deviceId = "device-b"))
        assertFalse(BleSessionIdentity("", "", 5L, "").matches("", "", 5L, ""))
    }

    @Test
    fun `permissions use nearby devices on Android 12 and location only on older Android`() {
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
            BlePermissionPolicy.requiredPermissions(30))
        val nearby = listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
        assertEquals(nearby, BlePermissionPolicy.requiredPermissions(31))
        assertEquals(nearby, BlePermissionPolicy.requiredPermissions(35))
    }

    @Test
    fun `credential namespace cannot mix accounts vehicles or ambiguous concatenations`() {
        val first = BleCredentialScope.storageKey("user-A", "VIN-A")
        assertEquals(first, BleCredentialScope.storageKey("user-A", "VIN-A"))
        assertNotEquals(first, BleCredentialScope.storageKey("user-B", "VIN-A"))
        assertNotEquals(first, BleCredentialScope.storageKey("user-A", "VIN-B"))
        assertNotEquals(BleCredentialScope.storageKey("ab", "c"), BleCredentialScope.storageKey("a", "bc"))
        assertFalse(first.contains("user-A"))
        assertFalse(first.contains("VIN-A"))
    }
}
