package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Widget4x2ActionPolicyTest {

    @Test
    fun `default actions contains standard five actions`() {
        assertEquals(
            listOf("unlock", "lock", "sentry", "ac", "trunk"),
            Widget4x2ActionPolicy.DEFAULT_ACTIONS
        )
    }

    @Test
    fun `resolve returns default when input is null or empty`() {
        assertEquals(Widget4x2ActionPolicy.DEFAULT_ACTIONS, Widget4x2ActionPolicy.resolve(null))
        assertEquals(Widget4x2ActionPolicy.DEFAULT_ACTIONS, Widget4x2ActionPolicy.resolve(emptyList()))
    }

    @Test
    fun `resolve filters invalid actions and bounds between 4 and 5`() {
        val input = listOf("unlock", "invalid_cmd", "horn", "ac", "windowVent", "sunshade", "lock")
        val resolved = Widget4x2ActionPolicy.resolve(input)
        assertEquals(5, resolved.size)
        assertEquals(listOf("unlock", "horn", "ac", "windowVent", "sunshade"), resolved)

        val fourActions = listOf("unlock", "lock", "sentry", "ac")
        val resolvedFour = Widget4x2ActionPolicy.resolve(fourActions)
        assertEquals(4, resolvedFour.size)
        assertEquals(fourActions, resolvedFour)

        val threeActions = listOf("unlock", "lock", "sentry")
        val resolvedFallback = Widget4x2ActionPolicy.resolve(threeActions)
        assertEquals(Widget4x2ActionPolicy.DEFAULT_ACTIONS, resolvedFallback)
    }

    @Test
    fun `findAction locates defined actions`() {
        val ac = Widget4x2ActionPolicy.findAction("ac")
        assertTrue(ac != null)
        assertEquals("空调", ac?.label)

        val horn = Widget4x2ActionPolicy.findAction("horn")
        assertTrue(horn != null)
        assertEquals("鸣笛寻车", horn?.label)

        val windowOpen = Widget4x2ActionPolicy.findAction("windowOpen")
        assertTrue(windowOpen != null)
        assertEquals("车窗半开", windowOpen?.label)

        val batteryPreheat = Widget4x2ActionPolicy.findAction("batteryPreheat")
        assertEquals(null, batteryPreheat)

        val unknown = Widget4x2ActionPolicy.findAction("unknown_xyz")
        assertEquals(null, unknown)
    }
}
