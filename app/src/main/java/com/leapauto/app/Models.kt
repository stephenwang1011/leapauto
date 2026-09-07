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
    val powerType: SessionStore.VehiclePowerType = SessionStore.VehiclePowerType.PURE_ELECTRIC
)

data class VehiclePictureMeta(
    val pictureKey: String,
    val shareBindUrl: String,
    val sourceUrl: String = "",
    val rawData: JSONObject? = null
)

/** 登录会话（内存态），由 SessionStore 持久化。 */
class Session(
    var deviceId: String,
    var phone: String = "",
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
    val appVersion = "1.22.87"
    val subVersion = "3.19.2-2"
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
    val outlet: AirOutlet
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
    const val SUCCESS_DURATION_MS = 3_000L
    const val WARNING_DURATION_MS = 4_000L
    const val ERROR_DURATION_MS = 4_000L

    fun autoDismissDelayMs(kind: ControlFeedbackKind): Long? = when (kind) {
        ControlFeedbackKind.IN_PROGRESS -> null
        ControlFeedbackKind.SUBMITTED -> SUBMITTED_DURATION_MS
        ControlFeedbackKind.SUCCESS -> SUCCESS_DURATION_MS
        ControlFeedbackKind.WARNING -> WARNING_DURATION_MS
        ControlFeedbackKind.ERROR -> ERROR_DURATION_MS
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
    val windshieldDefrost: Boolean? = null
)

data class ClimateTelemetrySnapshot(
    val acSwitch: Boolean?,
    val acSetting: String?,
    val windshieldDefrost: Boolean?
)

/**
 * Keeps the optimistic surface limited to values explicitly carried by existing commands.
 * Telemetry remains the source of truth and replaces these values during the next refresh.
 */
object ClimateOptimisticUpdates {
    fun acOn() = ClimateOptimisticUpdate(
        acSwitch = true,
        acSetting = "${Commands.DEFAULT_AC_TEMPERATURE} °C"
    )

    fun acOff() = ClimateOptimisticUpdate(acSwitch = false)

    fun temperature(value: Int): ClimateOptimisticUpdate {
        require(Commands.isSupportedAcTemperature(value))
        return ClimateOptimisticUpdate(acSwitch = true, acSetting = "$value °C")
    }

    fun windshieldDefrost() = ClimateOptimisticUpdate(
        acSetting = "${Commands.DEFAULT_AC_TEMPERATURE} °C",
        windshieldDefrost = true
    )

    fun mergeOver(update: ClimateOptimisticUpdate, telemetry: ClimateTelemetrySnapshot): ClimateTelemetrySnapshot =
        telemetry.copy(
            acSwitch = update.acSwitch ?: telemetry.acSwitch,
            acSetting = update.acSetting ?: telemetry.acSetting,
            windshieldDefrost = update.windshieldDefrost ?: telemetry.windshieldDefrost
        )
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

    fun failed(label: String): String = copyFor(label).failed

    private fun copyFor(label: String): Copy = when {
        label == "关空调" || label == "关闭空调" -> Copy(
            sending = "空调关闭中",
            submitted = "空调关闭已提交",
            confirmed = "空调已关闭",
            failed = "空调关闭失败"
        )
        label.startsWith("开空调") -> Copy(
            sending = "空调开启中",
            submitted = "空调开启已提交",
            confirmed = "空调已开启",
            failed = "空调开启失败"
        )
        label == "极速降温" -> Copy("降温中", "降温已提交", "降温已开启", "降温失败")
        label == "极速升温" -> Copy("升温中", "升温已提交", "升温已开启", "升温失败")
        label == "开启除雾" -> Copy("除雾中", "除雾已提交", "除雾已开启", "除雾失败")
        label == "快速除味" -> Copy("除味中", "除味已提交", "除味已开启", "除味失败")
        else -> Copy("空调设置中", "空调设置已提交", "空调已更新", "空调设置失败")
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
        """{"operate":"manual","temperature":"24","windlevel":"3","mode":"cold","circle":"in","wshld":"0","position":"all"}"""
    const val AC_OFF_STATE =
        """{"operate":"off","temperature":"24","windlevel":"3","mode":"nohotcold","circle":"out","wshld":"0","position":"all"}"""
    const val QUICK_COOL_STATE =
        """{"operate":"manual","temperature":"18","windlevel":"7","mode":"cold","circle":"in","wshld":"0","position":"all"}"""
    const val QUICK_HEAT_STATE =
        """{"operate":"manual","temperature":"32","windlevel":"7","mode":"hot","circle":"in","wshld":"0","position":"all"}"""
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
        else -> throw ApiException("未知命令: $name")
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
        val label = when (command.operation) {
            HvacOperation.ON -> "应用空调设置"
            HvacOperation.OFF -> "关闭空调"
            HvacOperation.FAST_COOL -> "极速降温"
            HvacOperation.FAST_HEAT -> "极速升温"
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

object SentryModeControlPolicy {
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
