package com.leapauto.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthyChargingPresentationTest {
    @Test
    fun endedAndPausedRequireKnownFullSoc() {
        for (state in listOf(2, 6)) {
            for (soc in listOf(null, "未知", "--", "80%", "90%", "99%", "99.9%", "NaN", "101%")) {
                assertEquals("充电已停止", formatChargeRemainTime("0分钟", state, soc))
            }
            assertEquals("已充满", formatChargeRemainTime(null, state, "100%"))
            assertEquals("已充满", formatChargeRemainTime("30分钟", state, " 100 % "))
        }
    }

    @Test
    fun activeChargingUsesOnlyPositiveDurationOtherwiseChargingLabel() {
        for (soc in listOf(null, "未知", "80%", "99%", "100%")) {
            for (time in listOf(null, "0分钟", "0", "0时0分", "已充满", "--", "-5", "NaN")) {
                assertEquals("充电中", formatChargeRemainTime(time, 1, soc))
            }
            assertEquals("45分", formatChargeRemainTime("45分钟", 1, soc))
            assertEquals("1时20分", formatChargeRemainTime("约1小时20分钟", 1, soc))
        }
    }

    @Test
    fun waitingFaultDisconnectedAndUnknownAreNotCompletion() {
        assertEquals("预约充电等待", formatChargeRemainTime("0分钟", 4, "100%"))
        assertEquals("充电故障", formatChargeRemainTime("已充满", 3, "100%"))
        assertEquals("未充电", formatChargeRemainTime("已充满", 0, "100%"))
        assertEquals("充电状态未知", formatChargeRemainTime(null, null, null))
        assertEquals("充电状态未知", formatChargeRemainTime("0分钟", 99, "100%"))
    }
}
