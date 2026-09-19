package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SensitiveControlPolicyTest {

    @Test
    fun `sensitive command identification follows vehicle security rules`() {
        // 后备箱处于关闭状态时开门属于高敏感操作
        assertTrue(SensitiveControlPolicy.isSensitiveCommand("trunk", isClosed = true))
        // 后备箱已经开启时关门属于非敏感常规操作
        assertFalse(SensitiveControlPolicy.isSensitiveCommand("trunk", isClosed = false))
        // 开前备箱属于高敏感操作
        assertTrue(SensitiveControlPolicy.isSensitiveCommand("trunkOpen"))
        assertTrue(SensitiveControlPolicy.isSensitiveCommand("frunkOpen"))

        // 常规控车属于非敏感动作
        assertFalse(SensitiveControlPolicy.isSensitiveCommand("lock"))
        assertFalse(SensitiveControlPolicy.isSensitiveCommand("unlock"))
        assertFalse(SensitiveControlPolicy.isSensitiveCommand("windowGroup"))
        assertFalse(SensitiveControlPolicy.isSensitiveCommand("acOn"))
    }

    @Test
    fun `sensitive action hint and hold duration constants are accurate`() {
        assertEquals("高敏感操作请长按1.2秒开启", SensitiveControlPolicy.SENSITIVE_ACTION_HINT)
        assertEquals(1200L, SensitiveControlPolicy.LONG_PRESS_HOLD_DURATION_MS)
    }

    @Test
    fun `quick vehicle actions implement 1200ms hold arc and permanent customize edit button`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        // 验证常驻末尾的【自定义 ✎】编辑按钮
        assertTrue(screenSource.contains("quickActionCustomize"))
        assertTrue(screenSource.contains("R.drawable.ic_edit"))
        assertTrue(screenSource.contains("openEditor()"))

        // 验证 1.2 秒长按外圈蓄力圆环
        assertTrue(screenSource.contains("isSensitive"))
        assertTrue(screenSource.contains("holdProgress.animateTo"))
        assertTrue(screenSource.contains("drawArc"))
        assertTrue(screenSource.contains("SENSITIVE_ACTION_HINT"))
        assertTrue(screenSource.contains("onLongPressConfirm"))
    }

    @Test
    fun `sensitive toast moves to upper screen area right above quick controls`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("showUpperToast("))
        assertTrue(screenSource.contains("toast.setGravity(android.view.Gravity.CENTER, 0, -220)"))
    }
}
