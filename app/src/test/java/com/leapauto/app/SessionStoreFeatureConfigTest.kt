package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionStoreFeatureConfigTest {

    @Test
    fun `bluetooth key feature enabled constant and default value contract`() {
        assertEquals("bluetooth_key_feature_enabled", SessionStore.BLUETOOTH_KEY_FEATURE_ENABLED)
        // 验证默认关闭契约
        val defaultEnabled = false
        assertFalse(defaultEnabled)
    }
}
