package com.leapauto.app

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** 车型配置下拉选项，使用短车型名便于用户识别和维护。 */
val SUPPORTED_VEHICLE_MODELS = listOf(
    "C01", "B01", "T03", "C11", "C16", "D99", "D19", "B05", "A05",
    "B10", "C10", "A10", "Lafa5"
)

/** 会话与操作密码的本地持久化（应用私有存储）。 */
class SessionStore(context: Context) {

    enum class VehiclePowerType { PURE_ELECTRIC, RANGE_EXTENDER }

    /** 用户确认的车型配置，按 VIN 隔离保存；未设置车型时由调用方回填会话 cartype。 */
    data class VehicleConfig(
        val modelYear: String = "",
        val powerType: VehiclePowerType? = null,
        val model: String = "",
        val color: String = "",
        val nickname: String = ""
    )

    data class WidgetSnapshot(
        val vin: String,
        val carType: String,
        val range: String,
        val soc: Int,
        val fuelSoc: Int? = null,
        val updated: String,
        val powerType: VehiclePowerType? = null,
        val electricRange: String? = null,
        val fuelRange: String? = null,
        val electricTotalRange: String? = null,
        val fuelTotalRange: String? = null,
        val statusLabel: String = "",
        val locked: Boolean? = null,
        val acEnabled: Boolean? = null,
        val acTone: ClimateTemperatureTone = ClimateTemperatureTone.DEFAULT,
        val chargingPower: String? = null,
        val chargeState: Int? = null,
        val chargeRemainTime: String? = null,
        val capturedAt: Long = 0L,
        val lastSuccessAt: Long = 0L,
        val sessionGeneration: Long = 0L,
        val driving: Boolean? = null,
        val trunkState: TrunkState = TrunkState.UNKNOWN,
        val sentryEnabled: Boolean? = null
    )

    enum class WidgetAccess { NO_SESSION, CONTROL }

    /**
     * Widget access is governed by the current app session, not the age of the
     * last successful snapshot. A parked vehicle may legitimately remain still
     * for hours; the next scheduled refresh is responsible for updating it.
     */
    @Suppress("UNUSED_PARAMETER")
    fun widgetAccess(snapshot: WidgetSnapshot?, session: Session, now: Long = System.currentTimeMillis()): WidgetAccess {
        if (session.oldAuth == null || session.newAuth == null || session.selectedVin.isBlank()) return WidgetAccess.NO_SESSION
        // Refresh age is intentionally not a gate, but a widget without a
        // snapshot for the current vehicle/session must never send a command.
        if (snapshot == null || snapshot.vin != session.selectedVin || snapshot.sessionGeneration != session.generation) {
            return WidgetAccess.NO_SESSION
        }
        if (prefs.getBoolean(WIDGET_AUTH_INVALID, false)) return WidgetAccess.NO_SESSION
        return WidgetAccess.CONTROL
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("leap_session", Context.MODE_PRIVATE)
    private val appPrefs = appContext.getSharedPreferences("leap_app_preferences", Context.MODE_PRIVATE)
    private val secureValues = SecureValueStore(context, prefs)

    fun load(): Session = synchronized(SESSION_LOCK) {
        val s = Session(
            deviceId = prefs.getString("deviceId", "")?.takeIf { it.isNotBlank() }
                ?: Crypto.randomDeviceId(),
            generation = prefs.getLong(SESSION_GENERATION, 0L)
        )
        s.phone = secureValues.getString("phone") ?: ""
        secureValues.getString("oldAuth")?.takeIf { it.isNotBlank() }?.let {
            s.oldAuth = OldAuth.fromJson(JSONObject(it))
        }
        secureValues.getString("newAuth")?.takeIf { it.isNotBlank() }?.let {
            s.newAuth = NewAuth.fromJson(JSONObject(it))
        }
        s.selectedVin = prefs.getString("vin", "") ?: ""
        s.selectedCarType = prefs.getString("carType", "") ?: ""
        s.selectedNickname = prefs.getString("nickname", "") ?: ""
        s.selectedYear = prefs.getString("year", "") ?: ""
        prefs.getString("route", null)?.takeIf { it.isNotBlank() }?.let {
            s.route = RouteData.fromJson(JSONObject(it))
        }
        prefs.getString("hvacCapability", null)?.takeIf { it.isNotBlank() }?.let {
            s.hvacCapability = HvacCapability.fromJson(JSONObject(it))
        }
        s
    }

    /** Returns false when this session predates a logout and must not restore credentials. */
    fun save(s: Session): Boolean = synchronized(SESSION_LOCK) {
        val currentGen = prefs.getLong(SESSION_GENERATION, 0L)
        if (s.oldAuth == null && s.generation != currentGen) return false
        val editor = prefs.edit()
            .putLong(SESSION_GENERATION, currentGen)
            .putString("deviceId", s.deviceId)
        secureValues.putString(editor, "phone", s.phone)
        secureValues.putString(editor, "oldAuth", s.oldAuth?.toJson()?.toString() ?: "")
        secureValues.putString(editor, "newAuth", s.newAuth?.toJson()?.toString() ?: "")
        editor
            .putString("vin", s.selectedVin)
            .putString("carType", s.selectedCarType)
            .putString("nickname", s.selectedNickname)
            .putString("year", s.selectedYear)
            .putString("route", s.route?.toJson()?.toString() ?: "")
            .putString("hvacCapability", s.hvacCapability.toJson().toString())
            .commit()
        true
    }

    fun clear() = synchronized(SESSION_LOCK) {
        val nextGeneration = prefs.getLong(SESSION_GENERATION, 0L) + 1L
        prefs.edit().clear().putLong(SESSION_GENERATION, nextGeneration).commit()
    }

    fun loadOpPassword(): String? = secureValues.getString("op_password")?.takeIf { it.matches(Regex("\\d{4}")) }

    fun saveOpPassword(password: String) {
        require(password.isBlank() || password.matches(Regex("\\d{4}"))) { "操作密码必须为 4 位数字" }
        secureValues.putString(prefs.edit(), "op_password", password).commit()
    }

    /** 快捷空调默认温度（本地缓存偏好，leap-design.md §2 痛点2）。 */
    fun loadAcTemp(): Int = prefs.getInt("ac_temp", 24)

    fun saveAcTemp(temperature: Int) {
        prefs.edit().putInt("ac_temp", temperature).apply()
    }

    /** 健康充电目标上限百分比（按 VIN 隔离存储，默认 80%）。 */
    fun loadHealthyChargeLimit(vin: String): Int {
        if (vin.isBlank()) return 80
        val key = "healthy_charge_limit_$vin"
        return prefs.getInt(key, 80)
    }

    fun saveHealthyChargeLimit(vin: String, soc: Int) {
        if (vin.isBlank()) return
        val key = "healthy_charge_limit_$vin"
        prefs.edit().putInt(key, soc.coerceIn(50, 100)).apply()
    }

    /** 用户确认的车型配置，按 VIN 隔离保存，避免多车续航规则串用。 */
    fun loadVehicleConfig(
        vin: String,
        defaultModel: String = "",
        defaultNickname: String = "",
        defaultYear: String = "",
        defaultPowerType: VehiclePowerType? = null
    ): VehicleConfig {
        if (vin.isBlank()) return VehicleConfig()
        val prefix = VehicleConfigStorageKeys.prefix(vin) ?: return VehicleConfig()
        val storedType = when (appPrefs.getString(prefix + "power_type", null)) {
            "pure_electric" -> VehiclePowerType.PURE_ELECTRIC
            "range_extender" -> VehiclePowerType.RANGE_EXTENDER
            else -> null
        }
        val type = storedType ?: defaultPowerType
        val storedModel = appPrefs.getString(prefix + "model", "")?.trim().orEmpty()
        val selectedModel = storedModel.ifBlank { defaultModel.trim() }
        val storedColor = appPrefs.getString(prefix + "color", "")?.trim().orEmpty()
        val storedYear = appPrefs.getString(prefix + "model_year", "")?.trim().orEmpty()
        val selectedYear = storedYear.ifBlank { defaultYear.trim() }
        val storedNickname = appPrefs.getString(prefix + "nickname", "")?.trim().orEmpty()
        val selectedNickname = if (storedNickname.isBlank() || storedNickname.equals(selectedModel, ignoreCase = true) || storedNickname.equals(defaultModel, ignoreCase = true)) {
            defaultNickname.trim().ifBlank { storedNickname }
        } else {
            storedNickname
        }
        return VehicleConfig(
            modelYear = selectedYear,
            powerType = type,
            model = selectedModel,
            color = VehicleAppearanceCatalog.normalizeColor(selectedModel, storedColor).orEmpty(),
            nickname = selectedNickname
        )
    }

    fun saveVehicleConfig(vin: String, config: VehicleConfig) {
        if (vin.isBlank()) return
        val prefix = VehicleConfigStorageKeys.prefix(vin) ?: return
        val normalizedModel = VehicleConfigConfirmationPolicy.normalizeModel(config.model)
        val normalizedColor = VehicleAppearanceCatalog.normalizeColor(normalizedModel, config.color)
        val confirmationSchema = VehicleConfigConfirmationPolicy.schemaToPersist(
            model = normalizedModel.orEmpty(),
            modelYear = config.modelYear,
            powerType = config.powerType,
            color = normalizedColor
        )
        appPrefs.edit()
            .putString(prefix + "model_year", config.modelYear.trim())
            .putString(prefix + "model", normalizedModel ?: config.model.trim())
            .putString(prefix + "nickname", config.nickname.trim())
            .apply {
                if (normalizedColor == null) remove(prefix + "color")
                else putString(prefix + "color", normalizedColor)
            }
            .apply {
                if (config.powerType == null) remove(prefix + "power_type")
                else putString(prefix + "power_type", if (config.powerType == VehiclePowerType.PURE_ELECTRIC) "pure_electric" else "range_extender")
                confirmationSchema?.let {
                    VehicleConfigConfirmationPolicy.confirmationPreferenceKey(vin)?.let { key ->
                        putInt(key, it)
                    }
                } ?: VehicleConfigConfirmationPolicy.confirmationPreferenceKey(vin)?.let { key ->
                    remove(key)
                }
            }
            .apply()
    }

    fun isVehicleConfigConfirmed(vin: String): Boolean {
        val key = VehicleConfigConfirmationPolicy.confirmationPreferenceKey(vin) ?: return false
        val storedSchemaVersion = appPrefs.getInt(key, 0)
        if (!VehicleConfigConfirmationPolicy.isConfirmed(storedSchemaVersion)) return false
        val config = loadVehicleConfig(vin)
        return VehicleConfigConfirmationPolicy.isValid(
            model = config.model,
            modelYear = config.modelYear,
            powerType = config.powerType,
            color = config.color
        )
    }

    fun loadWidgetOpacity(): Int =
        prefs.getInt("widget_opacity", WIDGET_OPACITY_OPAQUE).takeIf { it in WIDGET_OPACITY_OPTIONS }
            ?: WIDGET_OPACITY_OPAQUE

    fun saveWidgetOpacity(opacity: Int) {
        require(opacity in WIDGET_OPACITY_OPTIONS) { "不支持的小组件透明度" }
        prefs.edit().putInt("widget_opacity", opacity).apply()
    }

    /** Shared with 我的: missing preference remains opt-in for verification by default. */
    fun loadWidgetSensitiveActionVerificationEnabled(): Boolean =
        appPrefs.getBoolean(
            WIDGET_SENSITIVE_ACTION_VERIFICATION_ENABLED,
            WidgetControlSecurity.DEFAULT_VERIFICATION_ENABLED
        )

    /** Refreshes widget PendingIntents immediately after the 我的 setting changes. */
    fun saveWidgetSensitiveActionVerificationEnabled(enabled: Boolean) {
        appPrefs.edit().putBoolean(WIDGET_SENSITIVE_ACTION_VERIFICATION_ENABLED, enabled).apply()
        ControlWidget.refreshData(appContext)
    }

    /** 最近一次成功同步的小组件数据，用于网络短暂失败时保留可读状态。 */
    fun saveWidgetSnapshot(vin: String, carType: String, range: String, soc: Int, updated: String) {
        saveWidgetSnapshot(
            vin = vin,
            carType = carType,
            range = range,
            soc = soc,
            updated = updated,
            statusLabel = "",
            locked = null,
            acEnabled = null,
            chargingPower = null,
            capturedAt = 0L,
            lastSuccessAt = 0L,
            sessionGeneration = load().generation
        )
    }

    fun saveWidgetSnapshot(
        vin: String,
        carType: String,
        range: String,
        soc: Int,
        fuelSoc: Int? = null,
        updated: String,
        powerType: VehiclePowerType? = null,
        electricRange: String? = null,
        fuelRange: String? = null,
        electricTotalRange: String? = null,
        fuelTotalRange: String? = null,
        statusLabel: String,
        locked: Boolean?,
        acEnabled: Boolean?,
        acTone: ClimateTemperatureTone = ClimateTemperatureTone.DEFAULT,
        chargingPower: String?,
        chargeState: Int? = null,
        chargeRemainTime: String? = null,
        capturedAt: Long = System.currentTimeMillis(),
        lastSuccessAt: Long = capturedAt,
        sessionGeneration: Long = load().generation,
        driving: Boolean? = null,
        trunkState: TrunkState? = null,
        sentryEnabled: Boolean? = null
    ) {
        val previousTrunkState = if (vin == prefs.getString("widget_snapshot_vin", "") &&
            prefs.contains("widget_snapshot_trunk_open")
        ) {
            TrunkState.fromPersisted(prefs.getBoolean("widget_snapshot_trunk_open", false))
        } else {
            TrunkState.UNKNOWN
        }
        val storedTrunkState = trunkState ?: previousTrunkState
        prefs.edit()
            .putString("widget_snapshot_vin", vin)
            .putString("widget_snapshot_car_type", carType)
            .putString("widget_snapshot_range", range.trim().removeSuffix("km").trim())
            .putInt("widget_snapshot_soc", soc)
            .apply {
                if (fuelSoc == null) remove("widget_snapshot_fuel_soc")
                else putInt("widget_snapshot_fuel_soc", fuelSoc.coerceIn(0, 100))
            }
            .putString("widget_snapshot_updated", updated)
            .apply {
                if (powerType == null) remove("widget_snapshot_power_type")
                else putString("widget_snapshot_power_type", powerType.name)
            }
            .apply {
                if (electricRange == null) remove("widget_snapshot_electric_range")
                else putString("widget_snapshot_electric_range", electricRange.trim().removeSuffix("km").trim())
            }
            .apply {
                if (fuelRange == null) remove("widget_snapshot_fuel_range")
                else putString("widget_snapshot_fuel_range", fuelRange.trim().removeSuffix("km").trim())
            }
            .apply {
                if (electricTotalRange == null) remove("widget_snapshot_electric_total_range")
                else putString("widget_snapshot_electric_total_range", electricTotalRange.trim().removeSuffix("km").trim())
            }
            .apply {
                if (fuelTotalRange == null) remove("widget_snapshot_fuel_total_range")
                else putString("widget_snapshot_fuel_total_range", fuelTotalRange.trim().removeSuffix("km").trim())
            }
            .putString("widget_snapshot_status_label", statusLabel)
            .apply {
                if (locked == null) remove("widget_snapshot_locked")
                else putBoolean("widget_snapshot_locked", locked)
            }
            .apply {
                if (acEnabled == null) remove("widget_snapshot_ac_enabled")
                else putBoolean("widget_snapshot_ac_enabled", acEnabled)
            }
            .apply {
                if (acTone == ClimateTemperatureTone.DEFAULT) remove("widget_snapshot_ac_tone")
                else putString("widget_snapshot_ac_tone", acTone.name)
            }
            .putString("widget_snapshot_charging_power", chargingPower ?: "")
            .apply {
                if (chargeState == null) remove("widget_snapshot_charge_state")
                else putInt("widget_snapshot_charge_state", chargeState)
            }
            .apply {
                if (chargeRemainTime.isNullOrBlank()) remove("widget_snapshot_charge_remain_time")
                else putString("widget_snapshot_charge_remain_time", chargeRemainTime)
            }
            .putLong("widget_snapshot_captured_at", capturedAt)
            .putLong("widget_snapshot_last_success_at", lastSuccessAt)
            .putLong("widget_snapshot_generation", sessionGeneration)
            .apply { if (driving == null) remove("widget_snapshot_driving") else putBoolean("widget_snapshot_driving", driving) }
            .apply {
                if (storedTrunkState == TrunkState.UNKNOWN) remove("widget_snapshot_trunk_open")
                else putBoolean("widget_snapshot_trunk_open", storedTrunkState == TrunkState.OPEN)
            }
            .apply {
                if (sentryEnabled == null) remove("widget_snapshot_sentry_enabled")
                else putBoolean("widget_snapshot_sentry_enabled", sentryEnabled)
            }
            .remove(WIDGET_AUTH_INVALID)
            .apply()
    }

    fun loadWidgetSnapshot(vin: String): WidgetSnapshot? {
        if (vin.isBlank() || prefs.getString("widget_snapshot_vin", "") != vin) return null
        val range = prefs.getString("widget_snapshot_range", null)
            ?.trim()
            ?.removeSuffix("km")
            ?.trim()
            ?.removeSuffix("k")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return WidgetSnapshot(
            vin = vin,
            carType = prefs.getString("widget_snapshot_car_type", "") ?: "",
            range = range,
            soc = prefs.getInt("widget_snapshot_soc", 0).coerceIn(0, 100),
            fuelSoc = if (prefs.contains("widget_snapshot_fuel_soc")) {
                prefs.getInt("widget_snapshot_fuel_soc", 0).coerceIn(0, 100)
            } else {
                null
            },
            updated = prefs.getString("widget_snapshot_updated", "") ?: "",
            powerType = prefs.getString("widget_snapshot_power_type", null)?.let {
                runCatching { VehiclePowerType.valueOf(it) }.getOrNull()
            },
            electricRange = prefs.getString("widget_snapshot_electric_range", null),
            fuelRange = prefs.getString("widget_snapshot_fuel_range", null),
            electricTotalRange = prefs.getString("widget_snapshot_electric_total_range", null),
            fuelTotalRange = prefs.getString("widget_snapshot_fuel_total_range", null),
            statusLabel = prefs.getString("widget_snapshot_status_label", "") ?: "",
            locked = if (prefs.contains("widget_snapshot_locked")) {
                prefs.getBoolean("widget_snapshot_locked", false)
            } else {
                null
            },
            acEnabled = if (prefs.contains("widget_snapshot_ac_enabled")) {
                prefs.getBoolean("widget_snapshot_ac_enabled", false)
            } else {
                null
            },
            acTone = prefs.getString("widget_snapshot_ac_tone", null)?.let {
                runCatching { ClimateTemperatureTone.valueOf(it) }.getOrNull()
            } ?: ClimateTemperatureTone.DEFAULT,
            chargingPower = prefs.getString("widget_snapshot_charging_power", "")
                ?.takeIf { it.isNotBlank() },
            chargeState = if (prefs.contains("widget_snapshot_charge_state")) {
                prefs.getInt("widget_snapshot_charge_state", 0)
            } else {
                null
            },
            chargeRemainTime = prefs.getString("widget_snapshot_charge_remain_time", null)
                ?.takeIf { it.isNotBlank() },
            capturedAt = prefs.getLong("widget_snapshot_captured_at", 0L),
            lastSuccessAt = prefs.getLong("widget_snapshot_last_success_at", 0L),
            sessionGeneration = prefs.getLong("widget_snapshot_generation", 0L)
            ,driving = if (prefs.contains("widget_snapshot_driving")) {
                prefs.getBoolean("widget_snapshot_driving", false)
            } else {
                null
            },
            trunkState = if (prefs.contains("widget_snapshot_trunk_open")) {
                TrunkState.fromPersisted(prefs.getBoolean("widget_snapshot_trunk_open", false))
            } else {
                TrunkState.UNKNOWN
            },
            sentryEnabled = if (prefs.contains("widget_snapshot_sentry_enabled")) {
                prefs.getBoolean("widget_snapshot_sentry_enabled", false)
            } else {
                null
            }
        )
    }

    fun loadSelectedWidgetSnapshot(): WidgetSnapshot? =
        loadWidgetSnapshot(prefs.getString("vin", "") ?: "")

    /** Invalidates widget data after terminal authentication failure without logging out the app session. */
    fun invalidateWidgetSnapshot() {
        prefs.edit()
            .remove("widget_snapshot_vin")
            .remove("widget_snapshot_captured_at")
            .remove("widget_snapshot_last_success_at")
            .remove("widget_snapshot_generation")
            .remove("widget_snapshot_trunk_open")
            .putBoolean(WIDGET_AUTH_INVALID, true)
            .apply()
    }

    /** Updates only the locally cached AC state after a confirmed widget command. */
    fun updateWidgetAcState(vin: String, acEnabled: Boolean): Boolean {
        val snapshot = loadWidgetSnapshot(vin) ?: return false
        saveWidgetSnapshot(
            vin = snapshot.vin,
            carType = snapshot.carType,
            range = snapshot.range,
            soc = snapshot.soc,
            fuelSoc = snapshot.fuelSoc,
            updated = snapshot.updated,
            powerType = snapshot.powerType,
            electricRange = snapshot.electricRange,
            fuelRange = snapshot.fuelRange,
            electricTotalRange = snapshot.electricTotalRange,
            fuelTotalRange = snapshot.fuelTotalRange,
            statusLabel = snapshot.statusLabel,
            locked = snapshot.locked,
            acEnabled = acEnabled,
            chargingPower = snapshot.chargingPower,
            chargeState = snapshot.chargeState,
            chargeRemainTime = snapshot.chargeRemainTime,
            capturedAt = snapshot.capturedAt,
            lastSuccessAt = snapshot.lastSuccessAt,
            sessionGeneration = snapshot.sessionGeneration,
            driving = snapshot.driving,
            trunkState = snapshot.trunkState,
            sentryEnabled = snapshot.sentryEnabled
        )
        return true
    }

    /** Updates the complete widget snapshot after a confirmed trunk command. */
    fun updateWidgetTrunkState(vin: String, trunkState: TrunkState): Boolean {
        val snapshot = loadWidgetSnapshot(vin) ?: return false
        saveWidgetSnapshot(
            vin = snapshot.vin,
            carType = snapshot.carType,
            range = snapshot.range,
            soc = snapshot.soc,
            fuelSoc = snapshot.fuelSoc,
            updated = snapshot.updated,
            powerType = snapshot.powerType,
            electricRange = snapshot.electricRange,
            fuelRange = snapshot.fuelRange,
            electricTotalRange = snapshot.electricTotalRange,
            fuelTotalRange = snapshot.fuelTotalRange,
            statusLabel = snapshot.statusLabel,
            locked = snapshot.locked,
            acEnabled = snapshot.acEnabled,
            chargingPower = snapshot.chargingPower,
            chargeState = snapshot.chargeState,
            chargeRemainTime = snapshot.chargeRemainTime,
            capturedAt = snapshot.capturedAt,
            lastSuccessAt = snapshot.lastSuccessAt,
            sessionGeneration = snapshot.sessionGeneration,
            driving = snapshot.driving,
            trunkState = trunkState,
            sentryEnabled = snapshot.sentryEnabled
        )
        return true
    }

    /** Avoids re-enqueuing a sync when a launcher echoes our own widget render as an update broadcast. */
    fun suppressWidgetSyncFor(durationMillis: Long) {
        prefs.edit()
            .putLong(WIDGET_SYNC_SUPPRESSED_UNTIL, System.currentTimeMillis() + durationMillis)
            .apply()
    }

    fun isWidgetSyncSuppressed(): Boolean =
        System.currentTimeMillis() < prefs.getLong(WIDGET_SYNC_SUPPRESSED_UNTIL, 0L)

    /** Charging notifications are always enabled; the preference remains readable for migration. */
    fun loadChargeNotificationsEnabled(): Boolean = true

    /** Parking anomaly notifications are always enabled; the preference remains readable for migration. */
    fun loadParkingAnomalyNotificationsEnabled(): Boolean = true

    fun loadAppearanceMode(): AppearanceMode =
        AppearanceMode.fromPreference(appPrefs.getString(APPEARANCE_MODE, null))

    fun saveAppearanceMode(mode: AppearanceMode) {
        appPrefs.edit().putString(APPEARANCE_MODE, mode.name).apply()
    }

    fun loadPowerPagerAutoPlayEnabled(): Boolean =
        appPrefs.getBoolean(POWER_PAGER_AUTO_PLAY_ENABLED, false)

    fun savePowerPagerAutoPlayEnabled(enabled: Boolean) {
        appPrefs.edit().putBoolean(POWER_PAGER_AUTO_PLAY_ENABLED, enabled).apply()
    }

    fun loadQuickCommandOrder(vin: String): List<String>? =
        vin.trim().takeIf { it.isNotEmpty() }
            ?.let { appPrefs.getString(quickCommandOrderKey(it), null) }
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.takeIf { it.isNotEmpty() }

    fun saveQuickCommandOrder(vin: String, order: List<String>) {
        val normalizedVin = vin.trim()
        if (normalizedVin.isEmpty()) return
        appPrefs.edit()
            .putString(quickCommandOrderKey(normalizedVin), order.joinToString(","))
            .apply()
    }

    fun clearQuickCommandOrder(vin: String) {
        val normalizedVin = vin.trim()
        if (normalizedVin.isEmpty()) return
        appPrefs.edit().remove(quickCommandOrderKey(normalizedVin)).apply()
    }

    /**
     * Records the newest public version that the user has already handled in the
     * update prompt. It intentionally lives in app preferences and survives logout.
     */
    fun loadHandledUpdateVersion(): String? =
        appPrefs.getString(HANDLED_UPDATE_VERSION, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun saveHandledUpdateVersion(version: String) {
        val normalized = version.trim()
        if (normalized.isEmpty()) return
        appPrefs.edit().putString(HANDLED_UPDATE_VERSION, normalized).apply()
    }

    fun shouldShowAuthorSupportPrompt(): Boolean = AuthorSupportReminder.shouldShow(
        lastShownEpochDay = appPrefs.getLong(AUTHOR_SUPPORT_LAST_SHOWN_EPOCH_DAY, Long.MIN_VALUE),
        todayEpochDay = LocalDate.now().toEpochDay(),
        permanentlyDisabled = isAuthorSupportPromptDisabled()
    )

    fun isAuthorSupportPromptDisabled(): Boolean =
        appPrefs.getBoolean(AUTHOR_SUPPORT_PROMPT_DISABLED, false)

    fun disableAuthorSupportPrompt() {
        appPrefs.edit().putBoolean(AUTHOR_SUPPORT_PROMPT_DISABLED, true).apply()
    }

    fun recordAuthorSupportPromptShown() {
        appPrefs.edit()
            .putLong(AUTHOR_SUPPORT_LAST_SHOWN_EPOCH_DAY, LocalDate.now().toEpochDay())
            .apply()
    }

    /** Last observed charge state, isolated by vehicle so a switch cannot create a false transition. */
    fun loadLastChargeState(vin: String): Int? =
        vin.takeIf { it.isNotBlank() }?.let { prefs.getString(lastChargeStateKey(it), null)?.toIntOrNull() }

    fun saveLastChargeState(vin: String, state: Int?) {
        if (vin.isBlank()) return
        prefs.edit().apply {
            if (state == null) remove(lastChargeStateKey(vin)) else putString(lastChargeStateKey(vin), state.toString())
        }.apply()
    }

    private fun lastChargeStateKey(vin: String): String = "${LAST_CHARGE_STATE}_$vin"

    private companion object {
        const val SESSION_GENERATION = "session_generation"
        const val AUTHOR_SUPPORT_LAST_SHOWN_EPOCH_DAY = "author_support_last_shown_epoch_day"
        const val AUTHOR_SUPPORT_PROMPT_DISABLED = "author_support_prompt_disabled"
        const val QUICK_COMMAND_ORDER_PREFIX = "quick_command_order_"
        const val HANDLED_UPDATE_VERSION = "handled_update_version"
        const val APPEARANCE_MODE = "appearance_mode"
        const val WIDGET_SENSITIVE_ACTION_VERIFICATION_ENABLED =
            "widget_sensitive_action_verification_enabled"
        const val LAST_CHARGE_STATE = "last_charge_state"
        const val WIDGET_OPACITY_OPAQUE = 100

        fun quickCommandOrderKey(vin: String): String = QUICK_COMMAND_ORDER_PREFIX + vin
        const val WIDGET_SYNC_SUPPRESSED_UNTIL = "widget_sync_suppressed_until"
        const val WIDGET_AUTH_INVALID = "widget_auth_invalid"
        const val POWER_PAGER_AUTO_PLAY_ENABLED = "power_pager_auto_play_enabled"
        val WIDGET_OPACITY_OPTIONS = setOf(100, 75, 50, 25)
        val SESSION_LOCK = Any()
    }
}
