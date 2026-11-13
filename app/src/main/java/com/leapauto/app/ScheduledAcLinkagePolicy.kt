package com.leapauto.app

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 定时空调到点联动关窗与关遮阳帘时间计算策略。
 * 根据设定的开启时间（如 "07:30"）与星期重复掩码，精确计算下一个到点触发的时间戳。
 */
object ScheduledAcLinkagePolicy {

    /** 刚刚过去的 3 分钟容差窗口：若用户设置的时间为当前时刻或刚过数秒，判定为立即触发而不是跳到下周 */
    const val IMMEDIATE_TOLERANCE_WINDOW_MILLIS = 3 * 60 * 1000L

    /**
     * 计算下一个触发时刻的毫秒级时间戳。
     * 若在未来 7 天内找不到匹配的星期，返回 null。
     */
    fun nextTriggerEpochMillis(
        startTime: String,
        daysMask: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        val parts = startTime.split(":")
        val hour = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(0, 23) ?: return null
        val minute = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(0, 59) ?: return null
        val daySet = ChargePlanCyclesHelper.parseToDaySet(daysMask)
        if (daySet.isEmpty()) return null

        val nowDateTime = now.toLocalDateTime()
        val nowDate = nowDateTime.toLocalDate()

        // 检查今天起往后 7 天
        for (offset in 0L..7L) {
            val candidateDate = nowDate.plusDays(offset)
            val dayOfWeek = candidateDate.dayOfWeek.value // 1=周一 .. 7=周日
            if (daySet.contains(dayOfWeek)) {
                val candidateDateTime = candidateDate.atTime(hour, minute, 0)
                val candidateMillis = candidateDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (candidateMillis > nowMillis) {
                    return candidateMillis
                }
                // 今天设定的时间刚好在 3 分钟之内（如用户刚好设置了当前的这一分钟，或保存时差了几秒），判定为立刻触发
                if (offset == 0L && (nowMillis - candidateMillis) <= IMMEDIATE_TOLERANCE_WINDOW_MILLIS) {
                    return nowMillis + 500L
                }
            }
        }
        return null
    }

    /**
     * 校验当前时刻是否匹配设定的星期（容差窗口内）。
     */
    fun isTodayScheduled(daysMask: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        val dayOfWeek = now.dayOfWeek.value
        val daySet = ChargePlanCyclesHelper.parseToDaySet(daysMask)
        return daySet.contains(dayOfWeek)
    }
}
