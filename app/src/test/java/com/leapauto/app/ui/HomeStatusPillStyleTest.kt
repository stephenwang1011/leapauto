package com.leapauto.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.ui.theme.AppBackgroundDark
import com.leapauto.app.ui.theme.AppBackgroundLight
import com.leapauto.app.ui.theme.StatusGoodDark
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStatusPillStyleTest {
    @Test
    fun `status pill sizing follows the enlarged readability contract`() {
        assertEquals(12.sp, HomeStatusPillStyle.fontSize)
        assertEquals(16.sp, HomeStatusPillStyle.lineHeight)
        assertEquals(9.dp, HomeStatusPillStyle.horizontalPadding)
        assertEquals(3.dp, HomeStatusPillStyle.verticalPadding)
        assertEquals(12.dp, HomeStatusPillStyle.iconSize)
        assertEquals(6.dp, HomeStatusPillStyle.dotSize)
    }

    @Test
    fun `all three pills wire larger metrics and bounded single line labels`() {
        val source = screenSource()
        val charging = source.substringAfter("private fun ChargingCenterPill(")
            .substringBefore("private fun EnergyCapsuleProgressBar(")
        val location = source.substringAfter("// 1. 位置信息胶囊")
            .substringBefore("// 2. 驻车/行驶状态胶囊")
        val driving = source.substringAfter("// 2. 驻车/行驶状态胶囊")
            .substringBefore("// ====== 2. 底部展台区域")

        for (pill in listOf(charging, location, driving)) {
            assertTrue(pill.contains("fontSize = HomeStatusPillStyle.fontSize"))
            assertTrue(pill.contains("lineHeight = HomeStatusPillStyle.lineHeight"))
            assertTrue(pill.contains("horizontal = HomeStatusPillStyle.horizontalPadding"))
            assertTrue(pill.contains("vertical = HomeStatusPillStyle.verticalPadding"))
            assertTrue(pill.contains("fontWeight = FontWeight.SemiBold"))
            assertTrue(pill.contains("modifier = Modifier.weight(1f, fill = false)"))
            assertTrue(pill.contains("maxLines = 1"))
            assertTrue(pill.contains("overflow = TextOverflow.Ellipsis"))
            assertFalse(pill.contains("fontSize = 10.sp"))
        }
        assertTrue(charging.contains("Modifier.size(HomeStatusPillStyle.iconSize)"))
        assertTrue(location.contains("Modifier.size(HomeStatusPillStyle.iconSize)"))
        assertTrue(charging.contains(".size(HomeStatusPillStyle.dotSize)"))
        assertTrue(driving.contains(".size(HomeStatusPillStyle.dotSize)"))
        val breathingDot = source.substringAfter("private fun DrivingBreathingDot(")
            .substringBefore("internal fun glassCardBorder(")
        assertTrue(breathingDot.contains(".size(HomeStatusPillStyle.dotSize)"))
    }

    @Test
    fun `hero reserves width for both columns and sentry keeps its existing sizing`() {
        val heroHeader = screenSource().substringAfter("// ====== 1. 顶部区域")
            .substringBefore("// ====== 2. 底部展台区域")
        val left = heroHeader.substringBefore("// 右侧列：蓝牙状态按钮")
        val right = heroHeader.substringAfter("// 右侧列：蓝牙状态按钮")
        assertTrue(left.contains("horizontalArrangement = Arrangement.spacedBy(8.dp)"))
        assertTrue(left.contains("modifier = Modifier.weight(3f)"))
        assertTrue(right.contains("modifier = Modifier.weight(2f)"))
        assertFalse(right.contains("address.take(10)"))
        val sentry = left.substringAfter("if (status?.sentryMode == true)")
            .substringBefore("// 状态更新时间")
        assertTrue(sentry.contains("color = defaultPillBg"))
        assertTrue(sentry.contains("padding(horizontal = 7.dp, vertical = 1.5.dp)"))
        assertTrue(sentry.contains("fontSize = 10.sp"))
        assertTrue(sentry.contains("lineHeight = 11.sp"))
        assertTrue(sentry.contains(".size(5.dp)"))
        assertFalse(sentry.contains("HomeStatusPillStyle"))
    }

    @Test
    fun `light and dark backgrounds remain translucent neutral greys`() {
        val light = homeStatusPillBackground(isHeroDark = false)
        val dark = homeStatusPillBackground(isHeroDark = true)

        assertEquals(Color(0xFFDDE1E6), light.copy(alpha = 1f))
        assertEquals(0.65f, light.alpha, 0.005f)
        assertEquals(Color(0xFF303030), dark.copy(alpha = 1f))
        assertEquals(0.68f, dark.alpha, 0.005f)
        assertTrue(light.luminance() < Color.White.luminance())
    }

    @Test
    fun `ordinary labels retain AA contrast over light dark and extreme backdrops`() {
        val backdrops = listOf(Color.Black, Color.White, AppBackgroundLight, AppBackgroundDark)
        for (isHeroDark in listOf(false, true)) {
            val text = if (isHeroDark) Color.White else Color(0xFF141923)
            for (backdrop in backdrops) {
                val background = homeStatusPillBackground(isHeroDark).compositeOver(backdrop)
                assertTrue(
                    "Ordinary label contrast: dark=$isHeroDark, backdrop=$backdrop",
                    contrast(text, background) >= 4.5f
                )
            }
        }
    }

    @Test
    fun `dark charging text retains AA contrast over dark page and hero backdrops`() {
        for (backdrop in listOf(AppBackgroundDark, Color(0xFF12161F), Color.Black)) {
            val background = homeStatusPillBackground(isHeroDark = true).compositeOver(backdrop)
            assertTrue(contrast(StatusGoodDark, background) >= 4.5f)
        }
    }

    private fun screenSource(): String {
        val projectDir = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app").isDirectory }
        return File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt")
            .readText().replace("\r\n", "\n")
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val first = foreground.luminance()
        val second = background.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
