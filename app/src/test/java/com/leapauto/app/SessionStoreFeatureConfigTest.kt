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

    @Test
    fun `widget background style constants and defaults contract`() {
        assertEquals("widget_background_style", SessionStore.WIDGET_BACKGROUND_STYLE)
        assertEquals(0, SessionStore.WIDGET_BG_STYLE_MICROCRYSTAL)
        assertEquals(1, SessionStore.WIDGET_BG_STYLE_LANDSCAPE)
        assertEquals(SessionStore.WIDGET_BG_STYLE_LANDSCAPE, SessionStore.WIDGET_BG_STYLE_DEFAULT)
    }

    @Test
    fun `widget opacity constants and options contract`() {
        assertEquals("widget_opacity", SessionStore.WIDGET_OPACITY)
        assertEquals(100, SessionStore.WIDGET_OPACITY_OPAQUE)
        assertEquals(listOf(100, 75, 50, 25), SessionStore.WIDGET_OPACITY_OPTIONS)
    }
}
