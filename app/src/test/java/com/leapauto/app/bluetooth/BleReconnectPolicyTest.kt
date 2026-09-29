package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
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
}
