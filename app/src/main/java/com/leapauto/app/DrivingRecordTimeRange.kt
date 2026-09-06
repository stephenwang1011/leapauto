package com.leapauto.app

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class PreviousWeekTimestamps(
    val beginTime: Long,
    val endTime: Long
)

data class PurchaseToTodayTimestamps(
    val beginTime: Long,
    val endTime: Long
)

/**
 * Time window used by the recent seven-day mileage detail request.
 *
 * Unlike the legacy lifetime/previous-week contracts above, this endpoint
 * expects Unix millisecond timestamps.  Keep the unit in the type/property
 * names so a caller cannot accidentally pass the existing second values.
 */
data class RecentMileageTimestamps(
    val beginTimeMs: Long,
    val endTimeMs: Long
)

object DrivingRecordTimeRange {
    private val recentMileageZone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** Previous calendar week, Monday 00:00:00 through Sunday 23:59:59, as Unix seconds. */
    fun previousWeek(nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): PreviousWeekTimestamps {
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val previousWeekStart = currentWeekStart.minusWeeks(1)
        val begin = previousWeekStart.atStartOfDay(zoneId).toEpochSecond()
        val end = currentWeekStart.atStartOfDay(zoneId).toEpochSecond() - 1L
        return PreviousWeekTimestamps(begin, end)
    }

    /**
     * Vehicle purchase day 00:00:00 through the current instant, as Unix seconds.
     *
     * The vehicle list/session contract does not expose a purchase timestamp. When
     * one is unavailable (for example, on the first read after install), the
     * current local day is used as a deterministic test-safe fallback. Callers
     * should pass a timestamp inferred from a previously returned delivery-days
     * metric when available.
     */
    fun purchaseToToday(
        purchaseAtMs: Long?,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PurchaseToTodayTimestamps {
        val now = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val fallbackBegin = now.toLocalDate().atStartOfDay(zoneId).toEpochSecond()
        val purchaseBegin = purchaseAtMs
            ?.takeIf { it > 0L && it <= nowMs }
            ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toEpochSecond() }
            ?: fallbackBegin
        return PurchaseToTodayTimestamps(
            beginTime = purchaseBegin,
            endTime = now.toEpochSecond()
        )
    }

    /**
     * The recovered app asks the mileage detail endpoint for the local date
     * seven days ago at midnight through the current instant.  This is a
     * separate contract from [purchaseToToday]: it is deliberately expressed
     * in milliseconds and defaults to Shanghai time regardless of device
     * timezone.
     */
    fun recentMileageRange(
        nowMs: Long,
        zoneId: ZoneId = recentMileageZone
    ): RecentMileageTimestamps {
        val now = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val begin = now.toLocalDate()
            .minusDays(7)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        return RecentMileageTimestamps(
            beginTimeMs = begin,
            endTimeMs = nowMs
        )
    }
}
