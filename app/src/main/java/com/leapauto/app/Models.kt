package com.leapauto.app

import android.util.Base64
import org.json.JSONObject
import java.math.BigDecimal
import kotlin.math.roundToInt

data class OldAuth(
    val accountId: String,
    val token: String,
    val refreshToken: String,
    val tokenExpired: String,
    val tokenObtainedAt: Long
) {
    fun expiresAt(): Long =
        tokenExpired.toLongOrNull()?.takeIf { it > 0 }?.let { tokenObtainedAt + it * 1000 } ?: 0L

    fun toJson(): JSONObject = JSONObject().apply {
        put("accountId", accountId)
        put("token", token)
        put("refreshToken", refreshToken)
        put("tokenExpired", tokenExpired)
        put("tokenObtainedAt", tokenObtainedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = OldAuth(
            accountId = o.optString("accountId"),
            token = o.optString("token"),
            refreshToken = o.optString("refreshToken"),
            tokenExpired = o.optString("tokenExpired"),
            tokenObtainedAt = o.optLong("tokenObtainedAt")
        )
    }
}

/** 极验 GT4 验证挑战模型 */
data class GeetestChallenge(
    val captchaId: String,
    val riskType: String,
    val requestId: String,
    val phone: String,
    val smsCode: String
)

/** 极验 GT4 验证成功凭证 */
data class GeetestCaptchaResult(
    val lotNumber: String,
    val passToken: String,
    val genTime: String,
    val captchaOutput: String,
    val requestId: String
) {
    companion object {
        fun fromValidateJson(jsonStr: String, requestId: String): GeetestCaptchaResult {
            val obj = JSONObject(jsonStr)
            return GeetestCaptchaResult(
                lotNumber = obj.optString("lot_number"),
                passToken = obj.optString("pass_token"),
                genTime = obj.optString("gen_time"),
                captchaOutput = obj.optString("captcha_output"),
                requestId = requestId
            )
        }
    }
}

/** 触发极验安全验证异常，由 UI 层承接弹窗 */
class GeetestChallengeRequiredException(
    val challenge: GeetestChallenge,
    override val message: String = "需完成安全验证"
) : Exception(message)

data class NewAuth(
    val accountId: String,
    val accessToken: String,
    val refreshToken: String,
    val signKeyBase64: String,
    val accessTokenExpiresAt: Long
) {
    fun signKey(): ByteArray = Base64.decode(signKeyBase64, Base64.DEFAULT)

    fun toJson(): JSONObject = JSONObject().apply {
        put("accountId", accountId)
        put("accessToken", accessToken)
        put("refreshToken", refreshToken)
        put("signKeyBase64", signKeyBase64)
        put("accessTokenExpiresAt", accessTokenExpiresAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = NewAuth(
            accountId = o.optString("accountId"),
            accessToken = o.optString("accessToken"),
            refreshToken = o.optString("refreshToken"),
            signKeyBase64 = o.optString("signKeyBase64"),
            accessTokenExpiresAt = o.optLong("accessTokenExpiresAt")
        )
    }
}

data class RouteData(
    val appRegion: String,
    val appCenter: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("appRegion", appRegion)
        put("appCenter", appCenter)
    }

    companion object {
        fun fromJson(o: JSONObject) = RouteData(
            appRegion = o.optString("appRegion"),
            appCenter = o.optString("appCenter")
        )
    }
}

data class Vehicle(
    val vin: String,
    val carType: String,
    val hvacCapability: HvacCapability = HvacCapability.fallback(),
    val nickname: String = "",
    val year: String = "",
    val color: String = "",
    val powerType: SessionStore.VehiclePowerType = SessionStore.VehiclePowerType.PURE_ELECTRIC,
    val isSharedAccount: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("vin", vin)
        put("carType", carType)
        put("hvacCapability", hvacCapability.toJson())
        put("nickname", nickname)
        put("year", year)
        put("color", color)
        put("powerType", powerType.name)
        put("isSharedAccount", isSharedAccount)
    }

    companion object {
        fun fromJson(json: JSONObject): Vehicle = Vehicle(
            vin = json.optString("vin"),
            carType = json.optString("carType"),
            hvacCapability = json.optJSONObject("hvacCapability")?.let { HvacCapability.fromJson(it) }
                ?: HvacCapability.fallback(),
            nickname = json.optString("nickname"),
            year = json.optString("year"),
            color = json.optString("color"),
            powerType = json.optString("powerType").let {
                runCatching { SessionStore.VehiclePowerType.valueOf(it) }
                    .getOrDefault(SessionStore.VehiclePowerType.PURE_ELECTRIC)
            },
            isSharedAccount = json.optBoolean("isSharedAccount", false)
        )
    }
}

data class VehiclePictureMeta(
    val pictureKey: String,
    val shareBindUrl: String,
    val sourceUrl: String = "",
    val rawData: JSONObject? = null
) {
    val h5Key: String?
        get() = rawData?.optString("h5Key")?.takeIf { it.isNotBlank() }

    val srcKey: String?
        get() = rawData?.optString("srcKey")?.takeIf { it.isNotBlank() }

    val modelParam: JSONObject?
        get() = rawData?.optJSONObject("modelParam")
}

/** 登录会话（内存态），由 SessionStore 持久化。 */
class Session(
    var deviceId: String,
    var phone: String = "",
    var smDeviceId: String = "",
    var oldAuth: OldAuth? = null,
    var newAuth: NewAuth? = null,
    var selectedVin: String = "",
    var selectedCarType: String = "",
    var selectedNickname: String = "",
    var selectedYear: String = "",
    var route: RouteData? = null,
    var hvacCapability: HvacCapability = HvacCapability.fallback(),
    val generation: Long = 0L
) {
    val appVersion = "1.22.96"
    val subVersion = "3.21.3-2"
}

/** 控车命令预设（与 leap-cn-mcp commandPresets 一致）。 */
data class ControlCommand(
    val cmdid: String,
    val stateJson: String,
    val label: String
)

enum class HvacOperation {
    ON,
    OFF,
    FAST_COOL,
    FAST_HEAT,
    DEODORIZE
}

enum class AirCircle(
    val wireValue: String,
    val telemetryValue: Int,
    val displayLabel: String
) {
    INNER("in", 1, "内循环"),
    OUTER("out", 0, "外循环");

    companion object {
        fun fromTelemetryValue(value: Any?): AirCircle? = when (ClimateSignalValue.raw(value)?.toIntOrNull()) {
            INNER.telemetryValue -> INNER
            OUTER.telemetryValue -> OUTER
            else -> null
        }

        fun fromVehicleTelemetry(primaryValue: Any?, legacyValue: Any?): AirCircle? {
            if (ClimateSignalValue.raw(primaryValue) != null) return fromTelemetryValue(primaryValue)
            return when (ClimateSignalValue.boolean(legacyValue)) {
                true -> INNER
                false -> OUTER
                null -> null
            }
        }
    }
}

enum class AirOutlet(val wireValue: String) {
    ALL("all"),
    WINDSHIELD("wshld")
}

data class HvacCapability(
    val temperatureMinC: Int,
    val temperatureMaxC: Int,
    val fanMin: Int? = null,
    val fanMax: Int? = null
) {
    init {
        require(temperatureMinC in 10..40 && temperatureMaxC in temperatureMinC..40) {
            "空调温区能力无效"
        }
        require((fanMin == null) == (fanMax == null)) { "风量能力必须同时包含最小值和最大值" }
        fanMin?.let { minimum ->
            require(minimum >= 0 && requireNotNull(fanMax) >= minimum) { "空调风量能力无效" }
        }
    }

    fun effectiveUiFanRange(): IntRange =
        if (fanMin != null && fanMax != null && fanMin >= 1 && fanMax >= fanMin) {
            fanMin..fanMax
        } else {
            DEFAULT_FAN_MIN..DEFAULT_FAN_MAX
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("temperatureMinC", temperatureMinC)
        put("temperatureMaxC", temperatureMaxC)
        fanMin?.let { put("fanMin", it) }
        fanMax?.let { put("fanMax", it) }
    }

    companion object {
        const val DEFAULT_TEMPERATURE_MIN_C = 19
        const val DEFAULT_TEMPERATURE_MAX_C = 32
        const val DEFAULT_FAN_MIN = 1
        const val DEFAULT_FAN_MAX = 7

        fun fallback(): HvacCapability = HvacCapability(
            temperatureMinC = DEFAULT_TEMPERATURE_MIN_C,
            temperatureMaxC = DEFAULT_TEMPERATURE_MAX_C
        )

        fun fromJson(value: JSONObject): HvacCapability = runCatching {
            HvacCapability(
                temperatureMinC = value.getInt("temperatureMinC"),
                temperatureMaxC = value.getInt("temperatureMaxC"),
                fanMin = value.takeIf { it.has("fanMin") && !it.isNull("fanMin") }?.getInt("fanMin"),
                fanMax = value.takeIf { it.has("fanMax") && !it.isNull("fanMax") }?.getInt("fanMax")
            )
        }.getOrElse { fallback() }
    }
}

object HvacCapabilityParser {
    fun parse(funcConfig: JSONObject?): HvacCapability {
        val hvac = funcConfig?.optJSONObject("HVAC") ?: funcConfig?.optJSONObject("hvac")
        val temperature = hvac?.optJSONObject("temperature")
        val fan = hvac?.optJSONObject("fan")
        val temperatureMin = temperature.optIntOrNull("min")
        val temperatureMax = temperature.optIntOrNull("max")
        val fanMin = fan.optIntOrNull("min")
        val fanMax = fan.optIntOrNull("max")
        val validTemperature = temperatureMin != null && temperatureMax != null &&
            temperatureMin in 10..40 && temperatureMax in temperatureMin..40
        val validFan = fanMin != null && fanMax != null && fanMin >= 0 && fanMax >= fanMin
        return HvacCapability(
            temperatureMinC = if (validTemperature) requireNotNull(temperatureMin) else HvacCapability.DEFAULT_TEMPERATURE_MIN_C,
            temperatureMaxC = if (validTemperature) requireNotNull(temperatureMax) else HvacCapability.DEFAULT_TEMPERATURE_MAX_C,
            fanMin = if (validFan) fanMin else null,
            fanMax = if (validFan) fanMax else null
        )
    }

    private fun JSONObject?.optIntOrNull(key: String): Int? {
        if (this == null || !has(key) || isNull(key)) return null
        return when (val raw = opt(key)) {
            is Number -> raw.toInt().takeIf { raw.toDouble() == it.toDouble() }
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
    }
}

data class AirConditioningCommand(
    val operation: HvacOperation,
    val temperatureC: Int,
    val capability: HvacCapability,
    val windLevel: Int,
    val circle: AirCircle,
    val windshieldDefogging: Boolean,
    val outlet: AirOutlet,
    val customLabel: String? = null
)

data class ClimateTelemetryExpectation(
    val acSwitch: Boolean? = null,
    val temperatureC: Int? = null,
    val windLevel: Int? = null,
    val circle: AirCircle? = null,
    val windshieldDefogging: Boolean? = null,
    val hasUnavailableFields: Boolean = false
)

/** The subset of climate telemetry that has a verified readback signal. */
data class ClimateTelemetryReadback(
    val acSwitch: Boolean?,
    val temperatureC: Int?,
    val windLevel: Int?,
    val circle: AirCircle?,
    val windshieldDefogging: Boolean?
)

/** 控车下发结果。 */
data class ControlResult(val msgID: String, val raw: JSONObject) {
    fun hasPollingId(): Boolean = msgID.isNotBlank()
}

enum class ControlFeedbackKind {
    IN_PROGRESS,
    SUBMITTED,
    SUCCESS,
    WARNING,
    ERROR
}

data class ControlFeedback(
    val message: String,
    val kind: ControlFeedbackKind
)

object ControlFeedbackDisplayPolicy {
    const val SUBMITTED_DURATION_MS = 2_500L
    const val SUCCESS_DURATION_MS = 2_500L
    const val WARNING_DURATION_MS = 3_500L
    const val ERROR_DURATION_MS = 3_500L

    fun autoDismissDelayMs(kind: ControlFeedbackKind): Long? = when (kind) {
        ControlFeedbackKind.IN_PROGRESS -> null
        ControlFeedbackKind.SUBMITTED -> SUBMITTED_DURATION_MS
        ControlFeedbackKind.SUCCESS -> SUCCESS_DURATION_MS
        ControlFeedbackKind.WARNING -> WARNING_DURATION_MS
        ControlFeedbackKind.ERROR -> ERROR_DURATION_MS
    }
}

object ControlFeedbackFormatter {
    fun inProgress(commandName: String?, label: String?): String {
        val key = (commandName ?: label).orEmpty().trim()
        val lbl = label.orEmpty().trim()
        return when {
            key == "unlock" || key == "解锁" -> "解锁中..."
            key == "lock" || key == "上锁" -> "上锁中..."
            key == "trunkOpen" || key == "开后备箱" -> "开后备箱中..."
            key == "trunkClose" || key == "关后备箱" -> "关后备箱中..."
            key == "trunk" || key == "后备箱" -> "后备箱操作中..."
            key == "frunkOpen" || key == "开前备箱" -> "开前备箱中..."
            key == "frunkClose" || key == "关前备箱" -> "关前备箱中..."
            key == "windowVent" || key == "车窗微开" -> "车窗微开中..."
            key == "windowOpen" || key == "开窗" -> "开窗中..."
            key == "windowClose" || key == "关窗" -> "关窗中..."
            key == "sunshadeOpen" || key == "遮阳帘开启" -> "开启遮阳帘中..."
            key == "sunshadeClose" || key == "遮阳帘关闭" -> "关闭遮阳帘中..."
            key == "sunshadeHalf" || key == "遮阳帘半开" -> "调节遮阳帘中..."
            key == "acOn" || key == "开空调" -> "正在开启制冷..."
            key == "acOff" || key == "关空调" -> "正在关闭空调..."
            key == "quickCool" || key == "极速降温" -> "正在开启极速降温..."
            key == "quickHeat" || key == "一键制热" -> "正在开启制热..."
            key == "defrost" || key == "前挡除霜" -> "正在开启前挡除霜..."
            key == "deodorize" || key == "一键除味" || key == "快速除味" -> "正在开启快速除味..."
            key == "sentryOn" -> "开启哨兵模式中..."
            key == "sentryOff" -> "关闭哨兵模式中..."
            key == "sentry" || key == "哨兵模式" -> "切换哨兵模式中..."
            key == "batteryPreheat" || key == "电池预热" -> "开启电池预热中..."
            key == "horn" || key == "鸣笛" || key == "鸣笛寻车" -> "鸣笛寻车中..."
            key == "fridgeOn" || key == "开启冰箱" -> {
                if (lbl.contains("制热") || lbl.contains("50°C")) "正在开启车载冰箱保温..."
                else if (lbl.contains("制冷")) "正在开启车载冰箱冷藏..."
                else "开启冰箱中..."
            }
            key == "fridgeOff" || key == "关闭冰箱" -> "关闭冰箱中..."
            key == "关闭车载冰箱" -> "正在关闭车载冰箱..."
            key.startsWith("fridge") -> "调节冰箱中..."
            key == "driverSeatVentilation_0" || lbl == "主驾通风关闭" -> "正在关闭主驾座椅通风..."
            key.startsWith("driverSeatVentilation") || lbl.contains("主驾通风") -> "正在开启主驾座椅通风..."
            key == "driverSeatHeating_0" || lbl == "主驾加热关闭" -> "正在关闭主驾座椅加热..."
            key.startsWith("driverSeatHeating") || lbl.contains("主驾加热") -> "正在开启主驾座椅加热..."
            key == "passengerSeatVentilation_0" || lbl == "副驾通风关闭" -> "正在关闭副驾座椅通风..."
            key.startsWith("passengerSeatVentilation") || lbl.contains("副驾通风") -> "正在开启副驾座椅通风..."
            key == "passengerSeatHeating_0" || lbl == "副驾加热关闭" -> "正在关闭副驾座椅加热..."
            key.startsWith("passengerSeatHeating") || lbl.contains("副驾加热") -> "正在开启副驾座椅加热..."
            key == "leftRearSeatVentilation_0" || lbl == "二排左通风关闭" -> "正在关闭二排左座椅通风..."
            key.startsWith("leftRearSeatVentilation") || lbl.contains("二排左通风") -> "正在开启二排左座椅通风..."
            key == "leftRearSeatHeating_0" || lbl == "二排左加热关闭" -> "正在关闭二排左座椅加热..."
            key.startsWith("leftRearSeatHeating") || lbl.contains("二排左加热") -> "正在开启二排左座椅加热..."
            key == "rightRearSeatVentilation_0" || lbl == "二排右通风关闭" -> "正在关闭二排右座椅通风..."
            key.startsWith("rightRearSeatVentilation") || lbl.contains("二排右通风") -> "正在开启二排右座椅通风..."
            key == "rightRearSeatHeating_0" || lbl == "二排右加热关闭" -> "正在关闭二排右座椅加热..."
            key.startsWith("rightRearSeatHeating") || lbl.contains("二排右加热") -> "正在开启二排右座椅加热..."
            key == "steeringWheelHeating_0" || lbl == "方向盘加热关闭" -> "正在关闭方向盘加热..."
            key.startsWith("steeringWheelHeating") || lbl.contains("方向盘加热") -> "正在开启方向盘加热..."
            key == "rearviewMirrorHeating_0" || lbl == "关闭后视镜加热" -> "正在关闭后视镜加热..."
            key.startsWith("rearviewMirrorHeating") || lbl.contains("后视镜加热") -> "正在开启后视镜加热..."
            key.startsWith("fotaDownload") -> "正在启动固件下载..."
            key.startsWith("fotaInstall") -> "正在发送固件升级指令..."
            key.startsWith("fotaSchedule") -> "正在提交定时升级预约..."
            lbl.isNotBlank() && lbl != "应用空调设置" && lbl != "climate" -> "${lbl.removeSuffix("中").removeSuffix("...")}中..."
            else -> "处理中..."
        }
    }

    fun success(commandName: String?, label: String?): String {
        val key = (commandName ?: label).orEmpty().trim()
        val lbl = label.orEmpty().trim()
        return when {
            key == "unlock" || key == "解锁" -> "解锁成功"
            key == "lock" || key == "上锁" -> "上锁成功"
            key == "trunkOpen" || key == "开后备箱" -> "后备箱已开启"
            key == "trunkClose" || key == "关后备箱" -> "后备箱已关闭"
            key == "trunk" || key == "后备箱" -> "后备箱操作成功"
            key == "frunkOpen" || key == "开前备箱" -> "前备箱已开启"
            key == "frunkClose" || key == "关前备箱" -> "前备箱已关闭"
            key == "windowVent" || key == "车窗微开" -> "车窗已微开"
            key == "windowOpen" || key == "开窗" -> "车窗已开启"
            key == "windowClose" || key == "关窗" -> "车窗已关闭"
            key == "sunshadeOpen" || key == "遮阳帘开启" -> "遮阳帘已开启"
            key == "sunshadeClose" || key == "遮阳帘关闭" -> "遮阳帘已关闭"
            key == "sunshadeHalf" || key == "遮阳帘半开" -> "遮阳帘设置成功"
            key == "acOn" || key == "开空调" -> "制冷已开启"
            key == "acOff" || key == "关空调" -> "空调已关闭"
            key == "quickCool" || key == "极速降温" -> "极速降温已开启"
            key == "quickHeat" || key == "一键制热" -> "制热已开启"
            key == "defrost" || key == "前挡除霜" -> "前挡除霜已开启"
            key == "deodorize" || key == "一键除味" || key == "快速除味" -> "快速除味已开启"
            key == "sentryOn" -> "哨兵模式已开启"
            key == "sentryOff" -> "哨兵模式已关闭"
            key.startsWith("fotaDownload") -> "固件下载已启动"
            key.startsWith("fotaInstall") -> "整车升级指令已发送"
            key.startsWith("fotaSchedule") -> "定时升级已预约成功"
            key == "sentry" || key == "哨兵模式" -> "哨兵模式设置成功"
            key == "batteryPreheat" || key == "电池预热" -> "电池预热已开启"
            key == "horn" || key == "鸣笛" || key == "鸣笛寻车" -> "鸣笛寻车已完成"
            key == "fridgeOn" || key == "开启冰箱" -> {
                if (lbl.contains("制热") || lbl.contains("50°C")) {
                    "车载冰箱已开启 · 保温 50°C"
                } else {
                    val tempMatch = Regex("(-?\\d+°C)").find(lbl)?.value
                    if (tempMatch != null) "车载冰箱已开启 · 冷藏 $tempMatch"
                    else "冰箱已开启"
                }
            }
            key == "fridgeOff" || key == "关闭冰箱" || key == "关闭车载冰箱" -> {
                if (lbl.contains("车载冰箱")) "车载冰箱已关闭" else "冰箱已关闭"
            }
            key.startsWith("fridge") -> {
                val tempMatch = Regex("(-?\\d+°C)").find(lbl)?.value
                if (tempMatch != null) "车载冰箱已调至 $tempMatch"
                else "冰箱设置成功"
            }
            key == "driverSeatVentilation_0" || lbl == "主驾通风关闭" -> "主驾座椅通风已关闭"
            key.startsWith("driverSeatVentilation") || lbl.contains("主驾通风") -> "主驾座椅通风已打开"
            key == "driverSeatHeating_0" || lbl == "主驾加热关闭" -> "主驾座椅加热已关闭"
            key.startsWith("driverSeatHeating") || lbl.contains("主驾加热") -> "主驾座椅加热已打开"
            key == "passengerSeatVentilation_0" || lbl == "副驾通风关闭" -> "副驾座椅通风已关闭"
            key.startsWith("passengerSeatVentilation") || lbl.contains("副驾通风") -> "副驾座椅通风已打开"
            key == "passengerSeatHeating_0" || lbl == "副驾加热关闭" -> "副驾座椅加热已关闭"
            key.startsWith("passengerSeatHeating") || lbl.contains("副驾加热") -> "副驾座椅加热已打开"
            key == "leftRearSeatVentilation_0" || lbl == "二排左通风关闭" -> "二排左座椅通风已关闭"
            key.startsWith("leftRearSeatVentilation") || lbl.contains("二排左通风") -> "二排左座椅通风已打开"
            key == "leftRearSeatHeating_0" || lbl == "二排左加热关闭" -> "二排左座椅加热已关闭"
            key.startsWith("leftRearSeatHeating") || lbl.contains("二排左加热") -> "二排左座椅加热已打开"
            key == "rightRearSeatVentilation_0" || lbl == "二排右通风关闭" -> "二排右座椅通风已关闭"
            key.startsWith("rightRearSeatVentilation") || lbl.contains("二排右通风") -> "二排右座椅通风已打开"
            key == "rightRearSeatHeating_0" || lbl == "二排右加热关闭" -> "二排右座椅加热已关闭"
            key.startsWith("rightRearSeatHeating") || lbl.contains("二排右加热") -> "二排右座椅加热已打开"
            key == "steeringWheelHeating_0" || lbl == "方向盘加热关闭" -> "方向盘加热已关闭"
            key.startsWith("steeringWheelHeating") || lbl.contains("方向盘加热") -> "方向盘加热已打开"
            key == "rearviewMirrorHeating_0" || lbl == "关闭后视镜加热" -> "后视镜加热已关闭"
            key.startsWith("rearviewMirrorHeating") || lbl.contains("后视镜加热") -> "后视镜加热已打开"
            lbl.isNotBlank() && lbl != "应用空调设置" && lbl != "climate" -> {
                if (lbl.startsWith("已切换") || lbl.contains("已调至") || lbl.endsWith("已开启") || lbl.endsWith("已关闭") || lbl.endsWith("已打开") || lbl.endsWith("已完成") || lbl.endsWith("成功")) {
                    lbl
                } else if (lbl.startsWith("温度") || lbl.startsWith("风量")) {
                    "${lbl}已生效"
                } else {
                    "${lbl.removeSuffix("中").removeSuffix("...")}成功"
                }
            }
            else -> "操作成功"
        }
    }
}

data class AcTemperatureTarget(
    val value: Int,
    val confirmed: Boolean
)

data class PendingClimateTemperature(
    val requestId: Long,
    val target: Int,
    val baselineVehicleTemperature: Int?
)

enum class ClimateControlRequestPhase {
    IDLE,
    SENDING,
    ACCEPTED,
    COMPLETED,
    NOT_CONFIRMED,
    FAILED
}

data class ClimateControlRequestState(
    val requestId: Long = 0L,
    val phase: ClimateControlRequestPhase = ClimateControlRequestPhase.IDLE
) {
    fun isTerminalFor(requestId: Long): Boolean =
        this.requestId == requestId && phase in setOf(
            ClimateControlRequestPhase.COMPLETED,
            ClimateControlRequestPhase.NOT_CONFIRMED,
            ClimateControlRequestPhase.FAILED
        )
}

/** Fields that may be shown immediately after the control service completes. */
data class ClimateOptimisticUpdate(
    val acSwitch: Boolean? = null,
    val acSetting: String? = null,
    val windLevel: Int? = null,
    val circle: AirCircle? = null,
    val windshieldDefrost: Boolean? = null
)

data class ClimateTelemetrySnapshot(
    val acSwitch: Boolean?,
    val acSetting: String?,
    val windLevel: Int? = null,
    val circle: AirCircle? = null,
    val windshieldDefrost: Boolean? = null
)

/**
 * Keeps the optimistic surface limited to values explicitly carried by existing commands.
 * Telemetry remains the source of truth and replaces these values during the next refresh.
 */
object ClimateOptimisticUpdates {
    fun acOn() = ClimateOptimisticUpdate(
        acSwitch = true,
        acSetting = "${Commands.DEFAULT_AC_TEMPERATURE} °C",
        windLevel = 3,
        circle = AirCircle.INNER,
        windshieldDefrost = false
    )

    fun acOff() = ClimateOptimisticUpdate(acSwitch = false)

    fun temperature(value: Int): ClimateOptimisticUpdate {
        require(Commands.isSupportedAcTemperature(value))
        return ClimateOptimisticUpdate(
            acSwitch = true,
            acSetting = "$value °C",
            windLevel = 7,
            circle = AirCircle.INNER,
            windshieldDefrost = false
        )
    }

    fun windshieldDefrost() = ClimateOptimisticUpdate(
        acSwitch = true,
        acSetting = "${Commands.DEFAULT_AC_TEMPERATURE} °C",
        windLevel = 5,
        circle = AirCircle.OUTER,
        windshieldDefrost = true
    )

    fun deodorize() = ClimateOptimisticUpdate(
        acSwitch = true,
        acSetting = "${Commands.DEFAULT_AC_TEMPERATURE} °C",
        windLevel = 7,
        circle = AirCircle.OUTER,
        windshieldDefrost = false
    )

    fun detailed(command: AirConditioningCommand): ClimateOptimisticUpdate =
        if (command.operation == HvacOperation.OFF) {
            ClimateOptimisticUpdate(acSwitch = false)
        } else {
            ClimateOptimisticUpdate(
                acSwitch = true,
                acSetting = "${command.temperatureC} °C",
                windLevel = command.windLevel,
                circle = command.circle,
                windshieldDefrost = command.windshieldDefogging
            )
        }

    fun mergeOver(update: ClimateOptimisticUpdate, telemetry: ClimateTelemetrySnapshot): ClimateTelemetrySnapshot =
        telemetry.copy(
            acSwitch = update.acSwitch ?: telemetry.acSwitch,
            acSetting = update.acSetting ?: telemetry.acSetting,
            windLevel = update.windLevel ?: telemetry.windLevel,
            circle = update.circle ?: telemetry.circle,
            windshieldDefrost = update.windshieldDefrost ?: telemetry.windshieldDefrost
        )
}

/** Maps pointer coordinates to the visible slider track, including the thumb radius. */
object ClimateSliderMapping {
    fun fractionAt(pointerX: Float, widthPx: Float, thumbRadiusPx: Float): Float {
        if (!widthPx.isFinite() || widthPx <= 0f) return 0f
        val radius = thumbRadiusPx.coerceAtLeast(0f)
        val trackStart = radius
        val trackEnd = (widthPx - radius).coerceAtLeast(trackStart + 1f)
        return ((pointerX - trackStart) / (trackEnd - trackStart)).coerceIn(0f, 1f)
    }
}

/**
 * Keeps service acceptance separate from vehicle-state confirmation. A command
 * is confirmed only when every requested field with a verified readback matches.
 */
object ClimateTelemetryConfirmationPolicy {
    fun matches(
        optimisticUpdate: ClimateOptimisticUpdate?,
        telemetryExpectation: ClimateTelemetryExpectation?,
        readback: ClimateTelemetryReadback
    ): Boolean {
        telemetryExpectation?.let { expectation ->
            return (expectation.acSwitch == null || expectation.acSwitch == readback.acSwitch) &&
                (expectation.temperatureC == null || expectation.temperatureC == readback.temperatureC) &&
                (expectation.windLevel == null || expectation.windLevel == readback.windLevel) &&
                (expectation.circle == null || expectation.circle == readback.circle) &&
                (expectation.windshieldDefogging == null ||
                    expectation.windshieldDefogging == readback.windshieldDefogging)
        }
        optimisticUpdate ?: return false
        val expectedTemperature = Commands.normalizedAcTemperature(optimisticUpdate.acSetting)
        return (optimisticUpdate.acSwitch == null || optimisticUpdate.acSwitch == readback.acSwitch) &&
            (expectedTemperature == null || expectedTemperature == readback.temperatureC) &&
            (optimisticUpdate.windLevel == null || optimisticUpdate.windLevel == readback.windLevel) &&
            (optimisticUpdate.circle == null || optimisticUpdate.circle == readback.circle) &&
            (optimisticUpdate.windshieldDefrost == null ||
                optimisticUpdate.windshieldDefrost == readback.windshieldDefogging)
    }
}

data class ClimateOptimisticGuard(
    val revision: Long,
    val update: ClimateOptimisticUpdate,
    val postCompleted: Boolean = false
)

enum class ClimateTelemetryMergeDecision {
    APPLY,
    PRESERVE_CLIMATE
}

/** Prevents an in-flight or immediately stale snapshot from replacing a just-completed command. */
object ClimateTelemetryMergePolicy {
    fun decide(
        guard: ClimateOptimisticGuard?,
        refreshRevision: Long,
        currentRevision: Long,
        matchesOptimisticTarget: Boolean
    ): ClimateTelemetryMergeDecision {
        if (guard == null) return ClimateTelemetryMergeDecision.APPLY
        if (guard.revision == currentRevision &&
            refreshRevision == currentRevision &&
            guard.postCompleted &&
            matchesOptimisticTarget
        ) {
            return ClimateTelemetryMergeDecision.APPLY
        }
        return ClimateTelemetryMergeDecision.PRESERVE_CLIMATE
    }

    fun consume(
        guard: ClimateOptimisticGuard?,
        decision: ClimateTelemetryMergeDecision
    ): ClimateOptimisticGuard? = when (decision) {
        ClimateTelemetryMergeDecision.APPLY -> null
        ClimateTelemetryMergeDecision.PRESERVE_CLIMATE -> guard
    }
}

/** Integer, bounded slider semantics shared by the UI and pure tests. */
object ClimateTemperatureSlider {
    fun clamp(value: Float): Float = value.coerceIn(
        Commands.MIN_AC_TEMPERATURE.toFloat(),
        Commands.MAX_AC_TEMPERATURE.toFloat()
    )

    fun discreteStepCount(): Int = Commands.MAX_AC_TEMPERATURE - Commands.MIN_AC_TEMPERATURE - 1

    fun normalize(value: Float): Int = value.roundToInt().coerceIn(
        Commands.MIN_AC_TEMPERATURE,
        Commands.MAX_AC_TEMPERATURE
    )

    fun shouldSubmit(value: Float, current: Int, enabled: Boolean): Boolean =
        enabled && normalize(value) != current
}

/** Short user-facing copy; service acceptance remains separate from telemetry confirmation. */
object ClimateControlFeedbackText {
    private data class Copy(
        val sending: String,
        val submitted: String,
        val confirmed: String,
        val failed: String
    )

    fun sending(label: String): String = copyFor(label).sending

    fun submitted(label: String): String = copyFor(label).submitted

    fun waiting(label: String): String = copyFor(label).submitted

    fun confirmed(label: String): String = copyFor(label).confirmed

    fun confirmedAvailableFields(label: String): String = copyFor(label).confirmed

    fun responsePending(label: String): String = "空调响应较慢"

    fun notConfirmed(label: String): String = "${label}已下发，车辆状态暂未确认"

    fun failed(label: String): String = copyFor(label).failed

    private fun copyFor(label: String): Copy = when {
        label == "关空调" || label == "关闭空调" -> Copy(
            sending = "空调关闭中",
            submitted = "关空调已下发，待确认",
            confirmed = "空调已关闭",
            failed = "空调关闭失败"
        )
        label.startsWith("开空调") -> Copy(
            sending = "空调开启中",
            submitted = "开空调已下发，待确认",
            confirmed = "空调已开启",
            failed = "空调开启失败"
        )
        label == "极速降温" -> Copy("降温中", "降温已下发，待确认", "降温已开启", "降温失败")
        label == "极速升温" -> Copy("升温中", "升温已下发，待确认", "升温已开启", "升温失败")
        label == "开启除雾" -> Copy("除雾中", "除雾已下发，待确认", "除雾已开启", "除雾失败")
        label == "快速除味" -> Copy("除味中", "除味已下发，待确认", "除味已开启", "除味失败")
        else -> Copy("空调设置中", "设置已下发，待确认", "空调已更新", "空调设置失败")
    }
}

enum class ClimateControlPostOutcome {
    ACCEPTED,
    FAILED
}

data class ClimateControlPostDecision(
    val outcome: ClimateControlPostOutcome,
    val shouldQueryControlResult: Boolean
)

/** Verified climate commands may use result polling after the server accepts them. */
object ClimateControlPostPolicy {
    fun decision(
        command: ControlCommand,
        postSucceeded: Boolean,
        hasPollingId: Boolean = false
    ): ClimateControlPostDecision =
        if (postSucceeded && isSupported(command)) {
            ClimateControlPostDecision(
                ClimateControlPostOutcome.ACCEPTED,
                shouldQueryControlResult = hasPollingId
            )
        } else {
            ClimateControlPostDecision(ClimateControlPostOutcome.FAILED, shouldQueryControlResult = false)
        }

    private fun isSupported(command: ControlCommand): Boolean = command.cmdid == "170"
}

data class ClimateTemperaturePresentation(
    val value: Int,
    val isPending: Boolean,
    val clearPending: Boolean
)

/** Resolves display-only temperature state without changing the last vehicle status. */
object ClimateTemperatureSync {
    fun resolve(
        vehicleTarget: AcTemperatureTarget,
        pending: PendingClimateTemperature?,
        requestState: ClimateControlRequestState
    ): ClimateTemperaturePresentation {
        if (pending == null) {
            return ClimateTemperaturePresentation(vehicleTarget.value, isPending = false, clearPending = false)
        }
        return if (requestState.isTerminalFor(pending.requestId)) {
            ClimateTemperaturePresentation(vehicleTarget.value, isPending = false, clearPending = true)
        } else {
            ClimateTemperaturePresentation(pending.target, isPending = true, clearPending = false)
        }
    }
}

/** 已验证空调信号的无歧义展示解析，不参与既有空调控制判断。 */
object ClimateSignalValue {
    fun raw(value: Any?): String? =
        if (value == null || value == JSONObject.NULL) null
        else value.toString().trim().takeIf { it.isNotBlank() }

    fun boolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> when (value.toDouble()) {
            0.0 -> false
            1.0 -> true
            else -> null
        }
        is String -> when (value.trim().lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
        else -> null
    }
}

enum class ClimatePresetAction(val label: String) {
    QUICK_COOL("极速降温"),
    QUICK_HEAT("极速升温"),
    WINDSHIELD_DEFROST("前挡除霜"),
    DEODORIZE("快速除味")
}

/** Presentation only: follows the verified temperature-to-command-mode convention. */
enum class ClimateTemperatureTone {
    DEFAULT,
    COOLING,
    HEATING,
    VENTILATION
}

object ClimateTemperatureToneResolver {
    /**
     * Verified telemetry enum: 1=cooling, 0=heating, 2=ventilation.
     * climateMode: 1=cooling, 3=heating, 4=ventilation.
     */
    fun tone(
        acEnabled: Boolean?,
        coolingAndHeating: Int?,
        climateMode: Int? = null,
        targetTemperature: Int? = null
    ): ClimateTemperatureTone {
        if (acEnabled != true) return ClimateTemperatureTone.DEFAULT
        if (coolingAndHeating == 2 || climateMode == 4) return ClimateTemperatureTone.VENTILATION
        return when (coolingAndHeating) {
            1 -> ClimateTemperatureTone.COOLING
            0 -> ClimateTemperatureTone.HEATING
            else -> when (climateMode) {
                1 -> ClimateTemperatureTone.COOLING
                3 -> ClimateTemperatureTone.HEATING
                4 -> ClimateTemperatureTone.VENTILATION
                else -> when {
                    targetTemperature != null && targetTemperature >= 27 -> ClimateTemperatureTone.HEATING
                    targetTemperature != null && targetTemperature <= 26 -> ClimateTemperatureTone.COOLING
                    else -> ClimateTemperatureTone.COOLING
                }
            }
        }
    }
}

object Commands {
    const val MIN_AC_TEMPERATURE = 16
    const val MAX_AC_TEMPERATURE = 32
    const val DEFAULT_AC_TEMPERATURE = 24

    const val AC_ON_STATE =
        """{"operate":"auto","temperature":"24","windlevel":"3","mode":"cold","circle":"in","wshld":"1","position":"all"}"""
    const val AC_OFF_STATE =
        """{"operate":"off","temperature":"24","windlevel":"3","mode":"nohotcold","circle":"in","wshld":"1","position":"all"}"""
    const val QUICK_COOL_STATE =
        """{"operate":"manual","temperature":"18","windlevel":"7","mode":"cold","circle":"in","wshld":"1","position":"all"}"""
    const val QUICK_HEAT_STATE =
        """{"operate":"manual","temperature":"32","windlevel":"7","mode":"hot","circle":"in","wshld":"1","position":"all"}"""
    const val QUICK_DEODORIZE_STATE =
        """{"operate":"manual","temperature":"24","windlevel":"7","mode":"nohotcold","circle":"out","wshld":"0","position":"all"}"""

    fun build(name: String): ControlCommand = when (name) {
        "lock" -> ControlCommand("110", """{"value":"lock"}""", "上锁")
        "unlock" -> ControlCommand("110", """{"value":"unlock"}""", "解锁")
        "trunkOpen" -> ControlCommand("130", """{"value":"true"}""", "开后备箱")
        "trunkClose" -> ControlCommand("130", """{"value":"false"}""", "关后备箱")
        "frunkOpen" -> ControlCommand("131", """{"value":"100"}""", "开前备箱")
        "frunkClose" -> ControlCommand("131", """{"value":"0"}""", "关前备箱")
        "windowOpen" -> ControlCommand("230", """{"value":"5"}""", "车窗半开")
        "windowVent" -> ControlCommand("230", """{"value":"2"}""", "车窗通风")
        "windowClose" -> ControlCommand("230", """{"value":"0"}""", "车窗全关")
        "sunshadeOpen" -> ControlCommand("240", """{"value":"10"}""", "遮阳帘打开")
        "sunshadeClose" -> ControlCommand("240", """{"value":"0"}""", "遮阳帘关闭")
        "horn" -> ControlCommand("120", """{"value":"true"}""", "鸣笛寻车")
        "batteryPreheat" -> ControlCommand("160", """{"value":"ptcon"}""", "电池预热开")
        "batteryPreheatOff" -> ControlCommand("160", """{"value":"ptcoff"}""", "电池预热关")
        "acOn" -> ControlCommand("170", AC_ON_STATE, "开空调")
        "acOff" -> ControlCommand("170", AC_OFF_STATE, "关空调")
        "defrost" -> ControlCommand("170", """{"operate":"manual","temperature":"24","windlevel":"5","mode":"cold","circle":"out","wshld":"1","position":"wshld"}""", "开启除雾")
        "quickCool" -> ControlCommand("170", QUICK_COOL_STATE, "极速降温")
        "quickHeat" -> ControlCommand("170", QUICK_HEAT_STATE, "极速升温")
        "deodorize" -> ControlCommand("170", QUICK_DEODORIZE_STATE, "快速除味")
        "sentryOn" -> ControlCommand("400", """{"operation":"on"}""", "开启哨兵模式")
        "sentryOff" -> ControlCommand("400", """{"operation":"off"}""", "关闭哨兵模式")
        "startCharging" -> ControlCommand("193", """{"value":"start"}""", "开始充电")
        "stopCharging" -> ControlCommand("193", """{"value":"stop"}""", "停止充电")
        "unlockCharger" -> ControlCommand("192", """{"operation":"unlock"}""", "解锁充电枪")
        "fridgeOn" -> buildFridgeControl(FridgeControlCommand(enable = true, mode = FridgeMode.COLD, temp = FRIDGE_DEFAULT_TEMP, style = FridgeStyle.NORMAL))
        "fridgeOff" -> buildFridgeControl(FridgeControlCommand(enable = false, mode = FridgeMode.COLD, temp = FRIDGE_DEFAULT_TEMP, style = FridgeStyle.NORMAL))
        else -> {
            when {
                name.startsWith("driverSeatHeating_") -> {
                    val lvl = name.removePrefix("driverSeatHeating_").toIntOrNull() ?: 0
                    buildSeatHeating("left_front", lvl)
                }
                name.startsWith("passengerSeatHeating_") -> {
                    val lvl = name.removePrefix("passengerSeatHeating_").toIntOrNull() ?: 0
                    buildSeatHeating("right_front", lvl)
                }
                name.startsWith("driverSeatVentilation_") -> {
                    val lvl = name.removePrefix("driverSeatVentilation_").toIntOrNull() ?: 0
                    buildSeatVentilation("left_front", lvl)
                }
                name.startsWith("passengerSeatVentilation_") -> {
                    val lvl = name.removePrefix("passengerSeatVentilation_").toIntOrNull() ?: 0
                    buildSeatVentilation("right_front", lvl)
                }
                name.startsWith("leftRearSeatHeating_") -> {
                    val lvl = name.removePrefix("leftRearSeatHeating_").toIntOrNull() ?: 0
                    buildSeatHeating("left_rear", lvl)
                }
                name.startsWith("rightRearSeatHeating_") -> {
                    val lvl = name.removePrefix("rightRearSeatHeating_").toIntOrNull() ?: 0
                    buildSeatHeating("right_rear", lvl)
                }
                name.startsWith("leftRearSeatVentilation_") -> {
                    val lvl = name.removePrefix("leftRearSeatVentilation_").toIntOrNull() ?: 0
                    buildSeatVentilation("left_rear", lvl)
                }
                name.startsWith("rightRearSeatVentilation_") -> {
                    val lvl = name.removePrefix("rightRearSeatVentilation_").toIntOrNull() ?: 0
                    buildSeatVentilation("right_rear", lvl)
                }
                name.startsWith("steeringWheelHeating_") -> {
                    val lvl = name.removePrefix("steeringWheelHeating_").toIntOrNull() ?: 0
                    buildSteeringWheelHeating(lvl)
                }
                name == "rearviewMirrorHeating_on" -> buildRearviewMirrorHeating(true)
                name == "rearviewMirrorHeating_off" -> buildRearviewMirrorHeating(false)
                name.startsWith("fotaDownload:") -> {
                    val taskId = name.removePrefix("fotaDownload:")
                    ControlCommand("390", """{"taskId":"$taskId"}""", "下载固件")
                }
                name.startsWith("fotaInstall:") -> {
                    val taskId = name.removePrefix("fotaInstall:")
                    ControlCommand("391", """{"taskId":"$taskId"}""", "安装固件")
                }
                name.startsWith("fotaSchedule:") -> {
                    val parts = name.removePrefix("fotaSchedule:").split(":", limit = 2)
                    val taskId = parts.getOrNull(0).orEmpty()
                    val time = parts.getOrNull(1).orEmpty()
                    ControlCommand("392", """{"taskId":"$taskId","scheduleTime":"$time"}""", "预约安装固件")
                }
                else -> throw ApiException("未知命令: $name")
            }
        }
    }

    /** 座椅加热（cmdid=301）：position: left_front / right_front，level: 0..3 */
    fun buildSeatHeating(position: String = "left_front", level: Int): ControlCommand {
        val cleanLevel = level.coerceIn(0, 3)
        val posLabel = if (position == "left_front") "主驾" else "副驾"
        val stateLabel = if (cleanLevel == 0) "关闭" else "${cleanLevel}档"
        return ControlCommand(
            "301",
            """{"position":"$position","level":"$cleanLevel"}""",
            "${posLabel}加热$stateLabel"
        )
    }

    /** 座椅通风（cmdid=370）：position: left_front / right_front，level: 0..3 */
    fun buildSeatVentilation(position: String = "left_front", level: Int): ControlCommand {
        val cleanLevel = level.coerceIn(0, 3)
        val posLabel = if (position == "left_front") "主驾" else "副驾"
        val stateLabel = if (cleanLevel == 0) "关闭" else "${cleanLevel}档"
        return ControlCommand(
            "370",
            """{"position":"$position","level":"$cleanLevel"}""",
            "${posLabel}通风$stateLabel"
        )
    }

    /** 方向盘加热（cmdid=320）：level: 0(关), 1(弱), 2(强) */
    fun buildSteeringWheelHeating(level: Int): ControlCommand {
        val cleanLevel = level.coerceIn(0, 2)
        val stateLabel = when (cleanLevel) {
            0 -> "关闭"
            1 -> "弱档"
            else -> "强档"
        }
        return ControlCommand(
            "320",
            """{"level":"$cleanLevel"}""",
            "方向盘加热$stateLabel"
        )
    }

    /** 后视镜加热（cmdid=440）：enabled=true -> "2"(开), enabled=false -> "1"(关) */
    fun buildRearviewMirrorHeating(enabled: Boolean): ControlCommand {
        val value = if (enabled) "2" else "1"
        return ControlCommand(
            "440",
            """{"value":"$value"}""",
            if (enabled) "开启后视镜加热" else "关闭后视镜加热"
        )
    }

    const val FRIDGE_DEFAULT_TEMP = 4
    const val FRIDGE_HOT_TEMP = 50

    /** 车载冰箱控制（cmdid=500）：开关/模式/温度/风格/离车运行 */
    fun buildFridgeControl(command: FridgeControlCommand): ControlCommand {
        val temp = if (command.mode == FridgeMode.HOT) FRIDGE_HOT_TEMP else command.temp.coerceIn(-6, 15)
        val enableInt = if (command.enable) 1 else 0
        val parkEnableInt = if (command.parkEnable) 1 else 0
        val duration = command.durationSeconds.coerceAtLeast(1800)
        val stateJson = org.json.JSONObject().apply {
            put("cycles", if (command.cycles == "2") "2" else "1")
            put("duration", duration)
            put("enable", enableInt)
            put("mode", command.mode.raw)
            put("parkEnable", parkEnableInt)
            put("style", command.style.raw)
            put("temp", temp)
            put("value", command.value)
        }
        val actionLabel = if (!command.enable) {
            "关闭车载冰箱"
        } else {
            val modeText = if (command.mode == FridgeMode.HOT) "制热 50°C" else "制冷 ${temp}°C"
            val styleText = if (command.style == FridgeStyle.TURBO) "急速" else "标准"
            "开启车载冰箱（$modeText $styleText）"
        }
        return ControlCommand(
            "500",
            stateJson.toString(),
            actionLabel
        )
    }

    /** 快捷空调：按自定义温度下发（≤26°C 制冷，≥27°C 制热）。 */
    fun buildAc(temperature: Int): ControlCommand {
        require(isSupportedAcTemperature(temperature)) { "空调温度需在16-32°C" }
        val mode = if (temperature <= 26) "cold" else "hot"
        return ControlCommand(
            "170",
            """{"operate":"manual","temperature":"$temperature","windlevel":"7","mode":"$mode","circle":"in","wshld":"0","position":"all"}""",
            "开空调（$temperature°C）"
        )
    }

    fun climateExpectation(name: String): ClimateTelemetryExpectation? = when (name) {
        "acOn" -> ClimateTelemetryExpectation(
            acSwitch = true,
            temperatureC = DEFAULT_AC_TEMPERATURE,
            windLevel = 3,
            circle = AirCircle.INNER,
            windshieldDefogging = false
        )
        "acOff" -> ClimateTelemetryExpectation(acSwitch = false)
        "defrost" -> ClimateTelemetryExpectation(
            acSwitch = true,
            temperatureC = DEFAULT_AC_TEMPERATURE,
            windLevel = 5,
            circle = AirCircle.OUTER,
            windshieldDefogging = true
        )
        "quickCool" -> ClimateTelemetryExpectation(
            acSwitch = true,
            temperatureC = 18,
            windLevel = 7,
            circle = AirCircle.INNER,
            windshieldDefogging = false
        )
        "quickHeat" -> ClimateTelemetryExpectation(
            acSwitch = true,
            temperatureC = 32,
            windLevel = 7,
            circle = AirCircle.INNER,
            windshieldDefogging = false
        )
        "deodorize" -> ClimateTelemetryExpectation(
            acSwitch = true,
            temperatureC = DEFAULT_AC_TEMPERATURE,
            windLevel = 7,
            circle = AirCircle.OUTER,
            windshieldDefogging = false
        )
        else -> null
    }

    fun buildDetailedAc(command: AirConditioningCommand): ControlCommand {
        require(command.temperatureC in command.capability.temperatureMinC..command.capability.temperatureMaxC) {
            "空调温度超出当前车型能力范围"
        }
        require(command.windLevel >= 0) { "空调风量不能小于0" }
        command.capability.fanMin?.let { require(command.windLevel >= it) { "空调风量低于当前车型能力范围" } }
        command.capability.fanMax?.let { require(command.windLevel <= it) { "空调风量超出当前车型能力范围" } }
        val (operate, mode) = when (command.operation) {
            HvacOperation.ON -> "manual" to "cold"
            HvacOperation.OFF -> "off" to "nohotcold"
            HvacOperation.FAST_COOL -> "manual" to "cold"
            HvacOperation.FAST_HEAT -> "manual" to "hot"
            HvacOperation.DEODORIZE -> "manual" to "nohotcold"
        }
        val state = JSONObject().apply {
            put("operate", operate)
            put("temperature", command.temperatureC.toString())
            put("windlevel", command.windLevel.toString())
            put("mode", mode)
            put("circle", command.circle.wireValue)
            put("wshld", if (command.windshieldDefogging) "1" else "0")
            put("position", command.outlet.wireValue)
        }
        val label = command.customLabel ?: when (command.operation) {
            HvacOperation.ON -> {
                if (command.windshieldDefogging) "前挡除霜已开启"
                else "温度已调至 ${command.temperatureC}°C"
            }
            HvacOperation.OFF -> "空调已关闭"
            HvacOperation.FAST_COOL -> "极速降温已开启"
            HvacOperation.FAST_HEAT -> "制热已开启"
            HvacOperation.DEODORIZE -> "快速除味"
        }
        return ControlCommand("170", state.toString(), label)
    }

    fun detailedAcExpectation(command: AirConditioningCommand): ClimateTelemetryExpectation =
        if (command.operation == HvacOperation.OFF) {
            ClimateTelemetryExpectation(acSwitch = false, hasUnavailableFields = true)
        } else {
            ClimateTelemetryExpectation(
                acSwitch = true,
                temperatureC = command.temperatureC,
                windLevel = command.windLevel,
                circle = command.circle,
                windshieldDefogging = command.windshieldDefogging,
                // The current verified signal model has no outlet-position readback.
                hasUnavailableFields = true
            )
        }

    fun isSupportedAcTemperature(temperature: Int): Boolean =
        temperature in MIN_AC_TEMPERATURE..MAX_AC_TEMPERATURE

    /** Returns a verified set temperature, accepting only whole-number numeric values in range. */
    fun normalizedAcTemperature(value: String?): Int? {
        val numericText = value
            ?.trim()
            ?.replace(Regex("\\s*°?\\s*C\\s*$", RegexOption.IGNORE_CASE), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val numeric = numericText.toBigDecimalOrNull()?.let { decimal ->
            decimal.stripTrailingZeros()
                .takeIf { it.scale() <= 0 }
                ?.let { whole -> runCatching { whole.intValueExact() }.getOrNull() }
        } ?: return null
        return numeric.takeIf(::isSupportedAcTemperature)
    }

    fun acTemperatureTarget(value: String?): AcTemperatureTarget =
        normalizedAcTemperature(value)?.let { AcTemperatureTarget(it, confirmed = true) }
            ?: AcTemperatureTarget(DEFAULT_AC_TEMPERATURE, confirmed = false)

    fun canSubmitClimateControl(hasVehicleStatus: Boolean, commandInProgress: Boolean): Boolean =
        hasVehicleStatus && !commandInProgress
}

enum class FridgeMode(val raw: String, val label: String) {
    COLD("cold", "制冷"),
    HOT("hot", "制热");

    companion object {
        fun fromRaw(raw: String?): FridgeMode = when (raw?.lowercase()) {
            "hot", "1" -> HOT
            else -> COLD
        }
        fun fromSignal(signalValue: Int?): FridgeMode = if (signalValue == 1) HOT else COLD
    }
}

enum class FridgeStyle(val raw: String, val label: String) {
    NORMAL("normal", "标准"),
    TURBO("turbo", "急速");

    companion object {
        fun fromRaw(raw: String?): FridgeStyle = when (raw?.lowercase()) {
            "turbo", "1" -> TURBO
            else -> NORMAL
        }
        fun fromSignal(signalValue: Int?): FridgeStyle = if (signalValue == 1) TURBO else NORMAL
    }
}

data class FridgeControlCommand(
    val enable: Boolean = true,
    val mode: FridgeMode = FridgeMode.COLD,
    val temp: Int = 4,
    val style: FridgeStyle = FridgeStyle.NORMAL,
    val parkEnable: Boolean = false,
    val durationSeconds: Int = 3600,
    val cycles: String = "1",
    val value: String = "false"
)

data class FridgeStatus(
    val enabled: Boolean,
    val mode: FridgeMode = FridgeMode.COLD,
    val targetTemp: Int = Commands.FRIDGE_DEFAULT_TEMP,
    val style: FridgeStyle = FridgeStyle.NORMAL,
    val fault: Int = 0,
    val parkEnable: Boolean = false,
    val parkDurationHours: Int = 1,
    val parkCycles: Int = 0,
    val parkEndTimeEpochSeconds: Long = 0L
) {
    val isCooling: Boolean get() = enabled && mode == FridgeMode.COLD
    val isHeating: Boolean get() = enabled && mode == FridgeMode.HOT
    val isParkRunning: Boolean get() = enabled && parkEnable
}

object SentryModeControlPolicy {
    const val SUB_ACCOUNT_UNSUPPORTED_MESSAGE = "当前子账号不支持开启哨兵操作"

    fun canOperateSentry(isSharedAccount: Boolean, targetOn: Boolean): Boolean {
        if (isSharedAccount && targetOn) {
            return false
        }
        return true
    }

    fun activeLabel(currentEnabled: Boolean?): String? =
        if (currentEnabled == true) "哨兵已开" else null

    fun targetEnabled(commandName: String): Boolean? = when (commandName) {
        "sentryOn" -> true
        "sentryOff" -> false
        else -> null
    }

    fun commandName(currentEnabled: Boolean?): String =
        if (currentEnabled == true) "sentryOff" else "sentryOn"

    fun isConfirmed(targetEnabled: Boolean, telemetryEnabled: Boolean?): Boolean =
        telemetryEnabled == targetEnabled

    fun querySucceeded(result: Any?, code: Any?): Boolean {
        val value = when {
            result != null && result != JSONObject.NULL -> result.toString()
            code != null && code != JSONObject.NULL -> code.toString()
            else -> return false
        }
        return value == "0"
    }
}

object VehicleOtaPolicy {
    const val SUB_ACCOUNT_OTA_UNSUPPORTED_MESSAGE = "当前账号为授权子账号，车机 OTA 更新需车主主账号操作"
    const val SUB_ACCOUNT_OTA_HINT_TITLE = "子账号无车机 OTA 更新权限"
    const val SUB_ACCOUNT_OTA_HINT_DESC = "受整车安全法规与零跑官方云端权限管控，固件版本检测、更新日志查看与远程刷写升级仅限车主主账号可用。\n\n如需检查或升级车机系统，请使用车主手机号登录。"

    fun canOperateOta(isSharedAccount: Boolean): Boolean {
        return !isSharedAccount
    }
}

object SensitiveControlPolicy {
    const val LONG_PRESS_HOLD_DURATION_MS = 1200L
    const val SENSITIVE_ACTION_HINT = "高敏感操作请长按1.2秒开启"

    fun isSensitiveCommand(commandName: String, isClosed: Boolean = true): Boolean = when (commandName) {
        "trunk" -> isClosed
        "trunkOpen" -> true
        "frunkOpen" -> true
        else -> false
    }
}

/** Keeps user-defined quick-control ordering valid as vehicle capabilities change. */
object QuickCommandOrderPolicy {
    private const val SUNSHADE_GROUP_ID = "sunshadeGroup"
    private val legacySunshadeIds = setOf("sunshadeOpen", "sunshadeClose")

    /** Keeps the first legacy sunshade position while replacing two actions with one grouped entry. */
    fun migrateSunshadeGroup(saved: List<String>?): List<String>? {
        saved ?: return null
        var groupAdded = false
        return buildList {
            saved.forEach { id ->
                when {
                    id in legacySunshadeIds && !groupAdded -> {
                        add(SUNSHADE_GROUP_ID)
                        groupAdded = true
                    }
                    id in legacySunshadeIds || id == SUNSHADE_GROUP_ID && groupAdded -> Unit
                    else -> {
                        add(id)
                        if (id == SUNSHADE_GROUP_ID) groupAdded = true
                    }
                }
            }
        }
    }

    fun resolve(saved: List<String>?, available: List<String>): List<String> {
        val allowed = available.toSet()
        return (saved.orEmpty().filter { it in allowed } + available.filterNot { it in saved.orEmpty() })
            .distinct()
    }

    /** Maps one continuous drag to a bounded destination index. */
    fun targetIndex(
        startIndex: Int,
        dragOffsetPx: Float,
        rowExtentPx: Float,
        itemCount: Int
    ): Int {
        if (itemCount <= 0) return 0
        val safeStart = startIndex.coerceIn(0, itemCount - 1)
        if (!dragOffsetPx.isFinite() || !rowExtentPx.isFinite() || rowExtentPx <= 0f) {
            return safeStart
        }
        val displacement = (dragOffsetPx / rowExtentPx).roundToInt()
        return (safeStart + displacement).coerceIn(0, itemCount - 1)
    }

    fun move(order: List<String>, from: Int, to: Int): List<String> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }
}

object QuickCommandExecutionPolicy {
    fun isCommandInProgress(commandName: String, activeCmd: String?): Boolean {
        if (activeCmd.isNullOrBlank()) return false
        return when (commandName) {
            "unlock" -> activeCmd == "unlock"
            "lock" -> activeCmd == "lock"
            "trunk" -> activeCmd == "trunkOpen" || activeCmd == "trunkClose"
            "windowGroup" -> activeCmd.startsWith("window")
            "sunshadeGroup" -> activeCmd.startsWith("sunshade")
            "sentry" -> activeCmd.startsWith("sentry")
            "fridge" -> activeCmd.startsWith("fridge")
            else -> activeCmd == commandName
        }
    }
}

object OperationPasswordErrorPolicy {
    const val ERROR_PROMPT_MESSAGE = "密码错误，请更新密码"

    fun isPasswordError(error: Throwable?): Boolean {
        val raw = error?.message.orEmpty().lowercase()
        if (raw.contains("长度不足") || raw.contains("缺少用于加密")) return false
        return raw.contains("密码错误") ||
               raw.contains("密码不正确") ||
               raw.contains("密码校验失败") ||
               raw.contains("操作密码") ||
               raw.contains("oppwd") ||
               raw.contains("password error") ||
               raw.contains("invalid password") ||
               raw.contains("wrong password") ||
               (raw.contains("密码") && (raw.contains("错") || raw.contains("不对") || raw.contains("失效") || raw.contains("失败") || raw.contains("重试")))
    }
}

object PostLoginPinSetupPolicy {
    fun shouldPromptPinSetup(isNewLogin: Boolean, pinSaved: Boolean): Boolean {
        return isNewLogin && !pinSaved
    }
}

object RearSeatComfortPolicy {
    /**
     * 判定指定车型是否支持第二排独立座椅加热与通风。
     * 仅具备独立二排座椅特性的车型（如 C16 6座车型）支持；
     * C11、C10、C01（5座）、T03（4座）等车型硬件不支持，界面自适应隐藏。
     */
    fun supportsRearSeats(carType: String?): Boolean {
        val model = carType?.trim().orEmpty()
        if (model.isBlank()) return false
        return model.contains("C16", ignoreCase = true) ||
            model.contains("6座") ||
            model.contains("7座") ||
            model.contains("6-seat", ignoreCase = true)
    }
}

object VehicleDrivingSafetyPolicy {
    const val DRIVING_OPERATION_PROHIBITED_HINT = "为了您的安全，请停车后在操作"

    fun isDrivingGear(gearStatus: String?): Boolean {
        val gear = gearStatus?.trim()?.uppercase() ?: return false
        return gear in setOf("D", "D挡", "DRIVE", "前进", "3", "R", "R挡", "REVERSE", "倒车", "1")
    }
}

/** 驻车实景环视照片信息 */
data class ChassisParkingPhoto(
    val fileUrl: String,
    val uploadTimeMs: Long
) {
    val secureUrl: String
        get() = fileUrl.replaceFirst("http://", "https://", ignoreCase = true)
}

sealed interface ParkingPhotoLoadState {
    data object Idle : ParkingPhotoLoadState
    data object Loading : ParkingPhotoLoadState
    data class Success(val bitmap: android.graphics.Bitmap) : ParkingPhotoLoadState
    data object Empty : ParkingPhotoLoadState
    data class Failed(val message: String) : ParkingPhotoLoadState
}

/** 整车 FOTA 固件升级状态枚举 */
enum class OtaStatus {
    UP_TO_DATE,        // 已是最新版本
    UPDATE_AVAILABLE,  // 发现新版本（待下载）
    DOWNLOADING,       // 正在下载固件包
    DOWNLOADED,        // 固件包已下载（待安装）
    INSTALLING,        // 正在刷写安装
    SCHEDULED,         // 已预约定时安装
    FAILED,            // 升级/下载失败
    UNKNOWN            // 未知
}

/** 车辆车机系统与 OTA 固件升级详情数据模型 */
data class VehicleOtaInfo(
    val currentVersion: String = "--",
    val hasNewVersion: Boolean = false,
    val newVersion: String = "",
    val releaseNotes: String = "",
    val updateTime: String = "",
    val taskId: String = "",
    val packageSize: String = "",
    val status: OtaStatus = OtaStatus.UP_TO_DATE,
    val progressPercent: Int? = null,
    val scheduledTime: String? = null,
    val rawJson: JSONObject? = null
) {
    val isUpToDate: Boolean get() = status == OtaStatus.UP_TO_DATE || (!hasNewVersion && status != OtaStatus.DOWNLOADING && status != OtaStatus.DOWNLOADED)
    val isDownloading: Boolean get() = status == OtaStatus.DOWNLOADING
    val isDownloaded: Boolean get() = status == OtaStatus.DOWNLOADED
    val isInstalling: Boolean get() = status == OtaStatus.INSTALLING
    val isScheduled: Boolean get() = status == OtaStatus.SCHEDULED || !scheduledTime.isNullOrBlank()

    companion object {
        fun fromJson(data: JSONObject?, code: String? = null, msg: String? = null): VehicleOtaInfo {
            if (data == null || data.length() == 0) {
                return VehicleOtaInfo(
                    currentVersion = "最新系统",
                    hasNewVersion = false,
                    status = OtaStatus.UP_TO_DATE,
                    releaseNotes = msg.orEmpty()
                )
            }
            val current = data.optString("versionNo")
                .ifBlank { data.optString("currentVersion") }
                .ifBlank { data.optString("curVersion") }
                .ifBlank { data.optString("version") }
                .ifBlank { data.optString("currentVer") }
                .ifBlank { "最新系统" }
            val next = data.optString("targetVersion")
                .ifBlank { data.optString("newVersion") }
                .ifBlank { data.optString("nextVersion") }
                .ifBlank { data.optString("upgradeVersion") }
                .ifBlank { data.optString("updateVersion") }
            val notes = data.optString("logContent")
                .ifBlank { data.optString("releaseNotes") }
                .ifBlank { data.optString("description") }
                .ifBlank { data.optString("updateContent") }
                .ifBlank { data.optString("content") }
                .ifBlank { data.optString("remark") }
            val updateTime = data.optString("updateTime")
                .ifBlank { data.optString("releaseDate") }
            val taskId = data.optString("taskId")
                .ifBlank { data.optString("fotaTaskId") }
                .ifBlank { data.optString("id") }
            val rawSize = data.optLong("fileSize", 0L).takeIf { it > 0 }
                ?: data.optLong("packageSize", 0L).takeIf { it > 0 }
                ?: data.optLong("size", 0L).takeIf { it > 0 }
            val sizeStr = when {
                rawSize != null && rawSize > 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", rawSize.toDouble() / (1024L * 1024L * 1024L))
                rawSize != null && rawSize > 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", rawSize.toDouble() / (1024L * 1024L))
                else -> data.optString("sizeStr").ifBlank { data.optString("packageSizeStr") }
            }
            val rawStatus = data.optInt("status", -1).takeIf { it != -1 }
                ?: data.optInt("state", -1).takeIf { it != -1 }
                ?: data.optInt("downloadStatus", -1).takeIf { it != -1 }
                ?: data.optInt("upgradeStatus", -1)
            val progress = data.optInt("progress", -1).takeIf { it >= 0 }
                ?: data.optInt("downloadProgress", -1).takeIf { it >= 0 }
                ?: data.optInt("percent", -1).takeIf { it >= 0 }
            val hasUpdate = data.optBoolean("hasNewVersion", false) ||
                data.optBoolean("hasUpdate", false) ||
                (next.isNotBlank() && next != current && next != "--")

            val statusEnum = when {
                rawStatus == 2 || (progress != null && progress in 1..99) -> OtaStatus.DOWNLOADING
                rawStatus == 3 || progress == 100 -> OtaStatus.DOWNLOADED
                rawStatus == 4 -> OtaStatus.INSTALLING
                rawStatus == 6 -> OtaStatus.FAILED
                hasUpdate -> OtaStatus.UPDATE_AVAILABLE
                else -> OtaStatus.UP_TO_DATE
            }

            return VehicleOtaInfo(
                currentVersion = current,
                hasNewVersion = hasUpdate,
                newVersion = next,
                releaseNotes = notes,
                updateTime = updateTime,
                taskId = taskId,
                packageSize = sizeStr,
                status = statusEnum,
                progressPercent = progress,
                scheduledTime = data.optString("scheduleTime").ifBlank { data.optString("appointmentTime") }.takeIf { it.isNotBlank() },
                rawJson = data
            )
        }
    }
}

/** 界面展示态 */
sealed interface VehicleOtaState {
    data object Idle : VehicleOtaState
    data object Checking : VehicleOtaState
    data class Success(val info: VehicleOtaInfo) : VehicleOtaState
    data class Error(val message: String) : VehicleOtaState
}

