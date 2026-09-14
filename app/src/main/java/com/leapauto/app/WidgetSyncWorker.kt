package com.leapauto.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Performs widget network synchronization outside the AppWidgetProvider receiver window. */
class WidgetSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext
        val requestWasDriving = inputData.getBoolean(KEY_DRIVING_SYNC, false)
        val manager = AppWidgetManager.getInstance(context)
        val controlWidgetIds = manager.getAppWidgetIds(ComponentName(context, ControlWidget::class.java))
        val compactWidgetIds = manager.getAppWidgetIds(ComponentName(context, CompactControlWidget::class.java))
        if (controlWidgetIds.isEmpty() && compactWidgetIds.isEmpty()) return Result.success()

        val store = SessionStore(context)
        val views = ControlWidget.baseViews(context)
        var session: Session? = null
        return try {
            val current = store.load()
            session = current
            if (current.oldAuth == null || current.newAuth == null || current.selectedVin.isBlank()) {
                views.setTextViewText(R.id.txtWUpdated, "打开 App 登录")
                ControlWidget.updateSyncCadence(context, null)
            } else {
                val config = store.loadVehicleConfig(current.selectedVin)
                val status = LeapmotorApi(current).getVehicleState()
                ChargeNotificationManager.process(context, store, current.selectedVin, status)
                val hasFuel = VehicleStatusMapper.fuelRemainingRange(status) != null ||
                    VehicleStatusMapper.fuelSocPercent(status) != null ||
                    current.selectedCarType.contains("增程") ||
                    current.selectedCarType.contains("REEV", ignoreCase = true) ||
                    config.powerType == SessionStore.VehiclePowerType.RANGE_EXTENDER
                val workerPowerType = if (hasFuel) {
                    SessionStore.VehiclePowerType.RANGE_EXTENDER
                } else {
                    config.powerType
                }
                val powerType = workerPowerType?.let { if (it == SessionStore.VehiclePowerType.PURE_ELECTRIC) VehicleStatusMapper.PowerType.PURE_ELECTRIC else VehicleStatusMapper.PowerType.RANGE_EXTENDER }
                ControlWidget.renderStatus(context, views, status, current.selectedCarType)
                store.save(current)
                val updated = formatUpdatedTime()
                views.setTextViewText(R.id.txtWUpdated, updated)
                store.saveWidgetSnapshot(
                    vin = current.selectedVin,
                    carType = current.selectedCarType,
                    range = VehicleStatusMapper.widgetRange(status, session.selectedCarType, powerType) ?: "--",
                    soc = VehicleStatusMapper.electricSocPercent(status)
                        ?: VehicleStatusMapper.soc(status),
                    fuelSoc = VehicleStatusMapper.fuelSocPercent(status),
                    updated = updated,
                    powerType = workerPowerType,
                    electricRange = VehicleStatusMapper.electricRemainingRange(status),
                    fuelRange = VehicleStatusMapper.fuelRemainingRange(status),
                    electricTotalRange = VehicleStatusMapper.electricTotalRange(status),
                    fuelTotalRange = VehicleStatusMapper.fuelTotalRange(status),
                    statusLabel = WidgetStatusMapper.label(status, session.selectedCarType).orEmpty(),
                    locked = WidgetStatusMapper.locked(status),
                    acEnabled = WidgetAcMapper.state(status),
                    acTone = ClimateTemperatureToneResolver.tone(
                        acEnabled = WidgetAcMapper.state(status),
                        coolingAndHeating = status.optInt("acCoolingAndHeating", status.optInt("coolingAndHeating", -1)).takeIf { it != -1 },
                        climateMode = status.optInt("climateMode", -1).takeIf { it != -1 },
                        targetTemperature = status.opt("acSetting")?.toString()?.toIntOrNull()
                    ),
                    chargingPower = ChargeStatus.power(status),
                    chargeState = ChargeStatus.state(status),
                    chargeRemainTime = ChargeStatus.remainingTime(status.opt("chargeRemainTime")),
                    driving = WidgetStatusMapper.isDriving(status),
                    capturedAt = System.currentTimeMillis(),
                    lastSuccessAt = System.currentTimeMillis(),
                    sessionGeneration = current.generation,
                    trunkState = TrunkStateMapper.fromSignal(status.opt("bbcmBackDoorStatus")),
                    sentryEnabled = WidgetSentryMapper.state(status)
                )
                ControlWidget.updateSyncCadence(
                    context = context,
                    driving = WidgetStatusMapper.isDriving(status),
                    rescheduleAfterCurrentSync = true
                )
            }
            updateWidgets(store, manager, controlWidgetIds, compactWidgetIds, views)
            Result.success()
        } catch (error: Exception) {
            val terminalAuthFailure = error is ApiException &&
                (SessionExpiry.isRefreshTokenInvalid(error.message) || error.httpStatus in setOf(401, 403))
            if (terminalAuthFailure) {
                store.invalidateWidgetSnapshot()
                views.setTextViewText(R.id.txtWUpdated, "请打开App登录")
                ControlWidget.updateSyncCadence(context, null)
                updateWidgets(store, manager, controlWidgetIds, compactWidgetIds, views)
                return Result.success()
            }
            val snapshot = session?.let { current -> store.loadWidgetSnapshot(current.selectedVin) }
            val continueDrivingCadence = WidgetSyncCadencePolicy.shouldContinueDrivingCadence(
                requestWasDriving = requestWasDriving,
                lastKnownDriving = snapshot?.driving
            )
            if (snapshot != null) {
                // Preserve the last known-good data and timestamp on transient failures.
                ControlWidget.renderSnapshot(context, views, snapshot)
                ControlWidget.updateSyncCadence(
                    context,
                    continueDrivingCadence,
                    rescheduleAfterCurrentSync = true
                )
            } else {
                views.setTextViewText(
                    R.id.txtWUpdated,
                    if (continueDrivingCadence) "正在重试" else "同步失败"
                )
                ControlWidget.updateSyncCadence(
                    context,
                    continueDrivingCadence,
                    rescheduleAfterCurrentSync = true
                )
            }
            updateWidgets(store, manager, controlWidgetIds, compactWidgetIds, views)
            Result.success()
        }
    }

    private fun updateWidgets(
        store: SessionStore,
        manager: AppWidgetManager,
        controlWidgetIds: IntArray,
        compactWidgetIds: IntArray,
        views: RemoteViews
    ) {
        store.suppressWidgetSyncFor(SUPPRESS_ECHO_WINDOW_MILLIS)
        if (controlWidgetIds.isNotEmpty()) {
            manager.updateAppWidget(controlWidgetIds, views)
        }
        if (compactWidgetIds.isNotEmpty()) {
            manager.updateAppWidget(compactWidgetIds, CompactControlWidget.baseViews(applicationContext))
        }
    }

    private fun formatUpdatedTime(): String =
        SimpleDateFormat("'今天' HH:mm'更新'", Locale.CHINA).format(Date())

    companion object {
        internal const val KEY_DRIVING_SYNC = "widget_driving_sync"
        private const val SUPPRESS_ECHO_WINDOW_MILLIS = 10_000L
    }
}
