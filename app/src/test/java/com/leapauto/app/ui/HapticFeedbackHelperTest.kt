package com.leapauto.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticFeedbackHelperTest {

    @Test
    fun `performMechanicalClick handles null view and context without throwing exception`() {
        var completed = false
        try {
            HapticFeedbackHelper.performMechanicalClick(null, null)
            completed = true
        } catch (_: Exception) {
            completed = false
        }
        assertTrue("performMechanicalClick 必须具备空安全容错能力", completed)
    }

    @Test
    fun `vibrator fallback respects system haptic setting and avoids double vibration`() {
        assertTrue(HapticFeedbackHelper.shouldUseVibratorFallback(vibratedByView = false, systemHapticEnabled = true))
        assertFalse(HapticFeedbackHelper.shouldUseVibratorFallback(vibratedByView = false, systemHapticEnabled = false))
        assertFalse(HapticFeedbackHelper.shouldUseVibratorFallback(vibratedByView = true, systemHapticEnabled = true))
        assertFalse(HapticFeedbackHelper.shouldUseVibratorFallback(vibratedByView = true, systemHapticEnabled = false))
    }
}
