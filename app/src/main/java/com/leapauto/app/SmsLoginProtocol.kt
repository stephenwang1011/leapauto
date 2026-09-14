package com.leapauto.app

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal object SmsLoginProtocol {
    const val SMS_STAGE = "sendmessagecode"
    const val LOGIN_STAGE = "check_login_with_phone"
    private const val SMS_CAPTCHA_ID = "278c775b5dcb8dda2a2f196f60a28a34"

    fun requireDeviceId(provider: () -> String): String = try {
        provider().also {
            if (it.isBlank()) {
                throw ApiException("设备安全验证未返回标识，请重试", stage = "device_verification")
            }
        }
    } catch (error: DeviceSecurityException) {
        throw ApiException(error.failure.userMessage, stage = "device_verification")
    }

    fun buildSmsRequest(phoneCiphertext: String, headers: Map<String, String>): Request =
        buildRequest("compliance/$SMS_STAGE", headers, mapOf("phoneNo" to phoneCiphertext))

    fun buildLoginRequest(
        phoneCiphertext: String,
        phone: String,
        smsCode: String,
        deviceId: String,
        smDeviceId: String,
        headers: Map<String, String>,
        captchaResult: GeetestCaptchaResult? = null
    ): Request {
        val parameters = linkedMapOf(
            "phoneNoCiphertext" to phoneCiphertext,
            "smsCode" to smsCode.trim(),
            "deviceID" to deviceId,
            "smDeviceId" to smDeviceId,
            "os" to "android",
            "pageUrl" to ""
        )
        captchaResult?.let { addCaptchaParameters(parameters, it, phone) }
        return buildRequest(LOGIN_STAGE, headers, parameters, post = true)
    }

    private fun addCaptchaParameters(
        parameters: MutableMap<String, String>,
        result: GeetestCaptchaResult,
        phone: String
    ) {
        val validation = mapOf(
            "captchaOutput" to result.captchaOutput,
            "genTime" to result.genTime,
            "lotNumber" to result.lotNumber,
            "passToken" to result.passToken,
            "requestId" to result.requestId
        )
        if (validation.values.any { it.isBlank() }) {
            throw ApiException("安全验证凭据不完整，请重新验证后重试", stage = LOGIN_STAGE)
        }
        parameters.putAll(validation)
        parameters["phone"] = phone.trim()
    }

    private fun buildRequest(
        path: String,
        headers: Map<String, String>,
        parameters: Map<String, String>,
        post: Boolean = false
    ): Request {
        val url = "${LeapmotorApi.APP_USER_HOST}/app-user/applogin/$path".toHttpUrl()
            .newBuilder().apply {
                parameters.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()
        return Request.Builder().url(url).apply {
            headers.forEach { (key, value) -> header(key, value) }
            if (post) this.post("".toRequestBody(null)) else get()
        }.build()
    }

    fun parseHttpResponse(response: VehicleListRawResponse, stage: String): JSONObject {
        if (!response.isHttpSuccessful) {
            throw ApiException("请求失败，HTTP ${response.statusCode}", response.statusCode, stage = stage)
        }
        return try {
            JSONObject(response.rawBody)
        } catch (_: Exception) {
            throw ApiException("服务端响应格式异常，请稍后重试", response.statusCode, stage = stage)
        }
    }

    fun executeRequest(stage: String, execute: () -> VehicleListRawResponse): VehicleListRawResponse {
        val started = System.nanoTime()
        return try {
            execute()
        } catch (_: Exception) {
            throw ApiException(
                "网络请求失败，请检查网络后重试",
                durationMs = (System.nanoTime() - started) / 1_000_000,
                stage = stage
            )
        }
    }

    fun checkSmsResponse(response: JSONObject) {
        if (businessResult(response) !in setOf("0", "200")) {
            throw businessError(response, "发送验证码", SMS_STAGE)
        }
    }

    fun parseLoginResponse(
        response: JSONObject,
        phone: String,
        smsCode: String,
        nowMs: Long = System.currentTimeMillis()
    ): OldAuth {
        val data = response.optJSONObject("data")
        val auth = data?.optJSONObject("appLoginVO") ?: data?.optJSONObject("appOneLoginVO")
            ?: findAuthObject(response)
        val result = businessResult(response)
        val hasCredentials = auth?.scalarValue("accountId")?.isNotBlank() == true &&
            auth.stringValue("token") != null
        if (hasCredentials && (result == null || result in setOf("0", "200"))) {
            return parseOldAuth(auth, response, nowMs)
        }
        val riskType = data?.stringValue("risk_type") ?: response.stringValue("risk_type")
        if (riskType != null) {
            val requestId = data?.stringValue("requestId") ?: response.stringValue("requestId")
                ?: throw businessError(response, "登录", LOGIN_STAGE, "安全验证挑战缺少 requestId，请稍后重试")
            val captchaId = riskType.split('|').firstOrNull { it.matches(Regex("[0-9a-fA-F]{32}")) }
                ?: SMS_CAPTCHA_ID
            throw GeetestChallengeRequiredException(
                GeetestChallenge(captchaId, riskType, requestId, phone.trim(), smsCode.trim())
            )
        }
        if (auth != null && (result == null || result in setOf("0", "200"))) {
            throw businessError(response, "登录", LOGIN_STAGE, "登录响应缺少有效账号或凭据，请稍后重试")
        }
        throw businessError(response, "登录", LOGIN_STAGE)
    }

    private fun parseOldAuth(auth: JSONObject, response: JSONObject, nowMs: Long): OldAuth {
        val accountId = auth.scalarValue("accountId")?.takeIf { it.isNotBlank() }
        val token = auth.stringValue("token")
        if (accountId == null || token == null) {
            throw businessError(response, "登录", LOGIN_STAGE, "登录响应缺少有效账号或凭据，请稍后重试")
        }
        return OldAuth(
            accountId = accountId,
            token = token,
            refreshToken = auth.stringValue("refreshToken").orEmpty(),
            tokenExpired = auth.scalarValue("tokenExpired").orEmpty(),
            tokenObtainedAt = nowMs
        )
    }

    private fun findAuthObject(value: Any?): JSONObject? = when (value) {
        is JSONObject -> if (value.has("accountId") && value.has("token")) value else {
            value.keys().asSequence().mapNotNull { findAuthObject(value.opt(it)) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { findAuthObject(value.opt(it)) }
        else -> null
    }

    private fun businessResult(response: JSONObject): String? {
        val key = listOf("result", "code").firstOrNull { response.has(it) && !response.isNull(it) }
            ?: return null
        return response.scalarValue(key) ?: "[invalid]"
    }

    private fun businessError(
        response: JSONObject,
        action: String,
        stage: String,
        detail: String = safeErrorMessage(response)
    ): ApiException {
        val codes = listOf("result", "code").mapNotNull { key ->
            response.scalarValue(key)?.let { value ->
                "$key=${value.takeIf { it.matches(Regex("-?[0-9]{1,9}")) } ?: "[已隐藏]"}"
            }
        }.joinToString(", ").ifEmpty { "无业务码" }
        return ApiException("$action 失败($codes): $detail", stage = stage)
    }

    private fun safeErrorMessage(response: JSONObject): String {
        val message = response.stringValue("msg") ?: response.stringValue("message").orEmpty()
        return when {
            "临时管制" in message -> "登录触发安全风控临时管制，请稍后重试"
            "验证码" in message && ("过期" in message || "失效" in message) -> "验证码已失效，请重新获取"
            "验证码" in message && ("错误" in message || "不正确" in message) -> "验证码错误，请检查后重试"
            "频繁" in message -> "请求过于频繁，请稍后重试"
            else -> "服务端拒绝请求，请稍后重试"
        }
    }

    private fun JSONObject.stringValue(key: String): String? =
        (opt(key) as? String)?.takeIf { it.isNotBlank() }

    private fun JSONObject.scalarValue(key: String): String? = when (val value = opt(key)) {
        is String -> value
        is Number -> value.toString()
        else -> null
    }
}
