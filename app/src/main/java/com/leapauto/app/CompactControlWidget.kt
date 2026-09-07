package com.leapauto.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
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
                val opacity = store.loadWidgetOpacity()
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
                    ControlWidget.widgetBackgroundResource(opacity, darkTheme)
                )
                applyStaticAppearance(themeContext, this, opacity)
                setTextViewText(R.id.txtWCTitle, ControlWidget.widgetTitle(config, appearance))
                applyRangePresentation(
                    themeContext,
                    this,
                    CompactWidgetRangePresentationMapper.fromValues(
                        range = snapshot?.range,
                        soc = snapshot?.soc,
                        powerType = snapshot?.powerType ?: config.powerType,
                        electricRange = snapshot?.electricRange,
                        fuelRange = snapshot?.fuelRange,
                        electricSoc = snapshot?.soc,
                        fuelSoc = snapshot?.fuelSoc,
                        electricTotalRange = snapshot?.electricTotalRange,
                        fuelTotalRange = snapshot?.fuelTotalRange
                    ),
                    highContrast = opacity == 25
                )
                applyLockPresentation(themeContext, this, snapshot?.locked)
                applyAcPresentation(themeContext, this, snapshot?.acEnabled, snapshot?.acTone ?: ClimateTemperatureTone.DEFAULT)
                setOnClickPendingIntent(R.id.compactWidgetRoot, ControlWidget.openAppPendingIntent(context))
            }

        fun refreshData(context: Context) {
            val appContext = context.applicationContext
            val ids = widgetIds(appContext)
            if (ids.isEmpty()) return
            AppWidgetManager.getInstance(appContext).updateAppWidget(ids, baseViews(appContext))
        }

        fun refreshAppearance(context: Context) {
            val appContext = context.applicationContext
            val ids = widgetIds(appContext)
            if (ids.isEmpty()) return
            // Re-render the complete tree so values-night text/icon resources are rebound too.
            AppWidgetManager.getInstance(appContext).updateAppWidget(ids, baseViews(appContext))
        }

        fun showControlStatus(context: Context, acEnabled: Boolean?, acTone: ClimateTemperatureTone = ClimateTemperatureTone.DEFAULT) {
            val appContext = context.applicationContext
            val ids = widgetIds(appContext)
            if (ids.isEmpty()) return
            val views = baseViews(appContext)
            if (acEnabled != null) {
                applyAcPresentation(appContext, views, acEnabled, acTone)
            }
            AppWidgetManager.getInstance(appContext).partiallyUpdateAppWidget(ids, views)
        }

        private fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context.applicationContext).getAppWidgetIds(
                ComponentName(context.applicationContext, CompactControlWidget::class.java)
            )

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
            views.setViewVisibility(
                R.id.compactPureProgress,
                if (presentation.rangeExtender) View.GONE else View.VISIBLE
            )
            if (presentation.rangeExtender) {
                applyHybridRangePresentation(context, views, presentation, highContrast)
                return
            }
            views.setTextViewText(R.id.txtWCSocValue, presentation.socLabel)
            views.setTextViewText(R.id.txtWCRange, presentation.rangeLabel)
            views.setProgressBar(R.id.progressWCNormal, 100, presentation.progress, false)
            views.setProgressBar(R.id.progressWCWarning, 100, presentation.progress, false)
            views.setProgressBar(R.id.progressWCCritical, 100, presentation.progress, false)
            views.setViewVisibility(
                R.id.progressWCNormal,
                if (presentation.tone == WidgetPureRangeTone.NORMAL) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.progressWCWarning,
                if (presentation.tone == WidgetPureRangeTone.WARNING) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.progressWCCritical,
                if (presentation.tone == WidgetPureRangeTone.CRITICAL) View.VISIBLE else View.GONE
            )
            val colorResource = if (!presentation.progressKnown) {
                R.color.widget_on_surface
            } else {
                when (presentation.tone) {
                    WidgetPureRangeTone.NORMAL -> R.color.widget_range_good
                    WidgetPureRangeTone.WARNING -> R.color.widget_range_warning
                    WidgetPureRangeTone.CRITICAL -> R.color.widget_range_critical
                }
            }
            val color = ContextCompat.getColor(context, highContrastRangeColorResource(colorResource, highContrast))
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
            views.setTextViewText(R.id.txtWCElectricSoc, presentation.electricSocLabel)
            views.setTextViewText(R.id.txtWCFuelSoc, presentation.fuelSocLabel)
            applyHybridProgress(
                views,
                presentation.electricProgress,
                presentation.electricProgressKnown,
                R.id.progressWCElectric,
                R.id.progressWCElectricWarning,
                R.id.progressWCElectricCritical
            )
            views.setProgressBar(R.id.progressWCFuel, 100, presentation.fuelProgress, false)
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
            views.setInt(R.id.imgWCElectricIcon, "setColorFilter", electricColor)
            views.setInt(R.id.imgWCFuelIcon, "setColorFilter", fuelColor)
            views.setContentDescription(
                R.id.compactHybridRange,
                "总续航 ${presentation.totalRangeLabel}，纯电 ${presentation.electricRangeLabel} ${presentation.electricSocLabel}，燃油 ${presentation.fuelRangeLabel} ${presentation.fuelSocLabel}"
            )
        }

        private fun applyHybridProgress(
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
            val iconResource = when (presentation.locked) {
                true -> R.drawable.ic_phosphor_lock
                false -> R.drawable.ic_phosphor_lock_open
                null -> R.drawable.ic_phosphor_lock
            }
            val colorResource = when (presentation.locked) {
                true, false -> R.color.energy_green
                null -> R.color.widget_on_surface_variant
            }
            views.setImageViewResource(R.id.imgWCLock, iconResource)
            views.setInt(
                R.id.imgWCLock,
                "setColorFilter",
                ContextCompat.getColor(themeContext, colorResource)
            )
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
            val actionColor = ContextCompat.getColor(themeContext, R.color.widget_on_surface_variant)
            views.setInt(R.id.imgWCAcOff, "setColorFilter", actionColor)

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

        private fun applyStaticAppearance(context: Context, views: RemoteViews, opacity: Int) {
            val onSurface = ContextCompat.getColor(context, R.color.widget_on_surface)
            val onSurfaceVariant = ContextCompat.getColor(context, R.color.widget_on_surface_variant)
            listOf(R.id.txtWCTitle, R.id.txtWCSocValue, R.id.txtWCSocUnit).forEach { id ->
                views.setTextColor(id, onSurface)
            }
            views.setTextColor(R.id.txtWCRange, onSurfaceVariant)
            views.setViewVisibility(R.id.txtWCLock, View.GONE)
            views.setViewVisibility(R.id.txtWCAc, View.GONE)
            views.setInt(R.id.imgWCLock, "setColorFilter", onSurfaceVariant)
            views.setInt(R.id.imgWCAcOff, "setColorFilter", onSurfaceVariant)
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
                    listOf(R.id.progressWCNormal, R.id.progressWCElectric),
                (if (highContrast) R.color.widget_range_warning_high_contrast else R.color.widget_range_warning) to
                    listOf(R.id.progressWCWarning, R.id.progressWCElectricWarning),
                (if (highContrast) R.color.widget_range_critical_high_contrast else R.color.widget_range_critical) to
                    listOf(R.id.progressWCCritical, R.id.progressWCElectricCritical)
            )
            tones.forEach { (colorResource, viewIds) ->
                val tint = ColorStateList.valueOf(ContextCompat.getColor(context, colorResource))
                viewIds.forEach { id ->
                    views.setColorStateList(id, "setProgressTintList", tint)
                    views.setColorStateList(id, "setProgressBackgroundTintList", track)
                }
            }
            views.setColorStateList(
                R.id.progressWCFuel,
                "setProgressTintList",
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        if (highContrast) R.color.widget_fuel_range_high_contrast else R.color.widget_fuel_range
                    )
                )
            )
            views.setColorStateList(
                R.id.progressWCFuel,
                "setProgressBackgroundTintList",
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        if (highContrast) {
                            R.color.widget_fuel_range_track_high_contrast
                        } else {
                            R.color.widget_fuel_range_track
                        }
                    )
                )
            )
        }

        private fun compactActionBackgroundResource(context: Context, tone: CompactActionTone): Int {
            val darkTheme = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
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
