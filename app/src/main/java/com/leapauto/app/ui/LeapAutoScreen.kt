package com.leapauto.app.ui

import android.content.Context
import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
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
import com.leapauto.app.ErrorLogEntry
import com.leapauto.app.ErrorLogs
import com.leapauto.app.ExternalLinks
import com.leapauto.app.ExternalMapApp
import com.leapauto.app.ExternalMapLauncher
import com.leapauto.app.GeocodedAddress
import com.leapauto.app.HomeClimateTogglePresentation
import com.leapauto.app.HomeClimateTogglePresentationMapper
import com.leapauto.app.HvacCapability
import com.leapauto.app.HvacOperation
import com.leapauto.app.MainNavigationTabs
import com.leapauto.app.PgyerRelease
import com.leapauto.app.QuickCommandOrderPolicy
import com.leapauto.app.R
import com.leapauto.app.SUPPORTED_VEHICLE_MODELS
import com.leapauto.app.SentryModeControlPolicy
import com.leapauto.app.SessionExpiredDialogAction
import com.leapauto.app.SessionExpiredDialogPolicy
import com.leapauto.app.SessionStore
import com.leapauto.app.TireStatus
import com.leapauto.app.TrunkState
import com.leapauto.app.VehicleAppearance
import com.leapauto.app.VehicleAppearanceCatalog
import com.leapauto.app.VehicleConfigConfirmationPolicy
import com.leapauto.app.VehicleHomeStatus
import com.leapauto.app.VehicleImageCache
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
    SUMMARY,
    WEEKLY_CONSUMPTION,
    RECENT_MILEAGE,
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
    climateTemperatureRequestState: ClimateControlRequestState = ClimateControlRequestState(),
    vehicleModel: String,
    vehicleDisplayModel: String = vehicleModel,
    vehicleConfig: SessionStore.VehicleConfig = SessionStore.VehicleConfig(),
    vehicleAppearance: VehicleAppearance = VehicleAppearanceCatalog.resolveAppearance(
        vehicleDisplayModel,
        vehicleConfig.color
    ),
    hvacCapability: HvacCapability = HvacCapability.fallback(),
    pinSaved: Boolean,
    pinSetupInProgress: Boolean,
    showVehicleConfigConfirmationPrompt: Boolean,
    widgetOpacity: Int,
    widgetSensitiveActionVerificationEnabled: Boolean,
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
    onQuickAc: (Int, Long) -> Unit = { _, _ -> }
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showVehicleLocation by rememberSaveable { mutableStateOf(false) }
    var showClimateControl by rememberSaveable { mutableStateOf(false) }
    var showHealthyChargingSheet by rememberSaveable { mutableStateOf(false) }
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

    if (loggedIn && pinSetupInProgress && !showSessionExpiredDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("设置操作密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "请输入零跑APP上您设置过的4位操作密码。保存后用于远程控车指令鉴权。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = onPinChange,
                        label = { Text("4 位数字密码") },
                        placeholder = { Text("请输入控车密码") },
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
                ) { Text("保存密码") }
            },
            dismissButton = {
                TextButton(onClick = onCancelPinSetup) { Text("取消设置") }
            }
        )
    }

    if (
        loggedIn &&
        showVehicleConfigConfirmationPrompt &&
        !pinSetupInProgress &&
        !showSessionExpiredDialog
    ) {
        VehicleConfigDialog(
            vehicleModel = vehicleModel,
            config = vehicleConfig,
            title = "确认车型配置",
            supportingText = "请确认车型、年份和动力类型，续航信息将按本次配置展示。",
            dismissible = false,
            onDismiss = {},
            onSave = onSaveVehicleConfig
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
                        actions = {},
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
                    phone,
                    onPhoneChange,
                    code,
                    onCodeChange,
                    onSendSms,
                    onLogin,
                    busy = busy,
                    smsCountdownSeconds = smsCountdownSeconds
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
                    vehicleImageVersion = vehicleImageVersion
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
                    phone,
                    pinSaved,
                    pin,
                    onPinChange,
                    onSavePin,
                    pinSetupInProgress,
                    onCancelPinSetup,
                    widgetOpacity,
                    onWidgetOpacityChange,
                    widgetSensitiveActionVerificationEnabled,
                    onWidgetSensitiveActionVerificationChange,
                    appearanceMode,
                    onAppearanceModeChange,
                    vehicleModel,
                    vehicleConfig,
                    onSaveVehicleConfig,
                    currentVersion,
                    currentReleaseNotes,
                    versionUpdateState,
                    onCheckForUpdate,
                    onOpenUpdate,
                    onLogout
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
    smsCountdownSeconds: Int = 0
) {
    val haptic = LocalHapticFeedback.current
    val canLogin = phone.length == 11 && code.length >= 4 && !busy
    val canSendSms = smsCountdownSeconds == 0 && phone.length == 11 && !busy

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))

        // 品牌徽标与名称
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            shadowElevation = 0.dp
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_car),
                    contentDescription = "零跑智控",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "零跑智控",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "连接你的每一次出发",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))

        // 登录卡片
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.glassSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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

        Spacer(Modifier.height(28.dp))

        // 底部安全凭证提示胶囊
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.glassSurface,
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
    vehicleImageVersion: Int = 0
) {
    var showAddressNavigationDialog by rememberSaveable { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            onRefresh()
            onRefreshEnergy()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
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
                onOpenHealthyCharging = onOpenHealthyCharging
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
                onControl = onControl
            )
            ClimateOverviewCard(
                status = status,
                onOpenClimate = onOpenClimateControl,
                onQuickAcToggle = onControl,
                controlBusy = isRefreshing,
                modifier = Modifier.fillMaxWidth()
            )
            EnergyHomePagerCard(
                state = energyState,
                vehicleTotalMileage = status?.totalMileage,
                vehicleModel = vehicleDisplayModel.ifBlank { vehicleModel },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAddressNavigationDialog && locationSnapshot != null && vehicleAddress != null) {
        VehicleAddressNavigationDialog(
            fullAddress = vehicleAddress.fullAddress,
            locationSnapshot = locationSnapshot,
            onDismiss = { showAddressNavigationDialog = false }
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
private fun AccountInfoCard(maskedPhone: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_car),
                        contentDescription = "账号信息",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = maskedPhone,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "已连接零跑官方车联网",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.statusGood
                )
            }
        }
    }
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
    onLogout: () -> Unit
) {
    var showDiagnosticLogDialog by rememberSaveable { mutableStateOf(false) }
    val maskedPhone = when {
        phone.length == 11 -> "${phone.take(3)}****${phone.takeLast(4)}"
        phone.isNotBlank() -> phone
        else -> "已连接零跑"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AccountInfoCard(maskedPhone)

        SettingsSectionTitle("控车安全")
        PinCard(
            pinSaved = pinSaved,
            pin = pin,
            onPinChange = onPinChange,
            onSavePin = onSavePin,
            initialSetupInProgress = pinSetupInProgress,
            onCancelInitialSetup = onCancelPinSetup
        )

        VehicleConfigCard(vehicleModel, vehicleConfig, onSaveVehicleConfig)

        SettingsSectionTitle("个性化与小组件")
        AppearanceModeCard(appearanceMode, onAppearanceModeChange)
        WidgetOpacityCard(widgetOpacity, onWidgetOpacityChange)
        WidgetSensitiveActionVerificationCard(
            enabled = widgetSensitiveActionVerificationEnabled,
            onEnabledChange = onWidgetSensitiveActionVerificationChange
        )

        SettingsSectionTitle("系统与诊断")
        VersionUpdateCard(
            currentVersion = currentVersion,
            currentReleaseNotes = currentReleaseNotes,
            state = versionUpdateState,
            onCheckForUpdate = onCheckForUpdate,
            onOpenUpdate = onOpenUpdate
        )

        DiagnosticLogCard(onClick = { showDiagnosticLogDialog = true })

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
        ) {
            Text(
                "退出登录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showDiagnosticLogDialog) {
        DiagnosticLogDialog(onDismiss = { showDiagnosticLogDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleConfigCard(
    vehicleModel: String,
    config: SessionStore.VehicleConfig,
    onSave: (String, String, SessionStore.VehiclePowerType?, String, String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    val displayModel = config.model.ifBlank { vehicleModel }.ifBlank { "未设置" }
    val typeLabel = when (config.powerType) {
        SessionStore.VehiclePowerType.PURE_ELECTRIC -> "纯电"
        SessionStore.VehiclePowerType.RANGE_EXTENDER -> "增程"
        null -> "未设置"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { editing = true },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("座驾配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("· ${config.nickname.ifBlank { displayModel }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("修改 >", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        displayModel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val isReev = config.powerType == SessionStore.VehiclePowerType.RANGE_EXTENDER
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = (if (isReev) MaterialTheme.statusWarn else MaterialTheme.statusGood).copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, (if (isReev) MaterialTheme.statusWarn else MaterialTheme.statusGood).copy(alpha = 0.35f))
                ) {
                    Text(
                        typeLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isReev) MaterialTheme.statusWarn else MaterialTheme.statusGood
                    )
                }

                if (config.modelYear.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Text(
                            "${config.modelYear}款",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                "续航里程与电量算法按此配置展示",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (editing) {
        VehicleConfigDialog(
            vehicleModel = vehicleModel,
            config = config,
            title = "车型配置",
            dismissible = true,
            onDismiss = { editing = false },
            onSave = { model, year, type, color, nickname ->
                onSave(model, year, type, color, nickname)
                editing = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleConfigDialog(
    vehicleModel: String,
    config: SessionStore.VehicleConfig,
    title: String,
    supportingText: String? = null,
    dismissible: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, SessionStore.VehiclePowerType?, String, String) -> Unit
) {
    var model by remember(config, vehicleModel, title) {
        mutableStateOf(resolveVehicleConfigModel(config.model, vehicleModel))
    }
    var year by remember(config, title) {
        mutableStateOf(config.modelYear.ifBlank { "2026" })
    }
    var nickname by remember(config, title) {
        mutableStateOf(config.nickname.ifBlank { resolveVehicleConfigModel(config.model, vehicleModel) })
    }
    var type by remember(config, title) {
        mutableStateOf(config.powerType ?: SessionStore.VehiclePowerType.PURE_ELECTRIC)
    }
    var color by remember(config, vehicleModel, title) {
        mutableStateOf(
            VehicleAppearanceCatalog.reconciledColor(
                resolveVehicleConfigModel(config.model, vehicleModel),
                config.color
            )
        )
    }
    var modelExpanded by remember { mutableStateOf(false) }
    val valid = VehicleConfigConfirmationPolicy.isValid(model, year, type, color)

    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                supportingText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(16) },
                    label = { Text("车辆昵称") },
                    placeholder = { Text("例如：我的小零") },
                    supportingText = { Text("选填，仅保存在本机当前车辆配置中") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("车型") },
                        placeholder = { Text("请选择车型") },
                        trailingIcon = {
                            IconButton(onClick = { modelExpanded = !modelExpanded }) {
                                Text(
                                    if (modelExpanded) "▲" else "▼",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { modelExpanded = true }
                    )
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        SUPPORTED_VEHICLE_MODELS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    model = option
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    label = { Text("车型年份") },
                    placeholder = { Text("例如 2026") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(
                        SessionStore.VehiclePowerType.PURE_ELECTRIC to "纯电",
                        SessionStore.VehiclePowerType.RANGE_EXTENDER to "增程"
                    )
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { type = value },
                            selected = type == value,
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            label = { Text(label) }
                        )
                    }
                }
                if (!valid) {
                    Text(
                        "请选择车型，填写 4 位车型年份并选择动力类型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(model, year, type, color, nickname.trim()) }
            ) { Text("保存") }
        },
        dismissButton = {
            if (dismissible) {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

private fun resolveVehicleConfigModel(configuredModel: String, reportedModel: String): String {
    return VehicleConfigConfirmationPolicy.normalizeModel(configuredModel)
        ?: VehicleConfigConfirmationPolicy.normalizeModel(reportedModel)
        ?: ""
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
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.glassSurface
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
private fun DiagnosticLogCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_code),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "诊断异常日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "查看登录与车图接口的最近异常记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_arrow_clockwise),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DiagnosticLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(ErrorLogs.repository.list()) }
    val fullText = remember(logs) {
        if (logs.isEmpty()) "暂无诊断异常日志" else ErrorLogs.repository.copyText()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("诊断日志 (${logs.size})")
                if (logs.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            ErrorLogs.repository.clear()
                            logs = emptyList()
                        }
                    ) { Text("清空") }
                }
            }
        },
        text = {
            SelectionContainer {
                Text(
                    text = fullText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("error_log", fullText))
                    Toast.makeText(context, "已复制日志", Toast.LENGTH_SHORT).show()
                }
            ) { Text("复制全部") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
private fun glassCardBorder(): BorderStroke {
    val isDark = LocalAppDarkTheme.current
    val topLeftColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.78f)
    val bottomRightColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.18f)
    return BorderStroke(
        1.0.dp,
        Brush.linearGradient(
            colors = listOf(topLeftColor, bottomRightColor),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    )
}

@Composable
private fun glassInsetBorder(warning: Boolean = false): BorderStroke {
    if (warning) return BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
    val isDark = LocalAppDarkTheme.current
    val topLeftColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.70f)
    val bottomRightColor = if (isDark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.14f)
    return BorderStroke(
        0.6.dp,
        Brush.linearGradient(
            colors = listOf(topLeftColor, bottomRightColor),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    )
}

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
    onOpenHealthyCharging: () -> Unit = {}
) {
    val context = LocalContext.current
    val remoteBitmap = remember(vehicleVin, vehicleImageVersion) {
        if (vehicleVin.isNotBlank()) VehicleImageCache.loadCachedImageBitmap(context, vehicleVin) else null
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = heroColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            nickname,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                            .offset(y = (-5).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenHealthyCharging),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // 公里数大字 + 紧随其后的 km 单位
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (mileageHasUnit) mileageLabel.dropLast(2) else mileageLabel,
                                fontSize = 32.sp,
                                lineHeight = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = mileageDisplayColor,
                                maxLines = 1
                            )
                            Text(
                                text = "km",
                                fontSize = 14.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = unitDisplayColor,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
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
                                modifier = Modifier
                                    .padding(top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                // 1. 一体化双段双拼能量微高光槽 (左纯电·右燃油，中间留微缝)
                                Row(
                                    modifier = Modifier.width(180.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EnergyCapsuleProgressBar(
                                        progress = chargeProgress(normalizedSoc),
                                        color = electricColor,
                                        isCharging = status?.chargeState == 1,
                                        modifier = Modifier.weight(1f).height(4.5.dp)
                                    )
                                    EnergyCapsuleProgressBar(
                                        progress = chargeProgress(status?.fuelSoc),
                                        color = fuelColor,
                                        isCharging = false,
                                        modifier = Modifier.weight(1f).height(4.5.dp)
                                    )
                                }

                                // 2. 纯净字符排版行 (彻底移除外框与底色药丸补丁，极度通透高级)
                                Row(
                                    modifier = Modifier.width(180.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 纯电数据 (绿/橙/红变色)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_hybrid_electric),
                                            contentDescription = null,
                                            modifier = Modifier.size(11.dp),
                                            tint = electricColor
                                        )
                                        Text(
                                            text = "$elecMiles",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = electricColor
                                        )
                                        Text(
                                            text = "· $elecSoc",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = electricColor.copy(alpha = 0.85f),
                                            fontSize = 10.sp
                                        )
                                    }

                                    // 燃油数据 (橙黄/红变色)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_hybrid_fuel),
                                            contentDescription = null,
                                            modifier = Modifier.size(11.dp),
                                            tint = fuelColor
                                        )
                                        Text(
                                            text = "$fuelMiles",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = fuelColor
                                        )
                                        Text(
                                            text = "· $fSoc",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = fuelColor.copy(alpha = 0.85f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            val electricSocLabel = VehicleHomeStatus.resolvedSocLabel(status?.preciseSoc, status?.soc)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.offset(y = (-3).dp)
                            ) {
                                EnergyCapsuleProgressBar(
                                    progress = chargeProgress(normalizedSoc),
                                    color = rangeColor,
                                    isCharging = status?.chargeState == 1,
                                    modifier = Modifier.width(64.dp).height(4.5.dp)
                                )
                                Text(
                                    text = electricSocLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = rangeColor
                                )
                            }
                        }
                    }
                }

                // 右侧列：设置按钮 + 位置信息 (放在设置按钮正下方)
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
                                text = address,
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
                        isDriving = status?.isDriving
                    )
                    detailedDrivingState?.let { drivingState ->
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
                            )
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
                            }
                        }
                    }
                }
            }

            // ====== 2. 100% 原始饱满比例车身主图 (纯净无额外阴影) ======
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp),
                contentAlignment = Alignment.Center
            ) {
                if (remoteBitmap != null) {
                    Image(
                        bitmap = remoteBitmap,
                        contentDescription = "车身展示主图",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(142.dp)
                            .padding(horizontal = 20.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(vehicleAppearance.imageResource),
                        contentDescription = "车身展示主图",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(142.dp)
                            .padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
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
private fun EnergyCapsuleProgressBar(
    progress: Float,
    color: Color,
    isCharging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
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
            color.copy(alpha = 0.72f * pulseAlpha),
            color.copy(alpha = pulseAlpha)
        )
    )

    Box(
        modifier = modifier
            .height(4.5.dp)
            .clip(CircleShape)
            .background(trackColor)
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
    onControl: (String) -> Unit
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

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
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
                                        onClick = {
                                            if (!editing) {
                                                when (trunkState) {
                                                    TrunkState.CLOSED -> onControl("trunkOpen")
                                                    TrunkState.OPEN -> onControl("trunkClose")
                                                    TrunkState.UNKNOWN -> Toast.makeText(
                                                        context,
                                                        "后备箱状态未知，请先刷新车况",
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
                                    onClick = {
                                        if (!editing) {
                                            when (command.name) {
                                                "trunk" -> {
                                                    when (trunkState) {
                                                        TrunkState.CLOSED -> onControl("trunkOpen")
                                                        TrunkState.OPEN -> onControl("trunkClose")
                                                        TrunkState.UNKNOWN -> Toast.makeText(
                                                            context,
                                                            "后备箱状态未知，请先刷新车况",
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
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    iconTint: Color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    labelTint: Color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "quickBtnScale"
    )
    val haptic = LocalHapticFeedback.current

    val isWarning = warning || iconTint == MaterialTheme.colorScheme.error
    val circleBg = when {
        isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f)
        isPressed -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val circleBorder = when {
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
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                Icon(
                    painterResource(iconRes),
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = labelTint,
            fontWeight = if (isWarning) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun HomeTirePressureCard(status: VehicleStatus?, modifier: Modifier = Modifier) {
    val tireByPosition = status?.tires.orEmpty().associateBy { it.position }
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val cardBorder = glassCardBorder()
    Surface(
        modifier = modifier.heightIn(min = 120.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = cardBorder,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier.matchParentSize()
            ) {
                val lineColor = outlineVariant.copy(alpha = 0.35f)
                val strokePx = 1.dp.toPx()
                val axleWidth = size.width * 0.32f
                val left = (size.width - axleWidth) / 2f
                val right = left + axleWidth
                val centerX = size.width / 2f
                drawLine(
                    color = lineColor,
                    start = Offset(centerX, 2f),
                    end = Offset(centerX, size.height - 2f),
                    strokeWidth = strokePx
                )
                drawLine(
                    color = lineColor,
                    start = Offset(left, size.height * 0.28f),
                    end = Offset(right, size.height * 0.28f),
                    strokeWidth = strokePx
                )
                drawLine(
                    color = lineColor,
                    start = Offset(left, size.height * 0.72f),
                    end = Offset(right, size.height * 0.72f),
                    strokeWidth = strokePx
                )
            }

            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeTireCell("左前", tireByPosition["左前"], Modifier.weight(1f))
                    HomeTireCell("右前", tireByPosition["右前"], Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeTireCell("左后", tireByPosition["左后"], Modifier.weight(1f))
                    HomeTireCell("右后", tireByPosition["右后"], Modifier.weight(1f))
                }
            }
        }
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
        modifier = modifier.heightIn(min = 120.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
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
    VehicleStatusMapper.displayPreciseSoc(status?.soc)
        ?: VehicleStatusMapper.displayPreciseSoc(status?.preciseSoc)
        ?: "--"

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
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.glassSurface
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
    val pagerState = rememberPagerState { EnergyHomePage.entries.size }
    Surface(
        modifier = modifier.height(184.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
    val totalMileageDisplay = data.totalMileage?.let { displayEnergyMetric(it, "km") }
        ?: vehicleTotalMileage?.takeIf { it.isNotBlank() && it != "--" }
        ?: "—"
    val daysHasValue = data.ownershipDays?.value != null
    val mileageHasValue = totalMileageDisplay != "—" && totalMileageDisplay != "--"
    val energyHasValue = data.cumulativeEnergy != null

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        EnergyHomeMetricLine(
            label = "提车时长",
            value = data.ownershipDays?.value?.let { "$it 天" } ?: "--",
            valueColor = if (daysHasValue) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant,
            valueBold = daysHasValue
        )
        Spacer(Modifier.height(8.dp))
        EnergyHomeMetricLine(
            label = "累计里程",
            value = totalMileageDisplay,
            valueColor = if (mileageHasValue) MaterialTheme.statusWarn else MaterialTheme.colorScheme.onSurfaceVariant,
            valueBold = mileageHasValue
        )
        Spacer(Modifier.height(8.dp))
        EnergyHomeMetricLine(
            label = "累计能耗",
            value = displayEnergyMetric(data.cumulativeEnergy, "kWh"),
            valueColor = if (energyHasValue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            valueBold = energyHasValue
        )
    }
}

@Composable
fun EnergyHomeWeeklyPage(data: EnergyAnalyticsData) {
    val points = EnergyHomeCardPolicy.recentTrend(data, limit = 6)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            displayEnergyMetric(data.overallConsumption, "kWh/100km"),
            style = MaterialTheme.typography.titleMedium.energyStyle(),
            fontWeight = FontWeight.Bold,
            color = if (data.overallConsumption == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.statusGood,
            maxLines = 1
        )
        if (points.isEmpty()) {
            EnergyHomeMissingData("暂无近 6 周能耗数据")
        } else {
            EnergyHomeBars(points, modifier = Modifier.padding(bottom = 2.dp))
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
            EnergyHomeLineChart(points)
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEachIndexed { index, point ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height((10 + 38 * (point.value / max).toFloat().coerceIn(0f, 1f)).dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            if (index == points.lastIndex) MaterialTheme.statusGood
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        )
                )
                Text(
                    point.value.formatEnergyNumber(),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall.energyStyle().copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
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
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.take(3).forEachIndexed { index, category ->
                        val label = EnergyCompositionPresentation.displayLabel(category.label)
                        val percent = EnergyCompositionPresentation.displayPercent(category.value, total)
                        val energyValue = "${category.value.formatEnergyNumber()}kWh"
                        val typeColor = energyCompositionColor(category.label, index)
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
                            Spacer(Modifier.width(5.dp))
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                energyValue,
                                style = MaterialTheme.typography.labelSmall.energyStyle(),
                                fontWeight = FontWeight.Bold,
                                color = typeColor,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                percent,
                                modifier = Modifier.width(46.dp),
                                style = MaterialTheme.typography.labelSmall.energyStyle(),
                                fontWeight = FontWeight.Bold,
                                color = typeColor,
                                textAlign = TextAlign.End,
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
private fun EnergyHomeCompositionDonut(
    categories: List<com.leapauto.app.EnergyCategory>,
    total: Double,
    modifier: Modifier = Modifier
) {
    val chartColors = categories.mapIndexed { index, category ->
        energyCompositionColor(category.label, index)
    }
    val emptyChartColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            var start = -90f
            val stroke = 7.dp.toPx()
            if (total <= 0.0) {
                drawArc(
                    color = emptyChartColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
            } else {
                categories.forEachIndexed { index, category ->
                    val sweep = (category.value.coerceAtLeast(0.0) / total * 360.0).toFloat()
                    drawArc(
                        color = chartColors[index % chartColors.size],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-2).dp)
        ) {
            val totalNumber = if (total > 0.0) total.formatEnergyNumber() else "--"
            Text(
                text = totalNumber,
                style = MaterialTheme.typography.titleSmall.energyStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "kWh",
                style = MaterialTheme.typography.labelSmall.energyStyle().copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EnergyHomeLineChart(points: List<com.leapauto.app.EnergySeriesPoint>) {
    val max = points.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: 1.0
    val min = points.minOfOrNull { it.value } ?: 0.0
    val chartColor = MaterialTheme.statusGood
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val pointRadius = 2.5.dp.toPx()
        val horizontalInset = 16.dp.toPx()
        val labelReserve = 14.dp.toPx()
        val dateReserve = 16.dp.toPx()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = 9.sp.toPx()
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
            drawCircle(chartColor, radius = pointRadius, center = center)
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
            val range = (max - min).takeIf { it > 0.0 } ?: 1.0
            val coords = points.mapIndexed { index, point ->
                Offset(
                    x = xStart + index * step,
                    y = plotBottom - ((point.value - min) / range).toFloat().coerceIn(0f, 1f) * plotHeight
                )
            }
            coords.zipWithNext().forEach { (start, end) ->
                drawLine(chartColor, start, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            coords.forEachIndexed { index, point ->
                drawCircle(chartColor, radius = pointRadius, center = point)
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
        color = MaterialTheme.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (status?.acSwitch == true && climateTone != ClimateTemperatureTone.DEFAULT) {
                temperatureColor.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        shadowElevation = 0.dp,
        modifier = modifier.height(56.dp)
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
    var settingsDirty by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        status?.acSetting,
        status?.acAirVolume,
        status?.windshieldDefrost,
        status?.recirculationMode,
        hvacCapability
    ) {
        if (!settingsDirty) {
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
        settingsDirty = false
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.glassSurface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                shadowElevation = 0.dp
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val tempLabel = if (status?.acSettingRight != null) {
                            "主 ${status.acSetting ?: "--"} · 副 ${status.acSettingRight}"
                        } else {
                            climateWholeNumber(status?.acSetting)?.let(::displayClimateTemperature) ?: "--"
                        }
                        ClimateStatusItem(
                            "设定温度",
                            tempLabel,
                            Modifier.weight(1f)
                        )
                        ClimateStatusItem("车内温度", status?.indoorTemp ?: "--", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ClimateStatusItem("当前风量", displayClimateFanLevel(status?.acAirVolume), Modifier.weight(1f))
                        ClimateStatusItem("当前循环", AirCircle.fromTelemetryValue(status?.recirculationMode)?.displayLabel ?: "--", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ClimateStatusItem("空调总开关", climateBooleanLabel(status?.acSwitch), Modifier.weight(1f))
                        ClimateStatusItem("后窗加热", when (status?.rearWindowHeating) {
                            true -> "已开启"
                            false -> "未开启"
                            null -> "--"
                        }, Modifier.weight(1f))
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.glassSurface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                shadowElevation = 0.dp
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    // 1. 温度滑块
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("温度设定", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$editedTemperature °C", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        ThinClimateSlider(
                            value = editedTemperature.toFloat(),
                            onValueChange = {
                                editedTemperature = it.roundToInt().coerceIn(hvacCapability.temperatureMinC, hvacCapability.temperatureMaxC)
                                settingsDirty = true
                            },
                            onValueChangeFinished = {},
                            enabled = actionsEnabled,
                            valueRange = hvacCapability.temperatureMinC.toFloat()..hvacCapability.temperatureMaxC.toFloat(),
                            steps = (hvacCapability.temperatureMaxC - hvacCapability.temperatureMinC - 1).coerceAtLeast(0),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 2. 风量滑块
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
                                    settingsDirty = true
                                },
                                onValueChangeFinished = {},
                                enabled = actionsEnabled,
                                valueRange = fanRange.first.toFloat()..fanRange.last.toFloat(),
                                steps = (fanRange.last - fanRange.first - 1).coerceAtLeast(0),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("当前车型仅支持该风量挡位", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // 3. 内外循环分段切换
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
                                    settingsDirty = true
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                label = { Text("内循环 (速冷/隔绝尾气)", style = MaterialTheme.typography.labelSmall) }
                            )
                            SegmentedButton(
                                selected = editedCircle == AirCircle.OUTER,
                                onClick = {
                                    editedCircle = AirCircle.OUTER
                                    settingsDirty = true
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                label = { Text("外循环 (引入新风)", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // 4. 出风方向分段切换
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
                                    settingsDirty = true
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                label = { Text("全车环绕出风", style = MaterialTheme.typography.labelSmall) }
                            )
                            SegmentedButton(
                                selected = editedOutletName == AirOutlet.WINDSHIELD.name,
                                onClick = {
                                    editedOutletName = AirOutlet.WINDSHIELD.name
                                    editedDefogging = true
                                    settingsDirty = true
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                label = { Text("前风挡除雾", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (settingsDirty) {
                        Button(
                            onClick = { submitSettings(HvacOperation.ON) },
                            enabled = actionsEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("应用空调设置")
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

            // ====== 座舱舒适状态面板 (座椅与方向盘遥测) ======
            val hasComfortTelemetry = status?.driverSeatHeating != null ||
                status?.driverSeatVentilation != null ||
                status?.passengerSeatHeating != null ||
                status?.passengerSeatVentilation != null ||
                status?.steeringWheelHeating != null

            if (hasComfortTelemetry) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.glassSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    shadowElevation = 0.dp
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("座舱舒适状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ComfortSeatStatusItem(
                                seatLabel = "主驾座椅",
                                heatLevel = status?.driverSeatHeating,
                                ventLevel = status?.driverSeatVentilation,
                                modifier = Modifier.weight(1f)
                            )
                            ComfortSeatStatusItem(
                                seatLabel = "副驾座椅",
                                heatLevel = status?.passengerSeatHeating,
                                ventLevel = status?.passengerSeatVentilation,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (status?.steeringWheelHeating != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.glassInsetSurface)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("方向盘加热", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (status.steeringWheelHeating == true) "♨️ 加热中" else "未开启",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (status.steeringWheelHeating == true) MaterialTheme.statusWarn else MaterialTheme.colorScheme.onSurfaceVariant
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
private fun ComfortSeatStatusItem(
    seatLabel: String,
    heatLevel: Int?,
    ventLevel: Int?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.glassInsetSurface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(seatLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("♨️ 加热", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = when (heatLevel) {
                        null -> "--"
                        0 -> "关闭"
                        1 -> "1 挡"
                        2 -> "2 挡"
                        3 -> "3 挡"
                        else -> "$heatLevel 挡"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (heatLevel != null && heatLevel > 0) MaterialTheme.statusWarn else MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("❄️ 通风", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = when (ventLevel) {
                        null -> "--"
                        0 -> "关闭"
                        1 -> "1 挡"
                        2 -> "2 挡"
                        3 -> "3 挡"
                        else -> "$ventLevel 挡"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (ventLevel != null && ventLevel > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                        "操作密码",
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
                    label = { Text("4 位数字操作密码") },
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

