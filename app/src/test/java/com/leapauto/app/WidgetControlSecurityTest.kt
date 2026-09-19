package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetControlSecurityTest {

    @Test
    fun `missing preference defaults to verification for sensitive widget actions`() {
        assertTrue(WidgetControlSecurity.DEFAULT_VERIFICATION_ENABLED)
        assertFalse(WidgetControlSecurity.requiresVerification("unlock", true))
        assertTrue(WidgetControlSecurity.requiresVerification("trunkOpen", true))
    }

    @Test
    fun `explicitly disabled preference sends only sensitive actions directly`() {
        assertFalse(WidgetControlSecurity.requiresVerification("unlock", false))
        assertFalse(WidgetControlSecurity.requiresVerification("trunkOpen", false))
    }

    @Test
    fun `unlock does not require sensitive action verification`() {
        assertFalse(WidgetControlSecurity.isSensitiveCommand("unlock"))
        assertFalse(WidgetControlSecurity.requiresVerification("unlock", true))
        assertFalse(WidgetControlSecurity.requiresVerification("unlock", false))
    }

    @Test
    fun `only trunkOpen is marked as sensitive command`() {
        assertTrue(WidgetControlSecurity.isSensitiveCommand("trunkOpen"))
        listOf("unlock", "lock", "acOn", "acOff", "trunkClose", "windowVent", "windowOpen", "windowClose").forEach { command ->
            assertFalse(WidgetControlSecurity.isSensitiveCommand(command))
        }
    }

    @Test
    fun `other widget controls never use this verification gate`() {
        listOf("lock", "acOn", "acOff", "trunkClose").forEach { command ->
            assertFalse(WidgetControlSecurity.requiresVerification(command, true))
            assertFalse(WidgetControlSecurity.requiresVerification(command, false))
        }
    }

    @Test
    fun `double click confirmation validates window and matching command`() {
        val now = 10_000L
        // 第一次点击后，在 1.5 秒内再次点击同一命令：确认通过
        assertTrue(WidgetControlSecurity.isDoubleClickConfirmed("trunkOpen", now, "trunkOpen", now + 1_500L))
        // 刚好在 3 秒边界点击：确认通过
        assertTrue(WidgetControlSecurity.isDoubleClickConfirmed("trunkOpen", now, "trunkOpen", now + 3_000L))
        // 超过 3 秒窗口（如 3001ms）：超时失效，不予通过
        assertFalse(WidgetControlSecurity.isDoubleClickConfirmed("trunkOpen", now, "trunkOpen", now + 3_001L))
        // 命令不匹配（先点 unlock 再点 trunkOpen）：不予通过
        assertFalse(WidgetControlSecurity.isDoubleClickConfirmed("unlock", now, "trunkOpen", now + 1_000L))
        // 之前无点击记录（lastCommand 为 null）：不予通过
        assertFalse(WidgetControlSecurity.isDoubleClickConfirmed(null, 0L, "trunkOpen", now))
    }
}
