package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduledAcLinkagePolicyTest {

    private val zone = ZoneId.systemDefault()

    @Test
    fun `calculates next trigger today when scheduled time is in the future`() {
        // 设定当前时间：2026-11-13 07:00:00 (周五)
        val now = LocalDateTime.of(2026, 11, 13, 7, 0, 0)
            .atZone(zone).toInstant().toEpochMilli()

        // 预约 07:30，全周
        val nextTrigger = ScheduledAcLinkagePolicy.nextTriggerEpochMillis("07:30", "1,1,1,1,1,1,1", now)
        assertNotNull(nextTrigger)

        val expected = LocalDateTime.of(2026, 11, 13, 7, 30, 0)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, nextTrigger)
    }

    @Test
    fun `calculates next trigger tomorrow when today's scheduled time has already passed`() {
        // 设定当前时间：2026-11-13 08:00:00 (周五，设定的 07:30 已过)
        val now = LocalDateTime.of(2026, 11, 13, 8, 0, 0)
            .atZone(zone).toInstant().toEpochMilli()

        // 预约 07:30，全周
        val nextTrigger = ScheduledAcLinkagePolicy.nextTriggerEpochMillis("07:30", "1,1,1,1,1,1,1", now)
        assertNotNull(nextTrigger)

        // 下一次应顺延至周六 07:30
        val expected = LocalDateTime.of(2026, 11, 14, 7, 30, 0)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, nextTrigger)
    }

    @Test
    fun `skips weekend when days mask is configured for weekdays only`() {
        // 设定当前时间：2026-11-13 08:00:00 (周五下午，周五设定的 07:30 已过)
        val now = LocalDateTime.of(2026, 11, 13, 8, 0, 0)
            .atZone(zone).toInstant().toEpochMilli()

        // 预约 07:30，工作日（周一至周五：1,1,1,1,1,0,0）
        val nextTrigger = ScheduledAcLinkagePolicy.nextTriggerEpochMillis("07:30", "1,1,1,1,1,0,0", now)
        assertNotNull(nextTrigger)

        // 跳过周六(14日)与周日(15日)，下一次应为周一(16日) 07:30
        val expected = LocalDateTime.of(2026, 11, 16, 7, 30, 0)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, nextTrigger)
    }

    @Test
    fun `returns null for invalid time format`() {
        val now = System.currentTimeMillis()
        assertNull(ScheduledAcLinkagePolicy.nextTriggerEpochMillis("invalid", "1,1,1,1,1,1,1", now))
        assertNull(ScheduledAcLinkagePolicy.nextTriggerEpochMillis("", "1,1,1,1,1,1,1", now))
    }

    @Test
    fun `isTodayScheduled correctly verifies day of week against mask`() {
        // 2026-11-13 是周五 (DayOfWeek.FRIDAY = 5)
        val friday = LocalDateTime.of(2026, 11, 13, 10, 0, 0)
            .atZone(zone).toInstant().toEpochMilli()

        // 工作日掩码包含周五
        assertTrue(ScheduledAcLinkagePolicy.isTodayScheduled("1,1,1,1,1,0,0", friday))
        // 周末掩码不包含周五
        assertFalse(ScheduledAcLinkagePolicy.isTodayScheduled("0,0,0,0,0,1,1", friday))
    }

    @Test
    fun `triggers immediately when today scheduled time is within 3 minute tolerance window`() {
        // 设定设定的时间是 07:30，而当前时间是 07:31:00 (刚过 1 分钟)
        val now = LocalDateTime.of(2026, 11, 13, 7, 31, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val nextTrigger = ScheduledAcLinkagePolicy.nextTriggerEpochMillis("07:30", "1,1,1,1,1,1,1", now)
        assertNotNull(nextTrigger)
        assertEquals(now + 500L, nextTrigger)
    }
}
