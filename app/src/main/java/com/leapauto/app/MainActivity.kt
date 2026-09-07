package com.leapauto.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.leapauto.app.ui.LeapAutoScreen
import com.leapauto.app.ui.theme.LeapAutoTheme
import org.json.JSONObject


/** 车况解析结果（UI 状态卡用）。 */
data class VehicleStatus(
    val soc: String?,
    val preciseSoc: String?,
    val fuelSoc: String? = null,
    val mileage: String?,
    val fuelMileage: String? = null,
    val electricMileage: String? = null,
    val combinedMileage: String? = null,
    val rangeExtender: Boolean = false,
    val totalMileage: String?,
    val averageEnergyConsumption: String?,
    val chargingPower: String?,
    val chargeRemainTime: String?,
    val batteryVoltage: String?,
    val batteryCurrent: String?,
    val chargeType: String?,
    val minBatteryTemp: String?,
    val batteryPreheatEnabled: Boolean? = null,
    val healthyChargeEnabled: Boolean?,
    val rangeMode: String?,
    val speed: String?,
    val isDriving: Boolean?,
    val gearStatus: String?,
    val locked: Boolean?,
    val acSwitch: Boolean?,
    val acSetting: String?,
    val acCoolingAndHeating: Int?,
    val climateMode: Int? = null,
    val acOperateMode: Int? = null,
    val recirculationMode: Int? = null,
    val indoorTemp: String?,
    val acAirVolume: String?,
    val windshieldDefrost: Boolean?,
    val rearWindowHeating: Boolean?,
    val sentryMode: Boolean? = null,
    val windowStatusAvailable: Boolean = false,
    val openWindows: List<String> = emptyList(),
    val tires: List<TireStatus>,
    val chargeLabel: String,
    val chargeState: Int?,
    val locationSummary: VehicleLocationSummary? = null,
    val trunkState: TrunkState = TrunkState.UNKNOWN,
    val driverDoorOpen: Boolean = false,
    val passengerDoorOpen: Boolean = false,
    val leftRearDoorOpen: Boolean = false,
    val rightRearDoorOpen: Boolean = false,
    val anyDoorOpen: Boolean = false,
    val driverSeatHeating: Int? = null,
    val driverSeatVentilation: Int? = null,
    val passengerSeatHeating: Int? = null,
    val passengerSeatVentilation: Int? = null,
    val steeringWheelHeating: Boolean? = null,
    val acSettingRight: String? = null
)

data class TireStatus(
    val position: String,
    val pressure: String?,
    val temperature: String?,
    val warning: Boolean
)

private data class PendingClimateConfirmation(
    val revision: Long,
    val label: String,
    val optimisticUpdate: ClimateOptimisticUpdate?,
    val telemetryExpectation: ClimateTelemetryExpectation?
)

private fun String.isPureElectricModel(): Boolean =
    contains("纯电", ignoreCase = true) ||
        contains("EV", ignoreCase = true) ||
        contains("BEV", ignoreCase = true)

class MainActivity : ComponentActivity() {

    private lateinit var sessionStore: SessionStore
    private lateinit var energyCacheStore: EnergyCacheStore
    private lateinit var session: Session
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val statusRefreshInFlight = AtomicBoolean(false)
    private val statusRefreshRequested = AtomicBoolean(false)
    private val statusRefreshRequestedVerbose = AtomicBoolean(false)
    private val postControlStatusRefreshScheduled = AtomicBoolean(false)
    private val energyRefreshInFlight = AtomicBoolean(false)
    private val hvacCapabilityRefreshInFlight = AtomicBoolean(false)
    private var activityResumed = false
    private var carScreenVisible = false
    private var autoRefreshScheduled = false
    @Volatile private var operationGeneration = 0L
    @Volatile private var activityDestroyed = false

    // Compose 状态
    private var loggedIn by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf<VehicleStatus?>(null)
    private var statusUpdatedAtEpochMs by mutableStateOf(0L)
    /** Position coordinates stay in this activity-only snapshot and are never persisted or displayed. */
    @Volatile private var vehicleLocationSnapshot: VehicleLocationSnapshot? = null
    private var vehicleLocationSnapshotState by mutableStateOf<VehicleLocationSnapshot?>(null)
    private var vehicleAddress by mutableStateOf<GeocodedAddress?>(null)
    @Volatile private var lastGeocodedLocation: Pair<Double, Double>? = null
    private var statusError by mutableStateOf("")
    private var controlFeedback by mutableStateOf<ControlFeedback?>(null)
    private var climateTemperatureRequestState by mutableStateOf(ClimateControlRequestState())
    @Volatile private var climateStatusRevision = 0L
    @Volatile private var climateOptimisticGuard: ClimateOptimisticGuard? = null
    private var climatePreControlStatus: VehicleStatus? = null
    @Volatile private var pendingClimateConfirmation: PendingClimateConfirmation? = null
    private var pendingStatusRefreshCompletion: ((Boolean) -> Unit)? = null
    private var pinSaved by mutableStateOf(false)
    private var pinSetupInProgress by mutableStateOf(false)
    private var pendingPinProtectedAction: (() -> Unit)? = null
    private var pendingPinProtectedCancelAction: (() -> Unit)? = null
    private var showVehicleConfigConfirmationPrompt by mutableStateOf(false)
    private var phone by mutableStateOf("")
    private var code by mutableStateOf("")
    private var smsCountdownSeconds by mutableStateOf(0)
    private var pin by mutableStateOf("")
    private var widgetOpacity by mutableStateOf(100)
    private var widgetSensitiveActionVerificationEnabled by mutableStateOf(true)
    private var appearanceMode by mutableStateOf(AppearanceMode.SYSTEM)
    private var energyState by mutableStateOf<EnergyAnalyticsState>(EnergyAnalyticsState.Idle)
    @Volatile private var energyLastSuccessAt = 0L
    private var vehicleConfig by mutableStateOf(SessionStore.VehicleConfig())
    private var hvacCapability by mutableStateOf(HvacCapability.fallback())
    private var networkDebugEnabled by mutableStateOf(false)
    private var signalMapDebugState by mutableStateOf<VehicleSignalMapDebugState>(VehicleSignalMapDebugState.Idle)
    private var versionUpdateState by mutableStateOf<VersionUpdateState>(VersionUpdateState.Idle)
    private var handledUpdateVersion by mutableStateOf<String?>(null)
    private var showAuthorSupportDialog by mutableStateOf(false)
    private var showSessionExpiredDialog by mutableStateOf(false)

    private val authorSupportPromptRunnable = Runnable {
        if (canShowAuthorSupportPrompt()) {
            sessionStore.recordAuthorSupportPromptShown()
            showAuthorSupportDialog = true
        } else if (canArmAuthorSupportPrompt()) {
            maybeShowAuthorSupportPrompt()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupGlobalCrashHandler()
        checkLastCrashReport()
        NetworkDebugController.initialize(this)
        sessionStore = SessionStore(this)
        energyCacheStore = EnergyCacheStore(this)
        session = sessionStore.load()
        hvacCapability = session.hvacCapability
        vehicleConfig = sessionStore.loadVehicleConfig(
            vin = session.selectedVin,
            defaultModel = session.selectedCarType,
            defaultNickname = session.selectedNickname,
            defaultYear = session.selectedYear.ifBlank { "2026" },
            defaultPowerType = SessionStore.VehiclePowerType.PURE_ELECTRIC
        )
        pin = sessionStore.loadOpPassword() ?: ""
        pinSaved = pin.isNotBlank()
        widgetOpacity = sessionStore.loadWidgetOpacity()
        widgetSensitiveActionVerificationEnabled =
            sessionStore.loadWidgetSensitiveActionVerificationEnabled()
        appearanceMode = sessionStore.loadAppearanceMode()
        handledUpdateVersion = sessionStore.loadHandledUpdateVersion()
        ChargeNotificationManager.ensureChannel(this)
        ParkingAnomalyNotificationManager.ensureChannel(this)

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        setContent {
            LeapAutoTheme(darkTheme = appearanceMode.resolvesToDark(isSystemInDarkTheme())) {
                LeapAutoScreen(
                    loggedIn = loggedIn,
                    busy = busy,
                    status = status,
                    statusUpdatedAtEpochMs = statusUpdatedAtEpochMs,
                    locationSnapshot = vehicleLocationSnapshotState,
                    vehicleAddress = vehicleAddress,
                    vehicleVin = session.selectedVin,
                    statusError = statusError,
                    controlFeedback = controlFeedback,
                    climateTemperatureRequestState = climateTemperatureRequestState,
                    vehicleModel = session.selectedCarType,
                    vehicleDisplayModel = VehicleStatusMapper.resolveDisplayModel(vehicleConfig.model, session.selectedCarType),
                    vehicleConfig = vehicleConfig,
                    hvacCapability = hvacCapability,
                    vehicleAppearance = VehicleAppearanceCatalog.resolveAppearance(
                        VehicleStatusMapper.resolveDisplayModel(vehicleConfig.model, session.selectedCarType),
                        vehicleConfig.color
                    ),
                    pinSaved = pinSaved,
                    pinSetupInProgress = pinSetupInProgress,
                    showVehicleConfigConfirmationPrompt = showVehicleConfigConfirmationPrompt,
                    widgetOpacity = widgetOpacity,
                    appearanceMode = appearanceMode,
                    energyState = energyState,
                    networkDebugEnabled = networkDebugEnabled,
                    currentVersion = AppReleaseInfo.currentVersion,
                    currentReleaseNotes = AppReleaseInfo.currentReleaseNotes,
                    versionUpdateState = versionUpdateState,
                    handledUpdateVersion = handledUpdateVersion,
                    showAuthorSupportDialog = showAuthorSupportDialog,
                    showSessionExpiredDialog = showSessionExpiredDialog,
                    phone = phone,
                    onPhoneChange = { phone = it.take(11) },
                    code = code,
                    onCodeChange = { code = it.take(6) },
                    pin = pin,
                    onPinChange = { pin = it.take(4) },
                    onSendSms = ::sendSms,
                    smsCountdownSeconds = smsCountdownSeconds,
                    onLogin = ::login,
                    onSavePin = ::savePin,
                    onCancelPinSetup = ::cancelPinSetup,
                    onWidgetOpacityChange = ::saveWidgetOpacity,
                    widgetSensitiveActionVerificationEnabled = widgetSensitiveActionVerificationEnabled,
                    onWidgetSensitiveActionVerificationChange = ::saveWidgetSensitiveActionVerification,
                    onAppearanceModeChange = ::saveAppearanceMode,
                    onSaveVehicleConfig = ::saveVehicleConfig,
                    onNetworkDebugEnabledChange = { enabled ->
                        networkDebugEnabled = enabled
                        NetworkDebugController.setEnabled(enabled)
                    },
                    onCheckForUpdate = ::checkForUpdate,
                    onOpenUpdate = ::openUpdatePage,
                    onDismissVersionUpdatePrompt = ::markVersionUpdateHandled,
                    onOpenVersionUpdatePrompt = ::openVersionUpdatePage,
                    onDismissAuthorSupport = { showAuthorSupportDialog = false },
                    onDisableAuthorSupport = {
                        sessionStore.disableAuthorSupportPrompt()
                        showAuthorSupportDialog = false
                    },
                    onOpenFeedback = ::openFeedback,
                    onSessionExpiredConfirmed = ::logout,
                    onRefresh = ::refreshStatus,
                    onRefreshEnergy = { refreshEnergy(force = true) },
                    onAutoRefreshActiveChange = ::setAutoRefreshActive,
                    onLogout = ::logout,
                    onControl = { control(it) },
                    onApplyClimateSettings = ::applyClimateSettings,
                    onDismissControlFeedback = { controlFeedback = null },
                    onQuickAc = ::quickAc
                )
            }
        }

        if (session.oldAuth != null) {
            loggedIn = true
            beginPostLoginPrompts()
            checkForUpdate()
            refreshStatus()
            refreshEnergy(force = true)
            refreshHvacCapability()
            syncVehicleImage(session.selectedVin)
            refreshVehicleList()
        }
    }

    private fun saveAppearanceMode(mode: AppearanceMode) {
        appearanceMode = mode
        sessionStore.saveAppearanceMode(mode)
        ControlWidget.refreshAppearance(this)
    }

    private fun handleSessionFailure(error: Throwable) {
        if (loggedIn && SessionExpiry.isRefreshTokenInvalid(error.message)) {
            enterSessionExpiredState()
        }
    }

    private fun enterSessionExpiredState() {
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
        clearPendingPinProtectedAction(cancel = true)
        pinSetupInProgress = false
        showVehicleConfigConfirmationPrompt = false
        showAuthorSupportDialog = false
        showSessionExpiredDialog = true
    }

    override fun onDestroy() {
        activityDestroyed = true
        NetworkDebugController.disableAndClear()
        operationGeneration += 1L
        worker.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (loggedIn && carScreenVisible) {
            // Refresh immediately when returning to the foreground while the vehicle tab is visible.
            refreshStatus(silent = true)
            refreshEnergy(force = true)
        }
        updateAutoRefreshLoop()
        maybeShowAuthorSupportPrompt()
    }

    override fun onPause() {
        activityResumed = false
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
        updateAutoRefreshLoop()
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        maybeShowAuthorSupportPrompt()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Activity.onUserInteraction only guarantees the start of a gesture; moves keep resetting idle time.
        maybeShowAuthorSupportPrompt()
        return super.dispatchTouchEvent(event)
    }

    private fun setAutoRefreshActive(active: Boolean) {
        val wasActive = carScreenVisible
        carScreenVisible = active
        if (active && !wasActive && loggedIn) {
            // The vehicle tab is the source screen for the location detail page too.
            // Start with a fresh snapshot instead of waiting for the periodic tick.
            refreshStatus(silent = true)
        }
        updateAutoRefreshLoop()
    }

    private fun updateAutoRefreshLoop() {
        val shouldRun = activityResumed && carScreenVisible && loggedIn && status?.isDriving == true && !activityDestroyed
        if (!shouldRun) {
            autoRefreshScheduled = false
            mainHandler.removeCallbacks(autoRefreshRunnable)
        } else if (!autoRefreshScheduled) {
            autoRefreshScheduled = true
            mainHandler.post(autoRefreshRunnable)
        }
    }

    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            if (!activityResumed || !carScreenVisible || !loggedIn || activityDestroyed) {
                autoRefreshScheduled = false
                return
            }
            if (status?.isDriving != true) {
                autoRefreshScheduled = false
                return
            }
            refreshStatus(silent = true)
            mainHandler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS)
        }
    }

    // ------------------------------------------------------------- 登录流程

    private fun refreshHvacCapability() {
        if (!loggedIn || !hvacCapabilityRefreshInFlight.compareAndSet(false, true)) return
        val generation = operationGeneration
        val activeSession = session
        worker.execute {
            try {
                LeapmotorApi(activeSession).listVehicles()
                sessionStore.save(activeSession)
                runOnMain(generation) {
                    hvacCapability = activeSession.hvacCapability
                }
            } catch (_: Exception) {
                // Vehicle status refresh remains the user-facing auth/error source. A restored
                // session can safely continue with the documented HVAC fallback capability.
            } finally {
                hvacCapabilityRefreshInFlight.set(false)
            }
        }
    }

    private fun sendSms() {
        if (phone.length != 11) {
            toast("请输入 11 位手机号")
            return
        }
        busy { generation ->
            LeapmotorApi(session).sendSms(phone)
            startSmsCountdown()
            toast("验证码已发送", generation)
        }
    }

    private fun startSmsCountdown() {
        stopSmsCountdown()
        smsCountdownSeconds = 60
        smsCountdownTick()
    }

    private fun stopSmsCountdown() {
        mainHandler.removeCallbacks(smsCountdownTickRunnable)
    }

    private val smsCountdownTickRunnable = Runnable {
        if (smsCountdownSeconds > 0) {
            smsCountdownSeconds -= 1
            smsCountdownTick()
        }
    }

    private fun smsCountdownTick() {
        if (smsCountdownSeconds > 0) {
            mainHandler.postDelayed(smsCountdownTickRunnable, 1_000L)
        }
    }

    private fun login() {
        if (phone.length != 11 || code.isEmpty()) {
            toast("请输入手机号和验证码")
            return
        }
        busy { generation ->
            val started = System.currentTimeMillis()
            try {
            val api = LeapmotorApi(session)
            api.loginWithSms(phone, code)
            val vehicles = api.listVehicles()
            if (vehicles.isEmpty()) throw ApiException("账号下没有找到车辆")
            val selected = vehicles.firstOrNull { it.vin == session.selectedVin } ?: vehicles.first()
            vehicleConfig = sessionStore.loadVehicleConfig(
                vin = session.selectedVin,
                defaultModel = selected.carType,
                defaultNickname = selected.nickname,
                defaultYear = selected.year.ifBlank { "2026" },
                defaultPowerType = selected.powerType
            )
            sessionStore.save(session)
            runOnMain(generation) {
                hvacCapability = session.hvacCapability
                toast("登录成功")
                loggedIn = true
                beginPostLoginPrompts()
                checkForUpdate()
                refreshStatus()
                refreshEnergy(force = true)
                syncVehicleImage(session.selectedVin)
            }
            } catch (e: Exception) {
                val apiError = e as? ApiException
                ErrorLogs.repository.record(ErrorLogEntry(System.currentTimeMillis(), ErrorLogCategory.LOGIN_FAILURE, apiError?.stage ?: "login", apiError?.httpStatus, apiError?.durationMs ?: (System.currentTimeMillis() - started), apiError?.retryCount ?: 0, BuildConfig.VERSION_NAME, e.message ?: e.toString()))
                throw e
            }
        }
    }

    private fun logout() {
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
        clearPendingPinProtectedAction(cancel = true)
        ErrorLogs.repository.clear()
        energyCacheStore.clearAll()
        operationGeneration += 1L
        sessionStore.clear()
        sessionStore.saveOpPassword("")
        session = sessionStore.load()
        hvacCapability = session.hvacCapability
        vehicleConfig = SessionStore.VehicleConfig()
        clearEnergyState()
        pin = ""
        pinSaved = false
        pinSetupInProgress = false
        showVehicleConfigConfirmationPrompt = false
        code = ""
        status = null
        statusUpdatedAtEpochMs = 0L
        vehicleLocationSnapshot = null
        vehicleLocationSnapshotState = null
        vehicleAddress = null
        lastGeocodedLocation = null
        statusError = ""
        controlFeedback = null
        climateOptimisticGuard = null
        climatePreControlStatus = null
        pendingClimateConfirmation = null
        climateStatusRevision += 1L
        showAuthorSupportDialog = false
        showSessionExpiredDialog = false
        signalMapDebugState = VehicleSignalMapDebugState.Idle
        loggedIn = false
        versionUpdateState = VersionUpdateState.Idle
        busy = false
        toast("已退出登录")
    }

    private fun savePin() {
        if (!pin.matches(Regex("\\d{4}"))) {
            toast("请输入 4 位数字操作密码")
            return
        }
        sessionStore.saveOpPassword(pin)
        pinSaved = true
        val pendingAction = pendingPinProtectedAction
        pendingPinProtectedAction = null
        pendingPinProtectedCancelAction = null
        pinSetupInProgress = false
        pin = ""
        toast("操作密码已保存")
        pendingAction?.invoke()
    }

    private fun cancelPinSetup() {
        pin = ""
        pinSetupInProgress = false
        clearPendingPinProtectedAction(cancel = true)
        maybeShowAuthorSupportPrompt()
    }

    private fun requestOperationPassword(
        action: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        pendingPinProtectedAction = action
        pendingPinProtectedCancelAction = onCancel
        pin = ""
        pinSetupInProgress = true
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
    }

    private fun clearPendingPinProtectedAction(cancel: Boolean) {
        val cancelAction = pendingPinProtectedCancelAction
        pendingPinProtectedAction = null
        pendingPinProtectedCancelAction = null
        if (cancel) cancelAction?.invoke()
    }

    private fun saveWidgetOpacity(opacity: Int) {
        sessionStore.saveWidgetOpacity(opacity)
        widgetOpacity = opacity
        ControlWidget.refreshAppearance(this)
    }

    private fun saveWidgetSensitiveActionVerification(enabled: Boolean) {
        sessionStore.saveWidgetSensitiveActionVerificationEnabled(enabled)
        widgetSensitiveActionVerificationEnabled = enabled
    }

    private fun clearEnergyState() {
        energyLastSuccessAt = 0L
        energyState = EnergyAnalyticsState.Idle
    }

    private fun refreshEnergy(force: Boolean = false) {
        if (!loggedIn || session.selectedVin.isBlank()) return
        val now = System.currentTimeMillis()
        if (energyState is EnergyAnalyticsState.Idle) {
            energyCacheStore.load(session.selectedVin)
                ?.takeIf(EnergyRefreshPolicy::hasHomeDisplayableData)
                ?.let { cached ->
                energyLastSuccessAt = cached.capturedAt
                energyState = EnergyAnalyticsState.Success(cached)
            }
        }
        if (!EnergyRefreshPolicy.shouldRefresh(energyLastSuccessAt, now, force)) return
        if (!energyRefreshInFlight.compareAndSet(false, true)) return
        if (energyState !is EnergyAnalyticsState.Success) {
            energyState = EnergyAnalyticsState.Loading
        }
        val purchaseAtMs = (energyState as? EnergyAnalyticsState.Success)
            ?.data
            ?.ownershipDays
            ?.value
            ?.toLongOrNull()
            ?.takeIf { it in 0L..36500L }
            ?.let { now - it * 24L * 60L * 60L * 1000L }
        val currentVehicleTotalMileage = status?.totalMileage
        val generation = operationGeneration
        worker.execute {
            try {
                val api = LeapmotorApi(session)
                val parsed = try {
                    val response = api.getMileageEnergy(purchaseAtMs, now)
                    EnergyAnalyticsParser.parse(response)
                } catch (e: Exception) {
                    if (SessionExpiry.isRefreshTokenInvalid(e.message)) throw e
                    EnergyAnalyticsData.EMPTY.copy(capturedAt = now)
                }
                val rankResult = try {
                    Result.success(EnergyRankAnalyticsParser.parse(api.getLastNWeeks100kmEcAndRank()))
                } catch (e: Exception) {
                    if (SessionExpiry.isRefreshTokenInvalid(e.message)) throw e
                    Result.failure(e)
                }
                val rankData = rankResult.getOrNull()
                val compositionResult = try {
                    Result.success(
                        LastWeekEnergyCompositionParser.parse(api.getLastWeekEc())
                    )
                } catch (e: Exception) {
                    if (SessionExpiry.isRefreshTokenInvalid(e.message)) throw e
                    Result.failure(e)
                }
                val recentMileageResult = try {
                    Result.success(
                        RecentMileageEnergyParser.parse(api.getRecentMileageEnergy(now))
                    )
                } catch (e: Exception) {
                    if (SessionExpiry.isRefreshTokenInvalid(e.message)) throw e
                    Result.failure(e)
                }
                val recentMileage = recentMileageResult.getOrNull()
                val merged = parsed.copy(
                    overallConsumption = rankData?.overallConsumption ?: parsed.overallConsumption,
                    trend = rankData?.weeklyTrend?.takeIf { it.isNotEmpty() } ?: parsed.trend,
                    rankLabel = rankData?.rankLabel,
                    rankError = rankResult.exceptionOrNull()?.let { "周能耗趋势暂不可用" },
                    lastWeekComposition = compositionResult.getOrDefault(emptyList()),
                    ownershipDays = parsed.ownershipDays
                        ?: recentMileage?.deliveryDays?.let {
                            EnergyMetric(label = "提车天数", value = it.toString())
                        },
                    cumulativeEnergy = parsed.cumulativeEnergy
                        ?: recentMileage?.totalEnergyKwh?.let {
                            EnergyMetric(label = "累计能耗", value = it.toString(), unit = "kWh")
                        },
                    totalMileage = parsed.totalMileage
                        ?: currentVehicleTotalMileage
                            ?.filter { ch -> ch.isDigit() || ch == '.' }
                            ?.takeIf { text -> text.isNotBlank() }
                            ?.let { text -> EnergyMetric(label = "总里程", value = text, unit = "km") },
                    recentMileage = recentMileage?.let {
                        EnergyMetric(
                            label = "近7天行驶里程",
                            value = it.totalMileageKm.toString(),
                            unit = "km"
                        )
                    } ?: parsed.recentMileage,
                    mileageTrend = recentMileage
                        ?.mileage
                        ?.map { EnergySeriesPoint(label = it.day, value = it.mileageKm) }
                        ?.takeIf { it.isNotEmpty() }
                        ?: parsed.mileageTrend,
                    capturedAt = now
                )
                runOnMain(generation) {
                    if (EnergyRefreshPolicy.hasHomeDisplayableData(merged)) {
                        energyCacheStore.save(session.selectedVin, merged)
                        energyLastSuccessAt = merged.capturedAt
                        energyState = EnergyAnalyticsState.Success(merged)
                    } else {
                        energyLastSuccessAt = 0L
                        energyState = EnergyAnalyticsState.Failed("暂无可展示的能耗数据")
                    }
                }
            } catch (e: EnergyAnalyticsBusinessException) {
                ErrorLogs.repository.record(
                    ErrorLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        category = ErrorLogCategory.API_FAILURE,
                        stage = "mileage_energy_business",
                        appVersion = BuildConfig.VERSION_NAME,
                        message = "能耗接口业务失败"
                    )
                )
                runOnMain(generation) {
                    if (energyState !is EnergyAnalyticsState.Success) {
                        energyState = EnergyAnalyticsState.Failed(e.message ?: "能耗服务暂时不可用，请稍后重试")
                    }
                }
            } catch (e: EnergyAnalyticsEmptyDataException) {
                runOnMain(generation) {
                    if (energyState !is EnergyAnalyticsState.Success) {
                        energyState = EnergyAnalyticsState.Failed(e.message ?: "能耗接口暂未返回可展示数据")
                    }
                }
            } catch (e: EnergyAnalyticsParseException) {
                ErrorLogs.repository.record(
                    ErrorLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        category = ErrorLogCategory.PARSE_FAILURE,
                        stage = "mileage_energy_parse",
                        appVersion = BuildConfig.VERSION_NAME,
                        // Never put the old-service response or parser source
                        // text in local diagnostic logs.
                        message = "能耗响应解析失败"
                    )
                )
                runOnMain(generation) {
                    if (energyState !is EnergyAnalyticsState.Success) {
                        energyState = EnergyAnalyticsState.Failed("能耗数据暂无法解析，请稍后重试")
                    }
                }
            } catch (e: Exception) {
                val apiError = e as? ApiException
                ErrorLogs.repository.record(
                    ErrorLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        category = if (SessionExpiry.isRefreshTokenInvalid(e.message)) {
                            ErrorLogCategory.SESSION_EXPIRED
                        } else {
                            ErrorLogCategory.API_FAILURE
                        },
                        stage = apiError?.stage ?: "mileage_energy",
                        httpStatus = apiError?.httpStatus,
                        durationMs = apiError?.durationMs,
                        retryCount = apiError?.retryCount ?: 0,
                        appVersion = BuildConfig.VERSION_NAME,
                        // The low-level body can contain business data. Keep
                        // error telemetry structural only for this endpoint.
                        message = "能耗接口读取失败"
                    )
                )
                runOnMain(generation) {
                    handleSessionFailure(e)
                    if (energyState !is EnergyAnalyticsState.Success) {
                        energyState = EnergyAnalyticsState.Failed(
                            if (SessionExpiry.isRefreshTokenInvalid(e.message)) {
                                "登录状态已失效，请重新登录"
                            } else {
                                "能耗接口读取失败，请检查网络后重试"
                            }
                        )
                    }
                }
            } finally {
                energyRefreshInFlight.set(false)
            }
        }
    }

    private fun saveVehicleConfig(
        model: String,
        modelYear: String,
        powerType: SessionStore.VehiclePowerType?,
        color: String,
        nickname: String
    ) {
        val normalizedModel = VehicleConfigConfirmationPolicy.normalizeModel(model)
        if (
            normalizedModel == null ||
            !VehicleConfigConfirmationPolicy.isValid(model, modelYear, powerType, color)
        ) {
            toast("请确认车型、4 位车型年份和动力类型")
            return
        }
        val previousPowerType = vehicleConfig.powerType
        vehicleConfig = SessionStore.VehicleConfig(
            modelYear = modelYear.trim(),
            powerType = powerType,
            model = normalizedModel,
            color = VehicleAppearanceCatalog.reconciledColor(normalizedModel, color),
            nickname = nickname.trim()
        )
        sessionStore.saveVehicleConfig(session.selectedVin, vehicleConfig)
        showVehicleConfigConfirmationPrompt = false
        ControlWidget.refreshData(this)
        if (previousPowerType != powerType) refreshStatus()
        maybeShowAuthorSupportPrompt()
    }

    private fun maybeShowAuthorSupportPrompt() {
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
        if (canArmAuthorSupportPrompt()) {
            mainHandler.postDelayed(
                authorSupportPromptRunnable,
                PostLoginPromptTiming.AUTHOR_SUPPORT_IDLE_MS
            )
        }
    }

    private fun canArmAuthorSupportPrompt(): Boolean =
        AuthorSupportIdlePolicy.canArm(
            AuthorSupportPromptContext(
                activityAlive = !activityDestroyed,
                foreground = activityResumed,
                loggedIn = loggedIn,
                busy = busy,
                pinPromptVisible = false,
                pinSetupInProgress = pinSetupInProgress,
                vehicleConfigPromptVisible = showVehicleConfigConfirmationPrompt,
                sessionExpiredPromptVisible = showSessionExpiredDialog,
                authorSupportPromptVisible = showAuthorSupportDialog,
                authorSupportScreenOpening = false,
                reminderEligibleToday = sessionStore.shouldShowAuthorSupportPrompt()
            )
        )

    private fun canShowAuthorSupportPrompt(): Boolean = canArmAuthorSupportPrompt()

    private fun beginPostLoginPrompts() {
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
        showAuthorSupportDialog = false
        showVehicleConfigConfirmationPrompt = false
        pinSetupInProgress = false
        when (
            VehicleConfigConfirmationPolicy.initialPrompt(
                vehicleConfigConfirmed = sessionStore.isVehicleConfigConfirmed(session.selectedVin)
            )
        ) {
            PostLoginPrompt.VEHICLE_CONFIG -> showVehicleConfigConfirmationPrompt = true
            PostLoginPrompt.NONE -> maybeShowAuthorSupportPrompt()
        }
    }

    private fun openFeedback() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ExternalLinks.FEEDBACK_URL)))
        } catch (_: ActivityNotFoundException) {
            toast("未找到可用的浏览器")
        }
    }

    private fun checkForUpdate() {
        val currentVersion = AppReleaseInfo.currentVersion
        if (versionUpdateState is VersionUpdateState.Checking) return
        versionUpdateState = VersionUpdateState.Checking(currentVersion)
        val generation = operationGeneration
        worker.execute {
            try {
                val result = PgyerUpdateChecker.check(currentVersion)
                runOnMain(generation) { versionUpdateState = result }
            } catch (e: Exception) {
                val apiError = e as? ApiException
                ErrorLogs.repository.record(ErrorLogEntry(System.currentTimeMillis(), ErrorLogCategory.API_FAILURE, "version_update", apiError?.httpStatus, apiError?.durationMs, apiError?.retryCount ?: 0, BuildConfig.VERSION_NAME, e.message ?: e.toString()))
                val message = e.message?.takeIf { it.isNotBlank() } ?: "网络异常，请稍后重试"
                runOnMain(generation) {
                    versionUpdateState = VersionUpdateState.Failed(currentVersion, message)
                }
            }
        }
    }

    private fun openUpdatePage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PgyerUpdateChecker.DOWNLOAD_URL)))
        } catch (_: ActivityNotFoundException) {
            toast("未找到可用的浏览器")
        }
    }

    private fun markVersionUpdateHandled(version: String) {
        sessionStore.saveHandledUpdateVersion(version)
        handledUpdateVersion = version
    }

    private fun openVersionUpdatePage(version: String) {
        markVersionUpdateHandled(version)
        openUpdatePage()
    }

    // ------------------------------------------------------------- 状态查询

    private fun refreshStatus(silent: Boolean = false, completion: ((Boolean) -> Unit)? = null) {
        if (!statusRefreshInFlight.compareAndSet(false, true)) {
            statusRefreshRequested.set(true)
            if (!silent) statusRefreshRequestedVerbose.set(true)
            if (completion != null) pendingStatusRefreshCompletion = completion
            return
        }
        val refreshClimateRevision = climateStatusRevision
        busy(showBusy = !silent) { generation ->
            try {
                val api = LeapmotorApi(session)
                val signalMap = VehicleStatusMapper.withFuelMock(
                    status = api.getVehicleState(),
                    vin = session.selectedVin,
                    powerType = vehicleConfig.powerType
                )
                val receivedAtEpochMs = System.currentTimeMillis()
                val legacyT03Location = session.selectedCarType.contains("T03", ignoreCase = true)
                val locationTimestampEpochMs = VehicleLocationTimestamp.resolveEpochMs(
                    signalMap.opt(
                        if (legacyT03Location) "collectTime" else VehicleLocationDomain.LOCATION_TIMESTAMP_SIGNAL
                    ),
                    receivedAtEpochMs
                )
                val locationValidation = if (legacyT03Location) {
                    VehicleLocationDomain.validate(
                        values = mapOf(
                            VehicleLocationDomain.PRIMARY_LONGITUDE_KEY to signalMap.opt("longitude")
                                .takeUnless { it == JSONObject.NULL },
                            VehicleLocationDomain.PRIMARY_LATITUDE_KEY to signalMap.opt("latitude")
                                .takeUnless { it == JSONObject.NULL }
                        ),
                        previous = vehicleLocationSnapshot,
                        queriedAtEpochMs = locationTimestampEpochMs
                    ).let { validation ->
                        if (validation is VehicleLocationValidation.Valid) {
                            validation.copy(
                                location = validation.location.copy(
                                    coordinateSystem = VehicleCoordinateSystem.GCJ02
                                )
                            )
                        } else validation
                    }
                } else {
                    VehicleLocationDomain.validateVerifiedSignalMap(
                        values = mapOf(
                            VehicleLocationDomain.VERIFIED_LONGITUDE_SIGNAL to
                                signalMap.opt(VehicleLocationDomain.VERIFIED_LONGITUDE_SIGNAL)
                                    .takeUnless { it == JSONObject.NULL },
                            VehicleLocationDomain.VERIFIED_LATITUDE_SIGNAL to
                                signalMap.opt(VehicleLocationDomain.VERIFIED_LATITUDE_SIGNAL)
                                    .takeUnless { it == JSONObject.NULL }
                        ),
                        previous = vehicleLocationSnapshot,
                        queriedAtEpochMs = locationTimestampEpochMs
                    )
                }
                val candidateLocationSnapshot = VehicleLocationDomain.snapshot(
                    locationValidation,
                    queriedAtEpochMs = locationTimestampEpochMs,
                    receivedAtEpochMs = receivedAtEpochMs
                )?.takeUnless {
                    VehicleLocationDomain.isFutureSnapshot(it, receivedAtEpochMs)
                }
                val locationSnapshot = VehicleLocationDomain.retainLastTrustedSnapshot(
                    previous = vehicleLocationSnapshot,
                    candidate = candidateLocationSnapshot
                )
                val locationSummary = when {
                    candidateLocationSnapshot != null -> VehicleLocationSummary.fromValidation(
                        validation = locationValidation,
                        queriedAtEpochMs = locationTimestampEpochMs,
                        nowEpochMs = receivedAtEpochMs
                    )
                    locationSnapshot != null -> VehicleLocationSummary.fromSnapshot(
                        snapshot = locationSnapshot,
                        nowEpochMs = receivedAtEpochMs
                    )
                    else -> null
                }
                val parsed = try { parseStatus(signalMap).copy(
                    locationSummary = locationSummary
                ) } catch (e: Exception) {
                    ErrorLogs.repository.record(ErrorLogEntry(System.currentTimeMillis(), ErrorLogCategory.PARSE_FAILURE, "vehicle_state_parse", appVersion = BuildConfig.VERSION_NAME, message = e.message ?: e.toString()))
                    throw e
                }
                ChargeNotificationManager.process(this@MainActivity, sessionStore, session.selectedVin, signalMap)
                sessionStore.save(session)
                sessionStore.saveWidgetSnapshot(
                    vin = session.selectedVin,
                    carType = session.selectedCarType,
                    range = VehicleStatusMapper.widgetRange(signalMap, session.selectedCarType, vehicleConfig.powerType?.let { if (it == SessionStore.VehiclePowerType.PURE_ELECTRIC) VehicleStatusMapper.PowerType.PURE_ELECTRIC else VehicleStatusMapper.PowerType.RANGE_EXTENDER }) ?: "--",
                    soc = VehicleStatusMapper.electricSocPercent(signalMap)
                        ?: VehicleStatusMapper.soc(signalMap),
                    fuelSoc = VehicleStatusMapper.fuelSocPercent(signalMap),
                    updated = ControlWidget.formatUpdatedTime(),
                    powerType = vehicleConfig.powerType,
                    electricRange = VehicleStatusMapper.electricRemainingRange(signalMap),
                    fuelRange = VehicleStatusMapper.fuelRemainingRange(signalMap),
                    electricTotalRange = VehicleStatusMapper.electricTotalRange(signalMap),
                    fuelTotalRange = VehicleStatusMapper.fuelTotalRange(signalMap),
                    statusLabel = WidgetStatusMapper.label(signalMap, session.selectedCarType).orEmpty(),
                    locked = WidgetStatusMapper.locked(signalMap),
                    acEnabled = WidgetAcMapper.state(signalMap),
                    chargingPower = parsed.chargingPower,
                    chargeState = ChargeStatus.state(signalMap),
                    chargeRemainTime = ChargeStatus.remainingTime(signalMap.opt("chargeRemainTime")),
                    driving = parsed.isDriving,
                    trunkState = parsed.trunkState,
                    sentryEnabled = parsed.sentryMode
                )
                ControlWidget.updateSyncCadence(this@MainActivity, parsed.isDriving)
                ControlWidget.refreshData(this@MainActivity)
                runOnMain(generation) {
                    // Replace the activity-only snapshot only when this refresh yields a valid position.
                    vehicleLocationSnapshot = locationSnapshot
                    vehicleLocationSnapshotState = locationSnapshot
                    if (locationSnapshot != null) {
                        maybeUpdateVehicleAddress(locationSnapshot)
                    }
                    val guard = climateOptimisticGuard
                    val decision = ClimateTelemetryMergePolicy.decide(
                        guard = guard,
                        refreshRevision = refreshClimateRevision,
                        currentRevision = climateStatusRevision,
                        matchesOptimisticTarget = guard?.let {
                            climateTelemetryMatches(
                                optimisticUpdate = it.update,
                                telemetryExpectation = null,
                                refreshed = parsed
                            )
                        } ?: true
                    )
                    status = when (decision) {
                        ClimateTelemetryMergeDecision.APPLY -> parsed
                        ClimateTelemetryMergeDecision.PRESERVE_CLIMATE ->
                            preserveOptimisticClimate(parsed, guard?.update)
                    }
                    statusUpdatedAtEpochMs = receivedAtEpochMs
                    climateOptimisticGuard = ClimateTelemetryMergePolicy.consume(guard, decision)
                    if (decision == ClimateTelemetryMergeDecision.APPLY) {
                        confirmClimateTelemetryIfMatched(refreshClimateRevision, parsed)
                    }
                    if (!silent) {
                        statusError = ""
                    }
                    updateAutoRefreshLoop()
                    syncVehicleImage(session.selectedVin)
                    refreshVehicleList()
                    completion?.invoke(true)
                }
            } catch (e: Exception) {
                runOnMain(generation) {
                    // Main vehicle refresh must surface terminal refresh-token expiry before the generic error text.
                    handleSessionFailure(e)
                    val lastTrustedSnapshot = vehicleLocationSnapshot
                    vehicleLocationSnapshot = lastTrustedSnapshot
                    vehicleLocationSnapshotState = lastTrustedSnapshot
                    if (!silent) {
                        status = null
                        statusError = "车况获取失败：${e.message}"
                        recordPageError("vehicle_status_page", statusError)
                    } else {
                        // Keep other vehicle fields visible and retain the last trusted location.
                        status = status?.copy(
                            locationSummary = lastTrustedSnapshot?.let {
                                VehicleLocationSummary.fromSnapshot(it, System.currentTimeMillis())
                            }
                        )
                    }
                    completion?.invoke(false)
                }
            } finally {
                statusRefreshInFlight.set(false)
                if (statusRefreshRequested.compareAndSet(true, false)) {
                    val nextSilent = !statusRefreshRequestedVerbose.getAndSet(false)
                    val nextCompletion = pendingStatusRefreshCompletion
                    pendingStatusRefreshCompletion = null
                    refreshStatus(silent = nextSilent, completion = nextCompletion)
                } else {
                    pendingStatusRefreshCompletion = null
                }
            }
        }
    }

    private val vehicleImageSyncInFlight = AtomicBoolean(false)
    private val vehicleListRefreshInFlight = AtomicBoolean(false)

    private fun refreshVehicleList() {
        if (session.selectedVin.isBlank()) return
        if (!vehicleListRefreshInFlight.compareAndSet(false, true)) return
        worker.execute {
            try {
                val api = LeapmotorApi(session)
                val vehicles = api.listVehicles()
                val selected = vehicles.firstOrNull { it.vin == session.selectedVin } ?: vehicles.firstOrNull()
                if (selected != null) {
                    sessionStore.save(session)
                    runOnMain {
                        val current = vehicleConfig
                        val updatedNickname = if (current.nickname.isBlank() || current.nickname.equals(current.model, ignoreCase = true)) {
                            selected.nickname.ifBlank { current.nickname }
                        } else {
                            current.nickname
                        }
                        val updatedYear = current.modelYear.ifBlank { selected.year.ifBlank { "2026" } }
                        val updatedPower = current.powerType ?: selected.powerType
                        vehicleConfig = current.copy(
                            nickname = updatedNickname,
                            modelYear = updatedYear,
                            powerType = updatedPower
                        )
                        sessionStore.saveVehicleConfig(session.selectedVin, vehicleConfig)
                    }
                }
            } catch (_: Exception) {
            } finally {
                vehicleListRefreshInFlight.set(false)
            }
        }
    }

    private fun syncVehicleImage(vin: String) {
        if (vin.isBlank()) return
        if (!vehicleImageSyncInFlight.compareAndSet(false, true)) return
        worker.execute {
            try {
                val api = LeapmotorApi(session)
                val updated = VehicleImageCache.sync(this@MainActivity, api, vin)
                if (updated) {
                    ControlWidget.refreshData(this@MainActivity)
                }
            } catch (_: Exception) {
                // Keep local fallback
            } finally {
                vehicleImageSyncInFlight.set(false)
            }
        }
    }

    /** Reads raw signalMap data only after the user opens the diagnostics page. */
    private fun refreshSignalMapDebug() {
        if (!loggedIn || session.selectedVin.isBlank()) return
        if (signalMapDebugState is VehicleSignalMapDebugState.Loading) return
        signalMapDebugState = VehicleSignalMapDebugState.Loading
        val generation = operationGeneration
        worker.execute {
            try {
                val signalMap = LeapmotorApi(session).getVehicleSignalMap()
                val formatted = VehicleSignalMapDebugFormatter.format(signalMap)
                runOnMain(generation) {
                    signalMapDebugState = VehicleSignalMapDebugState.Success(
                        formattedJson = formatted,
                        signalCount = signalMap.length()
                    )
                }
            } catch (_: Exception) {
                runOnMain(generation) {
                    signalMapDebugState = VehicleSignalMapDebugState.Failed("读取车型信号失败，请稍后重试")
                }
            }
        }
    }

    /** Copying raw diagnostics is an explicit local user action. */
    private fun copySignalMapDebug(formattedJson: String) {
        if (formattedJson.isBlank()) return
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("signalMap", formattedJson))
        toast("已复制车型信号")
    }

    /**
     * Refresh immediately after an accepted/completed quick command, then once
     * more after telemetry has had time to catch up. Existing refresh
     * coalescing keeps signal/info/query requests serialized.
     */
    private fun refreshStatusAfterControl(
        generation: Long,
        completion: ((Boolean) -> Unit)? = null
    ) {
        runOnMain(generation) {
            refreshStatus(silent = true, completion = completion)
        }
        if (!postControlStatusRefreshScheduled.compareAndSet(false, true)) return
        mainHandler.postDelayed({
            postControlStatusRefreshScheduled.set(false)
            if (!activityDestroyed && loggedIn && generation == operationGeneration) {
                refreshStatus(silent = true)
            }
        }, POST_CONTROL_STATUS_REFRESH_DELAY_MS)
    }

    private fun quickAc(value: Int, requestId: Long) {
        if (!Commands.isSupportedAcTemperature(value)) {
            controlFeedback = ControlFeedback("空调温度未确认，请刷新车况后重试", ControlFeedbackKind.WARNING)
            updateClimateTemperatureRequest(requestId, ClimateControlRequestPhase.FAILED)
            return
        }
        updateClimateTemperatureRequest(requestId, ClimateControlRequestPhase.SENDING)
        submitClimateControl(
            command = Commands.buildAc(value),
            climateTemperatureRequestId = requestId,
            optimisticUpdate = ClimateOptimisticUpdates.temperature(value)
        )
    }

    private fun submitClimateControl(
        command: ControlCommand,
        climateTemperatureRequestId: Long? = null,
        optimisticUpdate: ClimateOptimisticUpdate? = null,
        telemetryExpectation: ClimateTelemetryExpectation? = null
    ) {
        val savedPin = sessionStore.loadOpPassword()
        if (savedPin.isNullOrEmpty()) {
            requestOperationPassword(
                action = {
                    submitClimateControl(
                        command = command,
                        climateTemperatureRequestId = climateTemperatureRequestId,
                        optimisticUpdate = optimisticUpdate,
                        telemetryExpectation = telemetryExpectation
                    )
                },
                onCancel = {
                    climateTemperatureRequestId?.let {
                        updateClimateTemperatureRequest(it, ClimateControlRequestPhase.FAILED)
                    }
                }
            )
            return
        }
        climateStatusRevision += 1L
        val climateControlRevision = climateStatusRevision
        climatePreControlStatus = status
        climateOptimisticGuard = optimisticUpdate?.let {
            applyClimateOptimisticUpdate(it)
            ClimateOptimisticGuard(climateControlRevision, it)
        }
        busy { generation ->
            runOnMain(generation) {
                controlFeedback = ControlFeedback(
                    ClimateControlFeedbackText.sending(command.label),
                    ControlFeedbackKind.IN_PROGRESS
                )
            }
            var postAccepted = false
            try {
                val api = LeapmotorApi(session)
                val result = api.sendControl(command, savedPin)
                postAccepted = true
                sessionStore.save(session)
                val postDecision = ClimateControlPostPolicy.decision(
                    command,
                    postSucceeded = true,
                    hasPollingId = result.hasPollingId()
                )
                check(
                    postDecision.outcome == ClimateControlPostOutcome.ACCEPTED
                ) {
                    "不支持的座舱控制命令"
                }
                acceptClimateControlPost(
                    generation = generation,
                    revision = climateControlRevision,
                    command = command,
                    optimisticUpdate = optimisticUpdate,
                    telemetryExpectation = telemetryExpectation,
                    climateTemperatureRequestId = climateTemperatureRequestId,
                    pollingId = result.msgID.takeIf { postDecision.shouldQueryControlResult }
                )
            } catch (e: Exception) {
                if (postAccepted) {
                    acceptClimateControlPost(
                        generation = generation,
                        revision = climateControlRevision,
                        command = command,
                        optimisticUpdate = optimisticUpdate,
                        telemetryExpectation = telemetryExpectation,
                        climateTemperatureRequestId = climateTemperatureRequestId,
                        pollingId = null
                    )
                } else {
                    failClimateControlPost(
                        generation = generation,
                        revision = climateControlRevision,
                        command = command,
                        climateTemperatureRequestId = climateTemperatureRequestId,
                        error = e
                    )
                }
            }
        }
    }

    private fun acceptClimateControlPost(
        generation: Long,
        revision: Long,
        command: ControlCommand,
        optimisticUpdate: ClimateOptimisticUpdate?,
        telemetryExpectation: ClimateTelemetryExpectation?,
        climateTemperatureRequestId: Long?,
        pollingId: String?
    ) {
        runOnMain(generation) {
            if (!isCurrentClimateRevision(generation, revision)) return@runOnMain
            registerPendingClimateConfirmation(revision, command, optimisticUpdate, telemetryExpectation)
            completeClimatePost(revision, optimisticUpdate)
            controlFeedback = ControlFeedback(
                ClimateControlFeedbackText.submitted(command.label),
                ControlFeedbackKind.SUBMITTED
            )
            climateTemperatureRequestId?.let {
                updateClimateTemperatureRequest(it, ClimateControlRequestPhase.COMPLETED)
            }
            scheduleClimateTelemetryRefreshes(generation, revision)
            pollingId?.let {
                scheduleClimateResultQuery(generation, revision, it, optimisticUpdate, telemetryExpectation, 0)
            }
        }
    }

    private fun failClimateControlPost(
        generation: Long,
        revision: Long,
        command: ControlCommand,
        climateTemperatureRequestId: Long?,
        error: Exception
    ) {
        runOnMain(generation) {
            handleSessionFailure(error)
            if (!isCurrentClimateRevision(generation, revision)) return@runOnMain
            status = climatePreControlStatus
            climatePreControlStatus = null
            climateOptimisticGuard = null
            pendingClimateConfirmation = null
            controlFeedback = ControlFeedback(
                climateControlFailureMessage(command.label, error),
                ControlFeedbackKind.ERROR
            )
            climateTemperatureRequestId?.let {
                updateClimateTemperatureRequest(it, ClimateControlRequestPhase.FAILED)
            }
        }
    }

    private fun scheduleClimateResultQuery(
        generation: Long,
        revision: Long,
        pollingId: String,
        optimisticUpdate: ClimateOptimisticUpdate?,
        telemetryExpectation: ClimateTelemetryExpectation?,
        attemptIndex: Int
    ) {
        val delayMs = ClimateControlConfirmationSchedule.resultQueryDelayMs(attemptIndex) ?: return
        mainHandler.postDelayed({
            if (!isClimateConfirmationPending(generation, revision)) return@postDelayed
            worker.execute {
                if (!isClimateConfirmationPending(generation, revision)) return@execute
                queryClimateControlResult(
                    generation,
                    revision,
                    pollingId,
                    optimisticUpdate,
                    telemetryExpectation,
                    attemptIndex
                )
            }
        }, delayMs)
    }

    private fun queryClimateControlResult(
        generation: Long,
        revision: Long,
        pollingId: String,
        optimisticUpdate: ClimateOptimisticUpdate?,
        telemetryExpectation: ClimateTelemetryExpectation?,
        attemptIndex: Int
    ) {
        try {
            val api = LeapmotorApi(session)
            val response = api.queryControlResult(pollingId)
            sessionStore.save(session)
            if (SentryModeControlPolicy.querySucceeded(response.opt("result"), response.opt("code"))) {
                readClimateTelemetryAfterResult(api, generation, revision, optimisticUpdate, telemetryExpectation)
            } else {
                scheduleNextClimateResultQuery(
                    generation,
                    revision,
                    pollingId,
                    optimisticUpdate,
                    telemetryExpectation,
                    attemptIndex
                )
            }
        } catch (e: Exception) {
            runOnMain(generation) { handleSessionFailure(e) }
            if (!SessionExpiry.isRefreshTokenInvalid(e.message)) {
                scheduleNextClimateResultQuery(
                    generation,
                    revision,
                    pollingId,
                    optimisticUpdate,
                    telemetryExpectation,
                    attemptIndex
                )
            }
        }
    }

    private fun scheduleNextClimateResultQuery(
        generation: Long,
        revision: Long,
        pollingId: String,
        optimisticUpdate: ClimateOptimisticUpdate?,
        telemetryExpectation: ClimateTelemetryExpectation?,
        attemptIndex: Int
    ) {
        val nextAttempt = attemptIndex + 1
        if (nextAttempt < ClimateControlConfirmationSchedule.resultQueryAttempts) {
            scheduleClimateResultQuery(
                generation,
                revision,
                pollingId,
                optimisticUpdate,
                telemetryExpectation,
                nextAttempt
            )
        }
    }

    private fun readClimateTelemetryAfterResult(
        api: LeapmotorApi,
        generation: Long,
        revision: Long,
        optimisticUpdate: ClimateOptimisticUpdate?,
        telemetryExpectation: ClimateTelemetryExpectation?
    ) {
        val refreshed = try {
            val latest = VehicleStatusMapper.withFuelMock(
                status = api.getVehicleState(),
                vin = session.selectedVin,
                powerType = vehicleConfig.powerType
            )
            sessionStore.save(session)
            parseStatus(latest)
        } catch (e: Exception) {
            runOnMain(generation) { handleSessionFailure(e) }
            return
        }
        val telemetryConfirmed = climateTelemetryMatches(optimisticUpdate, telemetryExpectation, refreshed)
        runOnMain(generation) {
            if (!isClimateConfirmationPending(generation, revision)) return@runOnMain
            if (telemetryExpectation != null || telemetryConfirmed) {
                status = refreshed
                climateOptimisticGuard = null
                climatePreControlStatus = null
            }
            if (telemetryConfirmed) {
                confirmClimateTelemetryIfMatched(revision, refreshed)
            }
        }
    }

    private fun scheduleClimateTelemetryRefreshes(generation: Long, revision: Long) {
        repeat(ClimateControlConfirmationSchedule.telemetryRefreshAttempts) { attemptIndex ->
            val elapsedMs = ClimateControlConfirmationSchedule.telemetryRefreshElapsedMs(attemptIndex)
                ?: return@repeat
            mainHandler.postDelayed({
                if (!isClimateConfirmationPending(generation, revision)) return@postDelayed
                val isFinalAttempt = attemptIndex == ClimateControlConfirmationSchedule.telemetryRefreshAttempts - 1
                if (isFinalAttempt && climateOptimisticGuard?.revision == revision) {
                    climateOptimisticGuard = null
                }
                refreshStatus(
                    silent = true,
                    completion = if (isFinalAttempt) {
                        { finishClimateConfirmationWindow(generation, revision) }
                    } else {
                        null
                    }
                )
            }, elapsedMs)
        }
    }

    private fun finishClimateConfirmationWindow(generation: Long, revision: Long) {
        if (!isClimateConfirmationPending(generation, revision)) return
        pendingClimateConfirmation = null
        climatePreControlStatus = null
    }

    private fun isCurrentClimateRevision(generation: Long, revision: Long): Boolean =
        !activityDestroyed && generation == operationGeneration && revision == climateStatusRevision

    private fun isClimateConfirmationPending(generation: Long, revision: Long): Boolean =
        isCurrentClimateRevision(generation, revision) && pendingClimateConfirmation?.revision == revision

    private fun climateControlFailureMessage(label: String, error: Exception): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("token", ignoreCase = true) || raw.contains("鉴权") -> "登录已失效"
            raw.contains("timeout", ignoreCase = true) || raw.contains("超时") -> "空调响应超时"
            raw.contains("网络") || raw.contains("连接") -> "空调网络异常"
            else -> ClimateControlFeedbackText.failed(label)
        }
    }

    private fun completeClimatePost(
        climateControlRevision: Long,
        optimisticUpdate: ClimateOptimisticUpdate?
    ) {
        optimisticUpdate?.let { update ->
            applyClimateOptimisticUpdate(update)
            climateOptimisticGuard = ClimateOptimisticGuard(
                revision = climateControlRevision,
                update = update,
                postCompleted = true
            )
        }
        climatePreControlStatus = null
    }

    private fun updateClimateTemperatureRequest(requestId: Long, phase: ClimateControlRequestPhase) {
        climateTemperatureRequestState = ClimateControlRequestState(requestId, phase)
    }

    private fun applyClimateOptimisticUpdate(update: ClimateOptimisticUpdate) {
        status?.let { current ->
            status = current.copy(
                acSwitch = update.acSwitch ?: current.acSwitch,
                acSetting = update.acSetting ?: current.acSetting,
                windshieldDefrost = update.windshieldDefrost ?: current.windshieldDefrost
            )
        }
    }

    private fun climateTelemetryMatches(
        optimisticUpdate: ClimateOptimisticUpdate?,
        telemetryExpectation: ClimateTelemetryExpectation?,
        refreshed: VehicleStatus
    ): Boolean = ClimateTelemetryConfirmationPolicy.matches(
        optimisticUpdate = optimisticUpdate,
        telemetryExpectation = telemetryExpectation,
        readback = refreshed.climateTelemetryReadback()
    )

    private fun VehicleStatus.climateTelemetryReadback(): ClimateTelemetryReadback = ClimateTelemetryReadback(
        acSwitch = acSwitch,
        temperatureC = normalizedDetailedClimateValue(acSetting),
        windLevel = acAirVolume?.toBigDecimalOrNull()
            ?.stripTrailingZeros()
            ?.takeIf { it.scale() <= 0 }
            ?.let { runCatching { it.intValueExact() }.getOrNull() },
        circle = AirCircle.fromTelemetryValue(recirculationMode),
        windshieldDefogging = windshieldDefrost
    )

    private fun registerPendingClimateConfirmation(
        revision: Long,
        command: ControlCommand,
        optimisticUpdate: ClimateOptimisticUpdate?,
        telemetryExpectation: ClimateTelemetryExpectation?
    ) {
        pendingClimateConfirmation = PendingClimateConfirmation(
            revision = revision,
            label = command.label,
            optimisticUpdate = optimisticUpdate,
            telemetryExpectation = telemetryExpectation
        )
    }

    private fun confirmClimateTelemetryIfMatched(revision: Long, refreshed: VehicleStatus): Boolean {
        val pending = pendingClimateConfirmation ?: return false
        if (pending.revision != revision || !climateTelemetryMatches(
                optimisticUpdate = pending.optimisticUpdate,
                telemetryExpectation = pending.telemetryExpectation,
                refreshed = refreshed
            )
        ) {
            return false
        }
        pendingClimateConfirmation = null
        controlFeedback = ControlFeedback(
            climateConfirmedFeedback(pending.label, pending.telemetryExpectation),
            ControlFeedbackKind.SUCCESS
        )
        return true
    }

    private fun climateConfirmedFeedback(
        label: String,
        expectation: ClimateTelemetryExpectation?
    ): String = if (expectation?.hasUnavailableFields == true) {
        ClimateControlFeedbackText.confirmedAvailableFields(label)
    } else {
        ClimateControlFeedbackText.confirmed(label)
    }

    private fun normalizedDetailedClimateValue(value: String?): Int? = value
        ?.trim()
        ?.replace(Regex("\\s*°?\\s*C\\s*$", RegexOption.IGNORE_CASE), "")
        ?.trim()
        ?.toBigDecimalOrNull()
        ?.stripTrailingZeros()
        ?.takeIf { it.scale() <= 0 }
        ?.let { runCatching { it.intValueExact() }.getOrNull() }

    private fun applyClimateSettings(command: AirConditioningCommand) {
        val controlCommand = runCatching { Commands.buildDetailedAc(command) }.getOrElse { error ->
            controlFeedback = ControlFeedback(error.message ?: "空调设置无效", ControlFeedbackKind.WARNING)
            return
        }
        submitClimateControl(
            command = controlCommand,
            telemetryExpectation = Commands.detailedAcExpectation(command)
        )
    }

    private fun preserveOptimisticClimate(
        refreshed: VehicleStatus,
        update: ClimateOptimisticUpdate?
    ): VehicleStatus {
        if (update == null) return refreshed
        val merged = ClimateOptimisticUpdates.mergeOver(
            update,
            ClimateTelemetrySnapshot(
                refreshed.acSwitch,
                refreshed.acSetting,
                refreshed.windshieldDefrost
            )
        )
        return refreshed.copy(
            acSwitch = merged.acSwitch,
            acSetting = merged.acSetting,
            windshieldDefrost = merged.windshieldDefrost
        )
    }

    private fun parseStatus(m: JSONObject): VehicleStatus {
        val tires = listOf(
            Triple("左前", "leftFrontTirePressure", "leftFrontTirePressureState") ,
            Triple("右前", "rightFrontTirePressure", "rightFrontTirePressureState"),
            Triple("左后", "leftRearTirePressure", "leftRearTirePressureState"),
            Triple("右后", "rightRearTirePressure", "rightRearTirePressureState")
        )
        val tireList = mutableListOf<TireStatus>()
        for ((label, pressureKey, stateKey) in tires) {
            val pressure = formatValue(m.opt(pressureKey), "kPa")
            val temperature = findTireTemperature(m, pressureKey.removeSuffix("Pressure"))
            if (pressure == null && temperature == null && m.opt(stateKey) == null) continue
            tireList.add(TireStatus(label, pressure, temperature, m.optBool(stateKey) == true))
        }
        val charge = m.opt("chargeState")
        val configuredPower = vehicleConfig.powerType
        val pureElectric = configuredPower == SessionStore.VehiclePowerType.PURE_ELECTRIC ||
            (configuredPower == null && session.selectedCarType.isPureElectricModel())
        val fuelMileage = VehicleStatusMapper.fuelRange(m)?.let { "$it km" }
        val electricMileage = VehicleStatusMapper.electricRange(m)?.let { "$it km" }
        val combinedMileage = VehicleStatusMapper.combinedRange(m)?.let { "$it km" }
        val fuelSoc = VehicleStatusMapper.fuelSocPercent(m)
        val inferredRangeExtender = !pureElectric &&
            (fuelSoc != null || (fuelMileage != null && combinedMileage != null))
        val rangeExtender = when (configuredPower) {
            SessionStore.VehiclePowerType.RANGE_EXTENDER -> true
            SessionStore.VehiclePowerType.PURE_ELECTRIC -> false
            null -> inferredRangeExtender
        }
        val displayPowerType = when {
            configuredPower == SessionStore.VehiclePowerType.PURE_ELECTRIC -> VehicleStatusMapper.PowerType.PURE_ELECTRIC
            configuredPower == SessionStore.VehiclePowerType.RANGE_EXTENDER -> VehicleStatusMapper.PowerType.RANGE_EXTENDER
            inferredRangeExtender -> VehicleStatusMapper.PowerType.RANGE_EXTENDER
            else -> null
        }
        val displayMileage = when {
            displayPowerType == VehicleStatusMapper.PowerType.RANGE_EXTENDER ->
                VehicleStatusMapper.combinedRange(m) ?: VehicleStatusMapper.remainingRange(m, session.selectedCarType, displayPowerType)
            else -> VehicleStatusMapper.remainingRange(m, session.selectedCarType, displayPowerType)
        }
        return VehicleStatus(
            soc = formatPercentage(m.opt("soc")),
            preciseSoc = formatPercentage(m.opt("preciseSoc")),
            fuelSoc = formatPercentage(m.opt("fuelSoc")),
            mileage = displayMileage?.let { "${it}km" },
            fuelMileage = if (rangeExtender) fuelMileage else null,
            electricMileage = if (rangeExtender) electricMileage else null,
            combinedMileage = if (rangeExtender) combinedMileage else null,
            rangeExtender = rangeExtender,
            totalMileage = formatValue(m.opt("totalMileage"), " km"),
            averageEnergyConsumption = formatEnergyConsumption(m),
            chargingPower = ChargeStatus.power(m),
            chargeRemainTime = ChargeStatus.remainingTime(m.opt("chargeRemainTime")),
            batteryVoltage = formatValue(m.opt("batteryVoltage"), " V"),
            batteryCurrent = ChargeStatus.displayCurrent(m)?.let { "$it A" },
            chargeType = ChargeStatus.type(m),
            minBatteryTemp = formatValue(m.opt("minBatteryTemp"), " °C"),
            batteryPreheatEnabled = BatteryPreheatState.fromRaw(m.opt("batteryThermalRequest")),
            healthyChargeEnabled = m.optBool("healthyChargeEnabled"),
            rangeMode = formatRawState(m.opt("rangeMode")),
            speed = formatSpeed(m.opt("speed")),
            isDriving = WidgetStatusMapper.isDriving(m),
            gearStatus = formatGear(m.opt("gearStatus")),
            locked = m.optBool("driverDoorLockStatus"),
            acSwitch = m.optBool("acSwitch"),
            acSetting = formatClimateSetting(m.opt("acSetting")),
            acSettingRight = formatClimateSetting(m.opt("acSettingRight")),
            acCoolingAndHeating = m.opt("acCoolingAndHeating")?.toString()?.toIntOrNull(),
            climateMode = m.opt("climateMode")?.toString()?.toIntOrNull(),
            acOperateMode = m.opt("acOperateMode")?.toString()?.toIntOrNull(),
            recirculationMode = AirCircle.fromVehicleTelemetry(
                primaryValue = m.opt("recirculationMode"),
                legacyValue = m.opt("acCircleMode")
            )?.telemetryValue,
            // Legacy T03 reports cabin temperature as minSingleTemp instead of interiorTemp.
            indoorTemp = formatValue(m.opt("interiorTemp") ?: m.opt("minSingleTemp"), " °C"),
            acAirVolume = ClimateSignalValue.raw(m.opt("acAirVolume")),
            windshieldDefrost = m.optBool("windshieldDefrost"),
            rearWindowHeating = ClimateSignalValue.boolean(m.opt("rearWindowHeating")),
            sentryMode = m.optBool("sentryMode"),
            windowStatusAvailable = WidgetStatusMapper.hasWindowTelemetry(m, session.selectedCarType),
            openWindows = WidgetStatusMapper.openWindowLabels(m, session.selectedCarType),
            tires = tireList,
            chargeLabel = ChargeStatus.label(charge?.toString()?.toIntOrNull()),
            chargeState = charge?.toString()?.toIntOrNull(),
            trunkState = TrunkStateMapper.fromSignal(m.opt("bbcmBackDoorStatus")),
            driverDoorOpen = m.optBool("lbcmDriverDoorStatus") == true,
            passengerDoorOpen = m.optBool("rbcmDriverDoorStatus") == true,
            leftRearDoorOpen = m.optBool("lbcmLeftRearDoorStatus") == true,
            rightRearDoorOpen = m.optBool("rbcmRightRearDoorStatus") == true,
            anyDoorOpen = (m.optBool("lbcmDriverDoorStatus") == true) ||
                (m.optBool("rbcmDriverDoorStatus") == true) ||
                (m.optBool("lbcmLeftRearDoorStatus") == true) ||
                (m.optBool("rbcmRightRearDoorStatus") == true),
            driverSeatHeating = m.opt("driverSeatHeating")?.toString()?.toIntOrNull(),
            driverSeatVentilation = m.opt("driverSeatVentilation")?.toString()?.toIntOrNull(),
            passengerSeatHeating = m.opt("passengerSeatHeating")?.toString()?.toIntOrNull(),
            passengerSeatVentilation = m.opt("passengerSeatVentilation")?.toString()?.toIntOrNull(),
            steeringWheelHeating = m.optBool("steeringWheelHeating")
        )
    }

    private fun formatValue(v: Any?, unit: String): String? =
        if (v == null || v == JSONObject.NULL) null else "$v$unit"

    private fun formatClimateSetting(v: Any?): String? {
        val raw = ClimateSignalValue.raw(v) ?: return null
        return Commands.normalizedAcTemperature(raw)?.let { "$it °C" } ?: raw
    }

    private fun formatPercentage(v: Any?): String? {
        if (v == null || v == JSONObject.NULL) return null
        val value = v.toString().trim().removeSuffix("%").trim()
        return value.takeIf { it.isNotBlank() }?.let { "$it%" }
    }

    private fun formatRawState(v: Any?): String? =
        if (v == null || v == JSONObject.NULL) null else v.toString().trim().takeIf { it.isNotBlank() }

    private fun parseSpeed(v: Any?): Double? =
        if (v == null || v == JSONObject.NULL) null
        else v.toString().trim().removeSuffix("km/h").trim().toDoubleOrNull()

    private fun formatSpeed(v: Any?): String? = parseSpeed(v)?.let { speed ->
        val value = if (speed % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", speed)
        } else {
            String.format(Locale.US, "%.1f", speed)
        }
        "$value km/h"
    }

    private fun formatGear(v: Any?): String? {
        if (v == null || v == JSONObject.NULL) return null
        val raw = v.toString().trim()
        if (raw.isBlank()) return null
        return when (raw.uppercase()) {
            "P", "PARK", "驻车", "停车", "0" -> "P挡"
            "R", "REVERSE", "倒车", "1" -> "R挡"
            "N", "NEUTRAL", "空挡", "2" -> "N挡"
            "D", "DRIVE", "前进", "3" -> "D挡"
            else -> raw
        }
    }

    private fun findTireTemperature(m: JSONObject, wheelPrefix: String): String? {
        val candidates = listOf("${wheelPrefix}Temperature", "${wheelPrefix}Temp", "${wheelPrefix}TireTemperature", "${wheelPrefix}TireTemp")
        return candidates.firstNotNullOfOrNull { key -> formatValue(m.opt(key), " °C") }
    }

    private fun formatEnergyConsumption(m: JSONObject): String? {
        val keys = listOf("averageEnergyConsumption", "comprehensiveEnergyConsumption", "integratedEnergyConsumption")
        return keys.firstNotNullOfOrNull { key -> formatValue(m.opt(key), " kWh/100km") }
    }

    private fun JSONObject.optBool(key: String): Boolean? = when (val v = opt(key)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> when (v) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
        else -> null
    }

    // --------------------------------------------------------------- 控车

    /** Schedules one post-lock safety check; it never turns a successful lock into a failure. */
    private fun scheduleParkingAnomalyCheck(generation: Long) {
        mainHandler.postDelayed({
            if (activityDestroyed || generation != operationGeneration || !loggedIn) return@postDelayed
            worker.execute {
                if (activityDestroyed || generation != operationGeneration) return@execute
                runCatching {
                    val currentSession = sessionStore.load()
                    if (currentSession.oldAuth == null ||
                        currentSession.newAuth == null ||
                        currentSession.selectedVin != session.selectedVin
                    ) return@runCatching
                    val latest = LeapmotorApi(currentSession).getVehicleState()
                    ParkingAnomalyNotificationManager.notifyIfNeeded(
                        this,
                        sessionStore,
                        currentSession.selectedCarType,
                        latest
                    )
                }
            }
        }, ParkingAnomalyPolicy.POST_LOCK_CHECK_DELAY_MS)
    }

    private fun maybeUpdateVehicleAddress(snapshot: VehicleLocationSnapshot) {
        val lat = snapshot.location.latitude
        val lng = snapshot.location.longitude
        val last = lastGeocodedLocation
        if (last != null &&
            kotlin.math.abs(last.first - lat) < 0.0002 &&
            kotlin.math.abs(last.second - lng) < 0.0002 &&
            vehicleAddress != null
        ) {
            return
        }
        lastGeocodedLocation = lat to lng
        val generation = operationGeneration
        worker.execute {
            val address = VehicleLocationGeocoder.reverseGeocode(lat, lng)
            if (address != null) {
                runOnMain(generation) {
                    vehicleAddress = address
                }
            }
        }
    }

    private fun control(name: String) {
        when (name) {
            "acOn" -> {
                submitClimateControl(
                    Commands.build(name),
                    optimisticUpdate = ClimateOptimisticUpdates.acOn(),
                    telemetryExpectation = Commands.climateExpectation(name)
                )
                return
            }
            "acOff" -> {
                submitClimateControl(
                    Commands.build(name),
                    optimisticUpdate = ClimateOptimisticUpdates.acOff(),
                    telemetryExpectation = Commands.climateExpectation(name)
                )
                return
            }
            "defrost" -> {
                submitClimateControl(
                    Commands.build(name),
                    optimisticUpdate = ClimateOptimisticUpdates.windshieldDefrost(),
                    telemetryExpectation = Commands.climateExpectation(name)
                )
                return
            }
            "quickCool" -> {
                submitClimateControl(
                    Commands.build(name),
                    optimisticUpdate = ClimateOptimisticUpdates.temperature(18),
                    telemetryExpectation = Commands.climateExpectation(name)
                )
                return
            }
            "quickHeat" -> {
                submitClimateControl(
                    Commands.build(name),
                    optimisticUpdate = ClimateOptimisticUpdates.temperature(32),
                    telemetryExpectation = Commands.climateExpectation(name)
                )
                return
            }
            "deodorize" -> {
                submitClimateControl(
                    Commands.build(name),
                    telemetryExpectation = Commands.climateExpectation(name)
                )
                return
            }
        }
        control(Commands.build(name), commandName = name)
    }

    /** Routes a remote-control command through the existing safety path. */
    private fun control(command: ControlCommand) {
        control(command, commandName = null)
    }

    private fun control(command: ControlCommand, commandName: String?) {
        val savedPin = sessionStore.loadOpPassword()
        if (savedPin.isNullOrEmpty()) {
            requestOperationPassword(
                action = { control(command, commandName) }
            )
            return
        }
        val sentryTarget = commandName?.let(SentryModeControlPolicy::targetEnabled)
        busy { generation ->
            runOnMain(generation) {
                controlFeedback = ControlFeedback("${command.label}：正在发送", ControlFeedbackKind.IN_PROGRESS)
            }
            try {
                val api = LeapmotorApi(session)
                if (sentryTarget != null) {
                    val latestBeforeSend = VehicleStatusMapper.withFuelMock(
                        status = api.getVehicleState(),
                        vin = session.selectedVin,
                        powerType = vehicleConfig.powerType
                    )
                    sessionStore.save(session)
                    val latestSentryMode = latestBeforeSend.optBool("sentryMode")
                    val latestStatus = parseStatus(latestBeforeSend)
                    runOnMain(generation) { status = latestStatus }
                    if (SentryModeControlPolicy.isConfirmed(sentryTarget, latestSentryMode)) {
                        runOnMain(generation) {
                            controlFeedback = ControlFeedback(
                                "${command.label}：车辆已处于目标状态",
                                ControlFeedbackKind.SUCCESS
                            )
                        }
                        refreshStatusAfterControl(generation)
                        return@busy
                    }
                }
                val result = api.sendControl(command, savedPin)
                sessionStore.save(session)
                val shouldQueryControlResult = result.hasPollingId()
                if (!shouldQueryControlResult) {
                    runOnMain(generation) {
                        controlFeedback = ControlFeedback(
                            "${command.label}：已发送，请稍后刷新车况确认",
                            ControlFeedbackKind.WARNING
                        )
                    }
                    refreshStatusAfterControl(generation)
                    if (commandName != null && ParkingAnomalyPolicy.shouldCheck(commandName, commandAccepted = true)) {
                        scheduleParkingAnomalyCheck(generation)
                    }
                    return@busy
                }
                runOnMain(generation) {
                    controlFeedback = ControlFeedback("${command.label}：等待车辆响应", ControlFeedbackKind.IN_PROGRESS)
                }
                var finalText = "${command.label}：已发送，暂未收到车辆响应"
                var completed = false
                var sentryQuerySucceeded = false
                for (i in 0 until ControlResultPollingPolicy.appMaxAttempts) {
                    Thread.sleep(ControlResultPollingPolicy.delayBeforeAttempt(i))
                    val resp = api.queryControlResult(result.msgID)
                    sessionStore.save(session)
                    if (SentryModeControlPolicy.querySucceeded(resp.opt("result"), resp.opt("code"))) {
                        if (sentryTarget != null) {
                            sentryQuerySucceeded = true
                            val latest = VehicleStatusMapper.withFuelMock(
                                status = api.getVehicleState(),
                                vin = session.selectedVin,
                                powerType = vehicleConfig.powerType
                            )
                            val latestSentryMode = latest.optBool("sentryMode")
                            if (!SentryModeControlPolicy.isConfirmed(sentryTarget, latestSentryMode)) {
                                continue
                            }
                            val confirmedStatus = parseStatus(latest)
                            runOnMain(generation) { status = confirmedStatus }
                        }
                        finalText = "${command.label}：已完成"
                        completed = true
                        break
                    }
                }
                if (!completed && sentryTarget != null && sentryQuerySucceeded) {
                    finalText = "${command.label}：命令已执行，车辆状态待确认"
                }
                runOnMain(generation) {
                    controlFeedback = ControlFeedback(
                        finalText,
                        if (completed) ControlFeedbackKind.SUCCESS else ControlFeedbackKind.WARNING
                    )
                }
                if (completed) {
                    refreshStatusAfterControl(generation)
                }
                if (commandName != null && ParkingAnomalyPolicy.shouldCheck(commandName, commandAccepted = completed)) {
                    scheduleParkingAnomalyCheck(generation)
                }
            } catch (e: Exception) {
                runOnMain(generation) {
                    handleSessionFailure(e)
                    controlFeedback = ControlFeedback(controlFailureMessage(command.label, e), ControlFeedbackKind.ERROR)
                }
            }
        }
    }

    private fun controlFailureMessage(label: String, error: Exception): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("token", ignoreCase = true) || raw.contains("鉴权") -> "登录状态已过期，请重新登录"
            raw.contains("timeout", ignoreCase = true) || raw.contains("超时") -> "${label}超时，请稍后重试"
            raw.contains("网络") || raw.contains("连接") -> "网络异常，${label}未完成"
            else -> "${label}失败，请稍后重试"
        }
    }

    // --------------------------------------------------------------- 工具

    private fun toast(text: String, generation: Long? = null) {
        runOnMain(generation) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(throwable, "UncaughtException: thread=${thread.name}")
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun recordCrash(throwable: Throwable, source: String) {
        try {
            val trace = android.util.Log.getStackTraceString(throwable)
            val crashReport = "[$source] ${throwable.javaClass.name}: ${throwable.message}\n$trace"
            getSharedPreferences("leap_crash_report", Context.MODE_PRIVATE)
                .edit()
                .putString("last_crash_trace", crashReport)
                .putLong("last_crash_time", System.currentTimeMillis())
                .commit()
            val logFile = java.io.File(filesDir, "last_crash.log")
            logFile.writeText(crashReport)
            ErrorLogs.repository.record(
                ErrorLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    category = ErrorLogCategory.PAGE_ERROR,
                    stage = source,
                    appVersion = BuildConfig.VERSION_NAME,
                    message = crashReport
                )
            )
        } catch (_: Throwable) {}
    }

    private fun checkLastCrashReport() {
        val crashPrefs = getSharedPreferences("leap_crash_report", Context.MODE_PRIVATE)
        val lastCrash = crashPrefs.getString("last_crash_trace", null)
        if (!lastCrash.isNullOrBlank()) {
            android.util.Log.e("MainActivity", "检测到应用上次异常退出:\n$lastCrash")
            ErrorLogs.repository.record(
                ErrorLogEntry(
                    timestampMs = crashPrefs.getLong("last_crash_time", System.currentTimeMillis()),
                    category = ErrorLogCategory.PAGE_ERROR,
                    stage = "last_crash_recovery",
                    appVersion = BuildConfig.VERSION_NAME,
                    message = "检测到应用上次异常退出:\n$lastCrash"
                )
            )
            crashPrefs.edit().remove("last_crash_trace").apply()
        }
    }

    private fun recordPageError(stage: String, message: String) {
        ErrorLogs.repository.record(ErrorLogEntry(System.currentTimeMillis(), ErrorLogCategory.PAGE_ERROR, stage, appVersion = BuildConfig.VERSION_NAME, message = message))
    }

    private fun runOnMain(generation: Long? = null, block: () -> Unit) {
        if (activityDestroyed) return
        mainHandler.post {
            if (!activityDestroyed && !isFinishing && !isDestroyed &&
                (generation == null || generation == operationGeneration)
            ) {
                try {
                    block()
                } catch (t: Throwable) {
                    android.util.Log.e("MainActivity", "runOnMain unhandled error", t)
                    recordCrash(t, "runOnMain")
                }
            }
        }
    }

    /** Operations run serially; logout invalidates queued or delayed UI updates. */
    private fun busy(showBusy: Boolean = true, action: (Long) -> Unit) {
        val generation = operationGeneration
        if (showBusy) {
            busy = true
            mainHandler.removeCallbacks(authorSupportPromptRunnable)
        }
        worker.execute {
            if (activityDestroyed || generation != operationGeneration) return@execute
            try {
                action(generation)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                toast(e.message ?: e.toString(), generation)
            } finally {
                if (showBusy) runOnMain(generation) {
                    busy = false
                    maybeShowAuthorSupportPrompt()
                }
            }
        }
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 10_000L
        const val POST_CONTROL_STATUS_REFRESH_DELAY_MS = 2_000L
    }
}
