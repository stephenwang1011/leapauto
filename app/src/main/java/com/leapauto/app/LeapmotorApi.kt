package com.leapauto.app

import com.leapauto.app.bluetooth.BleKeyCertificate
import com.leapauto.app.bluetooth.BleCloudApiModels
import com.leapauto.app.bluetooth.BleCloudRequestScope
import com.leapauto.app.bluetooth.BlePassiveConfiguration
import com.leapauto.app.bluetooth.BleVehicleMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(message: String, val httpStatus: Int? = null, val durationMs: Long? = null, val retryCount: Int = 0, val stage: String = "api") : Exception(message)

/**
 * 零跑中国 App 云接口客户端（阻塞式，请在后台线程调用）。
 * 协议移植自 leap-cn-mcp 的 leapmotor-cn-sdk.js。
 */
class LeapmotorApi internal constructor(
    private val session: Session,
    private val deviceFingerprintProvider: () -> String,
    private val loginRequestExecutor: (Request) -> VehicleListRawResponse
) {
    constructor(session: Session) : this(
        session,
        { ShumeiSecurityManager.requireDeviceId() },
        { request ->
            NetworkDebugController.httpClient().newCall(request).execute().use { response ->
                VehicleListRawResponse(response.code, response.body?.string().orEmpty())
            }
        }
    )

    companion object {
        const val APP_USER_HOST = "https://appuser.leapmotor.cn"
        const val GLOBAL_HOST = "https://app-gw-global-master.leapmotor.com"
        const val DRIVING_RECORD_HOST = "https://appgateway.leapmotor.com"
        const val MILEAGE_ENERGY_DETAIL_PATH =
            "/carownerservice/v3/api/drivingrecord/mileage/energy/detail"
        const val LAST_N_WEEKS_100KM_EC_RANK_PATH =
            "/carownerservice/v3/api/drivingrecord/getLastNweeks100kmECAndRank"
        const val DRIVING_RECORD_DEBUG_PREFIX = "/carownerservice/v3/api/drivingrecord"
        const val LAST_WEEK_EC_PATH = "/carownerservice/v3/api/drivingrecord/getLastweekEC"
        const val BLUETOOTH_CERTIFICATE_PATH =
            "/carownerservice/v3/api/bluetoothkey/combine/syncBluetoothKeys"
        const val BLUETOOTH_VEHICLE_METADATA_PATH = "/carownerservice/v3/api/vehicleinfo/commonConfig"
        const val BLUETOOTH_CONFIGURATION_PATH = "/app/app-global-service/v3/api/commoninfo/transparent/conf/upload"
        const val BLUETOOTH_CALIBRATION_PATH = "/app/app-global-service/v3/api/bluetoothkey/uploadAutonomyCalibrateParams"
        private const val BLUETOOTH_METADATA_STAGE = "ble_vehicle_metadata"
        private const val BLUETOOTH_CONFIGURATION_STAGE = "ble_cloud_configuration"
        private const val BLUETOOTH_CALIBRATION_STAGE = "ble_cloud_calibration"

        // Bluetooth identity and key material must not enter the HTTP inspector or a redirect target.
        internal val bluetoothCertificateHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }

        /** 新网关 accessToken 剩余不足 5 分钟时提前续期。 */
        private const val ACCESS_REFRESH_LEEWAY_MS = 5 * 60 * 1000L

        /** 旧 token 剩余不足 60 秒时提前续期。 */
        private const val OLD_REFRESH_LEEWAY_MS = 60 * 1000L

        /** 从车况原始响应中提取 signalMap。 */
        fun extractSignalMap(resp: JSONObject): JSONObject {
            return findObjectStatic(resp) { it.opt("signalMap") is JSONObject }?.optJSONObject("signalMap")
                ?: resp.optJSONObject("data")?.optJSONObject("signalMap")
                ?: resp.optJSONObject("signalMap")
                ?: findObjectStatic(resp) { isLegacyNamedVehicleSignalMap(it) }
                ?: JSONObject()
        }

        internal fun isLastWeekEnergyEndpoint(pathSuffix: String): Boolean =
            pathSuffix.trim().trim('/').substringAfterLast('/').lowercase(Locale.ROOT) == "getlastweekec"

        internal fun isLastNWeeksEnergyEndpoint(pathSuffix: String): Boolean =
            pathSuffix.trim().trim('/').substringAfterLast('/').lowercase(Locale.ROOT) ==
                "getlastnweeks100kmecandrank"

        /**
         * Business VIN key used by a driving-record endpoint before old-token
         * signing. The weekly rank API rejects `vin` and requires `carvin`.
         */
        internal fun drivingRecordVinParameterKey(pathSuffix: String): String =
            if (isLastNWeeksEnergyEndpoint(pathSuffix)) "carvin" else "vin"

        /** Verified gateway for driving-record energy endpoints. */
        internal fun mileageEnergyHosts(): List<String> = listOf(DRIVING_RECORD_HOST)

        internal fun isMileageEnergyDetailEndpoint(pathSuffix: String): Boolean =
            pathSuffix.trim().trim('/').lowercase(Locale.ROOT) == "mileage/energy/detail"

        internal fun mileageEnergyDetailBusinessParameters(
            vin: String,
            range: PurchaseToTodayTimestamps
        ): Map<String, String> = linkedMapOf(
            "begintime" to range.beginTime.toString(),
            "endtime" to range.endTime.toString(),
            "vin" to vin
        )

        /** Business parameters for the independent recent-seven-day query. */
        internal fun recentMileageEnergyDetailBusinessParameters(
            vin: String,
            range: RecentMileageTimestamps
        ): Map<String, String> = linkedMapOf(
            "begintime" to range.beginTimeMs.toString(),
            "endtime" to range.endTimeMs.toString(),
            "vin" to vin
        )

        /** T03 returns the vehicle signal object directly, without a signalMap wrapper. */
        private fun isLegacyNamedVehicleSignalMap(value: JSONObject): Boolean {
            val markers = listOf(
                "soc", "fuelSoc", "expectedMileage", "electricRangeStandard",
                "fuelRangeStandard", "fuelRangeDynamic", "combinedRangeStandard",
                "combinedRangeDynamic", "rangeMode", "acSwitch", "longitude", "latitude"
            )
            return markers.count { value.has(it) } >= 2
        }

        /**
         * Preserve the first authentication response when the post-refresh retry cannot connect.
         * The preserved response lets callers route terminal authentication failures consistently.
         */
        internal fun preserveAuthenticationFailureOnRetry(
            initialResponse: VehicleListRawResponse,
            retry: () -> VehicleListRawResponse
        ): VehicleListRawResponse = try {
            retry()
        } catch (_: Exception) {
            initialResponse.copy(authenticationFailed = true)
        }

        private fun findObjectStatic(root: Any?, predicate: (JSONObject) -> Boolean): JSONObject? {
            if (root == null) return null
            if (root is JSONObject) {
                if (predicate(root)) return root
                val keys = root.keys()
                while (keys.hasNext()) {
                    findObjectStatic(root.opt(keys.next()), predicate)?.let { return it }
                }
            } else if (root is JSONArray) {
                for (i in 0 until root.length()) {
                    findObjectStatic(root.opt(i), predicate)?.let { return it }
                }
            }
        return null
    }
    }

    // ------------------------------------------------------------------ HTTP

    // Blocking Bluetooth preflight stays on one worker thread; other callers keep their own client.
    private val bluetoothPreflightHttpClient = ThreadLocal<OkHttpClient>()

    private fun http(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String?>? = null,
        jsonBody: JSONObject? = null,
        formBody: Map<String, String>? = null,
        queryPost: Boolean = false
    ): JSONObject {
        val started = System.currentTimeMillis()
        val response = httpRaw(url, method, headers, query, jsonBody, formBody, queryPost)
        if (!response.isHttpSuccessful) {
            throw ApiException("HTTP ${response.statusCode}: ${response.rawBody.take(200)}", response.statusCode, System.currentTimeMillis() - started, stage = url.substringAfterLast('/'))
        }
        if (response.rawBody.isBlank()) return JSONObject()
        return try {
            JSONObject(response.rawBody)
        } catch (e: Exception) {
            throw ApiException("非 JSON 响应: ${response.rawBody.take(200)}", response.statusCode, System.currentTimeMillis() - started, stage = url.substringAfterLast('/'))
        }
    }

    private fun httpRaw(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String?>? = null,
        jsonBody: JSONObject? = null,
        formBody: Map<String, String>? = null,
        queryPost: Boolean = false
    ): VehicleListRawResponse {
        val target = StringBuilder(url)
        if (query != null) {
            val qs = query
                .filterValues { it != null }
                .entries.joinToString("&") { (k, v) -> urlEncode(k) + "=" + urlEncode(v!!) }
            if (qs.isNotEmpty()) {
                target.append(if (url.contains("?")) "&" else "?").append(qs)
            }
        }
        val bodyText: String? = when {
            jsonBody != null -> jsonBody.toString()
            formBody != null -> formBody.entries.joinToString("&") { (k, v) -> urlEncode(k) + "=" + urlEncode(v) }
            queryPost -> ""
            else -> null
        }
        val mediaType = when {
            jsonBody != null -> "application/json; charset=utf-8".toMediaType()
            formBody != null -> "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
            else -> null
        }
        val requestBody = bodyText?.toRequestBody(mediaType)
        val request = Request.Builder().url(target.toString()).method(method, requestBody)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        val client = bluetoothPreflightHttpClient.get() ?: NetworkDebugController.httpClient()
        return client.newCall(request).execute().use { response ->
            VehicleListRawResponse(response.code, response.body?.string().orEmpty())
        }
    }

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")

    // --------------------------------------------------------------- 签名助手

    /** 与 SDK sortAndConcatValues 一致：null 跳过，空字符串保留，按键排序后拼接。 */
    private fun sortAndConcatValues(vararg maps: Map<String, String?>): String {
        val merged = LinkedHashMap<String, String>()
        for (map in maps) {
            for ((k, v) in map) if (v != null) merged[k] = v
        }
        return merged.keys.sorted().joinToString("") { merged[it]!! }
    }

    private fun oldAppHeaders(withToken: Boolean = true): Map<String, String> {
        val headers = linkedMapOf(
            "User-Agent" to "okhttp/4.9.3",
            "APPPlatform" to "Android",
            "APPVersion" to session.appVersion,
            "APPImei" to session.deviceId,
            "C-VERSIONS" to "APP",
            "XFX-CDN-VRS" to "v4"
        )
        if (withToken && session.oldAuth != null) {
            headers["XFX-CDN-CROSS-NODE"] = session.oldAuth!!.token
        }
        return headers
    }

    /** 旧链路签名参数（含 token 参与签名后剔除）。 */
    private fun oldSignedParams(
        params: Map<String, String> = emptyMap(),
        withRefreshToken: Boolean = false,
        includeTimespan: Boolean = true
    ): Map<String, String> {
        val old = session.oldAuth ?: throw ApiException("未登录（缺少旧 token）")
        val map = LinkedHashMap<String, String?>()
        if (includeTimespan) map["timespan"] = System.currentTimeMillis().toString()
        map["nonce"] = Crypto.randomNonce()
        map["deviceID"] = session.deviceId
        if (withRefreshToken) map["refreshtoken"] = old.refreshToken else map["token"] = old.token
        for ((k, v) in params) map[k] = v
        val signStr = Crypto.md5Short(sortAndConcatValues(map))
        map.remove(if (withRefreshToken) "refreshtoken" else "token")
        val out = LinkedHashMap<String, String>()
        for ((k, v) in map) if (v != null) out[k] = v
        out["signStr"] = signStr
        return out
    }

    private fun newGatewayHeaders(
        params: Map<String, String?> = emptyMap(),
        needLogin: Boolean = true,
        signingKeyOverride: ByteArray? = null
    ): Map<String, String> {
        val old = session.oldAuth
        val new = session.newAuth
        val nonce = Crypto.randomNonce()
        val timestamp = System.currentTimeMillis().toString()
        val headers = linkedMapOf(
            "source" to "leapmotor",
            "channel" to "1",
            "acceptLanguage" to "zh-CN",
            "x-region" to "CN",
            "x-api-signature-version" to "2.0",
            "digest" to "",
            "version" to session.appVersion,
            "deviceType" to "android",
            "nonce" to nonce,
            "timestamp" to timestamp,
            "deviceId" to session.deviceId,
            "userId" to (new?.accountId ?: old?.accountId ?: ""),
            "carvin" to session.selectedVin,
            "cartype" to session.selectedCarType,
            "x-subversion" to session.subVersion
        )
        val signHeaders = mapOf(
            "acceptLanguage" to "zh-CN",
            "channel" to "1",
            "deviceId" to session.deviceId,
            "deviceType" to "android",
            "nonce" to nonce,
            "source" to "leapmotor",
            "timestamp" to timestamp,
            "version" to session.appVersion
        )
        val signBase = sortAndConcatValues(signHeaders, params)
        if (needLogin) {
            val auth = session.newAuth ?: throw ApiException("未完成网关登录")
            headers["token"] = auth.accessToken
            headers["userId"] = auth.accountId
            val signingKey = signingKeyOverride ?: auth.signKey()
            headers["sign"] = Crypto.hmacSha256Hex(signingKey, signBase)
        } else {
            headers["sign"] = Crypto.sha256Hex(signBase)
        }
        return headers
    }

    private fun combinedAppAndGatewayHeaders(
        params: Map<String, String?> = emptyMap(),
        needLogin: Boolean = true
    ): Map<String, String> {
        val headers = oldAppHeaders(true).toMutableMap()
        if (session.newAuth != null) {
            headers.putAll(newGatewayHeaders(params, needLogin))
        }
        return headers
    }

    // ------------------------------------------------------------------ 登录

    fun sendSms(phone: String) {
        check(phone.isNotBlank()) { "手机号不能为空" }
        val request = SmsLoginProtocol.buildSmsRequest(
            Crypto.rsaEncryptPhone(phone),
            oldAppHeaders(withToken = false)
        )
        val response = SmsLoginProtocol.parseHttpResponse(
            executeLoginRequest(request, SmsLoginProtocol.SMS_STAGE), SmsLoginProtocol.SMS_STAGE
        )
        SmsLoginProtocol.checkSmsResponse(response)
    }

    /** 短信验证码登录：旧 token + 换新网关 accessToken/signKey。可附带极验验证凭据。 */
    fun loginWithSms(
        phone: String,
        smsCode: String,
        captchaResult: GeetestCaptchaResult? = null
    ) {
        check(phone.isNotBlank() && smsCode.isNotBlank()) { "手机号和验证码不能为空" }
        val smId = SmsLoginProtocol.requireDeviceId(deviceFingerprintProvider)
        val request = SmsLoginProtocol.buildLoginRequest(
            phoneCiphertext = Crypto.rsaEncryptPhone(phone),
            phone = phone,
            smsCode = smsCode,
            deviceId = session.deviceId,
            smDeviceId = smId,
            headers = oldAppHeaders(withToken = false),
            captchaResult = captchaResult
        )
        val response = SmsLoginProtocol.parseHttpResponse(
            executeLoginRequest(request, SmsLoginProtocol.LOGIN_STAGE), SmsLoginProtocol.LOGIN_STAGE
        )
        val oldAuth = SmsLoginProtocol.parseLoginResponse(response, phone, smsCode)
        session.phone = phone.trim()
        session.oldAuth = oldAuth
        exchangeNewGateway()
    }

    private fun executeLoginRequest(request: Request, stage: String): VehicleListRawResponse =
        SmsLoginProtocol.executeRequest(stage) { loginRequestExecutor(request) }

    /** 通过抓包或导出的 JSON/Token 快速登录并换取新网关凭据。支持 JSON、纯 appLoginVO、逗号分隔、或含 token/accountId 的文本。 */
    fun loginWithRawAuth(rawText: String, phoneInput: String = "") {
        val trimmed = rawText.trim()
        check(trimmed.isNotBlank()) { "导入内容不能为空" }

        var accountId = ""
        var token = ""
        var refreshToken = ""
        var tokenExpired = "21600"
        var phone = phoneInput.trim()

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val jsonObj = try {
                JSONObject(trimmed)
            } catch (e: Exception) {
                throw ApiException("JSON 格式错误，请检查粘贴内容: ${e.message}")
            }
            val authObj = jsonObj.optJSONObject("appLoginVO")
                ?: jsonObj.optJSONObject("appOneLoginVO")
                ?: jsonObj.optJSONObject("data")?.optJSONObject("appLoginVO")
                ?: jsonObj.optJSONObject("data")?.optJSONObject("appOneLoginVO")
                ?: findObject(jsonObj) { it.has("accountId") && it.has("token") }
            if (authObj != null) {
                accountId = authObj.optString("accountId").ifEmpty { authObj.optString("identifier") }
                token = authObj.optString("token").ifEmpty { authObj.optString("security") }
                refreshToken = authObj.optString("refreshToken", "")
                tokenExpired = authObj.optString("tokenExpired", "21600")
            } else {
                accountId = jsonObj.optString("accountId").ifEmpty { jsonObj.optString("identifier") }
                token = jsonObj.optString("token").ifEmpty { jsonObj.optString("security") }
                refreshToken = jsonObj.optString("refreshToken", "")
                tokenExpired = jsonObj.optString("tokenExpired", "21600")
            }
            val devId = jsonObj.optString("deviceID").ifEmpty {
                jsonObj.optString("deviceId").ifEmpty {
                    jsonObj.optString("deviceid").ifEmpty {
                        jsonObj.optString("appimei")
                    }
                }
            }.trim()
            if (devId.isNotBlank()) {
                session.deviceId = devId
            }
            val smId = jsonObj.optString("smDeviceId").ifEmpty {
                jsonObj.optString("smdeviceid").ifEmpty {
                    jsonObj.optString("sm_device_id")
                }
            }.trim()
            if (smId.isNotBlank()) {
                session.smDeviceId = smId
            }
            if (phone.isBlank()) {
                phone = jsonObj.optString("phone").ifEmpty {
                    jsonObj.optJSONObject("data")?.optString("phone") ?: ""
                }
            }
        } else if (trimmed.contains(",") && !trimmed.contains("\n")) {
            val parts = trimmed.split(",")
            accountId = parts[0].trim()
            token = parts[1].trim()
            if (parts.size > 2) refreshToken = parts[2].trim()
        } else {
            val tokenMatch = Regex("""(?:token|security|XFX-CDN-CROSS-NODE)["':=\s]+([a-zA-Z0-9_-]{16,})""", RegexOption.IGNORE_CASE).find(trimmed)
            val accountMatch = Regex("""(?:accountId|identifier|userId|account_id)["':=\s]+([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE).find(trimmed)
            val refreshMatch = Regex("""(?:refreshToken|XFX-CDN-CROSS-REFRESH-NODE)["':=\s]+([a-zA-Z0-9_-]{16,})""", RegexOption.IGNORE_CASE).find(trimmed)
            val devIdMatch = Regex("""(?:deviceID|deviceId|deviceid|appimei)["':=\s]+([a-zA-Z0-9_-]{16,})""", RegexOption.IGNORE_CASE).find(trimmed)
            val smMatch = Regex("""(?:smDeviceId|smdeviceid|sm_device_id)["':=\s]+([a-zA-Z0-9_%/+=.-]{16,})""", RegexOption.IGNORE_CASE).find(trimmed)
            token = tokenMatch?.groupValues?.getOrNull(1) ?: ""
            accountId = accountMatch?.groupValues?.getOrNull(1) ?: ""
            refreshToken = refreshMatch?.groupValues?.getOrNull(1) ?: ""
            val devId = devIdMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
            if (devId.isNotBlank()) {
                session.deviceId = devId
            }
            val smId = smMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
            if (smId.isNotBlank()) {
                session.smDeviceId = smId
            }
        }

        check(accountId.isNotBlank() && token.isNotBlank()) {
            "未能解析出有效的 accountId 和 token，请确保内容包含这两项凭据"
        }

        if (phone.isNotBlank()) {
            session.phone = phone
        }
        session.oldAuth = OldAuth(
            accountId = accountId,
            token = token,
            refreshToken = refreshToken,
            tokenExpired = tokenExpired,
            tokenObtainedAt = System.currentTimeMillis()
        )
        exchangeNewGateway()
    }

    private fun exchangeNewGateway() {
        val old = session.oldAuth ?: throw ApiException("未登录")
        val body = JSONObject().apply {
            put("identifier", old.accountId)
            put("identifierType", "1")
            put("security", old.token)
        }
        val signParams = mapOf(
            "identifier" to old.accountId,
            "identifierType" to "1",
            "security" to old.token
        )
        val resp = http(
            "$GLOBAL_HOST/base/base-user/account/v1/login",
            method = "POST",
            headers = newGatewayHeaders(signParams, needLogin = false),
            jsonBody = body
        )
        session.newAuth = extractNewAuth(resp)
    }

    private fun refreshGatewayToken() {
        val auth = session.newAuth ?: throw ApiException("未登录")
        if (auth.refreshToken.isBlank()) throw ApiException("缺少 refreshToken，无法自动续期")
        val body = JSONObject().apply { put("refreshToken", auth.refreshToken) }
        val resp = http(
            "$GLOBAL_HOST/base/base-user/token/v1/refresh",
            method = "POST",
            headers = newGatewayHeaders(mapOf("refreshToken" to auth.refreshToken), needLogin = true),
            jsonBody = body
        )
        session.newAuth = extractNewAuth(resp)
    }

    /** 旧 App token 续期（远控签名用），需手机号。 */
    fun refreshOldToken() {
        val old = session.oldAuth ?: throw ApiException("未登录")
        if (session.phone.isBlank()) throw ApiException("旧 token 续期需要手机号，请重新登录")
        val signed = oldSignedParams(
            mapOf(
                "accountId" to old.accountId,
                "accountNumber" to Crypto.rsaEncryptPhone(session.phone)
            ),
            withRefreshToken = true
        )
        val headers = oldAppHeaders(true) +
            ("XFX-CDN-CROSS-REFRESH-NODE" to old.refreshToken)
        val resp = http(
            "$APP_USER_HOST/app-user/appuseroperate/getnewtoken",
            headers = headers,
            query = signed
        )
        val data = resp.optJSONObject("data") ?: resp
        val token = data.optString("token", "")
        val code = resp.optString("code", "").ifEmpty { resp.optString("result", "") }
        val success = code == "200" || code == "0" || resp.optBoolean("success", false)
        if (token.isEmpty() || !success) {
            throw ApiException("旧 token 续期失败: ${resp.optString("msg").ifEmpty { "code=$code" }}")
        }
        val tokenExpired = if (data.has("tokenExpired") && !data.isNull("tokenExpired")) {
            data.optString("tokenExpired")
        } else {
            "21600"
        }
        val obtainedAt = System.currentTimeMillis()
        session.oldAuth = old.copy(
            token = token,
            refreshToken = data.optString("refreshToken", old.refreshToken),
            tokenExpired = tokenExpired,
            tokenObtainedAt = obtainedAt
        )
        try {
            exchangeNewGateway()
        } catch (_: Exception) {
            // best-effort
        }
    }

    private fun extractNewAuth(resp: JSONObject): NewAuth {
        val data = resp.optJSONObject("data") ?: findObject(resp) {
            it.has("accessToken") && it.optJSONObject("signParam") != null
        } ?: throw ApiException(
            "网关登录响应缺少 accessToken/signParam: ${resp.optString("msg").ifEmpty { resp.toString().take(200) }}"
        )
        val accessToken = data.optString("accessToken")
        val signParam = data.optJSONObject("signParam")
        val r2 = signParam?.optString("r2") ?: ""
        val r3 = signParam?.optString("r3") ?: ""
        if (accessToken.isEmpty() || r2.isEmpty() || r3.isEmpty()) {
            throw ApiException("网关登录响应缺少 signParam.r2/r3")
        }
        val signKey = Crypto.deriveSignKey(accessToken, r2, r3)
        val boundDeviceId = Crypto.jwtDeviceId(accessToken)
        if (!boundDeviceId.isNullOrBlank()) {
            session.deviceId = boundDeviceId
        }
        val expiresAt = Crypto.jwtExpiryMs(accessToken).takeIf { it > 0 }
            ?: data.optLong("tokenExpireTime").takeIf { it > 0 }?.let {
                System.currentTimeMillis() + it * 1000
            } ?: 0L
        return NewAuth(
            // The gateway response may carry a different account identifier. The gateway HMAC
            // contract keeps using the account ID returned by the SMS-login (old) session.
            accountId = session.oldAuth?.accountId ?: data.optString("accountId"),
            accessToken = accessToken,
            refreshToken = data.optString("refreshToken", ""),
            signKeyBase64 = android.util.Base64.encodeToString(signKey, android.util.Base64.DEFAULT),
            accessTokenExpiresAt = expiresAt
        )
    }

    // ---------------------------------------------------------------- 车辆

    fun listVehicles(): List<Vehicle> {
        val resp = gatewayFetch("$GLOBAL_HOST/app/app-global-service/v1/vehicle/list", "GET")
        val rawObjects = collectObjects(resp) { it.has("vin") && it.optString("vin").length >= 8 }
        val vehicles = rawObjects
            .distinctBy { it.optString("vin") }
            .map {
                val carType = it.optString("carType")
                    .ifEmpty { it.optString("cartype") }
                    .ifEmpty { it.optJSONObject("modelParam")?.optString("carType").orEmpty() }
                    .ifEmpty { it.optString("model") }
                val rightList = it.optString("rightList")
                val isReev = carType.contains("REEV", ignoreCase = true) ||
                    carType.contains("增程") ||
                    rightList.split(",").map { c -> c.trim() }.contains("380")
                val inferredPowerType = if (isReev) {
                    SessionStore.VehiclePowerType.RANGE_EXTENDER
                } else {
                    SessionStore.VehiclePowerType.PURE_ELECTRIC
                }
                val nickname = it.optString("nickName")
                    .ifEmpty { it.optString("nickname") }
                    .ifEmpty { it.optString("vehicleName") }
                    .ifEmpty { it.optString("carName") }
                val year = it.optString("year")
                    .ifEmpty { it.optJSONObject("modelParam")?.optString("year").orEmpty() }
                    .ifEmpty { it.optString("modelYear") }
                    .ifEmpty { it.optString("modelyear") }
                    .ifEmpty { it.optString("carYear") }
                val isShared = it.optBoolean("isShare", false) ||
                    it.optBoolean("isShared", false) ||
                    it.optBoolean("share", false) ||
                    it.optString("bindType") == "2" ||
                    it.optInt("bindType", 0) == 2 ||
                    it.optString("userType") == "2" ||
                    it.optInt("userType", 0) == 2 ||
                    it.optString("shareType") == "1" ||
                    it.optInt("shareType", 0) == 1 ||
                    it.optInt("isMaster", 1) == 0 ||
                    (it.has("isOwner") && !it.optBoolean("isOwner", true)) ||
                    (it.has("owner") && !it.optBoolean("owner", true))
                Vehicle(
                    vin = it.optString("vin"),
                    carType = carType,
                    hvacCapability = HvacCapabilityParser.parse(it.optJSONObject("funcConfig")),
                    nickname = nickname,
                    year = year,
                    color = it.optString("outColor").ifEmpty { it.optString("color") },
                    powerType = inferredPowerType,
                    isSharedAccount = isShared
                )
            }
        if (vehicles.isNotEmpty()) {
            if (session.selectedVin.isBlank()) {
                session.selectedVin = vehicles.first().vin
                session.selectedCarType = vehicles.first().carType
                session.route = null
            }
            vehicles.firstOrNull { it.vin == session.selectedVin }?.let { selected ->
                session.selectedCarType = selected.carType.ifBlank { session.selectedCarType }
                session.hvacCapability = selected.hvacCapability
                session.selectedNickname = selected.nickname
                session.selectedYear = selected.year
            }
        }
        return vehicles
    }

    /**
     * 获取车辆图片元数据（包含官方 CDN 图片 URL 与切图包 Key）。
     * 若接口不支持或调用失败，返回 null，由展示层降级至本地静态图。
     */
    fun getVehiclePictureMeta(
        vin: String = session.selectedVin,
        diagnostics: MutableList<String>? = null
    ): VehiclePictureMeta? {
        if (vin.isBlank()) return null
        val formParams = mapOf(
            "deviceID" to session.deviceId,
            "vin" to vin
        )

        val candidatePaths = listOf(
            "/carownerservice/v3/api/carpicture/3d/key",
            "/carownerservice/v3/api/carpicture/key",
            "/carownerservice/v3/api/3d/key",
            "/carownerservice/vehicle/v1/carpicture/key",
            "/carownerservice/v1/vehicle/carpicture/key"
        )

        // 1. 优先尝试 appgateway.leapmotor.com（旧网关/能耗同一服务集群）
        for (path in candidatePaths) {
            val url = "$DRIVING_RECORD_HOST$path"

            // 1a. 旧链路签名 GET（很多查询元数据接口为 GET）
            try {
                val signed = oldSignedParams(
                    params = mapOf("vin" to vin, "carvin" to vin, "deviceID" to session.deviceId),
                    includeTimespan = true
                )
                val headers = combinedAppAndGatewayHeaders(signed, needLogin = true)
                val resp = http(
                    url = url,
                    method = "GET",
                    headers = headers,
                    query = signed
                )
                val extracted = parsePictureMetaFromResponse(resp, url)
                if (extracted != null) {
                    diagnostics?.add("成功: $url [OldSign GET]")
                    return extracted
                }
                val msg = resp.optString("msg").ifEmpty { resp.optString("message") }
                diagnostics?.add("$url [OldSign GET] 无图: $msg (code=${resp.opt("code")})")
            } catch (e: Exception) {
                diagnostics?.add("$url [OldSign GET] 异常: ${e.message?.take(80)}")
            }

            // 1b. 旧链路签名 POST（URL 携带签名 query，Body 携带表单）
            try {
                val signed = oldSignedParams(
                    params = mapOf("vin" to vin, "carvin" to vin, "deviceID" to session.deviceId),
                    includeTimespan = true
                )
                val headers = combinedAppAndGatewayHeaders(signed, needLogin = true)
                val resp = http(
                    url = url,
                    method = "POST",
                    headers = headers,
                    query = signed,
                    formBody = formParams
                )
                val extracted = parsePictureMetaFromResponse(resp, url)
                if (extracted != null) {
                    diagnostics?.add("成功: $url [OldSign POST]")
                    return extracted
                }
                val msg = resp.optString("msg").ifEmpty { resp.optString("message") }
                diagnostics?.add("$url [OldSign POST] 无图: $msg (code=${resp.opt("code")})")
            } catch (e: Exception) {
                diagnostics?.add("$url [OldSign POST] 异常: ${e.message?.take(80)}")
            }

            // 1c. 新网关签名 POST (Form)
            try {
                val resp = gatewayFetch(
                    url = url,
                    method = "POST",
                    params = formParams,
                    formBody = formParams
                )
                val extracted = parsePictureMetaFromResponse(resp, url)
                if (extracted != null) {
                    diagnostics?.add("成功: $url [NewGw Form]")
                    return extracted
                }
                val msg = resp.optString("msg").ifEmpty { resp.optString("message") }
                diagnostics?.add("$url [NewGw Form] 无图: $msg")
            } catch (e: Exception) {
                diagnostics?.add("$url [NewGw Form] 异常: ${e.message?.take(80)}")
            }

            // 1d. 新网关签名 POST (JSON)
            try {
                val jsonBody = JSONObject().apply {
                    put("deviceID", session.deviceId)
                    put("vin", vin)
                    put("carvin", vin)
                }
                val resp = gatewayFetch(
                    url = url,
                    method = "POST",
                    params = mapOf("vin" to vin),
                    jsonBody = jsonBody
                )
                val extracted = parsePictureMetaFromResponse(resp, url)
                if (extracted != null) {
                    diagnostics?.add("成功: $url [NewGw JSON]")
                    return extracted
                }
            } catch (e: Exception) {
                diagnostics?.add("$url [NewGw JSON] 异常: ${e.message?.take(80)}")
            }
        }

        // 2. 尝试车辆专属区域网关 route.appRegion（如已解析）
        val routeHost = session.route?.appRegion?.takeIf { it.isNotBlank() }
        if (routeHost != null) {
            for (path in candidatePaths) {
                val url = "$routeHost$path"
                try {
                    val resp = gatewayFetch(
                        url = url,
                        method = "POST",
                        params = formParams,
                        formBody = formParams
                    )
                    val extracted = parsePictureMetaFromResponse(resp, url)
                    if (extracted != null) {
                        diagnostics?.add("成功: $url [RouteGw Form]")
                        return extracted
                    }
                } catch (e: Exception) {
                    diagnostics?.add("$url [RouteGw] 异常: ${e.message?.take(80)}")
                }
            }
        }

        // 3. 备用尝试 GLOBAL_HOST
        for (path in candidatePaths.take(2)) {
            val url = "$GLOBAL_HOST$path"
            try {
                val resp = gatewayFetch(
                    url = url,
                    method = "POST",
                    params = formParams,
                    formBody = formParams
                )
                val extracted = parsePictureMetaFromResponse(resp, url)
                if (extracted != null) {
                    diagnostics?.add("成功: $url [GlobalGw]")
                    return extracted
                }
            } catch (e: Exception) {
                diagnostics?.add("$url [GlobalGw] 异常: ${e.message?.take(80)}")
            }
        }

        return null
    }

    private fun parsePictureMetaFromResponse(resp: JSONObject, sourceUrl: String = ""): VehiclePictureMeta? {
        val data = resp.optJSONObject("data") ?: resp
        val shareBindUrl = data.optString("shareBindUrl").takeIf { it.isNotBlank() }
            ?: data.optString("url").takeIf { it.isNotBlank() }
            ?: data.optString("imageUrl").takeIf { it.isNotBlank() }
            ?: data.optString("picUrl").takeIf { it.isNotBlank() }
            ?: data.optString("carPic").takeIf { it.isNotBlank() }
        if (shareBindUrl != null) {
            val key = data.optString("key").ifBlank { data.optString("pictureKey") }
            val secureUrl = if (shareBindUrl.startsWith("http://", ignoreCase = true)) {
                "https://" + shareBindUrl.substring(7)
            } else {
                shareBindUrl
            }
            return VehiclePictureMeta(
                pictureKey = key,
                shareBindUrl = secureUrl,
                sourceUrl = sourceUrl,
                rawData = data
            )
        }
        return null
    }

    /**
     * 下载官方 3D 车模交互 H5/资产压缩包，并解压至目标缓存目录 [destDir]。
     * 支持 h5Key 运行时网页包与 srcKey 3D 模型素材包在同一目录下的安全解压与合并。
     */
    fun downloadCarModelPackage(key: String, destDir: File): Boolean {
        if (key.isBlank()) return false
        val path = "/carownerservice/v3/api/carpicture/key/package"
        val candidateHosts = listOf(
            session.route?.appCenter?.takeIf { it.isNotBlank() },
            session.route?.appRegion?.takeIf { it.isNotBlank() },
            DRIVING_RECORD_HOST,
            APP_USER_HOST
        ).filterNotNull().distinct()

        destDir.mkdirs()
        val canonicalDest = destDir.canonicalFile

        fun tryDownload(host: String): Pair<Boolean, Boolean> {
            val cleanHost = host.trim().removeSuffix("/")
            val url = "$cleanHost$path"
            val signed = oldSignedParams(
                params = mapOf("key" to key),
                includeTimespan = true
            )
            val headers = oldAppHeaders(withToken = true)
            val queryStr = signed.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            val request = Request.Builder()
                .url("$url?$queryStr")
                .get()
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

            val client = NetworkDebugController.httpClient()
            return client.newCall(request).execute().use { response ->
                android.util.Log.i("CarModelDownload", "downloadCarModelPackage: url=$url, code=${response.code}")
                if (!response.isSuccessful) {
                    android.util.Log.w("CarModelDownload", "downloadCarModelPackage HTTP error: ${response.code} ${response.message}")
                    return@use false to false
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    android.util.Log.w("CarModelDownload", "downloadCarModelPackage body empty")
                    return@use false to false
                }
                if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) {
                    val content = bytes.take(150).toByteArray().toString(Charsets.UTF_8)
                    android.util.Log.w("CarModelDownload", "downloadCarModelPackage not ZIP! size=${bytes.size}, content=$content")
                    val isTokenError = content.contains("39") || content.contains("信息校验失败") || content.contains("token")
                    return@use false to isTokenError
                }

                ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val targetFile = File(canonicalDest, entry.name).canonicalFile
                        if (!targetFile.path.startsWith(canonicalDest.path + File.separator)) {
                            throw IOException("Zip traversal entry rejected: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                    }
                }
                true to false
            }
        }

        for (host in candidateHosts) {
            try {
                var (success, needRetryToken) = tryDownload(host)
                if (needRetryToken) {
                    android.util.Log.i("CarModelDownload", "3D 车模下载遇到 token 校验失败，尝试自动续期后重试一次...")
                    runCatching { refreshOldToken() }
                    val retry = tryDownload(host)
                    success = retry.first
                }
                if (success) return true
            } catch (e: Exception) {
                android.util.Log.w("CarModelDownload", "下载异常 host=$host: ${e.message}")
            }
        }
        return false
    }

    /**
     * Read-only mileage/energy detail from the legacy app-user service.
     *
     * This endpoint is intentionally separate from vehicle-state polling and
     * remote-control calls. The response is returned to the in-memory parser
     * only; callers must not persist or upload it.
     */
    fun getMileageEnergy(): JSONObject {
        return getMileageEnergy(purchaseAtMs = null)
    }

    /**
     * Read the detail endpoint with the current purchase-day-to-now window.
     * [purchaseAtMs] is optional because older sessions do not have a stored
     * purchase timestamp; callers can infer it from a cached delivery-days
     * response before calling this method.
     */
    fun getMileageEnergy(
        purchaseAtMs: Long?,
        nowMs: Long = System.currentTimeMillis()
    ): JSONObject {
        requireVin()
        try {
            ensureFreshOldToken()
        } catch (_: Exception) {
            // The signed request below remains the source of truth; a still
            // valid legacy token can continue without an unnecessary refresh.
        }

        fun candidates(): List<String> = mileageEnergyHosts()

        fun attempt(): JSONObject {
            var last: ApiException? = null
            // 1. 优先对齐官方 App v1.22 抓包格式（api.log 第 2216 行）：带 vin 与 timespan，挂载新网关鉴权
            val singleVinSigned = oldSignedParams(
                params = mapOf("vin" to session.selectedVin),
                includeTimespan = true
            )
            val singleVinHeaders = combinedAppAndGatewayHeaders(singleVinSigned, needLogin = true)
            for (host in candidates()) {
                try {
                    val resp = http(
                        "$host$MILEAGE_ENERGY_DETAIL_PATH",
                        headers = singleVinHeaders,
                        query = singleVinSigned
                    )
                    val data = resp.optJSONObject("data")
                    if (data != null && data.length() > 0) return resp
                } catch (e: ApiException) {
                    last = e
                }
            }

            // 2. 兼容回退：带 begintime/endtime 时间范围参数
            val range = DrivingRecordTimeRange.purchaseToToday(purchaseAtMs, nowMs)
            val rangeSigned = oldSignedParams(
                params = mileageEnergyDetailBusinessParameters(session.selectedVin, range),
                includeTimespan = true
            )
            val rangeHeaders = combinedAppAndGatewayHeaders(rangeSigned, needLogin = true)
            for (host in candidates()) {
                try {
                    return http(
                        "$host$MILEAGE_ENERGY_DETAIL_PATH",
                        headers = rangeHeaders,
                        query = rangeSigned
                    )
                } catch (e: ApiException) {
                    last = e
                }
            }
            throw (last ?: ApiException("能耗接口请求失败"))
        }

        var response: JSONObject
        try {
            response = attempt()
        } catch (error: ApiException) {
            // The old service can signal an expired token either as a business
            // result (39) or as an HTTP 401/403. Both paths get one refresh and
            // one retry; no energy request is added to the vehicle polling loop.
            if (error.httpStatus != 401 && error.httpStatus != 403) throw error
            refreshOldToken()
            try {
                response = attempt()
            } catch (retryError: ApiException) {
                if (retryError.httpStatus == 401 || retryError.httpStatus == 403) {
                    throw ApiException(
                        "旧 token 续期失败：refresh token失效",
                        retryError.httpStatus,
                        retryError.durationMs,
                        retryError.retryCount,
                        retryError.stage
                    )
                }
                throw retryError
            }
        }
        if (isOldAuthFailure(response)) {
            refreshOldToken()
            response = attempt()
            if (isOldAuthFailure(response)) {
                // The only legacy-auth retry has already completed. Preserve
                // the established terminal-expiry wording so MainActivity
                // enters its explicit re-login flow instead of treating this
                // as an ordinary business failure.
                throw ApiException("旧 token 续期失败：refresh token失效")
            }
        }
        checkResult(response, "能耗查询")
        return response
    }

    /**
     * Read only the recent seven-day mileage detail window.
     *
     * This is intentionally separate from [getMileageEnergy], whose existing
     * purchase-day-to-now contract feeds the lifetime energy cards.  The
     * recovered client sends millisecond `begintime/endtime` values for this
     * window and includes the normal `timespan`/nonce/deviceID/signStr query
     * fields generated by [oldSignedParams].
     */
    fun getRecentMileageEnergy(nowMs: Long = System.currentTimeMillis()): JSONObject {
        requireVin()
        runCatching { ensureFreshOldToken() }

        fun attempt(): JSONObject {
            // 优先直接查询单 VIN（对齐官方抓包日志，新网关返回完整 7 日及累计能耗明细）
            val singleVinSigned = oldSignedParams(
                params = mapOf("vin" to session.selectedVin),
                includeTimespan = true
            )
            val singleVinHeaders = combinedAppAndGatewayHeaders(singleVinSigned, needLogin = true)
            for (host in mileageEnergyHosts()) {
                try {
                    val resp = http(
                        "$host$MILEAGE_ENERGY_DETAIL_PATH",
                        headers = singleVinHeaders,
                        query = singleVinSigned
                    )
                    val data = resp.optJSONObject("data")
                    if (data?.optJSONArray("detail") != null) return resp
                } catch (_: Exception) {}
            }

            // 备用：带 recent 7-day 时间范围参数
            val range = DrivingRecordTimeRange.recentMileageRange(nowMs)
            val signed = oldSignedParams(
                params = recentMileageEnergyDetailBusinessParameters(session.selectedVin, range),
                includeTimespan = true
            )
            val headers = combinedAppAndGatewayHeaders(signed, needLogin = true)
            return http(
                "${DRIVING_RECORD_HOST}${MILEAGE_ENERGY_DETAIL_PATH}",
                headers = headers,
                query = signed
            )
        }

        var response: JSONObject
        var authenticationRefreshAttempted = false
        try {
            response = attempt()
        } catch (error: ApiException) {
            if (error.httpStatus != 401 && error.httpStatus != 403) throw error
            refreshOldToken()
            authenticationRefreshAttempted = true
            try {
                response = attempt()
            } catch (retryError: ApiException) {
                if (retryError.httpStatus == 401 || retryError.httpStatus == 403) {
                    throw ApiException(
                        "旧 token 续期失败：refresh token失效",
                        retryError.httpStatus,
                        retryError.durationMs,
                        retryError.retryCount,
                        "mileage_energy_recent"
                    )
                }
                throw retryError
            }
        }
        if (isOldAuthFailure(response)) {
            if (authenticationRefreshAttempted) {
                throw ApiException(
                    "旧 token 续期失败：refresh token失效",
                    stage = "mileage_energy_recent"
                )
            }
            refreshOldToken()
            response = attempt()
            if (isOldAuthFailure(response)) {
                throw ApiException(
                    "旧 token 续期失败：refresh token失效",
                    stage = "mileage_energy_recent"
                )
            }
        }
        checkResult(response, "近7日里程查询")
        return response
    }

    /**
     * User-triggered raw probe for the weekly 100-km energy/rank endpoint.
     * It uses the same legacy signing, VIN and route fallback as mileage energy,
     * but deliberately does not interpret or discard business fields.
     */
    fun getDrivingRecordDebugRaw(
        pathSuffix: String,
        requestBody: String? = null
    ): VehicleListRawResponse {
        val suffix = pathSuffix.trim()
            .let { if (it.startsWith('/')) it else "/$it" }
        require(
            suffix.length > 1 &&
                !suffix.contains('?') &&
                !suffix.contains('#') &&
                !suffix.contains("..") &&
                suffix.all { it.isLetterOrDigit() || it == '/' || it == '_' || it == '-' }
        ) { "接口路径后缀格式不合法" }
        requireVin()
        if (isLastWeekEnergyEndpoint(suffix)) return getLastWeekEcRaw()
        val bodyJson = requestBody?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                JSONObject(it)
            } catch (_: Exception) {
                throw IllegalArgumentException("请求体必须是合法 JSON")
            }
        }
        val fixedGetEndpoint = isMileageEnergyDetailEndpoint(suffix)
        runCatching { ensureFreshOldToken() }

        fun candidates(): List<String> = if (
            isLastNWeeksEnergyEndpoint(suffix) || fixedGetEndpoint
        ) {
            // Driving-record energy endpoints are served by the international
            // app gateway, not the newer global gateway.
            listOf(DRIVING_RECORD_HOST)
        } else buildList {
            add(GLOBAL_HOST)
            runCatching { ensureRoute() }.getOrNull()?.let { route ->
                route.appRegion.takeIf { it.isNotBlank() }?.let(::add)
                route.appCenter.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct()

        fun attempt(): VehicleListRawResponse {
            var last: VehicleListRawResponse? = null
            // The weekly rank endpoint specifically requires the legacy
            // `carvin` query parameter. Mileage detail keeps its verified
            // `vin` contract. Keep that distinction before signing, because
            // the gateway validates the business parameter name.
            val vinKey = drivingRecordVinParameterKey(suffix)
            val signed = if (fixedGetEndpoint) {
                val range = DrivingRecordTimeRange.purchaseToToday(null, System.currentTimeMillis())
                oldSignedParams(
                    params = mileageEnergyDetailBusinessParameters(
                        vin = bodyJson?.optString("vin")?.takeIf { it.isNotBlank() }
                            ?: session.selectedVin,
                        range = range
                    ),
                    includeTimespan = false
                )
            } else {
                oldSignedParams(
                    mapOf(
                        vinKey to (
                            bodyJson?.optString(vinKey)?.takeIf { it.isNotBlank() }
                                ?: session.selectedVin
                        )
                    )
                )
            }
            val headers = combinedAppAndGatewayHeaders(signed, needLogin = true)
            for (host in candidates()) {
                val response = httpRaw(
                    "$host$DRIVING_RECORD_DEBUG_PREFIX$suffix",
                    // The verified mileage/energy detail contract is GET with
                    // signed query parameters. Custom debug paths may still
                    // opt into POST by supplying a JSON request body.
                    method = if (bodyJson != null && !fixedGetEndpoint) "POST" else "GET",
                    headers = headers,
                    query = signed,
                    jsonBody = bodyJson?.takeUnless { fixedGetEndpoint }
                )
                last = response
                if (response.isHttpSuccessful) return response
            }
            return last ?: throw ApiException("周能耗排行接口请求失败", stage = "energy_rank")
        }

        var response = attempt()
        if (response.statusCode == 401 || response.statusCode == 403) {
            refreshOldToken()
            response = attempt()
        } else if (response.isHttpSuccessful && isOldAuthFailure(runCatching { JSONObject(response.rawBody) }.getOrNull() ?: JSONObject())) {
            refreshOldToken()
            response = attempt()
        }
        return response
    }

    /** Last-week energy composition endpoint used by the v3 GET contract. */
    private fun getLastWeekEcRaw(retriedAfterRefresh: Boolean = false): VehicleListRawResponse {
        runCatching { ensureFreshOldToken() }
        val range = DrivingRecordTimeRange.previousWeek(System.currentTimeMillis())
        val begin = range.beginTime.toString()
        val end = range.endTime.toString()
        val carvin = session.selectedVin
        // The v3 GET endpoint uses the international business parameters, but
        // still requires the legacy request signature fields. oldSignedParams
        // adds timespan/nonce/deviceID and calculates signStr with the token;
        // the token itself remains in the legacy request header.
        val query = oldSignedParams(
            mapOf(
                "begintime" to begin,
                "endtime" to end,
                "carvin" to carvin
            )
        )
        val headers = combinedAppAndGatewayHeaders(query, needLogin = true)
        var response = httpRaw(
            "$DRIVING_RECORD_HOST$LAST_WEEK_EC_PATH",
            method = "GET",
            headers = headers,
            query = query
        )
        if (!retriedAfterRefresh &&
            (response.statusCode == 401 || response.statusCode == 403 ||
                (response.isHttpSuccessful && isOldAuthFailure(
                    runCatching { JSONObject(response.rawBody) }.getOrNull() ?: JSONObject()
                )))
        ) {
            refreshOldToken()
            response = getLastWeekEcRaw(retriedAfterRefresh = true)
        }
        return response
    }

    fun getLastNWeeks100kmEcAndRankRaw(): VehicleListRawResponse =
        getDrivingRecordDebugRaw(LAST_N_WEEKS_100KM_EC_RANK_PATH.removePrefix(DRIVING_RECORD_DEBUG_PREFIX))

    /** Typed read-only response used by the Energy screen. */
    fun getLastNWeeks100kmEcAndRank(): JSONObject {
        val raw = getLastNWeeks100kmEcAndRankRaw()
        if (!raw.isHttpSuccessful) {
            throw ApiException(
                "周能耗排行接口请求失败（HTTP ${raw.statusCode}）",
                httpStatus = raw.statusCode,
                stage = "energy_rank"
            )
        }
        val response = JSONObject(raw.rawBody)
        checkResult(response, "周能耗排行查询")
        return response
    }

    /** Typed read-only response for the previous week's energy composition. */
    fun getLastWeekEc(): JSONObject {
        val raw = getLastWeekEcRaw()
        if (!raw.isHttpSuccessful) {
            throw ApiException(
                "上周能耗构成接口请求失败（HTTP ${raw.statusCode}）",
                httpStatus = raw.statusCode,
                stage = "energy_last_week_composition"
            )
        }
        val response = JSONObject(raw.rawBody)
        checkResult(response, "上周能耗构成查询")
        return response
    }

    /** Sync key material only after an explicit Bluetooth setup action. */
    fun fetchBluetoothKeyCertificate(): BleKeyCertificate {
        requireBluetoothCertificateSession()
        ensureFreshOldToken()
        if (session.route?.appCenter.isNullOrBlank()) getCarRoute()
        return fetchBluetoothKeyCertificate { request ->
            bluetoothCertificateHttpClient.newCall(request).execute().use { response ->
                VehicleListRawResponse(response.code, response.body?.string().orEmpty())
            }
        }
    }

    internal fun fetchBluetoothKeyCertificate(
        execute: (Request) -> VehicleListRawResponse
    ): BleKeyCertificate {
        val request = buildBluetoothCertificateRequest()
        val response = try {
            execute(request)
        } catch (_: Exception) {
            throw ApiException("蓝牙钥匙证书同步连接失败，请稍后重试", stage = "ble_certificate")
        }
        if (!response.isHttpSuccessful) {
            throw ApiException(
                "蓝牙钥匙证书同步失败（HTTP ${response.statusCode}）",
                httpStatus = response.statusCode,
                stage = "ble_certificate"
            )
        }
        val json = try {
            JSONObject(response.rawBody)
        } catch (_: Exception) {
            throw ApiException("蓝牙钥匙证书响应格式无效", stage = "ble_certificate")
        }
        return try {
            BleKeyCertificate.fromResponse(json, session.selectedVin)
        } catch (error: IllegalArgumentException) {
            throw ApiException(error.message ?: "蓝牙钥匙证书无效", stage = "ble_certificate")
        }
    }

    internal fun buildBluetoothCertificateRequest(): Request {
        requireBluetoothCertificateSession()
        val center = session.route?.appCenter?.toHttpUrlOrNull()
            ?: throw ApiException("缺少车辆蓝牙钥匙服务路由", stage = "ble_certificate")
        if (!center.isHttps || center.username.isNotEmpty() || center.password.isNotEmpty() ||
            center.query != null || center.fragment != null || center.encodedPath != "/" ||
            !(center.host.endsWith(".leapmotor.cn") || center.host.endsWith(".leapmotor.com"))
        ) {
            throw ApiException("车辆蓝牙钥匙服务路由无效", stage = "ble_certificate")
        }
        val body = FormBody.Builder().apply {
            oldSignedParams(mapOf("vin" to session.selectedVin)).forEach { (key, value) -> add(key, value) }
        }.build()
        return Request.Builder()
            .url(center.newBuilder().encodedPath(BLUETOOTH_CERTIFICATE_PATH).build())
            .post(body)
            .apply { oldAppHeaders().forEach { (key, value) -> header(key, value) } }
            .header("APPVersion", session.appVersion)
            .build()
    }

    private fun requireBluetoothCertificateSession() {
        if (session.oldAuth?.token.isNullOrBlank() || session.oldAuth?.accountId.isNullOrBlank()) {
            throw ApiException("请先登录后同步蓝牙钥匙", stage = "ble_certificate")
        }
        if (session.deviceId.isBlank() || session.selectedVin.isBlank()) {
            throw ApiException("请先选择车辆并确认设备信息", stage = "ble_certificate")
        }
    }

    /** Read advisory metadata only when the Bluetooth management flow requests it. */
    fun getBluetoothVehicleMetadata(): BleVehicleMetadata? = getBluetoothVehicleMetadata(
        execute = ::executeBluetoothCloudRequest,
        refreshSession = { refreshBluetoothCloudTokens(needsOldToken = false) }
    )

    internal fun getBluetoothVehicleMetadata(
        execute: (Request) -> VehicleListRawResponse,
        fetchedAtMillis: Long? = null,
        refreshSession: () -> Unit = {}
    ): BleVehicleMetadata? {
        val scope = prepareBluetoothCloudRequest(BLUETOOTH_METADATA_STAGE, needsOldToken = false, refreshSession = refreshSession)
        val response = bluetoothCloudResponse(buildBluetoothVehicleMetadataRequest(), BLUETOOTH_METADATA_STAGE, scope, execute)
        return try {
            BleVehicleMetadata.fromResponse(response, scope.vin, fetchedAtMillis ?: System.currentTimeMillis())
                .also { requireBluetoothCloudScope(scope, BLUETOOTH_METADATA_STAGE) }
        } catch (_: IllegalArgumentException) {
            throw ApiException("车辆蓝牙配置响应无效", stage = BLUETOOTH_METADATA_STAGE)
        }
    }

    /** Cloud acceptance does not mean the vehicle has applied these preferences. */
    fun uploadBluetoothConfiguration(configuration: BlePassiveConfiguration) {
        uploadBluetoothConfiguration(configuration,
            refreshSession = { refreshBluetoothCloudTokens(needsOldToken = true) },
            execute = ::executeBluetoothCloudRequest)
    }

    internal fun uploadBluetoothConfiguration(
        configuration: BlePassiveConfiguration,
        execute: (Request) -> VehicleListRawResponse
    ) = uploadBluetoothConfiguration(configuration, refreshSession = {}, execute = execute)

    internal fun uploadBluetoothConfiguration(
        configuration: BlePassiveConfiguration,
        refreshSession: () -> Unit,
        execute: (Request) -> VehicleListRawResponse
    ) {
        val scope = prepareBluetoothCloudRequest(BLUETOOTH_CONFIGURATION_STAGE, needsOldToken = true, refreshSession = refreshSession)
        bluetoothCloudResponse(buildBluetoothConfigurationRequest(configuration), BLUETOOTH_CONFIGURATION_STAGE, scope, execute)
    }

    /** A null value deletes the cloud calibration; it does not reset the vehicle configuration. */
    fun uploadBluetoothCalibration(params: String?, model: String) {
        uploadBluetoothCalibration(params, model,
            refreshSession = { refreshBluetoothCloudTokens(needsOldToken = true) },
            execute = ::executeBluetoothCloudRequest)
    }

    internal fun uploadBluetoothCalibration(
        params: String?,
        model: String,
        execute: (Request) -> VehicleListRawResponse
    ) = uploadBluetoothCalibration(params, model, refreshSession = {}, execute = execute)

    internal fun uploadBluetoothCalibration(
        params: String?,
        model: String,
        refreshSession: () -> Unit,
        execute: (Request) -> VehicleListRawResponse
    ) {
        bluetoothCalibrationParameters(params, model)
        val scope = prepareBluetoothCloudRequest(BLUETOOTH_CALIBRATION_STAGE, needsOldToken = true, refreshSession = refreshSession)
        bluetoothCloudResponse(buildBluetoothCalibrationRequest(params, model), BLUETOOTH_CALIBRATION_STAGE, scope, execute)
    }

    internal fun buildBluetoothVehicleMetadataRequest(): Request {
        val params = linkedMapOf("appVersion" to session.appVersion, "osType" to "Android", "vin" to session.selectedVin)
        return buildBluetoothCloudRequest(BLUETOOTH_VEHICLE_METADATA_PATH, params, BLUETOOTH_METADATA_STAGE, upload = false)
    }

    internal fun buildBluetoothConfigurationRequest(configuration: BlePassiveConfiguration): Request {
        requireBluetoothCloudSession(BLUETOOTH_CONFIGURATION_STAGE, needsOldToken = true)
        return buildBluetoothCloudRequest(
            BLUETOOTH_CONFIGURATION_PATH,
            BleCloudApiModels.configurationParameters(session.selectedVin, configuration),
            BLUETOOTH_CONFIGURATION_STAGE,
            upload = true
        )
    }

    internal fun buildBluetoothCalibrationRequest(params: String?, model: String): Request =
        buildBluetoothCloudRequest(
            BLUETOOTH_CALIBRATION_PATH, bluetoothCalibrationParameters(params, model),
            BLUETOOTH_CALIBRATION_STAGE, upload = true
        )

    private fun bluetoothCalibrationParameters(params: String?, model: String): Map<String, String> = try {
        BleCloudApiModels.calibrationParameters(session.selectedVin, params, model)
    } catch (_: IllegalArgumentException) {
        throw ApiException("蓝牙标定参数无效", stage = BLUETOOTH_CALIBRATION_STAGE)
    }

    private fun buildBluetoothCloudRequest(
        path: String,
        params: Map<String, String>,
        stage: String,
        upload: Boolean
    ): Request = try {
        requireBluetoothCloudSession(stage, needsOldToken = upload)
        val fields = if (upload) oldSignedParams(params) else params
        val host = if (upload) GLOBAL_HOST else DRIVING_RECORD_HOST
        val url = requireNotNull("$host$path".toHttpUrlOrNull()).newBuilder().apply {
            if (!upload) fields.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        Request.Builder().url(url).apply {
            if (upload) post(FormBody.Builder().apply {
                fields.forEach { (key, value) -> add(key, value) }
            }.build()) else get()
            newGatewayHeaders(fields, needLogin = true, signingKeyOverride = bluetoothSigningKey(stage)).forEach {
                (key, value) -> header(key, value)
            }
        }.build()
    } catch (_: Exception) {
        throw ApiException("无法构造蓝牙云端请求，请检查登录状态与车辆信息", stage = stage)
    }

    private fun bluetoothSigningKey(stage: String): ByteArray {
        val raw = session.newAuth?.signKeyBase64 ?: throw ApiException("缺少蓝牙云端签名密钥", stage = stage)
        return try {
            java.util.Base64.getDecoder().decode(raw.filterNot { it == ' ' || it in '\t'..'\r' })
        } catch (_: IllegalArgumentException) {
            throw ApiException("蓝牙云端签名密钥格式无效", stage = stage)
        }
    }

    private fun requireBluetoothCloudSession(stage: String, needsOldToken: Boolean) {
        val gateway = session.newAuth
        val oldAccountId = session.oldAuth?.accountId
        if (!oldAccountId.isNullOrBlank() && gateway != null && gateway.accountId.isNotBlank() &&
            oldAccountId != gateway.accountId
        ) throw ApiException("蓝牙登录身份不一致，请重新登录后重试", stage = stage)
        if (session.deviceId.isBlank() || session.selectedVin.isBlank() || session.appVersion.isBlank() ||
            gateway == null || gateway.accountId.isBlank() || gateway.accessToken.isBlank() || gateway.signKeyBase64.isBlank() ||
            (needsOldToken && (session.oldAuth?.accountId.isNullOrBlank() || session.oldAuth?.token.isNullOrBlank()))
        ) throw ApiException("请先登录并选择车辆后管理蓝牙钥匙", stage = stage)
    }

    private fun prepareBluetoothCloudRequest(
        stage: String,
        needsOldToken: Boolean,
        refreshSession: () -> Unit
    ): BleCloudRequestScope {
        requireBluetoothCloudSession(stage, needsOldToken)
        val scope = BleCloudRequestScope.capture(session)
        try {
            withBluetoothPreflightTransport(refreshSession)
            requireBluetoothCloudSession(stage, needsOldToken)
        } catch (_: Exception) {
            requireBluetoothCloudScope(scope, stage)
            throw ApiException("蓝牙云端登录状态更新失败，请稍后重试", stage = stage)
        }
        requireBluetoothCloudScope(scope, stage)
        return scope
    }

    private fun <T> withBluetoothPreflightTransport(block: () -> T): T {
        val previous = bluetoothPreflightHttpClient.get()
        bluetoothPreflightHttpClient.set(bluetoothCertificateHttpClient)
        return try {
            block()
        } finally {
            if (previous == null) bluetoothPreflightHttpClient.remove() else bluetoothPreflightHttpClient.set(previous)
        }
    }

    private fun refreshBluetoothCloudTokens(needsOldToken: Boolean) {
        if (needsOldToken) ensureFreshOldToken()
        ensureFreshAccessToken()
    }

    private fun requireBluetoothCloudScope(scope: BleCloudRequestScope, stage: String) {
        if (!scope.matches(session)) throw ApiException("蓝牙身份信息已变化，请重新同步钥匙后重试", stage = stage)
    }

    private fun executeBluetoothCloudRequest(request: Request): VehicleListRawResponse =
        bluetoothCertificateHttpClient.newCall(request).execute().use { response ->
            VehicleListRawResponse(response.code, response.body?.string().orEmpty())
        }

    private fun bluetoothCloudResponse(
        request: Request,
        stage: String,
        scope: BleCloudRequestScope,
        execute: (Request) -> VehicleListRawResponse
    ): JSONObject {
        requireBluetoothCloudScope(scope, stage)
        val response = try { execute(request) } catch (_: Exception) {
            throw ApiException("蓝牙云端请求连接失败，请稍后重试", stage = stage)
        } finally {
            requireBluetoothCloudScope(scope, stage)
        }
        if (!response.isHttpSuccessful) throw ApiException(
            "蓝牙云端请求失败（HTTP ${response.statusCode}）", httpStatus = response.statusCode, stage = stage
        )
        return try {
            JSONObject(response.rawBody).also(BleCloudApiModels::requireSuccess)
        } catch (_: Exception) {
            throw ApiException("蓝牙云端未确认请求成功，请稍后重试", stage = stage)
        } finally {
            requireBluetoothCloudScope(scope, stage)
        }
    }

    fun getCarRoute(): RouteData {
        requireVin()
        val params = mapOf("vin" to session.selectedVin)
        val resp = gatewayFetch(
            "$GLOBAL_HOST/app/app-global-service/v1/vehicle/getCarRoute", "GET", params, params
        )
        val route = resp.optJSONObject("data")?.let { routeFrom(it) }
            ?: findObject(resp) { it.has("appRegion") || it.has("appCenter") }?.let { routeFrom(it) }
            ?: throw ApiException("车辆路由响应缺少 appRegion")
        session.route = route
        return route
    }

    private fun ensureRoute(): RouteData {
        requireVin()
        session.route?.takeIf { it.appRegion.isNotBlank() }?.let { return it }
        return getCarRoute()
    }

    /** 车况查询：返回服务器原始响应（调试用），signalMap 用 [extractSignalMap] 提取。 */
    fun getVehicleStateRaw(): JSONObject {
        val route = ensureRoute()
        val body = JSONObject().apply { put("vin", session.selectedVin) }
        return gatewayFetch(
            "${route.appRegion}/app/app-signal-service/signal/info/query", "POST",
            params = mapOf("vin" to session.selectedVin), jsonBody = body
        )
    }

    /**
     * 车型信号调试：只读返回服务器原始 signalMap，保留数字信号键，不做字段解码。
     * 调用方不得持久化、上传或记录完整结果。
     */
    fun getVehicleSignalMap(): JSONObject {
        val signalMap = extractSignalMap(getVehicleStateRaw())
        if (signalMap.length() == 0) throw ApiException("车况接口未返回 signalMap")
        return signalMap
    }

    /** 车况查询：解码为命名字段的 signalMap（C16 数字信号协议）。 */
    fun getVehicleState(): JSONObject {
        val raw = getVehicleStateRaw()
        val signalMap = extractSignalMap(raw)
        if (signalMap.length() == 0) {
            throw ApiException("车况接口未返回 signalMap:\n${raw.toString().take(800)}")
        }
        val decoded = SignalTable.decode(signalMap)
        // 合并车端/网关可能下发的 config.3 充电计划
        val config = raw.optJSONObject("data")?.optJSONObject("config") ?: raw.optJSONObject("config")
        val chargePlan = config?.optJSONObject("3")
        if (chargePlan != null) {
            if (!decoded.has("chargeScheduleEnabled") && chargePlan.has("isEnable")) {
                decoded.put("chargeScheduleEnabled", chargePlan.optInt("isEnable"))
            }
            if (!decoded.has("chargeScheduleStart") && chargePlan.has("beginTime")) {
                decoded.put("chargeScheduleStart", chargePlan.optString("beginTime"))
            }
            if (!decoded.has("chargeScheduleEnd") && chargePlan.has("endTime")) {
                decoded.put("chargeScheduleEnd", chargePlan.optString("endTime"))
            }
            if (!decoded.has("chargeScheduleCycles") && chargePlan.has("cycles")) {
                decoded.put("chargeScheduleCycles", chargePlan.optString("cycles"))
            }
            if (!decoded.has("chargeScheduleCirculation") && chargePlan.has("circulation")) {
                decoded.put("chargeScheduleCirculation", chargePlan.optInt("circulation"))
            }
            if (!decoded.has("chargeScheduleRecharge") && chargePlan.has("recharge")) {
                decoded.put("chargeScheduleRecharge", chargePlan.optInt("recharge"))
            }
            if (!decoded.has("chargesocSetting") && chargePlan.has("percent")) {
                decoded.put("chargesocSetting", chargePlan.optInt("percent"))
            }
        }
        return decoded
    }

    /** 获取驻车实景环视照片信息（调用 GET /carownerservice/v3/api/chassis/query）。 */
    fun getChassisParkingPhoto(): ChassisParkingPhoto? {
        requireVin()
        val signed = oldSignedParams(
            params = mapOf("vin" to session.selectedVin),
            includeTimespan = true
        )
        val headers = combinedAppAndGatewayHeaders(signed, needLogin = true)
        val candidates = listOf(
            "https://iov-api.leapmotor.com/carownerservice/v3/api/chassis/query",
            "https://appgateway.leapmotor.com/carownerservice/v3/api/chassis/query",
            "${ensureRoute().appRegion}/carownerservice/v3/api/chassis/query"
        ).distinct()

        for (url in candidates) {
            try {
                val resp = http(url, method = "GET", headers = headers, query = signed)
                val data = resp.optJSONObject("data") ?: continue
                val rawUrl = data.optString("fileUrl", "").takeIf { it.isNotBlank() } ?: continue
                val uploadTime = data.optLong("uploadTime", 0L)
                return ChassisParkingPhoto(fileUrl = rawUrl, uploadTimeMs = uploadTime)
            } catch (_: Exception) {}
        }
        return null
    }

    /** 下载驻车照片并解码为 Bitmap。 */
    fun downloadParkingPhotoBitmap(secureUrl: String): android.graphics.Bitmap? {
        if (secureUrl.isBlank()) return null
        return try {
            val req = Request.Builder().url(secureUrl).get().build()
            NetworkDebugController.httpClient().newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val stream = response.body?.byteStream()
                    if (stream != null) android.graphics.BitmapFactory.decodeStream(stream) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取车辆 OTA 固件升级信息（调用 GET /carownerservice/v3/api/fota/getCurrentVersion）。
     */
    fun getVehicleOtaInfo(vin: String): VehicleOtaInfo? {
        val cleanVin = vin.ifBlank { session.selectedVin }
        if (cleanVin.isBlank()) return null
        val routeHost = session.route?.appRegion?.takeIf { it.isNotBlank() } ?: runCatching { ensureRoute().appRegion }.getOrNull() ?: "https://appgateway.leapmotor.com"
        val candidates = listOf(
            "https://appgateway.leapmotor.com/carownerservice/v3/api/fota/getCurrentVersion",
            "$routeHost/carownerservice/v3/api/fota/getCurrentVersion",
            "https://app-gw-global-master.leapmotor.com/carownerservice/v3/api/fota/getCurrentVersion"
        ).distinct()

        val signedQuery = oldSignedParams(params = mapOf("vin" to cleanVin), includeTimespan = true)
        val headers = combinedAppAndGatewayHeaders(params = signedQuery, needLogin = true)

        var lastError: String? = null
        for (url in candidates) {
            // 1. 官方实机抓包首选通道：GET 请求 + signedQuery 签名参数 + 包含 signedQuery 参数的完整网关鉴权签名
            try {
                val resp = http(url, method = "GET", headers = headers, query = signedQuery)
                android.util.Log.i("LeapOtaDebug", "getVehicleOtaInfo GET url=$url, resp=$resp")
                val code = resp.optInt("code", resp.optInt("result", -1))
                val isSuccess = code == 0 || resp.optString("message") == "请求成功"
                if (isSuccess) {
                    val data = resp.optJSONObject("data")
                    return VehicleOtaInfo.fromJson(data, code.toString(), resp.optString("message"))
                } else {
                    lastError = resp.optString("message").ifBlank { resp.optString("msg", "查询失败($code)") }
                }
            } catch (e: Exception) {
                android.util.Log.w("LeapOtaDebug", "GET failed at $url: ${e.message}")
                lastError = e.message
            }

            // 2. 备选尝试网关 JSON POST
            try {
                val params = mapOf("vin" to cleanVin, "carvin" to cleanVin)
                val resp = gatewayFetch(url, method = "POST", params = params, jsonBody = JSONObject(params))
                android.util.Log.i("LeapOtaDebug", "getVehicleOtaInfo POST JSON url=$url, resp=$resp")
                val code = resp.optInt("code", resp.optInt("result", -1))
                val isSuccess = code == 0 || resp.optString("message") == "请求成功"
                if (isSuccess) {
                    val data = resp.optJSONObject("data")
                    return VehicleOtaInfo.fromJson(data, code.toString(), resp.optString("message"))
                } else {
                    lastError = resp.optString("message").ifBlank { resp.optString("msg", "查询失败($code)") }
                }
            } catch (e: Exception) {
                android.util.Log.w("LeapOtaDebug", "POST JSON failed at $url: ${e.message}")
                if (lastError == null) lastError = e.message
            }
        }

        if (lastError != null) {
            throw ApiException(lastError)
        }
        return null
    }

    /**
     * 重置 FOTA 状态（调用 POST /carownerservice/v3/api/fota/resetStatus）。
     */
    fun fotaResetStatus(vin: String): Boolean {
        val cleanVin = vin.ifBlank { session.selectedVin }
        if (cleanVin.isBlank()) return false
        val signed = oldSignedParams(
            params = mapOf("vin" to cleanVin),
            includeTimespan = true
        )
        val headers = combinedAppAndGatewayHeaders(signed, needLogin = true)
        val routeHost = session.route?.appRegion?.takeIf { it.isNotBlank() } ?: runCatching { ensureRoute().appRegion }.getOrNull() ?: "https://appgateway.leapmotor.com"
        val url = "$routeHost/carownerservice/v3/api/fota/resetStatus"
        return try {
            val resp = http(url, method = "POST", headers = headers, query = signed, formBody = signed)
            resp.optString("code") == "0" || resp.optInt("code", -1) == 0
        } catch (_: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- 控车

    /** 获取用于加密操作密码的 Token：优先网关 JWT accessToken，回退旧 token。 */
    fun getOperationPasswordToken(): String {
        val jwtToken = session.newAuth?.accessToken?.takeIf { it.isNotBlank() }
        if (!jwtToken.isNullOrBlank()) return jwtToken
        val oldToken = session.oldAuth?.token?.takeIf { it.isNotBlank() }
        if (!oldToken.isNullOrBlank()) return oldToken
        throw ApiException("未登录（缺少用于加密操作密码的 Token）")
    }

    /** 校验操作密码（对齐官方 verifyoperatepwdnew 接口）。校验成功返回 true，密码错误抛出 ApiException。 */
    fun verifyOperatePassword(opPassword: String): Boolean {
        require(opPassword.isNotBlank()) { "操作密码不能为空" }
        requireVin()
        val encToken = getOperationPasswordToken()
        val params = LinkedHashMap<String, String>()
        params["oprpwd"] = Crypto.encryptOperationPassword(opPassword, encToken)
        params["vin"] = session.selectedVin
        val signedBody = oldSignedParams(params)
        val combinedHeaders = oldAppHeaders(true).toMutableMap().apply {
            if (session.newAuth != null) {
                putAll(newGatewayHeaders(signedBody, needLogin = true))
            }
        }
        val route = ensureRoute()
        val candidates = listOf(
            "https://appgateway.leapmotor.com/carownerservice/v3/api/appoperate/verifyoperatepwdnew",
            "${route.appRegion}/carownerservice/v3/api/appoperate/verifyoperatepwdnew"
        ).distinct()
        for (url in candidates) {
            try {
                val resp = http(url, method = "POST", headers = combinedHeaders, formBody = signedBody)
                val code = resp.optString("code", resp.optString("result", ""))
                if (code == "0" || code == "200") {
                    return true
                }
                val msg = resp.optString("msg").ifEmpty { resp.optString("message", "操作密码错误") }
                throw ApiException("操作密码校验失败($code): $msg")
            } catch (e: Exception) {
                if (e is ApiException && OperationPasswordErrorPolicy.isPasswordError(e)) {
                    throw e
                }
            }
        }
        return false
    }

    /** 下发控车命令，返回 msgID 与原始响应（全部命令都需要操作密码）。对齐官方 api.log 链路 */
    fun sendControl(command: ControlCommand, opPassword: String): ControlResult {
        require(opPassword.isNotBlank()) { "操作密码不能为空" }
        requireVin()
        val route = ensureRoute()
        val encToken = getOperationPasswordToken()
        val params = LinkedHashMap<String, String>()
        params["cmdid"] = command.cmdid
        params["state"] = command.stateJson
        params["carvin"] = session.selectedVin
        params["oppwd"] = Crypto.encryptOperationPassword(opPassword, encToken)
        val signedBody = oldSignedParams(params)
        val combinedHeaders = oldAppHeaders(true).toMutableMap().apply {
            if (session.newAuth != null) {
                putAll(newGatewayHeaders(signedBody, needLogin = true))
            }
        }
        val candidates = listOf(
            "https://appgateway.leapmotor.com/app/app-control-service/v3/api/appremotectl",
            "${route.appRegion}/app/app-control-service/v3/api/appremotectl"
        ).distinct()

        var lastException: Exception? = null
        for (url in candidates) {
            try {
                val resp = http(url, method = "POST", headers = combinedHeaders, formBody = signedBody)
                checkControlResult(resp)
                return ControlResult(msgID = extractMsgID(resp), raw = resp)
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw (lastException ?: ApiException("控车请求发送失败"))
    }

    fun queryControlResult(msgID: String): JSONObject {
        requireVin()
        val route = ensureRoute()
        val params = mapOf("msgID" to msgID)
        val signedQuery = oldSignedParams(params)
        val combinedHeaders = oldAppHeaders(true).toMutableMap().apply {
            if (session.newAuth != null) {
                putAll(newGatewayHeaders(signedQuery, needLogin = true))
            }
        }
        val candidates = listOf(
            "https://appgateway.leapmotor.com/app/app-control-service/v3/api/appremotectl/query",
            "${route.appRegion}/app/app-control-service/v3/api/appremotectl/query"
        ).distinct()

        var lastException: Exception? = null
        for (url in candidates) {
            try {
                val resp = http(url, headers = combinedHeaders, query = signedQuery)
                return resp
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw (lastException ?: ApiException("控车结果查询失败"))
    }

    /** 电池健康充电控制（慢充目标电量限额与养护开关）。 */
    fun setHealthyCharging(enabled: Boolean, targetSoc: Int): JSONObject {
        requireVin()
        val route = ensureRoute()
        val url = "${route.appRegion}/carownerservice/v3/api/healthyCharging/control"
        val stateInt = if (enabled) 1 else 0
        val socInt = targetSoc.coerceIn(50, 100)
        val params = mapOf(
            "carvin" to session.selectedVin,
            "vin" to session.selectedVin,
            "state" to stateInt.toString(),
            "soc" to socInt.toString(),
            "targetSoc" to socInt.toString()
        )
        return gatewayFetch(url, method = "POST", params = params, query = params, jsonBody = JSONObject(params))
    }

    /** 查询健康充电推送与策略状态。 */
    fun queryHealthyChargingPushState(): JSONObject {
        requireVin()
        val route = ensureRoute()
        val url = "${route.appRegion}/carownerservice/v3/api/healthyCharging/queryPushState"
        val params = mapOf(
            "carvin" to session.selectedVin,
            "vin" to session.selectedVin
        )
        return gatewayFetch(url, method = "POST", params = params, query = params, jsonBody = JSONObject(params))
    }

    /**
     * 谷电预约充电控制（插枪后在设定起止时间段内执行充电）。
     * 默认 23:00 开始至次日 07:00 结束。
     */
    /**
     * 充电计划 / 谷电预约充电控制（cmdid=190，RemoteActionCtlChargePlan）。
     * 字段与零跑车联网底层完全对齐：
     * chargeEnable: 是否启用（0/1）
     * chargesoc: 目标限额百分比
     * circulation: 循环模式（1=重复，0=单次）
     * cycles: 星期几重复（"1,2,3,4,5,6,7"）
     * starttime: 开始时间（"HH:mm"）
     * endtime: 结束时间（"HH:mm"）
     * recharge: 未达上限继续充电 / 自动再充（0/1）
     */
    fun setScheduledCharging(
        enabled: Boolean,
        startTime: String,
        endTime: String,
        targetSoc: Int = 80,
        opPassword: String = "",
        continueUntilLimit: Boolean = true,
        circulation: Int = 1,
        cycles: String = "1,2,3,4,5,6,7"
    ): JSONObject {
        requireVin()
        try {
            ensureFreshOldToken()
        } catch (_: Exception) {}
        val route = ensureRoute()
        val old = session.oldAuth ?: throw ApiException("未登录（缺少旧凭证）")

        val chargeEnableInt = if (enabled) 1 else 0
        val rechargeInt = if (continueUntilLimit) 1 else 0
        val vehicleCyclesMask = if (enabled && circulation == 1) {
            ChargePlanCyclesHelper.toVehicleMask(cycles)
        } else {
            ""
        }
        val stateJson = JSONObject().apply {
            put("chargeEnable", chargeEnableInt)
            put("chargesoc", targetSoc.coerceIn(50, 100))
            put("circulation", if (enabled) circulation else 0)
            put("cycles", vehicleCyclesMask)
            put("starttime", startTime)
            put("endtime", endTime)
            put("recharge", rechargeInt)
        }

        val params = LinkedHashMap<String, String>()
        params["cmdid"] = "190"
        params["state"] = stateJson.toString()
        params["carvin"] = session.selectedVin
        if (opPassword.isNotBlank()) {
            params["oppwd"] = Crypto.encryptOperationPassword(opPassword, getOperationPasswordToken())
        }

        // 1. 优先通道：走车控核心通道 (/app/app-control-service/v3/api/appremotectl) 带旧会话Token与加密码
        val signedBody = oldSignedParams(params)
        val headers = combinedAppAndGatewayHeaders(signedBody, needLogin = true)
        val controlUrl = "${route.appRegion}/app/app-control-service/v3/api/appremotectl"
        val respControl = try {
            http(controlUrl, method = "POST", headers = headers, formBody = signedBody)
        } catch (e: Exception) {
            null
        }
        val codeCtrl = respControl?.optInt("code", respControl.optInt("result", -1)) ?: -1
        if (codeCtrl == 0 || codeCtrl == 200) {
            return respControl!!
        }

        // 2. 备选通道：走预约通道 (/carownerservice/v3/api/appremotectl/appointment)
        val host = if (route.appCenter.isNotBlank()) route.appCenter else route.appRegion
        val apptUrl = "$host/carownerservice/v3/api/appremotectl/appointment"
        val respAppt = try {
            http(apptUrl, method = "POST", headers = headers, formBody = signedBody)
        } catch (e: Exception) {
            null
        }
        val codeAppt = respAppt?.optInt("code", respAppt.optInt("result", -1)) ?: -1
        if (codeAppt == 0 || codeAppt == 200) {
            return respAppt!!
        }

        // 3. 独立日程服务同步兜底 (/schedule/operate)
        val model = session.selectedCarType.ifBlank { "C16" }
        val scheduleUrl = "${route.appRegion}/carownerservice/v3/api/schedule/operate"
        val schedParams = linkedMapOf(
            "carvin" to session.selectedVin,
            "vin" to session.selectedVin,
            "model" to model,
            "type" to "1",
            "state" to chargeEnableInt.toString(),
            "status" to chargeEnableInt.toString(),
            "startTime" to startTime,
            "endTime" to endTime,
            "repeat" to "1,2,3,4,5,6,7",
            "cycle" to "1,2,3,4,5,6,7",
            "continueCharge" to rechargeInt.toString(),
            "continueUntilFull" to rechargeInt.toString()
        )
        return gatewayFetch(scheduleUrl, method = "POST", params = schedParams, formBody = schedParams)
    }

    /**
     * 预约电池预热控制（cmdid=161，PTC Battery Heating Schedule）。
     * controls 数组包装：on, set_id, start_time, update_time, days。
     */
    fun setScheduledBatteryPreheat(
        enabled: Boolean,
        startTime: String,
        opPassword: String = "",
        days: String = "1,1,1,1,1,1,1"
    ): JSONObject {
        requireVin()
        try {
            ensureFreshOldToken()
        } catch (_: Exception) {}
        val route = ensureRoute()
        val old = session.oldAuth ?: throw ApiException("未登录（缺少旧凭证）")

        val daySet = ChargePlanCyclesHelper.parseToDaySet(days)
        val daysArray = JSONArray().apply {
            ChargePlanCyclesHelper.toScheduleDaysIntList(daySet).forEach { put(it) }
        }

        val todayStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val fullStartTime = if (startTime.length == 5) "$todayStr $startTime:00" else startTime

        val stateJson = JSONObject().apply {
            if (enabled) {
                val item = JSONObject().apply {
                    put("on", "1")
                    put("set_id", "ptc_set_${System.currentTimeMillis()}")
                    put("start_time", fullStartTime)
                    put("update_time", System.currentTimeMillis().toString())
                    put("days", daysArray)
                }
                put("controls", JSONArray().put(item))
            } else {
                put("controls", JSONArray())
            }
        }

        val params = LinkedHashMap<String, String>()
        params["cmdid"] = "161"
        params["state"] = stateJson.toString()
        params["carvin"] = session.selectedVin
        if (opPassword.isNotBlank()) {
            params["oppwd"] = Crypto.encryptOperationPassword(opPassword, getOperationPasswordToken())
        }

        // 直接走车控核心通道 (/app/app-control-service/v3/api/appremotectl)
        val controlUrl = "${route.appRegion}/app/app-control-service/v3/api/appremotectl"
        val signedBody = oldSignedParams(params)
        val headers = combinedAppAndGatewayHeaders(signedBody, needLogin = true)
        return http(controlUrl, method = "POST", headers = headers, formBody = signedBody)
    }

    // ---------------------------------------------------------------- 内部

    private fun gatewayFetch(
        url: String,
        method: String,
        params: Map<String, String?> = emptyMap(),
        query: Map<String, String?>? = null,
        jsonBody: JSONObject? = null,
        formBody: Map<String, String>? = null
    ): JSONObject {
        ensureFreshAccessToken()
        val q = query ?: if (method == "GET") params else null
        val attempt = { http(url, method, newGatewayHeaders(params, true), query = q, jsonBody = jsonBody, formBody = formBody) }
        var resp = attempt()
        if (isAuthFailure(resp)) {
            refreshAccessTokenWithFallback()
            resp = attempt()
        }
        return resp
    }

    private fun gatewayFetchRaw(url: String, method: String): VehicleListRawResponse {
        ensureFreshAccessToken()
        val attempt = { httpRaw(url, method, newGatewayHeaders(emptyMap(), true)) }
        var response = attempt()
        val parsed = runCatching { JSONObject(response.rawBody) }.getOrNull()
        if (response.statusCode == 401 || response.statusCode == 403 ||
            (parsed != null && isAuthFailure(parsed))
        ) {
            try {
                refreshAccessTokenWithFallback()
            } catch (_: Exception) {
                return response.copy(authenticationFailed = true)
            }
            response = preserveAuthenticationFailureOnRetry(response, attempt)
            val retried = runCatching { JSONObject(response.rawBody) }.getOrNull()
            if (response.statusCode == 401 || response.statusCode == 403 ||
                (retried != null && isAuthFailure(retried))
            ) {
                return response.copy(authenticationFailed = true)
            }
        }
        return response
    }

    private fun ensureFreshAccessToken() {
        val auth = session.newAuth ?: throw ApiException("未登录")
        if (auth.accessTokenExpiresAt > 0 &&
            auth.accessTokenExpiresAt - System.currentTimeMillis() <= ACCESS_REFRESH_LEEWAY_MS
        ) {
            refreshAccessTokenWithFallback()
        }
    }

    /** 网关 refresh token 可能已在旧版本中轮换，用旧链路续期后重新交换网关会话。 */
    private fun refreshAccessTokenWithFallback() {
        val previousAccessToken = session.newAuth?.accessToken
        try {
            refreshGatewayToken()
            return
        } catch (_: Exception) {
            // Fall through to the still-supported old-token refresh path.
        }
        refreshOldToken()
        if (session.newAuth?.accessToken == previousAccessToken) {
            exchangeNewGateway()
        }
    }

    private fun ensureFreshOldToken() {
        val old = session.oldAuth ?: throw ApiException("未登录")
        val expiresAt = old.expiresAt()
        if (expiresAt > 0 && expiresAt - System.currentTimeMillis() <= OLD_REFRESH_LEEWAY_MS) {
            refreshOldToken()
        }
    }

    private fun requireVin() {
        if (session.selectedVin.isBlank()) throw ApiException("还没有选择车辆 VIN")
    }

    /** 从控车响应中提取 msgID：可能在嵌套对象里，也可能是 data 直接是字符串。 */
    private fun extractMsgID(resp: JSONObject): String {
        findObjectStatic(resp) { it.has("msgID") || it.has("msgId") || it.has("eventId") }?.let { obj ->
            return obj.optString("msgID", "")
                .ifBlank { obj.optString("msgId", "") }
                .ifBlank { obj.optString("eventId", "") }
        }
        val data = resp.opt("data")
        if (data != null && data != JSONObject.NULL && data !is JSONObject && data !is JSONArray) {
            return data.toString()
        }
        return ""
    }

    private fun checkResult(resp: JSONObject, action: String) {
        val result = resp.opt("result")
        val code = resp.opt("code")
        val value = when {
            result != null && result != JSONObject.NULL -> result.toString()
            code != null && code != JSONObject.NULL -> code.toString()
            else -> return
        }
        if (value != "0" && value != "200") {
            throw ApiException(
                "$action 失败($value): ${resp.optString("msg").ifEmpty { resp.optString("message") }}"
            )
        }
    }

    private fun checkControlResult(resp: JSONObject) {
        val result = resp.opt("result")
        val code = resp.opt("code")
        val value = when {
            result != null && result != JSONObject.NULL -> result.toString()
            code != null && code != JSONObject.NULL -> code.toString()
            else -> return
        }
        if (value != "0" && value != "200") {
            throw ApiException(
                "控车失败($value): ${resp.optString("msg").ifEmpty { resp.optString("message") }}"
            )
        }
    }

    private fun isOldAuthFailure(resp: JSONObject): Boolean {
        val result = resp.opt("result")
        if (result != null && result.toString() == "39") return true
        val message = resp.optString("msg").ifEmpty { resp.optString("message") }
        return Regex("信息校验失败|第三方TOKEN失效|token.*失效|鉴权失败", RegexOption.IGNORE_CASE)
            .containsMatchIn(message)
    }

    private fun isAuthFailure(resp: JSONObject): Boolean {
        val code = resp.optString("code", "").ifEmpty {
            resp.optString("result", "").ifEmpty { resp.optString("status", "") }
        }
        if (code == "401" || code == "403") return true
        val message = (resp.optString("msg") + resp.optString("message") + resp.optString("error")).lowercase()
        return Regex("token|登录|鉴权|认证").containsMatchIn(message) &&
            Regex("expire|expired|invalid|过期|失效|无效|重新登录").containsMatchIn(message)
    }

    private fun routeFrom(o: JSONObject) = RouteData(
        appRegion = o.optString("appRegion"),
        appCenter = o.optString("appCenter")
    )

    private fun findObject(root: Any?, predicate: (JSONObject) -> Boolean): JSONObject? =
        findObjectStatic(root, predicate)

    private fun collectObjects(root: Any?, predicate: (JSONObject) -> Boolean): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        fun walk(node: Any?) {
            if (node == null) return
            if (node is JSONObject) {
                if (predicate(node)) out.add(node)
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()))
            } else if (node is JSONArray) {
                for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        return out
    }
}
