package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTypeSelectionPolicyTest {
    @Test
    fun `confirmed power type cannot be changed`() {
        assertFalse(PowerTypeSelectionPolicy.canChange(isConfirmed = true))
        assertTrue(PowerTypeSelectionPolicy.canChange(isConfirmed = false))
    }

    @Test
    fun `confirmation warning explains data impact and recovery path`() {
        val warning = PowerTypeSelectionPolicy.CONFIRMATION_WARNING
        assertTrue(warning.contains("请务必确认自己的动力模式"))
        assertTrue(warning.contains("续航、能耗等数据全部错误"))
        assertTrue(warning.contains("退出登录后重新登录"))
    }
}
