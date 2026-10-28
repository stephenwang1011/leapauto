package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SupportAuthorModuleTest {

    @Test
    fun `support author card is placed after version update card and before open source card in settings`() {
        val screenSource = readSourceFile("app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt")

        val settingsSource = screenSource.substringAfter("private fun MyContent(")
        val versionCardIndex = settingsSource.indexOf("VersionUpdateCard(")
        val supportCardIndex = settingsSource.indexOf("SupportAuthorCard(")
        val openSourceCardIndex = settingsSource.indexOf("OpenSourceCodeCard()")

        assertTrue("VersionUpdateCard must be present", versionCardIndex >= 0)
        assertTrue("SupportAuthorCard must be present", supportCardIndex >= 0)
        assertTrue("OpenSourceCodeCard must be present", openSourceCardIndex >= 0)

        // 严格保证在设置界面下，“当前版本”模块后面加一个“支持作者”模块，再紧接着开源代码仓库
        assertTrue(
            "SupportAuthorCard must be located after VersionUpdateCard",
            supportCardIndex > versionCardIndex
        )
        assertTrue(
            "SupportAuthorCard must be located before OpenSourceCodeCard",
            supportCardIndex < openSourceCardIndex
        )
    }

    @Test
    fun `author support screen arranges alipay and wechat vertically with matching qr dimensions`() {
        val screenSource = readSourceFile("app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").replace("\r\n", "\n")

        // 验证支付宝在上，微信在下
        val alipayCardIndex = screenSource.indexOf("PaymentQrCard(\n            title = \"支付宝赞赏\"")
        val wechatCardIndex = screenSource.indexOf("PaymentQrCard(\n            title = \"微信赞赏\"")

        assertTrue("Alipay payment card must be present", alipayCardIndex >= 0)
        assertTrue("Wechat payment card must be present", wechatCardIndex >= 0)
        assertTrue(
            "Alipay must be arranged above WeChat vertically",
            alipayCardIndex < wechatCardIndex
        )

        // 验证统一的 PaymentQrCard 尺寸完全一致（220.dp 与 12.dp padding）
        assertTrue(
            "QR container must enforce uniform 220.dp size",
            screenSource.contains(".size(220.dp)")
        )
        assertTrue(
            "QR image padding must be uniform",
            screenSource.contains(".padding(12.dp)")
        )

        // 验证二维码下方无冗余说明文字
        assertFalse(
            "QR card should not have redundant bottom hint text",
            screenSource.contains("长按或截图后在对应 App 中识别")
        )
    }

    @Test
    fun `support author essay is subtle restrained and appreciative`() {
        val screenSource = readSourceFile("app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt")

        // 验证软文内容精炼克制、含蓄真诚、强调自愿与相伴
        assertTrue(screenSource.contains("个人业余开发维护"))
        assertTrue(screenSource.contains("纯粹为爱发电"))
        assertTrue(screenSource.contains("支持全凭自愿"))
        assertTrue(screenSource.contains("感谢一路相伴"))

        // 严禁商业化诱导或强行要求
        assertFalse(screenSource.contains("必须赞赏"))
        assertFalse(screenSource.contains("付费解锁"))
    }

    @Test
    fun `navigation and top app bar title support author screen destination`() {
        val screenSource = readSourceFile("app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt")

        assertTrue(screenSource.contains("AUTHOR_SUPPORT"))
        assertTrue(screenSource.contains("showAuthorSupportScreen -> \"支持作者\""))
        assertTrue(screenSource.contains("ScreenDestination.AUTHOR_SUPPORT -> {"))
        assertTrue(screenSource.contains("AuthorSupportContent()"))
    }

    @Test
    fun `login resume and idle work cannot schedule author support prompts`() {
        val activitySource = readSourceFile("app/src/main/java/com/leapauto/app/MainActivity.kt")
        val screenSource = readSourceFile("app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt")

        assertTrue(activitySource.contains("private fun beginPostLoginPrompts("))
        assertTrue(activitySource.contains("PostLoginPinSetupPolicy.shouldPromptPinSetup("))
        assertTrue(activitySource.contains("checkAndPromptPowerType(session.selectedVin)"))
        assertFalse("Activity must not own support reminder state or scheduling",
            activitySource.contains("authorSupport", ignoreCase = true))
        assertFalse(screenSource.contains("AuthorSupportDialog"))
        assertFalse(screenSource.contains("onDisableAuthorSupport"))
        assertFalse(screenSource.contains("onDismissAuthorSupport"))
    }

    @Test
    fun `settings card is the only support entry and back returns to settings`() {
        val screenSource = readSourceFile("app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt")
        val accountRoute = screenSource.substringAfter("ScreenDestination.ACCOUNT -> {")
            .substringBefore("ScreenDestination.AUTHOR_SUPPORT -> {")
        assertTrue(accountRoute.contains("MyContent("))
        assertTrue(accountRoute.contains("onOpenAuthorSupport = { showAuthorSupportScreen = true }"))
        assertEquals(1, Regex("showAuthorSupportScreen\\s*=\\s*true").findAll(screenSource).count())
        assertEquals(1, Regex("onClick\\s*=\\s*onOpenAuthorSupport").findAll(screenSource).count())
        assertTrue(screenSource.contains("var showAuthorSupportScreen by rememberSaveable { mutableStateOf(false) }"))

        val closeSupport = screenSource.substringAfter("val closeSubpage: () -> Unit = {")
            .substringBefore("} else if")
        assertTrue(closeSupport.contains("if (showAuthorSupportScreen)"))
        assertTrue(closeSupport.contains("showAuthorSupportScreen = false"))
        assertFalse("Closing support must keep the settings destination", closeSupport.contains("selectedTab ="))
        assertTrue(screenSource.contains("if (showAuthorSupportScreen) \"返回设置\""))

        val tabsSource = readSourceFile("app/src/main/java/com/leapauto/app/MainNavigationTabs.kt")
        assertFalse("Support must not be a root tab", tabsSource.contains("SUPPORT", ignoreCase = true))
    }

    @Test
    fun `legacy reminder preferences are ignored and cannot gate manual support`() {
        val sources = listOf(
            "app/src/main/java/com/leapauto/app/SessionStore.kt",
            "app/src/main/java/com/leapauto/app/MainActivity.kt",
            "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt"
        ).map(::readSourceFile)
        for (source in sources) {
            assertFalse(source.contains("author_support_last_shown_epoch_day", ignoreCase = true))
            assertFalse(source.contains("author_support_prompt_disabled", ignoreCase = true))
            assertFalse(source.contains("shouldShowAuthorSupportPrompt"))
        }
    }

    @Test
    fun `support qr and icon resources exist and are valid`() {
        val projectDir = projectDirectory()
        val heartIcon = File(projectDir, "app/src/main/res/drawable/ic_phosphor_heart.xml")
        val alipayQr = File(projectDir, "app/src/main/res/drawable/support_alipay_qr_safe.webp")
        val wechatQr = File(projectDir, "app/src/main/res/drawable/support_wechat_qr.webp")

        assertTrue("Heart icon must exist", heartIcon.exists())
        assertTrue("Alipay QR code resource must exist", alipayQr.exists())
        assertTrue("WeChat QR code resource must exist", wechatQr.exists())

        val heartXml = heartIcon.readText()
        assertTrue(heartXml.contains("<vector"))
        assertTrue(heartXml.contains("android:viewportWidth=\"256\""))
        assertTrue(heartXml.contains("android:viewportHeight=\"256\""))
    }

    private fun readSourceFile(relativePath: String): String {
        return File(projectDirectory(), relativePath).readText(Charsets.UTF_8)
    }

    private fun projectDirectory(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
    }
}
