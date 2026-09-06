package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorSupportReminderTest {

    @Test
    fun `shows when no prompt was shown today`() {
        assertTrue(
            AuthorSupportReminder.shouldShow(
                lastShownEpochDay = 20_000L,
                todayEpochDay = 20_001L,
                permanentlyDisabled = false
            )
        )
    }

    @Test
    fun `does not show twice on the same day`() {
        assertFalse(
            AuthorSupportReminder.shouldShow(
                lastShownEpochDay = 20_001L,
                todayEpochDay = 20_001L,
                permanentlyDisabled = false
            )
        )
    }

    @Test
    fun `does not show after the user disables reminders`() {
        assertFalse(
            AuthorSupportReminder.shouldShow(
                lastShownEpochDay = 20_000L,
                todayEpochDay = 20_001L,
                permanentlyDisabled = true
            )
        )
    }
}
