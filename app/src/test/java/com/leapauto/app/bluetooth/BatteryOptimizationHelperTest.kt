package com.leapauto.app.bluetooth

import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Test

class BatteryOptimizationHelperTest {

    @Test
    fun `helper functions execute safely on test context`() {
        val dummyContext = object : android.content.ContextWrapper(null) {
            override fun getPackageName(): String = "com.leapauto.app"
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
        }

        // 验证在没有真实系统的测试环境下，安全检查不会崩溃
        val ignored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(dummyContext)
        // 在 SDK < 23 默认返回 true，或根据系统服务安全兜底
        assertNotNull(ignored)
    }
}
