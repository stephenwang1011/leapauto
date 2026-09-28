package com.leapauto.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/** Fixed 2x2 widget backed by the same confirmed snapshot and control path as [ControlWidget]. */
class CompactControlWidget : AppWidgetProvider() {

    companion object {
        internal fun hasInstances(context: Context): Boolean = widgetIds(context).isNotEmpty()

        internal fun baseViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_compact_layout).apply {
                val store = SessionStore(context)
                val themeContext = ControlWidget.widgetThemeContext(context)
                val darkTheme = ControlWidget.widgetUsesDarkAppearance(context)
                val session = store.load()
                val snapshot = store.loadSelectedWidgetSnapshot()
                val config = store.loadVehicleConfig(session.selectedVin, session.selectedCarType)
                val appearance = ControlWidget.resolveWidgetAppearance(
                    config,
                    snapshot?.carType ?: session.selectedCarType
                )
                setInt(
                    R.id.compactWidgetRoot,
                    "setBackgroundResource",
                    ControlWidget.resolveWidgetCardBackground(context, darkTheme)
                )
                setVehicleImage(this, appearance, session.selectedVin, context)
                applyStaticAppearance(themeContext, this)
                setTextViewText(R.id.txtWCTitle, ControlWidget.widgetTitle(config, appearance))
                setTextViewText(R.id.txtWCUpdated, snapshot?.updated?.removePrefix("今天")?.trim().orEmpty())
                val resolvedPowerType = VehiclePowerTypeResolver.resolve(
                    configuredPowerType = config.powerType,
                    carType = snapshot?.carType?.ifBlank { session.selectedCarType } ?: session.selectedCarType,
                    cachedPowerType = snapshot?.powerType,
                    hasFuelTelemetry = snapshot?.fuelRange != null || snapshot?.fuelSoc != null
                )
                applyRangePresentation(
                    themeContext,
                    this,
                    CompactWidgetRangePresentationMapper.fromValues(
                        range = snapshot?.range,
                        soc = snapshot?.soc,
                        powerType = resolvedPowerType,
                        electricRange = snapshot?.electricRange,
                        fuelRange = snapshot?.fuelRange,
                        electricSoc = snapshot?.soc,
                        fuelSoc = snapshot?.fuelSoc,
                        electricTotalRange = snapshot?.electricTotalRange,
                        fuelTotalRange = snapshot?.fuelTotalRange
                    ),
                    highContrast = false
                )
                applyLockPresentation(themeContext, this, snapshot?.locked)
                applyAcPresentation(themeContext, this, snapshot?.acEnabled, snapshot?.acTone ?: ClimateTemperatureTone.DEFAULT)
                setOnClickPendingIntent(R.id.compactWidgetRoot, ControlWidget.openAppPendingIntent(context))
            }

        fun refreshData(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CompactControlWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, baseViews(context))
        }

        fun refreshAppearance(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CompactControlWidget::class.java))
            if (ids.isEmpty()) return
            // Re-render the complete tree so values-night text/icon resources are rebound too.
            manager.updateAppWidget(ids, baseViews(context))
        }

        fun showControlStatus(
            context: Context,
            acEnabled: Boolean? = null,
            acTone: ClimateTemperatureTone = ClimateTemperatureTone.DEFAULT,
            locked: Boolean? = null
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CompactControlWidget::class.java))
            if (ids.isEmpty()) return
            val views = baseViews(context)
            if (acEnabled != null) {
                applyAcPresentation(context, views, acEnabled, acTone)
            }
            if (locked != null) {
                applyLockPresentation(context, views, locked)
            }
            manager.partiallyUpdateAppWidget(ids, views)
        }

        internal fun widgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(ComponentName(context, CompactControlWidget::class.java))
        }

        private fun setVehicleImage(
            views: RemoteViews,
            appearance: VehicleAppearance,
            vin: String = "",
            context: Context? = null
        ) {
            val remoteBitmap = if (vin.isNotBlank() && context != null) {
                VehicleImageCache.loadWidgetBitmap(context, vin)
            } else {
                null
            }
            if (remoteBitmap != null) {
                val scaledBitmap = ControlWidget.scaleBitmapForWidget(remoteBitmap, 320)
                views.setImageViewBitmap(R.id.imgWCCar, scaledBitmap)
                views.setViewVisibility(R.id.imgWCCar, View.VISIBLE)
            } else {
                views.setImageViewBitmap(R.id.imgWCCar, null)
                views.setViewVisibility(R.id.imgWCCar, View.INVISIBLE)
            }
        }

        private fun applyRangePresentation(
            context: Context,
            views: RemoteViews,
            presentation: CompactWidgetRangePresentation,
            highContrast: Boolean
        ) {
            views.setViewVisibility(
                R.id.compactRangeContainer,
                if (presentation.rangeExtender) View.GONE else View.VISIBLE
            )
            views.setViewVisibility(
                R.id.compactHybridRange,
                if (presentation.rangeExtender) View.VISIBLE else View.GONE
            )
            if (presentation.rangeExtender) {
                applyHybridRangePresentation(context, views, presentation, highContrast)
                return
            }
            views.setTextViewText(R.id.txtWCSocValue, presentation.socLabel)
            views.setTextViewText(R.id.txtWCRange, presentation.rangeLabel)
            val colorResource = if (!presentation.progressKnown) {
                R.color.widget_on_surface
            } else {
                when (presentation.tone) {
                    WidgetPureRangeTone.NORMAL -> R.color.widget_range_good
                    WidgetPureRangeTone.WARNING -> R.color.widget_range_warning
                    WidgetPureRangeTone.CRITICAL -> R.color.widget_range_critical
                }
            }
            val themeCtx = ControlWidget.widgetThemeContext(context)
            val onSurface = ContextCompat.getColor(themeCtx, R.color.widget_on_surface)
            val color = ContextCompat.getColor(themeCtx, highContrastRangeColorResource(colorResource, highContrast))
            views.setTextColor(R.id.txtWCRange, onSurface)
            views.setTextColor(R.id.txtWCSocValue, color)
            views.setTextColor(R.id.txtWCSocUnit, color)
            views.setContentDescription(
                R.id.compactRangeContainer,
                "剩余电量 ${presentation.socLabel}%，续航 ${presentation.rangeLabel}"
            )
        }

        private fun applyHybridRangePresentation(
            context: Context,
            views: RemoteViews,
            presentation: CompactWidgetRangePresentation,
            highContrast: Boolean
        ) {
            views.setTextViewText(R.id.txtWCElectricRange, presentation.electricRangeLabel)
            views.setTextViewText(R.id.txtWCFuelRange, presentation.fuelRangeLabel)
            views.setTextViewText(R.id.txtWCElectricSoc, "· " + presentation.electricSocLabel)
            views.setTextViewText(R.id.txtWCFuelSoc, "· " + presentation.fuelSocLabel)

            val electricColor = ContextCompat.getColor(
                context,
                highContrastRangeColorResource(
                    hybridColorResource(presentation.electricProgress, presentation.electricProgressKnown),
                    highContrast
                )
            )
            val fuelColor = ContextCompat.getColor(
                context,
                if (presentation.fuelProgressKnown) {
                    if (highContrast) R.color.widget_fuel_range_high_contrast else R.color.widget_fuel_range
                } else {
                    if (highContrast) R.color.widget_on_surface_high_contrast else R.color.widget_on_surface_variant
                }
            )
            views.setTextColor(R.id.txtWCElectricRange, electricColor)
            views.setTextColor(R.id.txtWCElectricSoc, electricColor)
            views.setTextColor(R.id.txtWCFuelRange, fuelColor)
            views.setTextColor(R.id.txtWCFuelSoc, fuelColor)
            ControlWidget.setImageTint(views, R.id.imgWCElectricIcon, electricColor)
            ControlWidget.setImageTint(views, R.id.imgWCFuelIcon, fuelColor)
            views.setContentDescription(
                R.id.compactHybridRange,
                "总续航 ${presentation.totalRangeLabel}，纯电 ${presentation.electricRangeLabel} ${presentation.electricSocLabel}，燃油 ${presentation.fuelRangeLabel} ${presentation.fuelSocLabel}"
            )
        }

        private fun hybridColorResource(progress: Int, known: Boolean): Int = when {
            !known -> R.color.widget_on_surface_variant
            WidgetPureRangeToneMapper.fromSoc(progress) == WidgetPureRangeTone.CRITICAL -> R.color.widget_range_critical
            progress <= 40 -> R.color.widget_range_warning
            else -> R.color.widget_range_good
        }

        private fun highContrastRangeColorResource(colorResource: Int, enabled: Boolean): Int {
            if (!enabled) return colorResource
            return when (colorResource) {
                R.color.widget_range_good -> R.color.widget_range_good_high_contrast
                R.color.widget_range_warning -> R.color.widget_range_warning_high_contrast
                R.color.widget_range_critical -> R.color.widget_range_critical_high_contrast
                R.color.widget_on_surface,
                R.color.widget_on_surface_variant -> R.color.widget_on_surface_high_contrast
                else -> colorResource
            }
        }

        private fun applyLockPresentation(context: Context, views: RemoteViews, locked: Boolean?) {
            val presentation = CompactWidgetLockPresentationMapper.fromState(locked)
            val themeContext = ControlWidget.widgetThemeContext(context)
            // 动作导向交互：车辆处于锁车状态时显示【解锁】按键；车辆处于解锁状态时显示【锁车】按键
            val iconResource = when (presentation.locked) {
                true -> R.drawable.ic_phosphor_lock_open
                false -> R.drawable.ic_phosphor_lock
                null -> R.drawable.ic_phosphor_lock
            }
            val iconColor = ContextCompat.getColor(themeContext, R.color.widget_action_icon)
            views.setImageViewResource(R.id.imgWCLock, iconResource)
            ControlWidget.setImageTint(views, R.id.imgWCLock, iconColor)
            views.setFloat(R.id.imgWCLock, "setAlpha", if (presentation.locked == null) 0.65f else 1f)
            views.setInt(
                R.id.btnWCLock,
                "setBackgroundResource",
                compactActionBackgroundResource(themeContext, CompactActionTone.NEUTRAL)
            )
            views.setViewVisibility(R.id.txtWCLock, View.GONE)
            views.setContentDescription(R.id.btnWCLock, presentation.contentDescription)
            views.setOnClickPendingIntent(
                R.id.btnWCLock,
                presentation.command?.let { ControlWidget.click(context, it) }
                    ?: ControlWidget.openAppPendingIntent(context)
            )
        }

        private fun applyAcPresentation(
            context: Context,
            views: RemoteViews,
            acEnabled: Boolean?,
            tone: ClimateTemperatureTone = ClimateTemperatureTone.DEFAULT
        ) {
            val presentation = WidgetAcMapper.presentation(acEnabled, tone)
            val themeContext = ControlWidget.widgetThemeContext(context)
            val actionColor = ContextCompat.getColor(themeContext, R.color.widget_action_icon)
            ControlWidget.setImageTint(views, R.id.imgWCAcOff, actionColor)

            val showCooling = presentation.showEnabledIcon && presentation.tone == ClimateTemperatureTone.COOLING
            val showHeating = presentation.showEnabledIcon && presentation.tone == ClimateTemperatureTone.HEATING
            val showVent = presentation.showEnabledIcon && presentation.tone == ClimateTemperatureTone.VENTILATION
            val showOff = !presentation.showEnabledIcon

            views.setViewVisibility(R.id.imgWCAcOff, if (showOff) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.imgWCAcOn, if (showCooling) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.imgWCAcOnHeating, if (showHeating) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.imgWCAcOnVent, if (showVent) View.VISIBLE else View.GONE)

            val backgroundTone = when {
                !presentation.showEnabledIcon -> CompactActionTone.NEUTRAL
                presentation.tone == ClimateTemperatureTone.HEATING -> CompactActionTone.SUCCESS
                presentation.tone == ClimateTemperatureTone.VENTILATION -> CompactActionTone.SUCCESS
                else -> CompactActionTone.PRIMARY
            }
            views.setInt(
                R.id.btnWCAc,
                "setBackgroundResource",
                compactActionBackgroundResource(themeContext, backgroundTone)
            )
            views.setViewVisibility(R.id.txtWCAc, View.GONE)
            views.setContentDescription(R.id.btnWCAc, presentation.contentDescription)
            views.setOnClickPendingIntent(
                R.id.btnWCAc,
                presentation.command?.let { ControlWidget.click(context, it) }
                    ?: ControlWidget.openAppPendingIntent(context)
            )
        }

        private fun applyStaticAppearance(context: Context, views: RemoteViews) {
            val onSurface = ContextCompat.getColor(context, R.color.widget_on_surface)
            val onSurfaceVariant = ContextCompat.getColor(context, R.color.widget_on_surface_variant)
            val actionIcon = ContextCompat.getColor(context, R.color.widget_action_icon)
            listOf(R.id.txtWCTitle, R.id.txtWCSocValue, R.id.txtWCSocUnit).forEach { id ->
                views.setTextColor(id, onSurface)
            }
            views.setTextColor(R.id.txtWCRange, onSurface)
            views.setTextColor(R.id.txtWCUpdated, onSurfaceVariant)
            views.setViewVisibility(R.id.txtWCLock, View.GONE)
            views.setViewVisibility(R.id.txtWCAc, View.GONE)
            ControlWidget.setImageTint(views, R.id.imgWCLock, actionIcon)
            ControlWidget.setImageTint(views, R.id.imgWCAcOff, actionIcon)
            views.setInt(
                R.id.btnWCLock,
                "setBackgroundResource",
                compactActionBackgroundResource(context, CompactActionTone.NEUTRAL)
            )
            views.setInt(
                R.id.btnWCAc,
                "setBackgroundResource",
                compactActionBackgroundResource(context, CompactActionTone.NEUTRAL)
            )
            applyProgressAppearance(context, views, false)
        }

        private fun applyProgressAppearance(context: Context, views: RemoteViews, highContrast: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            val track = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (highContrast) R.color.widget_range_track_high_contrast else R.color.widget_range_track
                )
            )
        }

        private fun compactActionBackgroundResource(context: Context, tone: CompactActionTone): Int {
            val darkTheme = ControlWidget.widgetUsesDarkAppearance(context)
            return when (tone) {
                CompactActionTone.NEUTRAL -> if (darkTheme) {
                    R.drawable.widget_compact_action_neutral_dark
                } else {
                    R.drawable.widget_compact_action_neutral_light
                }
                CompactActionTone.SUCCESS -> if (darkTheme) {
                    R.drawable.widget_compact_action_success_dark
                } else {
                    R.drawable.widget_compact_action_success_light
                }
                CompactActionTone.PRIMARY -> if (darkTheme) {
                    R.drawable.widget_compact_action_primary_dark
                } else {
                    R.drawable.widget_compact_action_primary_light
                }
            }
        }

        private enum class CompactActionTone {
            NEUTRAL,
            SUCCESS,
            PRIMARY
        }

    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ControlWidget.schedulePeriodicSync(context)
        ControlWidget.enqueueSync(context)
    }

    override fun onDisabled(context: Context) {
        if (!ControlWidget.hasInstances(context)) {
            ControlWidget.updateSyncCadence(context, null)
        }
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            refreshAppearance(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (SessionStore(context).isWidgetSyncSuppressed()) return
        ControlWidget.schedulePeriodicSync(context)
        appWidgetManager.updateAppWidget(appWidgetIds, baseViews(context.applicationContext))
        ControlWidget.enqueueSync(context)
    }
}
