package com.leapauto.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(message: String, val httpStatus: Int? = null, val durationMs: Long? = null, val retryCount: Int = 0, val stage: String = "api") : Exception(message)

/**
 * 零跑中国 App 云接口客户端（阻塞式，请在后台线程调用）。
 * 协议移植自 leap-cn-mcp 的 leapmotor-cn-sdk.js。
 */
class LeapmotorApi(private val session: Session) {

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
        return NetworkDebugController.httpClient().newCall(request).execute().use { response ->
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
        needLogin: Boolean = true
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
            headers["sign"] = Crypto.hmacSha256Hex(auth.signKey(), signBase)
        } else {
            headers["sign"] = Crypto.sha256Hex(signBase)
        }
        return headers
    }

    // ------------------------------------------------------------------ 登录

    fun sendSms(phone: String) {
        check(phone.isNotBlank()) { "手机号不能为空" }
        val resp = http(
            "$APP_USER_HOST/app-user/applogin/compliance/sendmessagecode",
            headers = oldAppHeaders(withToken = false),
            query = mapOf("phoneNo" to Crypto.rsaEncryptPhone(phone))
        )
        checkResult(resp, "发送验证码")
    }

    /** 短信验证码登录：旧 token + 换新网关 accessToken/signKey。 */
    fun loginWithSms(phone: String, smsCode: String) {
        check(phone.isNotBlank() && smsCode.isNotBlank()) { "手机号和验证码不能为空" }
        val params = LinkedHashMap<String, String?>()
        params["phoneNoCiphertext"] = Crypto.rsaEncryptPhone(phone)
        params["smsCode"] = smsCode
        params["deviceID"] = session.deviceId
        params["smDeviceId"] = session.deviceId
        params["os"] = "android"
        params["pageUrl"] = ""
        val resp = http(
            "$APP_USER_HOST/app-user/applogin/check_login_with_phone",
            method = "POST",
            headers = oldAppHeaders(withToken = false),
            query = params,
            queryPost = true
        )
        val data = resp.optJSONObject("data")
        val authObj = data?.optJSONObject("appLoginVO") ?: data?.optJSONObject("appOneLoginVO")
            ?: findObject(resp) { it.has("accountId") && it.has("token") }
            ?: throw ApiException(
                "登录失败: ${resp.optString("msg").ifEmpty { resp.optString("message") }} " +
                    (data?.optString("risk_type")?.let { "(risk_type=$it)" } ?: "")
            )
        val tokenExpired = authObj.optString("tokenExpired", "")
        session.phone = phone.trim()
        session.oldAuth = OldAuth(
            accountId = authObj.optString("accountId"),
            token = authObj.optString("token"),
            refreshToken = authObj.optString("refreshToken", ""),
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
                val carType = it.optString("carType").ifEmpty { it.optString("cartype") }
                val rightList = it.optString("rightList")
                val isReev = carType.contains("REEV", ignoreCase = true) ||
                    carType.contains("增程") ||
                    rightList.split(",").map { c -> c.trim() }.contains("380")
                val inferredPowerType = if (isReev) {
                    SessionStore.VehiclePowerType.RANGE_EXTENDER
                } else {
                    SessionStore.VehiclePowerType.PURE_ELECTRIC
                }
                Vehicle(
                    vin = it.optString("vin"),
                    carType = carType,
                    hvacCapability = HvacCapabilityParser.parse(it.optJSONObject("funcConfig")),
                    nickname = it.optString("nickName").ifEmpty { it.optString("nickname") },
                    year = it.optString("year"),
                    color = it.optString("outColor").ifEmpty { it.optString("color") },
                    powerType = inferredPowerType
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
                if (selected.nickname.isNotBlank()) {
                    session.selectedNickname = selected.nickname
                }
                if (selected.year.isNotBlank()) {
                    session.selectedYear = selected.year
                }
            }
            rawObjects.firstOrNull { it.optString("vin") == session.selectedVin }?.let { rawJson ->
                ErrorLogs.repository.record(
                    ErrorLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        category = ErrorLogCategory.API_FAILURE, // 借用ErrorLogs展示在诊断日志中
                        stage = "vehicle_list_info",
                        appVersion = BuildConfig.VERSION_NAME,
                        message = "云端车辆档案数据:\n${rawJson.toString().take(600)}"
                    )
                )
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
                val resp = http(
                    url = url,
                    method = "GET",
                    headers = oldAppHeaders(true),
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
                val resp = http(
                    url = url,
                    method = "POST",
                    headers = oldAppHeaders(true),
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
            val range = DrivingRecordTimeRange.purchaseToToday(purchaseAtMs, nowMs)
            val signed = oldSignedParams(
                params = mileageEnergyDetailBusinessParameters(session.selectedVin, range),
                includeTimespan = false
            )
            for (host in candidates()) {
                try {
                    return http(
                        "$host$MILEAGE_ENERGY_DETAIL_PATH",
                        headers = oldAppHeaders(true),
                        query = signed
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
            val range = DrivingRecordTimeRange.recentMileageRange(nowMs)
            val signed = oldSignedParams(
                params = recentMileageEnergyDetailBusinessParameters(session.selectedVin, range)
            )
            return http(
                "${DRIVING_RECORD_HOST}${MILEAGE_ENERGY_DETAIL_PATH}",
                headers = oldAppHeaders(true),
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
            for (host in candidates()) {
                val response = httpRaw(
                    "$host$DRIVING_RECORD_DEBUG_PREFIX$suffix",
                    // The verified mileage/energy detail contract is GET with
                    // signed query parameters. Custom debug paths may still
                    // opt into POST by supplying a JSON request body.
                    method = if (bodyJson != null && !fixedGetEndpoint) "POST" else "GET",
                    headers = oldAppHeaders(true),
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
        var response = httpRaw(
            "$DRIVING_RECORD_HOST$LAST_WEEK_EC_PATH",
            method = "GET",
            headers = oldAppHeaders(true),
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
        return SignalTable.decode(signalMap)
    }

    // ---------------------------------------------------------------- 控车

    /** 下发控车命令，返回 msgID 与原始响应（全部命令都需要操作密码）。 */
    fun sendControl(command: ControlCommand, opPassword: String): ControlResult {
        require(opPassword.isNotBlank()) { "操作密码不能为空" }
        requireVin()
        try {
            ensureFreshOldToken()
        } catch (_: Exception) {
            // 可能仍可用，继续尝试
        }
        val route = ensureRoute()
        val url = "${route.appRegion}/app/app-control-service/v3/api/appremotectl"
        val attempt = {
            val old = session.oldAuth ?: throw ApiException("未登录")
            val params = LinkedHashMap<String, String>()
            params["cmdid"] = command.cmdid
            params["state"] = command.stateJson
            params["carvin"] = session.selectedVin
            params["oppwd"] = Crypto.encryptOperationPassword(opPassword, old.token)
            http(url, method = "POST", headers = oldAppHeaders(true), formBody = oldSignedParams(params))
        }
        var resp = attempt()
        if (isOldAuthFailure(resp)) {
            refreshOldToken()
            resp = attempt()
        }
        checkControlResult(resp)
        return ControlResult(msgID = extractMsgID(resp), raw = resp)
    }

    fun queryControlResult(msgID: String): JSONObject {
        requireVin()
        val route = ensureRoute()
        val url = "${route.appRegion}/app/app-control-service/v3/api/appremotectl/query"
        val attempt = {
            http(url, headers = oldAppHeaders(true), query = oldSignedParams(mapOf("msgID" to msgID)))
        }
        var resp = attempt()
        if (isOldAuthFailure(resp)) {
            refreshOldToken()
            resp = attempt()
        }
        return resp
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
    fun setScheduledCharging(
        enabled: Boolean,
        startTime: String,
        endTime: String,
        opPassword: String = "",
        continueUntilLimit: Boolean = true
    ): JSONObject {
        requireVin()
        val route = ensureRoute()
        val model = session.selectedCarType.ifBlank { "C16" }
        val stateInt = if (enabled) 1 else 0
        val continueInt = if (continueUntilLimit) 1 else 0
        val stateJson = JSONObject().apply {
            put("startTime", startTime)
            put("endTime", endTime)
            put("start", startTime)
            put("end", endTime)
            put("state", stateInt)
            put("status", stateInt)
            put("value", if (enabled) "1" else "0")
            put("repeat", "1,2,3,4,5,6,7")
            put("model", model)
            put("continueCharge", continueInt)
            put("continueUntilFull", continueInt)
        }

        // 1. 优先尝试车控原语通道 (/appremotectl/appointment) 带 oldAppHeaders + oppwd
        val old = session.oldAuth
        if (old != null && opPassword.isNotBlank()) {
            val host = if (route.appCenter.isNotBlank()) route.appCenter else route.appRegion
            val url = "$host/carownerservice/v3/api/appremotectl/appointment"
            val params = LinkedHashMap<String, String>()
            params["cmdid"] = "361"
            params["model"] = model
            params["state"] = stateJson.toString()
            params["carvin"] = session.selectedVin
            params["startTime"] = startTime
            params["endTime"] = endTime
            params["oppwd"] = Crypto.encryptOperationPassword(opPassword, old.token)

            try {
                ensureFreshOldToken()
                val resp = http(url, method = "POST", headers = oldAppHeaders(true), formBody = oldSignedParams(params))
                val code = resp.optInt("code", resp.optInt("result", -1))
                if (code == 0 || code == 200) {
                    return resp
                }
            } catch (_: Exception) {}
        }

        // 2. 新网关 appointment 签名通道
        val url = "${route.appRegion}/carownerservice/v3/api/appremotectl/appointment"
        val params = linkedMapOf(
            "carvin" to session.selectedVin,
            "vin" to session.selectedVin,
            "model" to model,
            "cmdid" to "361",
            "type" to "1",
            "state" to stateJson.toString(),
            "startTime" to startTime,
            "endTime" to endTime
        )
        val oldToken = session.oldAuth?.token
        if (oldToken != null && opPassword.isNotBlank()) {
            params["oppwd"] = Crypto.encryptOperationPassword(opPassword, oldToken)
        }
        return gatewayFetch(url, method = "POST", params = params, formBody = params)
    }

    /**
     * 预约电池预热控制（设定时间自动唤醒加热动力电池）。
     * 默认 23:00 开始预热。
     */
    fun setScheduledBatteryPreheat(
        enabled: Boolean,
        startTime: String,
        opPassword: String = ""
    ): JSONObject {
        requireVin()
        val route = ensureRoute()
        val model = session.selectedCarType.ifBlank { "C16" }
        val stateInt = if (enabled) 1 else 0
        val stateJson = JSONObject().apply {
            put("startTime", startTime)
            put("start", startTime)
            put("state", stateInt)
            put("status", stateInt)
            put("value", if (enabled) "1" else "0")
            put("repeat", "1,2,3,4,5,6,7")
            put("model", model)
        }

        // 1. 优先尝试车控原语通道 (/appremotectl/appointment)
        val old = session.oldAuth
        if (old != null && opPassword.isNotBlank()) {
            val host = if (route.appCenter.isNotBlank()) route.appCenter else route.appRegion
            val url = "$host/carownerservice/v3/api/appremotectl/appointment"
            val params = LinkedHashMap<String, String>()
            params["cmdid"] = "161"
            params["model"] = model
            params["state"] = stateJson.toString()
            params["carvin"] = session.selectedVin
            params["startTime"] = startTime
            params["oppwd"] = Crypto.encryptOperationPassword(opPassword, old.token)

            try {
                ensureFreshOldToken()
                val resp = http(url, method = "POST", headers = oldAppHeaders(true), formBody = oldSignedParams(params))
                val code = resp.optInt("code", resp.optInt("result", -1))
                if (code == 0 || code == 200) {
                    return resp
                }
            } catch (_: Exception) {}
        }

        // 2. 新网关 appointment 签名通道
        val url = "${route.appRegion}/carownerservice/v3/api/appremotectl/appointment"
        val params = linkedMapOf(
            "carvin" to session.selectedVin,
            "vin" to session.selectedVin,
            "model" to model,
            "cmdid" to "161",
            "type" to "3",
            "state" to stateJson.toString(),
            "startTime" to startTime
        )
        val oldToken = session.oldAuth?.token
        if (oldToken != null && opPassword.isNotBlank()) {
            params["oppwd"] = Crypto.encryptOperationPassword(opPassword, oldToken)
        }
        return gatewayFetch(url, method = "POST", params = params, formBody = params)
    }

    /** 查询车辆当前已设置的预约任务。 */
    fun getAppointment(): JSONObject {
        requireVin()
        val route = ensureRoute()
        val model = session.selectedCarType.ifBlank { "C16" }
        val url = "${route.appRegion}/carownerservice/v3/api/appremotectl/getappointment"
        val params = mapOf(
            "carvin" to session.selectedVin,
            "vin" to session.selectedVin,
            "model" to model
        )
        return gatewayFetch(url, method = "POST", params = params, formBody = params)
    }

    /** 查询车辆定时日程列表（/schedule/list），必需包含 type (Integer, 如 1=充电日程)。 */
    fun queryScheduleList(type: Int = 1): JSONObject {
        requireVin()
        val route = ensureRoute()
        val model = session.selectedCarType.ifBlank { "C16" }
        val url = "${route.appRegion}/carownerservice/v3/api/schedule/list"
        val params = mapOf(
            "carvin" to session.selectedVin,
            "vin" to session.selectedVin,
            "model" to model,
            "type" to type.toString()
        )
        return try {
            gatewayFetch(url, method = "POST", params = params, query = params, formBody = params)
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.toString())
        }
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
