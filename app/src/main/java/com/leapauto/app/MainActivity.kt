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
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.leapauto.app.ui.LeapAutoScreen
import com.leapauto.app.ui.BluetoothKeyDialog
import com.leapauto.app.ui.BluetoothActionConfirmation
import com.leapauto.app.ui.BluetoothConfigurationConfirmation
import com.leapauto.app.ui.BluetoothCalibrationConfirmation
import com.leapauto.app.ui.theme.LeapAutoTheme
import com.leapauto.app.bluetooth.BleAccessPolicy
import com.leapauto.app.bluetooth.BleConnectionPhase
import com.leapauto.app.bluetooth.BleConnectionState
import com.leapauto.app.bluetooth.BleControlConfirmation
import com.leapauto.app.bluetooth.BleDiagnosticEvent
import com.leapauto.app.bluetooth.BleVehicleMetadata
import com.leapauto.app.bluetooth.BleDiagnostics
import com.leapauto.app.bluetooth.BleKeyCertificate
import com.leapauto.app.bluetooth.BleLockAction
import com.leapauto.app.bluetooth.BleNearbyDevice
import com.leapauto.app.bluetooth.BlePermissionPolicy
import com.leapauto.app.bluetooth.BleSessionIdentity
import com.leapauto.app.bluetooth.BleCertificateSync
import com.leapauto.app.bluetooth.BluetoothKeyController
import com.leapauto.app.bluetooth.BleKeyRuntime
import com.leapauto.app.bluetooth.BleKeyService
import com.leapauto.app.bluetooth.BleManagedKey
import com.leapauto.app.bluetooth.BlePassiveConfiguration
import com.leapauto.app.bluetooth.BleCalibration
import com.leapauto.app.bluetooth.BleCloudCoordinator
import com.leapauto.app.bluetooth.BleCloudUiState
import com.leapauto.app.bluetooth.BleVehicleProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val isShutDown: Boolean = false,
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
    val leftFrontWindowPercent: Int? = null,
    val rightFrontWindowPercent: Int? = null,
    val leftRearWindowPercent: Int? = null,
    val rightRearWindowPercent: Int? = null,
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
    val leftRearSeatHeating: Int? = null,
    val leftRearSeatVentilation: Int? = null,
    val rightRearSeatHeating: Int? = null,
    val rightRearSeatVentilation: Int? = null,
    val steeringWheelHeating: Boolean? = null,
    val steeringWheelHeatingLevel: Int? = null,
    val rearviewMirrorHeating: Boolean? = null,
    val acSettingRight: String? = null,
    val chargeScheduleEnabled: Boolean? = null,
    val chargeScheduleStart: String? = null,
    val chargeScheduleEnd: String? = null,
    val chargeScheduleCycles: String? = null,
    val chargeScheduleCirculation: Int? = null,
    val chargeScheduleRecharge: Boolean? = null,
    val chargeScheduleSocLimit: Int? = null,
    val chargeGunConnected: Boolean = false,
    val roofOpeningPercent: Int? = null,
    val fridgeStatus: FridgeStatus? = null,
    val carType: String? = null
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
    val telemetryExpectation: ClimateTelemetryExpectation?,
    val climateTemperatureRequestId: Long?
)

private data class PendingBluetoothCalibration(
    val calibration: BleCalibration?,
    val identity: BleSessionIdentity,
    val generation: Long
)

class MainActivity : ComponentActivity() {

    private lateinit var sessionStore: SessionStore
    private lateinit var energyCacheStore: EnergyCacheStore
    private lateinit var session: Session
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val asyncWorker = Executors.newFixedThreadPool(3)
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
    private var liveWeather by mutableStateOf<com.leapauto.app.weather.LiveWeather?>(null)
    @Volatile private var lastGeocodedLocation: Pair<Double, Double>? = null
    private var statusError by mutableStateOf("")
    private var controlFeedback by mutableStateOf<ControlFeedback?>(null)
    private var activeControlCommandName by mutableStateOf<String?>(null)
    private var climateTemperatureRequestState by mutableStateOf(ClimateControlRequestState())
    @Volatile private var climateStatusRevision = 0L
    @Volatile private var climateOptimisticGuard: ClimateOptimisticGuard? = null
    @Volatile private var optimisticWindowPercent: Int? = null
    @Volatile private var lastWindowActionEpochMs = 0L
    @Volatile private var optimisticTrunkState: TrunkState? = null
    @Volatile private var lastTrunkActionEpochMs = 0L
    @Volatile private var optimisticLockState: Boolean? = null
    @Volatile private var lastLockActionEpochMs = 0L
    private val lockStatusRefreshRunnables = mutableListOf<Runnable>()
    @Volatile private var optimisticDriverSeatHeating: Int? = null
    @Volatile private var optimisticDriverSeatVentilation: Int? = null
    @Volatile private var optimisticPassengerSeatHeating: Int? = null
    @Volatile private var optimisticPassengerSeatVentilation: Int? = null
    @Volatile private var optimisticLeftRearSeatHeating: Int? = null
    @Volatile private var optimisticLeftRearSeatVentilation: Int? = null
    @Volatile private var optimisticRightRearSeatHeating: Int? = null
    @Volatile private var optimisticRightRearSeatVentilation: Int? = null
    @Volatile private var optimisticSteeringWheelHeating: Boolean? = null
    @Volatile private var optimisticRearviewMirrorHeating: Boolean? = null
    @Volatile private var lastComfortActionEpochMs = 0L
    @Volatile private var optimisticFridgeStatus: FridgeStatus? = null
    @Volatile private var lastFridgeActionEpochMs = 0L
    private var climatePreControlStatus: VehicleStatus? = null
    @Volatile private var pendingClimateConfirmation: PendingClimateConfirmation? = null
    private var pendingStatusRefreshCompletion: ((Boolean) -> Unit)? = null
    private var pinSaved by mutableStateOf(false)
    private var pinSetupInProgress by mutableStateOf(false)
    private var pinSetupErrorMessage by mutableStateOf("")
    private var pendingPinProtectedAction: (() -> Unit)? = null
    private var pendingPinProtectedCancelAction: (() -> Unit)? = null
    private var showVehicleConfigConfirmationPrompt by mutableStateOf(false)
    private var phone by mutableStateOf("")
    private var code by mutableStateOf("")
    private var smsCountdownSeconds by mutableStateOf(0)
    private var pin by mutableStateOf("")
    private var widget4x2Actions by mutableStateOf(Widget4x2ActionPolicy.DEFAULT_ACTIONS)
    private var widgetBackgroundStyle by mutableIntStateOf(SessionStore.WIDGET_BG_STYLE_DEFAULT)
    private var widgetOpacity by mutableIntStateOf(SessionStore.WIDGET_OPACITY_OPAQUE)
    private var appearanceMode by mutableStateOf(AppearanceMode.SYSTEM)
    private var energyState by mutableStateOf<EnergyAnalyticsState>(EnergyAnalyticsState.Idle)
    @Volatile private var energyLastSuccessAt = 0L
    private var vehicleConfig by mutableStateOf(SessionStore.VehicleConfig())
    private var hvacCapability by mutableStateOf(HvacCapability.fallback())
    private var networkDebugEnabled by mutableStateOf(false)
    private var healthyChargeLimitSoc by mutableStateOf(80)
    private var scheduledChargeEnabled by mutableStateOf(false)
    private var scheduledChargeStartTime by mutableStateOf("23:00")
    private var scheduledChargeEndTime by mutableStateOf("07:00")
    private var scheduledChargeContinueUntilLimit by mutableStateOf(true)
    private var scheduledChargeCirculation by mutableIntStateOf(1)
    private var scheduledChargeCycles by mutableStateOf("1,1,1,1,1,1,1")
    private var scheduledPreheatEnabled by mutableStateOf(false)
    private var scheduledPreheatStartTime by mutableStateOf("23:00")
    private var scheduledPreheatDays by mutableStateOf("1,1,1,1,1,1,1")
    private var signalMapDebugState by mutableStateOf<VehicleSignalMapDebugState>(VehicleSignalMapDebugState.Idle)
    private var versionUpdateState by mutableStateOf<VersionUpdateState>(VersionUpdateState.Idle)
    private var showPowerTypeDialog by mutableStateOf(false)
    private var handledUpdateVersion by mutableStateOf<String?>(null)
    private var downloadUpdateProgress by mutableStateOf<Int?>(null)
    private var showAuthorSupportDialog by mutableStateOf(false)
    private var showSessionExpiredDialog by mutableStateOf(false)
    private var availableVehicles by mutableStateOf<List<Vehicle>>(emptyList())
    private var vehicleImageVersion by mutableIntStateOf(0)
    private var activeGeetestChallenge by mutableStateOf<GeetestChallenge?>(null)
    private lateinit var bluetoothKeyController: BluetoothKeyController
    private lateinit var bluetoothRuntime: BleKeyRuntime
    private lateinit var bluetoothCloud: BleCloudCoordinator
    private var bluetoothCloudState by mutableStateOf(BleCloudUiState())
    private var bluetoothCalibrationConfirmation by mutableStateOf<PendingBluetoothCalibration?>(null)
    private var bluetoothManagedKey by mutableStateOf<BleManagedKey?>(null)
    private var bluetoothBackgroundRunning by mutableStateOf(false)
    private var bluetoothConfigurationConfirmation by mutableStateOf<BlePassiveConfiguration?>(null)
    private var bluetoothConfigurationIdentity: BleSessionIdentity? = null
    private var bluetoothSettingsRequestId by mutableStateOf(0L)
    private var showBluetoothKey by mutableStateOf(false)
    private var bluetoothPermissionsGranted by mutableStateOf(false)
    private var bluetoothKeyFeatureEnabled by mutableStateOf(false)
    private var bluetoothState by mutableStateOf(BleConnectionState())
    private var bluetoothSessionIdentity: BleSessionIdentity? = null
    private var bluetoothControlConfirmation by mutableStateOf<BleControlConfirmation?>(null)
    private var bluetoothPinRequestPending = false
    private var bluetoothReconnectDevice by mutableStateOf<BleNearbyDevice?>(null)
    private var bluetoothCertificate by mutableStateOf<BleKeyCertificate?>(null)
    private var bluetoothCertificateLoading by mutableStateOf(false)
    private var bluetoothCertificateMessage by mutableStateOf("")
    private var bluetoothGeneration = 0L
    private var bluetoothCertificateJob: Job? = null
    private var bluetoothPermissionGeneration: Long? = null
    private var bluetoothScanAfterPermissionGeneration: Long? = null

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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setupGlobalCrashHandler()
        checkLastCrashReport()
        ErrorLogs.repository.clear()
        NetworkDebugController.initialize(this)
        sessionStore = SessionStore(this)
        energyCacheStore = EnergyCacheStore(this)
        session = sessionStore.load()
        bluetoothKeyFeatureEnabled = sessionStore.loadBluetoothKeyFeatureEnabled()
        // 启动时零延迟预热最新天气（面向所有车辆开放）
        runCatching {
            val selectedVin = session.selectedVin
            liveWeather = com.leapauto.app.weather.AmapWeatherService.getLatestWeather(this, vin = selectedVin)
        }
        bluetoothRuntime = BleKeyRuntime.get(applicationContext)
        runCatching {
            bluetoothRuntime.attachSession(session)
        }
        bluetoothKeyController = bluetoothRuntime.controller
        bluetoothCloud = BleCloudCoordinator(applicationContext, lifecycleScope, ::currentBluetoothIdentity,
            { bluetoothRuntime.managedKey.value },
            bluetoothKeyController::recordDiagnostic)
        lifecycleScope.launch {
            bluetoothCloud.state.collect {
                bluetoothCloudState = it
                bluetoothKeyController.setVehicleMetadata(
                    BleVehicleProfile.freshMetadata(it.profile.metadata, System.currentTimeMillis()))
            }
        }
        lifecycleScope.launch {
            bluetoothRuntime.connection.collect { state ->
                bluetoothState = state
                if (!state.canControl) bluetoothControlConfirmation = null
                state.confirmedAction?.let { action ->
                    val isLock = action == BleLockAction.LOCK
                    val cmdName = if (isLock) "lock" else "unlock"
                    val label = if (isLock) "上锁" else "解锁"
                    controlFeedback = ControlFeedback(
                        ControlFeedbackFormatter.success(cmdName, label),
                        ControlFeedbackKind.SUCCESS
                    )
                    worker.execute {
                        runCatching { LeapmotorApi(session).uploadBluetoothRecord(action) }
                    }
                }
                if (state.phase == BleConnectionPhase.FAILED && controlFeedback?.kind == ControlFeedbackKind.IN_PROGRESS) {
                    controlFeedback = ControlFeedback(
                        state.detailMessage ?: "蓝牙控制未确认，可尝试再次操作",
                        ControlFeedbackKind.ERROR
                    )
                }
            }
        }
        lifecycleScope.launch { bluetoothRuntime.managedKey.collect { bluetoothManagedKey = it } }
        lifecycleScope.launch { bluetoothRuntime.backgroundRunning.collect { bluetoothBackgroundRunning = it } }
        hvacCapability = session.hvacCapability
        availableVehicles = sessionStore.loadVehicles()
        val defaultPower = VehiclePowerTypeResolver.fromCarType(session.selectedCarType)
        vehicleConfig = sessionStore.loadVehicleConfig(
            vin = session.selectedVin,
            defaultModel = session.selectedCarType,
            defaultNickname = session.selectedNickname,
            defaultYear = session.selectedYear,
            defaultPowerType = defaultPower
        )
        pin = sessionStore.loadOpPassword() ?: ""
        pinSaved = pin.isNotBlank()
        widget4x2Actions = sessionStore.loadWidget4x2Actions()
        widgetBackgroundStyle = sessionStore.loadWidgetBackgroundStyle()
        widgetOpacity = sessionStore.loadWidgetOpacity()
        appearanceMode = AppearanceMode.SYSTEM
        if (sessionStore.loadAppearanceMode() != AppearanceMode.SYSTEM) {
            sessionStore.saveAppearanceMode(AppearanceMode.SYSTEM)
        }
        handledUpdateVersion = sessionStore.loadHandledUpdateVersion()
        healthyChargeLimitSoc = sessionStore.loadHealthyChargeLimit(session.selectedVin)
        scheduledChargeEnabled = sessionStore.loadScheduledChargeEnabled(session.selectedVin)
        scheduledChargeStartTime = sessionStore.loadScheduledChargeStartTime(session.selectedVin)
        scheduledChargeEndTime = sessionStore.loadScheduledChargeEndTime(session.selectedVin)
        scheduledChargeContinueUntilLimit = sessionStore.loadScheduledChargeContinueUntilLimit(session.selectedVin)
        scheduledChargeCirculation = sessionStore.loadScheduledChargeCirculation(session.selectedVin)
        scheduledChargeCycles = sessionStore.loadScheduledChargeCycles(session.selectedVin)
        scheduledPreheatEnabled = sessionStore.loadScheduledPreheatEnabled(session.selectedVin)
        scheduledPreheatStartTime = sessionStore.loadScheduledPreheatStartTime(session.selectedVin)
        scheduledPreheatDays = sessionStore.loadScheduledPreheatDays(session.selectedVin)
        ChargeNotificationManager.ensureChannel(this)
        ParkingAnomalyNotificationManager.ensureChannel(this)
        if (pinSaved) {
            checkAndPromptPowerType(session.selectedVin)
        }

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
                    liveWeather = liveWeather,
                    vehicleVin = session.selectedVin,
                    statusError = statusError,
                    controlFeedback = controlFeedback,
                    activeControlCommand = activeControlCommandName,
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
                    pinSetupErrorMessage = pinSetupErrorMessage,
                    showVehicleConfigConfirmationPrompt = showVehicleConfigConfirmationPrompt,
                    availableVehicles = availableVehicles,
                    onSwitchVehicle = ::switchVehicle,
                    showPowerTypeDialog = showPowerTypeDialog,
                    onConfirmPowerType = { powerType ->
                        savePowerType(powerType)
                        showPowerTypeDialog = false
                    },
                    onDismissPowerTypeDialog = { showPowerTypeDialog = false },
                    bluetoothState = bluetoothState,
                    bluetoothKeyFeatureEnabled = bluetoothKeyFeatureEnabled,
                    onBluetoothKeyFeatureEnabledChange = ::updateBluetoothKeyFeatureEnabled,
                    appearanceMode = appearanceMode,
                    energyState = energyState,
                    healthyChargeLimitSoc = healthyChargeLimitSoc,
                    scheduledChargeEnabled = scheduledChargeEnabled,
                    scheduledChargeStartTime = scheduledChargeStartTime,
                    scheduledChargeEndTime = scheduledChargeEndTime,
                    scheduledChargeContinueUntilLimit = scheduledChargeContinueUntilLimit,
                    scheduledChargeCirculation = scheduledChargeCirculation,
                    scheduledChargeCycles = scheduledChargeCycles,
                    scheduledPreheatEnabled = scheduledPreheatEnabled,
                    scheduledPreheatStartTime = scheduledPreheatStartTime,
                    scheduledPreheatDays = scheduledPreheatDays,
                    onApplyChargingSettings = ::applyHealthyAndScheduledCharging,
                    onApplyScheduledPreheat = ::applyScheduledBatteryPreheat,
                    onRefreshChargingSettings = { syncChargePlanFromServer(force = true) },
                    onFetchParkingPhoto = ::fetchParkingPhoto,
                    networkDebugEnabled = networkDebugEnabled,
                    vehicleImageVersion = vehicleImageVersion,
                    onSelectCustomVehicleImage = ::handleCustomVehicleImage,
                    onResetCustomVehicleImage = ::resetCustomVehicleImage,
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
                    geetestChallenge = activeGeetestChallenge,
                    onGeetestSuccess = ::submitGeetestSolution,
                    onDismissGeetest = { activeGeetestChallenge = null },
                    onLoginWithRawAuth = ::loginWithRawAuth,
                    onLogin = { login() },
                    onSavePin = ::savePin,
                    onCancelPinSetup = ::cancelPinSetup,
                    widget4x2Actions = widget4x2Actions,
                    onWidget4x2ActionsChange = ::saveWidget4x2Actions,
                    widgetBackgroundStyle = widgetBackgroundStyle,
                    onWidgetBackgroundStyleChange = ::saveWidgetBackgroundStyle,
                    widgetOpacity = widgetOpacity,
                    onWidgetOpacityChange = ::saveWidgetOpacity,
                    onAppearanceModeChange = ::saveAppearanceMode,
                    onSaveVehicleConfig = ::saveVehicleConfig,
                    onUpdateNickname = ::updateVehicleNickname,
                    onNetworkDebugEnabledChange = { enabled ->
                        networkDebugEnabled = enabled
                        NetworkDebugController.setEnabled(enabled)
                    },
                    onCheckForUpdate = { checkForUpdate(force = true) },
                    onOpenUpdate = ::openUpdatePage,
                    onDismissVersionUpdatePrompt = ::markVersionUpdateHandled,
                    onOpenVersionUpdatePrompt = ::openVersionUpdatePage,
                    downloadUpdateProgress = downloadUpdateProgress,
                    onStartInAppUpdate = ::startInAppUpdateDownload,
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
                    onFridgeControl = ::handleFridgeControl,
                    onApplyClimateSettings = ::applyClimateSettings,
                    onDismissControlFeedback = { controlFeedback = null },
                    onQuickAc = ::quickAc,
                    onOpenBluetoothKey = ::openBluetoothKey,
                    onRetryDownload3D = ::retryDownload3DModel,
                    bluetoothSettingsRequestId = bluetoothSettingsRequestId,
                    onPowerTypeChange = ::savePowerType
                )
                if (showBluetoothKey && !pinSetupInProgress) {
                    BluetoothKeyDialog(
                        state = bluetoothState,
                        permissionsGranted = bluetoothPermissionsGranted,
                        certificateReady = BleAccessPolicy.isCertificateReady(bluetoothCertificate, session.selectedVin),
                        certificateLoading = bluetoothCertificateLoading,
                        certificateMessage = bluetoothCertificateMessage,
                        reconnectDevice = bluetoothReconnectDevice,
                        configuration = bluetoothManagedKey?.desired ?: BlePassiveConfiguration(),
                        appliedConfiguration = bluetoothManagedKey?.applied,
                        configurationRequested = bluetoothManagedKey?.requested == true,
                        configurationPending = bluetoothManagedKey?.pending == true,
                        backgroundRunning = bluetoothBackgroundRunning,
                        bound = bluetoothManagedKey != null,
                        protocolMinor = bluetoothManagedKey?.device?.protocolMinor,
                        onApplyConfiguration = ::requestBluetoothConfiguration,
                        onResumeBackground = ::resumeBluetoothBackground,
                        onSyncCertificate = ::syncBluetoothCertificate,
                        onScan = ::startBluetoothScan,
                        onConnect = ::connectBluetoothDevice,
                        onDisconnect = { bluetoothRuntime.stopBackground() },
                        onControl = ::requestBluetoothLockControl,
                        onOpenSettings = ::openBluetoothSettings,
                        onCopyDiagnostics = ::copyBluetoothDiagnostics,
                        onShareDiagnostics = ::shareBluetoothDiagnostics,
                        onClearDiagnostics = { bluetoothKeyController.clearDiagnostics() },
                        onDismiss = ::hideBluetoothKey,
                        metadata = BleVehicleProfile.freshMetadata(bluetoothCloudState.profile.metadata, System.currentTimeMillis()),
                        metadataLoading = bluetoothCloudState.metadataLoading,
                        metadataMessage = bluetoothCloudState.metadataMessage,
                        cloudState = bluetoothCloudState.profile.cloudState,
                        onRetryCloudSync = ::retryBluetoothCloudSync,
                        calibration = bluetoothCloudState.profile.effectiveCalibration,
                        calibrationApplied = bluetoothManagedKey?.requested == true && bluetoothManagedKey?.pending == false &&
                            bluetoothManagedKey?.applied?.calibration == bluetoothCloudState.profile.effectiveCalibration,
                        calibrationPending = bluetoothManagedKey?.pending == true &&
                            bluetoothManagedKey?.desired?.calibration == bluetoothCloudState.profile.effectiveCalibration,
                        onSaveCalibration = ::requestBluetoothCalibration,
                        carType = session.selectedCarType
                    )
                }
                bluetoothControlConfirmation?.takeIf { showBluetoothKey && !pinSetupInProgress }?.let { confirmation ->
                    BluetoothActionConfirmation(
                        action = confirmation.action,
                        enabled = isCurrentBluetoothConfirmation(confirmation),
                        onDismiss = { bluetoothControlConfirmation = null },
                        onConfirm = { confirmBluetoothLockControl(confirmation) }
                    )
                }
            }
        }

        checkForUpdate()

        if (session.oldAuth != null) {
            loggedIn = true
            beginPostLoginPrompts()
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

    private fun updateBluetoothKeyFeatureEnabled(enabled: Boolean) {
        bluetoothKeyFeatureEnabled = enabled
        sessionStore.saveBluetoothKeyFeatureEnabled(enabled)
        if (enabled) {
            runCatching { bluetoothRuntime.restoreBackground() }
            val accountId = session.oldAuth?.accountId.orEmpty()
            val vin = session.selectedVin
            if (accountId.isNotBlank() && vin.isNotBlank()) {
                val cert = sessionStore.loadBluetoothKeyCertificate(accountId, vin)
                if (cert != null) {
                    bluetoothCertificate = cert
                } else {
                    syncBluetoothCertificate(silent = true)
                }
            }
        } else {
            bluetoothRuntime.stopBackground()
            bluetoothKeyController.disconnect()
        }
    }

    private fun openBluetoothKey() {
        if (!isBluetoothForegroundContext(requireManagement = false)) return
        bluetoothRuntime.attachSession(session)
        bluetoothPermissionsGranted = bluetoothKeyController.hasPermissions()
        val identity = currentBluetoothIdentity() ?: return
        if (bluetoothSessionIdentity != null && bluetoothSessionIdentity != identity) {
            clearBluetoothState(closePage = true)
        }
        bluetoothSessionIdentity = identity
        bluetoothCloud.attach(identity)
        if (bluetoothCertificate == null && !bluetoothCertificateLoading) {
            bluetoothCertificate = sessionStore.loadBluetoothKeyCertificate(identity.accountId, identity.vin)
            updateBluetoothCertificateMessage()
        }
        showBluetoothKey = true
        if (BleVehicleProfile.freshMetadata(bluetoothCloud.state.value.profile.metadata, System.currentTimeMillis()) == null) {
            bluetoothCloud.refreshMetadata()
        }
        // 自动静默同步：若检测到本地尚未就绪有效凭证，打开即自动发起静默同步，无需用户手动点击
        if (!BleAccessPolicy.isCertificateReady(bluetoothCertificate, identity.vin) && !bluetoothCertificateLoading) {
            syncBluetoothCertificate(silent = true)
        }
    }

    private fun hideBluetoothKey() {
        showBluetoothKey = false
        bluetoothControlConfirmation = null
        bluetoothConfigurationConfirmation = null
        bluetoothCalibrationConfirmation = null
        bluetoothConfigurationIdentity = null
        bluetoothPermissionGeneration = null
        bluetoothScanAfterPermissionGeneration = null
        if (bluetoothState.phase == BleConnectionPhase.SCANNING) {
            bluetoothKeyController.disconnect()
        }
    }

    private fun updateBluetoothCertificateMessage() {
        bluetoothCertificateMessage = BleAccessPolicy.certificateMessage(bluetoothCertificate, session.selectedVin)
    }

    private fun syncBluetoothCertificate(silent: Boolean = false) {
        if (bluetoothBackgroundRunning) {
            if (!silent) toast("请先关闭后台钥匙并等待车辆确认")
            return
        }
        if (!isBluetoothForegroundContext(requireManagement = !silent) ||
            !BleAccessPolicy.canSyncCertificate(bluetoothState.phase, bluetoothCertificateLoading)) {
            return
        }
        bluetoothPermissionGeneration = null
        bluetoothScanAfterPermissionGeneration = null
        if (!silent) {
            bluetoothKeyController.disconnect()
        }
        bluetoothKeyController.recordDiagnostic(BleDiagnosticEvent.CERTIFICATE_SYNC_STARTED)
        val scope = bluetoothGeneration
        val requestSession = sessionStore.load()
        val sync = BleCertificateSync(requestSession)
        val identity = sync.identity
        bluetoothCloud.attach(identity)
        bluetoothCloud.refreshMetadata()
        bluetoothCertificateLoading = true
        bluetoothCertificateMessage = "正在同步钥匙"
        bluetoothCertificateJob = lifecycleScope.launch {
            try {
                val certificate = withContext(Dispatchers.IO) { LeapmotorApi(requestSession).fetchBluetoothKeyCertificate() }
                if (!isCurrentBluetoothContext(identity, scope)) return@launch
                if (!sync.canCommit(session, requestSession, certificate) ||
                    !sessionStore.completeBluetoothCertificateSync(sync, requestSession, certificate)) {
                    bluetoothKeyController.recordDiagnostic(BleDiagnosticEvent.CERTIFICATE_SYNC_FAILED)
                    bluetoothCertificateMessage = if (silent) "" else "会话已变化，请重新同步钥匙"
                    return@launch
                }
                if (session.deviceId != requestSession.deviceId) {
                    bluetoothKeyController.recordDiagnostic(BleDiagnosticEvent.CERTIFICATE_IDENTITY_UPDATED)
                }
                session.deviceId = requestSession.deviceId
                session.oldAuth = requestSession.oldAuth
                session.newAuth = requestSession.newAuth
                session.route = requestSession.route
                bluetoothRuntime.attachSession(session)
                bluetoothSessionIdentity = currentBluetoothIdentity()
                currentBluetoothIdentity()?.let {
                    bluetoothCloud.attach(it)
                    if (it != identity) bluetoothCloud.refreshMetadata()
                }
                bluetoothCertificate = certificate
                bluetoothKeyController.recordDiagnostic(BleDiagnosticEvent.CERTIFICATE_SYNCED, code = certificate.keyType)
                updateBluetoothCertificateMessage()
                if (silent) {
                    android.util.Log.d("MainActivity", "数字钥匙安全凭证静默自动同步成功")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isCurrentBluetoothContext(identity, scope)) {
                    bluetoothKeyController.recordDiagnostic(BleDiagnosticEvent.CERTIFICATE_SYNC_FAILED)
                    if (!silent) {
                        bluetoothCertificateMessage = "钥匙同步失败，请检查登录状态和网络后重试"
                    }
                }
            } finally {
                if (scope == bluetoothGeneration) bluetoothCertificateLoading = false
            }
        }
    }

    private fun checkAndAutoSyncBluetoothCertificate(vin: String? = null) {
        val targetVin = vin ?: session.selectedVin
        val accountId = session.oldAuth?.accountId
        if (targetVin.isBlank() || accountId.isNullOrBlank() || !loggedIn || bluetoothBackgroundRunning) return
        val existing = sessionStore.loadBluetoothKeyCertificate(accountId, targetVin)
        if (!BleAccessPolicy.isCertificateReady(existing, targetVin) && !bluetoothCertificateLoading) {
            syncBluetoothCertificate(silent = true)
        }
    }

    private var lastChargePlanFetchEpochMs = 0L
    private var lastChargePlanFetchVin = ""

    private fun syncChargePlanFromServer(vin: String? = null, force: Boolean = false) {
        val targetVin = vin ?: session.selectedVin
        if (targetVin.isBlank() || !loggedIn) return
        val now = System.currentTimeMillis()
        if (!force && targetVin == lastChargePlanFetchVin && now - lastChargePlanFetchEpochMs < 30_000L) {
            return
        }
        asyncWorker.execute {
            runCatching {
                val api = LeapmotorApi(session)
                val config = api.getVehicleCommonConfig()
                val plan = VehicleChargePlan.fromConfig(config)
                if (plan != null) {
                    lastChargePlanFetchEpochMs = System.currentTimeMillis()
                    lastChargePlanFetchVin = targetVin
                    val vehicleMask = plan.cycles?.let { ChargePlanCyclesHelper.toVehicleMask(it) } ?: "1,1,1,1,1,1,1"
                    sessionStore.saveScheduledChargeEnabled(targetVin, plan.isEnable)
                    plan.beginTime?.let { sessionStore.saveScheduledChargeStartTime(targetVin, it) }
                    plan.endTime?.let { sessionStore.saveScheduledChargeEndTime(targetVin, it) }
                    sessionStore.saveScheduledChargeCycles(targetVin, vehicleMask)
                    sessionStore.saveScheduledChargeCirculation(targetVin, plan.circulation)
                    sessionStore.saveScheduledChargeContinueUntilLimit(targetVin, plan.recharge)
                    plan.percent?.let { sessionStore.saveHealthyChargeLimit(targetVin, it) }

                    mainHandler.post {
                        if (session.selectedVin == targetVin) {
                            scheduledChargeEnabled = plan.isEnable
                            plan.beginTime?.let { scheduledChargeStartTime = it }
                            plan.endTime?.let { scheduledChargeEndTime = it }
                            scheduledChargeCycles = vehicleMask
                            scheduledChargeCirculation = plan.circulation
                            scheduledChargeContinueUntilLimit = plan.recharge
                            plan.percent?.let { healthyChargeLimitSoc = it }

                            // 同步驱动首页充电小胶囊与车况模型实时更新
                            status = status?.copy(
                                chargeScheduleEnabled = plan.isEnable,
                                chargeScheduleStart = plan.beginTime,
                                chargeScheduleEnd = plan.endTime,
                                chargeScheduleCycles = plan.cycles,
                                chargeScheduleCirculation = plan.circulation,
                                chargeScheduleRecharge = plan.recharge,
                                chargeScheduleSocLimit = plan.percent
                            )
                        }
                    }
                }

                // 顺便若蓝牙 MAC 尚未缓存，同步缓存
                val bluetooth = config?.optJSONObject("4")
                val mac = (bluetooth?.opt("mac") as? String)?.let { BleVehicleMetadata.normalizeAddress(it) }
                if (!mac.isNullOrBlank() && sessionStore.loadVehicleBluetoothMac(targetVin) == null) {
                    sessionStore.saveVehicleBluetoothMac(targetVin, mac)
                }
            }
        }
    }

    private fun isCurrentBluetoothContext(identity: BleSessionIdentity, generation: Long): Boolean =
        loggedIn && !activityDestroyed && !showSessionExpiredDialog && generation == bluetoothGeneration &&
            identity.matches(session.oldAuth?.accountId, session.selectedVin, session.generation, session.deviceId)

    private fun isBluetoothForegroundContext(requireManagement: Boolean = true): Boolean =
        (!requireManagement || showBluetoothKey) && loggedIn && activityResumed && !activityDestroyed && !showSessionExpiredDialog &&
            !session.oldAuth?.accountId.isNullOrBlank() && session.selectedVin.isNotBlank()

    private fun currentBluetoothIdentity(): BleSessionIdentity? {
        val accountId = session.oldAuth?.accountId?.takeIf { it.isNotBlank() } ?: return null
        if (session.selectedVin.isBlank()) return null
        return BleSessionIdentity(accountId, session.selectedVin, session.generation, session.deviceId)
    }

    private fun startBluetoothScan() {
        if (bluetoothBackgroundRunning || bluetoothManagedKey?.desired?.enabled == true) {
            toast("当前已绑定车辆，请恢复连接或先关闭后台钥匙")
            return
        }
        if (!isBluetoothForegroundContext() ||
            !BleAccessPolicy.canScan(bluetoothState.phase, bluetoothCertificateLoading)) return
        if (!bluetoothKeyController.hasPermissions()) {
            if (bluetoothPermissionGeneration != null) return
            bluetoothPermissionGeneration = bluetoothGeneration
            bluetoothKeyController.recordDiagnostic(BleDiagnosticEvent.PERMISSION_REQUESTED)
            requestPermissions(BlePermissionPolicy.requiredPermissions(Build.VERSION.SDK_INT).toTypedArray(), BLUETOOTH_PERMISSION_REQUEST)
            return
        }
        bluetoothPermissionGeneration = null
        bluetoothScanAfterPermissionGeneration = null
        bluetoothKeyController.scan()
    }

    private fun connectBluetoothDevice(device: BleNearbyDevice) {
        val certificate = sessionStore.loadBluetoothKeyCertificate(session.oldAuth?.accountId.orEmpty(), session.selectedVin)
        if (certificate == null || certificate != bluetoothCertificate) {
            bluetoothCertificate = certificate
            updateBluetoothCertificateMessage()
            toast("钥匙或设备信息已变化，请重新同步钥匙")
            return
        }
        val accountId = session.oldAuth?.accountId ?: return
        val ready = BleAccessPolicy.isCertificateReady(certificate, session.selectedVin)
        if (!isBluetoothForegroundContext() ||
            !BleAccessPolicy.canConnect(bluetoothState.phase, bluetoothCertificateLoading, ready)) return
        bluetoothPermissionGeneration = null
        bluetoothScanAfterPermissionGeneration = null
        bluetoothReconnectDevice = device
        runCatching { bluetoothRuntime.connectManually(device, certificate, session) }
            .onFailure { toast("无法连接，请先处理后台钥匙设置或重新扫描") }
    }

    private fun requestBluetoothConfiguration(configuration: BlePassiveConfiguration) {
        val identity = currentBluetoothIdentity() ?: run {
            toast("未找到车辆信息，请检查登录状态")
            return
        }
        val performSave = {
            val desired = configuration
            val calibrationChanged = configuration.calibration != bluetoothCloudState.profile.calibration
            if (calibrationChanged) {
                bluetoothCloud.saveCalibration(configuration.calibration) {}
            }
            val success = bluetoothCloud.saveConfiguration(desired) {
                bluetoothRuntime.applyConfiguration(desired)
            }
            if (success) {
                toast("设置已保存，正在同步车端与云端")
            } else {
                toast("设置保存未完成，请检查权限与网络")
            }
        }
        if (sessionStore.loadOpPassword().isNullOrBlank()) {
            bluetoothPinRequestPending = true
            requestOperationPassword(action = {
                bluetoothPinRequestPending = false
                performSave()
            }, onCancel = { bluetoothPinRequestPending = false })
        } else {
            performSave()
        }
    }

    private fun requestBluetoothCalibration(calibration: BleCalibration?) {
        val identity = currentBluetoothIdentity() ?: run {
            toast("未找到车辆信息，请检查登录状态")
            return
        }
        val performSave = {
            val bound = bluetoothManagedKey
            val configuration = bound?.desired?.copy(calibration = calibration ?: BleCalibration.DEFAULT)
            val saved = bluetoothCloud.saveCalibration(calibration) {
                if (configuration != null) bluetoothRuntime.applyConfiguration(configuration)
            }
            if (saved) {
                bluetoothKeyController.recordDiagnostic(BleDiagnosticEvent.CALIBRATION_SAVED, if (calibration == null) 0 else 1)
                toast(if (calibration == null) "已恢复默认标定" else "标定参数已应用并保存")
            } else {
                toast("标定保存失败，请检查网络后重试")
            }
        }
        if (sessionStore.loadOpPassword().isNullOrBlank()) {
            bluetoothPinRequestPending = true
            requestOperationPassword(action = {
                bluetoothPinRequestPending = false
                performSave()
            }, onCancel = { bluetoothPinRequestPending = false })
        } else {
            performSave()
        }
    }

    private fun retryBluetoothCloudSync() {
        if (isBluetoothForegroundContext() && !busy && !sessionStore.loadOpPassword().isNullOrBlank()) {
            bluetoothCloud.retryUploads()
        }
    }

    private fun resumeBluetoothBackground() {
        if (!isBluetoothForegroundContext()) return
        runCatching { bluetoothRuntime.startBackground() }
            .onFailure { toast("无法恢复后台连接，请检查蓝牙权限和操控密码") }
    }

    private fun requestBluetoothLockControl(action: BleLockAction) {
        if (!isBluetoothForegroundContext() || !bluetoothState.canControl) return
        if (busy) {
            toast("另一项车辆操作正在进行")
            return
        }
        val identity = currentBluetoothIdentity() ?: return
        if (bluetoothSessionIdentity != identity) return
        val confirmation = BleControlConfirmation(action, identity, bluetoothGeneration)
        if (sessionStore.loadOpPassword().isNullOrBlank()) {
            bluetoothPinRequestPending = true
            requestOperationPassword(action = {
                bluetoothPinRequestPending = false
                if (isCurrentBluetoothConfirmation(confirmation)) bluetoothControlConfirmation = confirmation
            }, onCancel = { bluetoothPinRequestPending = false })
            return
        }
        bluetoothControlConfirmation = confirmation
    }

    private fun isCurrentBluetoothConfirmation(confirmation: BleControlConfirmation): Boolean =
        confirmation.isCurrent(currentBluetoothIdentity(), bluetoothGeneration,
            isBluetoothForegroundContext(requireManagement = false), bluetoothState.canControl,
            managementVisible = showBluetoothKey) &&
            bluetoothSessionIdentity == confirmation.identity && !busy

    private fun confirmBluetoothLockControl(confirmation: BleControlConfirmation) {
        if (bluetoothControlConfirmation != confirmation) return
        bluetoothControlConfirmation = null
        if (!isCurrentBluetoothConfirmation(confirmation)) return
        if (sessionStore.loadOpPassword().isNullOrBlank()) return
        bluetoothKeyController.control(confirmation.action)
    }

    private fun executeDirectBluetoothLockControl(isLock: Boolean) {
        val savedPin = sessionStore.loadOpPassword()
        if (savedPin.isNullOrEmpty()) {
            requestOperationPassword(
                action = { executeDirectBluetoothLockControl(isLock) }
            )
            return
        }
        val action = if (isLock) BleLockAction.LOCK else BleLockAction.UNLOCK
        val actionName = if (isLock) "lock" else "unlock"
        val label = if (isLock) "上锁" else "解锁"

        controlFeedback = ControlFeedback(
            ControlFeedbackFormatter.inProgress(actionName, label),
            ControlFeedbackKind.IN_PROGRESS
        )
        // 物理蓝牙毫秒级直连下发
        bluetoothKeyController.control(action)

        // 800ms 模拟物理机械动作即刻响应状态翻转，并开启 15s 乐观防回弹保护
        postOptimisticResponse(800L) {
            lastLockActionEpochMs = System.currentTimeMillis()
            optimisticLockState = isLock
            status = status?.copy(locked = isLock)
            sessionStore.updateWidgetLockState(session.selectedVin, locked = isLock)
            com.leapauto.app.tiles.TilePromptHelper.requestTilesUpdate(this@MainActivity)
            ControlWidget.refreshData(this@MainActivity)
            CompactControlWidget.refreshData(this@MainActivity)
        }

        // 1.2s ➔ 1.5s ➔ 2.0s ➔ 2.5s 阶梯轮询，实车信号达成即刻早退闭环
        scheduleLockStatusRefreshes(isLock)
    }

    private fun openBluetoothSettings() {
        bluetoothPermissionsGranted = bluetoothKeyController.hasPermissions()
        val intent = if (bluetoothPermissionsGranted) Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure { toast("无法打开系统设置") }
    }

    private fun copyBluetoothDiagnostics() {
        if (!isBluetoothForegroundContext() || bluetoothState.diagnostics.isEmpty()) return
        val report = BleDiagnostics.formatReport(bluetoothState.diagnostics, BuildConfig.VERSION_NAME, bluetoothState.phase)
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("蓝牙连接诊断", report))
        toast("已复制蓝牙诊断")
    }

    private fun shareBluetoothDiagnostics() {
        if (!isBluetoothForegroundContext() || bluetoothState.diagnostics.isEmpty() || bluetoothState.isBusy) return
        val report = BleDiagnostics.formatReport(bluetoothState.diagnostics, BuildConfig.VERSION_NAME, bluetoothState.phase)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "蓝牙连接诊断")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching { startActivity(Intent.createChooser(intent, "分享蓝牙诊断")) }
            .onFailure { toast("无法打开系统分享") }
    }

    private fun clearBluetoothState(closePage: Boolean, endSession: Boolean = closePage) {
        bluetoothGeneration++
        bluetoothCalibrationConfirmation = null
        bluetoothControlConfirmation = null
        bluetoothConfigurationConfirmation = null
        bluetoothConfigurationIdentity = null
        if (bluetoothPinRequestPending) {
            clearPendingPinProtectedAction(cancel = true)
            pinSetupInProgress = false
        }
        bluetoothCertificateJob?.cancel()
        bluetoothCertificateJob = null
        bluetoothCertificateLoading = false
        if (!closePage) updateBluetoothCertificateMessage()
        bluetoothPermissionGeneration = null
        bluetoothScanAfterPermissionGeneration = null
        if (::bluetoothRuntime.isInitialized) {
            if (endSession) bluetoothRuntime.endSession() else bluetoothRuntime.pauseManualConnection()
        }
        if (closePage) {
            if (::bluetoothCloud.isInitialized) bluetoothCloud.detach()
            bluetoothSettingsRequestId = 0L
            if (::bluetoothKeyController.isInitialized) bluetoothKeyController.clearDiagnostics()
            bluetoothReconnectDevice = null
            bluetoothSessionIdentity = null
            showBluetoothKey = false
            bluetoothCertificate = null
            bluetoothCertificateMessage = ""
            bluetoothState = BleConnectionState()
        }
    }

    private fun handleSessionFailure(error: Throwable) {
        if (loggedIn && SessionExpiry.isRefreshTokenInvalid(error.message)) {
            enterSessionExpiredState()
        }
    }

    private fun enterSessionExpiredState() {
        clearBluetoothState(closePage = true)
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
        clearPendingPinProtectedAction(cancel = true)
        pinSetupInProgress = false
        showVehicleConfigConfirmationPrompt = false
        showAuthorSupportDialog = false
        showSessionExpiredDialog = true
    }

    override fun onDestroy() {
        clearBluetoothState(closePage = true, endSession = false)
        activityDestroyed = true
        cancelPendingControlStatusRefreshes()
        NetworkDebugController.disableAndClear()
        operationGeneration += 1L
        worker.shutdownNow()
        asyncWorker.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        bluetoothRuntime.attachSession(session)
        bluetoothRuntime.setForeground(true)
        if ((loggedIn || session.selectedVin.isNotBlank()) && bluetoothKeyFeatureEnabled) runCatching { bluetoothRuntime.restoreBackground() }
        if (intent.getBooleanExtra(BleKeyService.EXTRA_OPEN_KEY, false)) {
            intent.removeExtra(BleKeyService.EXTRA_OPEN_KEY)
            bluetoothSettingsRequestId++
        }
        bluetoothPermissionsGranted = bluetoothKeyController.hasPermissions()
        resumeBluetoothScanAfterPermission()
        if (loggedIn && carScreenVisible) {
            // Refresh immediately when returning to the foreground while the vehicle tab is visible.
            refreshStatus(silent = true)
            refreshEnergy(force = true)
        }
        checkForUpdate()
        updateAutoRefreshLoop()
        maybeShowAuthorSupportPrompt()
    }

    override fun onPause() {
        activityResumed = false
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
        updateAutoRefreshLoop()
        super.onPause()
    }

    override fun onStop() {
        bluetoothRuntime.setForeground(false)
        clearBluetoothState(closePage = false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != BLUETOOTH_PERMISSION_REQUEST) return
        bluetoothPermissionsGranted = bluetoothKeyController.hasPermissions()
        val requestedGeneration = bluetoothPermissionGeneration
        bluetoothPermissionGeneration = null
        if (requestedGeneration == bluetoothGeneration && showBluetoothKey && loggedIn) {
            if (bluetoothKeyController.hasPermissions()) {
                bluetoothKeyController.recordDiagnostic(BleDiagnosticEvent.PERMISSION_GRANTED)
                bluetoothScanAfterPermissionGeneration = requestedGeneration
                resumeBluetoothScanAfterPermission()
            } else {
                bluetoothKeyController.recordDiagnostic(BleDiagnosticEvent.PERMISSION_DENIED)
                bluetoothState = bluetoothState.copy(message = "未授予蓝牙扫描权限，可在系统设置中授权")
            }
        }
    }

    private fun resumeBluetoothScanAfterPermission() {
        if (!activityResumed) return
        val requestedGeneration = bluetoothScanAfterPermissionGeneration ?: return
        bluetoothScanAfterPermissionGeneration = null
        if (requestedGeneration == bluetoothGeneration) startBluetoothScan()
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

    private fun submitGeetestSolution(result: GeetestCaptchaResult) {
        activeGeetestChallenge = null
        login(captchaResult = result)
    }

    private fun login(captchaResult: GeetestCaptchaResult? = null) {
        if (phone.length != 11 || code.isEmpty()) {
            toast("请输入手机号和验证码")
            return
        }
        busy { generation ->
            val started = System.currentTimeMillis()
            try {
                val api = LeapmotorApi(session)
                ShumeiSecurityManager.initialize(applicationContext)
                api.loginWithSms(phone, code, captchaResult)
                val vehicles = api.listVehicles()
                if (vehicles.isEmpty()) throw ApiException("账号下没有找到车辆")
                sessionStore.saveVehicles(vehicles)
                val selected = vehicles.firstOrNull { it.vin == session.selectedVin } ?: vehicles.first()
                vehicleConfig = sessionStore.loadVehicleConfig(
                    vin = session.selectedVin,
                    defaultModel = selected.carType,
                    defaultNickname = selected.nickname,
                    defaultYear = selected.year,
                    defaultPowerType = selected.powerType
                )
                sessionStore.save(session)
                runOnMain(generation) {
                    availableVehicles = vehicles
                    hvacCapability = session.hvacCapability
                    toast("登录成功")
                    activeGeetestChallenge = null
                    loggedIn = true
                    beginPostLoginPrompts(isNewLogin = true)
                    checkForUpdate()
                    refreshStatus()
                    refreshEnergy(force = true)
                    syncVehicleImage(session.selectedVin)
                }
            } catch (e: GeetestChallengeRequiredException) {
                runOnMain(generation) {
                    activeGeetestChallenge = e.challenge
                    toast("触发安全风控，请完成拼图验证", generation)
                }
            } catch (e: Exception) {
                val apiError = e as? ApiException
                ErrorLogs.repository.record(ErrorLogEntry(System.currentTimeMillis(), ErrorLogCategory.LOGIN_FAILURE, apiError?.stage ?: "login", apiError?.httpStatus, apiError?.durationMs ?: (System.currentTimeMillis() - started), apiError?.retryCount ?: 0, BuildConfig.VERSION_NAME, e.message ?: e.toString()))
                throw e
            }
        }
    }

    private fun loginWithRawAuth(rawJson: String, phoneInput: String = "") {
        if (phoneInput.isNotBlank()) {
            phone = phoneInput.trim()
        }
        busy { generation ->
            val started = System.currentTimeMillis()
            try {
                val api = LeapmotorApi(session)
                api.loginWithRawAuth(rawJson, phoneInput)
                val vehicles = api.listVehicles()
                if (vehicles.isEmpty()) throw ApiException("账号下没有找到车辆")
                sessionStore.saveVehicles(vehicles)
                val selected = vehicles.firstOrNull { it.vin == session.selectedVin } ?: vehicles.first()
                vehicleConfig = sessionStore.loadVehicleConfig(
                    vin = session.selectedVin,
                    defaultModel = selected.carType,
                    defaultNickname = selected.nickname,
                    defaultYear = selected.year,
                    defaultPowerType = selected.powerType
                )
                sessionStore.save(session)
                runOnMain(generation) {
                    availableVehicles = vehicles
                    hvacCapability = session.hvacCapability
                    toast("凭据导入成功，已绑定远控权限")
                    loggedIn = true
                    beginPostLoginPrompts(isNewLogin = true)
                    checkForUpdate()
                    refreshStatus()
                    refreshEnergy(force = true)
                    syncVehicleImage(session.selectedVin)
                }
            } catch (e: Exception) {
                val apiError = e as? ApiException
                ErrorLogs.repository.record(ErrorLogEntry(System.currentTimeMillis(), ErrorLogCategory.LOGIN_FAILURE, apiError?.stage ?: "token_import", apiError?.httpStatus, apiError?.durationMs ?: (System.currentTimeMillis() - started), apiError?.retryCount ?: 0, BuildConfig.VERSION_NAME, e.message ?: e.toString()))
                throw e
            }
        }
    }

    private fun logout() {
        clearBluetoothState(closePage = true)
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
        cancelPendingControlStatusRefreshes()
        optimisticLockState = null
        lastLockActionEpochMs = 0L
        optimisticTrunkState = null
        lastTrunkActionEpochMs = 0L
        optimisticWindowPercent = null
        lastWindowActionEpochMs = 0L
        clearPendingPinProtectedAction(cancel = true)
        ErrorLogs.repository.clear()
        energyCacheStore.clearAll()
        operationGeneration += 1L
        sessionStore.clear()
        sessionStore.saveOpPassword("")
        session = sessionStore.load()
        hvacCapability = session.hvacCapability
        vehicleConfig = SessionStore.VehicleConfig()
        availableVehicles = emptyList()
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
        liveWeather = null
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
        toast("已登出")
    }

    private fun savePin() {
        if (!pin.matches(Regex("\\d{4}"))) {
            toast("请输入 4 位数字操控密码")
            return
        }
        sessionStore.saveOpPassword(pin)
        pinSaved = true
        val pendingAction = pendingPinProtectedAction
        pendingPinProtectedAction = null
        pendingPinProtectedCancelAction = null
        pinSetupInProgress = false
        pinSetupErrorMessage = ""
        pin = ""
        toast("操控密码已保存")
        pendingAction?.invoke()
        checkAndPromptPowerType(session.selectedVin)
        maybeShowAuthorSupportPrompt()
    }

    private fun cancelPinSetup() {
        pin = ""
        pinSetupInProgress = false
        pinSetupErrorMessage = ""
        clearPendingPinProtectedAction(cancel = true)
        checkAndPromptPowerType(session.selectedVin)
        maybeShowAuthorSupportPrompt()
    }

    private fun requestOperationPassword(
        action: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        pendingPinProtectedAction = action
        pendingPinProtectedCancelAction = onCancel
        pin = ""
        pinSetupErrorMessage = ""
        pinSetupInProgress = true
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
    }

    private fun promptUpdateOperationPassword(
        errorMessage: String = OperationPasswordErrorPolicy.ERROR_PROMPT_MESSAGE,
        retryAction: (() -> Unit)? = null
    ) {
        bluetoothRuntime.stopBackground()
        sessionStore.saveOpPassword("")
        pinSaved = false
        pin = ""
        pinSetupErrorMessage = errorMessage
        pendingPinProtectedAction = retryAction
        pinSetupInProgress = true
        controlFeedback = ControlFeedback(errorMessage, ControlFeedbackKind.ERROR)
        toast(errorMessage)
    }

    private fun clearPendingPinProtectedAction(cancel: Boolean) {
        val cancelAction = pendingPinProtectedCancelAction
        pendingPinProtectedAction = null
        pendingPinProtectedCancelAction = null
        if (cancel) cancelAction?.invoke()
    }

    private fun saveWidget4x2Actions(actions: List<String>) {
        sessionStore.saveWidget4x2Actions(actions)
        widget4x2Actions = actions
        ControlWidget.refreshAppearance(this)
    }

    private fun saveWidgetBackgroundStyle(style: Int) {
        sessionStore.saveWidgetBackgroundStyle(style)
        widgetBackgroundStyle = style
        ControlWidget.refreshData(this)
        CompactControlWidget.refreshData(this)
    }

    private fun saveWidgetOpacity(opacity: Int) {
        sessionStore.saveWidgetOpacity(opacity)
        widgetOpacity = opacity
        ControlWidget.refreshData(this)
        CompactControlWidget.refreshData(this)
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
                // 1. 发起并发前先保证 Token 处于新鲜状态，避免子线程并发争抢触发续期
                runCatching { api.ensureFreshOldToken() }

                // 2. 对齐官方 App 抓包 (lhlc [317], [318], [319])：
                // 三路并发异步请求，替代原有的多次串行阻塞等待，耗时降低至单次请求时长
                val executor = java.util.concurrent.Executors.newFixedThreadPool(3)
                val (recentMileageResult, rankResult, compositionResult) = try {
                    val futureRecentMileage = executor.submit(java.util.concurrent.Callable {
                        runCatching {
                            RecentMileageEnergyParser.parse(api.getRecentMileageEnergy(now))
                        }
                    })
                    val futureRank = executor.submit(java.util.concurrent.Callable {
                        runCatching {
                            EnergyRankAnalyticsParser.parse(api.getLastNWeeks100kmEcAndRank())
                        }
                    })
                    val futureComposition = executor.submit(java.util.concurrent.Callable {
                        runCatching {
                            LastWeekEnergyCompositionParser.parse(api.getLastWeekEc())
                        }
                    })
                    Triple(
                        futureRecentMileage.get(15, java.util.concurrent.TimeUnit.SECONDS),
                        futureRank.get(15, java.util.concurrent.TimeUnit.SECONDS),
                        futureComposition.get(15, java.util.concurrent.TimeUnit.SECONDS)
                    )
                } catch (timeout: java.util.concurrent.TimeoutException) {
                    Triple(
                        Result.failure(ApiException("近7日里程查询超时")),
                        Result.failure(ApiException("周能耗排行查询超时")),
                        Result.failure(ApiException("上周能耗构成查询超时"))
                    )
                } finally {
                    executor.shutdownNow()
                }

                // 若核心凭证失效，抛出以触发统一重新登录流程
                listOf(recentMileageResult, rankResult, compositionResult)
                    .mapNotNull { it.exceptionOrNull() }
                    .firstOrNull { SessionExpiry.isRefreshTokenInvalid(it.message) }
                    ?.let { throw it }

                val recentMileage = recentMileageResult.getOrNull()
                    ?: runCatching {
                        // 兜底回退：若直接查询失败，兼容尝试全历史范围接口
                        RecentMileageEnergyParser.parse(api.getMileageEnergy(purchaseAtMs, now))
                    }.getOrNull()

                val rankData = rankResult.getOrNull()
                val compositionList = compositionResult.getOrDefault(emptyList())

                val totalMileageVal = recentMileage?.vehicleTotalMileage
                    ?: currentVehicleTotalMileage
                        ?.filter { ch -> ch.isDigit() || ch == '.' }
                        ?.takeIf { text -> text.isNotBlank() }

                val merged = EnergyAnalyticsData(
                    overallConsumption = rankData?.overallConsumption,
                    trend = rankData?.weeklyTrend.orEmpty(),
                    rankLabel = rankData?.rankLabel,
                    rankError = rankResult.exceptionOrNull()?.let { "周能耗趋势暂不可用" },
                    lastWeekComposition = compositionList,
                    ownershipDays = recentMileage?.deliveryDays?.let {
                        EnergyMetric(label = "提车天数", value = it.toString())
                    },
                    cumulativeEnergy = recentMileage?.totalEnergyKwh?.let {
                        EnergyMetric(label = "累计能耗", value = it.toString(), unit = "kWh")
                    },
                    totalMileage = totalMileageVal?.let {
                        EnergyMetric(label = "总里程", value = it, unit = "km")
                    },
                    recentMileage = recentMileage?.let {
                        EnergyMetric(label = "近7天行驶里程", value = it.totalMileageKm.toString(), unit = "km")
                    },
                    mileageTrend = recentMileage?.mileage
                        ?.map { EnergySeriesPoint(label = it.day, value = it.mileageKm) }
                        .orEmpty(),
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
                    if (energyState !is EnergyAnalyticsState.Success) {
                        energyState = EnergyAnalyticsState.Failed(
                            if (SessionExpiry.isRefreshTokenInvalid(e.message)) {
                                "能耗服务暂不可用（需重新同步会话）"
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

    private fun applyHealthyAndScheduledCharging(
        healthyEnabled: Boolean,
        targetSoc: Int,
        scheduledEnabled: Boolean,
        startTime: String,
        endTime: String,
        continueUntilLimit: Boolean,
        circulation: Int = 1,
        cycles: String = "1,2,3,4,5,6,7"
    ) {
        val vin = session.selectedVin
        val vehicleCycles = ChargePlanCyclesHelper.toVehicleMask(cycles)
        sessionStore.saveHealthyChargeLimit(vin, targetSoc)
        sessionStore.saveScheduledChargeEnabled(vin, scheduledEnabled)
        sessionStore.saveScheduledChargeStartTime(vin, startTime)
        sessionStore.saveScheduledChargeEndTime(vin, endTime)
        sessionStore.saveScheduledChargeContinueUntilLimit(vin, continueUntilLimit)
        sessionStore.saveScheduledChargeCirculation(vin, circulation)
        sessionStore.saveScheduledChargeCycles(vin, vehicleCycles)
        healthyChargeLimitSoc = targetSoc
        scheduledChargeEnabled = scheduledEnabled
        scheduledChargeStartTime = startTime
        scheduledChargeEndTime = endTime
        scheduledChargeContinueUntilLimit = continueUntilLimit
        scheduledChargeCirculation = circulation
        scheduledChargeCycles = vehicleCycles

        val started = System.currentTimeMillis()
        worker.execute {
            try {
                val api = LeapmotorApi(session)
                val respHealthy = api.setHealthyCharging(healthyEnabled, targetSoc)
                val codeHealthy = respHealthy.optInt("code", respHealthy.optInt("result", -1))
                val msgHealthy = respHealthy.optString("msg", respHealthy.optString("message", ""))

                val savedPin = sessionStore.loadOpPassword().orEmpty()
                val respSched = api.setScheduledCharging(
                    enabled = scheduledEnabled,
                    startTime = startTime,
                    endTime = endTime,
                    targetSoc = targetSoc,
                    opPassword = savedPin,
                    continueUntilLimit = continueUntilLimit,
                    circulation = circulation,
                    cycles = vehicleCycles
                )
                val codeSched = respSched.optInt("code", respSched.optInt("result", -1))
                val msgSched = respSched.optString("msg", respSched.optString("message", ""))
                val isHealthySuccess = codeHealthy == 0 || codeHealthy == 200
                val isSchedSuccess = codeSched == 0 || codeSched == 200

                // 仅在真实失败时记录错误日志
                if (!isHealthySuccess || !isSchedSuccess) {
                    val elapsed = System.currentTimeMillis() - started
                    ErrorLogs.repository.record(
                        ErrorLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            category = ErrorLogCategory.CONTROL_FAILURE,
                            stage = "charging_settings_control",
                            httpStatus = if (codeHealthy != -1) codeHealthy else null,
                            durationMs = elapsed,
                            retryCount = 0,
                            appVersion = BuildConfig.VERSION_NAME,
                            message = buildString {
                                appendLine("下发充电设置部分未完成:")
                                appendLine("VIN: $vin")
                                appendLine("健康充电: enabled=$healthyEnabled, targetSoc=$targetSoc (code=$codeHealthy, msg=$msgHealthy)")
                                appendLine("预约充电: enabled=$scheduledEnabled, $startTime ~ $endTime, circulation=$circulation, cycles=$cycles, 未达上限继续充电=$continueUntilLimit (code=$codeSched, msg=$msgSched)")
                                appendLine("健康充电响应: $respHealthy")
                                appendLine("充电计划(190)响应: $respSched")
                            }
                        )
                    )
                }

                val msgId = respSched.optString("data").takeIf { it.isNotBlank() && it != "null" }
                mainHandler.post {
                    if (isHealthySuccess && isSchedSuccess) {
                        Toast.makeText(this@MainActivity, "充电设置已同步至车辆", Toast.LENGTH_SHORT).show()
                    } else if (isHealthySuccess) {
                        Toast.makeText(this@MainActivity, "健康充电已生效", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "充电设置已保存", Toast.LENGTH_SHORT).show()
                    }
                    refreshStatus()
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - started
                val apiError = e as? ApiException
                ErrorLogs.repository.record(
                    ErrorLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        category = ErrorLogCategory.API_FAILURE,
                        stage = "charging_settings_control",
                        httpStatus = apiError?.httpStatus,
                        durationMs = elapsed,
                        retryCount = 0,
                        appVersion = BuildConfig.VERSION_NAME,
                        message = buildString {
                            appendLine("下发充电设置异常")
                            appendLine("VIN: $vin")
                            appendLine("异常信息: ${e.message ?: e.toString()}")
                            appendLine("异常堆栈:\n${e.stackTraceToString().take(1200)}")
                        }
                    )
                )
                mainHandler.post {
                    Toast.makeText(this@MainActivity, "已本地保存，待连车同步", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchParkingPhoto(callback: (ChassisParkingPhoto?, android.graphics.Bitmap?) -> Unit) {
        val generation = operationGeneration
        worker.execute {
            try {
                val api = LeapmotorApi(session)
                val photoInfo = api.getChassisParkingPhoto()
                val bitmap = photoInfo?.secureUrl?.let { api.downloadParkingPhotoBitmap(it) }
                runOnMain(generation) {
                    callback(photoInfo, bitmap)
                }
            } catch (_: Exception) {
                runOnMain(generation) {
                    callback(null, null)
                }
            }
        }
    }

    private fun applyScheduledBatteryPreheat(
        enabled: Boolean,
        startTime: String,
        days: String = "1,1,1,1,1,1,1"
    ) {
        val vin = session.selectedVin
        val vehicleMask = ChargePlanCyclesHelper.toVehicleMask(days)
        sessionStore.saveScheduledPreheatEnabled(vin, enabled)
        sessionStore.saveScheduledPreheatStartTime(vin, startTime)
        sessionStore.saveScheduledPreheatDays(vin, vehicleMask)
        scheduledPreheatEnabled = enabled
        scheduledPreheatStartTime = startTime
        scheduledPreheatDays = vehicleMask

        val started = System.currentTimeMillis()
        worker.execute {
            try {
                // 间隔 1.5 秒避开可能的并发控车锁，杜绝网关“系统繁忙”冲突
                Thread.sleep(1500)
                val api = LeapmotorApi(session)
                val savedPin = sessionStore.loadOpPassword().orEmpty()
                val resp = api.setScheduledBatteryPreheat(enabled, startTime, savedPin, vehicleMask)
                val code = resp.optInt("code", resp.optInt("result", -1))
                val msg = resp.optString("msg", resp.optString("message", ""))
                val isPreheatSuccess = code == 200 || code == 0
                if (!isPreheatSuccess) {
                    val elapsed = System.currentTimeMillis() - started
                    ErrorLogs.repository.record(
                        ErrorLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            category = ErrorLogCategory.CONTROL_FAILURE,
                            stage = "battery_preheat_schedule",
                            httpStatus = if (code != -1) code else null,
                            durationMs = elapsed,
                            retryCount = 0,
                            appVersion = BuildConfig.VERSION_NAME,
                            message = buildString {
                                appendLine("下发预约电池预热未完成:")
                                appendLine("VIN: $vin")
                                appendLine("参数: enabled=$enabled, startTime=$startTime, days=$vehicleMask")
                                appendLine("状态码: code=$code, msg=$msg")
                                appendLine("服务端完整返回: $resp")
                            }
                        )
                    )
                }

                mainHandler.post {
                    if (isPreheatSuccess) {
                        Toast.makeText(this@MainActivity, "已设置 $startTime 预约预热", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "预热设置已保存至本地", Toast.LENGTH_SHORT).show()
                    }
                    refreshStatus()
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - started
                val apiError = e as? ApiException
                ErrorLogs.repository.record(
                    ErrorLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        category = ErrorLogCategory.API_FAILURE,
                        stage = "battery_preheat_schedule",
                        httpStatus = apiError?.httpStatus,
                        durationMs = elapsed,
                        retryCount = 0,
                        appVersion = BuildConfig.VERSION_NAME,
                        message = buildString {
                            appendLine("预约电池预热异常")
                            appendLine("VIN: $vin")
                            appendLine("异常信息: ${e.message ?: e.toString()}")
                            appendLine("异常堆栈:\n${e.stackTraceToString().take(1200)}")
                        }
                    )
                )
                mainHandler.post {
                    Toast.makeText(this@MainActivity, "已本地保存，待连车同步", Toast.LENGTH_SHORT).show()
                }
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

    private fun savePowerType(powerType: SessionStore.VehiclePowerType) {
        sessionStore.saveVehiclePowerType(session.selectedVin, powerType)
        vehicleConfig = vehicleConfig.copy(powerType = powerType)
        availableVehicles = availableVehicles.map { v ->
            if (v.vin == session.selectedVin) v.copy(powerType = powerType) else v
        }
        refreshStatus()
        ControlWidget.refreshData(this)
        CompactControlWidget.refreshData(this)
        toast("已切换为${if (powerType == SessionStore.VehiclePowerType.RANGE_EXTENDER) "增程" else "纯电"}模式")
    }

    private fun checkAndPromptPowerType(vin: String) {
        if (vin.isNotBlank() && loggedIn && !pinSetupInProgress && !sessionStore.isVehiclePowerTypeConfirmed(vin)) {
            showPowerTypeDialog = true
        }
    }

    private fun updateVehicleNickname(newNickname: String) {
        val trimmed = newNickname.trim().take(10)
        vehicleConfig = vehicleConfig.copy(nickname = trimmed)
        sessionStore.saveVehicleConfig(session.selectedVin, vehicleConfig)
        session.selectedNickname = trimmed
        sessionStore.save(session)
        availableVehicles = availableVehicles.map { v ->
            if (v.vin == session.selectedVin) v.copy(nickname = trimmed) else v
        }
        ControlWidget.refreshData(this)
        toast(if (trimmed.isEmpty()) "车辆昵称已重置" else "车辆昵称已更新为：$trimmed")
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

    private fun beginPostLoginPrompts(isNewLogin: Boolean = false) {
        mainHandler.removeCallbacks(authorSupportPromptRunnable)
        showAuthorSupportDialog = false
        showVehicleConfigConfirmationPrompt = false
        if (PostLoginPinSetupPolicy.shouldPromptPinSetup(isNewLogin = isNewLogin, pinSaved = pinSaved)) {
            pin = ""
            pinSetupErrorMessage = ""
            pinSetupInProgress = true
        } else {
            pinSetupInProgress = false
            checkAndPromptPowerType(session.selectedVin)
            maybeShowAuthorSupportPrompt()
        }
    }

    private fun openFeedback() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ExternalLinks.FEEDBACK_URL)))
        } catch (_: ActivityNotFoundException) {
            toast("未找到可用的浏览器")
        }
    }

    private var lastUpdateCheckEpochMs = 0L

    private fun checkForUpdate(force: Boolean = false) {
        val currentVersion = AppReleaseInfo.currentVersion
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheckEpochMs < 30_000L && versionUpdateState !is VersionUpdateState.Idle) {
            return
        }
        if (versionUpdateState is VersionUpdateState.Checking) return
        lastUpdateCheckEpochMs = now
        versionUpdateState = VersionUpdateState.Checking(currentVersion)
        val generation = operationGeneration
        asyncWorker.execute {
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

    private fun startInAppUpdateDownload(release: PgyerRelease) {
        if (downloadUpdateProgress != null && downloadUpdateProgress!! < 100) return
        downloadUpdateProgress = 0
        toast("正在解析新版本下载通道...")
        AppUpdateInstaller.downloadApk(
            context = this,
            release = release,
            onProgress = { progress ->
                runOnUiThread { downloadUpdateProgress = progress }
            },
            onSuccess = { apkFile ->
                runOnUiThread {
                    downloadUpdateProgress = null
                    markVersionUpdateHandled(release.versionName)
                    toast("下载完成，正在调起系统安装器")
                    AppUpdateInstaller.triggerInstall(this, apkFile)
                }
            },
            onError = { errMsg ->
                runOnUiThread {
                    downloadUpdateProgress = null
                    toast(errMsg)
                    openUpdatePage()
                }
            }
        )
    }

    private fun markVersionUpdateHandled(version: String) {
        sessionStore.saveHandledUpdateVersion(version)
        handledUpdateVersion = version
    }

    private fun openVersionUpdatePage(version: String) {
        val update = (versionUpdateState as? VersionUpdateState.UpdateAvailable)?.latestRelease
        if (update != null) {
            startInAppUpdateDownload(update)
        } else {
            markVersionUpdateHandled(version)
            openUpdatePage()
        }
    }

    // ------------------------------------------------------------- 状态查询

    private fun refreshStatus(silent: Boolean = false, completion: ((Boolean) -> Unit)? = null) {
        if (!silent) {
            optimisticTrunkState = null
            lastTrunkActionEpochMs = 0L
            optimisticWindowPercent = null
            lastWindowActionEpochMs = 0L
            optimisticLockState = null
            lastLockActionEpochMs = 0L
            cancelPendingLockStatusRefreshes()
        }
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
                val signalMap = api.getVehicleState()
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
                val snapshotPowerType = if (parsed.rangeExtender) SessionStore.VehiclePowerType.RANGE_EXTENDER else (VehiclePowerTypeResolver.fromStatus(
                    signalMap, vehicleConfig.powerType, session.selectedCarType
                ) ?: vehicleConfig.powerType)
                sessionStore.saveWidgetSnapshot(
                    vin = session.selectedVin,
                    carType = session.selectedCarType,
                    range = parsed.mileage ?: "--",
                    soc = VehicleStatusMapper.electricSocPercent(signalMap)
                        ?: VehicleStatusMapper.soc(signalMap),
                    fuelSoc = VehicleStatusMapper.fuelSocPercent(signalMap),
                    updated = ControlWidget.formatUpdatedTime(),
                    powerType = snapshotPowerType,
                    electricRange = VehicleStatusMapper.electricRemainingRange(signalMap),
                    fuelRange = parsed.fuelMileage?.removeSuffix(" km")?.trim() ?: VehicleStatusMapper.fuelRemainingRange(signalMap),
                    electricTotalRange = VehicleStatusMapper.electricTotalRange(signalMap),
                    fuelTotalRange = VehicleStatusMapper.fuelTotalRange(signalMap),
                    statusLabel = WidgetStatusMapper.label(signalMap, session.selectedCarType).orEmpty(),
                    locked = WidgetStatusMapper.locked(signalMap),
                    acEnabled = WidgetAcMapper.state(signalMap),
                    acTone = ClimateTemperatureToneResolver.tone(
                        acEnabled = WidgetAcMapper.state(signalMap),
                        coolingAndHeating = parsed.acCoolingAndHeating,
                        climateMode = parsed.climateMode,
                        targetTemperature = Commands.acTemperatureTarget(parsed.acSetting).value
                    ),
                    chargingPower = parsed.chargingPower,
                    chargeState = ChargeStatus.state(signalMap),
                    chargeRemainTime = ChargeStatus.remainingTime(signalMap.opt("chargeRemainTime")),
                    driving = parsed.isDriving,
                    trunkState = parsed.trunkState,
                    sentryEnabled = parsed.sentryMode,
                    windowOpen = parsed.windowStatusAvailable && parsed.openWindows.isNotEmpty()
                )
                ControlWidget.updateSyncCadence(this@MainActivity, parsed.isDriving)
                ControlWidget.refreshData(this@MainActivity)
                CompactControlWidget.refreshData(this@MainActivity)
                runOnMain(generation) {
                    // Replace the activity-only snapshot only when this refresh yields a valid position.
                    vehicleLocationSnapshot = locationSnapshot
                    vehicleLocationSnapshotState = locationSnapshot
                    if (locationSnapshot != null) {
                        maybeUpdateVehicleAddress(locationSnapshot)
                    }
                    val guard = climateOptimisticGuard
                    val pendingExpectation = pendingClimateConfirmation
                        ?.takeIf { it.revision == refreshClimateRevision }
                        ?.telemetryExpectation
                    val decision = ClimateTelemetryMergePolicy.decide(
                        guard = guard,
                        refreshRevision = refreshClimateRevision,
                        currentRevision = climateStatusRevision,
                        matchesOptimisticTarget = guard?.let {
                            climateTelemetryMatches(
                                optimisticUpdate = it.update,
                                telemetryExpectation = pendingExpectation,
                                refreshed = parsed
                            )
                        } ?: true
                    )
                    val baseRefreshed = when (decision) {
                        ClimateTelemetryMergeDecision.APPLY -> parsed
                        ClimateTelemetryMergeDecision.PRESERVE_CLIMATE ->
                            preserveOptimisticClimate(parsed, guard?.update)
                    }
                    val nowMs = System.currentTimeMillis()
                    val trunkProtected = nowMs - lastTrunkActionEpochMs < 15_000L
                    val winProtected = nowMs - lastWindowActionEpochMs < 15_000L
                    val lockProtected = nowMs - lastLockActionEpochMs < 15_000L
                    if (!trunkProtected) optimisticTrunkState = null
                    if (!winProtected) optimisticWindowPercent = null
                    if (!lockProtected) optimisticLockState = null
                    if (lockProtected && optimisticLockState != null && parsed.locked == optimisticLockState) {
                        optimisticLockState = null
                        lastLockActionEpochMs = 0L
                        cancelPendingControlStatusRefreshes()
                    }
                    if (trunkProtected && optimisticTrunkState != null && parsed.trunkState == optimisticTrunkState) {
                        optimisticTrunkState = null
                        lastTrunkActionEpochMs = 0L
                        cancelPendingControlStatusRefreshes()
                    }
                    if (winProtected && optimisticWindowPercent != null) {
                        val isAllClosed = parsed.openWindows.isEmpty() && (parsed.leftFrontWindowPercent ?: 0) == 0
                        if (optimisticWindowPercent == 0 && isAllClosed) {
                            optimisticWindowPercent = null
                            lastWindowActionEpochMs = 0L
                            cancelPendingControlStatusRefreshes()
                        } else if (optimisticWindowPercent != null && (optimisticWindowPercent ?: 0) > 0 && parsed.openWindows.isNotEmpty()) {
                            optimisticWindowPercent = null
                            lastWindowActionEpochMs = 0L
                            cancelPendingControlStatusRefreshes()
                        }
                    }
                    val effectiveLock = if (lockProtected && optimisticLockState != null) optimisticLockState else parsed.locked
                    val winPercent = if (winProtected) optimisticWindowPercent else null
                    val trunk = if (trunkProtected) optimisticTrunkState else null
                    val comfortProtected = nowMs - lastComfortActionEpochMs < 15_000L
                    val fridgeProtected = nowMs - lastFridgeActionEpochMs < 15_000L
                    status = baseRefreshed.copy(
                        locked = effectiveLock,
                        leftFrontWindowPercent = winPercent ?: baseRefreshed.leftFrontWindowPercent,
                        rightFrontWindowPercent = winPercent ?: baseRefreshed.rightFrontWindowPercent,
                        leftRearWindowPercent = winPercent ?: baseRefreshed.leftRearWindowPercent,
                        rightRearWindowPercent = winPercent ?: baseRefreshed.rightRearWindowPercent,
                        trunkState = trunk ?: baseRefreshed.trunkState,
                        openWindows = if (winPercent != null && winPercent > 0) listOf("左前", "右前", "左后", "右后")
                                      else if (winPercent == 0) emptyList()
                                      else baseRefreshed.openWindows,
                        driverSeatHeating = if (comfortProtected && optimisticDriverSeatHeating != null) optimisticDriverSeatHeating else baseRefreshed.driverSeatHeating,
                        driverSeatVentilation = if (comfortProtected && optimisticDriverSeatVentilation != null) optimisticDriverSeatVentilation else baseRefreshed.driverSeatVentilation,
                        passengerSeatHeating = if (comfortProtected && optimisticPassengerSeatHeating != null) optimisticPassengerSeatHeating else baseRefreshed.passengerSeatHeating,
                        passengerSeatVentilation = if (comfortProtected && optimisticPassengerSeatVentilation != null) optimisticPassengerSeatVentilation else baseRefreshed.passengerSeatVentilation,
                        leftRearSeatHeating = if (comfortProtected && optimisticLeftRearSeatHeating != null) optimisticLeftRearSeatHeating else baseRefreshed.leftRearSeatHeating,
                        leftRearSeatVentilation = if (comfortProtected && optimisticLeftRearSeatVentilation != null) optimisticLeftRearSeatVentilation else baseRefreshed.leftRearSeatVentilation,
                        rightRearSeatHeating = if (comfortProtected && optimisticRightRearSeatHeating != null) optimisticRightRearSeatHeating else baseRefreshed.rightRearSeatHeating,
                        rightRearSeatVentilation = if (comfortProtected && optimisticRightRearSeatVentilation != null) optimisticRightRearSeatVentilation else baseRefreshed.rightRearSeatVentilation,
                        steeringWheelHeating = if (comfortProtected && optimisticSteeringWheelHeating != null) optimisticSteeringWheelHeating else baseRefreshed.steeringWheelHeating,
                        steeringWheelHeatingLevel = if (comfortProtected && optimisticSteeringWheelHeating != null) (if (optimisticSteeringWheelHeating == true) 2 else 0) else baseRefreshed.steeringWheelHeatingLevel,
                        rearviewMirrorHeating = if (comfortProtected && optimisticRearviewMirrorHeating != null) optimisticRearviewMirrorHeating else baseRefreshed.rearviewMirrorHeating,
                        fridgeStatus = if (fridgeProtected && optimisticFridgeStatus != null) optimisticFridgeStatus else baseRefreshed.fridgeStatus
                    )
                    statusUpdatedAtEpochMs = receivedAtEpochMs
                    climateOptimisticGuard = ClimateTelemetryMergePolicy.consume(guard, decision)
                    if (decision == ClimateTelemetryMergeDecision.APPLY) {
                        confirmClimateTelemetryIfMatched(refreshClimateRevision, parsed)
                    }

                    val currentVin = session.selectedVin
                    asyncWorker.execute {
                        // 异步静默预缓存车载蓝牙硬件 MAC 地址
                        if (sessionStore.loadVehicleBluetoothMac(currentVin) == null) {
                            runCatching {
                                val meta = LeapmotorApi(session).getBluetoothVehicleMetadata()
                                meta?.address?.let { mac ->
                                    sessionStore.saveVehicleBluetoothMac(currentVin, mac)
                                }
                            }
                        }

                        // 异步静默预同步蓝牙数字钥匙凭证，保障开门控车零手动等待
                        checkAndAutoSyncBluetoothCertificate(currentVin)

                        // 异步从云端同步最新预约充电计划 (commonConfig 的 config.3)
                        syncChargePlanFromServer(currentVin)
                    }

                    // 车端若返回了真实充电计划 (config.3)，同步反显更新
                    parsed.chargeScheduleEnabled?.let { schedEnabled ->
                        scheduledChargeEnabled = schedEnabled
                        sessionStore.saveScheduledChargeEnabled(session.selectedVin, schedEnabled)
                    }
                    parsed.chargeScheduleStart?.let { start ->
                        scheduledChargeStartTime = start
                        sessionStore.saveScheduledChargeStartTime(session.selectedVin, start)
                    }
                    parsed.chargeScheduleEnd?.let { end ->
                        scheduledChargeEndTime = end
                        sessionStore.saveScheduledChargeEndTime(session.selectedVin, end)
                    }
                    parsed.chargeScheduleCycles?.let { cyc ->
                        val vehicleMask = ChargePlanCyclesHelper.toVehicleMask(cyc)
                        scheduledChargeCycles = vehicleMask
                        sessionStore.saveScheduledChargeCycles(session.selectedVin, vehicleMask)
                    }
                    parsed.chargeScheduleCirculation?.let { circ ->
                        scheduledChargeCirculation = circ
                        sessionStore.saveScheduledChargeCirculation(session.selectedVin, circ)
                    }
                    parsed.chargeScheduleRecharge?.let { rech ->
                        scheduledChargeContinueUntilLimit = rech
                        sessionStore.saveScheduledChargeContinueUntilLimit(session.selectedVin, rech)
                    }
                    parsed.chargeScheduleSocLimit?.let { socLimit ->
                        healthyChargeLimitSoc = socLimit
                        sessionStore.saveHealthyChargeLimit(session.selectedVin, socLimit)
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
                if (vehicles.isNotEmpty()) {
                    sessionStore.saveVehicles(vehicles)
                    runOnMain {
                        availableVehicles = vehicles.map { v ->
                            val localNick = sessionStore.loadVehicleConfig(v.vin).nickname.trim()
                            if (localNick.isNotBlank()) v.copy(nickname = localNick) else v
                        }
                    }
                }
                val selected = vehicles.firstOrNull { it.vin == session.selectedVin } ?: vehicles.firstOrNull()
                if (selected != null) {
                    sessionStore.save(session)
                    runOnMain {
                        val current = vehicleConfig
                        val updatedNickname = current.nickname.trim().ifBlank {
                            selected.nickname.ifBlank { current.model }
                        }
                        val updatedYear = selected.year.ifBlank { current.modelYear }
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
                    runOnUiThread {
                        vehicleImageVersion++
                    }
                    ControlWidget.refreshData(this@MainActivity)
                }
            } catch (_: Exception) {
                // Keep local fallback
            } finally {
                vehicleImageSyncInFlight.set(false)
            }
        }
    }

    private fun retryDownload3DModel() {
        val vin = session.selectedVin
        if (vin.isBlank()) return
        worker.execute {
            val meta = VehicleImageCache.getCachedMeta(this@MainActivity, vin)
            meta?.h5Key?.let { h5Key ->
                CarModel3DManager.cleanModelPackage(this@MainActivity, h5Key)
            }
            vehicleImageSyncInFlight.set(false)
            syncVehicleImage(vin)
        }
    }

    private fun handleCustomVehicleImage(uri: Uri) {
        val vin = session.selectedVin
        if (vin.isBlank()) return
        Log.i("LeapVehiclePic", "收到选图回调: $uri")
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // 不支持持久化权限的 URI provider 安全忽略
        }

        // 立即在主线程持有鲜活权限时，一次性把输入流读入内存字节数组，彻底杜绝跨线程权限失效
        val imageBytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e("LeapVehiclePic", "主线程读取选图输入流异常", e)
            null
        }

        if (imageBytes == null || imageBytes.isEmpty()) {
            toast("读取图片数据失败，请重试")
            return
        }

        worker.execute {
            try {
                val original = VehicleImageProcessor.decodeBitmapFromBytes(
                    bytes = imageBytes,
                    reqWidth = 1920,
                    reqHeight = 1920
                ) ?: VehicleImageProcessor.decodeBitmapFromUri(
                    contentResolver = contentResolver,
                    uri = uri,
                    reqWidth = 1920,
                    reqHeight = 1920
                )
                if (original == null) {
                    runOnUiThread {
                        toast("图片解析失败，请重试")
                    }
                    return@execute
                }
                val processed = VehicleImageProcessor.processUserVehicleImage(original)
                if (processed !== original) {
                    original.recycle()
                }
                VehicleImageCache.saveCustomImage(this@MainActivity, vin, processed)
                runOnUiThread {
                    vehicleImageVersion++
                    toast("爱车主图已更新")
                }
                ControlWidget.refreshData(this@MainActivity)
                CompactControlWidget.refreshData(this@MainActivity)
            } catch (e: Exception) {
                Log.e("LeapVehiclePic", "处理自定义车图失败", e)
                runOnUiThread {
                    toast("保存主图失败: ${e.message}")
                }
            }
        }
    }

    private fun resetCustomVehicleImage() {
        val vin = session.selectedVin
        if (vin.isBlank()) return
        VehicleImageCache.removeCustomImage(this, vin)
        vehicleImageVersion++
        toast("已恢复默认官方车模")
        ControlWidget.refreshData(this)
        CompactControlWidget.refreshData(this)
        worker.execute {
            val api = LeapmotorApi(session)
            val updated = VehicleImageCache.sync(this@MainActivity, api, vin)
            if (updated) {
                runOnUiThread {
                    vehicleImageVersion++
                    ControlWidget.refreshData(this@MainActivity)
                    CompactControlWidget.refreshData(this@MainActivity)
                }
            }
        }
    }

    private fun switchVehicle(targetVin: String) {
        if (targetVin.isBlank() || targetVin == session.selectedVin) return
        val target = availableVehicles.firstOrNull { it.vin == targetVin } ?: return
        clearBluetoothState(closePage = true)
        session.selectedVin = target.vin
        session.selectedCarType = target.carType
        session.selectedNickname = target.nickname
        session.selectedYear = target.year
        session.route = null
        session.hvacCapability = target.hvacCapability
        sessionStore.save(session)

        val defaultPower = VehiclePowerTypeResolver.fromCarType(target.carType) ?: target.powerType
        vehicleConfig = sessionStore.loadVehicleConfig(
            vin = target.vin,
            defaultModel = target.carType,
            defaultNickname = target.nickname,
            defaultYear = target.year,
            defaultPowerType = defaultPower
        )

        healthyChargeLimitSoc = sessionStore.loadHealthyChargeLimit(target.vin)
        scheduledChargeEnabled = sessionStore.loadScheduledChargeEnabled(target.vin)
        scheduledChargeStartTime = sessionStore.loadScheduledChargeStartTime(target.vin)
        scheduledChargeEndTime = sessionStore.loadScheduledChargeEndTime(target.vin)
        scheduledChargeContinueUntilLimit = sessionStore.loadScheduledChargeContinueUntilLimit(target.vin)
        scheduledChargeCirculation = sessionStore.loadScheduledChargeCirculation(target.vin)
        scheduledChargeCycles = sessionStore.loadScheduledChargeCycles(target.vin)
        scheduledPreheatEnabled = sessionStore.loadScheduledPreheatEnabled(target.vin)
        scheduledPreheatStartTime = sessionStore.loadScheduledPreheatStartTime(target.vin)
        scheduledPreheatDays = sessionStore.loadScheduledPreheatDays(target.vin)

        status = null
        statusError = ""
        controlFeedback = null
        vehicleLocationSnapshot = null
        vehicleLocationSnapshotState = null
        vehicleAddress = null
        liveWeather = null
        lastGeocodedLocation = null
        energyState = EnergyAnalyticsState.Idle
        energyLastSuccessAt = 0L
        vehicleImageVersion++

        ControlWidget.refreshData(this)
        if (::bluetoothRuntime.isInitialized) {
            bluetoothRuntime.attachSession(session)
            if (bluetoothManagedKey?.needsBackground == true) {
                runCatching { bluetoothRuntime.startBackground() }
            }
        }
        refreshStatus()
        refreshEnergy(force = true)
        syncVehicleImage(target.vin)
        checkAndAutoSyncBluetoothCertificate(target.vin)
        syncChargePlanFromServer(target.vin, force = true)
        checkAndPromptPowerType(target.vin)
        toast("已切换至 ${target.nickname.ifBlank { target.carType }}")
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

    private val controlStatusRefreshRunnables = mutableListOf<Runnable>()
    private var pendingOptimisticResponseRunnable: Runnable? = null

    private fun cancelPendingOptimisticResponse() {
        pendingOptimisticResponseRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingOptimisticResponseRunnable = null
    }

    private fun postOptimisticResponse(delayMs: Long, action: () -> Unit) {
        cancelPendingOptimisticResponse()
        val r = Runnable {
            pendingOptimisticResponseRunnable = null
            if (loggedIn && !activityDestroyed) {
                action()
            }
        }
        pendingOptimisticResponseRunnable = r
        mainHandler.postDelayed(r, delayMs)
    }

    private fun cancelPendingControlStatusRefreshes() {
        cancelPendingOptimisticResponse()
        controlStatusRefreshRunnables.forEach { mainHandler.removeCallbacks(it) }
        controlStatusRefreshRunnables.clear()
        lockStatusRefreshRunnables.forEach { mainHandler.removeCallbacks(it) }
        lockStatusRefreshRunnables.clear()
    }

    private fun cancelPendingLockStatusRefreshes() = cancelPendingControlStatusRefreshes()

    private fun isControlConfirmed(commandName: String): Boolean = when {
        commandName == "lock" -> optimisticLockState == null && status?.locked == true
        commandName == "unlock" -> optimisticLockState == null && status?.locked == false
        commandName == "trunkOpen" -> optimisticTrunkState == null && status?.trunkState == TrunkState.OPEN
        commandName == "trunkClose" -> optimisticTrunkState == null && status?.trunkState == TrunkState.CLOSED
        commandName == "windowClose" -> optimisticWindowPercent == null && (status?.openWindows.isNullOrEmpty())
        commandName == "windowVent" || commandName == "windowOpen" -> optimisticWindowPercent == null
        commandName.startsWith("driverSeatHeating_") -> optimisticDriverSeatHeating == null
        commandName.startsWith("driverSeatVentilation_") -> optimisticDriverSeatVentilation == null
        commandName.startsWith("passengerSeatHeating_") -> optimisticPassengerSeatHeating == null
        commandName.startsWith("passengerSeatVentilation_") -> optimisticPassengerSeatVentilation == null
        commandName.startsWith("steeringWheelHeating") -> optimisticSteeringWheelHeating == null
        commandName.startsWith("rearviewMirrorHeating") -> optimisticRearviewMirrorHeating == null
        commandName.startsWith("fridge") -> optimisticFridgeStatus == null
        else -> false
    }

    /** 统一整车控车极速阶梯早退轮询：实车信号达成即刻早退闭环 */
    private fun scheduleControlStatusRefreshes(commandName: String) {
        cancelPendingControlStatusRefreshes()
        val checkpoints = VehicleControlConfirmationPolicy.telemetryRefreshScheduleMs(commandName)
        checkpoints.forEach { delayMs ->
            val runnable = object : Runnable {
                override fun run() {
                    if (activityDestroyed || !loggedIn) return
                    // 智能早退：若实车信号已与目标一致，提前终止后续轮询
                    if (isControlConfirmed(commandName)) {
                        cancelPendingControlStatusRefreshes()
                        return
                    }
                    refreshStatus(silent = true) { _ ->
                        if (isControlConfirmed(commandName)) {
                            cancelPendingControlStatusRefreshes()
                        }
                    }
                }
            }
            controlStatusRefreshRunnables.add(runnable)
            mainHandler.postDelayed(runnable, delayMs)
        }
    }

    /** 车门锁专属极速阶梯早退轮询时序：1.2s ➔ 1.5s ➔ 2.0s ➔ 2.5s，实车信号达成即刻早退闭环 */
    private fun scheduleLockStatusRefreshes(targetLock: Boolean) {
        scheduleControlStatusRefreshes(if (targetLock) "lock" else "unlock")
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
        val effectiveCmdName = "climate"
        activeControlCommandName = effectiveCmdName
        climatePreControlStatus = status
        climateOptimisticGuard = optimisticUpdate?.let {
            applyClimateOptimisticUpdate(it)
            ClimateOptimisticGuard(climateControlRevision, it)
        }
        // 空调控制采用非阻塞后台调度 (showBusy = false)，不锁死整屏交互，保持极致跟手性
        busy(showBusy = false) { generation ->
            runOnMain(generation) {
                controlFeedback = ControlFeedback(
                    ControlFeedbackFormatter.inProgress("climate", command.label),
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
                    pollingId = null // 对齐官方App：空调指令云端接收后无需轮询 /query 接口，由遥测静默刷新接管
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
            } finally {
                runOnMain(generation) {
                    if (activeControlCommandName == effectiveCmdName) {
                        activeControlCommandName = null
                    }
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
            registerPendingClimateConfirmation(
                revision = revision,
                command = command,
                optimisticUpdate = optimisticUpdate,
                telemetryExpectation = telemetryExpectation,
                climateTemperatureRequestId = climateTemperatureRequestId
            )
            completeClimatePost(revision, optimisticUpdate)
            controlFeedback = ControlFeedback(
                ControlFeedbackFormatter.success("climate", command.label),
                ControlFeedbackKind.SUCCESS
            )
            climateTemperatureRequestId?.let {
                updateClimateTemperatureRequest(it, ClimateControlRequestPhase.ACCEPTED)
            }
            // 空调控制指令被服务器接收后，对齐官方App：立即安排遥测静默刷新确认最终状态
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
            if (!isCurrentClimateRevision(generation, revision)) return@runOnMain
            status = climatePreControlStatus
            climatePreControlStatus = null
            climateOptimisticGuard = null
            pendingClimateConfirmation = null
            climateTemperatureRequestId?.let {
                updateClimateTemperatureRequest(it, ClimateControlRequestPhase.FAILED)
            }
            if (OperationPasswordErrorPolicy.isPasswordError(error)) {
                promptUpdateOperationPassword(
                    errorMessage = OperationPasswordErrorPolicy.ERROR_PROMPT_MESSAGE,
                    retryAction = null
                )
                return@runOnMain
            }
            controlFeedback = ControlFeedback(
                climateControlFailureMessage(command.label, error),
                ControlFeedbackKind.ERROR
            )
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
            runOnMain(generation) {
                if (isClimateConfirmationPending(generation, revision)) {
                    handleSessionFailure(e)
                }
            }
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
            val latest = api.getVehicleState()
            sessionStore.save(session)
            parseStatus(latest)
        } catch (e: Exception) {
            runOnMain(generation) {
                if (isClimateConfirmationPending(generation, revision)) {
                    handleSessionFailure(e)
                }
            }
            return
        }
        val telemetryConfirmed = climateTelemetryMatches(optimisticUpdate, telemetryExpectation, refreshed)
        runOnMain(generation) {
            if (!isClimateConfirmationPending(generation, revision)) return@runOnMain
            val refreshedWithContext = refreshed.copy(locationSummary = status?.locationSummary)
            if (telemetryConfirmed) {
                status = refreshedWithContext
                statusUpdatedAtEpochMs = System.currentTimeMillis()
                climateOptimisticGuard = null
                climatePreControlStatus = null
                confirmClimateTelemetryIfMatched(revision, refreshed)
            } else {
                // A successful result query proves service execution, not that
                // the vehicle telemetry has caught up yet.
                status = preserveOptimisticClimate(refreshedWithContext, optimisticUpdate)
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
        val pending = pendingClimateConfirmation
        pending?.climateTemperatureRequestId?.let {
            updateClimateTemperatureRequest(it, ClimateControlRequestPhase.COMPLETED)
        }
        // 空调指令已成功送达车端，后续遥测由静默轮询更新，不以延迟未同步的“暂未确认”告警打扰用户
        pendingClimateConfirmation = null
        climateOptimisticGuard = null
        climatePreControlStatus = null
    }

    private fun isCurrentClimateRevision(generation: Long, revision: Long): Boolean =
        !activityDestroyed && generation == operationGeneration && revision == climateStatusRevision

    private fun isClimateConfirmationPending(generation: Long, revision: Long): Boolean =
        isCurrentClimateRevision(generation, revision) && pendingClimateConfirmation?.revision == revision

    private fun climateControlFailureMessage(label: String, error: Exception): String {
        val raw = error.message.orEmpty()
        return when {
            OperationPasswordErrorPolicy.isPasswordError(error) -> OperationPasswordErrorPolicy.ERROR_PROMPT_MESSAGE
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
                acAirVolume = update.windLevel?.toString() ?: current.acAirVolume,
                recirculationMode = update.circle?.telemetryValue ?: current.recirculationMode,
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
        telemetryExpectation: ClimateTelemetryExpectation?,
        climateTemperatureRequestId: Long? = null
    ) {
        pendingClimateConfirmation = PendingClimateConfirmation(
            revision = revision,
            label = command.label,
            optimisticUpdate = optimisticUpdate,
            telemetryExpectation = telemetryExpectation,
            climateTemperatureRequestId = climateTemperatureRequestId
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
        pending.climateTemperatureRequestId?.let {
            updateClimateTemperatureRequest(it, ClimateControlRequestPhase.COMPLETED)
        }
        // 车端遥测已确认匹配生效，静默闭环同步状态，不重复弹出多余提示打扰用户
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
            optimisticUpdate = ClimateOptimisticUpdates.detailed(command),
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
                refreshed.acAirVolume?.toBigDecimalOrNull()
                    ?.stripTrailingZeros()
                    ?.takeIf { it.scale() <= 0 }
                    ?.let { runCatching { it.intValueExact() }.getOrNull() },
                AirCircle.fromTelemetryValue(refreshed.recirculationMode),
                refreshed.windshieldDefrost
            )
        )
        return refreshed.copy(
            acSwitch = merged.acSwitch,
            acSetting = merged.acSetting,
            acAirVolume = merged.windLevel?.toString() ?: refreshed.acAirVolume,
            recirculationMode = merged.circle?.telemetryValue ?: refreshed.recirculationMode,
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
        val isConfirmed = sessionStore.isVehiclePowerTypeConfirmed(session.selectedVin)
        val configuredPower = vehicleConfig.powerType

        // 硬件与传感器特征探测
        val hasDirectFuelSignal = m.has("3256") || m.has("fuelRangeStandard") || m.has("3259") || m.has("fuelRangeDynamic")
        val fuelSocValue = VehicleStatusMapper.fuelSocPercent(m) ?: 0
        val isCarTypeReev = session.selectedCarType.contains("增程") || session.selectedCarType.contains("REEV", ignoreCase = true)
        val isCarTypeEv = session.selectedCarType.contains("纯电") || session.selectedCarType.contains("EV", ignoreCase = true)
        val detectedReev = hasDirectFuelSignal || fuelSocValue > 0 || isCarTypeReev

        val effectivePowerType = when {
            configuredPower != null -> configuredPower
            isConfirmed -> SessionStore.VehiclePowerType.PURE_ELECTRIC
            detectedReev -> SessionStore.VehiclePowerType.RANGE_EXTENDER
            isCarTypeEv -> SessionStore.VehiclePowerType.PURE_ELECTRIC
            else -> SessionStore.VehiclePowerType.PURE_ELECTRIC
        }

        val rangeExtender = effectivePowerType == SessionStore.VehiclePowerType.RANGE_EXTENDER
        val fuelMileage = if (rangeExtender) VehicleStatusMapper.fuelRange(m)?.let { "$it km" } else null
        val electricMileage = VehicleStatusMapper.electricRange(m)?.let { "$it km" }
        val combinedMileage = if (rangeExtender) VehicleStatusMapper.combinedRange(m)?.let { "$it km" } else null

        // 尚未确认且配置为空时，根据检测结果预设配置
        if (!isConfirmed && configuredPower == null && detectedReev) {
            vehicleConfig = vehicleConfig.copy(powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER)
        }
        val displayMileage = VehicleStatusMapper.remainingRange(
            m, session.selectedCarType, effectivePowerType.toStatusPowerType()
        )
        val preciseSocStr = VehicleStatusMapper.displayPreciseSoc(m.opt("preciseSoc"))
            ?: formatPercentage(m.opt("preciseSoc"))
        val standardSocStr = VehicleStatusMapper.displayPreciseSoc(m.opt("soc"))
            ?: formatPercentage(m.opt("soc"))
        val effectiveSoc = preciseSocStr ?: standardSocStr
        val fuelSocStr = VehicleStatusMapper.displayPreciseSoc(m.opt("fuelSoc"))
            ?: formatPercentage(m.opt("fuelSoc"))

        val openWinLabels = WidgetStatusMapper.openWindowLabels(m, session.selectedCarType)
        val hasWindowTelemetry = WidgetStatusMapper.hasWindowTelemetry(m, session.selectedCarType)
        val selectedVin = session.selectedVin
        val lastTargetPercent = if (selectedVin.isNotBlank()) sessionStore.loadLastTargetWindowPercent(selectedVin) else null
        if (hasWindowTelemetry && openWinLabels.isEmpty() && selectedVin.isNotBlank() && (lastTargetPercent ?: 0) > 0) {
            sessionStore.saveLastTargetWindowPercent(selectedVin, 0)
        }
        val parseWindowPercent = { key: String, legacyKey: String, statusKey: String, label: String ->
            val raw = (m.opt(key) ?: m.opt(legacyKey))?.toString()?.trim()?.removeSuffix("%")?.toIntOrNull()
            if (raw != null && raw > 0) {
                val resolved = when (raw) {
                    in 1..3 -> 15   // 0~10 刻度下的通风微开 (如指令 2 对应 15% 微开开度)
                    in 4..6 -> 50   // 0~10 刻度下的半开 (如指令 5 对应 50% 半开开度)
                    in 7..10 -> 100 // 0~10 刻度下的全开 (如指令 10 对应 100% 全开)
                    else -> raw.coerceIn(0, 100) // 0~100 刻度下的真实百分比直接采用
                }
                if (selectedVin.isNotBlank()) sessionStore.saveLastTargetWindowPercent(selectedVin, resolved)
                resolved
            } else {
                val statusVal = m.opt(statusKey)?.toString()?.trim()?.toIntOrNull()
                if (statusVal == 0) {
                    0
                } else if (openWinLabels.contains(label)) {
                    // 车窗处于打开状态但网关未提供具体连续开度百分比：
                    // 严格保持用户在 App 内明确下发过的目标开度（15%微开、50%半开、100%全开等），杜绝冷启动后误变为50%半开
                    lastTargetPercent?.takeIf { it > 0 } ?: 15
                } else {
                    0
                }
            }
        }

        return VehicleStatus(
            soc = effectiveSoc,
            preciseSoc = preciseSocStr ?: standardSocStr,
            fuelSoc = fuelSocStr,
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
            isShutDown = VehicleHomeStatus.isVehicleShutDown(
                bcmKeyPositionOn3 = m.opt("bcmKeyPositionOn3"),
                vehicleState = m.opt("vehicleState"),
                parkingBrakeState = m.opt("parkingBrakeState"),
                locked = m.optBool("driverDoorLockStatus")
            ),
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
            openWindows = openWinLabels,
            leftFrontWindowPercent = parseWindowPercent("leftFrontWindowPercent", "", "driverWindowStatus", "左前"),
            rightFrontWindowPercent = parseWindowPercent("rightFrontWindowPercent", "", "rightFrontWindowStatus", "右前"),
            leftRearWindowPercent = parseWindowPercent("leftRearWindowPercent", "", "leftRearWindowStatus", "左后"),
            rightRearWindowPercent = parseWindowPercent("rightRearWindowPercent", "", "rightRearWindowStatus", "右后"),
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
            leftRearSeatHeating = if (RearSeatComfortPolicy.supportsRearSeats(session.selectedCarType)) m.opt("leftRearSeatHeating")?.toString()?.toIntOrNull() else null,
            leftRearSeatVentilation = if (RearSeatComfortPolicy.supportsRearSeats(session.selectedCarType)) m.opt("leftRearSeatVentilation")?.toString()?.toIntOrNull() else null,
            rightRearSeatHeating = if (RearSeatComfortPolicy.supportsRearSeats(session.selectedCarType)) m.opt("rightRearSeatHeating")?.toString()?.toIntOrNull() else null,
            rightRearSeatVentilation = if (RearSeatComfortPolicy.supportsRearSeats(session.selectedCarType)) m.opt("rightRearSeatVentilation")?.toString()?.toIntOrNull() else null,
            steeringWheelHeating = m.optBool("steeringWheelHeating"),
            steeringWheelHeatingLevel = m.opt("steeringWheelHeating")?.toString()?.toIntOrNull()
                ?: if (m.optBool("steeringWheelHeating") == true) 2 else 0,
            rearviewMirrorHeating = m.opt("leftMirrorHeating")?.let { it.toString() != "0" }
                ?: m.opt("rightMirrorHeating")?.let { it.toString() != "0" }
                ?: m.opt("rearviewMirrorHeating")?.let { it.toString() != "0" }
                ?: m.optBool("rearWindowHeating"),
            chargeScheduleEnabled = m.opt("chargeScheduleEnabled")?.let { it.toString() == "1" }
                ?: scheduledChargeEnabled,
            chargeScheduleStart = m.optString("chargeScheduleStart").takeIf { it.isNotBlank() }
                ?: scheduledChargeStartTime.takeIf { it.isNotBlank() },
            chargeScheduleEnd = m.optString("chargeScheduleEnd").takeIf { it.isNotBlank() }
                ?: scheduledChargeEndTime.takeIf { it.isNotBlank() },
            chargeScheduleCycles = m.optString("chargeScheduleCycles").takeIf { it.isNotBlank() }
                ?: scheduledChargeCycles.takeIf { it.isNotBlank() },
            chargeScheduleCirculation = m.opt("chargeScheduleCirculation")?.toString()?.toIntOrNull()
                ?: scheduledChargeCirculation,
            chargeScheduleRecharge = m.opt("chargeScheduleRecharge")?.let { it.toString() == "1" }
                ?: scheduledChargeContinueUntilLimit,
            chargeScheduleSocLimit = m.opt("chargesocSetting")?.toString()?.toIntOrNull()
                ?: healthyChargeLimitSoc,
            chargeGunConnected = ChargeStatus.isGunConnected(m),
            roofOpeningPercent = m.opt("roofOpening")?.toString()?.toIntOrNull(),
            fridgeStatus = parseFridgeStatus(m),
            carType = session.selectedCarType
        )
    }

    private fun parseFridgeStatus(m: JSONObject): FridgeStatus? {
        val fridgeSwitch = m.opt("fridgeSwitch")?.toString()?.toIntOrNull() ?: return null
        val modeInt = m.opt("fridgeMode")?.toString()?.toIntOrNull() ?: 0
        val targetTemp = m.opt("fridgeTargetTemp")?.toString()?.toIntOrNull() ?: Commands.FRIDGE_DEFAULT_TEMP
        val styleInt = m.opt("fridgeStyle")?.toString()?.toIntOrNull() ?: 0
        val faultInt = m.opt("fridgeFault")?.toString()?.toIntOrNull() ?: 0
        val parkSwitch = m.opt("fridgeParkSwitch")?.toString()?.toIntOrNull() ?: 0
        val parkHours = m.opt("fridgeParkDurationHours")?.toString()?.toIntOrNull() ?: 1
        val parkCycles = m.opt("fridgeParkCycles")?.toString()?.toIntOrNull() ?: 0
        val parkEndTime = m.opt("fridgeParkEndTime")?.toString()?.toLongOrNull() ?: 0L
        return FridgeStatus(
            enabled = fridgeSwitch == 1,
            mode = FridgeMode.fromSignal(modeInt),
            targetTemp = targetTemp,
            style = FridgeStyle.fromSignal(styleInt),
            fault = faultInt,
            parkEnable = parkSwitch == 1,
            parkDurationHours = parkHours,
            parkCycles = parkCycles,
            parkEndTimeEpochSeconds = parkEndTime
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
        val num = value.toDoubleOrNull()
        return if (num != null) "${Math.round(num).toInt()}%" else value.takeIf { it.isNotBlank() }?.let { "$it%" }
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
        asyncWorker.execute {
            val address = VehicleLocationGeocoder.reverseGeocode(lat, lng, context = this@MainActivity)
            if (address != null) {
                runOnMain(generation) {
                    vehicleAddress = address
                }
                if (address.adcode.isNotBlank()) {
                    val currentVin = session.selectedVin
                    val weather = com.leapauto.app.weather.AmapWeatherService.fetchLiveWeather(
                        adcode = address.adcode,
                        vin = currentVin,
                        context = this@MainActivity
                    )
                    if (weather != null) {
                        runOnMain(generation) {
                            liveWeather = weather
                        }
                    }
                }
            }
        }
    }

    private fun control(name: String) {
        if (VehicleDrivingSafetyPolicy.isDrivingGear(status?.gearStatus)) {
            toast(VehicleDrivingSafetyPolicy.DRIVING_OPERATION_PROHIBITED_HINT)
            return
        }
        if (name == "lock" || name == "unlock") {
            // 抓包时序与极速直连优化：若当前蓝牙钥匙处于就绪态 (车旁近场)，直接走物理蓝牙毫秒级开锁/落锁
            if (bluetoothState.canControl && isBluetoothForegroundContext(requireManagement = false)) {
                executeDirectBluetoothLockControl(name == "lock")
                return
            }
            if (!BleAccessPolicy.canStartCloudLockControl(bluetoothState.phase, busy)) {
                toast("当前车锁操作尚未完成，请稍后再试")
                return
            }
        }
        if (name == "trunkOpen") {
            executeTrunkOpen()
            return
        }
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
                    optimisticUpdate = ClimateOptimisticUpdates.deodorize(),
                    telemetryExpectation = Commands.climateExpectation(name)
                )
                return
            }
        }
        control(Commands.build(name), commandName = name)
    }

    private fun executeTrunkOpen() {
        control(Commands.build("trunkOpen"), commandName = "trunkOpen")
    }

    private fun handleFridgeControl(command: FridgeControlCommand) {
        val controlCmd = Commands.buildFridgeControl(command)
        val commandName = if (command.enable) "fridgeOn" else "fridgeOff"
        // 15 秒车载冰箱乐观状态保护：0ms 同步本地状态，绝不被延迟回传的旧遥测弹回冲刷
        val optimistic = (status?.fridgeStatus ?: FridgeStatus(enabled = false)).copy(
            enabled = command.enable,
            mode = command.mode,
            targetTemp = command.temp,
            style = command.style,
            parkEnable = command.parkEnable,
            parkDurationHours = (command.durationSeconds / 3600).coerceAtLeast(1),
            parkCycles = if (command.cycles == "2") 1 else 0
        )
        lastFridgeActionEpochMs = System.currentTimeMillis()
        optimisticFridgeStatus = optimistic
        status = status?.copy(fridgeStatus = optimistic)
        control(controlCmd, commandName = commandName)
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
        val effectiveCmdName = commandName ?: command.label
        val currentVehicle = availableVehicles.firstOrNull { it.vin == session.selectedVin }
        val isSubAccount = currentVehicle?.isSharedAccount == true
        if (effectiveCmdName == "sentryOn" || effectiveCmdName == "sentry") {
            if (!SentryModeControlPolicy.canOperateSentry(isSubAccount, targetOn = true)) {
                toast(SentryModeControlPolicy.SUB_ACCOUNT_UNSUPPORTED_MESSAGE)
                return
            }
        }

        val sentryTarget = commandName?.let(SentryModeControlPolicy::targetEnabled)
        val started = System.currentTimeMillis()
        activeControlCommandName = effectiveCmdName

        val preControlStatus = status
        when (effectiveCmdName) {
            "lock" -> {
                postOptimisticResponse(800L) {
                    lastLockActionEpochMs = System.currentTimeMillis()
                    optimisticLockState = true
                    status = status?.copy(locked = true)
                    sessionStore.updateWidgetLockState(session.selectedVin, locked = true)
                    com.leapauto.app.tiles.TilePromptHelper.requestTilesUpdate(this@MainActivity)
                    ControlWidget.refreshData(this@MainActivity)
                    CompactControlWidget.refreshData(this@MainActivity)
                }
            }
            "unlock" -> {
                postOptimisticResponse(800L) {
                    lastLockActionEpochMs = System.currentTimeMillis()
                    optimisticLockState = false
                    status = status?.copy(locked = false)
                    sessionStore.updateWidgetLockState(session.selectedVin, locked = false)
                    com.leapauto.app.tiles.TilePromptHelper.requestTilesUpdate(this@MainActivity)
                    ControlWidget.refreshData(this@MainActivity)
                    CompactControlWidget.refreshData(this@MainActivity)
                }
            }
            "trunkOpen" -> {
                postOptimisticResponse(800L) {
                    lastTrunkActionEpochMs = System.currentTimeMillis()
                    optimisticTrunkState = TrunkState.OPEN
                    status = status?.copy(trunkState = TrunkState.OPEN)
                    ControlWidget.refreshData(this@MainActivity)
                    CompactControlWidget.refreshData(this@MainActivity)
                }
            }
            "trunkClose" -> {
                postOptimisticResponse(800L) {
                    lastTrunkActionEpochMs = System.currentTimeMillis()
                    optimisticTrunkState = TrunkState.CLOSED
                    status = status?.copy(trunkState = TrunkState.CLOSED)
                    ControlWidget.refreshData(this@MainActivity)
                    CompactControlWidget.refreshData(this@MainActivity)
                }
            }
            "windowVent" -> {
                postOptimisticResponse(500L) {
                    lastWindowActionEpochMs = System.currentTimeMillis()
                    optimisticWindowPercent = 15
                    if (session.selectedVin.isNotBlank()) sessionStore.saveLastTargetWindowPercent(session.selectedVin, 15)
                    status = status?.copy(
                        leftFrontWindowPercent = 15,
                        rightFrontWindowPercent = 15,
                        leftRearWindowPercent = 15,
                        rightRearWindowPercent = 15,
                        openWindows = listOf("左前", "右前", "左后", "右后")
                    )
                    ControlWidget.refreshData(this@MainActivity)
                    CompactControlWidget.refreshData(this@MainActivity)
                }
            }
            "windowOpen" -> {
                postOptimisticResponse(500L) {
                    lastWindowActionEpochMs = System.currentTimeMillis()
                    optimisticWindowPercent = 50
                    if (session.selectedVin.isNotBlank()) sessionStore.saveLastTargetWindowPercent(session.selectedVin, 50)
                    status = status?.copy(
                        leftFrontWindowPercent = 50,
                        rightFrontWindowPercent = 50,
                        leftRearWindowPercent = 50,
                        rightRearWindowPercent = 50,
                        openWindows = listOf("左前", "右前", "左后", "右后")
                    )
                    ControlWidget.refreshData(this@MainActivity)
                    CompactControlWidget.refreshData(this@MainActivity)
                }
            }
            "windowClose" -> {
                postOptimisticResponse(500L) {
                    lastWindowActionEpochMs = System.currentTimeMillis()
                    optimisticWindowPercent = 0
                    if (session.selectedVin.isNotBlank()) sessionStore.saveLastTargetWindowPercent(session.selectedVin, 0)
                    status = status?.copy(
                        leftFrontWindowPercent = 0,
                        rightFrontWindowPercent = 0,
                        leftRearWindowPercent = 0,
                        rightRearWindowPercent = 0,
                        openWindows = emptyList()
                    )
                    ControlWidget.refreshData(this@MainActivity)
                    CompactControlWidget.refreshData(this@MainActivity)
                }
            }
            else -> {
                if (effectiveCmdName.startsWith("driverSeatHeating_")) {
                    val lvl = effectiveCmdName.removePrefix("driverSeatHeating_").toIntOrNull() ?: 0
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticDriverSeatHeating = lvl
                    if (lvl > 0) optimisticDriverSeatVentilation = 0
                    status = status?.copy(
                        driverSeatHeating = lvl,
                        driverSeatVentilation = if (lvl > 0) 0 else status?.driverSeatVentilation
                    )
                } else if (effectiveCmdName.startsWith("driverSeatVentilation_")) {
                    val lvl = effectiveCmdName.removePrefix("driverSeatVentilation_").toIntOrNull() ?: 0
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticDriverSeatVentilation = lvl
                    if (lvl > 0) optimisticDriverSeatHeating = 0
                    status = status?.copy(
                        driverSeatVentilation = lvl,
                        driverSeatHeating = if (lvl > 0) 0 else status?.driverSeatHeating
                    )
                } else if (effectiveCmdName.startsWith("passengerSeatHeating_")) {
                    val lvl = effectiveCmdName.removePrefix("passengerSeatHeating_").toIntOrNull() ?: 0
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticPassengerSeatHeating = lvl
                    if (lvl > 0) optimisticPassengerSeatVentilation = 0
                    status = status?.copy(
                        passengerSeatHeating = lvl,
                        passengerSeatVentilation = if (lvl > 0) 0 else status?.passengerSeatVentilation
                    )
                } else if (effectiveCmdName.startsWith("passengerSeatVentilation_")) {
                    val lvl = effectiveCmdName.removePrefix("passengerSeatVentilation_").toIntOrNull() ?: 0
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticPassengerSeatVentilation = lvl
                    if (lvl > 0) optimisticPassengerSeatHeating = 0
                    status = status?.copy(
                        passengerSeatVentilation = lvl,
                        passengerSeatHeating = if (lvl > 0) 0 else status?.passengerSeatHeating
                    )
                } else if (effectiveCmdName.startsWith("leftRearSeatHeating_")) {
                    val lvl = effectiveCmdName.removePrefix("leftRearSeatHeating_").toIntOrNull() ?: 0
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticLeftRearSeatHeating = lvl
                    if (lvl > 0) optimisticLeftRearSeatVentilation = 0
                    status = status?.copy(
                        leftRearSeatHeating = lvl,
                        leftRearSeatVentilation = if (lvl > 0) 0 else status?.leftRearSeatVentilation
                    )
                } else if (effectiveCmdName.startsWith("leftRearSeatVentilation_")) {
                    val lvl = effectiveCmdName.removePrefix("leftRearSeatVentilation_").toIntOrNull() ?: 0
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticLeftRearSeatVentilation = lvl
                    if (lvl > 0) optimisticLeftRearSeatHeating = 0
                    status = status?.copy(
                        leftRearSeatVentilation = lvl,
                        leftRearSeatHeating = if (lvl > 0) 0 else status?.leftRearSeatHeating
                    )
                } else if (effectiveCmdName.startsWith("rightRearSeatHeating_")) {
                    val lvl = effectiveCmdName.removePrefix("rightRearSeatHeating_").toIntOrNull() ?: 0
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticRightRearSeatHeating = lvl
                    if (lvl > 0) optimisticRightRearSeatVentilation = 0
                    status = status?.copy(
                        rightRearSeatHeating = lvl,
                        rightRearSeatVentilation = if (lvl > 0) 0 else status?.rightRearSeatVentilation
                    )
                } else if (effectiveCmdName.startsWith("rightRearSeatVentilation_")) {
                    val lvl = effectiveCmdName.removePrefix("rightRearSeatVentilation_").toIntOrNull() ?: 0
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticRightRearSeatVentilation = lvl
                    if (lvl > 0) optimisticRightRearSeatHeating = 0
                    status = status?.copy(
                        rightRearSeatVentilation = lvl,
                        rightRearSeatHeating = if (lvl > 0) 0 else status?.rightRearSeatHeating
                    )
                } else if (effectiveCmdName.startsWith("steeringWheelHeating_")) {
                    val lvl = effectiveCmdName.removePrefix("steeringWheelHeating_").toIntOrNull() ?: 0
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticSteeringWheelHeating = lvl > 0
                    status = status?.copy(steeringWheelHeating = lvl > 0, steeringWheelHeatingLevel = lvl)
                } else if (effectiveCmdName == "rearviewMirrorHeating_on" || effectiveCmdName == "rearviewMirrorHeating_1" || effectiveCmdName == "rearviewMirrorHeating_2") {
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticRearviewMirrorHeating = true
                    status = status?.copy(rearviewMirrorHeating = true)
                } else if (effectiveCmdName == "rearviewMirrorHeating_off" || effectiveCmdName == "rearviewMirrorHeating_0") {
                    lastComfortActionEpochMs = System.currentTimeMillis()
                    optimisticRearviewMirrorHeating = false
                    status = status?.copy(rearviewMirrorHeating = false)
                } else if (sentryTarget != null) {
                    status = status?.copy(sentryMode = sentryTarget)
                }
            }
        }

        busy { generation ->
            runOnMain(generation) {
                controlFeedback = ControlFeedback(
                    ControlFeedbackFormatter.inProgress(commandName, command.label),
                    ControlFeedbackKind.IN_PROGRESS
                )
            }
            try {
                val api = LeapmotorApi(session)
                if (sentryTarget != null) {
                    val latestBeforeSend = api.getVehicleState()
                    sessionStore.save(session)
                    val latestSentryMode = latestBeforeSend.optBool("sentryMode")
                    val latestStatus = parseStatus(latestBeforeSend)
                    runOnMain(generation) { status = latestStatus }
                    if (SentryModeControlPolicy.isConfirmed(sentryTarget, latestSentryMode)) {
                        runOnMain(generation) {
                            controlFeedback = ControlFeedback(
                                ControlFeedbackFormatter.success(commandName, command.label),
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
                            ControlFeedbackFormatter.success(commandName, command.label),
                            ControlFeedbackKind.SUCCESS
                        )
                    }
                    scheduleControlStatusRefreshes(effectiveCmdName)
                    if (commandName != null && ParkingAnomalyPolicy.shouldCheck(commandName, commandAccepted = true)) {
                        scheduleParkingAnomalyCheck(generation)
                    }
                    return@busy
                }
                var finalText = ControlFeedbackFormatter.success(commandName, command.label)
                var completed = false
                var sentryQuerySucceeded = false
                for (i in 0 until ControlResultPollingPolicy.appMaxAttempts) {
                    Thread.sleep(ControlResultPollingPolicy.delayBeforeAttempt(i))
                    val resp = api.queryControlResult(result.msgID)
                    sessionStore.save(session)
                    if (SentryModeControlPolicy.querySucceeded(resp.opt("result"), resp.opt("code"))) {
                        if (sentryTarget != null) {
                            sentryQuerySucceeded = true
                            val latest = api.getVehicleState()
                            val latestSentryMode = latest.optBool("sentryMode")
                            if (!SentryModeControlPolicy.isConfirmed(sentryTarget, latestSentryMode)) {
                                continue
                            }
                            val confirmedStatus = parseStatus(latest)
                            runOnMain(generation) { status = confirmedStatus }
                        }
                        finalText = ControlFeedbackFormatter.success(commandName, command.label)
                        completed = true
                        break
                    }
                }
                if (!completed && sentryTarget != null && sentryQuerySucceeded) {
                    finalText = ControlFeedbackFormatter.success(commandName, command.label)
                }
                runOnMain(generation) {
                    controlFeedback = ControlFeedback(
                        finalText,
                        if (completed) ControlFeedbackKind.SUCCESS else ControlFeedbackKind.WARNING
                    )
                }
                if (completed) {
                    scheduleControlStatusRefreshes(effectiveCmdName)
                }
                if (commandName != null && ParkingAnomalyPolicy.shouldCheck(commandName, commandAccepted = completed)) {
                    scheduleParkingAnomalyCheck(generation)
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - started
                val apiError = e as? ApiException
                ErrorLogs.repository.record(
                    ErrorLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        category = ErrorLogCategory.CONTROL_FAILURE,
                        stage = "control_${command.cmdid}",
                        httpStatus = apiError?.httpStatus,
                        durationMs = elapsed,
                        retryCount = 0,
                        appVersion = BuildConfig.VERSION_NAME,
                        message = buildString {
                            appendLine("控车异常: ${command.label} (cmdid=${command.cmdid})")
                            appendLine("载荷: ${command.stateJson}")
                            appendLine("错误: ${e.message ?: e.toString()}")
                        }
                    )
                )
                runOnMain(generation) {
                    cancelPendingControlStatusRefreshes()
                    lastLockActionEpochMs = 0L
                    optimisticLockState = null
                    lastTrunkActionEpochMs = 0L
                    optimisticTrunkState = null
                    lastWindowActionEpochMs = 0L
                    optimisticWindowPercent = null
                    lastComfortActionEpochMs = 0L
                    optimisticDriverSeatHeating = null
                    optimisticDriverSeatVentilation = null
                    optimisticPassengerSeatHeating = null
                    optimisticPassengerSeatVentilation = null
                    optimisticLeftRearSeatHeating = null
                    optimisticLeftRearSeatVentilation = null
                    optimisticRightRearSeatHeating = null
                    optimisticRightRearSeatVentilation = null
                    optimisticSteeringWheelHeating = null
                    optimisticRearviewMirrorHeating = null
                    lastFridgeActionEpochMs = 0L
                    optimisticFridgeStatus = null
                    status = preControlStatus
                    if (OperationPasswordErrorPolicy.isPasswordError(e)) {
                        promptUpdateOperationPassword(
                            errorMessage = OperationPasswordErrorPolicy.ERROR_PROMPT_MESSAGE,
                            retryAction = { control(command, commandName) }
                        )
                        return@runOnMain
                    }
                    val errMsg = controlFailureMessage(command.label, e)
                    controlFeedback = ControlFeedback(errMsg, ControlFeedbackKind.ERROR)
                    Toast.makeText(this@MainActivity, errMsg, Toast.LENGTH_SHORT).show()
                }
            } finally {
                runOnMain(generation) {
                    if (activeControlCommandName == effectiveCmdName) {
                        activeControlCommandName = null
                    }
                }
            }
        }
    }

    private fun controlFailureMessage(label: String, error: Exception): String {
        val raw = error.message.orEmpty()
        return when {
            OperationPasswordErrorPolicy.isPasswordError(error) -> OperationPasswordErrorPolicy.ERROR_PROMPT_MESSAGE
            raw.contains("token", ignoreCase = true) || raw.contains("鉴权") -> "登录状态已过期，请重新登录"
            raw.contains("timeout", ignoreCase = true) || raw.contains("超时") -> "${label}响应超时，请稍后重试"
            raw.contains("网络") || raw.contains("连接") || raw.contains("Connect", ignoreCase = true) -> "网络连接异常，请检查网络"
            raw.contains("500") || raw.contains("502") || raw.contains("503") || raw.contains("504") -> "车联服务暂时繁忙，请稍后重试"
            else -> "${label}未完成，请稍后重试"
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
        const val BLUETOOTH_PERMISSION_REQUEST = 1202
        const val AUTO_REFRESH_INTERVAL_MS = 10_000L
        const val POST_CONTROL_STATUS_REFRESH_DELAY_MS = 2_000L
    }
}
