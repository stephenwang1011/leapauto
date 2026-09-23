package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetWindowTogglePolicyTest {

    @Test
    fun `resolves windowOpen to windowClose when window is currently open`() {
        assertEquals(
            "windowClose",
            WidgetWindowTogglePolicy.resolveCommand(command = "windowOpen", isWindowOpen = true)
        )
    }

    @Test
    fun `resolves windowVent to windowClose when window is currently open`() {
        assertEquals(
            "windowClose",
            WidgetWindowTogglePolicy.resolveCommand(command = "windowVent", isWindowOpen = true)
        )
    }

    @Test
    fun `preserves windowOpen when window is currently closed`() {
        assertEquals(
            "windowOpen",
            WidgetWindowTogglePolicy.resolveCommand(command = "windowOpen", isWindowOpen = false)
        )
    }

    @Test
    fun `preserves windowVent when window is currently closed`() {
        assertEquals(
            "windowVent",
            WidgetWindowTogglePolicy.resolveCommand(command = "windowVent", isWindowOpen = false)
        )
    }

    @Test
    fun `preserves other commands regardless of window state`() {
        assertEquals(
            "unlock",
            WidgetWindowTogglePolicy.resolveCommand(command = "unlock", isWindowOpen = true)
        )
        assertEquals(
            "windowClose",
            WidgetWindowTogglePolicy.resolveCommand(command = "windowClose", isWindowOpen = true)
        )
        assertEquals(
            "windowClose",
            WidgetWindowTogglePolicy.resolveCommand(command = "windowClose", isWindowOpen = false)
        )
    }

    @Test
    fun `contentDescription reflects toggle state`() {
        assertEquals(
            "一键关窗（当前车窗已开）",
            WidgetWindowTogglePolicy.contentDescription("windowVent", isWindowOpen = true)
        )
        assertEquals(
            "车窗通风",
            WidgetWindowTogglePolicy.contentDescription("windowVent", isWindowOpen = false)
        )
        assertEquals(
            "一键关窗（当前车窗已开）",
            WidgetWindowTogglePolicy.contentDescription("windowOpen", isWindowOpen = true)
        )
        assertEquals(
            "车窗半开",
            WidgetWindowTogglePolicy.contentDescription("windowOpen", isWindowOpen = false)
        )
    }
}
