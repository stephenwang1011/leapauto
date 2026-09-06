package com.leapauto.app

object ControlResultPollingPolicy {
    const val INITIAL_DELAY_MS = 1_000L
    const val INTERVAL_MS = 500L
    const val APP_MAX_WAIT_MS = 24_000L
    const val WIDGET_MAX_WAIT_MS = 12_000L

    val appMaxAttempts: Int = attemptsWithin(APP_MAX_WAIT_MS)
    val widgetMaxAttempts: Int = attemptsWithin(WIDGET_MAX_WAIT_MS)

    fun delayBeforeAttempt(attemptIndex: Int): Long {
        require(attemptIndex >= 0) { "attemptIndex must be non-negative" }
        return if (attemptIndex == 0) INITIAL_DELAY_MS else INTERVAL_MS
    }

    internal fun attemptsWithin(maxWaitMs: Long): Int {
        if (maxWaitMs < INITIAL_DELAY_MS) return 0
        return 1 + ((maxWaitMs - INITIAL_DELAY_MS) / INTERVAL_MS).toInt()
    }
}

/**
 * Non-blocking schedule used only by app climate commands. Values are elapsed
 * time from POST acceptance; MainActivity posts one request per scheduled tick.
 */
object ClimateControlConfirmationSchedule {
    private val resultQueryElapsedMs = longArrayOf(
        1_000L,
        2_000L,
        4_000L,
        8_000L,
        12_000L,
        16_000L,
        20_000L,
        24_000L
    )
    private val telemetryRefreshScheduleMs = longArrayOf(
        3_000L,
        6_000L,
        12_000L,
        20_000L
    )

    val resultQueryAttempts: Int = resultQueryElapsedMs.size
    val telemetryRefreshAttempts: Int = telemetryRefreshScheduleMs.size

    fun resultQueryDelayMs(attemptIndex: Int): Long? = relativeDelay(resultQueryElapsedMs, attemptIndex)

    fun telemetryRefreshElapsedMs(attemptIndex: Int): Long? =
        telemetryRefreshScheduleMs.getOrNull(attemptIndex)

    private fun relativeDelay(schedule: LongArray, attemptIndex: Int): Long? {
        val elapsed = schedule.getOrNull(attemptIndex) ?: return null
        return if (attemptIndex == 0) elapsed else elapsed - schedule[attemptIndex - 1]
    }
}
