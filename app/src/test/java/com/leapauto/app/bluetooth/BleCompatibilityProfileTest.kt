package com.leapauto.app.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BleCompatibilityProfileTest {
    @Test
    fun `current connections use one pao protocol choices as one coherent profile`() {
        val profile = BleCompatibilityProfile.forConnection()
        assertEquals(BleAuthenticationTextProfile.ONE_PAO_V010, profile.authenticationText)
        assertEquals(BleChunkProfile.ONE_PAO_V010, profile.chunkProfile)
        assertEquals(1, profile.diagnosticCode)
    }

    @Test
    fun `one pao configuration is fixed nine bytes while legacy remains versioned`() {
        val configuration = BlePassiveConfiguration(
            enabled = true,
            autoUnlock = true,
            autoLock = true,
            buttonEnabled = true
        )
        assertArrayEquals(configuration.reconnectFields(supportsButton = true),
            BleCompatibilityProfile.ONE_PAO_V010.encodeConfiguration(configuration, 8, supportsButton = true))
        assertEquals(9, BleCompatibilityProfile.ONE_PAO_V010.encodeConfiguration(configuration, 8).size)
        assertEquals(7, BleCompatibilityProfile.LEGACY.encodeConfiguration(
            configuration.copy(buttonEnabled = false), 8).size)
    }
}
