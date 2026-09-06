package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationTabsTest {
    @Test
    fun `home and account destination ids remain stable`() {
        assertEquals(
            0,
            MainNavigationTabs.VEHICLE
        )
        assertEquals(
            2,
            MainNavigationTabs.ACCOUNT
        )
    }
}
