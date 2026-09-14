package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ShumeiSecurityManagerTest {

    @Test
    fun `getDeviceId returns null gracefully when not running on android device`() {
        // On JVM tests without Android Context and native libsmsdk.so,
        // it must return null gracefully without throwing an uncaught exception.
        val deviceId = ShumeiSecurityManager.getDeviceId()
        assertNull(deviceId)
    }

    @Test
    fun `requireDeviceId explains that initialization has not run`() {
        val error = assertThrows(DeviceSecurityException::class.java) {
            ShumeiSecurityManager.requireDeviceId()
        }
        assertEquals(DeviceSecurityFailure.NOT_INITIALIZED, error.failure)
        assertEquals(DeviceSecurityFailure.NOT_INITIALIZED.userMessage, error.message)
    }
}
