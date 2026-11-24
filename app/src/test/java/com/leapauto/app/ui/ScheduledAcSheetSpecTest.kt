package com.leapauto.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScheduledAcSheetSpecTest {

    @Test
    fun `scheduled ac screen aligns visual language with climate screen and preserves selected highlight semantics`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val source = File(projectDir, "app/src/main/java/com/leapauto/app/ui/ScheduledAcSheet.kt")
            .readText()
            .replace("\r\n", "\n")

        // 1. 统一复用的仪表控件与触感
        assertTrue(source.contains("ClimateTempStepButton("))
        assertTrue(source.contains("ThinClimateSlider("))
        assertTrue(source.contains("ClimateOutletSegmentItem("))
        assertTrue(source.contains("HapticFeedbackHelper.performMechanicalClick("))
        assertFalse("不应残留旧的文本光标微弱震动", source.contains("HapticFeedbackType.TextHandleMove"))

        // 2. 预约语义：冷暖模式分别采用能量绿与暖琥珀，且选中即高亮
        assertTrue(source.contains("ScheduledAcMode.COLD.label"))
        assertTrue(source.contains("ScheduledAcMode.HOT.label"))
        assertTrue(source.contains("activeColor = MaterialTheme.statusGood"))
        assertTrue(source.contains("activeColor = MaterialTheme.statusWarn"))
        assertTrue(source.contains("acOn = true"))

        // 3. 单字星期胶囊
        listOf("7 to \"日\"", "1 to \"一\"", "2 to \"二\"", "3 to \"三\"", "4 to \"四\"", "5 to \"五\"", "6 to \"六\"").forEach { entry ->
            assertTrue("星期胶囊应使用清爽单字: $entry", source.contains(entry))
        }

        // 4. 座椅区同构排布 + 到点联动独立卡片
        assertTrue(source.contains("text = \"全车座椅舒适\""))
        assertTrue(source.contains("text = \"二排左座\""))
        assertTrue(source.contains("text = \"主驾后\""))
        assertTrue(source.contains("text = \"二排右座\""))
        assertTrue(source.contains("text = \"副驾后\""))
        assertTrue(source.contains("label = \"方向盘加热\""))
        assertTrue(source.contains("text = \"到点联动\""))
        assertTrue(source.contains("text = \"关闭车窗\""))
        assertTrue(source.contains("text = \"关闭遮阳帘\""))
        assertFalse("座椅区不应再保留冗长重复的旧副标题", source.contains("定时到点时联动开启方向盘加热"))
        assertFalse("不应展示工程术语云端已就绪", source.contains("云端已就绪"))
        assertTrue(source.contains("定时未开启"))
        assertTrue(source.contains("已开启 · \${draft.startTime} 自动启动"))
    }
}
