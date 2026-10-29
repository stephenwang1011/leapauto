package com.leapauto.app.ui

import org.junit.Assert.*
import org.junit.Test

class HomeLocationPillTest {
    @Test
    fun `missing key shows setup even before an address is available`() {
        for (address in listOf(null, "", "  ")) {
            val pill = homeLocationPill(false, address)!!
            assertEquals("位置服务待配置", pill.label)
            assertTrue(pill.opensConfiguration)
        }
    }

    @Test
    fun `clearing configuration replaces cached address with setup entry`() {
        val pill = homeLocationPill(false, "cached address")!!
        assertEquals("位置服务待配置", pill.label)
        assertTrue(pill.opensConfiguration)
    }

    @Test
    fun `configured key preserves address and parking destination`() {
        val pill = homeLocationPill(true, "existing address")!!
        assertEquals("existing address", pill.label)
        assertFalse(pill.opensConfiguration)
    }

    @Test
    fun `configured key without an address keeps existing hidden state`() {
        for (address in listOf(null, "", " \t ")) {
            assertNull(homeLocationPill(true, address))
        }
    }

    @Test
    fun `saving and clearing configuration changes destination with the label`() {
        assertTrue(homeLocationPill(false, "address")!!.opensConfiguration)
        assertFalse(homeLocationPill(true, "address")!!.opensConfiguration)
        assertTrue(homeLocationPill(false, "address")!!.opensConfiguration)
    }
}
