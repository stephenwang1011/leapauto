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
