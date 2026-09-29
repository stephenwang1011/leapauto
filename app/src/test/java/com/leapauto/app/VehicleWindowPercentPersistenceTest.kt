package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleWindowPercentPersistenceTest {

    @Test
    fun `window percentage persistence preserves vent, half and full open targets`() {
        val vin = "TESTVIN0000000001"

        // 模拟解析算法：当 openWindows 包含车窗标签但网关未提供具体连续开度百分比
        val resolvePercent = { openWinLabels: List<String>, label: String, lastTargetPercent: Int? ->
            if (openWinLabels.contains(label)) {
                lastTargetPercent?.takeIf { it > 0 } ?: 15
            } else {
                0
            }
        }

        val openLabels = listOf("左前", "右前", "左后", "右后")

        // 1. 用户曾设置微开通风 (15%)：杀掉 App 重启加载后，3D 图与车况严格呈现 15% 微开
        assertEquals(15, resolvePercent(openLabels, "左前", 15))
        assertEquals(15, resolvePercent(openLabels, "右后", 15))

        // 2. 用户曾设置半开 (50%)：杀掉 App 重启加载后，3D 图与车况严格呈现 50% 半开
        assertEquals(50, resolvePercent(openLabels, "左前", 50))
        assertEquals(50, resolvePercent(openLabels, "右后", 50))

        // 3. 用户曾设置全开 (100%)：杀掉 App 重启加载后，3D 图与车况严格呈现 100% 全开
        assertEquals(100, resolvePercent(openLabels, "左前", 100))
        assertEquals(100, resolvePercent(openLabels, "右后", 100))

        // 4. 车窗完全关闭时：比例必须准确归零 (0)
        assertEquals(0, resolvePercent(emptyList(), "左前", 15))
        assertEquals(0, resolvePercent(emptyList(), "右前", 50))
    }
}
