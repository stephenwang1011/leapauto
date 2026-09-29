package com.leapauto.app.ui

import android.content.Context
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColor
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import com.leapauto.app.bluetooth.BleConnectionPhase
import com.leapauto.app.bluetooth.BleConnectionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import com.leapauto.app.PhoneLocationHelper
import com.leapauto.app.SensitiveControlPolicy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.graphics.Bitmap
import com.leapauto.app.ChassisParkingPhoto
import com.leapauto.app.ParkingPhotoLoadState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.leapauto.app.AcTemperatureTarget
import com.leapauto.app.AirCircle
import com.leapauto.app.AirConditioningCommand
import com.leapauto.app.AirOutlet
import com.leapauto.app.AppearanceMode
import com.leapauto.app.ClimateControlRequestState
import com.leapauto.app.ClimateSliderMapping
import com.leapauto.app.ClimatePresetAction
import com.leapauto.app.ClimateTemperatureTone
import com.leapauto.app.ClimateTemperatureToneResolver
import com.leapauto.app.Commands
import com.leapauto.app.ControlFeedback
import com.leapauto.app.ControlFeedbackDisplayPolicy
import com.leapauto.app.ControlFeedbackKind
import com.leapauto.app.EnergyAnalyticsData
import com.leapauto.app.EnergyAnalyticsState
import com.leapauto.app.EnergyCompositionPresentation
import com.leapauto.app.EnergyHomeCardPolicy
import com.leapauto.app.EnergyWeekPeriodFormatter
import com.leapauto.app.ExternalLinks
import com.leapauto.app.ExternalMapApp
import com.leapauto.app.ExternalMapLauncher
import com.leapauto.app.GeetestCaptchaResult
import com.leapauto.app.GeetestChallenge
import com.leapauto.app.GeocodedAddress
import com.leapauto.app.HomeClimateTogglePresentation
import com.leapauto.app.HomeClimateTogglePresentationMapper
import com.leapauto.app.HvacCapability
import com.leapauto.app.HvacOperation
import com.leapauto.app.MainNavigationTabs
import com.leapauto.app.PgyerRelease
import com.leapauto.app.RearSeatComfortPolicy
import com.leapauto.app.VehicleDrivingSafetyPolicy
import com.leapauto.app.QuickCommandExecutionPolicy
import com.leapauto.app.QuickCommandOrderPolicy
import com.leapauto.app.R
import com.leapauto.app.SUPPORTED_VEHICLE_MODELS
import com.leapauto.app.SentryModeControlPolicy
import com.leapauto.app.SessionExpiredDialogAction
import com.leapauto.app.SessionExpiredDialogPolicy
import com.leapauto.app.SessionStore
import com.leapauto.app.CarModel3DManager
import com.leapauto.app.TireStatus
import com.leapauto.app.TrunkState
import com.leapauto.app.VehicleAppearance
import com.leapauto.app.VehicleAppearanceCatalog
import com.leapauto.app.VehicleConfigConfirmationPolicy
import com.leapauto.app.VehicleHomeStatus
import com.leapauto.app.VehicleImageCache
import com.leapauto.app.Vehicle
import com.leapauto.app.VehicleLocationAvailability
import com.leapauto.app.VehicleLocationMapDomain
import com.leapauto.app.VehicleLocationMapModel
import com.leapauto.app.VehicleLocationMapState
import com.leapauto.app.VehicleLocationSnapshot
import com.leapauto.app.VehicleLocationSummary
import com.leapauto.app.VehicleLocationSummaryPresentation
import com.leapauto.app.VehicleQuickControlCapabilities
import com.leapauto.app.VehicleStatus
import com.leapauto.app.VehicleStatusMapper
import com.leapauto.app.VersionUpdatePromptPolicy
import com.leapauto.app.VersionUpdateState
import com.leapauto.app.Widget4x2ActionPolicy
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.glassInsetSurface
import com.leapauto.app.ui.theme.glassSurface
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn
import java.util.Locale
import kotlin.math.roundToInt

private const val MAP_ACTION_NAVIGATE = "navigate"
private const val SMS_RESEND_COUNTDOWN_SECONDS = 60

private val HybridRangeIconSize = 16.dp
private val PureElectricRangeValueSize = 38.sp
private val PureElectricRangeValueLineHeight = 38.sp
private val PureElectricRangeUnitSize = 16.sp
private val PureElectricRangeUnitLineHeight = 18.sp
private const val PureElectricProgressMaxWidthFraction = 0.44f

data class Cmd(val name: String, val label: String, @DrawableRes val iconRes: Int)

val allCommands = listOf(
    Cmd("windowOpen", "车窗半开", R.drawable.ic_window_half),
    Cmd("windowClose", "车窗全关", R.drawable.ic_phosphor_wind),
    Cmd("sunshadeGroup", "遮阳帘", R.drawable.ic_phosphor_sun),
    Cmd("horn", "鸣笛寻车", R.drawable.ic_phosphor_bell_ringing)
)

private enum class ScreenDestination(val navigationOrder: Int) {
    LOGIN(0),
    HOME(1),
    LOCATION_DETAIL(2),
    CLIMATE_CONTROL(2),
    ACCOUNT(3)
}

private enum class EnergyHomePage {
    SUMMARY,
    RECENT_MILEAGE,
    WEEKLY_CONSUMPTION,
    WEEKLY_COMPOSITION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeapAutoScreen(
    loggedIn: Boolean,
    busy: Boolean,
    status: VehicleStatus?,
    statusUpdatedAtEpochMs: Long = 0L,
    locationSnapshot: VehicleLocationSnapshot? = null,
    vehicleAddress: GeocodedAddress? = null,
    liveWeather: com.leapauto.app.weather.LiveWeather? = null,
    vehicleVin: String = "",
    statusError: String,
    controlFeedback: ControlFeedback?,
    activeControlCommand: String? = null,
    climateTemperatureRequestState: ClimateControlRequestState = ClimateControlRequestState(),
    vehicleModel: String,
    vehicleDisplayModel: String = vehicleModel,
    vehicleConfig: SessionStore.VehicleConfig = SessionStore.VehicleConfig(),
    vehicleAppearance: VehicleAppearance = VehicleAppearanceCatalog.resolveAppearance(
        vehicleDisplayModel,
        vehicleConfig.color
    ),
    availableVehicles: List<Vehicle> = emptyList(),
    onSwitchVehicle: (String) -> Unit = {},
    hvacCapability: HvacCapability = HvacCapability.fallback(),
    pinSaved: Boolean,
    pinSetupInProgress: Boolean,
    pinSetupErrorMessage: String = "",
    showVehicleConfigConfirmationPrompt: Boolean,
    bluetoothState: BleConnectionState = BleConnectionState(),
    widget4x2Actions: List<String> = Widget4x2ActionPolicy.DEFAULT_ACTIONS,
    onWidget4x2ActionsChange: (List<String>) -> Unit = {},
    appearanceMode: AppearanceMode,
    energyState: EnergyAnalyticsState = EnergyAnalyticsState.Idle,
    healthyChargeLimitSoc: Int = 80,
    scheduledChargeEnabled: Boolean = false,
    scheduledChargeStartTime: String = "23:00",
    scheduledChargeEndTime: String = "07:00",
    scheduledChargeContinueUntilLimit: Boolean = true,
    scheduledChargeCirculation: Int = 1,
    scheduledChargeCycles: String = "1,1,1,1,1,1,1",
    scheduledPreheatEnabled: Boolean = false,
    scheduledPreheatStartTime: String = "23:00",
    scheduledPreheatDays: String = "1,1,1,1,1,1,1",
    onApplyChargingSettings: (Boolean, Int, Boolean, String, String, Boolean, Int, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onApplyScheduledPreheat: (Boolean, String, String) -> Unit = { _, _, _ -> },
    networkDebugEnabled: Boolean = false,
    vehicleImageVersion: Int = 0,
    currentVersion: String,
    currentReleaseNotes: String,
    versionUpdateState: VersionUpdateState,
    vehicleOtaState: com.leapauto.app.VehicleOtaState = com.leapauto.app.VehicleOtaState.Idle,
    onCheckVehicleOta: () -> Unit = {},
    onDownloadVehicleOta: (String) -> Unit = {},
    onInstallVehicleOta: (String, String) -> Unit = { _, _ -> },
    onScheduleVehicleOta: (String, String, String) -> Unit = { _, _, _ -> },
    handledUpdateVersion: String? = null,
    showAuthorSupportDialog: Boolean = false,
    showSessionExpiredDialog: Boolean = false,
    phone: String,
    onPhoneChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    onSendSms: () -> Unit,
    smsCountdownSeconds: Int,
    geetestChallenge: GeetestChallenge?,
    onGeetestSuccess: (GeetestCaptchaResult) -> Unit,
    onDismissGeetest: () -> Unit,
    onLoginWithRawAuth: (String, String) -> Unit = { _, _ -> },
    onLogin: () -> Unit,
    onSavePin: () -> Unit,
    onCancelPinSetup: () -> Unit,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onSaveVehicleConfig: (String, String, SessionStore.VehiclePowerType?, String, String) -> Unit = { _, _, _, _, _ -> },
    onNetworkDebugEnabledChange: (Boolean) -> Unit = {},
    onCheckForUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onDismissVersionUpdatePrompt: (String) -> Unit = {},
    onOpenVersionUpdatePrompt: (String) -> Unit = {},
    downloadUpdateProgress: Int? = null,
    onStartInAppUpdate: (PgyerRelease) -> Unit = {},
    onDismissAuthorSupport: () -> Unit,
    onDisableAuthorSupport: () -> Unit,
    onOpenFeedback: () -> Unit,
    onSessionExpiredConfirmed: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshEnergy: () -> Unit = {},
    onAutoRefreshActiveChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onControl: (String) -> Unit,
    onFridgeControl: (com.leapauto.app.FridgeControlCommand) -> Unit = {},
    onApplyClimateSettings: (AirConditioningCommand) -> Unit = {},
    onDismissControlFeedback: () -> Unit,
    onQuickAc: (Int, Long) -> Unit = { _, _ -> },
    onSelectCustomVehicleImage: (Uri) -> Unit = {},
    onResetCustomVehicleImage: () -> Unit = {},
    onUpdateNickname: (String) -> Unit = {},
    bluetoothSettingsRequestId: Long = 0,
    onPowerTypeChange: (SessionStore.VehiclePowerType) -> Unit = {},
    onOpenBluetoothKey: () -> Unit = {},
    onRetryDownload3D: () -> Unit = {},
    onStraightMove: (com.leapauto.app.bluetooth.BleStraightAction) -> Unit = {},
    onStraightRemoteActiveChange: (Boolean) -> Unit = {},
    onFetchParkingPhoto: ((ChassisParkingPhoto?, Bitmap?) -> Unit) -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var settingsTitleTapCount by remember(loggedIn, selectedTab) { mutableIntStateOf(0) }
    var showVehicleLocation by rememberSaveable { mutableStateOf(false) }
    var showClimateControl by rememberSaveable { mutableStateOf(false) }
    var showHealthyChargingSheet by rememberSaveable { mutableStateOf(false) }
    var showVehicleHealthCheckSheet by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirmationDialog by rememberSaveable { mutableStateOf(false) }

    val destination = when {
        !loggedIn -> ScreenDestination.LOGIN
        showVehicleLocation && selectedTab == MainNavigationTabs.VEHICLE -> ScreenDestination.LOCATION_DETAIL
        showClimateControl && selectedTab == MainNavigationTabs.VEHICLE -> ScreenDestination.CLIMATE_CONTROL
        selectedTab == MainNavigationTabs.ACCOUNT -> ScreenDestination.ACCOUNT
        else -> ScreenDestination.HOME
    }
    val appBarTitle = when {
        loggedIn && showVehicleLocation -> "车辆位置"
        loggedIn && showClimateControl -> "空调"
        loggedIn -> when (selectedTab) {
            MainNavigationTabs.ACCOUNT -> "设置"
            else -> "爱车"
        }
        else -> "零跑智控"
    }
    val appBarSubtitle: String? = if (loggedIn) {
        null
    } else {
        "连接你的每一次出发"
    }

    val hasSubpage = selectedTab == MainNavigationTabs.ACCOUNT ||
        showVehicleLocation ||
        showClimateControl
    val updatePromptRelease = (versionUpdateState as? VersionUpdateState.UpdateAvailable)?.latestRelease
    val shouldShowVersionUpdatePrompt = updatePromptRelease != null &&
        VersionUpdatePromptPolicy.shouldShow(
            state = versionUpdateState,
            handledVersion = handledUpdateVersion,
            loggedIn = loggedIn,
            onVehicleTab = selectedTab == MainNavigationTabs.VEHICLE
        ) &&
        !pinSetupInProgress &&
        !showVehicleConfigConfirmationPrompt &&
        !showSessionExpiredDialog &&
        !showAuthorSupportDialog
    val hasBlockingPrompt = pinSetupInProgress ||
        showVehicleConfigConfirmationPrompt || showSessionExpiredDialog || showAuthorSupportDialog ||
        shouldShowVersionUpdatePrompt
    val closeSubpage: () -> Unit = {
        if (selectedTab == MainNavigationTabs.ACCOUNT) {
            selectedTab = MainNavigationTabs.VEHICLE
        } else {
            showVehicleLocation = false
            showClimateControl = false
        }
    }

    // Compose's BackHandler also receives Android predictive/edge-back gestures.
    // Keep the current root tab intact so a detail page returns to its entry point.
    BackHandler(enabled = loggedIn && hasSubpage && !hasBlockingPrompt) {
        closeSubpage()
    }

    LaunchedEffect(loggedIn, selectedTab, vehicleVin) {
        if (!loggedIn) {
            showVehicleLocation = false
            showClimateControl = false
        }
        onAutoRefreshActiveChange(loggedIn && selectedTab == MainNavigationTabs.VEHICLE)
    }

    LaunchedEffect(loggedIn, bluetoothSettingsRequestId) {
        if (loggedIn && bluetoothSettingsRequestId > 0) {
            selectedTab = MainNavigationTabs.ACCOUNT
            showVehicleLocation = false
            showClimateControl = false
            onOpenBluetoothKey()
        }
    }

    if (loggedIn && pinSetupInProgress && !showSessionExpiredDialog) {
        AlertDialog(
            onDismissRequest = onCancelPinSetup,
            modifier = solidDialogModifier(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = if (pinSetupErrorMessage.isNotBlank()) "更新操控密码" else "设置操控密码",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (pinSetupErrorMessage.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_phosphor_warning),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    pinSetupErrorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        Text(
                            "请输入零跑APP上您设置过的4位操控密码。保存后用于远程控车指令鉴权。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = pin,
                        onValueChange = onPinChange,
                        label = { Text("4 位数字密码") },
                        placeholder = { Text("请输入4位数字控车密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.matches(Regex("\\d{4}")),
                    onClick = onSavePin
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = onCancelPinSetup) { Text("取消") }
            }
        )
    }

    if (loggedIn && showSessionExpiredDialog) {
        AlertDialog(
            // This dialog is intentionally blocking: back/outside dismissal must not clear the session.
            onDismissRequest = {
                if (SessionExpiredDialogPolicy.shouldLogout(SessionExpiredDialogAction.DISMISS_REQUESTED)) {
                    onSessionExpiredConfirmed()
                }
            },
            modifier = solidDialogModifier(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(24.dp),
            title = { Text("登录状态已失效") },
            text = { Text("你的账号已在其他地方登录，请在零跑智控APP中重新登录后再使用。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (SessionExpiredDialogPolicy.shouldLogout(SessionExpiredDialogAction.CONFIRM_RELOGIN)) {
                            onSessionExpiredConfirmed()
                        }
                    }
                ) { Text("确定并重新登录") }
            }
        )
    }

    if (
        loggedIn &&
        showAuthorSupportDialog &&
        !pinSetupInProgress &&
        !showVehicleConfigConfirmationPrompt &&
        !showSessionExpiredDialog
    ) {
        AuthorSupportDialog(
            onDismiss = onDismissAuthorSupport,
            onDisable = onDisableAuthorSupport
        )
    }

    updatePromptRelease?.takeIf { shouldShowVersionUpdatePrompt }?.let { release ->
        VersionUpdatePromptDialog(
            release = release,
            downloadProgress = downloadUpdateProgress,
            onDismiss = {
                if (downloadUpdateProgress == null || downloadUpdateProgress == 100) {
                    onDismissVersionUpdatePrompt(release.versionName)
                }
            },
            onUpdate = { onStartInAppUpdate(release) }
        )
    }

    if (showHealthyChargingSheet) {
        HealthyChargingBottomSheet(
            onDismissRequest = { showHealthyChargingSheet = false },
            status = status,
            currentLimitSoc = healthyChargeLimitSoc,
            isHealthyChargeEnabled = status?.healthyChargeEnabled ?: true,
            initialScheduledChargeEnabled = scheduledChargeEnabled,
            initialScheduledStartTime = scheduledChargeStartTime,
            initialScheduledEndTime = scheduledChargeEndTime,
            initialContinueUntilLimit = scheduledChargeContinueUntilLimit,
            initialScheduledCirculation = scheduledChargeCirculation,
            initialScheduledCycles = scheduledChargeCycles,
            initialScheduledPreheatEnabled = scheduledPreheatEnabled,
            initialScheduledPreheatStartTime = scheduledPreheatStartTime,
            initialScheduledPreheatDays = scheduledPreheatDays,
            onApplyChargingSettings = { healthyEnabled, targetSoc, schedEnabled, startTime, endTime, continueUntilLimit, circulation, cycles ->
                onApplyChargingSettings(
                    healthyEnabled,
                    targetSoc,
                    schedEnabled,
                    startTime,
                    endTime,
                    continueUntilLimit,
                    circulation,
                    cycles
                )
                showHealthyChargingSheet = false
            },
            onApplyScheduledPreheat = { preheatEnabled, startTime, days ->
                onApplyScheduledPreheat(preheatEnabled, startTime, days)
            },
            onControl = onControl,
            onRefreshStatus = onRefresh
        )
    }

    if (showVehicleHealthCheckSheet) {
        val context = LocalContext.current
        val remoteBitmap = remember(vehicleVin, vehicleImageVersion) {
            if (vehicleVin.isNotBlank()) VehicleImageCache.loadWidgetBitmap(context, vehicleVin) else null
        }
        VehicleHealthCheckBottomSheet(
            status = status,
            vehicleAppearance = vehicleAppearance,
            vehicleNickname = vehicleConfig.nickname.ifBlank { vehicleDisplayModel },
            remoteBitmap = remoteBitmap,
            vehicleVin = vehicleVin,
            vehicleImageVersion = vehicleImageVersion,
            onControl = onControl,
            onRefreshStatus = onRefresh,
            onDismissRequest = { showVehicleHealthCheckSheet = false }
        )
    }

    geetestChallenge?.let { challenge ->
        GeetestCaptchaDialog(
            challenge = challenge,
            onSuccess = onGeetestSuccess,
            onDismiss = onDismissGeetest
        )
    }

    if (showLogoutConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmationDialog = false },
            modifier = solidDialogModifier(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "退出登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "确定要退出当前账号吗？退出后将清除本地会话与操控密码，桌面插件也将暂停更新。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmationDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("确定退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmationDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    val isAppDark = LocalAppDarkTheme.current
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isAppDark) {
                // 浅色模式：方案1【曜石冷钛 · 晶透液态玻璃】，背景沉降拉开景深，衬托通透水晶卡片
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFCED4DC),
                            Color(0xFFD6DCE4),
                            Color(0xFFDFE5ED)
                        ),
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE8EEF7).copy(alpha = 0.60f),
                            Color(0xFFDCE2EC).copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                        radius = size.width * 0.90f
                    )
                )
            } else {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B).copy(alpha = 0.45f),
                            Color(0xFF0F172A).copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                        radius = size.width * 0.85f
                    )
                )
            }
        }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (destination != ScreenDestination.HOME && destination != ScreenDestination.LOGIN) {
                    TopAppBar(
                        navigationIcon = if (showVehicleLocation || showClimateControl ||
                            selectedTab == MainNavigationTabs.ACCOUNT
                        ) {
                            {
                                IconButton(onClick = closeSubpage) {
                                    Icon(
                                        painterResource(R.drawable.ic_phosphor_arrow_left),
                                        contentDescription = "返回爱车",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else {
                            {}
                        },
                        title = {
                            Column {
                                Text(
                                    appBarTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = if (destination == ScreenDestination.ACCOUNT) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            settingsTitleTapCount = (settingsTitleTapCount + 1).coerceAtMost(5)
                                        }
                                    } else Modifier
                                )
                                appBarSubtitle?.let { subtitle ->
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            if (selectedTab == MainNavigationTabs.ACCOUNT) {
                                TextButton(
                                    onClick = { showLogoutConfirmationDialog = true },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(
                                        "登出",
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
    ) { padding ->
        if (!loggedIn) {
            LoginContent(
                phone = phone,
                onPhoneChange = onPhoneChange,
                code = code,
                onCodeChange = onCodeChange,
                onSendSms = onSendSms,
                onLogin = onLogin,
                busy = busy,
                smsCountdownSeconds = smsCountdownSeconds,
                onLoginWithRawAuth = onLoginWithRawAuth
            )
        } else {
            val homeAlpha by animateFloatAsState(
                targetValue = if (destination == ScreenDestination.HOME) 1f else 0f,
                animationSpec = tween(durationMillis = 180),
                label = "home_keepalive_alpha"
            )

            // 保持主页顶部状态栏安全边距，避免顶到物理状态栏，同时在切到有 TopAppBar 的子页面时主页不发生纵向跳变
            var homeTopPadding by remember { mutableStateOf(0.dp) }
            if (destination == ScreenDestination.HOME && padding.calculateTopPadding() > 0.dp) {
                homeTopPadding = padding.calculateTopPadding()
            }
            val effectiveHomeTopPadding = if (homeTopPadding > 0.dp) {
                homeTopPadding
            } else {
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // 1. HOME 主界面：永远常驻，保持 3D 车模及状态不被销毁
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = effectiveHomeTopPadding,
                            bottom = padding.calculateBottomPadding()
                        )
                        .graphicsLayer {
                            alpha = homeAlpha
                            translationX = if (destination == ScreenDestination.HOME || homeAlpha > 0.05f) 0f else -50000f
                        }
                ) {
                    HomeContent(
                        busy,
                        vehicleVin,
                        vehicleModel,
                        vehicleDisplayModel,
                        vehicleAppearance,
                        status,
                        statusUpdatedAtEpochMs,
                        vehicleConfig.nickname,
                        locationSnapshot,
                        vehicleAddress,
                        liveWeather,
                        energyState,
                        statusError,
                        controlFeedback,
                        onRefresh,
                        onRefreshEnergy,
                        onControl,
                        onDismissControlFeedback,
                        onQuickAc,
                        onOpenClimateControl = {
                            showClimateControl = true
                        },
                        onOpenHealthyCharging = {
                            showHealthyChargingSheet = true
                        },
                        onOpenAccount = {
                            selectedTab = MainNavigationTabs.ACCOUNT
                            showVehicleLocation = false
                            showClimateControl = false
                        },
                        vehicleImageVersion = vehicleImageVersion,
                        availableVehicles = availableVehicles,
                        onSwitchVehicle = onSwitchVehicle,
                        onOpenHealthCheck = {
                            showVehicleHealthCheckSheet = true
                        },
                        onUpdateNickname = onUpdateNickname,
                        onFetchParkingPhoto = onFetchParkingPhoto,
                        activeControlCommand = activeControlCommand,
                        onFridgeControl = onFridgeControl,
                        hvacCapability = hvacCapability,
                        onApplyClimateSettings = onApplyClimateSettings,
                        onRetryDownload3D = onRetryDownload3D,
                        bluetoothState = bluetoothState,
                        onStraightMove = onStraightMove,
                        onStraightRemoteActiveChange = onStraightRemoteActiveChange,
                        onOpenBluetoothKey = onOpenBluetoothKey
                    )
                }

                // 2. 子页面容器（设置/空调/位置）：以平滑动画覆盖在主页之上，返回时直接露出已就绪的主页
                AnimatedContent(
                    targetState = destination,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 150))
                    },
                    label = "subpage-navigation"
                ) { target ->
                    when (target) {
                        ScreenDestination.HOME, ScreenDestination.LOGIN -> {
                            // 主页状态下，子页面层为空，完全展示底层常驻的 HomeContent
                        }
                        ScreenDestination.LOCATION_DETAIL -> {
                            VehicleLocationDetailContent(
                                summary = status?.locationSummary,
                                locationSnapshot = locationSnapshot,
                                liveWeather = liveWeather,
                                onHorn = onControl,
                            )
                        }
                        ScreenDestination.CLIMATE_CONTROL -> {
                            ClimateControlContent(
                                status = status,
                                statusError = statusError,
                                busy = busy,
                                controlFeedback = controlFeedback,
                                statusUpdatedAtEpochMs = statusUpdatedAtEpochMs,
                                hvacCapability = hvacCapability,
                                vehicleModel = vehicleModel,
                                onDismissRequest = { showClimateControl = false },
                                onDismissControlFeedback = onDismissControlFeedback,
                                onRefresh = onRefresh,
                                onControl = onControl,
                                onApplyClimateSettings = onApplyClimateSettings
                            )
                        }
                        ScreenDestination.ACCOUNT -> {
                            MyContent(
                                phone = phone,
                                pinSaved = pinSaved,
                                pin = pin,
                                onPinChange = onPinChange,
                                onSavePin = onSavePin,
                                pinSetupInProgress = pinSetupInProgress,
                                onCancelPinSetup = onCancelPinSetup,
                                widget4x2Actions = widget4x2Actions,
                                onWidget4x2ActionsChange = onWidget4x2ActionsChange,
                                appearanceMode = appearanceMode,
                                onAppearanceModeChange = onAppearanceModeChange,
                                vehicleModel = vehicleModel,
                                vehicleConfig = vehicleConfig,
                                onSaveVehicleConfig = onSaveVehicleConfig,
                                currentVersion = currentVersion,
                                currentReleaseNotes = currentReleaseNotes,
                                versionUpdateState = versionUpdateState,
                                vehicleOtaState = vehicleOtaState,
                                onCheckVehicleOta = onCheckVehicleOta,
                                onDownloadVehicleOta = onDownloadVehicleOta,
                                onInstallVehicleOta = onInstallVehicleOta,
                                onScheduleVehicleOta = onScheduleVehicleOta,
                                onCheckForUpdate = onCheckForUpdate,
                                onOpenUpdate = onOpenUpdate,
                                downloadUpdateProgress = downloadUpdateProgress,
                                onStartInAppUpdate = onStartInAppUpdate,
                                availableVehicles = availableVehicles,
                                onSwitchVehicle = onSwitchVehicle,
                                vehicleVin = vehicleVin,
                                vehicleImageVersion = vehicleImageVersion,
                                onSelectCustomVehicleImage = onSelectCustomVehicleImage,
                                onResetCustomVehicleImage = onResetCustomVehicleImage,
                                showBluetoothKeyEntry = settingsTitleTapCount >= 5,
                                onOpenBluetoothKey = onOpenBluetoothKey,
                                onPowerTypeChange = onPowerTypeChange,
                                onLogout = onLogout
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun LoginContent(
    phone: String,
    onPhoneChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    busy: Boolean = false,
    smsCountdownSeconds: Int = 0,
    onLoginWithRawAuth: (String, String) -> Unit = { _, _ -> }
) {
    val haptic = LocalHapticFeedback.current
    val canLogin = phone.length == 11 && code.length >= 4 && !busy
    val canSendSms = smsCountdownSeconds == 0 && phone.length == 11 && !busy
    var showTokenImportDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(52.dp))

        var titleTapCount by remember { mutableIntStateOf(0) }
        var lastTapEpochMs by remember { mutableLongStateOf(0L) }

        Text(
            text = "零跑智控",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapEpochMs > 1500L) {
                        titleTapCount = 1
                    } else {
                        titleTapCount += 1
                        if (titleTapCount >= 5) {
                            titleTapCount = 0
                            showTokenImportDialog = true
                        }
                    }
                    lastTapEpochMs = now
                }
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "连接你的每一次出发",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        // 登录卡片
        val loginAura = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .frostedGlassCard(
                    shape = RoundedCornerShape(22.dp),
                    auraColor = loginAura,
                    auraCenter = Offset(0.5f, 0.2f)
                ),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = glassCardBorder(),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "账号验证登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 手机号输入框
                OutlinedTextField(
                    value = phone,
                    onValueChange = { onPhoneChange(it.filter(Char::isDigit).take(11)) },
                    label = { Text("手机号") },
                    placeholder = { Text("零跑 App 注册手机号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        if (phone.isNotEmpty()) {
                            IconButton(onClick = { onPhoneChange("") }) {
                                Text(
                                    "✕",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // 验证码输入框（行内尾部整合“获取验证码”按钮）
                OutlinedTextField(
                    value = code,
                    onValueChange = { onCodeChange(it.filter(Char::isDigit).take(6)) },
                    label = { Text("短信验证码") },
                    placeholder = { Text("6 位验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSendSms()
                            },
                            enabled = canSendSms,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = if (smsCountdownSeconds == 0) "获取验证码" else "${smsCountdownSeconds}s",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                // 登录大按钮
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLogin()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                    ),
                    enabled = canLogin
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "登录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (showTokenImportDialog) {
            var tokenInput by remember { mutableStateOf("") }
            var phoneInput by remember { mutableStateOf(phone) }
            AlertDialog(
                onDismissRequest = { showTokenImportDialog = false },
                modifier = solidDialogModifier(shape = RoundedCornerShape(24.dp)),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        "Token 凭据导入",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "请确认关联手机号（用于旧链路远控续期与签名），并粘贴完整登录响应 JSON：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it.filter(Char::isDigit).take(11) },
                            label = { Text("关联手机号 (必填，用于远控续期)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("凭据 JSON 内容") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            placeholder = { Text("粘贴包含 accountId、token、refreshToken 的完整 JSON…") },
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tokenInput.isNotBlank()) {
                                showTokenImportDialog = false
                                onLoginWithRawAuth(tokenInput, phoneInput)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        enabled = tokenInput.isNotBlank() && phoneInput.length == 11 && !busy
                    ) {
                        Text("导入并登录")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showTokenImportDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消")
                    }
                }
            )
        }

        Spacer(Modifier.height(28.dp))

        // 底部安全凭证提示胶囊
        Surface(
            modifier = Modifier.frostedGlassCard(shape = RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_lock),
                    contentDescription = "安全存储",
                    tint = MaterialTheme.statusGood,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "直连零跑官方车联服务 · 凭据本地硬件级加密存储",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CabinDivider(
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp
) {
    val isDark = LocalAppDarkTheme.current
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF0F172A).copy(alpha = 0.06f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(0.6.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        dividerColor,
                        dividerColor,
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
private fun CabinVerticalDivider(
    height: androidx.compose.ui.unit.Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF0F172A).copy(alpha = 0.06f)
    Box(
        modifier = modifier
            .width(0.6.dp)
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        dividerColor,
                        dividerColor,
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeContent(
    isRefreshing: Boolean, vehicleVin: String, vehicleModel: String, vehicleDisplayModel: String = vehicleModel,
    vehicleAppearance: VehicleAppearance = VehicleAppearanceCatalog.resolveAppearance(vehicleDisplayModel, null),
    status: VehicleStatus?, statusUpdatedAtEpochMs: Long, vehicleNickname: String,
    locationSnapshot: VehicleLocationSnapshot?,
    vehicleAddress: GeocodedAddress? = null,
    liveWeather: com.leapauto.app.weather.LiveWeather? = null,
    energyState: EnergyAnalyticsState,
    statusError: String,
    controlFeedback: ControlFeedback?, onRefresh: () -> Unit, onRefreshEnergy: () -> Unit,
    onControl: (String) -> Unit, onDismissControlFeedback: () -> Unit, onQuickAc: (Int, Long) -> Unit,
    onOpenClimateControl: () -> Unit,
    onOpenHealthyCharging: () -> Unit = {},
    onOpenAccount: () -> Unit,
    vehicleImageVersion: Int = 0,
    availableVehicles: List<Vehicle> = emptyList(),
    onSwitchVehicle: (String) -> Unit = {},
    onOpenHealthCheck: () -> Unit = {},
    onUpdateNickname: (String) -> Unit = {},
    onFetchParkingPhoto: ((ChassisParkingPhoto?, Bitmap?) -> Unit) -> Unit = {},
    activeControlCommand: String? = null,
    onFridgeControl: (com.leapauto.app.FridgeControlCommand) -> Unit = {},
    hvacCapability: HvacCapability = HvacCapability.fallback(),
    onApplyClimateSettings: (AirConditioningCommand) -> Unit = {},
    onRetryDownload3D: () -> Unit = {},
    bluetoothState: BleConnectionState = BleConnectionState(),
    onStraightMove: (com.leapauto.app.bluetooth.BleStraightAction) -> Unit = {},
    onStraightRemoteActiveChange: (Boolean) -> Unit = {},
    onOpenBluetoothKey: (() -> Unit)? = null
) {
    var showAddressNavigationDialog by rememberSaveable { mutableStateOf(false) }
    var showParkingDetailDialog by rememberSaveable { mutableStateOf(false) }
    var showFridgeControlBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showClimateControlBottomSheet by rememberSaveable { mutableStateOf(false) }

    var isPullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            isPullRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshing,
        onRefresh = {
            isPullRefreshing = true
            onRefresh()
            onRefreshEnergy()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            val density = LocalDensity.current
            var topContentHeightPx by remember { mutableIntStateOf(0) }
            val minCardHeight = EnergyHomeCardPolicy.MIN_CARD_HEIGHT_DP.dp
            val bottomPadding = EnergyHomeCardPolicy.BOTTOM_PADDING_DP.dp
            val dynamicCardHeight = remember(topContentHeightPx, viewportHeight) {
                val heightDp = EnergyHomeCardPolicy.calculateDynamicCardHeightDp(
                    viewportHeightPx = with(density) { viewportHeight.roundToPx() },
                    topContentHeightPx = topContentHeightPx,
                    topPaddingPx = with(density) { 4.dp.roundToPx() },
                    spacingPx = with(density) { 4.dp.roundToPx() },
                    bottomPaddingPx = with(density) { bottomPadding.roundToPx() },
                    density = density.density,
                    minHeightDp = EnergyHomeCardPolicy.MIN_CARD_HEIGHT_DP
                )
                heightDp.dp
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { topContentHeightPx = it.height },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VehicleHero(
                        vehicleDisplayModel,
                        vehicleAppearance,
                        status,
                        statusUpdatedAtEpochMs,
                        vehicleNickname,
                        onOpenAccount,
                        vehicleAddress = vehicleAddress?.shortAddress,
                        liveWeather = liveWeather,
                        onAddressClick = {
                            if (locationSnapshot != null) {
                                showAddressNavigationDialog = true
                            }
                        },
                        vehicleVin = vehicleVin,
                        vehicleImageVersion = vehicleImageVersion,
                        onControl = onControl,
                        onOpenHealthyCharging = onOpenHealthyCharging,
                        availableVehicles = availableVehicles,
                        onSwitchVehicle = onSwitchVehicle,
                        onOpenHealthCheck = onOpenHealthCheck,
                        onUpdateNickname = onUpdateNickname,
                        onRetryDownload3D = onRetryDownload3D,
                        onParkingClick = { showParkingDetailDialog = true },
                        locationSnapshot = locationSnapshot,
                        activeControlCommand = activeControlCommand,
                        controlFeedback = controlFeedback,
                        onDismissControlFeedback = onDismissControlFeedback,
                        bluetoothState = bluetoothState,
                        onStraightMove = onStraightMove,
                        onStraightRemoteActiveChange = onStraightRemoteActiveChange,
                        onOpenBluetoothKey = onOpenBluetoothKey
                    )

                    // 1. 胎压与车况状态卡片
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HomeTirePressureCard(
                            status = status,
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp),
                            onCarClick = onOpenHealthCheck,
                            seamless = false
                        )
                        VehicleStatusCard(
                            status = status,
                            todayMileage = EnergyHomeCardPolicy.todayMileage((energyState as? EnergyAnalyticsState.Success)?.data),
                            onOpenHealthyCharging = onOpenHealthyCharging,
                            onControl = onControl,
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp),
                            seamless = false
                        )
                    }

                    // 2. 座舱空调与车载冰箱温控舱
                    if (status?.fridgeStatus != null) {
                        // 有车载冰箱车型：左右 50:50 并排双子温控舱
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ClimateOverviewCard(
                                status = status,
                                liveWeather = liveWeather,
                                onOpenClimate = { showClimateControlBottomSheet = true },
                                onQuickAcToggle = onControl,
                                controlBusy = activeControlCommand == "climate" || activeControlCommand?.startsWith("ac") == true,
                                compact = true,
                                seamless = false,
                                modifier = Modifier.weight(1f)
                            )
                            FridgeOverviewCard(
                                fridgeStatus = status.fridgeStatus,
                                onOpenFridgeControl = { showFridgeControlBottomSheet = true },
                                onToggleFridge = { enable ->
                                    val current = status.fridgeStatus
                                    onFridgeControl(
                                        com.leapauto.app.FridgeControlCommand(
                                            enable = enable,
                                            mode = current.mode,
                                            temp = current.targetTemp,
                                            style = current.style,
                                            parkEnable = current.parkEnable,
                                            durationSeconds = current.parkDurationHours * 3600,
                                            cycles = if (current.parkCycles == 1) "2" else "1"
                                        )
                                    )
                                },
                                controlBusy = activeControlCommand?.startsWith("fridge") == true,
                                compact = true,
                                seamless = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        // 无车载冰箱车型：空调卡片全宽独占整行
                        ClimateOverviewCard(
                            status = status,
                            liveWeather = liveWeather,
                            onOpenClimate = { showClimateControlBottomSheet = true },
                            onQuickAcToggle = onControl,
                            controlBusy = activeControlCommand == "climate" || activeControlCommand?.startsWith("ac") == true,
                            compact = false,
                            seamless = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 3. 底部能耗里程大卡片（保持在原本的最底部位置）
                EnergyHomePagerCard(
                    state = energyState,
                    vehicleTotalMileage = status?.totalMileage,
                    vehicleModel = vehicleDisplayModel.ifBlank { vehicleModel },
                    seamless = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dynamicCardHeight)
                )
            }
        }
    }

    if (showParkingDetailDialog || (showAddressNavigationDialog && locationSnapshot != null)) {
        ParkingDetailDialog(
            fullAddress = vehicleAddress?.fullAddress ?: vehicleAddress?.shortAddress.orEmpty(),
            statusUpdatedAtEpochMs = statusUpdatedAtEpochMs,
            locationSnapshot = locationSnapshot,
            liveWeather = liveWeather,
            onFetchParkingPhoto = onFetchParkingPhoto,
            onDismiss = {
                showParkingDetailDialog = false
                showAddressNavigationDialog = false
            }
        )
    }

    if (showFridgeControlBottomSheet && status?.fridgeStatus != null) {
        FridgeControlBottomSheet(
            onDismissRequest = { showFridgeControlBottomSheet = false },
            fridgeStatus = status.fridgeStatus,
            onApplyFridgeControl = onFridgeControl,
            busy = activeControlCommand?.startsWith("fridge") == true
        )
    }

    if (showClimateControlBottomSheet) {
        ClimateControlBottomSheet(
            onDismissRequest = { showClimateControlBottomSheet = false },
            status = status,
            liveWeather = liveWeather,
            busy = activeControlCommand == "climate" || activeControlCommand?.startsWith("ac") == true,
            hvacCapability = hvacCapability,
            vehicleModel = vehicleModel,
            onControl = onControl,
            onApplyClimateSettings = onApplyClimateSettings,
            controlFeedback = controlFeedback,
            onDismissControlFeedback = onDismissControlFeedback
        )
    }
}

@Composable
private fun ControlFeedbackBanner(feedback: ControlFeedback, onDismiss: () -> Unit) {
    val accent = when (feedback.kind) {
        ControlFeedbackKind.SUCCESS -> MaterialTheme.statusGood
        ControlFeedbackKind.WARNING -> MaterialTheme.statusWarn
        ControlFeedbackKind.ERROR -> MaterialTheme.colorScheme.error
        ControlFeedbackKind.SUBMITTED,
        ControlFeedbackKind.IN_PROGRESS -> MaterialTheme.colorScheme.primary
    }
    val container = accent.copy(alpha = 0.12f)

    LaunchedEffect(feedback) {
        ControlFeedbackDisplayPolicy.autoDismissDelayMs(feedback.kind)?.let { delayMs ->
            kotlinx.coroutines.delay(delayMs)
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (feedback.kind == ControlFeedbackKind.IN_PROGRESS) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = accent
                )
            } else {
                Icon(
                    painter = painterResource(
                        when (feedback.kind) {
                            ControlFeedbackKind.SUCCESS -> R.drawable.ic_phosphor_check
                            ControlFeedbackKind.SUBMITTED -> R.drawable.ic_phosphor_arrow_clockwise
                            else -> R.drawable.ic_phosphor_warning
                        }
                    ),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                feedback.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun MyContent(
    phone: String = "",
    pinSaved: Boolean,
    pin: String,
    onPinChange: (String) -> Unit,
    onSavePin: () -> Unit,
    pinSetupInProgress: Boolean,
    onCancelPinSetup: () -> Unit,
    widget4x2Actions: List<String> = Widget4x2ActionPolicy.DEFAULT_ACTIONS,
    onWidget4x2ActionsChange: (List<String>) -> Unit = {},
    appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    onAppearanceModeChange: (AppearanceMode) -> Unit = {},
    vehicleModel: String,
    vehicleConfig: SessionStore.VehicleConfig,
    onSaveVehicleConfig: (String, String, SessionStore.VehiclePowerType?, String, String) -> Unit,
    currentVersion: String,
    currentReleaseNotes: String,
    versionUpdateState: VersionUpdateState,
    vehicleOtaState: com.leapauto.app.VehicleOtaState = com.leapauto.app.VehicleOtaState.Idle,
    onCheckVehicleOta: () -> Unit = {},
    onDownloadVehicleOta: (String) -> Unit = {},
    onInstallVehicleOta: (String, String) -> Unit = { _, _ -> },
    onScheduleVehicleOta: (String, String, String) -> Unit = { _, _, _ -> },
    onCheckForUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    downloadUpdateProgress: Int? = null,
    onStartInAppUpdate: (PgyerRelease) -> Unit = {},
    availableVehicles: List<Vehicle> = emptyList(),
    onSwitchVehicle: (String) -> Unit = {},
    vehicleVin: String = "",
    vehicleImageVersion: Int = 0,
    onSelectCustomVehicleImage: (Uri) -> Unit = {},
    onResetCustomVehicleImage: () -> Unit = {},
    showBluetoothKeyEntry: Boolean = false,
    onOpenBluetoothKey: () -> Unit = {},
    onPowerTypeChange: (SessionStore.VehiclePowerType) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var showVehicleSelectorInAccount by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (availableVehicles.size > 1) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(shape = RoundedCornerShape(16.dp))
                    .clickable { showVehicleSelectorInAccount = true },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = glassCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("切换座驾", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "当前选中：${vehicleConfig.nickname.ifBlank { vehicleModel }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Text(
                                "共 ${availableVehicles.size} 台",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_caret_right),
                            contentDescription = "切换",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showBluetoothKeyEntry) {
            BluetoothKeyEntry(onClick = onOpenBluetoothKey)
        }

        VehicleCustomImageCard(
            vehicleVin = vehicleVin,
            vehicleImageVersion = vehicleImageVersion,
            onSelectImageUri = onSelectCustomVehicleImage,
            onResetToDefault = onResetCustomVehicleImage
        )

        Widget4x2ActionsCard(
            actions = widget4x2Actions,
            onActionsChange = onWidget4x2ActionsChange
        )
        QuickSettingsTileCard()

        SettingsSectionTitle("系统与更新")
        val isSubAccount = availableVehicles.find { it.vin == vehicleVin }?.isSharedAccount == true
        VehicleOtaCard(
            state = vehicleOtaState,
            isSubAccount = isSubAccount,
            onCheck = onCheckVehicleOta,
            onDownload = onDownloadVehicleOta,
            onInstall = onInstallVehicleOta,
            onSchedule = onScheduleVehicleOta
        )
        VersionUpdateCard(
            currentVersion = currentVersion,
            currentReleaseNotes = currentReleaseNotes,
            state = versionUpdateState,
            downloadProgress = downloadUpdateProgress,
            onCheckForUpdate = onCheckForUpdate,
            onOpenUpdate = onOpenUpdate,
            onStartInAppUpdate = onStartInAppUpdate
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showVehicleSelectorInAccount && availableVehicles.size > 1) {
        VehicleSelectorDialog(
            currentVin = vehicleVin,
            vehicles = availableVehicles,
            onSelectVehicle = onSwitchVehicle,
            onDismiss = { showVehicleSelectorInAccount = false }
        )
    }
}

@Composable
private fun ModifyNicknameDialog(
    currentNickname: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentNickname.take(10)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(shape = RoundedCornerShape(24.dp)),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "修改车辆昵称",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "设置便于识别的座驾名称（保存在本机配置中）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= 10) {
                            text = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            "请输入车辆昵称（最多10字）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_phosphor_x),
                                    contentDescription = "清空",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    supportingText = {
                        Text(
                            text = "${text.length}/10",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (text.length == 10) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(text.trim())
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消")
            }
        }
    )
}

private enum class SupportChannel(val label: String, val qrRes: Int) {
    WECHAT("微信", R.drawable.support_wechat_qr),
    ALIPAY("支付宝", R.drawable.support_alipay_qr_safe)
}

@Composable
private fun SupportQrImage(channel: SupportChannel, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(8.dp),
        color = Color.White
    ) {
        Image(
            painter = painterResource(channel.qrRes),
            contentDescription = "${channel.label}收款码",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        )
    }
}

@Composable
private fun AuthorSupportDialog(onDismiss: () -> Unit, onDisable: () -> Unit) {
    var channelName by rememberSaveable { mutableStateOf(SupportChannel.WECHAT.name) }
    val channel = SupportChannel.valueOf(channelName)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 380.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("支持作者", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "每一份支持，都会化为下一次更好的更新。完全自愿，感谢你愿意同行。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SupportChannel.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, SupportChannel.entries.size),
                            onClick = { channelName = item.name },
                            selected = item == channel,
                            label = { Text(item.label) },
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
                SupportQrImage(
                    channel = channel,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                )
                TextButton(
                    onClick = onDisable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "不再提醒",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@Composable
private fun VersionUpdatePromptDialog(
    release: PgyerRelease,
    downloadProgress: Int? = null,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val isDownloading = downloadProgress != null && downloadProgress < 100
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        modifier = solidDialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = { Text("发现新版本") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "最新版本 ${release.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    release.updateDescription
                        ?.takeIf { it.isNotBlank() }
                        ?: "发现新版本，建议更新以获取最新功能和修复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (downloadProgress != null) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !isDownloading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isDownloading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("正在下载 ${downloadProgress}%...")
                    }
                } else {
                    Text("立即更新")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("稍后再说")
                }
            }
        }
    )
}

@Composable
private fun VersionUpdateCard(
    currentVersion: String,
    currentReleaseNotes: String,
    state: VersionUpdateState,
    downloadProgress: Int? = null,
    onCheckForUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onStartInAppUpdate: ((PgyerRelease) -> Unit)? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(220),
        label = "versionCaretRotation"
    )
    val statusColor = when (state) {
        is VersionUpdateState.UpToDate -> MaterialTheme.statusGood
        is VersionUpdateState.UpdateAvailable -> MaterialTheme.colorScheme.primary
        is VersionUpdateState.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (state) {
        VersionUpdateState.Idle -> "尚未检查蒲公英最新版本"
        is VersionUpdateState.Checking -> "正在检查蒲公英最新版本..."
        is VersionUpdateState.UpToDate -> "已是最新版本"
        is VersionUpdateState.UpdateAvailable -> "发现新版本 ${state.latestRelease.versionName}"
        is VersionUpdateState.Failed -> "检查失败：${state.message}"
    }
    val latestRelease = (state as? VersionUpdateState.UpdateAvailable)?.latestRelease

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlassCard(shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Icon(
                        painter = painterResource(
                            if (state is VersionUpdateState.Failed) R.drawable.ic_phosphor_warning
                            else R.drawable.ic_phosphor_check
                        ),
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "当前版本 v$currentVersion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_caret_right),
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(arrowRotation)
                )
            }
            if (expanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.glassInsetSurface
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "本版本更新内容：",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            currentReleaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val updateDesc = latestRelease?.updateDescription
                if (!updateDesc.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.glassInsetSurface
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "新版本更新说明：",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                updateDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (state is VersionUpdateState.UpdateAvailable) {
                    val isDownloading = downloadProgress != null && downloadProgress < 100
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    Button(
                        onClick = { onStartInAppUpdate?.invoke(state.latestRelease) ?: onOpenUpdate() },
                        enabled = !isDownloading,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isDownloading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text("正在下载更新 ${downloadProgress}%...")
                            }
                        } else {
                            Text("立即下载更新 (${state.latestRelease.versionName})")
                        }
                    }
                }
                OutlinedButton(
                    onClick = onCheckForUpdate,
                    enabled = state !is VersionUpdateState.Checking,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("检查更新") }
            }
        }
    }
}

@Composable
private fun VehicleCustomImageCard(
    vehicleVin: String,
    vehicleImageVersion: Int,
    onSelectImageUri: (Uri) -> Unit,
    onResetToDefault: () -> Unit
) {
    val context = LocalContext.current
    val hasCustomImage = remember(vehicleVin, vehicleImageVersion) {
        if (vehicleVin.isNotBlank()) VehicleImageCache.hasCustomImage(context, vehicleVin) else false
    }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectImageUri(uri)
        }
    }

    val generalPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                onSelectImageUri(uri)
            }
        }
    }

    val launchPicker = {
        try {
            // Tier 1: 优先尝试官方照片选择器 (Android 13+ Photo Picker，免存储权限)
            photoPickerLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        } catch (e1: Throwable) {
            try {
                // Tier 2: 降级尝试系统相册 ACTION_PICK (所有自带图库相册的手机均支持)
                val pickIntent = android.content.Intent(
                    android.content.Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                ).apply {
                    setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                }
                generalPickerLauncher.launch(pickIntent)
            } catch (e2: Throwable) {
                try {
                    // Tier 3: 降级尝试 ACTION_GET_CONTENT
                    val getContentIntent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                    }
                    generalPickerLauncher.launch(getContentIntent)
                } catch (fatal: Throwable) {
                    // Tier 4: 终极安全拦截，绝对不闪退
                    android.widget.Toast.makeText(context, "未找到可用系统相册，请检查相册权限或应用", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 第一行：标题 + 右侧无轮廓文字操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "爱车主图定制",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasCustomImage) {
                        TextButton(
                            onClick = { showResetConfirmDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "恢复官图",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1
                            )
                        }
                    }
                    TextButton(
                        onClick = { launchPicker() },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (hasCustomImage) "更换图片" else "相册选择",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            // 第二行：当前状态标签 + 快速指南胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (hasCustomImage) {
                        MaterialTheme.statusGood.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        0.5.dp,
                        if (hasCustomImage) MaterialTheme.statusGood.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (hasCustomImage) "已应用自定义主图" else "当前：官方 3D 渲染图",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (hasCustomImage) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showGuideDialog = true },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_info_circle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "AI 生图指南",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            modifier = solidDialogModifier(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(24.dp),
            title = { Text("恢复官方车模") },
            text = { Text("确定要清除当前自定义主图，恢复为官方提供的标准车模渲染图吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetToDefault()
                    }
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showGuideDialog) {
        VehicleCustomImageGuideDialog(onDismiss = { showGuideDialog = false })
    }
}

@Composable
private fun VehicleCustomImageGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val universalPromptCn = "一艘宏伟的现代化航空母舰/东风导弹发射车/超级跑车（可替换您想生成的任意物品），主体朝向侧前方45度角（3/4侧透视视角），完整位于画面正中央，四周留出15%安全留白，无任何局部被裁切。影棚商业级布光，金属质感细腻，反光真实，细节极其丰富。背景必须为纯白单一纯色实心背景，无渐变、无阴影、无多余杂物、无地平线、无倒影。图片尺寸：1200 × 522 像素，宽高比 2.3:1。"

    val highlightColor = MaterialTheme.colorScheme.primary
    val promptAnnotated = remember(highlightColor) {
        buildAnnotatedString {
            append("一艘宏伟的现代化航空母舰/东风导弹发射车/超级跑车")
            withStyle(
                SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.Bold,
                    background = highlightColor.copy(alpha = 0.12f)
                )
            ) {
                append("（可替换您想生成的任意物品）")
            }
            append("，主体朝向侧前方45度角（3/4侧透视视角），完整位于画面正中央，四周留出15%安全留白，无任何局部被裁切。影棚商业级布光，金属质感细腻，反光真实，细节极其丰富。背景必须为纯白单一纯色实心背景，无渐变、无阴影、无多余杂物、无地平线、无倒影。图片尺寸：1200 × 522 像素，宽高比 2.3:1。")
        }
    }

    fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("ai_prompt", text))
        Toast.makeText(context, "提示词已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_info_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    "AI 生图与抠图指南",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 通用提示词卡片
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "AI 提示词（侧前方 45 度透视）",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = promptAnnotated,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { copyText(universalPromptCn) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_phosphor_copy),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("复制提示词", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // 手机相册 1 秒抠图出图流程
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "手机相册 1 秒抠图出图流程",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "1. 发送上方提示词给 AI，生成纯白底图片并保存到手机相册（纯白底避免 AI 画出假棋盘格，抠图边缘最平滑）\n" +
                        "2. 在手机自带相册打开图片，手指长按主体 1 秒（小米澎湃/华为鸿蒙/vivo/OPPO/iPhone 均支持系统级长按抠图发光）\n" +
                        "3. 弹出菜单点击「存储为图像 / 拷贝」，存为真正的无损透明 PNG\n" +
                        "4. 回到 App 点击「相册选择」上传即可！系统将自动紧凑裁切与合成接地柔光暗影",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
private fun Widget4x2ActionsCard(
    actions: List<String>,
    onActionsChange: (List<String>) -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlassCard(shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "4×2 插件快捷按键",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "自定义桌面 4×2 小组件底部展示的 4~5 个快捷按键",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showDialog = true }) {
                    Text("配置")
                }
            }

            // Current actions preview row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.take(5).forEach { actionId ->
                    val action = Widget4x2ActionPolicy.findAction(actionId)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.glassInsetSurface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (action != null) {
                                Icon(
                                    painter = painterResource(action.iconRes),
                                    contentDescription = action.label,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        Widget4x2ActionsDialog(
            currentActions = actions,
            onDismiss = { showDialog = false },
            onConfirm = { newActions ->
                onActionsChange(newActions)
                showDialog = false
            }
        )
    }
}

@Composable
private fun QuickSettingsTileCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlassCard(shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "下拉控制中心快捷开关",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "支持在手机下拉通知栏添加快捷开关，单手盲按即控",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.leapauto.app.tiles.TilePromptHelper.TileType.values().forEach { tileType ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                com.leapauto.app.tiles.TilePromptHelper.requestAddTile(context, tileType)
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.glassInsetSurface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(tileType.iconRes),
                                contentDescription = tileType.title,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "+ ${tileType.title}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Widget4x2ActionsDialog(
    currentActions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var editingActions by remember(currentActions) { mutableStateOf(currentActions.toMutableList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "配置 4×2 插件按键",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "当前桌面显示（4~5 个，通过 ‹ › 调整顺序）：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Active items list with reorder and delete
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    editingActions.forEachIndexed { index, actionId ->
                        val action = Widget4x2ActionPolicy.findAction(actionId)
                        if (action != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.glassInsetSurface,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            painter = painterResource(action.iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = action.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val updated = editingActions.toMutableList()
                                                    val temp = updated[index]
                                                    updated[index] = updated[index - 1]
                                                    updated[index - 1] = temp
                                                    editingActions = updated
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("‹", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = {
                                                if (index < editingActions.lastIndex) {
                                                    val updated = editingActions.toMutableList()
                                                    val temp = updated[index]
                                                    updated[index] = updated[index + 1]
                                                    updated[index + 1] = temp
                                                    editingActions = updated
                                                }
                                            },
                                            enabled = index < editingActions.lastIndex,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("›", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = {
                                                if (editingActions.size > 4) {
                                                    editingActions = editingActions.filterIndexed { i, _ -> i != index }.toMutableList()
                                                }
                                            },
                                            enabled = editingActions.size > 4,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("×", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (editingActions.size > 4) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Available to add
                val unselected = Widget4x2ActionPolicy.ALL_AVAILABLE_ACTIONS.filter { it.id !in editingActions }
                if (unselected.isNotEmpty()) {
                    Text(
                        text = "点击可添加功能：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    unselected.chunked(3).forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowActions.forEach { action ->
                                val canAdd = editingActions.size < 5
                                Surface(
                                    shape = RoundedCornerShape(percent = 50),
                                    color = if (canAdd) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = canAdd) {
                                            if (canAdd) {
                                                editingActions = (editingActions + action.id).toMutableList()
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(action.iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = action.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            // Fill empty slots if row has fewer than 3 items
                            repeat(3 - rowActions.count()) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(editingActions) },
                enabled = editingActions.isNotEmpty()
            ) {
                Text("保存并应用")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        editingActions = Widget4x2ActionPolicy.DEFAULT_ACTIONS.toMutableList()
                    }
                ) {
                    Text("恢复默认")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun DrivingBreathingDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "drivingBreathing")
    val haloScale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )
    val haloAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )
    val coreAlpha by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coreAlpha"
    )

    Box(modifier.size(10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer {
                    scaleX = haloScale
                    scaleY = haloScale
                    alpha = haloAlpha
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Box(
            Modifier
                .size(6.dp)
                .graphicsLayer {
                    alpha = coreAlpha
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
internal fun glassCardBorder(): BorderStroke {
    val isDark = LocalAppDarkTheme.current
    val topLeftColor = if (isDark) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.88f)
    val middleColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.45f)
    val bottomRightColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.20f)
    return BorderStroke(
        1.0.dp,
        Brush.linearGradient(
            colors = listOf(topLeftColor, middleColor, bottomRightColor),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    )
}

@Composable
internal fun glassInsetBorder(warning: Boolean = false): BorderStroke {
    if (warning) return BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
    val isDark = LocalAppDarkTheme.current
    val topLeftColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.78f)
    val middleColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.35f)
    val bottomRightColor = if (isDark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.14f)
    return BorderStroke(
        0.8.dp,
        Brush.linearGradient(
            colors = listOf(topLeftColor, middleColor, bottomRightColor),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    )
}

@Composable
internal fun Modifier.frostedGlassCard(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    auraColor: Color? = null,
    auraCenter: Offset = Offset(0.2f, 0.2f),
    auraRadiusRatio: Float = 0.45f,
    topSpecularLine: Boolean = true
): Modifier {
    val isDark = LocalAppDarkTheme.current
    return this
        .clip(shape)
        .drawBehind {
            val w = size.width
            val h = size.height

            // 1. 基底磨砂微晶双对角渐变
            val baseGradient = if (isDark) {
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF222938).copy(alpha = 0.82f),
                        Color(0xFF181E2A).copy(alpha = 0.70f),
                        Color(0xFF131722).copy(alpha = 0.80f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF).copy(alpha = 0.88f),
                        Color(0xFFF6F8FC).copy(alpha = 0.72f),
                        Color(0xFFFFFFFF).copy(alpha = 0.82f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            }
            drawRect(brush = baseGradient)

            // 2. 底层环境极光折射晕（若提供）
            if (auraColor != null && auraColor != Color.Transparent) {
                val cx = w * auraCenter.x
                val cy = h * auraCenter.y
                val radius = maxOf(w, h) * auraRadiusRatio
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            auraColor,
                            auraColor.copy(alpha = auraColor.alpha * 0.4f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = radius
                    ),
                    center = Offset(cx, cy),
                    radius = radius
                )
            }

            // 3. 顶端高光微晶折射横光（0.5dp）
            if (topSpecularLine) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isDark) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.80f),
                            Color.Transparent
                        ),
                        startX = w * 0.12f,
                        endX = w * 0.88f
                    ),
                    start = Offset(w * 0.12f, 0.5f),
                    end = Offset(w * 0.88f, 0.5f),
                    strokeWidth = 1f
                )
            }
        }
    }

@Composable
internal fun solidDialogModifier(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)
): Modifier = Modifier
    .clip(shape)
    .border(
        width = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        shape = shape
    )

@Composable
internal fun frostedGlassDialogModifier(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    auraColor: Color? = null
): Modifier = solidDialogModifier(shape)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleHero(
    vehicleModel: String,
    vehicleAppearance: VehicleAppearance,
    status: VehicleStatus?,
    statusUpdatedAtEpochMs: Long,
    vehicleNickname: String,
    onOpenAccount: () -> Unit,
    vehicleAddress: String? = null,
    liveWeather: com.leapauto.app.weather.LiveWeather? = null,
    onAddressClick: () -> Unit = {},
    vehicleVin: String = "",
    vehicleImageVersion: Int = 0,
    onControl: ((String) -> Unit)? = null,
    onOpenHealthyCharging: () -> Unit = {},
    availableVehicles: List<Vehicle> = emptyList(),
    onSwitchVehicle: (String) -> Unit = {},
    onOpenHealthCheck: () -> Unit = {},
    onUpdateNickname: (String) -> Unit = {},
    onRetryDownload3D: () -> Unit = {},
    onParkingClick: () -> Unit = {},
    locationSnapshot: VehicleLocationSnapshot? = null,
    activeControlCommand: String? = null,
    controlFeedback: ControlFeedback? = null,
    onDismissControlFeedback: () -> Unit = {},
    bluetoothState: BleConnectionState? = null,
    onStraightMove: (com.leapauto.app.bluetooth.BleStraightAction) -> Unit = {},
    onStraightRemoteActiveChange: (Boolean) -> Unit = {},
    onOpenBluetoothKey: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val remoteBitmap = remember(vehicleVin, vehicleImageVersion) {
        if (vehicleVin.isNotBlank()) VehicleImageCache.loadCachedImageBitmap(context, vehicleVin) else null
    }
    val cachedMeta = remember(vehicleVin, vehicleImageVersion) {
        if (vehicleVin.isNotBlank()) VehicleImageCache.getCachedMeta(context, vehicleVin) else null
    }
    val hasCustomImage = remember(vehicleVin, vehicleImageVersion) {
        if (vehicleVin.isNotBlank()) VehicleImageCache.hasCustomImage(context, vehicleVin) else false
    }
    val h5Key = cachedMeta?.h5Key
    val srcKey = cachedMeta?.srcKey
    var is3DReady by remember(h5Key, srcKey, vehicleImageVersion) {
        mutableStateOf(h5Key != null && CarModel3DManager.isModelReady(context, h5Key, srcKey))
    }
    var is3DRendered by remember(h5Key, srcKey, vehicleImageVersion) { mutableStateOf(false) }
    var is3DLoadFailed by remember(h5Key, srcKey, vehicleImageVersion) { mutableStateOf(false) }
    var is3DTimedOut by remember(h5Key, srcKey, vehicleImageVersion) { mutableStateOf(false) }
    var prefer2DModel by remember(vehicleVin, vehicleImageVersion) { mutableStateOf(false) }
    val gear = status?.gearStatus?.trim()?.uppercase()
    val isDrivingGear = gear in setOf("D", "D挡", "DRIVE", "前进", "3", "R", "R挡", "REVERSE", "倒车", "1")
    val speedValue = status?.speed?.replace("km/h", "", ignoreCase = true)?.trim()?.toFloatOrNull() ?: 0f
    val isActuallyDriving = if (isDrivingGear && speedValue <= 0f) false else (isDrivingGear || status?.isDriving == true || speedValue > 0f)
    val show3D = !hasCustomImage && !prefer2DModel && is3DReady && h5Key != null && !is3DLoadFailed && !is3DTimedOut
    val modelAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (is3DRendered) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 350),
        label = "3d_fade"
    )

    // 15 秒超时倒计时：若 15 秒内未渲染完成，自动提示超时并提供重试与使用 2D 车图选项
    androidx.compose.runtime.LaunchedEffect(h5Key, srcKey, vehicleImageVersion, is3DLoadFailed, prefer2DModel) {
        if (h5Key != null && !hasCustomImage && !prefer2DModel && !is3DRendered) {
            is3DTimedOut = false
            val completed = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                while (!is3DRendered && !is3DLoadFailed) {
                    kotlinx.coroutines.delay(300)
                    if (!is3DReady && CarModel3DManager.isModelReady(context, h5Key, srcKey)) {
                        is3DReady = true
                    }
                }
                true
            }
            if (completed == null && !is3DRendered) {
                is3DTimedOut = true
            }
        }
    }
    val heroColor = MaterialTheme.glassSurface
    val nickname = vehicleNickname.ifBlank { formatVehicleModel(vehicleModel) }
    val normalizedSoc = VehicleHomeStatus.resolvedSoc(status?.preciseSoc, status?.soc)
    val rangeColor = when (VehicleHomeStatus.socBand(normalizedSoc)) {
        VehicleHomeStatus.SocBand.NORMAL -> MaterialTheme.statusGood
        VehicleHomeStatus.SocBand.WARNING -> MaterialTheme.statusWarn
        VehicleHomeStatus.SocBand.CRITICAL -> MaterialTheme.colorScheme.error
    }

    val isRangeExtender = status?.rangeExtender == true
    val rangeModeLabel = when (status?.rangeMode) {
        "0" -> "标准续航"
        "1" -> "动态续航"
        else -> null
    }
    val mileageLabel = status?.mileage ?: "--"
    val mileageHasUnit = mileageLabel.endsWith("km", ignoreCase = true)
    var showVehicleSelectorDialog by remember { mutableStateOf(false) }
    var showModifyNicknameDialog by remember { mutableStateOf(false) }

    val isDark = LocalAppDarkTheme.current
    val auraColor = when (VehicleHomeStatus.socBand(normalizedSoc)) {
        VehicleHomeStatus.SocBand.NORMAL -> if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        VehicleHomeStatus.SocBand.WARNING -> if (isDark) MaterialTheme.statusWarn.copy(alpha = 0.20f) else MaterialTheme.statusWarn.copy(alpha = 0.12f)
        VehicleHomeStatus.SocBand.CRITICAL -> if (isDark) MaterialTheme.colorScheme.error.copy(alpha = 0.20f) else MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
    }

    val pageBg = MaterialTheme.colorScheme.background
    val heroCardShape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(heroCardShape),
        shape = heroCardShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth().clip(heroCardShape)) {
            if (!show3D) {
                // 真实毛玻璃折射层（全景微晶渐变基底 + 能量环境极光 + 展台漫反射）
                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height

                    // 1. 基底磨砂微晶渐变（深浅色自适应半透）
                    val baseGradient = if (isDark) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF222938).copy(alpha = 0.82f),
                                Color(0xFF181E2A).copy(alpha = 0.70f),
                                Color(0xFF131722).copy(alpha = 0.80f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF).copy(alpha = 0.88f),
                                Color(0xFFF6F8FC).copy(alpha = 0.72f),
                                Color(0xFFFFFFFF).copy(alpha = 0.82f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        )
                    }
                    drawRect(brush = baseGradient)

                    // 2. 左上方能量环境极光晕（经毛玻璃折射后泛起的深层光晕）
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                auraColor,
                                auraColor.copy(alpha = auraColor.alpha * 0.4f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.18f, h * 0.22f),
                            radius = w * 0.45f
                        ),
                        center = Offset(w * 0.18f, h * 0.22f),
                        radius = w * 0.45f
                    )

                    // 3. 车模下方柔光漫反射（提升立体浮空展台感）
                    val stageLightColor = if (isDark) Color(0xFF384358).copy(alpha = 0.18f) else Color(0xFFFFFFFF).copy(alpha = 0.45f)
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                stageLightColor,
                                Color.Transparent
                            ),
                            center = Offset(w * 0.50f, h * 0.78f),
                            radius = w * 0.50f
                        ),
                        topLeft = Offset(0f, h * 0.55f),
                        size = androidx.compose.ui.geometry.Size(w, h * 0.45f)
                    )
                }

                // 2D 展台底部地平线地雾消融层（柔和托起悬浮快捷按键）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.25f to pageBg.copy(alpha = if (isDark) 0.15f else 0.10f),
                                0.55f to pageBg.copy(alpha = if (isDark) 0.45f else 0.35f),
                                0.80f to pageBg.copy(alpha = if (isDark) 0.75f else 0.65f),
                                1.0f to pageBg.copy(alpha = if (isDark) 0.92f else 0.88f)
                            )
                        )
                )
            } else {
                // 1. 3D 原厂 4K 全景天幕完全铺满整张首卡视口（浑然天成无任何画中画黑边断层）
                val isDarkTheme = LocalAppDarkTheme.current
                CarModel3DView(
                    h5Key = h5Key!!,
                    modelParam = cachedMeta?.modelParam,
                    status = status,
                    vin = vehicleVin,
                    isCruising = isActuallyDriving,
                    isDark = isDarkTheme,
                    modifier = Modifier
                        .matchParentSize()
                        .layout { measurable, constraints ->
                            val extraPx = 125.dp.roundToPx()
                            val offsetPx = 74.dp.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minHeight = (constraints.maxHeight + extraPx).coerceAtLeast(0),
                                    maxHeight = (constraints.maxHeight + extraPx).coerceAtLeast(0)
                                )
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.placeRelative(0, -offsetPx)
                            }
                        }
                        .clip(heroCardShape)
                        .graphicsLayer { alpha = modelAlpha },
                    onReady = {
                        is3DRendered = true
                        is3DLoadFailed = false
                    },
                    onError = {
                        is3DRendered = false
                        is3DLoadFailed = true
                    },
                    onCarClick = null
                )

                // 2. 顶部微晶 HUD 渐变保护层（确保天幕上方的数据文字字字清晰锐利）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    pageBg.copy(alpha = if (isDark) 0.65f else 0.55f),
                                    pageBg.copy(alpha = if (isDark) 0.22f else 0.16f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 3. 底部地平线地雾消融层（从透明自然过渡到底色，杜绝生硬地表切线，柔和托起悬浮快捷按键）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.25f to pageBg.copy(alpha = if (isDark) 0.15f else 0.10f),
                                0.55f to pageBg.copy(alpha = if (isDark) 0.45f else 0.35f),
                                0.80f to pageBg.copy(alpha = if (isDark) 0.75f else 0.65f),
                                1.0f to pageBg.copy(alpha = if (isDark) 0.92f else 0.88f)
                            )
                        )
                )
            }

            Column(
                modifier = Modifier.padding(top = 10.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {

            // ====== 1. 顶部区域：左侧昵称+更新时间+续航电量(整体紧密靠拢)，右侧设置按钮+位置信息 ======
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp)
                    .zIndex(2f),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧列：座驾名称 + 状态更新时间 + 公里数 + 进度条 (紧密纵向堆叠)
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showModifyNicknameDialog = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                nickname,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (availableVehicles.size > 1) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showVehicleSelectorDialog = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                    contentDescription = "切换车辆",
                                    modifier = Modifier
                                        .size(13.dp)
                                        .rotate(90f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (status?.sentryMode == true) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.90f)
                            ) {
                                Text(
                                    "哨兵已开",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 状态更新时间与实况气象文字 (显示具体日期/今天，加点连接气象)
                    val updatedBaseText = VehicleHomeStatus.updatedLabel(statusUpdatedAtEpochMs)
                    val weatherSummary = liveWeather?.summaryText?.takeIf { it.isNotBlank() }
                    val statusTextWithWeather = if (weatherSummary != null) {
                        "$updatedBaseText · $weatherSummary"
                    } else {
                        updatedBaseText
                    }

                    Text(
                        text = statusTextWithWeather,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
                        maxLines = 1
                    )

                    // 方案 A 智能座舱极光双色微晶体系：
                    // 1. 里程大字统一为纯净微晶高光白 (深色) / 深邃科技黑曜灰 (浅色)
                    val mileageDisplayColor = MaterialTheme.colorScheme.onSurface
                    val unitDisplayColor = MaterialTheme.colorScheme.onSurfaceVariant

                    // 2. 纯电与增程纯电百分比：极光薄荷青 (深色 #00E676 / 浅色 #00B42A) / 警示色
                    val auroraMintGreen = if (isDark) Color(0xFF00E676) else Color(0xFF00B42A)
                    val electricSocColor = when (VehicleHomeStatus.socBand(normalizedSoc)) {
                        VehicleHomeStatus.SocBand.NORMAL -> auroraMintGreen
                        VehicleHomeStatus.SocBand.WARNING -> MaterialTheme.statusWarn
                        VehicleHomeStatus.SocBand.CRITICAL -> MaterialTheme.colorScheme.error
                    }

                    // 3. 纯电微晶流光渐变（零跑蓝 ➔ 极光能量绿）
                    val electricGradientColors = when (VehicleHomeStatus.socBand(normalizedSoc)) {
                        VehicleHomeStatus.SocBand.NORMAL -> if (isDark) {
                            listOf(Color(0xFF0066FF), Color(0xFF00E676))
                        } else {
                            listOf(Color(0xFF0052D9), Color(0xFF00B42A))
                        }
                        VehicleHomeStatus.SocBand.WARNING -> listOf(Color(0xFFFF9800), Color(0xFFFFB74D))
                        VehicleHomeStatus.SocBand.CRITICAL -> listOf(Color(0xFFE53935), Color(0xFFFF5252))
                    }

                    // 4. 增程燃油：科技暖金双色微晶体系
                    val fuelSocValue = status?.fuelSoc?.trim()?.removeSuffix("%")?.toIntOrNull() ?: 100
                    val fuelAmberGold = if (isDark) Color(0xFFFFB300) else Color(0xFFD97706)
                    val fuelColor = when {
                        fuelSocValue <= 10 -> MaterialTheme.colorScheme.error
                        fuelSocValue <= 20 -> MaterialTheme.statusWarn
                        else -> fuelAmberGold
                    }
                    val fuelGradientColors = when {
                        fuelSocValue <= 10 -> listOf(Color(0xFFE53935), Color(0xFFFF5252))
                        fuelSocValue <= 20 -> listOf(Color(0xFFFF5722), Color(0xFFFF9800))
                        else -> if (isDark) listOf(Color(0xFFFF8F00), Color(0xFFFFCA28))
                                else listOf(Color(0xFFD97706), Color(0xFFF59E0B))
                    }

                    Column(
                        modifier = Modifier
                            .offset(y = (-4).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenHealthyCharging),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        val pureElectricSocLabel = if (!isRangeExtender) {
                            VehicleHomeStatus.resolvedSocLabel(status?.preciseSoc, status?.soc)
                        } else null

                        // 公里数大字 + 紧随其后的 km 单位 + 纯电模式下融合电量百分比
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (mileageHasUnit) mileageLabel.dropLast(2) else mileageLabel,
                                fontSize = 26.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = mileageDisplayColor,
                                maxLines = 1
                            )
                            Text(
                                text = "km",
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = unitDisplayColor,
                                modifier = Modifier.padding(bottom = 1.5.dp)
                            )
                            if (pureElectricSocLabel != null) {
                                Text(
                                    text = "· $pureElectricSocLabel",
                                    fontSize = 13.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = electricSocColor,
                                    modifier = Modifier.padding(bottom = 1.5.dp)
                                )
                            }
                        }

                        // 下方进度条与电量百分比 / 增程双胶囊 (向上贴紧大数字至 3~4dp)
                        if (isRangeExtender) {
                            val elecMiles = status?.electricMileage?.trim()?.takeIf { it.isNotEmpty() } ?: "--"
                            val elecSoc = VehicleStatusMapper.displayPreciseSoc(
                                VehicleHomeStatus.resolvedSoc(status?.preciseSoc, status?.soc)
                            ) ?: "--"
                            val fuelMiles = status?.fuelMileage?.trim()?.takeIf { it.isNotEmpty() } ?: "--"
                            val fSoc = VehicleStatusMapper.displayPreciseSoc(status?.fuelSoc) ?: "--"

                            Column(
                                modifier = Modifier.padding(top = 1.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                // 1. 能量槽 + 百分比行 (左纯电·右燃油，紧凑间距 5dp)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 纯电能量槽（44dp）+ 电量百分比
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        EnergyCapsuleProgressBar(
                                            progress = chargeProgress(normalizedSoc),
                                            color = electricSocColor,
                                            gradientColors = electricGradientColors,
                                            isCharging = status?.chargeState == 1,
                                            modifier = Modifier.width(44.dp).height(4.5.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = elecSoc,
                                            fontSize = 10.sp,
                                            lineHeight = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = electricSocColor.copy(alpha = 0.90f),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    // 燃油能量槽（44dp）+ 油量百分比
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        EnergyCapsuleProgressBar(
                                            progress = chargeProgress(status?.fuelSoc),
                                            color = fuelColor,
                                            gradientColors = fuelGradientColors,
                                            isCharging = false,
                                            modifier = Modifier.width(44.dp).height(4.5.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = fSoc,
                                            fontSize = 10.sp,
                                            lineHeight = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = fuelColor.copy(alpha = 0.90f),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                // 2. 纯电与燃油里程数据行 (严格对应上方左右栏，紧凑间距 5dp 对齐)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 纯电数据 (左侧对应纯电条，宽度与上方纯电组一致)
                                    Row(
                                        modifier = Modifier.widthIn(min = 76.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_hybrid_electric),
                                            contentDescription = null,
                                            modifier = Modifier.size(11.dp),
                                            tint = electricSocColor
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = "$elecMiles",
                                            fontSize = 11.sp,
                                            lineHeight = 13.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = electricSocColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    // 燃油数据 (右侧对应燃油条)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_hybrid_fuel),
                                            contentDescription = null,
                                            modifier = Modifier.size(11.dp),
                                            tint = fuelColor
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = "$fuelMiles",
                                            fontSize = 11.sp,
                                            lineHeight = 13.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = fuelColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                ChargingCenterPill(
                                    isCharging = status?.chargeState == 1,
                                    chargeRemainTime = status?.chargeRemainTime,
                                    onClick = onOpenHealthyCharging
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                EnergyCapsuleProgressBar(
                                    progress = chargeProgress(normalizedSoc),
                                    color = electricSocColor,
                                    gradientColors = electricGradientColors,
                                    isCharging = status?.chargeState == 1,
                                    modifier = Modifier.width(110.dp).height(4.5.dp)
                                )
                                ChargingCenterPill(
                                    isCharging = status?.chargeState == 1,
                                    chargeRemainTime = status?.chargeRemainTime,
                                    onClick = onOpenHealthyCharging
                                )
                            }
                        }
                    }
                }

                // 右侧列：蓝牙状态按钮 + 设置按钮 + 位置信息
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (onOpenBluetoothKey != null) {
                            HeroBluetoothStatusButton(
                                phase = bluetoothState?.phase ?: BleConnectionPhase.IDLE,
                                onClick = onOpenBluetoothKey
                            )
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("设置") } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                onClick = onOpenAccount,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_gear),
                                    contentDescription = "设置",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    val detailedDrivingState = VehicleHomeStatus.resolveDetailedDrivingState(
                        gearStatus = status?.gearStatus,
                        speed = status?.speed,
                        isDriving = status?.isDriving,
                        isShutDown = status?.isShutDown == true
                    )
                    val isMoving = detailedDrivingState?.isMoving == true
                    val stateLabel = detailedDrivingState?.label ?: "已驻车"
                    val address = vehicleAddress?.takeIf { it.isNotBlank() }
                    val displayAddress = if (address != null && address.length > 10) "${address.take(10)}..." else address

                    // 1. 位置信息胶囊 (在上面)
                    if (displayAddress != null) {
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .clickable(onClick = onParkingClick),
                            shape = RoundedCornerShape(percent = 50),
                            color = Color.White.copy(alpha = if (isDark) 0.08f else 0.12f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = null,
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_location_pin),
                                    contentDescription = null,
                                    modifier = Modifier.size(9.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = displayAddress,
                                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 11.sp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 2. 驻车/行驶状态胶囊 (在下面)
                    Spacer(Modifier.height(2.5.dp))
                    val pillBgColor = if (isMoving) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        Color.White.copy(alpha = if (isDark) 0.08f else 0.12f)
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable(onClick = onParkingClick),
                        shape = RoundedCornerShape(percent = 50),
                        color = pillBgColor,
                        contentColor = if (isMoving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        border = null,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (isMoving) {
                                DrivingBreathingDot()
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(MaterialTheme.statusGood, CircleShape)
                                )
                            }
                            Text(
                                text = stateLabel,
                                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 11.sp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // ====== 2. 底部展台区域（若开启 3D 则留出开阔舞台，2D 或超时则展示静态图） ======
            if (!show3D && remoteBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val imageModifier = if (hasCustomImage) {
                        // 车主个性化自定义图（如战舰/跑车等特殊画幅）：释放横向开阔度，垂直匀称居中，杜绝底部大片空白
                        Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .padding(horizontal = 14.dp)
                            .offset(y = (-4).dp)
                            .clickable(onClick = onOpenHealthCheck)
                    } else {
                        // 官方 2D 车图：精确对齐官方 3D 车模尺寸(260dp)与重心高度(-18dp)
                        Modifier
                            .width(260.dp)
                            .height(130.dp)
                            .offset(y = (-18).dp)
                            .clickable(onClick = onOpenHealthCheck)
                    }
                    Image(
                        bitmap = remoteBitmap,
                        contentDescription = "车身展示主图",
                        contentScale = ContentScale.Fit,
                        modifier = imageModifier
                    )
                    // 车模正上方水平居中极简 HUD 提示胶囊
                    if (controlFeedback != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
                                .zIndex(10f)
                        ) {
                            HeroControlHudPill(controlFeedback, onDismissControlFeedback)
                        }
                    }
                    // 若当前处于 2D 视图且支持 3D，提供随时轻触切回 3D 的快捷入口
                    if ((prefer2DModel || is3DTimedOut || is3DLoadFailed) && h5Key != null && !hasCustomImage) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    prefer2DModel = false
                                    is3DTimedOut = false
                                    is3DLoadFailed = false
                                    onRetryDownload3D()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0xFF1E2330).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.88f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_phosphor_arrow_clockwise),
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "切回3D",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                // 3D 展台区域：包含高度支撑与加载中提示
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (remoteBitmap != null && !is3DRendered && !hasCustomImage && !prefer2DModel) {
                        Image(
                            bitmap = remoteBitmap,
                            contentDescription = "车身展示底图",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(260.dp)
                                .height(130.dp)
                                .offset(y = (-18).dp)
                                .graphicsLayer { alpha = 0.60f }
                        )
                    }
                    // 车模正上方水平居中极简 HUD 提示胶囊
                    if (controlFeedback != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
                                .zIndex(10f)
                        ) {
                            HeroControlHudPill(controlFeedback, onDismissControlFeedback)
                        }
                    }
                    // 当 3D 尚未渲染完成时展示轻量加载提示
                    if (!is3DRendered && h5Key != null && !hasCustomImage && !prefer2DModel && !is3DLoadFailed && !is3DTimedOut) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) Color(0xFF131722).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.75f),
                            border = BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)),
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "3D车模加载中...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ====== 底部自然悬浮的快捷操作栏 (参考原厂水面倒影悬浮质感) ======
            if (onControl != null) {
                QuickVehicleActions(
                    vehicleVin = vehicleVin,
                    vehicleModel = vehicleModel,
                    status = status,
                    locationSnapshot = locationSnapshot,
                    onControl = onControl,
                    activeControlCommand = activeControlCommand,
                    embedded = true,
                    onOpenHealthCheck = onOpenHealthCheck,
                    bluetoothState = bluetoothState ?: BleConnectionState(),
                    onStraightMove = onStraightMove,
                    onStraightRemoteActiveChange = onStraightRemoteActiveChange
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

        if (showVehicleSelectorDialog && availableVehicles.size > 1) {
            VehicleSelectorDialog(
                currentVin = vehicleVin,
                vehicles = availableVehicles,
                onSelectVehicle = onSwitchVehicle,
                onDismiss = { showVehicleSelectorDialog = false }
            )
        }

        if (showModifyNicknameDialog) {
            ModifyNicknameDialog(
                currentNickname = vehicleNickname,
                onDismiss = { showModifyNicknameDialog = false },
                onConfirm = { newName ->
                    onUpdateNickname(newName)
                }
            )
        }
    }
}

@Composable
private fun HeroControlHudPill(
    feedback: ControlFeedback,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    LaunchedEffect(feedback) {
        ControlFeedbackDisplayPolicy.autoDismissDelayMs(feedback.kind)?.let { delayMs ->
            kotlinx.coroutines.delay(delayMs)
            onDismiss()
        }
    }

    val isSuccess = feedback.kind == ControlFeedbackKind.SUCCESS
    val isInProgress = feedback.kind == ControlFeedbackKind.IN_PROGRESS || feedback.kind == ControlFeedbackKind.SUBMITTED
    val isError = feedback.kind == ControlFeedbackKind.ERROR || feedback.kind == ControlFeedbackKind.WARNING

    val pillBg = when {
        isDark -> Color(0xFF181C26).copy(alpha = 0.88f)
        else -> Color.White.copy(alpha = 0.92f)
    }

    val pillBorderColor = when {
        isSuccess -> Color(0xFF00C853).copy(alpha = if (isDark) 0.55f else 0.75f)
        isInProgress -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.45f else 0.65f)
        isError -> Color(0xFFFF3B30).copy(alpha = if (isDark) 0.55f else 0.75f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onDismiss),
        shape = CircleShape,
        color = pillBg,
        border = BorderStroke(0.8.dp, pillBorderColor),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                isInProgress -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                isSuccess -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_check),
                        contentDescription = null,
                        tint = Color(0xFF00C853),
                        modifier = Modifier.size(14.dp)
                    )
                }
                isError -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_warning),
                        contentDescription = null,
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = feedback.message,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = if (isSuccess) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroBluetoothStatusButton(
    phase: BleConnectionPhase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = phase == BleConnectionPhase.READY || phase == BleConnectionPhase.SENDING
    val isConnecting = phase in setOf(
        BleConnectionPhase.CONNECTING,
        BleConnectionPhase.DISCOVERING,
        BleConnectionPhase.SUBSCRIBING,
        BleConnectionPhase.AUTHENTICATING,
        BleConnectionPhase.CONFIGURING,
        BleConnectionPhase.SCANNING
    )

    // 连接中的灰蓝色动态平滑呼吸切换 (1.2 秒往复周期)
    val transition = rememberInfiniteTransition(label = "heroBtPulse")
    val pulseColor by transition.animateColor(
        initialValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroBtColor"
    )

    val iconColor = when {
        isConnecting -> pulseColor
        isConnected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    val containerColor = when {
        isConnected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        isConnecting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    val statusText = when {
        isConnected -> "蓝牙钥匙：已连接"
        isConnecting -> "蓝牙钥匙：正在连接..."
        else -> "蓝牙钥匙：未连接"
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(statusText) } },
        state = rememberTooltipState()
    ) {
        Box(
            modifier = modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bluetooth_key_hero),
                contentDescription = statusText,
                tint = iconColor,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun VehicleSelectorDialog(
    currentVin: String,
    vehicles: List<Vehicle>,
    onSelectVehicle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "切换座驾",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = "共 ${vehicles.size} 台车",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                vehicles.forEach { vehicle ->
                    val isSelected = vehicle.vin == currentVin
                    val isReev = vehicle.carType.contains("增程") ||
                        vehicle.carType.contains("REEV", ignoreCase = true) ||
                        vehicle.powerType == SessionStore.VehiclePowerType.RANGE_EXTENDER
                    val powerLabel = if (isReev) "增程" else "纯电"
                    val displayName = vehicle.nickname.ifBlank { vehicle.carType }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onSelectVehicle(vehicle.vin)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.glassInsetSurface
                        },
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = (if (isReev) MaterialTheme.statusWarn else MaterialTheme.statusGood).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = powerLabel,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isReev) MaterialTheme.statusWarn else MaterialTheme.statusGood
                                        )
                                    }
                                }
                                val maskedVin = if (vehicle.vin.length >= 8) {
                                    "${vehicle.vin.take(6)}...${vehicle.vin.takeLast(4)}"
                                } else vehicle.vin
                                Text(
                                    text = "${vehicle.carType} · VIN: $maskedVin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_phosphor_check),
                                        contentDescription = "当前选中",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun RangeModeLabel(rangeModeLabel: String, modifier: Modifier = Modifier) {
    Text(
        text = rangeModeLabel,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                color = MaterialTheme.glassInsetSurface.copy(alpha = 0.75f),
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 3.5.dp, vertical = 0.5.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

@Composable
private fun HybridRangeBreakdown(
    electricMileage: String?,
    fuelMileage: String?,
    electricSoc: String?,
    fuelSoc: String?,
    electricColor: Color,
    fuelColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-4).dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HybridRangeMetric(
            modifier = Modifier.weight(1f),
            mileage = electricMileage,
            percentage = VehicleStatusMapper.displayPreciseSoc(electricSoc),
            progress = chargeProgress(electricSoc),
            color = electricColor,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_hybrid_electric),
                    contentDescription = "纯电续航",
                    tint = electricColor,
                    modifier = Modifier.size(HybridRangeIconSize)
                )
            }
        )
        HybridRangeMetric(
            modifier = Modifier.weight(1f),
            mileage = fuelMileage,
            percentage = VehicleStatusMapper.displayPreciseSoc(fuelSoc),
            progress = chargeProgress(fuelSoc),
            color = fuelColor,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_hybrid_fuel),
                    contentDescription = "燃油续航",
                    tint = fuelColor,
                    modifier = Modifier.size(HybridRangeIconSize)
                )
            }
        )
    }
}

@Composable
private fun ChargingCenterPill(
    isCharging: Boolean = false,
    chargeRemainTime: String? = null,
    onClick: () -> Unit
) {
    val isDark = LocalAppDarkTheme.current
    val borderColor = if (isCharging) {
        MaterialTheme.statusGood.copy(alpha = 0.50f)
    } else {
        Color.White.copy(alpha = if (isDark) 0.20f else 0.28f)
    }
    val backgroundColor = if (isCharging) {
        MaterialTheme.statusGood.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = if (isDark) 0.08f else 0.12f)
    }

    val formattedTime = chargeRemainTime?.trim()?.takeIf { it.isNotBlank() && it != "--" }?.let { raw ->
        raw.removePrefix("约").removeSuffix("钟")
    }

    val labelText = when {
        isCharging && formattedTime != null -> "剩$formattedTime"
        isCharging -> "充电中"
        else -> "充电中心"
    }

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = backgroundColor,
        border = BorderStroke(0.5.dp, borderColor),
        shadowElevation = 0.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            if (isCharging) {
                Icon(
                    painter = painterResource(R.drawable.ic_widget_charging_bolt),
                    contentDescription = "充电中",
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.statusGood
                )
            }
            Text(
                text = labelText,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCharging) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun EnergyCapsuleProgressBar(
    progress: Float,
    color: Color = MaterialTheme.statusGood,
    gradientColors: List<Color>? = null,
    isCharging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val isDark = LocalAppDarkTheme.current

    // 底槽：告别死黑槽！深色模式下使用 12% 半透微晶白槽，浅色模式下使用 8% 柔和冰雾槽，隐隐透出背景天幕
    val slotBaseColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val trackBorderColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)

    val pulseAlpha = if (isCharging) {
        val transition = rememberInfiniteTransition(label = "chargingPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.60f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        alpha
    } else {
        1f
    }

    val progressBrush = if (!gradientColors.isNullOrEmpty()) {
        Brush.horizontalGradient(
            gradientColors.map { it.copy(alpha = it.alpha * pulseAlpha) }
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                color.copy(alpha = 0.80f * pulseAlpha),
                color.copy(alpha = pulseAlpha)
            )
        )
    }

    Box(
        modifier = modifier
            .height(4.5.dp)
            .clip(CircleShape)
            .background(slotBaseColor)
            .border(0.5.dp, trackBorderColor, CircleShape)
    ) {
        if (clampedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clampedProgress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(progressBrush)
            )
        }
    }
}

@Composable
private fun HybridRangeMetric(
    modifier: Modifier,
    mileage: String?,
    percentage: String?,
    progress: Float,
    color: Color,
    icon: @Composable () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "hybridProgress"
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Text(
                text = compactRangeLabel(mileage),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            percentage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        EnergyCapsuleProgressBar(
            progress = animatedProgress,
            color = color,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun compactRangeLabel(raw: String?): String {
    val value = raw
        ?.trim()
        ?.removeSuffix("km")
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "--" }
        ?: return "--"
    return "${value}km"
}

private fun rangeColorForSoc(
    soc: String?,
    fallback: Color,
    normal: Color,
    warning: Color,
    critical: Color
): Color {
    if (VehicleStatusMapper.displayPreciseSoc(soc) == null) return fallback
    return when (VehicleHomeStatus.socBand(soc)) {
        VehicleHomeStatus.SocBand.NORMAL -> normal
        VehicleHomeStatus.SocBand.WARNING -> warning
        VehicleHomeStatus.SocBand.CRITICAL -> critical
    }
}

@Composable
fun WindowStatusDialog(
    status: VehicleStatus?,
    onControl: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    WindowStatusDialog(
        available = status?.windowStatusAvailable == true,
        openWindows = status?.openWindows.orEmpty(),
        leftFrontPercent = status?.leftFrontWindowPercent,
        rightFrontPercent = status?.rightFrontWindowPercent,
        leftRearPercent = status?.leftRearWindowPercent,
        rightRearPercent = status?.rightRearWindowPercent,
        roofOpeningPercent = status?.roofOpeningPercent,
        onCloseAllWindows = onControl?.let { ctrl -> { ctrl("windowClose") } },
        onVentWindows = onControl?.let { ctrl -> { ctrl("windowVent") } },
        onDismiss = onDismiss
    )
}

@Composable
fun WindowStatusDialog(
    available: Boolean,
    openWindows: List<String>,
    onDismiss: () -> Unit
) {
    WindowStatusDialog(
        available = available,
        openWindows = openWindows,
        leftFrontPercent = null,
        rightFrontPercent = null,
        leftRearPercent = null,
        rightRearPercent = null,
        roofOpeningPercent = null,
        onCloseAllWindows = null,
        onVentWindows = null,
        onDismiss = onDismiss
    )
}

@Composable
fun WindowStatusDialog(
    available: Boolean,
    openWindows: List<String>,
    leftFrontPercent: Int? = null,
    rightFrontPercent: Int? = null,
    leftRearPercent: Int? = null,
    rightRearPercent: Int? = null,
    roofOpeningPercent: Int? = null,
    onCloseAllWindows: (() -> Unit)? = null,
    onVentWindows: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val anyOpen = VehicleHomeStatus.hasAnyWindowOpen(
        available = available,
        openWindows = openWindows,
        percents = listOf(leftFrontPercent, rightFrontPercent, leftRearPercent, rightRearPercent)
    )

    val lfItem = VehicleHomeStatus.resolveWindowItem("lf", "左前", "主驾", openWindows, leftFrontPercent)
    val rfItem = VehicleHomeStatus.resolveWindowItem("rf", "右前", "副驾", openWindows, rightFrontPercent)
    val lrItem = VehicleHomeStatus.resolveWindowItem("lr", "左后", "后左", openWindows, leftRearPercent)
    val rrItem = VehicleHomeStatus.resolveWindowItem("rr", "右后", "后右", openWindows, rightRearPercent)

    val openCount = listOf(lfItem, rfItem, lrItem, rrItem).count { it.isOpen }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(shape = RoundedCornerShape(24.dp)),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (anyOpen) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                if (anyOpen) R.drawable.ic_window_half else R.drawable.ic_phosphor_wind
                            ),
                            contentDescription = null,
                            tint = if (anyOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "车窗状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (available) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (anyOpen) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else MaterialTheme.statusGood.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (anyOpen) "$openCount 扇未关" else "全部已关",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (anyOpen) MaterialTheme.colorScheme.error else MaterialTheme.statusGood,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!available) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "当前车型或本次车况未返回可用的车窗信号。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // 2x2 四车窗网格卡片
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 第一行：前排（左前·主驾 / 右前·副驾）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WindowGridCell(
                                item = lfItem,
                                modifier = Modifier.weight(1f)
                            )
                            WindowGridCell(
                                item = rfItem,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 第二行：后排（左后 / 右后）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WindowGridCell(
                                item = lrItem,
                                modifier = Modifier.weight(1f)
                            )
                            WindowGridCell(
                                item = rrItem,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 全景天窗状态条（如果车型返回了天窗信号）
                    if (roofOpeningPercent != null) {
                        val roofOpen = roofOpeningPercent > 0
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                0.8.dp,
                                if (roofOpen) MaterialTheme.colorScheme.error.copy(alpha = 0.40f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_phosphor_sun),
                                        contentDescription = null,
                                        tint = if (roofOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "全景天窗",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = VehicleHomeStatus.roofOpeningSummary(roofOpeningPercent),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (roofOpen) MaterialTheme.colorScheme.error else MaterialTheme.statusGood
                                )
                            }
                        }
                    }

                    // 底部安全说明
                    Text(
                        text = if (anyOpen) "提示：雨天或离车驻车时请确认车窗关闭，以防进水与财产损失。"
                        else "全部车窗已完全关闭，车内密闭安全。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )
                }
            }
        },
        dismissButton = {
            if (anyOpen && onCloseAllWindows != null) {
                TextButton(onClick = onDismiss) {
                    Text(
                        "暂不关闭",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!anyOpen && onVentWindows != null) {
                OutlinedButton(
                    onClick = {
                        onVentWindows.invoke()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_window_vent),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "微开通风",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            if (anyOpen && onCloseAllWindows != null) {
                Button(
                    onClick = {
                        onCloseAllWindows.invoke()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_wind),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "一键全关车窗",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("知道了", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}

@Composable
private fun WindowGridCell(
    item: VehicleHomeStatus.WindowItemPresentation,
    modifier: Modifier = Modifier
) {
    val borderColor = if (item.isOpen) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    val bgColor = if (item.isOpen) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.positionLabel} · ${item.roleLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (item.isOpen) MaterialTheme.colorScheme.error
                            else MaterialTheme.statusGood
                        )
                )
            }
            Text(
                text = item.statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (item.isOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (item.isOpen) {
                val progressFraction = ((item.percent ?: 100).coerceIn(5, 100)) / 100f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}

private fun formatVehicleModel(carType: String): String {
    val model = carType.trim()
    return when {
        model.isBlank() -> "零跑"
        model.startsWith("零跑") -> model
        else -> "零跑 $model"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickVehicleActions(
    vehicleVin: String,
    vehicleModel: String,
    status: VehicleStatus?,
    locationSnapshot: VehicleLocationSnapshot?,
    onControl: (String) -> Unit,
    activeControlCommand: String? = null,
    seamless: Boolean = false,
    embedded: Boolean = false,
    onOpenHealthCheck: () -> Unit = {},
    bluetoothState: BleConnectionState = BleConnectionState(),
    onStraightMove: (com.leapauto.app.bluetooth.BleStraightAction) -> Unit = {},
    onStraightRemoteActiveChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val sessionStore = remember(context) { SessionStore(context) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var windowMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var sunshadeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showStraightRemoteSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showStraightRemoteSheet) {
        onStraightRemoteActiveChange(showStraightRemoteSheet)
    }
    var windowButtonTopLeft by remember { mutableStateOf(Offset.Zero) }
    var windowButtonWidth by remember { mutableStateOf(0f) }
    var windowButtonHeight by remember { mutableStateOf(0f) }
    var sunshadeButtonTopLeft by remember { mutableStateOf(Offset.Zero) }
    var sunshadeButtonWidth by remember { mutableStateOf(0f) }
    var sunshadeButtonHeight by remember { mutableStateOf(0f) }
    val trunkState = status?.trunkState ?: TrunkState.UNKNOWN
    val isDrivingGear = VehicleDrivingSafetyPolicy.isDrivingGear(status?.gearStatus)
    val commandsPerPage = 5
    val availableCommands = remember(vehicleVin, vehicleModel, status?.sentryMode) {
        val supportsWindowGroup = !vehicleModel.contains("T03", ignoreCase = true)
        val supportsFrunk = VehicleQuickControlCapabilities.supportsFrunk(vehicleModel)
        val windowGroup = if (supportsWindowGroup) {
            listOf(Cmd("windowGroup", "车窗", R.drawable.ic_phosphor_wind))
        } else {
            emptyList()
        }
        val frunkCommands = if (supportsFrunk) {
            listOf(
                Cmd("frunkOpen", "开前备箱", R.drawable.ic_phosphor_trunk_open),
                Cmd("frunkClose", "关前备箱", R.drawable.ic_phosphor_trunk_open)
            )
        } else {
            emptyList()
        }
        val extraCommands = allCommands.filterNot { it.name == "windowOpen" || it.name == "windowClose" }
        val straightRemoteCmd = if (com.leapauto.app.bluetooth.BleStraightProtocol.isAuthorized(vehicleVin)) {
            listOf(Cmd("straightRemote", "直进直出", R.drawable.ic_straight_remote))
        } else {
            emptyList()
        }
        listOf(
            Cmd("unlock", "解锁", R.drawable.ic_phosphor_lock_open),
            Cmd("lock", "上锁", R.drawable.ic_phosphor_lock),
            *windowGroup.toTypedArray(),
            Cmd("trunk", "开后备箱", R.drawable.ic_phosphor_trunk_open),
            *frunkCommands.toTypedArray(),
            *extraCommands.toTypedArray(),
            *straightRemoteCmd.toTypedArray(),
            Cmd("sentry", "哨兵模式", R.drawable.ic_sentry),
            Cmd("diagnostics", "诊断", R.drawable.ic_quick_diagnostics)
        )
    }
    var savedOrder by remember(vehicleVin, availableCommands) { mutableStateOf<List<String>?>(null) }
    var editingOrder by remember(vehicleVin, availableCommands) {
        mutableStateOf(availableCommands.map { it.name })
    }
    LaunchedEffect(vehicleVin, availableCommands) {
        val storedOrder = sessionStore.loadQuickCommandOrder(vehicleVin)
        val migratedOrder = QuickCommandOrderPolicy.migrateSunshadeGroup(storedOrder)
        savedOrder = migratedOrder
        editingOrder = QuickCommandOrderPolicy.resolve(migratedOrder, availableCommands.map { it.name })
        if (storedOrder != null && storedOrder != migratedOrder) {
            sessionStore.saveQuickCommandOrder(vehicleVin, requireNotNull(migratedOrder))
        }
    }
    val orderedCommands = QuickCommandOrderPolicy.resolve(
        saved = savedOrder,
        available = availableCommands.map { it.name }
    ).mapNotNull { id -> availableCommands.firstOrNull { it.name == id } }
    // 常驻末尾的【自定义 ✎】编辑按钮，永远排在快捷操作最后一位，不参与排序
    val displayCommands = remember(orderedCommands) {
        orderedCommands + Cmd(
            name = "quickActionCustomize",
            label = "自定义",
            iconRes = R.drawable.ic_edit
        )
    }
    val lockPresentation = VehicleHomeStatus.lockButtonPresentation(status?.locked)
    val windowOpen = status?.windowStatusAvailable == true && status.openWindows.isNotEmpty()
    val trunkOpen = trunkState == TrunkState.OPEN
    val pageCount = (displayCommands.size + commandsPerPage - 1) / commandsPerPage
    val pagerState = rememberPagerState(pageCount = { pageCount })

    fun openEditor() {
        editingOrder = orderedCommands.map { it.name }
        editing = true
    }

    @Composable
    fun WindowBadge(modifier: Modifier = Modifier) {
        Surface(
            modifier = modifier
                .offset(x = 4.dp, y = (-4).dp)
                .size(16.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error,
            content = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    @Composable
    fun QuickMenuAction(label: String, iconRes: Int, onClick: () -> Unit) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val dockShape = RoundedCornerShape(16.dp)

    val handleQuickActionClick = { action: () -> Unit ->
        if (isDrivingGear) {
            showUpperToast(context, VehicleDrivingSafetyPolicy.DRIVING_OPERATION_PROHIBITED_HINT)
        } else {
            action()
        }
    }

    val actionsContent = @Composable {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (embedded) 6.dp else 8.dp, vertical = if (pageCount > 1) 2.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(70.dp),
                beyondViewportPageCount = 1
            ) { page ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val pageCommands = displayCommands.drop(page * commandsPerPage).take(commandsPerPage)
                    pageCommands.forEach { command ->
                        val cmdInProgress = QuickCommandExecutionPolicy.isCommandInProgress(command.name, activeControlCommand)
                        val label = if (command.name == "trunk") {
                            when (trunkState) {
                                TrunkState.CLOSED -> "开后备箱"
                                TrunkState.OPEN -> "关后备箱"
                                TrunkState.UNKNOWN -> "后备箱状态未知"
                            }
                        } else command.label
                        if (command.name == "quickActionCustomize") {
                            // 独立常驻末尾的【自定义 ✎】编辑按钮（不参与排序）
                            QuickVehicleButton(
                                label = "自定义",
                                iconRes = R.drawable.ic_edit,
                                inProgress = false,
                                isSensitive = false,
                                onClick = { handleQuickActionClick { if (!editing) openEditor() } },
                                modifier = Modifier.weight(1f)
                            )
                        } else if (command.name == "windowGroup") {
                            val maxWindowPercent = maxOf(
                                status?.leftFrontWindowPercent ?: 0,
                                status?.rightFrontWindowPercent ?: 0,
                                status?.leftRearWindowPercent ?: 0,
                                status?.rightRearWindowPercent ?: 0
                            )
                            val isHalfOpen = windowOpen && maxWindowPercent > 25
                            val isVentOpen = windowOpen && !isHalfOpen
                            val windowIconRes = when {
                                isHalfOpen -> R.drawable.ic_window_half
                                isVentOpen -> R.drawable.ic_window_vent
                                else -> R.drawable.ic_phosphor_wind
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                QuickVehicleButton(
                                    label = label,
                                    iconRes = windowIconRes,
                                    warning = windowOpen,
                                    inProgress = cmdInProgress,
                                    isSensitive = false,
                                    onClick = {
                                        handleQuickActionClick {
                                            if (!editing) {
                                                sunshadeMenuExpanded = false
                                                windowMenuExpanded = !windowMenuExpanded
                                            }
                                        }
                                    },
                                    iconTint = if (windowOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    labelTint = if (windowOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coordinates ->
                                            windowButtonTopLeft = coordinates.positionInWindow()
                                            windowButtonWidth = coordinates.size.width.toFloat()
                                            windowButtonHeight = coordinates.size.height.toFloat()
                                        }
                                )
                                if (windowOpen) {
                                    WindowBadge(modifier = Modifier.align(Alignment.TopEnd))
                                }
                            }
                        } else if (command.name == "sunshadeGroup") {
                            QuickVehicleButton(
                                label = label,
                                iconRes = command.iconRes,
                                inProgress = cmdInProgress,
                                isSensitive = false,
                                onClick = {
                                    handleQuickActionClick {
                                        if (!editing) {
                                            windowMenuExpanded = false
                                            sunshadeMenuExpanded = !sunshadeMenuExpanded
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coordinates ->
                                        sunshadeButtonTopLeft = coordinates.positionInWindow()
                                        sunshadeButtonWidth = coordinates.size.width.toFloat()
                                        sunshadeButtonHeight = coordinates.size.height.toFloat()
                                    }
                            )
                        } else if (command.name == "trunk") {
                            val isSensitiveTrunkOpen = trunkState != TrunkState.OPEN
                            Box(modifier = Modifier.weight(1f)) {
                                QuickVehicleButton(
                                    label = label,
                                    iconRes = command.iconRes,
                                    warning = trunkOpen,
                                    inProgress = cmdInProgress,
                                    isSensitive = isSensitiveTrunkOpen,
                                    onClick = {
                                        handleQuickActionClick {
                                            if (!editing) {
                                                if (trunkOpen) {
                                                    onControl("trunkClose")
                                                } else {
                                                    showUpperToast(context, SensitiveControlPolicy.SENSITIVE_ACTION_HINT)
                                                }
                                            }
                                        }
                                    },
                                    onLongPressConfirm = {
                                        handleQuickActionClick {
                                            if (!editing && !trunkOpen) {
                                                onControl("trunkOpen")
                                            }
                                        }
                                    },
                                    iconTint = if (trunkOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    labelTint = if (trunkOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (trunkOpen) {
                                    WindowBadge(modifier = Modifier.align(Alignment.TopEnd))
                                }
                            }
                        } else {
                            val isWarningCmd = when (command.name) {
                                "unlock" -> status?.locked == false
                                "windowOpen" -> windowOpen
                                else -> false
                            }
                            val isFrunkOpen = command.name == "frunkOpen"
                            QuickVehicleButton(
                                label = label,
                                iconRes = command.iconRes,
                                warning = isWarningCmd,
                                inProgress = cmdInProgress,
                                isSensitive = isFrunkOpen,
                                onClick = {
                                    handleQuickActionClick {
                                        if (!editing) {
                                            if (isFrunkOpen) {
                                                showUpperToast(context, SensitiveControlPolicy.SENSITIVE_ACTION_HINT)
                                            } else {
                                                when (command.name) {
                                                    "diagnostics" -> onOpenHealthCheck()
                                                    "sentry" -> onControl(SentryModeControlPolicy.commandName(status?.sentryMode))
                                                    "straightRemote" -> {
                                                        showStraightRemoteSheet = true
                                                        onControl("straightActivate")
                                                    }
                                                    else -> onControl(command.name)
                                                }
                                            }
                                        }
                                    }
                                },
                                onLongPressConfirm = if (isFrunkOpen) {
                                    { handleQuickActionClick { if (!editing) onControl("frunkOpen") } }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    repeat(commandsPerPage - pageCommands.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (pageCount > 1) {
                val isDark = LocalAppDarkTheme.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pageCount) { page ->
                        val isSelected = pagerState.currentPage == page
                        val dotSize by animateDpAsState(
                            targetValue = if (isSelected) 5.5.dp else 4.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "pageIndicatorDotSize"
                        )
                        val indicatorColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                if (isDark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.20f)
                            },
                            animationSpec = tween(200),
                            label = "pageIndicatorColor"
                        )
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(indicatorColor)
                        )
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (embedded) {
            actionsContent()
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = dockShape,
                        auraColor = primaryColor.copy(alpha = 0.08f),
                        auraCenter = Offset(0.5f, 0.5f)
                    ),
                shape = dockShape,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = glassCardBorder(),
                shadowElevation = 0.dp
            ) {
                actionsContent()
            }
        }

        if (windowMenuExpanded) {
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val menuWidth = popupContentSize.width
                        val menuHeight = popupContentSize.height
                        val buttonCenterX = (windowButtonTopLeft.x + windowButtonWidth / 2).roundToInt()
                        val desiredX = buttonCenterX - menuWidth / 2
                        val clampedX = desiredX.coerceIn(8, windowSize.width - menuWidth - 8)
                        val spaceAbove = windowButtonTopLeft.y.roundToInt()
                        val y = if (menuHeight <= spaceAbove) {
                            spaceAbove - menuHeight - 8
                        } else {
                            spaceAbove + windowButtonHeight.roundToInt() + 8
                        }
                        return IntOffset(clampedX, y)
                    }
                },
                onDismissRequest = { windowMenuExpanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier.widthIn(min = 120.dp, max = 160.dp),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 10.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))
                ) {
                    Column {
                        QuickMenuAction(
                            label = "车窗微开",
                            iconRes = R.drawable.ic_window_vent,
                            onClick = {
                                handleQuickActionClick {
                                    windowMenuExpanded = false
                                    onControl("windowVent")
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        )
                        QuickMenuAction(
                            label = "车窗半开",
                            iconRes = R.drawable.ic_window_half,
                            onClick = {
                                handleQuickActionClick {
                                    windowMenuExpanded = false
                                    onControl("windowOpen")
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        )
                        QuickMenuAction(
                            label = "车窗全关",
                            iconRes = R.drawable.ic_phosphor_wind,
                            onClick = {
                                handleQuickActionClick {
                                    windowMenuExpanded = false
                                    onControl("windowClose")
                                }
                            }
                        )
                    }
                }
            }
        }

        if (sunshadeMenuExpanded) {
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val menuWidth = popupContentSize.width
                        val menuHeight = popupContentSize.height
                        val buttonCenterX = (sunshadeButtonTopLeft.x + sunshadeButtonWidth / 2).roundToInt()
                        val desiredX = buttonCenterX - menuWidth / 2
                        val clampedX = desiredX.coerceIn(8, windowSize.width - menuWidth - 8)
                        val spaceAbove = sunshadeButtonTopLeft.y.roundToInt()
                        val y = if (menuHeight <= spaceAbove) {
                            spaceAbove - menuHeight - 8
                        } else {
                            spaceAbove + sunshadeButtonHeight.roundToInt() + 8
                        }
                        return IntOffset(clampedX, y)
                    }
                },
                onDismissRequest = { sunshadeMenuExpanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier.widthIn(min = 120.dp, max = 160.dp),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 10.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))
                ) {
                    Column {
                        QuickMenuAction(
                            label = "打开遮阳帘",
                            iconRes = R.drawable.ic_phosphor_sun,
                            onClick = {
                                handleQuickActionClick {
                                    sunshadeMenuExpanded = false
                                    onControl("sunshadeOpen")
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        )
                        QuickMenuAction(
                            label = "关闭遮阳帘",
                            iconRes = R.drawable.ic_phosphor_sun,
                            onClick = {
                                handleQuickActionClick {
                                    sunshadeMenuExpanded = false
                                    onControl("sunshadeClose")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = {
                editing = false
            },
            modifier = solidDialogModifier(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(24.dp),
            title = { Text("调整快捷操作") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("长按右侧手柄调整顺序", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DraggableButtonList(
                        items = editingOrder.mapNotNull { commandId ->
                            availableCommands.firstOrNull { it.name == commandId }?.let { command ->
                                QuickButton(command.name, command.label, command.iconRes)
                            }
                        },
                        onReorder = { reordered ->
                            editingOrder = reordered.map { it.id }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp, max = 360.dp)
                            .weight(1f, fill = false)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    editingOrder = availableCommands.map { it.name }
                }) { Text("恢复默认") }
            },
            confirmButton = {
                TextButton(onClick = {
                    sessionStore.saveQuickCommandOrder(vehicleVin, editingOrder)
                    savedOrder = editingOrder
                    editing = false
                }) { Text("完成") }
            }
        )
    }

    if (showStraightRemoteSheet && com.leapauto.app.bluetooth.BleStraightProtocol.isAuthorized(vehicleVin)) {
        StraightRemoteBottomSheet(
            onDismissRequest = {
                onStraightMove(com.leapauto.app.bluetooth.BleStraightAction.STOP)
                onControl("straightDeactivate")
                showStraightRemoteSheet = false
            },
            canControl = bluetoothState.canControl,
            bluetoothPhase = bluetoothState.phase,
            onStartMoving = { action ->
                onStraightMove(action)
            },
            onStopMoving = {
                onStraightMove(com.leapauto.app.bluetooth.BleStraightAction.STOP)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickVehicleButton(
    label: String,
    iconRes: Int,
    warning: Boolean = false,
    inProgress: Boolean = false,
    isSensitive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongPressConfirm: (() -> Unit)? = null,
    iconTint: Color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    labelTint: Color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !inProgress) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "quickBtnScale"
    )
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }
    val holdProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "inProgressPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isDark = LocalAppDarkTheme.current
    val goodColor = MaterialTheme.statusGood
    val isWarning = warning || iconTint == MaterialTheme.colorScheme.error
    val circleBg = when {
        inProgress -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f * pulseAlpha)
        isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        isPressed || isHolding -> MaterialTheme.glassInsetSurface.copy(alpha = 0.92f)
        else -> if (isDark) Color(0xFF262E3D).copy(alpha = 0.68f) else Color.White.copy(alpha = 0.76f)
    }
    val circleBorder = when {
        inProgress -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
        isWarning -> BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
        else -> BorderStroke(
            0.8.dp,
            if (isDark) Color.White.copy(alpha = if (isPressed || isHolding) 0.35f else 0.18f)
            else Color.White.copy(alpha = if (isPressed || isHolding) 0.95f else 0.82f)
        )
    }

    val buttonTouchModifier = if (isSensitive && !inProgress) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var completed = false
                val job = coroutineScope.launch {
                    isHolding = true
                    holdProgress.snapTo(0f)
                    holdProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = SensitiveControlPolicy.LONG_PRESS_HOLD_DURATION_MS.toInt(),
                            easing = LinearEasing
                        )
                    )
                    completed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPressConfirm?.invoke()
                }
                val up = waitForUpOrCancellation()
                job.cancel()
                coroutineScope.launch {
                    holdProgress.snapTo(0f)
                    isHolding = false
                }
                if (up != null && !completed) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            }
        }
    } else {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = !inProgress,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            onLongClick = onLongClick?.let { longClick ->
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    longClick()
                }
            }
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 高敏感操作 1.2 秒长按环形外圈蓄力进度光环
            if (isSensitive && isHolding) {
                val successColor = MaterialTheme.statusGood
                val chargingColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.size(54.dp)) {
                    val strokeW = 2.5.dp.toPx()
                    drawArc(
                        color = if (holdProgress.value >= 1f) successColor else chargingColor,
                        startAngle = -90f,
                        sweepAngle = holdProgress.value * 360f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = if (isHolding) 0.94f else scale
                        scaleY = if (isHolding) 0.94f else scale
                    }
                    .clip(CircleShape)
                    .drawBehind {
                        drawRect(circleBg)
                        if (!isWarning && !inProgress) {
                            val cx = size.width * 0.76f
                            val cy = size.height * 0.24f
                            val radius = size.width * 0.55f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        goodColor.copy(alpha = if (isDark) 0.11f else 0.08f),
                                        goodColor.copy(alpha = if (isDark) 0.035f else 0.025f),
                                        Color.Transparent
                                    ),
                                    center = Offset(cx, cy),
                                    radius = radius
                                ),
                                center = Offset(cx, cy),
                                radius = radius
                            )
                        }
                    }
                    .then(buttonTouchModifier),
                shape = CircleShape,
                color = Color.Transparent,
                border = circleBorder
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (inProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painterResource(iconRes),
                            contentDescription = label,
                            tint = iconTint,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
            color = if (inProgress) MaterialTheme.colorScheme.primary else labelTint,
            fontWeight = if (isWarning || inProgress) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** 将原始系统 Toast 上移至屏幕中上部（快捷操作栏正上方），确保视线清晰可见且不被手掌遮挡。 */
private fun showUpperToast(context: Context, text: String) {
    try {
        val toast = Toast(context.applicationContext)
        val textView = android.widget.TextView(context).apply {
            this.text = text
            setTextColor(android.graphics.Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(42, 22, 42, 22)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#E620242F"))
                cornerRadius = 32f
                setStroke(2, android.graphics.Color.parseColor("#40FFFFFF"))
            }
            background = bg
        }
        @Suppress("DEPRECATION")
        toast.view = textView
        toast.duration = Toast.LENGTH_SHORT
        toast.setGravity(android.view.Gravity.CENTER, 0, -220)
        toast.show()
    } catch (_: Throwable) {
        val fallback = Toast.makeText(context, text, Toast.LENGTH_SHORT)
        fallback.setGravity(android.view.Gravity.CENTER, 0, -220)
        fallback.show()
    }
}

@Composable
private fun HomeTirePressureCard(
    status: VehicleStatus?,
    modifier: Modifier = Modifier,
    onCarClick: () -> Unit = {},
    seamless: Boolean = false
) {
    val tireByPosition = status?.tires.orEmpty().associateBy { it.position }
    val hasTireData = tireByPosition.isNotEmpty()
    val hasWarning = tireByPosition.values.any { it.warning }
    val cardBorder = glassCardBorder()
    val warningColor = MaterialTheme.colorScheme.error
    val goodColor = MaterialTheme.statusGood
    val auraColor = if (hasWarning) warningColor.copy(alpha = 0.14f) else goodColor.copy(alpha = 0.10f)
    Surface(
        modifier = modifier
            .heightIn(min = 120.dp)
            .then(
                if (!seamless) Modifier.frostedGlassCard(
                    shape = RoundedCornerShape(16.dp),
                    auraColor = auraColor,
                    auraCenter = Offset(0.85f, 0.15f)
                ) else Modifier
            ),
        shape = if (!seamless) RoundedCornerShape(16.dp) else androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (!seamless) cardBorder else null,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            // 仅在胎压异常时在右上角显示警示圆点与文本
            if (hasWarning) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.5.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                    )
                    Text(
                        text = "异常",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧两轮：左前 + 左后
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    HomeTireValueDisplay(tireByPosition["左前"], alignEnd = true)
                    HomeTireValueDisplay(tireByPosition["左后"], alignEnd = true)
                }

                Spacer(Modifier.width(6.dp))

                // 中央俯视极简车模（点击直接进入全车体检/健康诊断界面）
                TopDownCarModel(
                    leftFrontWarning = tireByPosition["左前"]?.warning == true,
                    rightFrontWarning = tireByPosition["右前"]?.warning == true,
                    leftRearWarning = tireByPosition["左后"]?.warning == true,
                    rightRearWarning = tireByPosition["右后"]?.warning == true,
                    modifier = Modifier
                        .size(width = 46.dp, height = 94.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .clickable(onClick = onCarClick)
                )

                Spacer(Modifier.width(6.dp))

                // 右侧两轮：右前 + 右后
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    HomeTireValueDisplay(tireByPosition["右前"], alignEnd = false)
                    HomeTireValueDisplay(tireByPosition["右后"], alignEnd = false)
                }
            }
        }
    }
}

@Composable
private fun HomeTireValueDisplay(
    tire: TireStatus?,
    alignEnd: Boolean,
    modifier: Modifier = Modifier
) {
    val warning = tire?.warning == true
    val rawPressure = tire?.pressure?.removeSuffix("kPa")?.trim()
    val displayPressure = if (!rawPressure.isNullOrBlank()) rawPressure else "--"
    val textColor = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val unitColor = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = displayPressure,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (warning) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            color = textColor,
            maxLines = 1
        )
        Text(
            text = "kPa",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            color = unitColor,
            maxLines = 1
        )
    }
}

@Composable
private fun TopDownCarModel(
    leftFrontWarning: Boolean,
    rightFrontWarning: Boolean,
    leftRearWarning: Boolean,
    rightRearWarning: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    val outlineColor = if (isDark) {
        Color(0xFF5E6D82).copy(alpha = 0.65f)
    } else {
        Color(0xFF7D8C9F).copy(alpha = 0.55f)
    }
    val bodyBgColor = if (isDark) {
        Color(0xFF222731).copy(alpha = 0.90f)
    } else {
        Color(0xFFE6EBF2).copy(alpha = 0.95f)
    }
    val glassCanopyColor = if (isDark) {
        Color(0xFF11151C).copy(alpha = 0.96f)
    } else {
        Color(0xFF242A36).copy(alpha = 0.88f)
    }
    val glassBorderColor = if (isDark) {
        Color(0xFF3F4A5A).copy(alpha = 0.60f)
    } else {
        Color(0xFF1E242E).copy(alpha = 0.35f)
    }
    val mirrorColor = if (isDark) {
        Color(0xFF3A4352)
    } else {
        Color(0xFF8F9DAE)
    }
    val wheelNormalColor = if (isDark) {
        Color(0xFF282D36)
    } else {
        Color(0xFF48505E)
    }
    val wheelNormalBorder = if (isDark) {
        Color(0xFF4E5A6C).copy(alpha = 0.65f)
    } else {
        Color(0xFF717E90).copy(alpha = 0.55f)
    }
    val wheelRimColor = if (isDark) {
        Color(0xFF7E8D9F).copy(alpha = 0.60f)
    } else {
        Color(0xFFC4D0DE).copy(alpha = 0.75f)
    }
    val wheelWarningColor = MaterialTheme.colorScheme.error

    // 车灯科技微光
    val headlightColor = if (isDark) Color(0xFF64B5F6) else MaterialTheme.colorScheme.primary
    val taillightColor = Color(0xFFFF3B30)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        val bodyTop = h * 0.05f
        val bodyBottom = h * 0.95f
        val bodyH = bodyBottom - bodyTop

        val frontAxleY = bodyTop + bodyH * 0.20f
        val rearAxleY = bodyTop + bodyH * 0.77f

        val stroke1dp = 1.dp.toPx()
        val stroke05dp = 0.6.dp.toPx()

        // 1. 宽体肌肉轿跑流线车身 (前悬俯冲弧线、前轮拱舒展、腰线自然收束、后轮拱宽体外扩)
        val bodyPath = Path().apply {
            moveTo(cx, bodyTop)
            // 右侧流线：车头微弧 -> 前轮拱 -> 收腰 -> 宽体后轮包 -> 尾翼
            cubicTo(
                cx + w * 0.20f, bodyTop,
                cx + w * 0.34f, bodyTop + bodyH * 0.05f,
                cx + w * 0.35f, bodyTop + bodyH * 0.18f
            )
            cubicTo(
                cx + w * 0.35f, bodyTop + bodyH * 0.28f,
                cx + w * 0.30f, bodyTop + bodyH * 0.38f,
                cx + w * 0.30f, bodyTop + bodyH * 0.50f
            )
            cubicTo(
                cx + w * 0.30f, bodyTop + bodyH * 0.62f,
                cx + w * 0.37f, bodyTop + bodyH * 0.68f,
                cx + w * 0.37f, bodyTop + bodyH * 0.78f
            )
            cubicTo(
                cx + w * 0.37f, bodyTop + bodyH * 0.88f,
                cx + w * 0.30f, bodyBottom,
                cx, bodyBottom
            )
            // 左侧对称流线
            cubicTo(
                cx - w * 0.30f, bodyBottom,
                cx - w * 0.37f, bodyTop + bodyH * 0.88f,
                cx - w * 0.37f, bodyTop + bodyH * 0.78f
            )
            cubicTo(
                cx - w * 0.37f, bodyTop + bodyH * 0.68f,
                cx - w * 0.30f, bodyTop + bodyH * 0.62f,
                cx - w * 0.30f, bodyTop + bodyH * 0.50f
            )
            cubicTo(
                cx - w * 0.30f, bodyTop + bodyH * 0.38f,
                cx - w * 0.35f, bodyTop + bodyH * 0.28f,
                cx - w * 0.35f, bodyTop + bodyH * 0.18f
            )
            cubicTo(
                cx - w * 0.34f, bodyTop + bodyH * 0.05f,
                cx - w * 0.20f, bodyTop,
                cx, bodyTop
            )
            close()
        }

        // 绘制车身底色与流线高光切边
        drawPath(path = bodyPath, color = bodyBgColor)
        drawPath(
            path = bodyPath,
            color = outlineColor,
            style = Stroke(width = stroke1dp)
        )

        // 2. 左右流线外后视镜 (羽翼微角)
        val mirrorY = bodyTop + bodyH * 0.28f
        val mirrorLen = 3.8.dp.toPx()
        // 左外后视镜
        val leftMirrorPath = Path().apply {
            moveTo(cx - w * 0.31f, mirrorY)
            lineTo(cx - w * 0.43f, mirrorY + 1.2.dp.toPx())
            lineTo(cx - w * 0.42f, mirrorY + mirrorLen)
            lineTo(cx - w * 0.30f, mirrorY + 2.5.dp.toPx())
            close()
        }
        drawPath(leftMirrorPath, color = mirrorColor)
        drawPath(leftMirrorPath, color = outlineColor, style = Stroke(width = stroke05dp))

        // 右外后视镜
        val rightMirrorPath = Path().apply {
            moveTo(cx + w * 0.31f, mirrorY)
            lineTo(cx + w * 0.43f, mirrorY + 1.2.dp.toPx())
            lineTo(cx + w * 0.42f, mirrorY + mirrorLen)
            lineTo(cx + w * 0.30f, mirrorY + 2.5.dp.toPx())
            close()
        }
        drawPath(rightMirrorPath, color = mirrorColor)
        drawPath(rightMirrorPath, color = outlineColor, style = Stroke(width = stroke05dp))

        // 3. 黑曜石全景天幕与前后风挡一体化座舱舱体
        val roofTop = bodyTop + bodyH * 0.21f
        val roofBottom = bodyTop + bodyH * 0.78f
        val canopyPath = Path().apply {
            moveTo(cx, roofTop)
            // 前风挡弧度
            cubicTo(
                cx + w * 0.15f, roofTop,
                cx + w * 0.22f, roofTop + bodyH * 0.04f,
                cx + w * 0.22f, roofTop + bodyH * 0.10f
            )
            // 天幕中段微收
            lineTo(cx + w * 0.20f, bodyTop + bodyH * 0.48f)
            // 后风挡弧度延伸
            cubicTo(
                cx + w * 0.23f, bodyTop + bodyH * 0.65f,
                cx + w * 0.21f, roofBottom - bodyH * 0.02f,
                cx + w * 0.15f, roofBottom
            )
            lineTo(cx - w * 0.15f, roofBottom)
            cubicTo(
                cx - w * 0.21f, roofBottom - bodyH * 0.02f,
                cx - w * 0.23f, bodyTop + bodyH * 0.65f,
                cx - w * 0.20f, bodyTop + bodyH * 0.48f
            )
            lineTo(cx - w * 0.22f, roofTop + bodyH * 0.10f)
            cubicTo(
                cx - w * 0.22f, roofTop + bodyH * 0.04f,
                cx - w * 0.15f, roofTop,
                cx, roofTop
            )
            close()
        }
        drawPath(canopyPath, color = glassCanopyColor)
        drawPath(canopyPath, color = glassBorderColor, style = Stroke(width = stroke05dp))

        // 前后风挡与天幕分界线
        val frontDividerY = roofTop + bodyH * 0.11f
        drawLine(
            color = glassBorderColor.copy(alpha = 0.55f),
            start = Offset(cx - w * 0.21f, frontDividerY),
            end = Offset(cx + w * 0.21f, frontDividerY),
            strokeWidth = stroke05dp
        )
        val rearDividerY = bodyTop + bodyH * 0.65f
        drawLine(
            color = glassBorderColor.copy(alpha = 0.55f),
            start = Offset(cx - w * 0.21f, rearDividerY),
            end = Offset(cx + w * 0.21f, rearDividerY),
            strokeWidth = stroke05dp
        )

        // 玻璃全景高光晶线 (45° 优雅反光，彰显黑曜石通透感)
        drawLine(
            color = Color.White.copy(alpha = if (isDark) 0.16f else 0.26f),
            start = Offset(cx - w * 0.13f, bodyTop + bodyH * 0.32f),
            end = Offset(cx + w * 0.15f, bodyTop + bodyH * 0.50f),
            strokeWidth = 0.8.dp.toPx()
        )

        // 4. 星环贯穿科技大灯与星翼贯穿尾灯
        // 车头星环贯穿式前灯
        val hlSpan = w * 0.21f
        val frontLightPath = Path().apply {
            moveTo(cx - hlSpan, bodyTop + 2.2.dp.toPx())
            quadraticTo(
                cx, bodyTop + 0.8.dp.toPx(),
                cx + hlSpan, bodyTop + 2.2.dp.toPx()
            )
        }
        drawPath(
            path = frontLightPath,
            color = headlightColor,
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // 车尾星翼贯穿式尾灯
        val tlSpan = w * 0.21f
        val rearLightPath = Path().apply {
            moveTo(cx - tlSpan, bodyBottom - 1.8.dp.toPx())
            quadraticTo(
                cx, bodyBottom - 0.6.dp.toPx(),
                cx + tlSpan, bodyBottom - 1.8.dp.toPx()
            )
        }
        drawPath(
            path = rearLightPath,
            color = taillightColor,
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // 5. 四轮双层机械轮毂（外层胎面 + 内层微光轮心）
        val wheelW = 4.5.dp.toPx()
        val wheelH = 13.5.dp.toPx()
        val wheelRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        fun drawWheel(x: Float, y: Float, isWarning: Boolean) {
            val fillColor = if (isWarning) wheelWarningColor else wheelNormalColor
            val borderColor = if (isWarning) wheelWarningColor else wheelNormalBorder

            // 外层轮胎本体
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(x - wheelW / 2f, y - wheelH / 2f),
                size = Size(wheelW, wheelH),
                cornerRadius = wheelRadius
            )
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(x - wheelW / 2f, y - wheelH / 2f),
                size = Size(wheelW, wheelH),
                cornerRadius = wheelRadius,
                style = Stroke(width = stroke05dp)
            )

            // 内层轮毂微光纵线 (精致机械质感)
            if (!isWarning) {
                drawLine(
                    color = wheelRimColor,
                    start = Offset(x, y - wheelH * 0.28f),
                    end = Offset(x, y + wheelH * 0.28f),
                    strokeWidth = 0.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            } else {
                // 异常轮胎向外呼吸红光波纹
                drawRoundRect(
                    color = wheelWarningColor.copy(alpha = 0.35f),
                    topLeft = Offset(x - wheelW / 2f - 2.dp.toPx(), y - wheelH / 2f - 2.dp.toPx()),
                    size = Size(wheelW + 4.dp.toPx(), wheelH + 4.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // 左右四轮位置，依前轮包与宽体后肩精确贴合
        val frontWheelOffset = w * 0.36f
        val rearWheelOffset = w * 0.38f

        drawWheel(cx - frontWheelOffset, frontAxleY, leftFrontWarning)
        drawWheel(cx - rearWheelOffset, rearAxleY, leftRearWarning)
        drawWheel(cx + frontWheelOffset, frontAxleY, rightFrontWarning)
        drawWheel(cx + rearWheelOffset, rearAxleY, rightRearWarning)
    }
}

@Composable
fun HomeTireCell(position: String, tire: TireStatus?, modifier: Modifier = Modifier) {
    val warning = tire?.warning == true
    val cellBorder = glassInsetBorder(warning)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (warning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f) else MaterialTheme.glassInsetSurface,
        border = cellBorder
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                position,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = if (warning) MaterialTheme.colorScheme.error.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tire?.pressure ?: "--kPa",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (warning) FontWeight.Bold else FontWeight.SemiBold,
                color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun VehicleStatusCard(
    status: VehicleStatus?,
    todayMileage: String = "--",
    modifier: Modifier = Modifier,
    powerAutoPlayEnabled: Boolean = false,
    onOpenHealthyCharging: () -> Unit = {},
    seamless: Boolean = false,
    onControl: ((String) -> Unit)? = null
) {
    val powerSummary = VehicleHomeStatus.powerSummary(
        chargeState = status?.chargeState,
        speed = status?.speed,
        isDriving = status?.isDriving,
        batteryVoltage = status?.batteryVoltage,
        batteryCurrent = status?.batteryCurrent
    )
    val windowAvailable = status?.windowStatusAvailable == true
    val openWindows = status?.openWindows.orEmpty()
    val windowLabel = when {
        !windowAvailable -> "--"
        openWindows.isNotEmpty() -> "车窗未关"
        else -> "车窗已关"
    }
    val lockLabel = when (status?.locked) {
        true -> "车锁已锁"
        false -> "车锁未锁"
        null -> "--"
    }
    val remainTime = status?.chargeRemainTime?.trim().takeUnless { it.isNullOrBlank() } ?: "未充"
    var showWindowDetails by rememberSaveable { mutableStateOf(false) }
    var powerNextPageRequest by rememberSaveable { mutableStateOf<Int?>(null) }
    val windowAlert = windowAvailable && openWindows.isNotEmpty()
    val lockAlert = status?.locked == false
    val hasAlert = windowAlert || lockAlert
    val cardBorder = glassCardBorder()
    val auraColor = if (hasAlert) {
        MaterialTheme.statusWarn.copy(alpha = 0.12f)
    } else {
        MaterialTheme.statusGood.copy(alpha = 0.10f)
    }
    Surface(
        modifier = modifier
            .heightIn(min = 120.dp)
            .then(
                if (!seamless) Modifier.frostedGlassCard(
                    shape = RoundedCornerShape(16.dp),
                    auraColor = auraColor,
                    auraCenter = Offset(0.85f, 0.15f)
                ) else Modifier
            ),
        shape = if (!seamless) RoundedCornerShape(16.dp) else androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
        border = if (!seamless) cardBorder else null,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 左侧里程卡片：支持点击/滑动在「今日里程」与「总里程」之间平滑切换
                val formattedTotalMileage = remember(status?.totalMileage) {
                    val raw = status?.totalMileage?.trim().orEmpty()
                    when {
                        raw.isEmpty() || raw == "--" || raw == "-- km" -> "--"
                        raw.endsWith("km", ignoreCase = true) -> raw.replace(" ", "")
                        else -> "${raw}km"
                    }
                }
                val mileageItems = listOf(
                    "今日里程" to todayMileage,
                    "总里程" to formattedTotalMileage
                )
                val mileagePagerState = rememberPagerState { mileageItems.size }
                var mileageNextPageRequest by rememberSaveable { mutableStateOf<Int?>(null) }
                LaunchedEffect(mileageNextPageRequest) {
                    mileageNextPageRequest?.let { page ->
                        mileagePagerState.animateScrollToPage(page)
                        mileageNextPageRequest = null
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.glassInsetSurface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    HorizontalPager(
                        state = mileagePagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                mileageNextPageRequest = (mileagePagerState.currentPage + 1) % mileageItems.size
                            },
                        pageSpacing = 8.dp,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = true
                    ) { page ->
                        val item = mileageItems[page]
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 2.dp, top = 5.5.dp, end = 2.dp, bottom = 9.5.dp),
                                verticalArrangement = Arrangement.spacedBy(1.5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = item.first,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = item.second,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        letterSpacing = (-0.3).sp
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 3.5.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(mileageItems.size) { dotIndex ->
                                    val isCurrent = mileagePagerState.currentPage == dotIndex
                                    val indicatorWidth by animateDpAsState(
                                        targetValue = if (isCurrent) 6.dp else 2.5.dp,
                                        animationSpec = tween(200),
                                        label = "mileageIndicatorWidth"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(indicatorWidth, 1.8.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(
                                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                // 右侧功率卡片：充电功率 / 剩余时间 / 电压 / 电流 / 电池温度
                val speedValue = status?.speed?.trim()?.removeSuffix("km/h")?.toDoubleOrNull()
                val powerTitle = if (speedValue != null && speedValue > 0.0) "行车功率" else "充电功率"
                val powerItems = listOf(
                    powerTitle to powerSummary,
                    "剩余时间" to remainTime,
                    "电压" to (status?.batteryVoltage ?: "--"),
                    "电流" to (status?.batteryCurrent ?: "--"),
                    "电池温度" to (status?.minBatteryTemp ?: "--")
                )
                val powerPagerState = rememberPagerState { powerItems.size }
                LaunchedEffect(powerNextPageRequest) {
                    powerNextPageRequest?.let { page ->
                        powerPagerState.animateScrollToPage(page)
                        powerNextPageRequest = null
                    }
                }
                LaunchedEffect(powerPagerState, powerAutoPlayEnabled) {
                    if (!powerAutoPlayEnabled || powerItems.size <= 1) return@LaunchedEffect
                    while (true) {
                        kotlinx.coroutines.delay(2000)
                        val next = (powerPagerState.currentPage + 1) % powerItems.size
                        powerPagerState.animateScrollToPage(next)
                    }
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.glassInsetSurface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    HorizontalPager(
                        state = powerPagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                powerNextPageRequest = (powerPagerState.currentPage + 1) % powerItems.size
                            },
                        pageSpacing = 8.dp,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = true
                    ) { page ->
                        val item = powerItems[page]
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 2.dp, top = 5.5.dp, end = 2.dp, bottom = 9.5.dp),
                                verticalArrangement = Arrangement.spacedBy(1.5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = item.first,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = item.second,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        letterSpacing = (-0.3).sp
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 3.5.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(powerItems.size) { dotIndex ->
                                    val isCurrent = powerPagerState.currentPage == dotIndex
                                    val indicatorWidth by animateDpAsState(
                                        targetValue = if (isCurrent) 6.dp else 2.5.dp,
                                        animationSpec = tween(200),
                                        label = "powerIndicatorWidth"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(indicatorWidth, 1.8.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(
                                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                VehicleStatusCell(
                    "门锁",
                    lockLabel,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    warning = status?.locked == false
                )
                VehicleStatusCell(
                    "车窗",
                    windowLabel,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    warning = windowAvailable && openWindows.isNotEmpty(),
                    onClick = if (windowAvailable) {
                        { showWindowDetails = true }
                    } else {
                        null
                    }
                )
            }
        }
    }

    if (showWindowDetails) {
        WindowStatusDialog(
            status = status,
            onControl = onControl,
            onDismiss = { showWindowDetails = false }
        )
    }
}

@Composable
fun VehicleStatusCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    onClick: (() -> Unit)? = null,
    warning: Boolean = false,
    valueColor: Color? = null
) {
    val cellBorder = glassInsetBorder()
    Surface(
        modifier = modifier.clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.glassInsetSurface,
        border = cellBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            val finalColor = valueColor ?: when {
                warning -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    letterSpacing = (-0.3).sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = finalColor,
                maxLines = 1,
                softWrap = false
            )
            if (unit != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun batteryPercentage(status: VehicleStatus?): String =
    VehicleHomeStatus.resolvedSocLabel(status?.preciseSoc, status?.soc)

fun chargeProgress(soc: String?): Float = VehicleStatusMapper.socFraction(soc)

@Composable
fun VehicleLocationDetailContent(
    summary: VehicleLocationSummary? = null,
    locationSnapshot: VehicleLocationSnapshot? = null,
    liveWeather: com.leapauto.app.weather.LiveWeather? = null,
    onHorn: (String) -> Unit = {}
) {
    val displaySummary = summary ?: locationSnapshot?.let {
        VehicleLocationSummary.fromSnapshot(it, System.currentTimeMillis())
    }
    var pendingMapAction by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val mapModel = VehicleLocationMapDomain.model(locationSnapshot, displaySummary, mapRequested = true)
    val hasLocationData = locationSnapshot != null && displaySummary?.availability == VehicleLocationAvailability.AVAILABLE
    val canNavigateToVehicle = hasLocationData && mapModel.showMarker
    val apps = pendingMapAction?.let { action ->
        ExternalMapLauncher.availableApps(context, forNavigation = action == MAP_ACTION_NAVIGATE)
    }.orEmpty()

    LaunchedEffect(pendingMapAction, apps.size) {
        val action = pendingMapAction ?: return@LaunchedEffect
        if (apps.size == 1) {
            val app = apps.first()
            pendingMapAction = null
            if (action == MAP_ACTION_NAVIGATE && canNavigateToVehicle) {
                locationSnapshot?.location?.let { loc ->
                    runCatching {
                        ExternalMapLauncher.navigateToVehicle(context, app, loc.latitude, loc.longitude)
                    }
                }
            }
        }
    }

    if (pendingMapAction != null && apps.isNotEmpty()) {
        ExternalMapAppDialog(
            title = "选择地图应用",
            apps = apps,
            onDismiss = { pendingMapAction = null },
            onSelect = { app ->
                val action = pendingMapAction
                pendingMapAction = null
                if (action == MAP_ACTION_NAVIGATE && canNavigateToVehicle) {
                    locationSnapshot?.location?.let { loc ->
                        runCatching {
                            ExternalMapLauncher.navigateToVehicle(context, app, loc.latitude, loc.longitude)
                        }
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hasLocationData) {
            LocationMapStateMessage(
                title = "暂无可用位置",
                message = "在最后一次成功的车辆刷新后，可使用外部地图进行导航。"
            )
        } else if (mapModel.state == VehicleLocationMapState.INVALID) {
            LocationMapStateMessage(
                title = "位置数据无效",
                message = "当前没有可用于外部地图导航的可用位置。"
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .frostedGlassCard(
                            shape = RoundedCornerShape(16.dp),
                            auraColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            auraCenter = Offset(0.5f, 0.4f)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = glassCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_vehicle_location_marker),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "车辆位置已获取",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "位置信息仅在内存中使用，可通过右下角按钮选择外部地图进行导航",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            VehicleLocationSummaryPresentation.refreshTimeLabel(displaySummary),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (liveWeather != null && liveWeather.weather.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                modifier = Modifier.fillMaxWidth(0.92f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    val weatherIcon = com.leapauto.app.weather.WeatherVisualResolver.iconRes(liveWeather.weather)
                                    val weatherTint = com.leapauto.app.weather.WeatherVisualResolver.iconTint(liveWeather.weather, isDark = true)
                                    Icon(
                                        painter = painterResource(weatherIcon),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = weatherTint
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    val windText = if (liveWeather.windDirection.isNotBlank() && liveWeather.windPower.isNotBlank()) {
                                        "${liveWeather.windDirection}风 ${liveWeather.windPower}级"
                                    } else ""
                                    val humidityText = if (liveWeather.humidity.isNotBlank()) "湿度 ${liveWeather.humidity}%" else ""
                                    val updateTimeText = if (liveWeather.reportTime.isNotBlank()) "更新于 ${liveWeather.reportTime.substringAfter(" ")}" else ""
                                    val subParts = listOf(windText, humidityText, updateTimeText).filter { it.isNotBlank() }
                                    val subSummary = if (subParts.isNotEmpty()) " · " + subParts.joinToString(" · ") else ""
                                    Text(
                                        text = "${liveWeather.weather} ${liveWeather.temperature}℃$subSummary",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                if (mapModel.showMarker) {
                    LocationMapActionColumn(
                        navigateEnabled = canNavigateToVehicle,
                        onHorn = { onHorn("horn") },
                        onNavigate = { pendingMapAction = MAP_ACTION_NAVIGATE }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.LocationMapActionColumn(
    navigateEnabled: Boolean,
    onHorn: () -> Unit,
    onNavigate: () -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LocationMapActionButton(
            iconResId = R.drawable.ic_location_horn,
            contentDescription = "鸣笛寻车",
            enabled = true,
            onClick = onHorn
        )
        LocationMapActionButton(
            iconResId = R.drawable.ic_location_navigate,
            contentDescription = "导航到车",
            enabled = navigateEnabled,
            onClick = onNavigate
        )
    }
}

@Composable
private fun LocationMapActionButton(
    iconResId: Int,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f), CircleShape),
        enabled = enabled
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ExternalMapAppDialog(
    title: String,
    apps: List<ExternalMapApp>,
    onDismiss: () -> Unit,
    onSelect: (ExternalMapApp) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.forEach { app ->
                    OutlinedButton(
                        onClick = { onSelect(app) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text(app.displayName)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun FullScreenImagePreviewDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 5f)
                        val maxOffsetX = (size.width * (scale - 1) / 2f).coerceAtLeast(0f)
                        val maxOffsetY = (size.height * (scale - 1) / 2f).coerceAtLeast(0f)
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                            y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        )
                    }
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "全屏实景照片",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )

            // 顶部关闭按钮
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 20.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.40f)),
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_x),
                        contentDescription = "关闭预览",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 底部手势提示 (大幅抬高避开系统手势导航横条)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp),
                shape = RoundedCornerShape(percent = 50),
                color = Color.Black.copy(alpha = 0.70f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.30f)),
                shadowElevation = 0.dp
            ) {
                Text(
                    text = "双指可自由缩放与拖动查看细节",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun ParkingDetailDialog(
    fullAddress: String,
    statusUpdatedAtEpochMs: Long,
    locationSnapshot: VehicleLocationSnapshot? = null,
    liveWeather: com.leapauto.app.weather.LiveWeather? = null,
    onFetchParkingPhoto: ((ChassisParkingPhoto?, android.graphics.Bitmap?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember(context) { ExternalMapLauncher.availableApps(context, forNavigation = true) }
    var photoState by remember { mutableStateOf<ParkingPhotoLoadState>(ParkingPhotoLoadState.Loading) }
    var uploadTimeMs by remember { mutableLongStateOf(0L) }
    var showExternalMapDialog by remember { mutableStateOf(false) }
    var showFullScreenPhoto by remember { mutableStateOf(false) }

    var phoneLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var hasLocationPerm by remember { mutableStateOf<Boolean>(PhoneLocationHelper.hasLocationPermission(context)) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        hasLocationPerm = granted
        if (granted) {
            PhoneLocationHelper.requestCurrentLocation(context) { lat: Double, lng: Double ->
                phoneLocation = Pair(lat, lng)
            }
        }
    }

    LaunchedEffect(hasLocationPerm) {
        if (hasLocationPerm) {
            PhoneLocationHelper.requestCurrentLocation(context) { lat: Double, lng: Double ->
                phoneLocation = Pair(lat, lng)
            }
        }
    }

    val distanceText = remember(phoneLocation, locationSnapshot?.location) {
        val phone = phoneLocation
        val car = locationSnapshot?.location
        if (phone != null && car != null) {
            val meters = PhoneLocationHelper.calculateDistanceMeters(phone.first, phone.second, car.latitude, car.longitude)
            PhoneLocationHelper.formatDistance(meters)
        } else {
            null
        }
    }

    LaunchedEffect(Unit) {
        onFetchParkingPhoto { photoInfo, bitmap ->
            if (photoInfo != null && bitmap != null) {
                uploadTimeMs = photoInfo.uploadTimeMs
                photoState = ParkingPhotoLoadState.Success(bitmap)
            } else if (photoInfo != null) {
                uploadTimeMs = photoInfo.uploadTimeMs
                photoState = ParkingPhotoLoadState.Empty
            } else {
                photoState = ParkingPhotoLoadState.Empty
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 实景照片卡片 (点击可放大全屏预览)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlassCard(shape = RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = glassCardBorder()
                ) {
                    when (val state = photoState) {
                        is ParkingPhotoLoadState.Loading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "正在获取驻车实景照片...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is ParkingPhotoLoadState.Success -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showFullScreenPhoto = true }
                            ) {
                                Image(
                                    bitmap = state.bitmap.asImageBitmap(),
                                    contentDescription = "驻车实景照片",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 280.dp, max = 380.dp),
                                    contentScale = ContentScale.Crop
                                )
                                // 左下角：手机与车辆直线距离
                                if (locationSnapshot?.location != null) {
                                    Surface(
                                        shape = RoundedCornerShape(percent = 50),
                                        color = Color.Black.copy(alpha = 0.60f),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(10.dp)
                                            .then(
                                                if (!hasLocationPerm) {
                                                    Modifier.clickable {
                                                        permissionLauncher.launch(
                                                            arrayOf(
                                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                                                            )
                                                        )
                                                    }
                                                } else Modifier
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_location_pin),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            val distLabel = when {
                                                distanceText != null -> "距车 $distanceText"
                                                hasLocationPerm -> "距车 计算中..."
                                                else -> "距车 点击测距"
                                            }
                                            Text(
                                                text = distLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(percent = 50),
                                    color = Color.Black.copy(alpha = 0.60f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp)
                                ) {
                                    val timeStr = if (uploadTimeMs > 0L) {
                                        SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(Date(uploadTimeMs)) + " · 点击放大"
                                    } else {
                                        "点击放大"
                                    }
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "暂无驻车实景照片",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "熄火停稳后车身环视相机自动拍摄",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. 位置信息 (点击直接弹出地图应用进行导航，纯粹无冗余)
                val hasLocation = locationSnapshot?.location != null
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlassCard(shape = RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = hasLocation && apps.isNotEmpty()) {
                            if (apps.size == 1) {
                                val loc = locationSnapshot?.location
                                if (loc != null) {
                                    runCatching {
                                        ExternalMapLauncher.navigateToVehicle(context, apps.first(), loc.latitude, loc.longitude)
                                    }
                                }
                            } else {
                                showExternalMapDialog = true
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = glassCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_location_pin),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(
                                text = fullAddress.ifBlank { "正在获取车辆位置..." },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (hasLocation && apps.isNotEmpty()) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                contentDescription = "导航",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. 实况气象与爱车洗车建议卡片 (方案 A 一体化微晶卡片)
                if (liveWeather != null && liveWeather.weather.isNotBlank()) {
                    val weatherTint = com.leapauto.app.weather.WeatherVisualResolver.iconTint(liveWeather.weather, isDark = true)
                    val weatherIcon = com.leapauto.app.weather.WeatherVisualResolver.iconRes(liveWeather.weather)
                    val carWashAdvice = remember(liveWeather) {
                        com.leapauto.app.weather.CarWashRecommendationPolicy.evaluate(liveWeather)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedGlassCard(
                                shape = RoundedCornerShape(14.dp),
                                auraColor = weatherTint.copy(alpha = 0.08f),
                                auraCenter = Offset(0.85f, 0.20f),
                                auraRadiusRatio = 0.65f
                            ),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        border = glassCardBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 首层：天气图标 + 气温 + 天气现象 + 洗车建议标签
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(weatherIcon),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = weatherTint
                                    )
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = liveWeather.temperature.ifBlank { "--" },
                                            fontSize = 24.sp,
                                            lineHeight = 26.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "℃",
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = weatherTint,
                                            modifier = Modifier.padding(bottom = 2.dp, start = 1.dp)
                                        )
                                    }
                                    Text(
                                        text = liveWeather.weather,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (carWashAdvice != null) {
                                    Surface(
                                        shape = RoundedCornerShape(percent = 50),
                                        color = carWashAdvice.tagColor.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, carWashAdvice.tagColor.copy(alpha = 0.40f))
                                    ) {
                                        Text(
                                            text = carWashAdvice.levelLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = carWashAdvice.tagColor,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                                        )
                                    }
                                }
                            }

                            // 中层：并排双微晶格 (风向风力 & 空气湿度)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. 风向风力小卡
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(com.leapauto.app.weather.WeatherVisualResolver.windIconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        val windStr = if (liveWeather.windDirection.isNotBlank() && liveWeather.windPower.isNotBlank()) {
                                            "${liveWeather.windDirection}风 ${liveWeather.windPower}级"
                                        } else if (liveWeather.windPower.isNotBlank()) {
                                            "${liveWeather.windPower}级"
                                        } else "--"
                                        Text(
                                            text = windStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // 2. 空气湿度小卡
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(com.leapauto.app.weather.WeatherVisualResolver.humidityIconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        val humidityStr = if (liveWeather.humidity.isNotBlank()) "湿度 ${liveWeather.humidity}%" else "--"
                                        Text(
                                            text = humidityStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // 底层：贴心爱车洗车建议
                            if (carWashAdvice != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = carWashAdvice.tagColor.copy(alpha = 0.06f),
                                    border = BorderStroke(0.5.dp, carWashAdvice.tagColor.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_phosphor_car),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = carWashAdvice.tagColor
                                        )
                                        Text(
                                            text = carWashAdvice.reason,
                                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    if (showExternalMapDialog && apps.isNotEmpty()) {
        ExternalMapAppDialog(
            title = "选择地图应用开始导航",
            apps = apps,
            onDismiss = { showExternalMapDialog = false },
            onSelect = { app ->
                showExternalMapDialog = false
                val loc = locationSnapshot?.location
                if (loc != null) {
                    runCatching {
                        ExternalMapLauncher.navigateToVehicle(context, app, loc.latitude, loc.longitude)
                    }
                }
            }
        )
    }

    if (showFullScreenPhoto && photoState is ParkingPhotoLoadState.Success) {
        FullScreenImagePreviewDialog(
            bitmap = (photoState as ParkingPhotoLoadState.Success).bitmap,
            onDismiss = { showFullScreenPhoto = false }
        )
    }
}

@Composable
private fun LocationMapStateMessage(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun formatRankLeadingAnnotatedString(rawRank: String, vehicleModel: String): AnnotatedString {
    val trimmed = rawRank.trim()
    val percentMatch = Regex("\\d+(?:\\.\\d+)?%?").find(trimmed)?.value ?: trimmed
    val percent = if (percentMatch.endsWith("%")) percentMatch else "$percentMatch%"
    val model = VehicleConfigConfirmationPolicy.normalizeModel(vehicleModel)
        ?: vehicleModel.removePrefix("零跑").trim().takeIf { it.isNotBlank() }
        ?: "零跑"
    val numericValue = percentMatch.removeSuffix("%").toDoubleOrNull()
    val percentColor = when {
        numericValue == null -> MaterialTheme.colorScheme.onSurfaceVariant
        numericValue <= 20.0 -> MaterialTheme.colorScheme.error
        numericValue <= 50.0 -> MaterialTheme.statusWarn
        numericValue <= 75.0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.statusGood
    }
    return buildAnnotatedString {
        append("超越全国")
        withStyle(SpanStyle(color = percentColor, fontWeight = FontWeight.Bold)) {
            append(percent)
        }
        append("的${model}车主")
    }
}

@Composable
fun EnergyHomePagerCard(
    state: EnergyAnalyticsState,
    vehicleTotalMileage: String? = null,
    vehicleModel: String = "",
    modifier: Modifier = Modifier,
    seamless: Boolean = false
) {
    val pagerState = rememberPagerState(initialPage = EnergyHomePage.SUMMARY.ordinal) { EnergyHomePage.entries.size }
    val goodColor = MaterialTheme.statusGood
    Surface(
        modifier = modifier
            .heightIn(min = EnergyHomeCardPolicy.MIN_CARD_HEIGHT_DP.dp)
            .then(
                if (!seamless) Modifier.frostedGlassCard(
                    shape = RoundedCornerShape(16.dp),
                    auraColor = goodColor.copy(alpha = 0.10f),
                    auraCenter = Offset(0.85f, 0.15f)
                ) else Modifier
            ),
        shape = if (!seamless) RoundedCornerShape(16.dp) else androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (!seamless) glassCardBorder() else null,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val pageTitle = when (EnergyHomePage.entries[pagerState.currentPage]) {
                    EnergyHomePage.SUMMARY -> "能耗里程"
                    EnergyHomePage.WEEKLY_CONSUMPTION -> "近6周百公里能耗"
                    EnergyHomePage.RECENT_MILEAGE -> "近7天行驶里程"
                    EnergyHomePage.WEEKLY_COMPOSITION -> "周能耗分布"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (pagerState.currentPage == EnergyHomePage.WEEKLY_CONSUMPTION.ordinal) {
                        val rank = (state as? EnergyAnalyticsState.Success)?.data?.rankLabel
                        rank?.let {
                            Text(
                                text = formatRankLeadingAnnotatedString(it, vehicleModel),
                                style = MaterialTheme.typography.labelSmall.energyStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                when (state) {
                    is EnergyAnalyticsState.Success -> HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        when (EnergyHomePage.entries[page]) {
                            EnergyHomePage.SUMMARY -> EnergyHomeSummaryPage(state.data, vehicleTotalMileage)
                            EnergyHomePage.WEEKLY_CONSUMPTION -> EnergyHomeWeeklyPage(state.data)
                            EnergyHomePage.RECENT_MILEAGE -> EnergyHomeMileagePage(state.data)
                            EnergyHomePage.WEEKLY_COMPOSITION -> EnergyHomeCompositionPage(state.data)
                        }
                    }
                    is EnergyAnalyticsState.Idle,
                    is EnergyAnalyticsState.Loading,
                    is EnergyAnalyticsState.Failed -> EnergyHomeEmptyPage(state)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(EnergyHomePage.entries.size) { page ->
                        val isSelected = pagerState.currentPage == page
                        val indicatorWidth by animateDpAsState(
                            targetValue = if (isSelected) 10.dp else 3.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "energyIndicatorWidth"
                        )
                        val indicatorColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                            },
                            animationSpec = tween(200),
                            label = "energyIndicatorColor"
                        )
                        Box(
                            Modifier
                                .padding(horizontal = 2.dp)
                                .size(indicatorWidth, 2.5.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(indicatorColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnergyHomeSummaryPage(
    data: EnergyAnalyticsData,
    vehicleTotalMileage: String? = null
) {
    val totalMileageDisplay = data.totalMileage?.value?.trim()?.removeSuffix("km")?.trim()
        ?: vehicleTotalMileage?.trim()?.removeSuffix("km")?.trim()?.takeIf { it.isNotBlank() && it != "--" }
        ?: "—"
    val daysHasValue = data.ownershipDays?.value != null
    val mileageHasValue = totalMileageDisplay != "—" && totalMileageDisplay != "--"
    val energyHasValue = data.cumulativeEnergy != null

    val daysText = data.ownershipDays?.value?.toString() ?: "--"
    val energyText = data.cumulativeEnergy?.value?.trim()?.removeSuffix("kWh")?.trim() ?: "--"

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EnergyMetricCard(
            label = "提车时长",
            value = daysText,
            unit = "天",
            valueColor = if (daysHasValue) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant,
            accentColor = MaterialTheme.statusGood,
            modifier = Modifier.weight(1f)
        )
        EnergyMetricCard(
            label = "累计里程",
            value = totalMileageDisplay,
            unit = "km",
            valueColor = if (mileageHasValue) MaterialTheme.statusWarn else MaterialTheme.colorScheme.onSurfaceVariant,
            accentColor = MaterialTheme.statusWarn,
            modifier = Modifier.weight(1f)
        )
        EnergyMetricCard(
            label = "累计能耗",
            value = energyText,
            unit = "kWh",
            valueColor = if (energyHasValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EnergyMetricCard(
    label: String,
    value: String,
    unit: String,
    valueColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .frostedGlassCard(
                shape = RoundedCornerShape(12.dp),
                auraColor = accentColor.copy(alpha = 0.08f),
                auraCenter = Offset(0.5f, 0.2f)
            ),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = glassCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            Text(
                text = value,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                style = MaterialTheme.typography.titleMedium.energyStyle(),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall.energyStyle().copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EnergyHomeWeeklyPage(data: EnergyAnalyticsData) {
    val points = EnergyHomeCardPolicy.recentTrend(data, limit = 6)
    val overallNumber = data.overallConsumption?.value?.trim()?.removeSuffix("kWh/100km")?.trim() ?: "--"
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = overallNumber,
                    fontSize = 22.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (data.overallConsumption == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.statusGood,
                    style = MaterialTheme.typography.titleLarge.energyStyle()
                )
                Text(
                    text = "kWh/100km",
                    style = MaterialTheme.typography.labelSmall.energyStyle().copy(fontSize = 11.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        if (points.isEmpty()) {
            EnergyHomeMissingData("暂无近 6 周能耗数据")
        } else {
            EnergyHomeBars(points, modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp, bottom = 2.dp))
        }
    }
}

@Composable
fun EnergyHomeMileagePage(data: EnergyAnalyticsData) {
    val points = EnergyHomeCardPolicy.recentMileage(data, limit = 7)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            displayEnergyMetric(data.recentMileage, "km"),
            style = MaterialTheme.typography.titleLarge.energyStyle(),
            fontWeight = FontWeight.Bold,
            color = if (data.recentMileage == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.statusGood,
            maxLines = 1
        )
        if (points.isEmpty()) {
            EnergyHomeMissingData("暂无近 7 天里程数据")
        } else {
            EnergyHomeLineChart(points, modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp, bottom = 2.dp))
        }
    }
}

@Composable
private fun EnergyHomeMetricLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueBold: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.energyStyle(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.energyStyle(),
            color = valueColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EnergyHomeBars(
    points: List<com.leapauto.app.EnergySeriesPoint>,
    modifier: Modifier = Modifier
) {
    val max = points.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: 1.0
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
    ) {
        val totalHeight = maxHeight
        val valueLabelHeight = 18.dp
        val dateLabelHeight = 16.dp
        val spacing = 8.dp
        val trackHeight = (totalHeight - valueLabelHeight - dateLabelHeight - spacing).coerceAtLeast(32.dp)

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            points.forEachIndexed { index, point ->
                val isLast = index == points.lastIndex
                val ratio = (point.value / max).toFloat().coerceIn(0f, 1f)
                val barHeight = (trackHeight * 0.15f + trackHeight * 0.85f * ratio).coerceIn(6.dp, trackHeight)
                val barBrush = if (isLast) {
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.statusGood,
                            MaterialTheme.statusGood.copy(alpha = 0.70f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    )
                }
                val dateLabel = if (EnergyWeekPeriodFormatter.isCurrentWeek(point.label, point.endLabel)) {
                    "本周"
                } else {
                    EnergyWeekPeriodFormatter.weekEndDate(point.label, point.endLabel)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 柱顶能耗数值
                    Text(
                        text = point.value.formatEnergyNumber(),
                        style = MaterialTheme.typography.labelSmall.energyStyle().copy(fontSize = 10.sp),
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isLast) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )

                    // 能量仓胶囊轨道
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(trackHeight)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.glassInsetSurface)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .clip(RoundedCornerShape(9.dp))
                                .background(barBrush)
                        )
                    }

                    // 柱底时间标签
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall.energyStyle().copy(fontSize = 9.5.sp),
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLast) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
fun EnergyHomeCompositionPage(data: EnergyAnalyticsData) {
    val categories = data.lastWeekComposition.ifEmpty { data.composition }
    val total = categories.sumOf { it.value.coerceAtLeast(0.0) }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (categories.isEmpty()) {
            EnergyHomeMissingData("暂无上周能耗分布数据")
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EnergyHomeCompositionDonut(
                    categories = categories,
                    total = total,
                    modifier = Modifier.padding(start = 2.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    categories.take(3).forEachIndexed { index, category ->
                        val label = EnergyCompositionPresentation.displayLabel(category.label)
                        val percentInt = if (total > 0.0) {
                            (category.value.coerceAtLeast(0.0) / total * 100.0).roundToInt().coerceIn(0, 100)
                        } else 0
                        val energyValue = "${category.value.formatEnergyNumber()} kWh"
                        val typeColor = energyCompositionColor(category.label, index)
                        val fraction = if (total > 0.0) (category.value.coerceAtLeast(0.0) / total).toFloat().coerceIn(0f, 1f) else 0f
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(typeColor)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    energyValue,
                                    style = MaterialTheme.typography.bodySmall.energyStyle(),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = typeColor.copy(alpha = 0.12f),
                                    contentColor = typeColor
                                ) {
                                    Text(
                                        text = "$percentInt%",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall.energyStyle().copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction.coerceAtLeast(0.03f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(typeColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnergyHomeCompositionDonut(
    categories: List<com.leapauto.app.EnergyCategory>,
    total: Double,
    modifier: Modifier = Modifier
) {
    val chartColors = categories.mapIndexed { index, category ->
        energyCompositionColor(category.label, index)
    }
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    val emptyChartColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier.size(86.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val stroke = 8.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (total > 0.0) {
                var start = -90f
                val gap = if (categories.size > 1) 4f else 0f
                categories.forEachIndexed { index, category ->
                    val sweep = (category.value.coerceAtLeast(0.0) / total * 360.0).toFloat()
                    if (sweep > gap) {
                        drawArc(
                            color = chartColors[index % chartColors.size],
                            startAngle = start + gap / 2f,
                            sweepAngle = sweep - gap,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    start += sweep
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val totalNumber = if (total > 0.0) total.formatEnergyNumber() else "--"
            Text(
                text = totalNumber,
                style = MaterialTheme.typography.titleMedium.energyStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "总能耗 kWh",
                style = MaterialTheme.typography.labelSmall.energyStyle().copy(fontSize = 9.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EnergyHomeLineChart(
    points: List<com.leapauto.app.EnergySeriesPoint>,
    modifier: Modifier = Modifier
) {
    val plotMax = points.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: 1.0
    val chartColor = MaterialTheme.statusGood
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
    ) {
        val pointRadius = 3.dp.toPx()
        val horizontalInset = 16.dp.toPx()
        val labelReserve = 15.dp.toPx()
        val dateReserve = 16.dp.toPx()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = 9.5.sp.toPx()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val plotTop = labelReserve + pointRadius
        val plotBottom = (size.height - dateReserve - pointRadius).coerceAtLeast(plotTop)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(0f)
        val xStart = horizontalInset
        val xEnd = (size.width - horizontalInset).coerceAtLeast(xStart)
        val dateY = size.height - 2.dp.toPx()
        if (points.size == 1) {
            val center = Offset(
                x = size.width / 2,
                y = plotTop + plotHeight / 2
            )
            drawCircle(chartColor.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = center)
            drawCircle(chartColor, radius = pointRadius, center = center)
            drawCircle(Color.White, radius = pointRadius * 0.5f, center = center)
            drawContext.canvas.nativeCanvas.drawText(
                points.single().value.formatEnergyNumber(),
                center.x,
                (center.y - pointRadius - 3.dp.toPx()).coerceAtLeast(labelPaint.textSize),
                labelPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                EnergyWeekPeriodFormatter.monthDay(points.single().label),
                center.x,
                dateY,
                labelPaint
            )
        } else {
            val step = (xEnd - xStart) / (points.size - 1)
            val coords = points.mapIndexed { index, point ->
                Offset(
                    x = xStart + index * step,
                    y = plotBottom - (point.value / plotMax).toFloat().coerceIn(0f, 1f) * plotHeight
                )
            }
            val strokePath = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 0 until coords.size - 1) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
            }
            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(coords.last().x, plotBottom)
                lineTo(coords.first().x, plotBottom)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        chartColor.copy(alpha = 0.25f),
                        chartColor.copy(alpha = 0.03f)
                    ),
                    startY = plotTop,
                    endY = plotBottom
                )
            )
            drawPath(
                path = strokePath,
                color = chartColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            coords.forEachIndexed { index, point ->
                val isLast = index == points.lastIndex
                if (isLast) {
                    drawCircle(chartColor.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = point)
                }
                drawCircle(chartColor, radius = pointRadius, center = point)
                drawCircle(Color.White, radius = pointRadius * 0.5f, center = point)
                drawContext.canvas.nativeCanvas.drawText(
                    points[index].value.formatEnergyNumber(),
                    point.x,
                    (point.y - pointRadius - 3.dp.toPx()).coerceAtLeast(labelPaint.textSize),
                    labelPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    EnergyWeekPeriodFormatter.monthDay(points[index].label),
                    point.x,
                    dateY,
                    labelPaint
                )
            }
        }
    }
}

@Composable
fun EnergyHomeEmptyPage(state: EnergyAnalyticsState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            when (state) {
                EnergyAnalyticsState.Idle -> "能耗数据待同步"
                EnergyAnalyticsState.Loading -> "正在读取能耗数据"
                is EnergyAnalyticsState.Failed -> "能耗服务暂不可用"
                is EnergyAnalyticsState.Success -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EnergyHomeMissingData(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ClimateOverviewCard(
    status: VehicleStatus?,
    liveWeather: com.leapauto.app.weather.LiveWeather? = null,
    onOpenClimate: () -> Unit,
    onQuickAcToggle: (String) -> Unit,
    controlBusy: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    seamless: Boolean = false
) {
    val temperatureTarget = Commands.acTemperatureTarget(status?.acSetting)
    val quickToggle = HomeClimateTogglePresentationMapper.from(status?.acSwitch, controlBusy)
    val quickToggleCommand = quickToggle.command
    val climateTone = ClimateTemperatureToneResolver.tone(
        acEnabled = status?.acSwitch,
        coolingAndHeating = status?.acCoolingAndHeating,
        climateMode = status?.climateMode,
        targetTemperature = temperatureTarget.value
    )
    val temperatureColor = when (climateTone) {
        ClimateTemperatureTone.COOLING -> MaterialTheme.colorScheme.primary
        ClimateTemperatureTone.HEATING -> MaterialTheme.statusWarn
        ClimateTemperatureTone.VENTILATION -> MaterialTheme.statusGood
        ClimateTemperatureTone.DEFAULT -> MaterialTheme.colorScheme.onSurface
    }

    val isAcRunning = status?.acSwitch == true && climateTone != ClimateTemperatureTone.DEFAULT
    val infiniteTransition = rememberInfiniteTransition(label = "acRunningRotation")
    val animatedAngle by infiniteTransition.animateFloat(
        initialValue = ClimateIconRotationSpec.START_DEGREES,
        targetValue = ClimateIconRotationSpec.END_DEGREES,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ClimateIconRotationSpec.ROTATION_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "acRotationAngle"
    )
    val rotationAngle = ClimateIconRotationSpec.rotationAngle(isAcRunning, animatedAngle)

    val acStateLabel = when (status?.acSwitch) {
        true -> "ON"
        false -> "OFF"
        null -> "--"
    }
    val indoorTempValue = status?.indoorTemp
        ?.replace("°C", "", ignoreCase = true)
        ?.replace("°", "", ignoreCase = true)
        ?.trim()
        ?.toDoubleOrNull()
    val indoorTempColor = when {
        indoorTempValue == null -> MaterialTheme.colorScheme.onSurfaceVariant
        indoorTempValue >= 26.0 -> MaterialTheme.statusWarn
        indoorTempValue < 18.0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.statusGood
    }
    val indoorTempClean = status?.indoorTemp
        ?.replace("°C", "", ignoreCase = true)
        ?.replace("°", "", ignoreCase = true)
        ?.trim()

    val ambientBrush = if (status?.acSwitch == true) {
        when (climateTone) {
            ClimateTemperatureTone.COOLING -> Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    Color.Transparent
                )
            )
            ClimateTemperatureTone.HEATING -> Brush.horizontalGradient(
                listOf(
                    MaterialTheme.statusWarn.copy(alpha = 0.06f),
                    Color.Transparent
                )
            )
            ClimateTemperatureTone.VENTILATION -> Brush.horizontalGradient(
                listOf(
                    MaterialTheme.statusGood.copy(alpha = 0.06f),
                    Color.Transparent
                )
            )
            ClimateTemperatureTone.DEFAULT -> null
        }
    } else {
        null
    }

    val isAcOn = status?.acSwitch == true
    val climateAuraColor = if (isAcOn) {
        when (climateTone) {
            ClimateTemperatureTone.COOLING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ClimateTemperatureTone.HEATING -> MaterialTheme.statusWarn.copy(alpha = 0.14f)
            else -> temperatureColor.copy(alpha = 0.14f)
        }
    } else {
        MaterialTheme.statusGood.copy(alpha = 0.08f)
    }

    Surface(
        shape = if (!seamless) RoundedCornerShape(16.dp) else androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (!seamless && isAcOn && climateTone != ClimateTemperatureTone.DEFAULT) {
            BorderStroke(1.dp, temperatureColor.copy(alpha = 0.45f))
        } else if (!seamless) {
            glassCardBorder()
        } else null,
        shadowElevation = 0.dp,
        modifier = modifier
            .height(if (compact) 48.dp else 46.dp)
            .then(
                if (!seamless) Modifier.frostedGlassCard(
                    shape = RoundedCornerShape(16.dp),
                    auraColor = climateAuraColor,
                    auraCenter = Offset(0.85f, 0.25f),
                    auraRadiusRatio = 0.5f
                ) else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ambientBrush != null) Modifier.background(ambientBrush) else Modifier)
        ) {
            if (compact) {
                // 并排紧凑双子舱布局
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenClimate),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_fan),
                            contentDescription = "空调状态",
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = rotationAngle },
                            tint = temperatureColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                if (status?.acSwitch == true) "空调 · ${temperatureTarget.value}°C" else "空调",
                                style = MaterialTheme.typography.labelMedium.energyStyle(),
                                fontWeight = FontWeight.Bold,
                                color = if (status?.acSwitch == true) temperatureColor else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(1.dp))
                            val subTitle = if (status?.acSwitch == true) {
                                if (indoorTempClean.isNullOrBlank()) "运行中" else "车内 $indoorTempClean°C"
                            } else "已关闭"
                            Text(
                                subTitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    ClimateToggle(
                        checked = status?.acSwitch == true,
                        onCheckedChange = if (quickToggle.enabled && quickToggleCommand != null) {
                            { onQuickAcToggle(quickToggleCommand) }
                        } else null,
                        contentDescription = quickToggle.contentDescription,
                        stateDescription = acStateLabel
                    )
                }
            } else {
                // 原有全宽独立布局
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenClimate),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_fan),
                            contentDescription = "空调状态",
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    rotationZ = rotationAngle
                                },
                            tint = temperatureColor
                        )
                        Spacer(Modifier.width(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "设定：",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${temperatureTarget.value} °C",
                                style = MaterialTheme.typography.labelLarge.energyStyle(),
                                fontWeight = FontWeight.Bold,
                                color = temperatureColor
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "车内：",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (indoorTempClean.isNullOrBlank()) "-- °C" else "$indoorTempClean °C",
                                style = MaterialTheme.typography.labelLarge.energyStyle(),
                                fontWeight = FontWeight.Bold,
                                color = indoorTempColor
                            )
                        }
                    }
                    Text(
                        acStateLabel,
                        style = MaterialTheme.typography.labelMedium.energyStyle(),
                        fontWeight = FontWeight.SemiBold,
                        color = if (status?.acSwitch == true) temperatureColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ClimateToggle(
                        checked = status?.acSwitch == true,
                        onCheckedChange = if (quickToggle.enabled && quickToggleCommand != null) {
                            { onQuickAcToggle(quickToggleCommand) }
                        } else null,
                        contentDescription = quickToggle.contentDescription,
                        stateDescription = acStateLabel
                    )
                }
            }
        }
    }
}

private fun climateModeLabel(status: VehicleStatus): String? = when {
    status.acOperateMode == 1 && status.climateMode == 0 -> "自动空调"
    status.climateMode == 1 -> "快速制冷"
    status.climateMode == 3 -> "快速制热"
    status.climateMode == 4 -> "通风除味"
    else -> null
}

private fun activeClimatePresetName(status: VehicleStatus?): String? {
    if (status?.acSwitch != true) return null
    if (status.windshieldDefrost == true) return ClimatePresetAction.WINDSHIELD_DEFROST.name

    val temperature = climateWholeNumber(status.acSetting)
    val windLevel = climateWholeNumber(status.acAirVolume)
    return when {
        temperature == 18 && windLevel == 7 && status.climateMode == 1 ->
            ClimatePresetAction.QUICK_COOL.name
        temperature == 32 && windLevel == 7 && status.climateMode == 3 ->
            ClimatePresetAction.QUICK_HEAT.name
        temperature == 24 && windLevel == 7 && status.climateMode == 4 && status.recirculationMode == 0 ->
            ClimatePresetAction.DEODORIZE.name
        else -> null
    }
}

private fun circulationModeLabel(status: VehicleStatus): String? =
    AirCircle.fromTelemetryValue(status.recirculationMode)?.displayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClimateControlBottomSheet(
    onDismissRequest: () -> Unit,
    status: VehicleStatus?,
    liveWeather: com.leapauto.app.weather.LiveWeather? = null,
    busy: Boolean = false,
    hvacCapability: HvacCapability = HvacCapability.fallback(),
    vehicleModel: String = "",
    onControl: (String) -> Unit,
    onApplyClimateSettings: (AirConditioningCommand) -> Unit,
    controlFeedback: ControlFeedback? = null,
    onDismissControlFeedback: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coroutineScope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // 解除全局 busy 锁定：只要有车辆数据，面板按键随时响应触控，实现极致跟手性
    val actionsEnabled = status != null
    val fanRange = hvacCapability.effectiveUiFanRange()
    val currentSettingTemp = climateWholeNumber(status?.acSetting)
        ?.coerceIn(hvacCapability.temperatureMinC, hvacCapability.temperatureMaxC)
        ?: 24.coerceIn(hvacCapability.temperatureMinC, hvacCapability.temperatureMaxC)
    val currentSettingWind = climateWholeNumber(status?.acAirVolume)
        ?.coerceIn(fanRange)
        ?: 3.coerceIn(fanRange)
    val currentDefrost = status?.windshieldDefrost ?: false
    val currentCircle = AirCircle.fromTelemetryValue(status?.recirculationMode) ?: AirCircle.INNER
    val currentOutlet = if (currentDefrost) AirOutlet.WINDSHIELD.name else AirOutlet.ALL.name

    // 动态依据车辆最新车况实时回显空调设置
    var editedTemperature by remember(status?.acSetting) {
        mutableStateOf(currentSettingTemp)
    }
    var editedWindLevel by remember(status?.acAirVolume) {
        mutableStateOf(currentSettingWind)
    }
    var editedDefogging by remember(status?.windshieldDefrost) {
        mutableStateOf(currentDefrost)
    }
    var editedOutletName by remember(status?.windshieldDefrost) {
        mutableStateOf(currentOutlet)
    }
    var editedCircle by remember(status?.recirculationMode) {
        mutableStateOf(currentCircle)
    }
    var userLastActionEpochMs by remember { mutableLongStateOf(0L) }
    var pendingTemperatureJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(
        status?.acSetting,
        status?.acAirVolume,
        status?.windshieldDefrost,
        status?.recirculationMode,
        hvacCapability
    ) {
        // 若车主在 8 秒内刚手动操作过，保护本地状态不被未生效的车辆旧遥测覆盖冲刷
        val isProtected = System.currentTimeMillis() - userLastActionEpochMs < 8000L
        if (!isProtected) {
            editedTemperature = currentSettingTemp
            editedWindLevel = currentSettingWind
            editedDefogging = currentDefrost
            editedOutletName = currentOutlet
            editedCircle = currentCircle
        }
    }

    fun currentCommand(operation: HvacOperation, actionDescription: String? = null): AirConditioningCommand {
        return AirConditioningCommand(
            operation = operation,
            temperatureC = editedTemperature,
            capability = hvacCapability,
            windLevel = editedWindLevel,
            circle = editedCircle,
            windshieldDefogging = editedDefogging,
            outlet = AirOutlet.valueOf(editedOutletName),
            customLabel = actionDescription
        )
    }

    fun restoreEditedClimateFromStatus() {
        editedTemperature = currentSettingTemp
        editedWindLevel = currentSettingWind
        editedDefogging = currentDefrost
        editedOutletName = currentOutlet
        editedCircle = currentCircle
        userLastActionEpochMs = 0L
        pendingTemperatureJob?.cancel()
    }

    fun submitSettings(operation: HvacOperation, actionDescription: String? = null) {
        if (!actionsEnabled) return
        val command = currentCommand(operation, actionDescription)
        onApplyClimateSettings(command)
    }

    var optimisticAcSwitch by remember(status?.acSwitch) {
        mutableStateOf(status?.acSwitch)
    }
    var optimisticPresetName by remember(status?.climateMode, status?.windshieldDefrost) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(controlFeedback?.kind, controlFeedback?.message) {
        if (controlFeedback?.kind == ControlFeedbackKind.SUCCESS ||
            controlFeedback?.kind == ControlFeedbackKind.ERROR
        ) {
            // A terminal result must clear the local preset highlight even if
            // its telemetry enum did not change and Compose cannot use it as a key.
            optimisticPresetName = null
            val terminalFailure = controlFeedback.kind == ControlFeedbackKind.ERROR
            if (terminalFailure) {
                optimisticAcSwitch = status?.acSwitch
                restoreEditedClimateFromStatus()
            }
        }
    }

    val isAcOn = optimisticAcSwitch == true
    val activePresetName = optimisticPresetName ?: activeClimatePresetName(status)
    val isDefrostActive = activePresetName == ClimatePresetAction.WINDSHIELD_DEFROST.name ||
        editedDefogging || status?.windshieldDefrost == true
    val temperatureTarget = Commands.acTemperatureTarget(status?.acSetting)
    val indoorTemp = climateWholeNumber(status?.indoorTemp)
    val climateTone = when {
        activePresetName == ClimatePresetAction.QUICK_COOL.name -> ClimateTemperatureTone.COOLING
        activePresetName == ClimatePresetAction.QUICK_HEAT.name -> ClimateTemperatureTone.HEATING
        isDefrostActive -> ClimateTemperatureTone.COOLING
        status?.acCoolingAndHeating == 1 -> ClimateTemperatureTone.COOLING
        status?.acCoolingAndHeating == 2 -> ClimateTemperatureTone.HEATING
        indoorTemp != null && editedTemperature < indoorTemp -> ClimateTemperatureTone.COOLING
        indoorTemp != null && editedTemperature > indoorTemp -> ClimateTemperatureTone.HEATING
        else -> ClimateTemperatureTone.COOLING
    }
    val activeAcModeText = when {
        !isAcOn -> "空调已关闭"
        isDefrostActive -> "前挡除霜"
        activePresetName == ClimatePresetAction.QUICK_COOL.name -> "极速降温"
        activePresetName == ClimatePresetAction.QUICK_HEAT.name -> "极速升温"
        activePresetName == ClimatePresetAction.DEODORIZE.name -> "快速除味"
        climateTone == ClimateTemperatureTone.COOLING -> "强劲制冷"
        climateTone == ClimateTemperatureTone.HEATING -> "舒暖制热"
        climateTone == ClimateTemperatureTone.VENTILATION -> "自然通风"
        else -> "运行中"
    }
    val activeAcIcon = when {
        !isAcOn -> R.drawable.ic_phosphor_snowflake
        isDefrostActive -> R.drawable.ic_windshield_defrost
        activePresetName == ClimatePresetAction.QUICK_COOL.name -> R.drawable.ic_phosphor_snowflake
        activePresetName == ClimatePresetAction.QUICK_HEAT.name -> R.drawable.ic_phosphor_sun
        climateTone == ClimateTemperatureTone.HEATING -> R.drawable.ic_phosphor_sun
        else -> R.drawable.ic_phosphor_fan
    }
    val activeAcColor = when {
        isDefrostActive -> MaterialTheme.colorScheme.primary
        activePresetName == ClimatePresetAction.QUICK_COOL.name -> MaterialTheme.colorScheme.primary
        activePresetName == ClimatePresetAction.QUICK_HEAT.name -> MaterialTheme.statusWarn
        climateTone == ClimateTemperatureTone.COOLING -> MaterialTheme.colorScheme.primary
        climateTone == ClimateTemperatureTone.HEATING -> MaterialTheme.statusWarn
        climateTone == ClimateTemperatureTone.VENTILATION -> MaterialTheme.statusGood
        else -> MaterialTheme.colorScheme.primary
    }
    val currentAcColor = if (isAcOn) activeAcColor else MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val fullSheetHeight = maxHeight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fullSheetHeight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    // 1. 顶部标题栏：图标 + 标题 + 状态标签 + 一键开关胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = currentAcColor.copy(alpha = if (isAcOn) 0.15f else 0.08f),
                        border = BorderStroke(0.8.dp, currentAcColor.copy(alpha = if (isAcOn) 0.5f else 0.25f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(activeAcIcon),
                                contentDescription = null,
                                tint = currentAcColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = activeAcModeText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isAcOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val outdoorTemp = liveWeather?.temperature?.takeIf { it.isNotBlank() }
                        val indoorTempStr = status?.indoorTemp ?: "--"
                        val tempSubtitle = if (outdoorTemp != null) {
                            "车内 $indoorTempStr · 室外 $outdoorTemp℃ (${liveWeather.weather})"
                        } else {
                            "车内 $indoorTempStr"
                        }
                        Text(
                            text = tempSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 一键开启/关闭快捷触控胶囊
                Surface(
                    shape = CircleShape,
                    color = if (isAcOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.glassInsetSurface,
                    border = BorderStroke(
                        0.8.dp,
                        if (isAcOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = actionsEnabled) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            userLastActionEpochMs = System.currentTimeMillis()
                            val nextOn = !isAcOn
                            optimisticAcSwitch = nextOn
                            if (!nextOn) optimisticPresetName = null
                            if (isAcOn) onControl("acOff") else onControl("acOn")
                        }
                ) {
                    Text(
                        text = if (isAcOn) "关闭" else "开启",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isAcOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }

            // 1.5 场景化「一键智能舒享」建议芯片 (烈日暴晒/雨雪潮湿/严寒升温)
            val smartSuggestion = remember(status?.indoorTemp, liveWeather, isAcOn, optimisticPresetName) {
                com.leapauto.app.weather.ClimateSmartRecommendationPolicy.resolve(
                    indoorTempStr = status?.indoorTemp,
                    weather = liveWeather,
                    isAcOn = isAcOn,
                    activePreset = optimisticPresetName
                )
            }
            if (smartSuggestion != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = smartSuggestion.accentColor.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, smartSuggestion.accentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painter = painterResource(smartSuggestion.iconRes),
                                contentDescription = null,
                                tint = smartSuggestion.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = smartSuggestion.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = smartSuggestion.accentColor
                                )
                                Text(
                                    text = smartSuggestion.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = smartSuggestion.accentColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, smartSuggestion.accentColor.copy(alpha = 0.50f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    userLastActionEpochMs = System.currentTimeMillis()
                                    optimisticAcSwitch = true
                                    optimisticPresetName = smartSuggestion.command
                                    onControl(smartSuggestion.command)
                                }
                        ) {
                            Text(
                                text = smartSuggestion.actionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = smartSuggestion.accentColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.5.dp)
                            )
                        }
                    }
                }
            }

            // 2. 全息光环温控主盘 (38sp 超大字 + 两侧 44dp 舒适步进触控 + 极光光晕)
            val climateRingAura = if (isAcOn) currentAcColor.copy(alpha = 0.22f) else null
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, if (isAcOn) currentAcColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(20.dp),
                        auraColor = climateRingAura,
                        auraCenter = Offset(0.5f, 0.4f),
                        auraRadiusRatio = 0.65f
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.glassSurface,
                        border = BorderStroke(1.dp, if (isAcOn) currentAcColor.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = actionsEnabled && editedTemperature > hvacCapability.temperatureMinC) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                userLastActionEpochMs = System.currentTimeMillis()
                                optimisticAcSwitch = true
                                optimisticPresetName = null
                                editedDefogging = false
                                editedOutletName = AirOutlet.ALL.name
                                editedTemperature = (editedTemperature - 1).coerceAtLeast(hvacCapability.temperatureMinC)
                                pendingTemperatureJob?.cancel()
                                pendingTemperatureJob = coroutineScope.launch {
                                    kotlinx.coroutines.delay(400L)
                                    submitSettings(HvacOperation.ON, "温度已调至 ${editedTemperature}°C")
                                }
                            }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (actionsEnabled && editedTemperature > hvacCapability.temperatureMinC) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "$editedTemperature",
                                fontSize = 38.sp,
                                lineHeight = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentAcColor
                            )
                            Text(
                                text = "°C",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = currentAcColor.copy(alpha = 0.85f),
                                modifier = Modifier.padding(start = 2.dp, top = 3.dp)
                            )
                        }
                        Text(
                            text = "设定温度",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.glassSurface,
                        border = BorderStroke(1.dp, if (isAcOn) currentAcColor.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = actionsEnabled && editedTemperature < hvacCapability.temperatureMaxC) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                userLastActionEpochMs = System.currentTimeMillis()
                                optimisticAcSwitch = true
                                optimisticPresetName = null
                                editedDefogging = false
                                editedOutletName = AirOutlet.ALL.name
                                editedTemperature = (editedTemperature + 1).coerceAtMost(hvacCapability.temperatureMaxC)
                                pendingTemperatureJob?.cancel()
                                pendingTemperatureJob = coroutineScope.launch {
                                    kotlinx.coroutines.delay(400L)
                                    submitSettings(HvacOperation.ON, "温度已调至 ${editedTemperature}°C")
                                }
                            }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (actionsEnabled && editedTemperature < hvacCapability.temperatureMaxC) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    }
                }
            }

            // 3. 高频快捷微晶金刚键 (4 列均分：极速降温 / 一键制热 / 前挡除霜 / 内外循环)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isCoolActive = activePresetName == ClimatePresetAction.QUICK_COOL.name
                QuickClimateCircleAction(
                    icon = R.drawable.ic_phosphor_snowflake,
                    label = "极速降温",
                    isActive = isCoolActive,
                    activeColor = MaterialTheme.colorScheme.primary,
                    enabled = actionsEnabled,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        userLastActionEpochMs = System.currentTimeMillis()
                        optimisticAcSwitch = true
                        optimisticPresetName = ClimatePresetAction.QUICK_COOL.name
                        editedDefogging = false
                        editedOutletName = AirOutlet.ALL.name
                        editedCircle = AirCircle.INNER
                        editedWindLevel = 7
                        editedTemperature = 18
                        onControl("quickCool")
                    },
                    modifier = Modifier.weight(1f)
                )

                val isHeatActive = activePresetName == ClimatePresetAction.QUICK_HEAT.name
                QuickClimateCircleAction(
                    icon = R.drawable.ic_phosphor_sun,
                    label = "一键制热",
                    isActive = isHeatActive,
                    activeColor = MaterialTheme.statusWarn,
                    enabled = actionsEnabled,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        userLastActionEpochMs = System.currentTimeMillis()
                        optimisticAcSwitch = true
                        optimisticPresetName = ClimatePresetAction.QUICK_HEAT.name
                        editedDefogging = false
                        editedOutletName = AirOutlet.ALL.name
                        editedCircle = AirCircle.INNER
                        editedWindLevel = 7
                        editedTemperature = 32
                        onControl("quickHeat")
                    },
                    modifier = Modifier.weight(1f)
                )

                val isDefrostActive = activePresetName == ClimatePresetAction.WINDSHIELD_DEFROST.name || editedDefogging || status?.windshieldDefrost == true
                QuickClimateCircleAction(
                    icon = R.drawable.ic_windshield_defrost,
                    label = "前挡除霜",
                    isActive = isDefrostActive,
                    activeColor = MaterialTheme.colorScheme.primary,
                    enabled = actionsEnabled,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        userLastActionEpochMs = System.currentTimeMillis()
                        optimisticAcSwitch = true
                        optimisticPresetName = ClimatePresetAction.WINDSHIELD_DEFROST.name
                        editedDefogging = true
                        editedOutletName = AirOutlet.WINDSHIELD.name
                        editedCircle = AirCircle.OUTER
                        editedWindLevel = 5
                        editedTemperature = 24
                        onControl("defrost")
                    },
                    modifier = Modifier.weight(1f)
                )

                val isInner = editedCircle == AirCircle.INNER
                QuickClimateCircleAction(
                    icon = R.drawable.ic_phosphor_arrow_clockwise,
                    label = if (isInner) "内循环" else "外循环",
                    isActive = isInner,
                    activeColor = MaterialTheme.colorScheme.primary,
                    enabled = actionsEnabled,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        userLastActionEpochMs = System.currentTimeMillis()
                        optimisticAcSwitch = true
                        val targetCircle = if (isInner) AirCircle.OUTER else AirCircle.INNER
                        editedCircle = targetCircle
                        submitSettings(
                            HvacOperation.ON,
                            if (targetCircle == AirCircle.INNER) "已切换至内循环" else "已切换至外循环"
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // 4. 完整空调设置界面 (风量滑块 / 出风方向 / 座椅舒适度)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 风量调节
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("风量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$editedWindLevel 挡", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (fanRange.first < fanRange.last) {
                        ThinClimateSlider(
                            value = editedWindLevel.toFloat(),
                            onValueChange = {
                                userLastActionEpochMs = System.currentTimeMillis()
                                editedWindLevel = it.roundToInt().coerceIn(fanRange)
                            },
                            onValueChangeFinished = {
                                userLastActionEpochMs = System.currentTimeMillis()
                                optimisticAcSwitch = true
                                optimisticPresetName = null
                                submitSettings(HvacOperation.ON, "风量已调至 ${editedWindLevel}挡")
                            },
                            onValueChangeCancelled = {
                                restoreEditedClimateFromStatus()
                            },
                            enabled = actionsEnabled,
                            valueRange = fanRange.first.toFloat()..fanRange.last.toFloat(),
                            steps = (fanRange.last - fanRange.first - 1).coerceAtLeast(0),
                            modifier = Modifier.fillMaxWidth()
                        )
                        } else {
                            Text("固定挡位", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // 出风方向
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("出风方向", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (editedOutletName == AirOutlet.ALL.name) "全车出风" else "前风挡出风",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = editedOutletName == AirOutlet.ALL.name,
                                onClick = {
                                    userLastActionEpochMs = System.currentTimeMillis()
                                    optimisticAcSwitch = true
                                    optimisticPresetName = null
                                    editedOutletName = AirOutlet.ALL.name
                                    editedDefogging = false
                                    submitSettings(HvacOperation.ON, "已切换至全车出风")
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = {},
                                label = { Text("全车出风", style = MaterialTheme.typography.labelSmall) }
                            )
                            SegmentedButton(
                                selected = editedOutletName == AirOutlet.WINDSHIELD.name,
                                onClick = {
                                    userLastActionEpochMs = System.currentTimeMillis()
                                    optimisticAcSwitch = true
                                    optimisticPresetName = ClimatePresetAction.WINDSHIELD_DEFROST.name
                                    editedOutletName = AirOutlet.WINDSHIELD.name
                                    editedDefogging = true
                                    submitSettings(HvacOperation.ON, "前挡除霜已开启")
                                },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {},
                            label = { Text("前风挡出风", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // 座椅舒适度控制卡片
                SeatComfortControlCard(
                    status = status,
                    enabled = actionsEnabled,
                    onControl = onControl,
                    vehicleModel = vehicleModel
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // 悬浮居中 HUD 胶囊：绝对层叠浮动在顶部，零像素推挤内容，彻底根治高度抖动与抽屉回缩
        controlFeedback?.let { feedback ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                HeroControlHudPill(feedback, onDismissControlFeedback)
            }
        }
    }
}
}
}

@Composable
private fun QuickClimateCircleAction(
    icon: Int,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "climate-button-press"
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) activeColor.copy(alpha = 0.16f) else MaterialTheme.glassInsetSurface,
        border = BorderStroke(
            0.8.dp,
            if (isActive) activeColor.copy(alpha = 0.50f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
        ),
        shadowElevation = 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClimateControlContent(
    status: VehicleStatus?,
    statusError: String,
    busy: Boolean,
    controlFeedback: ControlFeedback?,
    statusUpdatedAtEpochMs: Long,
    hvacCapability: HvacCapability,
    vehicleModel: String = "",
    onDismissRequest: () -> Unit = {},
    onDismissControlFeedback: () -> Unit,
    onRefresh: () -> Unit,
    onControl: (String) -> Unit,
    onApplyClimateSettings: (AirConditioningCommand) -> Unit
) {
    ClimateControlBottomSheet(
        onDismissRequest = onDismissRequest,
        status = status,
        busy = busy,
        hvacCapability = hvacCapability,
        vehicleModel = vehicleModel,
        onControl = onControl,
        onApplyClimateSettings = onApplyClimateSettings,
        controlFeedback = controlFeedback,
        onDismissControlFeedback = onDismissControlFeedback
    )
}

@Composable
fun SeatComfortControlCard(
    status: VehicleStatus?,
    enabled: Boolean,
    onControl: (String) -> Unit,
    modifier: Modifier = Modifier,
    vehicleModel: String = ""
) {
    val effectiveModel = status?.carType ?: vehicleModel
    val supportsRearSeats = RearSeatComfortPolicy.supportsRearSeats(effectiveModel)

    // 硬件能力检测（信号存在即具备硬件，不支持项自适应隐藏）
    val hasDriverHeating = status == null || status.driverSeatHeating != null
    val hasDriverVent = status == null || status.driverSeatVentilation != null
    val hasPassengerHeating = status == null || status.passengerSeatHeating != null
    val hasPassengerVent = status == null || status.passengerSeatVentilation != null
    val hasFrontRow = hasDriverHeating || hasDriverVent || hasPassengerHeating || hasPassengerVent

    val hasLeftRearHeating = supportsRearSeats && status?.leftRearSeatHeating != null
    val hasLeftRearVent = supportsRearSeats && status?.leftRearSeatVentilation != null
    val hasRightRearHeating = supportsRearSeats && status?.rightRearSeatHeating != null
    val hasRightRearVent = supportsRearSeats && status?.rightRearSeatVentilation != null
    val hasRearRow = supportsRearSeats && (hasLeftRearHeating || hasLeftRearVent || hasRightRearHeating || hasRightRearVent)

    val hasSteerHeating = status == null || status.steeringWheelHeating != null || status.steeringWheelHeatingLevel != null
    val hasMirrorHeating = status == null || status.rearviewMirrorHeating != null
    val hasAnyComfort = hasFrontRow || hasRearRow || hasSteerHeating || hasMirrorHeating

    if (!hasAnyComfort) return

    var userTouchEpochMs by remember { mutableLongStateOf(0L) }
    var driverHeating by remember { mutableStateOf((status?.driverSeatHeating ?: 0) > 0) }
    var driverVent by remember { mutableStateOf((status?.driverSeatVentilation ?: 0) > 0) }
    var passengerHeating by remember { mutableStateOf((status?.passengerSeatHeating ?: 0) > 0) }
    var passengerVent by remember { mutableStateOf((status?.passengerSeatVentilation ?: 0) > 0) }
    var leftRearHeating by remember { mutableStateOf((status?.leftRearSeatHeating ?: 0) > 0) }
    var leftRearVent by remember { mutableStateOf((status?.leftRearSeatVentilation ?: 0) > 0) }
    var rightRearHeating by remember { mutableStateOf((status?.rightRearSeatHeating ?: 0) > 0) }
    var rightRearVent by remember { mutableStateOf((status?.rightRearSeatVentilation ?: 0) > 0) }
    var steerHeating by remember { mutableStateOf((status?.steeringWheelHeatingLevel ?: if (status?.steeringWheelHeating == true) 2 else 0) > 0) }
    var mirrorHeating by remember { mutableStateOf(status?.rearviewMirrorHeating == true) }

    LaunchedEffect(
        status?.driverSeatHeating,
        status?.driverSeatVentilation,
        status?.passengerSeatHeating,
        status?.passengerSeatVentilation,
        status?.leftRearSeatHeating,
        status?.leftRearSeatVentilation,
        status?.rightRearSeatHeating,
        status?.rightRearSeatVentilation,
        status?.steeringWheelHeating,
        status?.steeringWheelHeatingLevel,
        status?.rearviewMirrorHeating
    ) {
        val isProtected = System.currentTimeMillis() - userTouchEpochMs < 15_000L
        if (!isProtected) {
            driverHeating = (status?.driverSeatHeating ?: 0) > 0
            driverVent = (status?.driverSeatVentilation ?: 0) > 0
            passengerHeating = (status?.passengerSeatHeating ?: 0) > 0
            passengerVent = (status?.passengerSeatVentilation ?: 0) > 0
            leftRearHeating = (status?.leftRearSeatHeating ?: 0) > 0
            leftRearVent = (status?.leftRearSeatVentilation ?: 0) > 0
            rightRearHeating = (status?.rightRearSeatHeating ?: 0) > 0
            rightRearVent = (status?.rightRearSeatVentilation ?: 0) > 0
            steerHeating = (status?.steeringWheelHeatingLevel ?: if (status?.steeringWheelHeating == true) 2 else 0) > 0
            mirrorHeating = status?.rearviewMirrorHeating == true
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .frostedGlassCard(shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_sun),
                    contentDescription = "座舱舒适",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "座椅与座舱舒适",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // ====== 1. 第一排：主驾与副驾并排双舱 ======
            if (hasFrontRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 主驾驶舱
                    if (hasDriverHeating || hasDriverVent) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.glassInsetSurface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "主驾驶位",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (hasDriverHeating) {
                                    SeatComfortTogglePill(
                                        icon = R.drawable.ic_phosphor_sun,
                                        label = "座椅加热",
                                        isActive = driverHeating,
                                        activeColor = MaterialTheme.statusWarn,
                                        enabled = enabled,
                                        onToggle = {
                                            userTouchEpochMs = System.currentTimeMillis()
                                            driverHeating = it
                                            onControl(if (it) "driverSeatHeating_2" else "driverSeatHeating_0")
                                        }
                                    )
                                }
                                if (hasDriverVent) {
                                    SeatComfortTogglePill(
                                        icon = R.drawable.ic_phosphor_snowflake,
                                        label = "座椅通风",
                                        isActive = driverVent,
                                        activeColor = MaterialTheme.colorScheme.primary,
                                        enabled = enabled,
                                        onToggle = {
                                            userTouchEpochMs = System.currentTimeMillis()
                                            driverVent = it
                                            onControl(if (it) "driverSeatVentilation_2" else "driverSeatVentilation_0")
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 副驾驶舱
                    if (hasPassengerHeating || hasPassengerVent) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.glassInsetSurface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "副驾驶位",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (hasPassengerHeating) {
                                    SeatComfortTogglePill(
                                        icon = R.drawable.ic_phosphor_sun,
                                        label = "座椅加热",
                                        isActive = passengerHeating,
                                        activeColor = MaterialTheme.statusWarn,
                                        enabled = enabled,
                                        onToggle = {
                                            userTouchEpochMs = System.currentTimeMillis()
                                            passengerHeating = it
                                            onControl(if (it) "passengerSeatHeating_2" else "passengerSeatHeating_0")
                                        }
                                    )
                                }
                                if (hasPassengerVent) {
                                    SeatComfortTogglePill(
                                        icon = R.drawable.ic_phosphor_snowflake,
                                        label = "座椅通风",
                                        isActive = passengerVent,
                                        activeColor = MaterialTheme.colorScheme.primary,
                                        enabled = enabled,
                                        onToggle = {
                                            userTouchEpochMs = System.currentTimeMillis()
                                            passengerVent = it
                                            onControl(if (it) "passengerSeatVentilation_2" else "passengerSeatVentilation_0")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ====== 2. 第二排：左后与右后并排双舱（支持车型自适应展开） ======
            if (hasRearRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 二排左（主驾后）
                    if (hasLeftRearHeating || hasLeftRearVent) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.glassInsetSurface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "二排左座 (主驾后)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (hasLeftRearHeating) {
                                    SeatComfortTogglePill(
                                        icon = R.drawable.ic_phosphor_sun,
                                        label = "座椅加热",
                                        isActive = leftRearHeating,
                                        activeColor = MaterialTheme.statusWarn,
                                        enabled = enabled,
                                        onToggle = {
                                            userTouchEpochMs = System.currentTimeMillis()
                                            leftRearHeating = it
                                            onControl(if (it) "leftRearSeatHeating_2" else "leftRearSeatHeating_0")
                                        }
                                    )
                                }
                                if (hasLeftRearVent) {
                                    SeatComfortTogglePill(
                                        icon = R.drawable.ic_phosphor_snowflake,
                                        label = "座椅通风",
                                        isActive = leftRearVent,
                                        activeColor = MaterialTheme.colorScheme.primary,
                                        enabled = enabled,
                                        onToggle = {
                                            userTouchEpochMs = System.currentTimeMillis()
                                            leftRearVent = it
                                            onControl(if (it) "leftRearSeatVentilation_2" else "leftRearSeatVentilation_0")
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 二排右（副驾后）
                    if (hasRightRearHeating || hasRightRearVent) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.glassInsetSurface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "二排右座 (副驾后)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (hasRightRearHeating) {
                                    SeatComfortTogglePill(
                                        icon = R.drawable.ic_phosphor_sun,
                                        label = "座椅加热",
                                        isActive = rightRearHeating,
                                        activeColor = MaterialTheme.statusWarn,
                                        enabled = enabled,
                                        onToggle = {
                                            userTouchEpochMs = System.currentTimeMillis()
                                            rightRearHeating = it
                                            onControl(if (it) "rightRearSeatHeating_2" else "rightRearSeatHeating_0")
                                        }
                                    )
                                }
                                if (hasRightRearVent) {
                                    SeatComfortTogglePill(
                                        icon = R.drawable.ic_phosphor_snowflake,
                                        label = "座椅通风",
                                        isActive = rightRearVent,
                                        activeColor = MaterialTheme.colorScheme.primary,
                                        enabled = enabled,
                                        onToggle = {
                                            userTouchEpochMs = System.currentTimeMillis()
                                            rightRearVent = it
                                            onControl(if (it) "rightRearSeatVentilation_2" else "rightRearSeatVentilation_0")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ====== 3. 底部外设：方向盘加热 + 后视镜加热（条件化并排/满宽/隐藏） ======
            if (hasSteerHeating || hasMirrorHeating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasSteerHeating) {
                        Surface(
                            modifier = if (hasMirrorHeating) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.glassInsetSurface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SeatComfortTogglePill(
                                    icon = R.drawable.ic_phosphor_sun,
                                    label = "方向盘加热",
                                    isActive = steerHeating,
                                    activeColor = MaterialTheme.statusWarn,
                                    enabled = enabled,
                                    onToggle = {
                                        userTouchEpochMs = System.currentTimeMillis()
                                        steerHeating = it
                                        onControl(if (it) "steeringWheelHeating_2" else "steeringWheelHeating_0")
                                    }
                                )
                            }
                        }
                    }

                    if (hasMirrorHeating) {
                        Surface(
                            modifier = if (hasSteerHeating) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.glassInsetSurface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SeatComfortTogglePill(
                                    icon = R.drawable.ic_phosphor_sun,
                                    label = "后视镜加热",
                                    isActive = mirrorHeating,
                                    activeColor = MaterialTheme.statusWarn,
                                    enabled = enabled,
                                    onToggle = {
                                        userTouchEpochMs = System.currentTimeMillis()
                                        mirrorHeating = it
                                        onControl(if (it) "rearviewMirrorHeating_on" else "rearviewMirrorHeating_off")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatComfortTogglePill(
    icon: Int,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) activeColor.copy(alpha = 0.16f) else MaterialTheme.glassSurface,
        border = BorderStroke(
            0.8.dp,
            if (isActive) activeColor.copy(alpha = 0.50f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onToggle(!isActive)
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun ThinClimateSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onValueChangeCancelled: () -> Unit = {},
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnValueChangeCancelled by rememberUpdatedState(onValueChangeCancelled)
    val thumbRadiusPx = with(LocalDensity.current) { 10.dp.toPx() }
    val total = valueRange.endInclusive - valueRange.start
    val fraction = if (total <= 0f) 0f else ((value - valueRange.start) / total).coerceIn(0f, 1f)
    val enabledColor = MaterialTheme.colorScheme.primary
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val trackColor = if (enabled) enabledColor else disabledColor
    val trackBackground = if (enabled) {
        if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }
    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .pointerInput(valueRange, steps, enabled, thumbRadiusPx) {
                if (!enabled) return@pointerInput
                fun snap(x: Float, width: Int): Float {
                    val f = ClimateSliderMapping.fractionAt(
                        pointerX = x,
                        widthPx = width.toFloat(),
                        thumbRadiusPx = thumbRadiusPx
                    )
                    val raw = valueRange.start + f * total
                    return if (steps > 0) {
                        val stepSize = total / (steps + 1)
                        val snapped = valueRange.start + ((raw - valueRange.start) / stepSize).roundToInt() * stepSize
                        snapped.coerceIn(valueRange.start, valueRange.endInclusive)
                    } else {
                        raw.coerceIn(valueRange.start, valueRange.endInclusive)
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset -> latestOnValueChange(snap(offset.x, size.width)) },
                    onHorizontalDrag = { change, _ ->
                        latestOnValueChange(snap(change.position.x, size.width))
                    },
                    onDragEnd = { latestOnValueChangeFinished() },
                    onDragCancel = { latestOnValueChangeCancelled() }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.Center)
        ) {
            val trackY = size.height / 2f
            val trackHeightPx = 3.5.dp.toPx()
            val trackTop = trackY - trackHeightPx / 2f
            val thumbRadius = 10.dp.toPx()
            val trackStart = thumbRadius
            val trackEnd = size.width - thumbRadius
            val thumbX = trackStart + fraction * (trackEnd - trackStart)
            val corner = CornerRadius(trackHeightPx / 2f)
            drawRoundRect(
                color = trackBackground,
                topLeft = Offset(trackStart, trackTop),
                size = Size(trackEnd - trackStart, trackHeightPx),
                cornerRadius = corner
            )
            if (thumbX > trackStart) {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(trackStart, trackTop),
                    size = Size(thumbX - trackStart, trackHeightPx),
                    cornerRadius = corner
                )
            }
            drawCircle(
                color = trackColor,
                radius = thumbRadius,
                center = Offset(thumbX, trackY)
            )
        }
    }
}

@Composable
fun ClimateSegmentedSetting(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val presetIcon = when (option.first) {
                    ClimatePresetAction.QUICK_COOL.name -> R.drawable.ic_phosphor_snowflake
                    ClimatePresetAction.WINDSHIELD_DEFROST.name -> R.drawable.ic_windshield_defrost
                    ClimatePresetAction.QUICK_HEAT.name -> R.drawable.ic_phosphor_sun
                    ClimatePresetAction.DEODORIZE.name -> R.drawable.ic_phosphor_fan
                    else -> null
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = { onSelected(option.first) },
                    selected = selected == option.first,
                    enabled = enabled,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        activeContentColor = MaterialTheme.colorScheme.primary,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    icon = {},
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (presetIcon != null) {
                                Icon(
                                    painter = painterResource(presetIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(option.second, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                        }
                    }
                )
            }
        }
    }
}

fun climateWholeNumber(value: String?): Int? = value
    ?.trim()
    ?.replace(Regex("\\s*°?\\s*C\\s*$", RegexOption.IGNORE_CASE), "")
    ?.trim()
    ?.toBigDecimalOrNull()
    ?.stripTrailingZeros()
    ?.takeIf { it.scale() <= 0 }
    ?.let { runCatching { it.intValueExact() }.getOrNull() }

fun displayClimateFanLevel(value: String?): String =
    climateWholeNumber(value)?.takeIf { it in 1..7 }?.let { "$it 档" } ?: "--"

@Composable
fun ClimateStatusItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

private fun climateBooleanLabel(value: Boolean?): String = when (value) {
    true -> "已开启"
    false -> "已关闭"
    null -> "未同步"
}

fun displayClimateTemperature(target: AcTemperatureTarget): String = "${target.value} °C"

fun displayClimateTemperature(temperature: Int): String = "$temperature °C"

fun TextStyle.energyStyle(): TextStyle = copy(letterSpacing = 0.sp)

fun displayEnergyMetric(metric: com.leapauto.app.EnergyMetric?, fallbackUnit: String? = null): String {
    if (metric == null) return "--"
    val unit = metric.unit ?: fallbackUnit
    return if (unit != null) "${metric.value} $unit" else metric.value
}

@Composable
fun energyCompositionColor(label: String, index: Int): Color = when (label.trim()) {
    "行车", "驾驶" -> MaterialTheme.statusGood
    "空调" -> MaterialTheme.colorScheme.primary
    "其他" -> MaterialTheme.statusWarn
    else -> listOf(
        MaterialTheme.statusGood,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.statusWarn,
        MaterialTheme.colorScheme.tertiary
    )[index % 4]
}

fun Double.formatEnergyNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.1f", this)

@Composable
fun PowerPagerAutoPlayCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.glassSurface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "轮播卡片自动播放",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "开启后首页能耗与电量卡片将自动轮播",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@Composable
fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.energyStyle(), fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun PinCard(
    pinSaved: Boolean,
    pin: String,
    onPinChange: (String) -> Unit,
    onSavePin: () -> Unit,
    initialSetupInProgress: Boolean,
    onCancelInitialSetup: () -> Unit
) {
    var editingPin by rememberSaveable(pinSaved) { mutableStateOf(!pinSaved || initialSetupInProgress) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.glassSurface,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "操控密码",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (pinSaved) "已设置（用于控车指令加密）" else "未设置（用于控车指令加密）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (pinSaved && !editingPin) {
                    TextButton(onClick = { editingPin = true }) {
                        Text("修改")
                    }
                }
            }
            if (editingPin) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = { Text("4 位数字操控密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pinSaved || initialSetupInProgress) {
                        OutlinedButton(
                            onClick = {
                                if (initialSetupInProgress) {
                                    onCancelInitialSetup()
                                } else {
                                    editingPin = false
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = neutralOutlinedButtonColors(),
                            border = neutralButtonBorder()
                        ) {
                            Text(if (initialSetupInProgress) "取消设置" else "取消")
                        }
                    }
                    Button(
                        onClick = {
                            onSavePin()
                            editingPin = false
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = neutralButtonColors(),
                        enabled = pin.matches(Regex("\\d{4}"))
                    ) {
                        Text(if (pinSaved) "更新密码" else "保存密码")
                    }
                }
            }
        }
    }
}

@Composable
fun neutralButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.onSurface,
    contentColor = MaterialTheme.colorScheme.surface
)

@Composable
fun neutralOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun neutralTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun neutralButtonBorder() = androidx.compose.foundation.BorderStroke(
    1.dp,
    MaterialTheme.colorScheme.outlineVariant
)

