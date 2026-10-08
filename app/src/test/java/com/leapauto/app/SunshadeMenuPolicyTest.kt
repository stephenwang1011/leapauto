package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunshadeMenuPolicyTest {

    @Test
    fun sunshadeMenuItemsHaveDistinctOpenAndCloseIcons() {
        val items = SunshadeMenuPolicy.items
        assertEquals(2, items.size)

        val openItem = items.first { it.command == "sunshadeOpen" }
        val closeItem = items.first { it.command == "sunshadeClose" }

        assertEquals("打开遮阳帘", openItem.label)
        assertEquals("关闭遮阳帘", closeItem.label)

        assertEquals(R.drawable.ic_sunshade_open, openItem.iconRes)
        assertEquals(R.drawable.ic_sunshade_close, closeItem.iconRes)

        // 核心门禁：打开和关闭按钮必须使用不同图标，防止视觉同质化
        assertNotEquals(openItem.iconRes, closeItem.iconRes)
    }

    @Test
    fun sunshadeMenuPolicyResolvesIconsByCommand() {
        assertEquals(R.drawable.ic_sunshade_open, SunshadeMenuPolicy.iconForCommand("sunshadeOpen"))
        assertEquals(R.drawable.ic_sunshade_close, SunshadeMenuPolicy.iconForCommand("sunshadeClose"))
        assertEquals(R.drawable.ic_phosphor_sun, SunshadeMenuPolicy.iconForCommand("unknown"))
    }

    @Test
    fun sunshadeMenuItemsMatchQuickCommandExecutionPolicy() {
        SunshadeMenuPolicy.items.forEach { item ->
            assertTrue(
                "Command ${item.command} should be recognized by QuickCommandExecutionPolicy",
                QuickCommandExecutionPolicy.isCommandInProgress("sunshadeGroup", item.command)
            )
        }
    }
}
