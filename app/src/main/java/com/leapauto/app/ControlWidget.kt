package com.leapauto.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * 零跑桌面插件：续航和四轮胎压在后台同步，四个图标入口复用现有控车服务。
 * 车况同步失败时保留可操作布局，并显示最近一次成功同步的数据。
 */
class ControlWidget : AppWidgetProvider() {

    companion object {
        /** 在不重建布局的前提下，把控车进度回写到所有桌面插件实例。 */
        fun showControlStatus(context: Context, text: String, acEnabled: Boolean? = null, acTone: ClimateTemperatureTone = ClimateTemperatureTone.DEFAULT) {
            CompactControlWidget.showControlStatus(context, acEnabled, acTone)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ControlWidget::class.java))
            if (ids.isEmpty()) return
            val update = baseViews(context).apply {
                setTextViewText(R.id.txtWUpdated, text)
                if (acEnabled != null) {
                    applyAcPresentation(context, this, acEnabled, acTone)
                }
            }
            manager.partiallyUpdateAppWidget(ids, update)
        }

        /** 立即把本机保存的外观应用到所有已添加的小组件。 */
        fun refreshAppearance(context: Context) {
            CompactControlWidget.refreshAppearance(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ControlWidget::class.java))
            if (ids.isEmpty()) return
            // Re-render the complete tree so values-night text/icon resources are rebound too.
            manager.updateAppWidget(ids, baseViews(context))
        }

        fun enqueueSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "widget_status_sync",
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Runs one network sync after a widget command when vehicle telemetry
         * may arrive just after the bounded command-confirmation window.
         * This uses a separate work name so a parked cadence request cannot
         * suppress the command's follow-up refresh.
         */
        fun enqueueCommandSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                COMMAND_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /** Keeps a single silent refresh alive while at least one widget is installed. */
        fun schedulePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        /**
         * Uses a short silent refresh while the vehicle is moving and the normal
         * parked cadence otherwise. Unknown state stops both loops until the
         * next explicit widget update or a successful app refresh.
         */
        fun updateSyncCadence(
            context: Context,
            driving: Boolean?,
            rescheduleAfterCurrentSync: Boolean = false
        ) {
            if (driving == true) {
                cancelPeriodicSync(context)
                val request = OneTimeWorkRequestBuilder<WidgetSyncWorker>()
                    .setInitialDelay(DRIVING_SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(WidgetSyncWorker.KEY_DRIVING_SYNC to true))
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    RAPID_WORK_NAME,
                    if (rescheduleAfterCurrentSync) {
                        // Do not cancel the Worker that just fetched the current snapshot.
                        ExistingWorkPolicy.APPEND_OR_REPLACE
                    } else {
                        ExistingWorkPolicy.REPLACE
                    },
                    request
                )
            } else if (driving == false) {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(RAPID_WORK_NAME)
                schedulePeriodicSync(context)
            } else {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(RAPID_WORK_NAME)
                cancelPeriodicSync(context)
            }
        }

        /** Immediately renders the latest locally saved snapshot without starting another network call. */
        fun refreshData(context: Context) {
            val appContext = context.applicationContext
            CompactControlWidget.refreshData(appContext)
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, ControlWidget::class.java))
            if (ids.isEmpty()) return
            SessionStore(appContext).suppressWidgetSyncFor(10_000L)
            manager.updateAppWidget(ids, baseViews(appContext))
        }

        fun formatUpdatedTime(): String =
            SimpleDateFormat("'今天' HH:mm'更新'", Locale.CHINA).format(Date())

        internal fun baseViews(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_layout).apply {
            val store = SessionStore(context)
            val themeContext = widgetThemeContext(context)
            val opacity = store.loadWidgetOpacity()
            applyWidgetOpacity(this, opacity, widgetUsesDarkAppearance(context))
            applyStaticAppearance(themeContext, this, opacity)
            val snapshot = store.loadSelectedWidgetSnapshot()
            val session = store.load()
            val config = store.loadVehicleConfig(session.selectedVin, session.selectedCarType)
            val appearance = resolveWidgetAppearance(config, snapshot?.carType ?: session.selectedCarType)
            setVehicleImage(this, appearance, session.selectedVin, context)
            setTextViewText(R.id.txtWTitle, widgetTitle(config, appearance))
            snapshot?.let { snapshot ->
                val widgetPowerType = snapshot.powerType ?: config.powerType
                setTextViewText(R.id.txtWTitle, widgetTitle(config, appearance))
                setTextViewText(R.id.txtWRange, snapshot.range)
                applyRangePresentation(
                    context,
                    this,
                    WidgetRangePresentationMapper.fromValues(
                        totalRange = snapshot.range,
                        electricRange = snapshot.electricRange,
                        fuelRange = snapshot.fuelRange,
                        electricTotalRange = snapshot.electricTotalRange,
                        fuelTotalRange = snapshot.fuelTotalRange,
                        electricSocPercent = snapshot.soc,
                        fuelSocPercent = snapshot.fuelSoc,
                        powerType = widgetPowerType
                    )
                )
                applyPureRangeTone(context, this, snapshot.soc, widgetPowerType)
                setWidgetStatusText(
                    context,
                    this,
                    WidgetStatusMapper.presentation(
                        chargeState = snapshot.chargeState,
                        chargeRemainTime = snapshot.chargeRemainTime,
                        fallbackLabel = snapshot.statusLabel
                    )
                )
                val access = store.widgetAccess(snapshot, session)
                setTextViewText(R.id.txtWUpdated, when (access) {
                    SessionStore.WidgetAccess.CONTROL -> snapshot.updated
                    SessionStore.WidgetAccess.NO_SESSION -> "请打开App登录"
                })
            } ?: run {
                applyRangePresentation(context, this, WidgetRangePresentationMapper.fromValues(null, null, null, null))
                applyPureRangeTone(context, this, null, null)
                setWidgetStatusText(context, this, null)
                setTextViewText(
                    R.id.txtWUpdated,
                    when (store.widgetAccess(null, session)) {
                        SessionStore.WidgetAccess.CONTROL -> "等待同步"
                        SessionStore.WidgetAccess.NO_SESSION -> "请打开App登录"
                    }
                )
            }
            setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent(context))
            applyLockPresentation(context, this, snapshot?.locked)
            applyAcPresentation(context, this, snapshot?.acEnabled, snapshot?.acTone ?: ClimateTemperatureTone.DEFAULT)
            applyTrunkPresentation(context, this, snapshot?.trunkState ?: TrunkState.UNKNOWN)
            applySentryPresentation(context, this, snapshot?.sentryEnabled)
        }

        internal fun renderStatus(context: Context, views: RemoteViews, status: JSONObject, carType: String) {
            val store = SessionStore(context)
            val opacity = store.loadWidgetOpacity()
            val themeContext = widgetThemeContext(context)
            val darkTheme = widgetUsesDarkAppearance(context)
            applyWidgetOpacity(views, opacity, darkTheme)
            applyStaticAppearance(themeContext, views, opacity)
            val session = store.load()
            val config = store.loadVehicleConfig(session.selectedVin, carType)
            val displayStatus = VehicleStatusMapper.withFuelMock(
                status = status,
                vin = session.selectedVin,
                powerType = config.powerType
            )
            val appearance = resolveWidgetAppearance(config, carType)
            setVehicleImage(views, appearance, session.selectedVin, context)
            views.setTextViewText(R.id.txtWTitle, widgetTitle(config, appearance))
            val powerType = config.powerType?.let { if (it == SessionStore.VehiclePowerType.PURE_ELECTRIC) VehicleStatusMapper.PowerType.PURE_ELECTRIC else VehicleStatusMapper.PowerType.RANGE_EXTENDER }
            val soc = VehicleStatusMapper.electricSocPercent(displayStatus)
            views.setTextViewText(R.id.txtWRange, VehicleStatusMapper.widgetRange(displayStatus, carType, powerType) ?: "--")
            applyRangePresentation(
                context,
                views,
                WidgetRangePresentationMapper.fromValues(
                    totalRange = VehicleStatusMapper.widgetRange(displayStatus, carType, powerType),
                    electricRange = VehicleStatusMapper.electricRemainingRange(displayStatus),
                    fuelRange = VehicleStatusMapper.fuelRemainingRange(displayStatus),
                    electricTotalRange = VehicleStatusMapper.electricTotalRange(displayStatus),
                    fuelTotalRange = VehicleStatusMapper.fuelTotalRange(displayStatus),
                    electricSocPercent = VehicleStatusMapper.electricSocPercent(displayStatus),
                    fuelSocPercent = VehicleStatusMapper.fuelSocPercent(displayStatus),
                    powerType = config.powerType
                )
            )
            applyPureRangeTone(context, views, soc, config.powerType)
            setWidgetStatusText(context, views, WidgetStatusMapper.presentation(displayStatus, carType))
            applyLockPresentation(context, views, WidgetStatusMapper.locked(displayStatus))
            val acEnabled = WidgetAcMapper.state(displayStatus)
            val acTone = ClimateTemperatureToneResolver.tone(
                acEnabled = acEnabled,
                coolingAndHeating = displayStatus.optInt("acCoolingAndHeating", displayStatus.optInt("coolingAndHeating", -1)).takeIf { it != -1 },
                climateMode = displayStatus.optInt("climateMode", -1).takeIf { it != -1 },
                targetTemperature = displayStatus.opt("acSetting")?.toString()?.toIntOrNull()
            )
            applyAcPresentation(context, views, acEnabled, acTone)
            applyTrunkPresentation(context, views, TrunkStateMapper.fromSignal(displayStatus.opt("bbcmBackDoorStatus")))
            applySentryPresentation(context, views, WidgetSentryMapper.state(displayStatus))
        }

        internal fun renderSnapshot(context: Context, views: RemoteViews, snapshot: SessionStore.WidgetSnapshot) {
            val store = SessionStore(context)
            val opacity = store.loadWidgetOpacity()
            val themeContext = widgetThemeContext(context)
            val darkTheme = widgetUsesDarkAppearance(context)
            applyWidgetOpacity(views, opacity, darkTheme)
            applyStaticAppearance(themeContext, views, opacity)
            val session = store.load()
            val configVin = session.selectedVin.ifBlank { snapshot.vin }
            val config = store.loadVehicleConfig(configVin, snapshot.carType)
            val appearance = resolveWidgetAppearance(
                config,
                snapshot.carType.ifBlank { session.selectedCarType }
            )
            setVehicleImage(views, appearance, configVin, context)
            views.setTextViewText(R.id.txtWTitle, widgetTitle(config, appearance))
            val widgetPowerType = snapshot.powerType ?: config.powerType
            views.setTextViewText(R.id.txtWRange, snapshot.range)
            applyRangePresentation(
                context,
                views,
                WidgetRangePresentationMapper.fromValues(
                    totalRange = snapshot.range,
                    electricRange = snapshot.electricRange,
                    fuelRange = snapshot.fuelRange,
                    electricTotalRange = snapshot.electricTotalRange,
                    fuelTotalRange = snapshot.fuelTotalRange,
                    electricSocPercent = snapshot.soc,
                    fuelSocPercent = snapshot.fuelSoc,
                    powerType = widgetPowerType
                )
            )
            applyPureRangeTone(context, views, snapshot.soc, widgetPowerType)
            setWidgetStatusText(
                context,
                views,
                WidgetStatusMapper.presentation(
                    chargeState = snapshot.chargeState,
                    chargeRemainTime = snapshot.chargeRemainTime,
                    fallbackLabel = snapshot.statusLabel.takeUnless {
                        snapshot.carType.contains("T03", ignoreCase = true) && it == "车窗未关闭"
                    }
                )
            )
            views.setTextViewText(R.id.txtWUpdated, snapshot.updated)
            applyLockPresentation(context, views, snapshot.locked)
            applyAcPresentation(context, views, snapshot.acEnabled, snapshot.acTone)
            applyTrunkPresentation(context, views, snapshot.trunkState)
            applySentryPresentation(context, views, snapshot.sentryEnabled)
        }

        /** Unknown telemetry must not be treated as a safe state for a control command. */
        private fun applyAcPresentation(
            context: Context,
            views: RemoteViews,
            acEnabled: Boolean?,
            tone: ClimateTemperatureTone = ClimateTemperatureTone.DEFAULT
        ) {
            val presentation = WidgetAcMapper.presentation(acEnabled, tone)
            val themeContext = widgetThemeContext(context)
            val actionColor = ContextCompat.getColor(themeContext, R.color.widget_action_icon)
            views.setInt(R.id.imgWAcOff, "setColorFilter", actionColor)
            views.setInt(
                R.id.btnWAc,
                "setBackgroundResource",
                widgetActionBackgroundResource(widgetUsesDarkAppearance(context))
            )

            val showCooling = presentation.showEnabledIcon && presentation.tone == ClimateTemperatureTone.COOLING
            val showHeating = presentation.showEnabledIcon && presentation.tone == ClimateTemperatureTone.HEATING
            val showVent = presentation.showEnabledIcon && presentation.tone == ClimateTemperatureTone.VENTILATION
            val showOff = !presentation.showEnabledIcon

            views.setViewVisibility(R.id.imgWAcOff, if (showOff) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.imgWAcOn, if (showCooling) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.imgWAcOnHeating, if (showHeating) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.imgWAcOnVent, if (showVent) View.VISIBLE else View.GONE)

            views.setContentDescription(R.id.btnWAc, presentation.contentDescription)
            val pendingIntent = presentation.command
                ?.let { command -> click(context, command) }
                ?: openAppPendingIntent(context)
            views.setOnClickPendingIntent(R.id.btnWAc, pendingIntent)
        }

        private fun applyLockPresentation(context: Context, views: RemoteViews, locked: Boolean?) {
            val themeContext = widgetThemeContext(context)
            val iconColor = ContextCompat.getColor(themeContext, R.color.widget_action_icon)
            views.setImageViewResource(R.id.btnWUnlock, R.drawable.ic_phosphor_lock_open)
            views.setImageViewResource(R.id.btnWLock, R.drawable.ic_phosphor_lock)
            views.setInt(R.id.btnWUnlock, "setColorFilter", iconColor)
            views.setInt(R.id.btnWLock, "setColorFilter", iconColor)
            val background = widgetActionBackgroundResource(widgetUsesDarkAppearance(context))
            views.setInt(R.id.btnWUnlock, "setBackgroundResource", background)
            views.setInt(R.id.btnWLock, "setBackgroundResource", background)
            views.setContentDescription(
                R.id.btnWUnlock,
                if (locked == false) "解锁（当前已解锁）" else "解锁"
            )
            views.setContentDescription(
                R.id.btnWLock,
                if (locked == true) "上锁（当前已上锁）" else "上锁"
            )
            views.setOnClickPendingIntent(R.id.btnWUnlock, click(context, "unlock"))
            views.setOnClickPendingIntent(R.id.btnWLock, click(context, "lock"))
        }

        /** Binds the trunk action to confirmed telemetry; unknown never guesses a direction. */
        private fun applyTrunkPresentation(context: Context, views: RemoteViews, trunkState: TrunkState) {
            val presentation = TrunkControlPresentationMapper.fromState(trunkState)
            val themeContext = widgetThemeContext(context)
            val iconColor = ContextCompat.getColor(themeContext, R.color.widget_action_icon)
            views.setImageViewResource(R.id.btnWTrunk, R.drawable.ic_phosphor_trunk_open)
            views.setInt(R.id.btnWTrunk, "setColorFilter", iconColor)
            val background = widgetActionBackgroundResource(widgetUsesDarkAppearance(context))
            views.setInt(R.id.btnWTrunk, "setBackgroundResource", background)
            views.setContentDescription(R.id.btnWTrunk, presentation.contentDescription)
            views.setFloat(R.id.btnWTrunk, "setAlpha", if (trunkState == TrunkState.UNKNOWN) 0.65f else 1f)
            views.setOnClickPendingIntent(
                R.id.btnWTrunk,
                presentation.command?.let { click(context, it) } ?: openAppPendingIntent(context)
            )
        }

        private fun applySentryPresentation(context: Context, views: RemoteViews, sentryEnabled: Boolean?) {
            val presentation = WidgetSentryMapper.presentation(sentryEnabled)
            val themeContext = widgetThemeContext(context)
            val actionColor = ContextCompat.getColor(themeContext, R.color.widget_action_icon)
            views.setImageViewResource(R.id.imgWSentryOff, R.drawable.ic_sentry)
            views.setImageViewResource(R.id.imgWSentryOn, R.drawable.ic_sentry)
            views.setInt(R.id.imgWSentryOff, "setColorFilter", actionColor)
            views.setInt(R.id.imgWSentryOn, "setColorFilter", ContextCompat.getColor(themeContext, R.color.energy_green))
            val background = widgetActionBackgroundResource(widgetUsesDarkAppearance(context))
            views.setInt(R.id.btnWSentry, "setBackgroundResource", background)
            views.setViewVisibility(
                R.id.imgWSentryOff,
                if (presentation.showEnabledIcon) View.GONE else View.VISIBLE
            )
            views.setViewVisibility(
                R.id.imgWSentryOn,
                if (presentation.showEnabledIcon) View.VISIBLE else View.GONE
            )
            views.setContentDescription(R.id.btnWSentry, presentation.contentDescription)
            views.setOnClickPendingIntent(
                R.id.btnWSentry,
                click(context, presentation.command)
            )
        }

        private fun setWidgetStatusText(
            context: Context,
            views: RemoteViews,
            presentation: WidgetStatusPresentation?
        ) {
            val text = presentation?.label
            val visible = !text.isNullOrBlank()
            views.setViewVisibility(R.id.widgetStatusContainer, if (visible) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.txtWGeneralStatus, if (visible) View.VISIBLE else View.GONE)
            views.setViewVisibility(
                R.id.imgWChargingStatus,
                if (visible && presentation?.showChargingIcon == true) View.VISIBLE else View.GONE
            )
            if (visible) {
                views.setTextViewText(R.id.txtWGeneralStatus, text)
                views.setTextColor(
                    R.id.txtWGeneralStatus,
                    ContextCompat.getColor(widgetThemeContext(context), widgetStatusColorResource(text))
                )
            }
        }

        private fun applyRangePresentation(
            context: Context,
            views: RemoteViews,
            presentation: WidgetRangePresentation
        ) {
            val hybridVisibility = if (presentation.rangeExtender) View.VISIBLE else View.GONE
            val pureVisibility = if (presentation.rangeExtender) View.GONE else View.VISIBLE
            views.setViewVisibility(R.id.widgetPureRange, pureVisibility)
            views.setViewVisibility(R.id.widgetHybridRange, hybridVisibility)
            if (!presentation.rangeExtender) return
            views.setTextViewText(R.id.txtWElectricRange, formatWidgetRangeLabel(presentation.electricRange))
            views.setTextViewText(R.id.txtWFuelRange, formatWidgetRangeLabel(presentation.fuelRange))
            applyHybridRangeTone(
                views = views,
                progress = presentation.electricProgress,
                known = presentation.electricProgressKnown,
                normalId = R.id.progressWElectric,
                warningId = R.id.progressWElectricWarning,
                criticalId = R.id.progressWElectricCritical
            )
            applyHybridRangeTone(
                views = views,
                progress = presentation.fuelProgress,
                known = presentation.fuelProgressKnown,
                normalId = R.id.progressWFuel,
                warningId = R.id.progressWFuelWarning,
                criticalId = R.id.progressWFuelCritical
            )
            val electricColor = hybridRangeColorResource(presentation.electricProgress, presentation.electricProgressKnown)
            val fuelColor = hybridRangeColorResource(presentation.fuelProgress, presentation.fuelProgressKnown)
            val themeContext = widgetThemeContext(context)
            val highContrast = SessionStore(context).loadWidgetOpacity() == 25
            val electricColorValue = ContextCompat.getColor(
                themeContext,
                highContrastRangeColorResource(electricColor, highContrast)
            )
            val fuelColorValue = ContextCompat.getColor(
                themeContext,
                highContrastRangeColorResource(fuelColor, highContrast)
            )
            views.setImageViewResource(R.id.imgWElectricRangeIcon, R.drawable.ic_hybrid_electric)
            views.setImageViewResource(R.id.imgWFuelRangeIcon, R.drawable.ic_hybrid_fuel)
            views.setInt(R.id.imgWElectricRangeIcon, "setColorFilter", electricColorValue)
            views.setInt(R.id.imgWFuelRangeIcon, "setColorFilter", fuelColorValue)
            views.setTextColor(R.id.txtWElectricRange, electricColorValue)
            views.setTextColor(R.id.txtWFuelRange, fuelColorValue)
            views.setContentDescription(
                R.id.widgetHybridRange,
                "纯电续航 ${formatWidgetRangeLabel(presentation.electricRange)}，剩余 " +
                    "${progressLabel(presentation.electricProgress, presentation.electricProgressKnown)}；" +
                    "燃油续航 ${formatWidgetRangeLabel(presentation.fuelRange)}，剩余 " +
                    progressLabel(presentation.fuelProgress, presentation.fuelProgressKnown)
            )
        }

        private fun applyHybridRangeTone(
            views: RemoteViews,
            progress: Int,
            known: Boolean,
            normalId: Int,
            warningId: Int,
            criticalId: Int
        ) {
            val tone = if (known) WidgetPureRangeToneMapper.fromSoc(progress) else WidgetPureRangeTone.NORMAL
            views.setProgressBar(normalId, 100, progress, false)
            views.setProgressBar(warningId, 100, progress, false)
            views.setProgressBar(criticalId, 100, progress, false)
            views.setViewVisibility(normalId, if (tone == WidgetPureRangeTone.NORMAL) View.VISIBLE else View.GONE)
            views.setViewVisibility(warningId, if (tone == WidgetPureRangeTone.WARNING) View.VISIBLE else View.GONE)
            views.setViewVisibility(criticalId, if (tone == WidgetPureRangeTone.CRITICAL) View.VISIBLE else View.GONE)
        }

        private fun hybridRangeColorResource(progress: Int, known: Boolean): Int {
            if (!known) return R.color.widget_on_surface_variant
            return when (WidgetPureRangeToneMapper.fromSoc(progress)) {
            WidgetPureRangeTone.NORMAL -> R.color.widget_range_good
            WidgetPureRangeTone.WARNING -> R.color.widget_range_warning
            WidgetPureRangeTone.CRITICAL -> R.color.widget_range_critical
            }
        }

        private fun progressLabel(progress: Int, known: Boolean): String =
            if (known) "$progress%" else "数据待同步"

        private fun applyPureRangeTone(
            context: Context,
            views: RemoteViews,
            soc: Int?,
            powerType: SessionStore.VehiclePowerType?
        ) {
            val progress = soc?.coerceIn(0, 100) ?: 0
            val tone = if (powerType == SessionStore.VehiclePowerType.PURE_ELECTRIC && soc != null) {
                WidgetPureRangeToneMapper.fromSoc(soc)
            } else {
                WidgetPureRangeTone.NORMAL
            }
            views.setProgressBar(R.id.progressWCharge, 100, progress, false)
            views.setProgressBar(R.id.progressWChargeWarning, 100, progress, false)
            views.setProgressBar(R.id.progressWChargeCritical, 100, progress, false)
            views.setViewVisibility(
                R.id.progressWCharge,
                if (tone == WidgetPureRangeTone.NORMAL) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.progressWChargeWarning,
                if (tone == WidgetPureRangeTone.WARNING) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.progressWChargeCritical,
                if (tone == WidgetPureRangeTone.CRITICAL) View.VISIBLE else View.GONE
            )
            val colorResource = if (powerType != SessionStore.VehiclePowerType.PURE_ELECTRIC || soc == null) {
                R.color.widget_on_surface
            } else {
                when (tone) {
                    WidgetPureRangeTone.NORMAL -> R.color.widget_range_good
                    WidgetPureRangeTone.WARNING -> R.color.widget_range_warning
                    WidgetPureRangeTone.CRITICAL -> R.color.widget_range_critical
                }
            }
            val color = ContextCompat.getColor(
                widgetThemeContext(context),
                highContrastRangeColorResource(
                    colorResource,
                    SessionStore(context).loadWidgetOpacity() == 25
                )
            )
            views.setTextColor(R.id.txtWRange, color)
            views.setTextColor(R.id.txtWRangeUnit, color)
        }

        private fun highContrastRangeColorResource(colorResource: Int, enabled: Boolean): Int {
            if (!enabled) return colorResource
            return when (colorResource) {
                R.color.widget_range_good -> R.color.widget_range_good_high_contrast
                R.color.widget_range_warning -> R.color.widget_range_warning_high_contrast
                R.color.widget_range_critical -> R.color.widget_range_critical_high_contrast
                R.color.widget_fuel_range -> R.color.widget_fuel_range_high_contrast
                R.color.widget_on_surface,
                R.color.widget_on_surface_variant -> R.color.widget_on_surface_high_contrast
                else -> colorResource
            }
        }

        private fun widgetStatusColorResource(text: String?): Int = when {
            text?.startsWith("剩余") == true -> R.color.energy_green
            text == "行驶中" -> R.color.leap_blue
            text == "充电中" || text == "充电完成" -> R.color.energy_green
            text == "车窗未关闭" -> R.color.alert_red
            else -> R.color.widget_on_surface_variant
        }


        internal fun formatVehicleModel(carType: String): String {
            val model = carType.trim()
            return when {
                model.isBlank() -> "零跑"
                model.startsWith("零跑") -> model
                else -> "零跑 $model"
            }
        }

        /** 车辆昵称优先，未设置昵称时回退到车型名。 */
        internal fun widgetTitle(config: SessionStore.VehicleConfig, appearance: VehicleAppearance): String =
            config.nickname.trim().takeIf { it.isNotEmpty() } ?: formatVehicleModel(appearance.model)

        /**
         * Appearance configuration is display-only. The reported carType remains
         * the source for range parsing, snapshots, status text, refresh, and commands.
         */
        internal fun resolveWidgetAppearance(
            config: SessionStore.VehicleConfig,
            reportedCarType: String
        ): VehicleAppearance {
            val displayModel = VehicleStatusMapper.resolveDisplayModel(config.model, reportedCarType)
            return VehicleAppearanceCatalog.resolveAppearance(displayModel, config.color)
        }

        internal fun scaleBitmapForWidget(bitmap: android.graphics.Bitmap, targetWidth: Int = 400): android.graphics.Bitmap {
            if (bitmap.width <= targetWidth) return bitmap
            val targetHeight = (bitmap.height * (targetWidth.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
            return android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        }

        private fun setVehicleImage(
            views: RemoteViews,
            appearance: VehicleAppearance,
            vin: String = "",
            context: Context? = null
        ) {
            val remoteBitmap = if (vin.isNotBlank() && context != null) {
                VehicleImageCache.loadCachedBitmap(context, vin)
            } else {
                null
            }
            if (remoteBitmap != null) {
                val scaledBitmap = scaleBitmapForWidget(remoteBitmap, 400)
                views.setImageViewBitmap(R.id.imgWCar, scaledBitmap)
            } else {
                views.setImageViewResource(
                    R.id.imgWCar,
                    appearance.imageResource
                )
            }
        }

        internal fun openAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        internal fun click(context: Context, command: String): PendingIntent {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val verificationEnabled =
                SessionStore(context).loadWidgetSensitiveActionVerificationEnabled()
            return if (WidgetControlSecurity.requiresVerification(command, verificationEnabled)) {
                val intent = Intent(context, ControlConfirmActivity::class.java)
                    .putExtra(ControlConfirmActivity.EXTRA_COMMAND, command)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                PendingIntent.getActivity(context, command.hashCode(), intent, flags)
            } else {
                val intent = Intent(context, ControlService::class.java)
                    .putExtra(ControlService.EXTRA_COMMAND, command)
                PendingIntent.getForegroundService(context, command.hashCode(), intent, flags)
            }
        }

        internal fun widgetBackgroundResource(opacity: Int): Int = when (opacity) {
            75 -> R.drawable.widget_card_background_75
            50 -> R.drawable.widget_card_background_50
            25 -> R.drawable.widget_card_background_25
            else -> R.drawable.widget_card_background
        }

        internal fun widgetBackgroundResource(opacity: Int, darkTheme: Boolean): Int = when {
            darkTheme && opacity == 75 -> R.drawable.widget_card_background_75_dark
            darkTheme && opacity == 50 -> R.drawable.widget_card_background_50_dark
            darkTheme && opacity == 25 -> R.drawable.widget_card_background_25_dark
            darkTheme -> R.drawable.widget_card_background_dark
            opacity == 75 -> R.drawable.widget_card_background_75_light
            opacity == 50 -> R.drawable.widget_card_background_50_light
            opacity == 25 -> R.drawable.widget_card_background_25_light
            else -> R.drawable.widget_card_background_light
        }

        internal fun widgetUsesDarkAppearance(context: Context): Boolean {
            val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            return SessionStore(context).loadAppearanceMode().resolvesToDark(systemDark)
        }

        internal fun widgetThemeContext(context: Context): Context {
            val darkTheme = widgetUsesDarkAppearance(context)
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or
                    if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            return context.createConfigurationContext(configuration)
        }

        private fun widgetActionBackgroundResource(darkTheme: Boolean): Int =
            if (darkTheme) R.drawable.widget_action_background_dark else R.drawable.widget_action_background_light

        private fun applyWidgetOpacity(views: RemoteViews, opacity: Int, darkTheme: Boolean) {
            views.setInt(R.id.widgetRoot, "setBackgroundResource", widgetBackgroundResource(opacity, darkTheme))
        }

        private fun applyStaticAppearance(context: Context, views: RemoteViews, opacity: Int) {
            val onSurface = ContextCompat.getColor(context, R.color.widget_on_surface)
            val onSurfaceVariant = ContextCompat.getColor(context, R.color.widget_on_surface_variant)
            val actionIcon = ContextCompat.getColor(context, R.color.widget_action_icon)
            views.setTextColor(R.id.txtWTitle, onSurface)
            views.setTextColor(R.id.txtWUpdated, onSurfaceVariant)
            views.setTextColor(R.id.txtWRange, onSurface)
            views.setTextColor(R.id.txtWRangeUnit, onSurface)
            views.setTextColor(R.id.txtWGeneralStatus, onSurfaceVariant)
            listOf(R.id.btnWUnlock, R.id.btnWLock, R.id.imgWSentryOff, R.id.imgWAcOff, R.id.btnWTrunk).forEach { id ->
                views.setInt(id, "setColorFilter", actionIcon)
            }
            val actionBackground = widgetActionBackgroundResource(
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            )
            listOf(R.id.btnWUnlock, R.id.btnWLock, R.id.btnWSentry, R.id.btnWAc, R.id.btnWTrunk).forEach { id ->
                views.setInt(id, "setBackgroundResource", actionBackground)
            }
            applyProgressAppearance(context, views, opacity == 25)
        }

        private fun applyProgressAppearance(context: Context, views: RemoteViews, highContrast: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            val track = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (highContrast) R.color.widget_range_track_high_contrast else R.color.widget_range_track
                )
            )
            val tones = listOf(
                (if (highContrast) R.color.widget_range_good_high_contrast else R.color.widget_range_good) to
                    listOf(R.id.progressWCharge, R.id.progressWElectric, R.id.progressWFuel),
                (if (highContrast) R.color.widget_range_warning_high_contrast else R.color.widget_range_warning) to listOf(
                    R.id.progressWChargeWarning,
                    R.id.progressWElectricWarning,
                    R.id.progressWFuelWarning
                ),
                (if (highContrast) R.color.widget_range_critical_high_contrast else R.color.widget_range_critical) to listOf(
                    R.id.progressWChargeCritical,
                    R.id.progressWElectricCritical,
                    R.id.progressWFuelCritical
                )
            )
            tones.forEach { (colorResource, viewIds) ->
                val tint = ColorStateList.valueOf(ContextCompat.getColor(context, colorResource))
                viewIds.forEach { id ->
                    views.setColorStateList(id, "setProgressTintList", tint)
                    views.setColorStateList(id, "setProgressBackgroundTintList", track)
                }
            }
        }

        internal fun hasInstances(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context.applicationContext)
            return manager.getAppWidgetIds(
                ComponentName(context.applicationContext, ControlWidget::class.java)
            ).isNotEmpty()
        }


        private const val PERIODIC_WORK_NAME = "widget_status_periodic_sync"
        private const val RAPID_WORK_NAME = "widget_status_rapid_sync"
        private const val COMMAND_SYNC_WORK_NAME = "widget_command_sync"
        private const val DRIVING_SYNC_INTERVAL_SECONDS = 10L
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicSync(context)
        enqueueSync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            refreshAppearance(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDisabled(context: Context) {
        if (!CompactControlWidget.hasInstances(context)) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(RAPID_WORK_NAME)
            cancelPeriodicSync(context)
        }
        super.onDisabled(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (SessionStore(context).isWidgetSyncSuppressed()) return
        schedulePeriodicSync(context)
        val views = baseViews(context.applicationContext)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
        enqueueSync(context)
    }
}
