package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ControlResultPollingPolicyTest {
    @Test
    fun `polling waits one second then polls every half second`() {
        assertEquals(1_000L, ControlResultPollingPolicy.delayBeforeAttempt(0))
        assertEquals(500L, ControlResultPollingPolicy.delayBeforeAttempt(1))
        assertEquals(500L, ControlResultPollingPolicy.delayBeforeAttempt(20))
        assertThrows(IllegalArgumentException::class.java) {
            ControlResultPollingPolicy.delayBeforeAttempt(-1)
        }
    }

    @Test
    fun `app and widget preserve their existing maximum wait windows`() {
        assertEquals(47, ControlResultPollingPolicy.appMaxAttempts)
        assertEquals(23, ControlResultPollingPolicy.widgetMaxAttempts)
        assertEquals(
            ControlResultPollingPolicy.APP_MAX_WAIT_MS,
            elapsedBeforeLastAttempt(ControlResultPollingPolicy.appMaxAttempts)
        )
        assertEquals(
            ControlResultPollingPolicy.WIDGET_MAX_WAIT_MS,
            elapsedBeforeLastAttempt(ControlResultPollingPolicy.widgetMaxAttempts)
        )
    }

    @Test
    fun `climate result polling uses sparse non blocking delays through twenty four seconds`() {
        assertEquals(8, ClimateControlConfirmationSchedule.resultQueryAttempts)
        val elapsed = (0 until ClimateControlConfirmationSchedule.resultQueryAttempts)
            .mapNotNull(ClimateControlConfirmationSchedule::resultQueryDelayMs)
            .runningReduce(Long::plus)

        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 12_000L, 16_000L, 20_000L, 24_000L),
            elapsed
        )
        assertNull(
            ClimateControlConfirmationSchedule.resultQueryDelayMs(
                ClimateControlConfirmationSchedule.resultQueryAttempts
            )
        )
    }

    @Test
    fun `climate telemetry refreshes are bounded and stop after twenty seconds`() {
        assertEquals(4, ClimateControlConfirmationSchedule.telemetryRefreshAttempts)
        assertEquals(
            listOf(3_000L, 6_000L, 12_000L, 20_000L),
            (0 until ClimateControlConfirmationSchedule.telemetryRefreshAttempts)
                .mapNotNull(ClimateControlConfirmationSchedule::telemetryRefreshElapsedMs)
        )
        assertNull(
            ClimateControlConfirmationSchedule.telemetryRefreshElapsedMs(
                ClimateControlConfirmationSchedule.telemetryRefreshAttempts
            )
        )
    }

    private fun elapsedBeforeLastAttempt(attempts: Int): Long =
        (0 until attempts).sumOf(ControlResultPollingPolicy::delayBeforeAttempt)
}
