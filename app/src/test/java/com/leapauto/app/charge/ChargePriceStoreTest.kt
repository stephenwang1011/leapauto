package com.leapauto.app.charge

import org.junit.Assert.assertEquals
import org.junit.Test

class ChargePriceStoreTest {

    @Test
    fun `computeCost rounds to two decimal places correctly`() {
        // 54.27 kWh * 0.60 = 32.562 -> 32.56
        assertEquals(32.56, ChargePriceStore.computeCost(54.27, 0.60), 0.001)

        // 30.5 kWh * 1.50 = 45.75 -> 45.75
        assertEquals(45.75, ChargePriceStore.computeCost(30.5, 1.50), 0.001)

        // 50.0 kWh * 1.234 = 61.7 -> 61.70
        assertEquals(61.70, ChargePriceStore.computeCost(50.0, 1.234), 0.001)

        // 边界
        assertEquals(0.0, ChargePriceStore.computeCost(0.0, 1.50), 0.001)
        assertEquals(0.0, ChargePriceStore.computeCost(54.27, 0.0), 0.001)
    }

    @Test
    fun `computePeriodCost accurately calculates weighted monthly and annual cost`() {
        // 月度场景：54.3 度，0% 快充，慢充 0.60，快充 1.50 -> 54.3 * 0.60 = 32.58
        assertEquals(32.58, ChargePriceStore.computePeriodCost(54.3, 0.0, 1.50, 0.60), 0.001)

        // 年度场景：2953.5 度，25% 快充，慢充 0.60，快充 1.50
        // 快充: 2953.5 * 0.25 = 738.375 度 * 1.50 = 1107.56
        // 慢充: 2953.5 * 0.75 = 2215.125 度 * 0.60 = 1329.08
        // 总额: 1107.56 + 1329.08 = 2436.64
        assertEquals(2436.64, ChargePriceStore.computePeriodCost(2953.5, 25.0, 1.50, 0.60), 0.001)

        // 100% 快充
        assertEquals(150.0, ChargePriceStore.computePeriodCost(100.0, 100.0, 1.50, 0.60), 0.001)

        // 边界：0 度电
        assertEquals(0.0, ChargePriceStore.computePeriodCost(0.0, 25.0, 1.50, 0.60), 0.001)
    }

    @Test
    fun `formatEnergyDisplay and formatCostDisplay format numbers gracefully`() {
        assertEquals("0", ChargePriceStore.formatEnergyDisplay(0.0))
        assertEquals("54.3", ChargePriceStore.formatEnergyDisplay(54.3))
        assertEquals("2953.5", ChargePriceStore.formatEnergyDisplay(2953.5))
        assertEquals("12850", ChargePriceStore.formatEnergyDisplay(12850.4))

        assertEquals("0", ChargePriceStore.formatCostDisplay(0.0))
        assertEquals("32.58", ChargePriceStore.formatCostDisplay(32.58))
        assertEquals("2436.6", ChargePriceStore.formatCostDisplay(2436.64))
        assertEquals("18521", ChargePriceStore.formatCostDisplay(18520.64))
    }

    @Test
    fun `charge detail item supports price and cost fields`() {
        val item = ChargeDetailItem(
            startTs = 1794668409000,
            endTs = 1794700548000,
            chargeType = "SLOW",
            energyKwh = 54.27,
            pricePerKwh = 0.60,
            costYuan = 32.56
        )

        assertEquals(0.60, item.pricePerKwh ?: 0.0, 0.001)
        assertEquals(32.56, item.costYuan ?: 0.0, 0.001)
    }
}
