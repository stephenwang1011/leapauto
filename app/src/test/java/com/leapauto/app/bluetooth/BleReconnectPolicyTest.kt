package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleReconnectPolicyTest {
    @Test
    fun `reconnection backs off and stays capped during extended absence`() {
        assertEquals(listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L, 30_000L),
            (0..5).map(BleReconnectPolicy::delayMillis))
        assertEquals(30_000L, BleReconnectPolicy.delayMillis(Int.MAX_VALUE))
        assertEquals(2_000L, BleReconnectPolicy.delayMillis(-1))
    }

    @Test
    fun `probe delay prioritizes in-car media and foreground fast discovery`() {
        // 车载媒体连接时：无论前台还是后台，均让出信道至 30 秒
        assertEquals(30_000L, BleReconnectPolicy.probeDelayMillis(foreground = true, inCarMediaActive = true, consecutiveMissCount = 0))
        assertEquals(30_000L, BleReconnectPolicy.probeDelayMillis(foreground = false, inCarMediaActive = true, consecutiveMissCount = 5))

        // 前台打开应用时：保持 1.5 秒极速探测窗口，秒级连车
        assertEquals(1_500L, BleReconnectPolicy.probeDelayMillis(foreground = true, inCarMediaActive = false, consecutiveMissCount = 0))
        assertEquals(1_500L, BleReconnectPolicy.probeDelayMillis(foreground = true, inCarMediaActive = false, consecutiveMissCount = 10))

        // 后台静默模式：断开前 3 次平滑阶梯探测 (1.5s -> 3s -> 5s)
        assertEquals(1_500L, BleReconnectPolicy.probeDelayMillis(foreground = false, inCarMediaActive = false, consecutiveMissCount = 0))
        assertEquals(3_000L, BleReconnectPolicy.probeDelayMillis(foreground = false, inCarMediaActive = false, consecutiveMissCount = 1))
        assertEquals(5_000L, BleReconnectPolicy.probeDelayMillis(foreground = false, inCarMediaActive = false, consecutiveMissCount = 2))

        // 车主正在走开 (3..5 次)：8 秒休眠
        assertEquals(8_000L, BleReconnectPolicy.probeDelayMillis(foreground = false, inCarMediaActive = false, consecutiveMissCount = 3))
        assertEquals(8_000L, BleReconnectPolicy.probeDelayMillis(foreground = false, inCarMediaActive = false, consecutiveMissCount = 5))

        // 确认远离车身 (>5 次)：12 秒均衡休眠省电且保证走到车旁秒连
        assertEquals(12_000L, BleReconnectPolicy.probeDelayMillis(foreground = false, inCarMediaActive = false, consecutiveMissCount = 6))
        assertEquals(12_000L, BleReconnectPolicy.probeDelayMillis(foreground = false, inCarMediaActive = false, consecutiveMissCount = 100))
    }

    @Test
    fun `direct fallback is prohibited in foreground and only enabled periodically in background`() {
        // 前台打开应用时：永远禁止发起直接物理连接，彻底杜绝 15 秒卡死盲区
        assertFalse(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = true, consecutiveMissCount = 0))
        assertFalse(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = true, consecutiveMissCount = 5))
        assertFalse(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = true, consecutiveMissCount = 10))
        assertFalse(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = true, consecutiveMissCount = 15))

        // 后台静默运行且每 5 次未命中时：允许自愈直连
        assertFalse(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = false, consecutiveMissCount = 0))
        assertFalse(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = false, consecutiveMissCount = 4))
        assertTrue(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = false, consecutiveMissCount = 5))
        assertFalse(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = false, consecutiveMissCount = 6))
        assertTrue(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = false, consecutiveMissCount = 10))
    }
}
