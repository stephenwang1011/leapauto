package com.leapauto.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LoginUiPolicyTest {

    @Test
    fun `filterPhoneNumber strips non digits and limits to 11 digits`() {
        assertEquals("13800000000", LoginUiPolicy.filterPhoneNumber("13800000000"))
        assertEquals("13800000000", LoginUiPolicy.filterPhoneNumber(" 138-0000-0000 "))
        assertEquals("13800000000", LoginUiPolicy.filterPhoneNumber("1380000000012345"))
        assertEquals("", LoginUiPolicy.filterPhoneNumber("abcdefg"))
    }

    @Test
    fun `filterSmsCode strips non digits and limits to 6 digits`() {
        assertEquals("123456", LoginUiPolicy.filterSmsCode("123456"))
        assertEquals("123456", LoginUiPolicy.filterSmsCode("123-456"))
        assertEquals("123456", LoginUiPolicy.filterSmsCode("12345678"))
        assertEquals("", LoginUiPolicy.filterSmsCode("xyz"))
    }

    @Test
    fun `canSendSms only returns true when phone is 11 digits, countdown is zero and not busy`() {
        assertTrue(LoginUiPolicy.canSendSms(phone = "13800000000", smsCountdownSeconds = 0, busy = false))

        // 手机号未达到 11 位
        assertFalse(LoginUiPolicy.canSendSms(phone = "1380000000", smsCountdownSeconds = 0, busy = false))
        // 倒计时未结束
        assertFalse(LoginUiPolicy.canSendSms(phone = "13800000000", smsCountdownSeconds = 59, busy = false))
        // 处于请求中 (busy)
        assertFalse(LoginUiPolicy.canSendSms(phone = "13800000000", smsCountdownSeconds = 0, busy = true))
    }

    @Test
    fun `canSubmitLogin only returns true when phone is 11 digits, code has at least 4 digits and not busy`() {
        assertTrue(LoginUiPolicy.canSubmitLogin(phone = "13800000000", code = "1234", busy = false))
        assertTrue(LoginUiPolicy.canSubmitLogin(phone = "13800000000", code = "123456", busy = false))

        // 手机号不足 11 位
        assertFalse(LoginUiPolicy.canSubmitLogin(phone = "1380000000", code = "123456", busy = false))
        // 验证码不足 4 位
        assertFalse(LoginUiPolicy.canSubmitLogin(phone = "13800000000", code = "123", busy = false))
        // 处于请求中 (busy)
        assertFalse(LoginUiPolicy.canSubmitLogin(phone = "13800000000", code = "123456", busy = true))
    }

    @Test
    fun `handleSendSmsClick coordinates haptic, focus request, keyboard show and sms dispatch in order`() {
        val callOrder = mutableListOf<String>()

        LoginUiPolicy.handleSendSmsClick(
            performHaptic = { callOrder.add("haptic") },
            requestCodeFocus = { callOrder.add("focus") },
            showKeyboard = { callOrder.add("keyboard") },
            onSendSms = { callOrder.add("sms") }
        )

        assertEquals(listOf("haptic", "focus", "keyboard", "sms"), callOrder)
    }

    @Test
    fun `login screen binds focusRequester to code field and requests focus on sms send`() {
        val rootDir = projectDirectory()
        val screenFile = File(rootDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt")
        assertTrue("LeapAutoScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        assertTrue(
            "LoginContent must define FocusRequester for code field",
            content.contains("val codeFocusRequester = remember { FocusRequester() }")
        )
        assertTrue(
            "Code OutlinedTextField must attach codeFocusRequester",
            content.contains(".focusRequester(codeFocusRequester)")
        )
        assertTrue(
            "Send SMS click must request code field focus",
            content.contains("requestCodeFocus = { codeFocusRequester.requestFocus() }")
        )
    }

    private fun projectDirectory(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
    }
}
