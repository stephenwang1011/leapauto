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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import android.graphics.Bitmap
import com.leapauto.app.ChassisParkingPhoto
import com.leapauto.app.ParkingPhotoLoadState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    Cmd("windowOpen", "车窗半开", R.drawable.ic_phosphor_wind),
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
    RECENT_MILEAGE,
    SUMMARY,
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
    widgetOpacity: Int,
    widgetSensitiveActionVerificationEnabled: Boolean,
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
    handledUpdateVersion: String? = null,
    showAuthorSupportDialog: Boolean,
    showSessionExpiredDialog: Boolean,
    phone: String,
    onPhoneChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    onSendSms: () -> Unit,
    smsCountdownSeconds: Int = 0,
    geetestChallenge: GeetestChallenge? = null,
    onGeetestSuccess: (GeetestCaptchaResult) -> Unit = {},
    onDismissGeetest: () -> Unit = {},
    onLoginWithRawAuth: (String, String) -> Unit = { _, _ -> },
    onLogin: () -> Unit,
    onSavePin: () -> Unit,
    onCancelPinSetup: () -> Unit,
    onWidgetOpacityChange: (Int) -> Unit,
    onWidgetSensitiveActionVerificationChange: (Boolean) -> Unit,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onSaveVehicleConfig: (String, String, SessionStore.VehiclePowerType?, String, String) -> Unit = { _, _, _, _, _ -> },
    onNetworkDebugEnabledChange: (Boolean) -> Unit = {},
    onCheckForUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onDismissVersionUpdatePrompt: (String) -> Unit = {},
    onOpenVersionUpdatePrompt: (String) -> Unit = {},
    onDismissAuthorSupport: () -> Unit,
    onDisableAuthorSupport: () -> Unit,
    onOpenFeedback: () -> Unit,
    onSessionExpiredConfirmed: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshEnergy: () -> Unit = {},
    onAutoRefreshActiveChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onControl: (String) -> Unit,
    onApplyClimateSettings: (AirConditioningCommand) -> Unit = {},
    onDismissControlFeedback: () -> Unit,
    onQuickAc: (Int, Long) -> Unit = { _, _ -> },
    onSelectCustomVehicleImage: (Uri) -> Unit = {},
    onResetCustomVehicleImage: () -> Unit = {},
    onUpdateNickname: (String) -> Unit = {},
    bluetoothSettingsRequestId: Long = 0,
    onOpenBluetoothKey: () -> Unit = {},
    onFetchParkingPhoto: ((ChassisParkingPhoto?, Bitmap?) -> Unit) -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
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
            onDismiss = { onDismissVersionUpdatePrompt(release.versionName) },
            onUpdate = { onOpenVersionUpdatePrompt(release.versionName) }
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
            if (vehicleVin.isNotBlank()) VehicleImageCache.loadCachedBitmap(context, vehicleVin) else null
        }
        VehicleHealthCheckBottomSheet(
            status = status,
            vehicleAppearance = vehicleAppearance,
            vehicleNickname = vehicleConfig.nickname.ifBlank { vehicleDisplayModel },
            remoteBitmap = remoteBitmap,
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
                                    fontWeight = FontWeight.SemiBold
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
        AnimatedContent(
            targetState = destination,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                // A location detail contains a MapView while the home page can contain
                // another map preview. Sliding both full-screen surfaces together makes
                // edge-back visibly drop frames, so switch those routes immediately.
                if (initialState == ScreenDestination.LOCATION_DETAIL ||
                    targetState == ScreenDestination.LOCATION_DETAIL
                ) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val moveForward = targetState.navigationOrder > initialState.navigationOrder
                    val enterOffset = if (moveForward) 1 else -1
                    val animation = tween<IntOffset>(durationMillis = 160, easing = FastOutSlowInEasing)
                    val alphaAnimation = tween<Float>(durationMillis = 160, easing = FastOutSlowInEasing)
                    (slideInHorizontally(animationSpec = animation) { width -> width * enterOffset } +
                        fadeIn(animationSpec = alphaAnimation)).togetherWith(
                        slideOutHorizontally(animationSpec = animation) { width -> -width * enterOffset } +
                            fadeOut(animationSpec = alphaAnimation)
                    )
                }
            },
            label = "screen-navigation"
        ) { target ->
            when (target) {
                ScreenDestination.LOGIN -> LoginContent(
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
                ScreenDestination.HOME -> HomeContent(
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
                    activeControlCommand = activeControlCommand
                )
                ScreenDestination.LOCATION_DETAIL -> VehicleLocationDetailContent(
                    summary = status?.locationSummary,
                    locationSnapshot = locationSnapshot,
                    onHorn = onControl,
                )
                ScreenDestination.CLIMATE_CONTROL -> ClimateControlContent(
                    status = status,
                    statusError = statusError,
                    busy = busy,
                    controlFeedback = controlFeedback,
                    statusUpdatedAtEpochMs = statusUpdatedAtEpochMs,
                    hvacCapability = hvacCapability,
                    onDismissControlFeedback = onDismissControlFeedback,
                    onRefresh = onRefresh,
                    onControl = onControl,
                    onApplyClimateSettings = onApplyClimateSettings
                )
                ScreenDestination.ACCOUNT -> MyContent(
                    phone = phone,
                    pinSaved = pinSaved,
                    pin = pin,
                    onPinChange = onPinChange,
                    onSavePin = onSavePin,
                    pinSetupInProgress = pinSetupInProgress,
                    onCancelPinSetup = onCancelPinSetup,
                    widgetOpacity = widgetOpacity,
                    onWidgetOpacityChange = onWidgetOpacityChange,
                    widgetSensitiveActionVerificationEnabled = widgetSensitiveActionVerificationEnabled,
                    onWidgetSensitiveActionVerificationChange = onWidgetSensitiveActionVerificationChange,
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
                    onCheckForUpdate = onCheckForUpdate,
                    onOpenUpdate = onOpenUpdate,
                    availableVehicles = availableVehicles,
                    onSwitchVehicle = onSwitchVehicle,
                    vehicleVin = vehicleVin,
                    vehicleImageVersion = vehicleImageVersion,
                    onSelectCustomVehicleImage = onSelectCustomVehicleImage,
                    onResetCustomVehicleImage = onResetCustomVehicleImage,
                    onOpenBluetoothKey = onOpenBluetoothKey,
                    onLogout = onLogout
                )
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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeContent(
    isRefreshing: Boolean, vehicleVin: String, vehicleModel: String, vehicleDisplayModel: String = vehicleModel,
    vehicleAppearance: VehicleAppearance = VehicleAppearanceCatalog.resolveAppearance(vehicleDisplayModel, null),
    status: VehicleStatus?, statusUpdatedAtEpochMs: Long, vehicleNickname: String,
    locationSnapshot: VehicleLocationSnapshot?,
    vehicleAddress: GeocodedAddress? = null,
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
    activeControlCommand: String? = null
) {
    var showAddressNavigationDialog by rememberSaveable { mutableStateOf(false) }
    var showParkingDetailDialog by rememberSaveable { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
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
                    spacingPx = with(density) { 8.dp.roundToPx() },
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
                        onParkingClick = { showParkingDetailDialog = true }
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HomeTirePressureCard(status, Modifier.weight(1f))
                        VehicleStatusCard(
                            status = status,
                            todayMileage = EnergyHomeCardPolicy.todayMileage((energyState as? EnergyAnalyticsState.Success)?.data),
                            onOpenHealthyCharging = onOpenHealthyCharging,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    controlFeedback?.let { feedback ->
                        ControlFeedbackBanner(feedback, onDismissControlFeedback)
                    }
                    QuickVehicleActions(
                        vehicleVin = vehicleVin,
                        vehicleModel = vehicleModel,
                        status = status,
                        locationSnapshot = locationSnapshot,
                        onControl = onControl,
                        activeControlCommand = activeControlCommand
                    )
                    ClimateOverviewCard(
                        status = status,
                        onOpenClimate = onOpenClimateControl,
                        onQuickAcToggle = onControl,
                        controlBusy = isRefreshing,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                EnergyHomePagerCard(
                    state = energyState,
                    vehicleTotalMileage = status?.totalMileage,
                    vehicleModel = vehicleDisplayModel.ifBlank { vehicleModel },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dynamicCardHeight)
                )
            }
        }
    }

    if (showAddressNavigationDialog && locationSnapshot != null && vehicleAddress != null) {
        VehicleAddressNavigationDialog(
            fullAddress = vehicleAddress.fullAddress,
            locationSnapshot = locationSnapshot,
            onDismiss = { showAddressNavigationDialog = false }
        )
    }

    if (showParkingDetailDialog) {
        ParkingDetailDialog(
            fullAddress = vehicleAddress?.fullAddress ?: vehicleAddress?.shortAddress.orEmpty(),
            statusUpdatedAtEpochMs = statusUpdatedAtEpochMs,
            locationSnapshot = locationSnapshot,
            onFetchParkingPhoto = onFetchParkingPhoto,
            onDismiss = { showParkingDetailDialog = false }
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
    widgetOpacity: Int,
    onWidgetOpacityChange: (Int) -> Unit,
    widgetSensitiveActionVerificationEnabled: Boolean,
    onWidgetSensitiveActionVerificationChange: (Boolean) -> Unit,
    widget4x2Actions: List<String> = Widget4x2ActionPolicy.DEFAULT_ACTIONS,
    onWidget4x2ActionsChange: (List<String>) -> Unit = {},
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    vehicleModel: String,
    vehicleConfig: SessionStore.VehicleConfig,
    onSaveVehicleConfig: (String, String, SessionStore.VehiclePowerType?, String, String) -> Unit,
    currentVersion: String,
    currentReleaseNotes: String,
    versionUpdateState: VersionUpdateState,
    onCheckForUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    availableVehicles: List<Vehicle> = emptyList(),
    onSwitchVehicle: (String) -> Unit = {},
    vehicleVin: String = "",
    vehicleImageVersion: Int = 0,
    onSelectCustomVehicleImage: (Uri) -> Unit = {},
    onResetCustomVehicleImage: () -> Unit = {},
    onOpenBluetoothKey: () -> Unit = {},
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

        BluetoothKeyEntry(onClick = onOpenBluetoothKey)

        VehicleCustomImageCard(
            vehicleVin = vehicleVin,
            vehicleImageVersion = vehicleImageVersion,
            onSelectImageUri = onSelectCustomVehicleImage,
            onResetToDefault = onResetCustomVehicleImage
        )

        AppearanceModeCard(appearanceMode, onAppearanceModeChange)
        WidgetOpacityCard(widgetOpacity, onWidgetOpacityChange)
        WidgetSensitiveActionVerificationCard(
            enabled = widgetSensitiveActionVerificationEnabled,
            onEnabledChange = onWidgetSensitiveActionVerificationChange
        )
        Widget4x2ActionsCard(
            actions = widget4x2Actions,
            onActionsChange = onWidget4x2ActionsChange
        )

        SettingsSectionTitle("系统与更新")
        VersionUpdateCard(
            currentVersion = currentVersion,
            currentReleaseNotes = currentReleaseNotes,
            state = versionUpdateState,
            onCheckForUpdate = onCheckForUpdate,
            onOpenUpdate = onOpenUpdate
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
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) {
                Text("更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun VersionUpdateCard(
    currentVersion: String,
    currentReleaseNotes: String,
    state: VersionUpdateState,
    onCheckForUpdate: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
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
                    painter = painterResource(
                        if (expanded) R.drawable.ic_phosphor_arrow_clockwise
                        else R.drawable.ic_phosphor_arrow_clockwise
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
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
                    Button(
                        onClick = onOpenUpdate,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text("前往蒲公英更新") }
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectImageUri(uri)
        }
    }

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
                        onClick = { imagePickerLauncher.launch("image/*") },
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
private fun AppearanceModeCard(
    mode: AppearanceMode,
    onModeChange: (AppearanceMode) -> Unit
) {
    val description = when (mode) {
        AppearanceMode.SYSTEM -> "自动使用手机当前浅色或深色外观"
        AppearanceMode.LIGHT -> "始终使用浅色外观"
        AppearanceMode.DARK -> "始终使用深色外观"
    }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("外观模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(mode.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppearanceMode.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = AppearanceMode.entries.size),
                        onClick = { onModeChange(option) },
                        selected = mode == option,
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        label = { Text(option.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetOpacityCard(opacity: Int, onOpacityChange: (Int) -> Unit) {
    val options = listOf(
        100 to "不透明",
        75 to "微透",
        50 to "半透",
        25 to "全透"
    )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("小组件透明度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("控制桌面卡片的背景显示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    options.firstOrNull { it.first == opacity }?.second ?: "半透",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        onClick = { onOpacityChange(option.first) },
                        selected = opacity == option.first,
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            activeContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        label = { Text(option.second, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetSensitiveActionVerificationCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
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
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "小组件敏感操作验证",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "解锁和开启后备箱前需验证密码或指纹",
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
    onAddressClick: () -> Unit = {},
    vehicleVin: String = "",
    vehicleImageVersion: Int = 0,
    onControl: ((String) -> Unit)? = null,
    onOpenHealthyCharging: () -> Unit = {},
    availableVehicles: List<Vehicle> = emptyList(),
    onSwitchVehicle: (String) -> Unit = {},
    onOpenHealthCheck: () -> Unit = {},
    onUpdateNickname: (String) -> Unit = {},
    onParkingClick: () -> Unit = {}
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
    val h5Key = cachedMeta?.h5Key ?: CarModel3DManager.getFirstReadyKey(context)
    var is3DReady by remember(h5Key, vehicleImageVersion) {
        mutableStateOf(h5Key != null && CarModel3DManager.isModelReady(context, h5Key))
    }
    var is3DRendered by remember(h5Key, vehicleImageVersion) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(h5Key, vehicleImageVersion) {
        if (h5Key != null && !is3DReady) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                for (i in 0 until 40) {
                    kotlinx.coroutines.delay(500)
                    if (CarModel3DManager.isModelReady(context, h5Key)) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            is3DReady = true
                        }
                        break
                    }
                }
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
    val glassBorder = remember(isDark) {
        val topLeftColor = if (isDark) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.88f)
        val middleColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.45f)
        val bottomRightColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.20f)
        BorderStroke(
            1.0.dp,
            Brush.linearGradient(
                colors = listOf(topLeftColor, middleColor, bottomRightColor),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        )
    }

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
        Box(modifier = Modifier.fillMaxWidth()) {
            // 真实毛玻璃折射层（微晶渐变基底 + 能量环境极光 + 展台漫反射）
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

                // 4. 顶端高光玻璃折射微横光（极轻 0.5dp 顶层晶边）
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isDark) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.90f),
                            Color.Transparent
                        ),
                        startX = w * 0.10f,
                        endX = w * 0.90f
                    ),
                    start = Offset(w * 0.10f, 0.5f),
                    end = Offset(w * 0.90f, 0.5f),
                    strokeWidth = 1f
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
                    .padding(start = 20.dp, end = 16.dp),
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

                    // 状态更新时间
                    Text(
                        text = VehicleHomeStatus.updatedLabel(statusUpdatedAtEpochMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    // 核心数据组整体上提，大字与下方电量收紧至精致的 3~4dp 间隙
                    val mileageDisplayColor = if (isRangeExtender) MaterialTheme.colorScheme.onSurface else rangeColor
                    val unitDisplayColor = if (isRangeExtender) MaterialTheme.colorScheme.onSurfaceVariant else rangeColor

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
                                    color = rangeColor,
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

                            val electricColor = rangeColor
                            val fuelColor = rangeColorForSoc(
                                soc = status?.fuelSoc,
                                fallback = MaterialTheme.colorScheme.onSurfaceVariant,
                                normal = MaterialTheme.statusGood,
                                warning = MaterialTheme.statusWarn,
                                critical = MaterialTheme.colorScheme.error
                            )

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
                                            color = electricColor,
                                            isCharging = status?.chargeState == 1,
                                            modifier = Modifier.width(44.dp).height(4.5.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = elecSoc,
                                            fontSize = 10.sp,
                                            lineHeight = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = electricColor.copy(alpha = 0.90f),
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
                                            tint = electricColor
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = "$elecMiles",
                                            fontSize = 11.sp,
                                            lineHeight = 13.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = electricColor,
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
                                    color = rangeColor,
                                    isCharging = status?.chargeState == 1,
                                    modifier = Modifier.width(110.dp).height(5.dp)
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

                // 右侧列：设置按钮 + 位置信息
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("设置") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onOpenAccount,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_settings_tight),
                                contentDescription = "设置",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    val address = vehicleAddress?.takeIf { it.isNotBlank() }
                    if (address != null) {
                        val displayAddress = if (address.length > 10) "${address.take(10)}..." else address
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onAddressClick)
                                .background(MaterialTheme.glassInsetSurface.copy(alpha = 0.85f))
                                .border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_location_pin),
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = displayAddress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // ====== 行车状态显示在位置下方，间隔 4dp ======
                    val detailedDrivingState = VehicleHomeStatus.resolveDetailedDrivingState(
                        gearStatus = status?.gearStatus,
                        speed = status?.speed,
                        isDriving = status?.isDriving,
                        isShutDown = status?.isShutDown == true
                    )
                    detailedDrivingState?.let { drivingState ->
                        val isParked = drivingState.label == "已驻车"
                        Spacer(Modifier.height(if (address != null) 4.dp else 2.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (drivingState.isMoving) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f)
                            } else {
                                MaterialTheme.glassInsetSurface.copy(alpha = 0.85f)
                            },
                            contentColor = if (drivingState.isMoving) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            border = BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                            ),
                            modifier = if (isParked) {
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = onParkingClick)
                            } else Modifier
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (drivingState.isMoving) {
                                    DrivingBreathingDot()
                                }
                                Text(
                                    text = drivingState.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                if (isParked) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                        contentDescription = "查看驻车实景与位置",
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ====== 2. 100% 原始饱满比例车身主图 / 3D 旋转交互车模 ======
            val show3D = !hasCustomImage && is3DReady && h5Key != null
            val bitmapAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (show3D && is3DRendered) 0f else 1f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 400),
                label = "2d_fade"
            )
            val modelAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (show3D && is3DRendered) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 400),
                label = "3d_fade"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-16).dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // 1. 底层 2D 静态渲染官图（极速秒开垫底）
                if (remoteBitmap != null) {
                    Image(
                        bitmap = remoteBitmap,
                        contentDescription = "车身展示主图",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(horizontal = 16.dp)
                            .graphicsLayer { alpha = bitmapAlpha }
                            .clickable(enabled = !show3D, onClick = onOpenHealthCheck)
                    )
                }

                // 2. 顶层 3D 交互三维车模（支持手势 360° 前后左右旋转，就绪后丝滑淡入）
                if (show3D) {
                    CarModel3DView(
                        h5Key = h5Key!!,
                        modelParam = cachedMeta?.modelParam,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(horizontal = 4.dp)
                            .graphicsLayer { alpha = modelAlpha },
                        onReady = { is3DRendered = true },
                        onError = { is3DRendered = false },
                        onCarClick = onOpenHealthCheck
                    )
                }
            }
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
    val borderColor = if (isCharging) {
        MaterialTheme.statusGood.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
    }
    val backgroundColor = if (isCharging) {
        MaterialTheme.statusGood.copy(alpha = 0.08f)
    } else {
        MaterialTheme.glassInsetSurface.copy(alpha = 0.85f)
    }

    val formattedTime = chargeRemainTime?.trim()?.takeIf { it.isNotBlank() && it != "--" }?.let { raw ->
        raw.removePrefix("约").removeSuffix("钟")
    }

    val labelText = when {
        isCharging && formattedTime != null -> "充电中心 · 剩$formattedTime"
        isCharging -> "充电中心 · 充电中"
        else -> "充电中心"
    }

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = backgroundColor,
        border = BorderStroke(0.5.dp, borderColor),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (isCharging) {
                Icon(
                    painter = painterResource(R.drawable.ic_widget_charging_bolt),
                    contentDescription = "充电中",
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.statusGood
                )
            }
            Text(
                text = labelText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCharging) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_caret_right),
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = if (isCharging) MaterialTheme.statusGood.copy(alpha = 0.80f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
            )
        }
    }
}

@Composable
private fun EnergyCapsuleProgressBar(
    progress: Float,
    color: Color,
    isCharging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val isDark = LocalAppDarkTheme.current

    // 底槽与已消耗背景：深色凹槽基底 + 微量语义底透 + 晶体高光微轮廓，清晰界定整个胶囊形态与已消耗区间
    val slotBaseColor = if (isDark) Color(0xFF10141C).copy(alpha = 0.95f) else Color(0xFFE4E8F0).copy(alpha = 0.95f)
    val consumedTrackColor = color.copy(alpha = if (isDark) 0.16f else 0.12f)
    val trackBorderColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)

    val pulseAlpha = if (isCharging) {
        val transition = rememberInfiniteTransition(label = "chargingPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.55f,
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
    val progressBrush = Brush.horizontalGradient(
        listOf(
            color.copy(alpha = 0.80f * pulseAlpha),
            color.copy(alpha = pulseAlpha)
        )
    )

    Box(
        modifier = modifier
            .height(5.dp)
            .clip(CircleShape)
            .background(slotBaseColor)
            .background(consumedTrackColor)
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
private fun WindowStatusDialog(
    available: Boolean,
    openWindows: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("车窗状态") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    !available -> Text(
                        "当前车型或本次车况未返回可用的车窗信号。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    openWindows.isEmpty() -> Text("四个车窗均已关闭")
                    else -> openWindows.forEach { position ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(7.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                            Spacer(Modifier.width(9.dp))
                            Text("$position 车窗未关闭")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
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
    activeControlCommand: String? = null
) {
    val context = LocalContext.current
    val sessionStore = remember(context) { SessionStore(context) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var windowMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var sunshadeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var windowButtonTopLeft by remember { mutableStateOf(Offset.Zero) }
    var windowButtonWidth by remember { mutableStateOf(0f) }
    var windowButtonHeight by remember { mutableStateOf(0f) }
    var sunshadeButtonTopLeft by remember { mutableStateOf(Offset.Zero) }
    var sunshadeButtonWidth by remember { mutableStateOf(0f) }
    var sunshadeButtonHeight by remember { mutableStateOf(0f) }
    val trunkState = status?.trunkState ?: TrunkState.UNKNOWN
    val commandsPerPage = 4
    val availableCommands = remember(vehicleModel, status?.sentryMode) {
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
        listOf(
            Cmd("unlock", "解锁", R.drawable.ic_phosphor_lock_open),
            Cmd("lock", "上锁", R.drawable.ic_phosphor_lock),
            *windowGroup.toTypedArray(),
            Cmd("trunk", "开后备箱", R.drawable.ic_phosphor_trunk_open),
            *frunkCommands.toTypedArray(),
            *extraCommands.toTypedArray(),
            Cmd("sentry", "哨兵模式", R.drawable.ic_sentry)
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
    val lockPresentation = VehicleHomeStatus.lockButtonPresentation(status?.locked)
    val windowOpen = status?.windowStatusAvailable == true && status.openWindows.isNotEmpty()
    val trunkOpen = trunkState == TrunkState.OPEN
    val pageCount = (orderedCommands.size + commandsPerPage - 1) / commandsPerPage
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
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .frostedGlassCard(
                    shape = RoundedCornerShape(16.dp),
                    auraColor = primaryColor.copy(alpha = 0.08f),
                    auraCenter = Offset(0.5f, 0.5f)
                ),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = glassCardBorder(),
            shadowElevation = 0.dp
        ) {
            Column(
                Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = if (pageCount > 1) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(84.dp),
                    beyondViewportPageCount = 1
                ) { page ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val pageCommands = orderedCommands.drop(page * commandsPerPage).take(commandsPerPage)
                        pageCommands.forEach { command ->
                            val cmdInProgress = QuickCommandExecutionPolicy.isCommandInProgress(command.name, activeControlCommand)
                            val label = if (command.name == "trunk") {
                                when (trunkState) {
                                    TrunkState.CLOSED -> "开后备箱"
                                    TrunkState.OPEN -> "关后备箱"
                                    TrunkState.UNKNOWN -> "后备箱状态未知"
                                }
                            } else command.label
                            if (command.name == "windowGroup") {
                                Box(modifier = Modifier.weight(1f)) {
                                    QuickVehicleButton(
                                        label = label,
                                        iconRes = command.iconRes,
                                        warning = windowOpen,
                                        inProgress = cmdInProgress,
                                        onClick = {
                                            if (!editing) {
                                                sunshadeMenuExpanded = false
                                                windowMenuExpanded = !windowMenuExpanded
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
                                            },
                                        onLongClick = if (!editing) ::openEditor else null
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
                                    onClick = {
                                        if (!editing) {
                                            windowMenuExpanded = false
                                            sunshadeMenuExpanded = !sunshadeMenuExpanded
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { coordinates ->
                                            sunshadeButtonTopLeft = coordinates.positionInWindow()
                                            sunshadeButtonWidth = coordinates.size.width.toFloat()
                                            sunshadeButtonHeight = coordinates.size.height.toFloat()
                                        },
                                    onLongClick = if (!editing) ::openEditor else null
                                )
                            } else if (command.name == "trunk") {
                                Box(modifier = Modifier.weight(1f)) {
                                    QuickVehicleButton(
                                        label = label,
                                        iconRes = command.iconRes,
                                        warning = trunkOpen,
                                        inProgress = cmdInProgress,
                                        onClick = {
                                            if (!editing) {
                                                when (trunkState) {
                                                    TrunkState.CLOSED -> onControl("trunkOpen")
                                                    TrunkState.OPEN -> onControl("trunkClose")
                                                    TrunkState.UNKNOWN -> Toast.makeText(
                                                        context,
                                                        "请先刷新后备箱状态",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        iconTint = if (trunkOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        labelTint = if (trunkOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth(),
                                        onLongClick = if (!editing) ::openEditor else null
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
                                QuickVehicleButton(
                                    label = label,
                                    iconRes = command.iconRes,
                                    warning = isWarningCmd,
                                    inProgress = cmdInProgress,
                                    onClick = {
                                        if (!editing) {
                                            when (command.name) {
                                                "trunk" -> {
                                                    when (trunkState) {
                                                        TrunkState.CLOSED -> onControl("trunkOpen")
                                                        TrunkState.OPEN -> onControl("trunkClose")
                                                        TrunkState.UNKNOWN -> Toast.makeText(
                                                            context,
                                                            "请先刷新后备箱状态",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                                "sentry" -> onControl(SentryModeControlPolicy.commandName(status?.sentryMode))
                                                else -> onControl(command.name)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    onLongClick = if (!editing) ::openEditor else null
                                )
                            }
                        }
                        repeat(commandsPerPage - pageCommands.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (pageCount > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pageCount) { page ->
                            val isSelected = pagerState.currentPage == page
                            val indicatorWidth by animateDpAsState(
                                targetValue = if (isSelected) 10.dp else 3.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "pageIndicatorWidth"
                            )
                            val indicatorColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                                },
                                animationSpec = tween(200),
                                label = "pageIndicatorColor"
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
                            iconRes = R.drawable.ic_phosphor_wind,
                            onClick = {
                                windowMenuExpanded = false
                                onControl("windowVent")
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
                            iconRes = R.drawable.ic_phosphor_wind,
                            onClick = {
                                windowMenuExpanded = false
                                onControl("windowOpen")
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
                                windowMenuExpanded = false
                                onControl("windowClose")
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
                                sunshadeMenuExpanded = false
                                onControl("sunshadeOpen")
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
                                sunshadeMenuExpanded = false
                                onControl("sunshadeClose")
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickVehicleButton(
    label: String,
    iconRes: Int,
    warning: Boolean = false,
    inProgress: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
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

    val isWarning = warning || iconTint == MaterialTheme.colorScheme.error
    val circleBg = when {
        inProgress -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * pulseAlpha)
        isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f)
        isPressed -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val circleBorder = when {
        inProgress -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
        isWarning -> BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
        else -> BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isPressed) 0.6f else 0.35f))
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(54.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .combinedClickable(
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
                ),
            shape = CircleShape,
            color = circleBg,
            border = circleBorder
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        painterResource(iconRes),
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (inProgress) MaterialTheme.colorScheme.primary else labelTint,
            fontWeight = if (isWarning || inProgress) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun HomeTirePressureCard(status: VehicleStatus?, modifier: Modifier = Modifier) {
    val tireByPosition = status?.tires.orEmpty().associateBy { it.position }
    val hasTireData = tireByPosition.isNotEmpty()
    val hasWarning = tireByPosition.values.any { it.warning }
    val cardBorder = glassCardBorder()
    val warningColor = MaterialTheme.statusWarn
    Surface(
        modifier = modifier
            .heightIn(min = 120.dp)
            .frostedGlassCard(
                shape = RoundedCornerShape(16.dp),
                auraColor = if (hasWarning) warningColor.copy(alpha = 0.15f) else null,
                auraCenter = Offset(0.85f, 0.15f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = cardBorder,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
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

                // 中央俯视极简车模（适配卡片比例，大小适中）
                TopDownCarModel(
                    leftFrontWarning = tireByPosition["左前"]?.warning == true,
                    rightFrontWarning = tireByPosition["右前"]?.warning == true,
                    leftRearWarning = tireByPosition["左后"]?.warning == true,
                    rightRearWarning = tireByPosition["右后"]?.warning == true,
                    modifier = Modifier.size(width = 46.dp, height = 94.dp)
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
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)
    }
    val bodyBgColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
    }
    val roofBgColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.60f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    }
    val wheelNormalColor = if (isDark) {
        Color(0xFF42424A)
    } else {
        Color(0xFF5A5A65)
    }
    val wheelNormalBorder = if (isDark) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)
    }
    val wheelWarningColor = MaterialTheme.colorScheme.error
    val headlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val taillightColor = Color(0xFFE53935).copy(alpha = 0.85f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        val bodyW = w * 0.74f
        val bodyH = h * 0.90f
        val bodyLeft = cx - bodyW / 2f
        val bodyRight = cx + bodyW / 2f
        val bodyTop = h * 0.05f
        val bodyBottom = bodyTop + bodyH

        val frontAxleY = bodyTop + bodyH * 0.22f
        val rearAxleY = bodyTop + bodyH * 0.78f

        val stroke1dp = 1.dp.toPx()
        val stroke05dp = 0.6.dp.toPx()

        // 1. 轮轴辅助线
        val axleLeft = bodyLeft - 3.dp.toPx()
        val axleRight = bodyRight + 3.dp.toPx()
        drawLine(
            color = outlineColor.copy(alpha = 0.35f),
            start = Offset(axleLeft, frontAxleY),
            end = Offset(axleRight, frontAxleY),
            strokeWidth = stroke05dp
        )
        drawLine(
            color = outlineColor.copy(alpha = 0.35f),
            start = Offset(axleLeft, rearAxleY),
            end = Offset(axleRight, rearAxleY),
            strokeWidth = stroke05dp
        )

        // 2. 车身流线轮廓
        val bodyPath = Path().apply {
            moveTo(cx, bodyTop)
            cubicTo(
                cx - bodyW * 0.35f, bodyTop,
                bodyLeft, bodyTop + bodyH * 0.06f,
                bodyLeft, bodyTop + bodyH * 0.16f
            )
            lineTo(bodyLeft, bodyTop + bodyH * 0.84f)
            cubicTo(
                bodyLeft, bodyBottom - bodyH * 0.06f,
                cx - bodyW * 0.35f, bodyBottom,
                cx, bodyBottom
            )
            cubicTo(
                cx + bodyW * 0.35f, bodyBottom,
                bodyRight, bodyBottom - bodyH * 0.06f,
                bodyRight, bodyTop + bodyH * 0.84f
            )
            lineTo(bodyRight, bodyTop + bodyH * 0.16f)
            cubicTo(
                bodyRight, bodyTop + bodyH * 0.06f,
                cx + bodyW * 0.35f, bodyTop,
                cx, bodyTop
            )
            close()
        }

        drawPath(path = bodyPath, color = bodyBgColor)
        drawPath(
            path = bodyPath,
            color = outlineColor,
            style = Stroke(width = stroke1dp)
        )

        // 3. 全景天幕车顶
        val roofW = bodyW * 0.65f
        val roofH = bodyH * 0.50f
        val roofLeft = cx - roofW / 2f
        val roofTop = bodyTop + bodyH * 0.22f
        val roofRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        drawRoundRect(
            color = roofBgColor,
            topLeft = Offset(roofLeft, roofTop),
            size = Size(roofW, roofH),
            cornerRadius = roofRadius
        )
        drawRoundRect(
            color = outlineColor.copy(alpha = 0.40f),
            topLeft = Offset(roofLeft, roofTop),
            size = Size(roofW, roofH),
            cornerRadius = roofRadius,
            style = Stroke(width = stroke05dp)
        )

        // 4. 前后风挡与车灯细节
        val windshieldY = roofTop + 2.dp.toPx()
        drawLine(
            color = outlineColor.copy(alpha = 0.50f),
            start = Offset(roofLeft + 2.dp.toPx(), windshieldY),
            end = Offset(roofLeft + roofW - 2.dp.toPx(), windshieldY),
            strokeWidth = stroke05dp
        )
        val rearWindowY = roofTop + roofH - 2.dp.toPx()
        drawLine(
            color = outlineColor.copy(alpha = 0.50f),
            start = Offset(roofLeft + 2.dp.toPx(), rearWindowY),
            end = Offset(roofLeft + roofW - 2.dp.toPx(), rearWindowY),
            strokeWidth = stroke05dp
        )

        val hlLen = 4.dp.toPx()
        drawLine(
            color = headlightColor,
            start = Offset(bodyLeft + 1.5.dp.toPx(), bodyTop + bodyH * 0.05f),
            end = Offset(bodyLeft + 1.5.dp.toPx() + hlLen, bodyTop + bodyH * 0.02f),
            strokeWidth = 1.3.dp.toPx()
        )
        drawLine(
            color = headlightColor,
            start = Offset(bodyRight - 1.5.dp.toPx(), bodyTop + bodyH * 0.05f),
            end = Offset(bodyRight - 1.5.dp.toPx() - hlLen, bodyTop + bodyH * 0.02f),
            strokeWidth = 1.3.dp.toPx()
        )

        val tlW = bodyW * 0.55f
        drawLine(
            color = taillightColor,
            start = Offset(cx - tlW / 2f, bodyBottom - 1.dp.toPx()),
            end = Offset(cx + tlW / 2f, bodyBottom - 1.dp.toPx()),
            strokeWidth = 1.3.dp.toPx()
        )

        // 5. 四车轮 (带预警独立高亮)
        val wheelW = 4.5.dp.toPx()
        val wheelH = 13.dp.toPx()
        val wheelRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        fun drawWheel(x: Float, y: Float, isWarning: Boolean) {
            val fillColor = if (isWarning) wheelWarningColor else wheelNormalColor
            val borderColor = if (isWarning) wheelWarningColor else wheelNormalBorder
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
            if (isWarning) {
                drawRoundRect(
                    color = wheelWarningColor.copy(alpha = 0.35f),
                    topLeft = Offset(x - wheelW / 2f - 1.5.dp.toPx(), y - wheelH / 2f - 1.5.dp.toPx()),
                    size = Size(wheelW + 3.dp.toPx(), wheelH + 3.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        drawWheel(bodyLeft, frontAxleY, leftFrontWarning)
        drawWheel(bodyLeft, rearAxleY, leftRearWarning)
        drawWheel(bodyRight, frontAxleY, rightFrontWarning)
        drawWheel(bodyRight, rearAxleY, rightRearWarning)
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
    onOpenHealthyCharging: () -> Unit = {}
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
    val cardBorder = glassCardBorder()
    Surface(
        modifier = modifier
            .heightIn(min = 120.dp)
            .frostedGlassCard(shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = cardBorder,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleStatusCell("今日里程", todayMileage, Modifier.weight(1f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.glassInsetSurface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        HorizontalPager(
                            state = powerPagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    powerNextPageRequest = (powerPagerState.currentPage + 1) % powerItems.size
                                },
                            pageSpacing = 8.dp,
                            beyondViewportPageCount = 1,
                            userScrollEnabled = true
                        ) { page ->
                            val item = powerItems[page]
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
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
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
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
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleStatusCell(
                    "门锁",
                    lockLabel,
                    Modifier.weight(1f),
                    warning = status?.locked == false
                )
                VehicleStatusCell(
                    "车窗",
                    windowLabel,
                    Modifier.weight(1f),
                    warning = windowAvailable && openWindows.isNotEmpty(),
                    onClick = if (windowAvailable && openWindows.isNotEmpty()) {
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
            available = windowAvailable,
            openWindows = openWindows,
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = finalColor,
                maxLines = 1
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
private fun VehicleAddressNavigationDialog(
    fullAddress: String,
    locationSnapshot: VehicleLocationSnapshot?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember(context) { ExternalMapLauncher.availableApps(context, forNavigation = true) }
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
        title = {
            Text("车辆位置详情")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.glassInsetSurface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_location_navigate),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(
                                fullAddress,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Text(
                    "选择地图应用开始导航",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (apps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        apps.forEach { app ->
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    val loc = locationSnapshot?.location
                                    if (loc != null) {
                                        runCatching {
                                            ExternalMapLauncher.navigateToVehicle(context, app, loc.latitude, loc.longitude)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Text(app.displayName)
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ParkingDetailDialog(
    fullAddress: String,
    statusUpdatedAtEpochMs: Long,
    locationSnapshot: VehicleLocationSnapshot? = null,
    onFetchParkingPhoto: ((ChassisParkingPhoto?, android.graphics.Bitmap?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var photoState by remember { mutableStateOf<ParkingPhotoLoadState>(ParkingPhotoLoadState.Loading) }
    var uploadTimeMs by remember { mutableLongStateOf(0L) }

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 实景照片卡片
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
                                    .height(360.dp),
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
                            Column(Modifier.fillMaxWidth()) {
                                Image(
                                    bitmap = state.bitmap.asImageBitmap(),
                                    contentDescription = "驻车实景照片",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 340.dp, max = 500.dp),
                                    contentScale = ContentScale.Crop
                                )
                                if (uploadTimeMs > 0L) {
                                    val formattedPhotoTime = remember(uploadTimeMs) {
                                        SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(Date(uploadTimeMs))
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "拍摄时间：$formattedPhotoTime",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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

                // 2. 位置信息
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlassCard(shape = RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = glassCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_location_pin),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            SelectionContainer(Modifier.weight(1f)) {
                                Text(
                                    fullAddress.ifBlank { "正在获取车辆位置..." },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        if (statusUpdatedAtEpochMs > 0L) {
                            val statusTimeText = remember(statusUpdatedAtEpochMs) {
                                SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(Date(statusUpdatedAtEpochMs))
                            }
                            Text(
                                "位置更新：$statusTimeText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
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
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = EnergyHomePage.RECENT_MILEAGE.ordinal) { EnergyHomePage.entries.size }
    val goodColor = MaterialTheme.statusGood
    Surface(
        modifier = modifier
            .heightIn(min = EnergyHomeCardPolicy.MIN_CARD_HEIGHT_DP.dp)
            .frostedGlassCard(
                shape = RoundedCornerShape(16.dp),
                auraColor = goodColor.copy(alpha = 0.10f),
                auraCenter = Offset(0.85f, 0.15f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
            valueColor = if (mileageHasValue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            accentColor = MaterialTheme.statusWarn,
            modifier = Modifier.weight(1f)
        )
        EnergyMetricCard(
            label = "累计能耗",
            value = energyText,
            unit = "kWh",
            valueColor = if (energyHasValue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold,
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
    onOpenClimate: () -> Unit,
    onQuickAcToggle: (String) -> Unit,
    controlBusy: Boolean = false,
    modifier: Modifier = Modifier
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

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (status?.acSwitch == true && climateTone != ClimateTemperatureTone.DEFAULT) {
            BorderStroke(1.dp, temperatureColor.copy(alpha = 0.45f))
        } else {
            glassCardBorder()
        },
        shadowElevation = 0.dp,
        modifier = modifier
            .height(56.dp)
            .frostedGlassCard(
                shape = RoundedCornerShape(16.dp),
                auraColor = if (status?.acSwitch == true) temperatureColor.copy(alpha = 0.16f) else null,
                auraCenter = Offset(0.06f, 0.5f),
                auraRadiusRatio = 0.5f
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ambientBrush != null) Modifier.background(ambientBrush) else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 4.dp),
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
fun ClimateControlContent(
    status: VehicleStatus?,
    statusError: String,
    busy: Boolean,
    controlFeedback: ControlFeedback?,
    statusUpdatedAtEpochMs: Long,
    hvacCapability: HvacCapability,
    onDismissControlFeedback: () -> Unit,
    onRefresh: () -> Unit,
    onControl: (String) -> Unit,
    onApplyClimateSettings: (AirConditioningCommand) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    val actionsEnabled = Commands.canSubmitClimateControl(status != null, busy)
    val fanRange = hvacCapability.effectiveUiFanRange()
    var editedTemperature by rememberSaveable(hvacCapability.temperatureMinC, hvacCapability.temperatureMaxC) {
        mutableStateOf(22.coerceIn(hvacCapability.temperatureMinC, hvacCapability.temperatureMaxC))
    }
    var editedWindLevel by rememberSaveable(fanRange.first, fanRange.last) {
        mutableStateOf(3.coerceIn(fanRange))
    }
    var editedDefogging by rememberSaveable { mutableStateOf(false) }
    var editedOutletName by rememberSaveable { mutableStateOf(AirOutlet.ALL.name) }
    var editedCircle by rememberSaveable {
        mutableStateOf(AirCircle.fromTelemetryValue(status?.recirculationMode) ?: AirCircle.INNER)
    }

    LaunchedEffect(
        status?.acSetting,
        status?.acAirVolume,
        status?.windshieldDefrost,
        status?.recirculationMode,
        hvacCapability,
        busy
    ) {
        if (!busy) {
            editedTemperature = climateWholeNumber(status?.acSetting)
                ?.coerceIn(hvacCapability.temperatureMinC, hvacCapability.temperatureMaxC)
                ?: 22.coerceIn(hvacCapability.temperatureMinC, hvacCapability.temperatureMaxC)
            editedWindLevel = climateWholeNumber(status?.acAirVolume)
                ?.coerceIn(fanRange)
                ?: 3.coerceIn(fanRange)
            editedDefogging = status?.windshieldDefrost ?: false
            AirCircle.fromTelemetryValue(status?.recirculationMode)?.let {
                editedCircle = it
            }
        }
    }

    fun currentCommand(operation: HvacOperation): AirConditioningCommand {
        return AirConditioningCommand(
            operation = operation,
            temperatureC = editedTemperature,
            capability = hvacCapability,
            windLevel = editedWindLevel,
            circle = editedCircle,
            windshieldDefogging = editedDefogging,
            outlet = AirOutlet.valueOf(editedOutletName)
        )
    }

    fun submitSettings(operation: HvacOperation) {
        if (!actionsEnabled) return
        val command = currentCommand(operation)
        onApplyClimateSettings(command)
    }

    val activePresetName = activeClimatePresetName(status)

    PullToRefreshBox(
        isRefreshing = busy,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val climateAura = if (status?.acSwitch == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else null
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(20.dp),
                        auraColor = climateAura,
                        auraCenter = Offset(0.2f, 0.2f)
                    ),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = glassCardBorder(),
                shadowElevation = 0.dp
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    // 1. 温度调节 (支持 - / + 步进快捷点按与滑动松手即刻下发)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("温度设定", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = actionsEnabled && editedTemperature > hvacCapability.temperatureMinC) {
                                            editedTemperature = (editedTemperature - 1).coerceAtLeast(hvacCapability.temperatureMinC)
                                            submitSettings(HvacOperation.ON)
                                        }
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    "$editedTemperature °C",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = actionsEnabled && editedTemperature < hvacCapability.temperatureMaxC) {
                                            editedTemperature = (editedTemperature + 1).coerceAtMost(hvacCapability.temperatureMaxC)
                                            submitSettings(HvacOperation.ON)
                                        }
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        ThinClimateSlider(
                            value = editedTemperature.toFloat(),
                            onValueChange = {
                                editedTemperature = it.roundToInt().coerceIn(hvacCapability.temperatureMinC, hvacCapability.temperatureMaxC)
                            },
                            onValueChangeFinished = {
                                submitSettings(HvacOperation.ON)
                            },
                            enabled = actionsEnabled,
                            valueRange = hvacCapability.temperatureMinC.toFloat()..hvacCapability.temperatureMaxC.toFloat(),
                            steps = (hvacCapability.temperatureMaxC - hvacCapability.temperatureMinC - 1).coerceAtLeast(0),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 2. 风量滑块 (松手即刻下发)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("风量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$editedWindLevel 挡", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (fanRange.first < fanRange.last) {
                            ThinClimateSlider(
                                value = editedWindLevel.toFloat(),
                                onValueChange = {
                                    editedWindLevel = it.roundToInt().coerceIn(fanRange)
                                },
                                onValueChangeFinished = {
                                    submitSettings(HvacOperation.ON)
                                },
                                enabled = actionsEnabled,
                                valueRange = fanRange.first.toFloat()..fanRange.last.toFloat(),
                                steps = (fanRange.last - fanRange.first - 1).coerceAtLeast(0),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("当前车型仅支持该风量挡位", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // 3. 内外循环分段切换 (即点即生效)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("循环模式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (editedCircle == AirCircle.INNER) "内循环" else "外循环",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (editedCircle == AirCircle.INNER) MaterialTheme.colorScheme.primary else MaterialTheme.statusWarn
                            )
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = editedCircle == AirCircle.INNER,
                                onClick = {
                                    editedCircle = AirCircle.INNER
                                    submitSettings(HvacOperation.ON)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                label = { Text("内循环 (速冷/隔绝尾气)", style = MaterialTheme.typography.labelSmall) }
                            )
                            SegmentedButton(
                                selected = editedCircle == AirCircle.OUTER,
                                onClick = {
                                    editedCircle = AirCircle.OUTER
                                    submitSettings(HvacOperation.ON)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                label = { Text("外循环 (引入新风)", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // 4. 出风方向分段切换 (即点即生效)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("出风方向", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (editedOutletName == AirOutlet.ALL.name) "全车出风" else "前风挡除雾出风",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = editedOutletName == AirOutlet.ALL.name,
                                onClick = {
                                    editedOutletName = AirOutlet.ALL.name
                                    editedDefogging = false
                                    submitSettings(HvacOperation.ON)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                label = { Text("全车环绕出风", style = MaterialTheme.typography.labelSmall) }
                            )
                            SegmentedButton(
                                selected = editedOutletName == AirOutlet.WINDSHIELD.name,
                                onClick = {
                                    editedOutletName = AirOutlet.WINDSHIELD.name
                                    editedDefogging = true
                                    submitSettings(HvacOperation.ON)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                label = { Text("前风挡除雾", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    ClimateSegmentedSetting(
                        title = "快捷预设",
                        options = listOf(
                            ClimatePresetAction.QUICK_COOL.name to "制冷",
                            ClimatePresetAction.WINDSHIELD_DEFROST.name to "除霜",
                            ClimatePresetAction.QUICK_HEAT.name to "制热",
                            ClimatePresetAction.DEODORIZE.name to "除味"
                        ),
                        selected = activePresetName.orEmpty(),
                        enabled = actionsEnabled,
                        onSelected = { name ->
                            val action = ClimatePresetAction.valueOf(name)
                            val cmd = when (action) {
                                ClimatePresetAction.QUICK_COOL -> "quickCool"
                                ClimatePresetAction.WINDSHIELD_DEFROST -> "defrost"
                                ClimatePresetAction.QUICK_HEAT -> "quickHeat"
                                ClimatePresetAction.DEODORIZE -> "deodorize"
                            }
                            onControl(cmd)
                        }
                    )
                }
            }

            SeatComfortControlCard(
                status = status,
                enabled = actionsEnabled,
                onControl = onControl
            )
        }
    }
}

@Composable
fun SeatComfortControlCard(
    status: VehicleStatus?,
    enabled: Boolean,
    onControl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .frostedGlassCard(shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

            // 主驾驶
            ComfortLevelSegmentedRow(
                title = "主驾座椅加热",
                levels = listOf("关", "1档", "2档", "3档"),
                selectedLevel = status?.driverSeatHeating ?: 0,
                enabled = enabled,
                onLevelSelected = { onControl("driverSeatHeating_$it") },
                activeColor = MaterialTheme.statusWarn
            )
            ComfortLevelSegmentedRow(
                title = "主驾座椅通风",
                levels = listOf("关", "1档", "2档", "3档"),
                selectedLevel = status?.driverSeatVentilation ?: 0,
                enabled = enabled,
                onLevelSelected = { onControl("driverSeatVentilation_$it") },
                activeColor = MaterialTheme.colorScheme.primary
            )

            // 副驾驶
            ComfortLevelSegmentedRow(
                title = "副驾座椅加热",
                levels = listOf("关", "1档", "2档", "3档"),
                selectedLevel = status?.passengerSeatHeating ?: 0,
                enabled = enabled,
                onLevelSelected = { onControl("passengerSeatHeating_$it") },
                activeColor = MaterialTheme.statusWarn
            )
            ComfortLevelSegmentedRow(
                title = "副驾座椅通风",
                levels = listOf("关", "1档", "2档", "3档"),
                selectedLevel = status?.passengerSeatVentilation ?: 0,
                enabled = enabled,
                onLevelSelected = { onControl("passengerSeatVentilation_$it") },
                activeColor = MaterialTheme.colorScheme.primary
            )

            // 方向盘与后视镜加热
            ComfortLevelSegmentedRow(
                title = "方向盘加热",
                levels = listOf("关", "弱档", "强档"),
                selectedLevel = status?.steeringWheelHeatingLevel ?: if (status?.steeringWheelHeating == true) 2 else 0,
                enabled = enabled,
                onLevelSelected = { onControl("steeringWheelHeating_$it") },
                activeColor = MaterialTheme.statusWarn
            )
            ComfortLevelSegmentedRow(
                title = "后视镜加热",
                levels = listOf("关", "开"),
                selectedLevel = if (status?.rearviewMirrorHeating == true) 1 else 0,
                enabled = enabled,
                onLevelSelected = { onControl(if (it == 1) "rearviewMirrorHeating_on" else "rearviewMirrorHeating_off") },
                activeColor = MaterialTheme.statusWarn
            )
        }
    }
}

@Composable
private fun ComfortLevelSegmentedRow(
    title: String,
    levels: List<String>,
    selectedLevel: Int,
    enabled: Boolean,
    onLevelSelected: (Int) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val currentText = levels.getOrElse(selectedLevel) { "关" }
            Text(
                currentText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selectedLevel > 0) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            levels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedLevel == index,
                    onClick = { onLevelSelected(index) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
fun ThinClimateSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier
) {
    val total = valueRange.endInclusive - valueRange.start
    val fraction = if (total <= 0f) 0f else ((value - valueRange.start) / total).coerceIn(0f, 1f)
    val enabledColor = MaterialTheme.colorScheme.primary
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val trackColor = if (enabled) enabledColor else disabledColor
    val trackBackground = if (enabled) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .pointerInput(valueRange, steps, enabled) {
                if (!enabled) return@pointerInput
                fun snap(x: Float, width: Int): Float {
                    val f = (x / width.toFloat()).coerceIn(0f, 1f)
                    val raw = valueRange.start + f * total
                    return if (steps > 0) {
                        val stepSize = total / (steps + 1)
                        val snapped = valueRange.start + ((raw - valueRange.start) / stepSize).roundToInt() * stepSize
                        snapped.coerceIn(valueRange.start, valueRange.endInclusive)
                    } else {
                        raw.coerceIn(valueRange.start, valueRange.endInclusive)
                    }
                }
                detectDragGestures(
                    onDragStart = { offset -> onValueChange(snap(offset.x, size.width)) },
                    onDrag = { change, _ -> onValueChange(snap(change.position.x, size.width)) },
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() }
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
            val trackHeightPx = 2.dp.toPx()
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
                    label = { Text(option.second, style = MaterialTheme.typography.labelSmall) }
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

