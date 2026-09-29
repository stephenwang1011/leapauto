package com.leapauto.app.bluetooth

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleKeyBootReceiverTest {

    private val accountId = "test-account"
    private val vin = "TESTVIN0000000001"

    @Test
    fun `auto start succeeds on boot completed with valid session and background enabled`() {
        val allowed = BleKeyBootPolicy.shouldAutoStart(
            action = Intent.ACTION_BOOT_COMPLETED,
            accountId = accountId,
            selectedVin = vin,
            hasOpPassword = true,
            needsBackground = true
        )
        assertTrue(allowed)
    }

    @Test
    fun `auto start succeeds on package replaced after app update`() {
        val allowed = BleKeyBootPolicy.shouldAutoStart(
            action = Intent.ACTION_MY_PACKAGE_REPLACED,
            accountId = accountId,
            selectedVin = vin,
            hasOpPassword = true,
            needsBackground = true
        )
        assertTrue(allowed)
    }

    @Test
    fun `auto start rejected when user has not enabled background key`() {
        val allowed = BleKeyBootPolicy.shouldAutoStart(
            action = Intent.ACTION_BOOT_COMPLETED,
            accountId = accountId,
            selectedVin = vin,
            hasOpPassword = true,
            needsBackground = false
        )
        assertFalse(allowed)
    }

    @Test
    fun `auto start rejected when op password is missing`() {
        val allowed = BleKeyBootPolicy.shouldAutoStart(
            action = Intent.ACTION_BOOT_COMPLETED,
            accountId = accountId,
            selectedVin = vin,
            hasOpPassword = false,
            needsBackground = true
        )
        assertFalse(allowed)
    }

    @Test
    fun `auto start rejected when unauthenticated or missing vin`() {
        assertFalse(
            BleKeyBootPolicy.shouldAutoStart(
                action = Intent.ACTION_BOOT_COMPLETED,
                accountId = null,
                selectedVin = vin,
                hasOpPassword = true,
                needsBackground = true
            )
        )
        assertFalse(
            BleKeyBootPolicy.shouldAutoStart(
                action = Intent.ACTION_BOOT_COMPLETED,
                accountId = accountId,
                selectedVin = "",
                hasOpPassword = true,
                needsBackground = true
            )
        )
    }

    @Test
    fun `auto start rejected on unrelated intent actions`() {
        val allowed = BleKeyBootPolicy.shouldAutoStart(
            action = "android.intent.action.BATTERY_LOW",
            accountId = accountId,
            selectedVin = vin,
            hasOpPassword = true,
            needsBackground = true
        )
        assertFalse(allowed)
    }
}
