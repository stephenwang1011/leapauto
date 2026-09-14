package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SmsLoginProtocolTest {
    private val headers = mapOf("APPVersion" to "3.19.2-2", "APPImei" to "fixture-device")
    private val phone = "13800000000"
    private val smsCode = "123456"

    @Test
    fun `missing fingerprint blocks login before HTTP despite saved identifiers`() {
        val session = Session(deviceId = "fixture-device", smDeviceId = "stale-fixture-fingerprint")
        var requestCount = 0
        val api = LeapmotorApi(session, { "" }, {
            requestCount++
            throw AssertionError("HTTP must not run before device verification")
        })

        val error = assertThrows(ApiException::class.java) { api.loginWithSms(phone, smsCode) }

        assertEquals("device_verification", error.stage)
        assertEquals(0, requestCount)
        assertNull(session.oldAuth)
        assertEquals("", session.phone)
    }

    @Test
    fun `fingerprint provider failure does not send HTTP or change session`() {
        var requestCount = 0
        val session = Session(deviceId = "fixture-device")
        val failure = ApiException("设备安全验证失败，请稍后重试", stage = "device_verification")
        val api = LeapmotorApi(session, { throw failure }, {
            requestCount++
            throw AssertionError("HTTP must not run")
        })

        val error = assertThrows(ApiException::class.java) { api.loginWithSms(phone, smsCode) }

        assertEquals(failure, error)
        assertEquals(0, requestCount)
        assertNull(session.oldAuth)
    }

    @Test
    fun `SDK failure retains safe message and device verification stage`() {
        val failure = DeviceSecurityFailure.DEVICE_ID_TIMEOUT
        val error = assertThrows(ApiException::class.java) {
            SmsLoginProtocol.requireDeviceId { throw DeviceSecurityException(failure) }
        }

        assertEquals("device_verification", error.stage)
        assertEquals(failure.userMessage, error.message)
        assertNull(error.cause)
    }

    @Test
    fun `SMS uses GET encrypted phone query and unchanged headers`() {
        val request = SmsLoginProtocol.buildSmsRequest("rsa-fixture_-", headers)

        assertEquals("GET", request.method)
        assertEquals("/app-user/applogin/compliance/sendmessagecode", request.url.encodedPath)
        assertEquals(setOf("phoneNo"), request.url.queryParameterNames)
        assertEquals("rsa-fixture_-", request.url.queryParameter("phoneNo"))
        assertEquals("3.19.2-2", request.header("APPVersion"))
        assertEquals("fixture-device", request.header("APPImei"))
        assertNull(request.body)
    }

    @Test
    fun `login keeps six query parameters and empty POST body`() {
        val request = buildLoginRequest("Dfixture+local/value=")

        assertEquals("POST", request.method)
        assertEquals("/app-user/applogin/check_login_with_phone", request.url.encodedPath)
        assertEquals(
            setOf("phoneNoCiphertext", "smsCode", "deviceID", "smDeviceId", "os", "pageUrl"),
            request.url.queryParameterNames
        )
        assertEquals("rsa-fixture_-", request.url.queryParameter("phoneNoCiphertext"))
        assertEquals(smsCode, request.url.queryParameter("smsCode"))
        assertEquals("fixture-device", request.url.queryParameter("deviceID"))
        assertEquals("Dfixture+local/value=", request.url.queryParameter("smDeviceId"))
        assertEquals("android", request.url.queryParameter("os"))
        assertEquals("", request.url.queryParameter("pageUrl"))
        assertEquals(0L, request.body?.contentLength())
        assertNull(request.body?.contentType())
        assertEquals("fixture-device", request.header("APPImei"))
    }

    @Test
    fun `captcha retry preserves server request ID and adds existing validation fields`() {
        val result = GeetestCaptchaResult("fixture-lot", "fixture-pass", "123", "fixture-output", "server-request")
        val request = buildLoginRequest("Bfixture", result)

        assertEquals("server-request", request.url.queryParameter("requestId"))
        assertEquals("fixture-output", request.url.queryParameter("captchaOutput"))
        assertEquals(phone, request.url.queryParameter("phone"))
        assertEquals(0L, request.body?.contentLength())
    }

    @Test
    fun `captcha retry with missing validation is blocked locally`() {
        val result = GeetestCaptchaResult("fixture-lot", "fixture-pass", "123", "fixture-output", "")
        val error = assertThrows(ApiException::class.java) { buildLoginRequest("Bfixture", result) }

        assertEquals(SmsLoginProtocol.LOGIN_STAGE, error.stage)
        assertTrue(error.message.orEmpty().contains("凭据不完整"))
    }

    @Test
    fun `standard and one login success nodes return old credentials`() {
        for (node in listOf("appLoginVO", "appOneLoginVO")) {
            val response = JSONObject().put("result", 0).put("data", JSONObject().put(node, authObject()))
            val auth = SmsLoginProtocol.parseLoginResponse(response, phone, smsCode, nowMs = 42L)

            assertEquals("fixture-account", auth.accountId)
            assertEquals("fixture-token", auth.token)
            assertEquals("fixture-refresh", auth.refreshToken)
            assertEquals("21600", auth.tokenExpired)
            assertEquals(42L, auth.tokenObtainedAt)
        }
    }

    @Test
    fun `compatible nested old auth without business code still succeeds`() {
        val response = JSONObject().put("data", JSONObject().put("legacy", authObject()))
        val auth = SmsLoginProtocol.parseLoginResponse(response, phone, smsCode)

        assertEquals("fixture-account", auth.accountId)
        assertEquals("fixture-token", auth.token)
    }

    @Test
    fun `empty or null credentials never count as successful login`() {
        for (auth in listOf(JSONObject(), JSONObject().put("accountId", "fixture").put("token", JSONObject.NULL))) {
            val response = JSONObject().put("code", 0).put("data", JSONObject().put("appLoginVO", auth))
            val error = assertThrows(ApiException::class.java) {
                SmsLoginProtocol.parseLoginResponse(response, phone, smsCode)
            }
            assertEquals(SmsLoginProtocol.LOGIN_STAGE, error.stage)
            assertTrue(error.message.orEmpty().contains("缺少有效账号或凭据"))
        }
    }

    @Test
    fun `ordinary login failure keeps business codes and stage without sensitive response`() {
        val response = JSONObject().put("result", 100115).put("code", 400)
            .put("msg", "验证码错误 $phone fixture-token")
        val error = assertThrows(ApiException::class.java) {
            SmsLoginProtocol.parseLoginResponse(response, phone, smsCode)
        }

        assertEquals(SmsLoginProtocol.LOGIN_STAGE, error.stage)
        assertTrue(error.message.orEmpty().contains("result=100115, code=400"))
        assertTrue(error.message.orEmpty().contains("验证码错误"))
        assertFalse(error.message.orEmpty().contains(phone))
        assertFalse(error.message.orEmpty().contains("fixture-token"))
    }

    @Test
    fun `temporary restriction without challenge remains business failure even with auth object`() {
        for (auth in listOf(JSONObject(), authObject())) {
            val response = JSONObject().put("result", 123).put("msg", "登录触发安全风控临时管制")
                .put("data", JSONObject().put("appLoginVO", auth))
            val error = assertThrows(ApiException::class.java) {
                SmsLoginProtocol.parseLoginResponse(response, phone, smsCode)
            }
            assertTrue(error.message.orEmpty().contains("临时管制"))
            assertEquals(SmsLoginProtocol.LOGIN_STAGE, error.stage)
        }
    }

    @Test
    fun `real challenge supports data and root nodes and retains server request ID`() {
        val captchaId = "0123456789abcdef0123456789abcdef"
        val challenge = JSONObject().put("risk_type", "slide|123|$captchaId|fixture-risk")
            .put("requestId", "server-request")
        for (response in listOf(JSONObject().put("data", challenge), challenge)) {
            val error = assertThrows(GeetestChallengeRequiredException::class.java) {
                SmsLoginProtocol.parseLoginResponse(response, phone, smsCode)
            }
            assertEquals(captchaId, error.challenge.captchaId)
            assertEquals("server-request", error.challenge.requestId)
            assertEquals(phone, error.challenge.phone)
        }
    }

    @Test
    fun `complete challenge is preserved when response includes empty login object`() {
        val response = JSONObject().put("result", 0).put("data", JSONObject()
            .put("appLoginVO", JSONObject()).put("risk_type", "fixture-risk")
            .put("requestId", "server-request"))
        val error = assertThrows(GeetestChallengeRequiredException::class.java) {
            SmsLoginProtocol.parseLoginResponse(response, phone, smsCode)
        }

        assertEquals("server-request", error.challenge.requestId)
    }

    @Test
    fun `challenge without embedded captcha ID retains documented SMS captcha configuration`() {
        val response = JSONObject().put("risk_type", "fixture-risk").put("requestId", "server-request")
        val error = assertThrows(GeetestChallengeRequiredException::class.java) {
            SmsLoginProtocol.parseLoginResponse(response, phone, smsCode)
        }

        assertEquals("278c775b5dcb8dda2a2f196f60a28a34", error.challenge.captchaId)
        assertEquals("server-request", error.challenge.requestId)
    }

    @Test
    fun `missing empty or null server request ID never produces a manufactured challenge`() {
        for (requestId in listOf(null, "", " ", JSONObject.NULL)) {
            val response = JSONObject().put("result", 456).put("data", JSONObject()
                .put("risk_type", "fixture-risk").put("requestId", requestId))
            val error = assertThrows(ApiException::class.java) {
                SmsLoginProtocol.parseLoginResponse(response, phone, smsCode)
            }
            assertEquals(SmsLoginProtocol.LOGIN_STAGE, error.stage)
            assertTrue(error.message.orEmpty().contains("缺少 requestId"))
            assertTrue(error.message.orEmpty().contains("result=456"))
        }
    }

    @Test
    fun `request ID alone and null risk type do not open a challenge`() {
        val response = JSONObject().put("data", JSONObject()
            .put("risk_type", JSONObject.NULL).put("requestId", "server-request"))
        val error = assertThrows(ApiException::class.java) {
            SmsLoginProtocol.parseLoginResponse(response, phone, smsCode)
        }

        assertEquals(SmsLoginProtocol.LOGIN_STAGE, error.stage)
    }

    @Test
    fun `HTTP and malformed JSON errors never echo response content`() {
        for (response in listOf(
            VehicleListRawResponse(403, "fixture-secret-token $phone"),
            VehicleListRawResponse(200, "not-json fixture-secret-token $phone")
        )) {
            val error = assertThrows(ApiException::class.java) {
                SmsLoginProtocol.parseHttpResponse(response, SmsLoginProtocol.LOGIN_STAGE)
            }
            assertEquals(SmsLoginProtocol.LOGIN_STAGE, error.stage)
            assertEquals(response.statusCode, error.httpStatus)
            assertFalse(error.message.orEmpty().contains("fixture-secret-token"))
            assertFalse(error.message.orEmpty().contains(phone))
        }
    }

    @Test
    fun `transport exception hides sensitive URL and retains timing and endpoint stage`() {
        for (stage in listOf(SmsLoginProtocol.SMS_STAGE, SmsLoginProtocol.LOGIN_STAGE)) {
            val error = assertThrows(ApiException::class.java) {
                SmsLoginProtocol.executeRequest(stage) {
                    throw IOException("https://fixture.invalid/login?phone=$phone&token=fixture-secret")
                }
            }
            assertEquals(stage, error.stage)
            assertTrue((error.durationMs ?: -1L) >= 0L)
            assertFalse(error.message.orEmpty().contains("fixture-secret"))
            assertFalse(error.message.orEmpty().contains(phone))
            assertNull(error.cause)
        }
    }

    @Test
    fun `SMS response without business code is not reported as sent`() {
        val error = assertThrows(ApiException::class.java) { SmsLoginProtocol.checkSmsResponse(JSONObject()) }

        assertEquals(SmsLoginProtocol.SMS_STAGE, error.stage)
        assertTrue(error.message.orEmpty().contains("无业务码"))
    }

    @Test
    fun `SMS business errors retain SMS stage and redact untrusted business code`() {
        SmsLoginProtocol.checkSmsResponse(JSONObject().put("result", 0))
        SmsLoginProtocol.checkSmsResponse(JSONObject().put("code", "200"))
        val response = JSONObject().put("result", "fixture-sensitive-token").put("msg", phone)
        val error = assertThrows(ApiException::class.java) { SmsLoginProtocol.checkSmsResponse(response) }

        assertEquals(SmsLoginProtocol.SMS_STAGE, error.stage)
        assertTrue(error.message.orEmpty().contains("result=[已隐藏]"))
        assertFalse(error.message.orEmpty().contains("fixture-sensitive-token"))
        assertFalse(error.message.orEmpty().contains(phone))
    }

    private fun buildLoginRequest(smDeviceId: String, captchaResult: GeetestCaptchaResult? = null) =
        SmsLoginProtocol.buildLoginRequest(
            "rsa-fixture_-", phone, " $smsCode ", "fixture-device", smDeviceId, headers, captchaResult
        )

    private fun authObject() = JSONObject().put("accountId", "fixture-account")
        .put("token", "fixture-token").put("refreshToken", "fixture-refresh").put("tokenExpired", 21600)
}
