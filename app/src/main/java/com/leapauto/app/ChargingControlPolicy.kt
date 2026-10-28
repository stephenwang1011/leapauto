package com.leapauto.app

enum class ChargingControlQueryOutcome { NO_ID, CONFIRMED, UNCONFIRMED }

enum class ChargingControlOutcome { SUCCESS, AWAITING_CONFIRMATION }

enum class ChargingControlFailureStage { SUBMISSION, RESULT_QUERY, TELEMETRY, SESSION_SAVE }

/** App confirmation rules only; command payloads remain owned by Commands. */
object ChargingControlPolicy {
    const val TELEMETRY_ATTEMPTS = 4
    const val TELEMETRY_INTERVAL_MS = 2_000L
    const val LATE_CONFIRMATION_WINDOW_MS = 60_000L

    fun supports(name: String): Boolean =
        name == "startCharging" || name == "stopCharging" || name == "unlockCharger"

    fun canConfirmFromRefresh(acceptedAtMs: Long, refreshStartedAtMs: Long, nowMs: Long): Boolean =
        refreshStartedAtMs > acceptedAtMs && refreshStartedAtMs <= nowMs &&
            nowMs - acceptedAtMs in 0..LATE_CONFIRMATION_WINDOW_MS

    fun telemetryMatches(name: String, chargeState: Int?): Boolean = when (name) {
        "startCharging" -> chargeState == 1
        "stopCharging" -> chargeState in setOf(0, 2, 4, 6)
        else -> false
    }

    /** Merge charging readback without replacing control/settings optimistic state. */
    fun mergeTelemetry(current: VehicleStatus, refreshed: VehicleStatus): VehicleStatus = current.copy(
        soc = refreshed.soc,
        preciseSoc = refreshed.preciseSoc,
        mileage = refreshed.mileage,
        electricMileage = refreshed.electricMileage,
        combinedMileage = refreshed.combinedMileage,
        rangeMode = refreshed.rangeMode,
        chargeState = refreshed.chargeState,
        chargeLabel = refreshed.chargeLabel,
        chargeGunConnected = refreshed.chargeGunConnected,
        chargingPower = refreshed.chargingPower,
        chargeRemainTime = refreshed.chargeRemainTime,
        chargeSoc = refreshed.chargeSoc,
        chargeType = refreshed.chargeType,
        batteryVoltage = refreshed.batteryVoltage,
        batteryCurrent = refreshed.batteryCurrent,
        minBatteryTemp = refreshed.minBatteryTemp
    )

    fun outcome(
        name: String,
        query: ChargingControlQueryOutcome,
        freshChargeState: Int?
    ): ChargingControlOutcome {
        require(supports(name))
        val confirmed = if (name == "unlockCharger") {
            query == ChargingControlQueryOutcome.CONFIRMED
        } else {
            query != ChargingControlQueryOutcome.UNCONFIRMED && telemetryMatches(name, freshChargeState)
        }
        return if (confirmed) ChargingControlOutcome.SUCCESS else ChargingControlOutcome.AWAITING_CONFIRMATION
    }

    fun feedback(name: String, outcome: ChargingControlOutcome): ControlFeedback {
        require(supports(name))
        return if (outcome == ChargingControlOutcome.SUCCESS) {
            ControlFeedback(
                when (name) {
                    "startCharging" -> "车况已确认正在充电"
                    "stopCharging" -> "车况已确认停止充电"
                    else -> "充电枪解锁结果已确认"
                },
                ControlFeedbackKind.SUCCESS
            )
        } else {
            ControlFeedback("指令已发送，执行结果待确认，请刷新车况或检查车辆", ControlFeedbackKind.WARNING)
        }
    }

    fun failureFeedback(postAccepted: Boolean, stage: ChargingControlFailureStage): ControlFeedback? =
        if (stage == ChargingControlFailureStage.SESSION_SAVE) null else ControlFeedback(
            if (!postAccepted) "充电指令发送未确认，请检查车辆状态后重试" else when (stage) {
                ChargingControlFailureStage.RESULT_QUERY -> "指令已发送，结果查询异常，执行结果待确认"
                ChargingControlFailureStage.TELEMETRY -> "指令已发送，车况刷新失败，执行结果待确认"
                ChargingControlFailureStage.SESSION_SAVE -> error("Persistence errors are log-only")
                ChargingControlFailureStage.SUBMISSION -> "指令已发送，执行结果待确认"
            },
            if (postAccepted) ControlFeedbackKind.WARNING else ControlFeedbackKind.ERROR
        )
}
