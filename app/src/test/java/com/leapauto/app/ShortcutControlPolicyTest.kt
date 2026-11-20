package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutControlPolicyTest {

    @Test
    fun `resolves command from intent extra correctly`() {
        assertEquals("unlock", ShortcutControlPolicy.resolveCommand("unlock"))
        assertEquals("lock", ShortcutControlPolicy.resolveCommand("LOCK"))
        assertEquals("ac_on", ShortcutControlPolicy.resolveCommand("  ac_on  "))
        assertEquals("trunk", ShortcutControlPolicy.resolveCommand("trunk"))
        assertEquals("horn", ShortcutControlPolicy.resolveCommand("horn"))
        assertNull(ShortcutControlPolicy.resolveCommand("destroyCar"))
        assertNull(ShortcutControlPolicy.resolveCommand(""))
        assertNull(ShortcutControlPolicy.resolveCommand(null))
    }

    @Test
    fun `resolves command from deep link uri correctly`() {
        assertEquals(
            "unlock",
            ShortcutControlPolicy.resolveCommand(
                commandExtra = null,
                uriString = "leapauto://control?cmd=unlock"
            )
        )
        assertEquals(
            "lock",
            ShortcutControlPolicy.resolveCommand(
                commandExtra = null,
                uriString = "leapauto://control?command=lock"
            )
        )
        assertEquals(
            "ac_on",
            ShortcutControlPolicy.resolveCommand(
                commandExtra = null,
                uriString = "leapauto://control?source=xiaoai&cmd=ac_on"
            )
        )
        assertEquals(
            "trunk",
            ShortcutControlPolicy.resolveCommand(
                commandExtra = null,
                uriString = "leapauto://control?cmd=trunk"
            )
        )

        // 异常 URL 保护
        assertNull(
            ShortcutControlPolicy.resolveCommand(
                commandExtra = null,
                uriString = "https://control?cmd=unlock"
            )
        )
        assertNull(
            ShortcutControlPolicy.resolveCommand(
                commandExtra = null,
                uriString = "leapauto://settings?cmd=unlock"
            )
        )
        assertNull(
            ShortcutControlPolicy.resolveCommand(
                commandExtra = null,
                uriString = "leapauto://control?cmd=invalid_command"
            )
        )
    }

    @Test
    fun `extra takes precedence over deep link uri`() {
        val resolved = ShortcutControlPolicy.resolveCommand(
            commandExtra = "unlock",
            uriString = "leapauto://control?cmd=lock"
        )
        assertEquals("unlock", resolved)
    }

    @Test
    fun `maps shortcut commands to service commands correctly`() {
        assertEquals("acOn", ShortcutControlPolicy.mapToServiceCommand("ac_on"))
        assertEquals("acOff", ShortcutControlPolicy.mapToServiceCommand("ac_off"))
        assertEquals("trunkOpen", ShortcutControlPolicy.mapToServiceCommand("trunk"))
        assertEquals("trunkOpen", ShortcutControlPolicy.mapToServiceCommand("trunk_open"))
        assertEquals("unlock", ShortcutControlPolicy.mapToServiceCommand("unlock"))
        assertEquals("lock", ShortcutControlPolicy.mapToServiceCommand("lock"))
        assertEquals("horn", ShortcutControlPolicy.mapToServiceCommand("horn"))
    }

    @Test
    fun `provides friendly labels for user toast and status`() {
        assertEquals("解锁车辆", ShortcutControlPolicy.commandLabel("unlock"))
        assertEquals("车辆上锁", ShortcutControlPolicy.commandLabel("lock"))
        assertEquals("开启空调", ShortcutControlPolicy.commandLabel("ac_on"))
        assertEquals("开启空调", ShortcutControlPolicy.commandLabel("acOn"))
        assertEquals("开启后备箱", ShortcutControlPolicy.commandLabel("trunk"))
        assertEquals("开启后备箱", ShortcutControlPolicy.commandLabel("trunkOpen"))
        assertEquals("鸣笛寻车", ShortcutControlPolicy.commandLabel("horn"))
        assertEquals("远程控车", ShortcutControlPolicy.commandLabel("unknown"))
    }
}
