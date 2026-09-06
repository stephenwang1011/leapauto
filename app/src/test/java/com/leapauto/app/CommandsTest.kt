package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class CommandsTest {

    @Test
    fun lockAndUnlockUseTheSameRemoteCommandWithDifferentState() {
        assertEquals("110", Commands.build("lock").cmdid)
        assertEquals("110", Commands.build("unlock").cmdid)
        assertTrue(Commands.build("lock").stateJson.contains("lock"))
        assertTrue(Commands.build("unlock").stateJson.contains("unlock"))
    }

    @Test
    fun sentryModeUsesTheVerifiedCommandAndExplicitTargetState() {
        val enable = Commands.build("sentryOn")
        val disable = Commands.build("sentryOff")

        assertEquals("400", enable.cmdid)
        assertEquals("{\"operation\":\"on\"}", enable.stateJson)
        assertEquals("400", disable.cmdid)
        assertEquals("{\"operation\":\"off\"}", disable.stateJson)
        assertEquals(true, SentryModeControlPolicy.targetEnabled("sentryOn"))
        assertEquals(false, SentryModeControlPolicy.targetEnabled("sentryOff"))
    }

    @Test
    fun sentryModeButtonAndConfirmationFollowTelemetry() {
        assertEquals("哨兵已开", SentryModeControlPolicy.activeLabel(true))
        assertNull(SentryModeControlPolicy.activeLabel(false))
        assertNull(SentryModeControlPolicy.activeLabel(null))
        assertEquals("sentryOn", SentryModeControlPolicy.commandName(null))
        assertEquals("sentryOn", SentryModeControlPolicy.commandName(false))
        assertEquals("sentryOff", SentryModeControlPolicy.commandName(true))
        assertTrue(SentryModeControlPolicy.isConfirmed(true, true))
        assertTrue(SentryModeControlPolicy.isConfirmed(false, false))
        assertFalse(SentryModeControlPolicy.isConfirmed(true, null))
        assertFalse(SentryModeControlPolicy.isConfirmed(true, false))
        assertTrue(SentryModeControlPolicy.querySucceeded(0, null))
        assertTrue(SentryModeControlPolicy.querySucceeded(null, 0))
        assertFalse(SentryModeControlPolicy.querySucceeded(null, 200))
        assertFalse(SentryModeControlPolicy.querySucceeded(1, 0))
        assertFalse(SentryModeControlPolicy.querySucceeded(null, null))
    }

    @Test
    fun trunkOpenUsesTheTrunkCommandAndTrueState() {
        val command = Commands.build("trunkOpen")

        assertEquals("130", command.cmdid)
        assertEquals("{\"value\":\"true\"}", command.stateJson)
    }

    @Test
    fun trunkCloseUsesTheTrunkCommandAndFalseState() {
        val command = Commands.build("trunkClose")

        assertEquals("130", command.cmdid)
        assertEquals("{\"value\":\"false\"}", command.stateJson)
    }

    @Test
    fun frunkActionsUseExplicitOpenAndCloseTargets() {
        val open = Commands.build("frunkOpen")
        val close = Commands.build("frunkClose")

        assertEquals("131", open.cmdid)
        assertEquals("{\"value\":\"100\"}", open.stateJson)
        assertEquals("开前备箱", open.label)
        assertEquals("131", close.cmdid)
        assertEquals("{\"value\":\"0\"}", close.stateJson)
        assertEquals("关前备箱", close.label)
    }

    @Test
    fun batteryPreheatActionsUseExplicitOnAndOffTargets() {
        val on = Commands.build("batteryPreheat")
        val off = Commands.build("batteryPreheatOff")

        assertEquals("160", on.cmdid)
        assertEquals("{\"value\":\"ptcon\"}", on.stateJson)
        assertEquals("160", off.cmdid)
        assertEquals("{\"value\":\"ptcoff\"}", off.stateJson)
    }

    @Test
    fun sunshadeQuickActionsReuseTheVerifiedCommandAndExplicitTargets() {
        val open = Commands.build("sunshadeOpen")
        val close = Commands.build("sunshadeClose")

        assertEquals("240", open.cmdid)
        assertEquals("{\"value\":\"10\"}", open.stateJson)
        assertEquals("遮阳帘打开", open.label)
        assertEquals("240", close.cmdid)
        assertEquals("{\"value\":\"0\"}", close.stateJson)
        assertEquals("遮阳帘关闭", close.label)
    }

    @Test
    fun vehicle_location_horn_action_reuses_the_verified_control_command() {
        val command = Commands.build("horn")

        assertEquals("120", command.cmdid)
        assertEquals("{\"value\":\"true\"}", command.stateJson)
        assertEquals("鸣笛寻车", command.label)
    }

    @Test
    fun acTemperatureSelectsCoolingOrHeatingMode() {
        assertTrue(Commands.buildAc(18).stateJson.contains("\"mode\":\"cold\""))
        assertTrue(Commands.buildAc(32).stateJson.contains("\"mode\":\"hot\""))
    }

    @Test
    fun climateTemperatureToneUsesTheVerifiedModeEnumNotTemperature() {
        assertEquals(ClimateTemperatureTone.COOLING, ClimateTemperatureToneResolver.tone(true, 1))
        assertEquals(ClimateTemperatureTone.HEATING, ClimateTemperatureToneResolver.tone(true, 0))
        assertEquals(ClimateTemperatureTone.DEFAULT, ClimateTemperatureToneResolver.tone(true, 2))
        assertEquals(ClimateTemperatureTone.DEFAULT, ClimateTemperatureToneResolver.tone(false, 1))
        assertEquals(ClimateTemperatureTone.DEFAULT, ClimateTemperatureToneResolver.tone(null, 0))
    }

    @Test
    fun climateTemperatureToneFallsBackToTheVerifiedClimateModeWhenNeeded() {
        assertEquals(ClimateTemperatureTone.COOLING, ClimateTemperatureToneResolver.tone(true, null, 1))
        assertEquals(ClimateTemperatureTone.HEATING, ClimateTemperatureToneResolver.tone(true, null, 3))
        assertEquals(ClimateTemperatureTone.DEFAULT, ClimateTemperatureToneResolver.tone(true, null, 4))
    }

    @Test
    fun climateTemperatureToneFallsBackToTargetTemperatureWhenModesMissing() {
        assertEquals(ClimateTemperatureTone.COOLING, ClimateTemperatureToneResolver.tone(true, null, null, 24))
        assertEquals(ClimateTemperatureTone.HEATING, ClimateTemperatureToneResolver.tone(true, null, null, 28))
        assertEquals(ClimateTemperatureTone.DEFAULT, ClimateTemperatureToneResolver.tone(false, null, null, 28))
    }

    @Test
    fun acTemperatureKeepsTheVerifiedRangeAndGlobalPosition() {
        val command = Commands.buildAc(26)

        assertEquals("170", command.cmdid)
        assertTrue(command.stateJson.contains("\"temperature\":\"26\""))
        assertTrue(command.stateJson.contains("\"position\":\"all\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun acTemperatureRejectsValuesOutsideTheVerifiedRange() {
        Commands.buildAc(33)
    }

    @Test
    fun climatePresetsKeepTheirVerifiedCommands() {
        assertEquals("170", Commands.build("acOn").cmdid)
        assertEquals("170", Commands.build("acOff").cmdid)
        assertTrue(Commands.build("defrost").stateJson.contains("\"wshld\":\"1\""))
        assertTrue(Commands.build("quickCool").stateJson.contains("\"temperature\":\"18\""))
        assertTrue(Commands.build("quickHeat").stateJson.contains("\"temperature\":\"32\""))

        val deodorize = Commands.build("deodorize")
        val deodorizeState = JSONObject(deodorize.stateJson)
        assertEquals("170", deodorize.cmdid)
        assertEquals("快速除味", deodorize.label)
        assertEquals("manual", deodorizeState.getString("operate"))
        assertEquals("24", deodorizeState.getString("temperature"))
        assertEquals("7", deodorizeState.getString("windlevel"))
        assertEquals("nohotcold", deodorizeState.getString("mode"))
        assertEquals("out", deodorizeState.getString("circle"))
        assertEquals("0", deodorizeState.getString("wshld"))
        assertEquals("all", deodorizeState.getString("position"))
    }

    @Test
    fun batteryPreheatToggleUsesOnlyVerifiedTelemetryValues() {
        assertEquals(true, BatteryPreheatState.fromRaw(4))
        assertEquals(false, BatteryPreheatState.fromRaw(0))
        assertNull(BatteryPreheatState.fromRaw(1))
        assertNull(BatteryPreheatState.fromRaw(null))
    }

    @Test
    fun frunkCapabilityIsRestrictedToD19ModelNames() {
        assertTrue(VehicleQuickControlCapabilities.supportsFrunk("零跑 D19"))
        assertTrue(VehicleQuickControlCapabilities.supportsFrunk("D19 Ultra"))
        assertFalse(VehicleQuickControlCapabilities.supportsFrunk("零跑 D190"))
        assertFalse(VehicleQuickControlCapabilities.supportsFrunk("C16"))
    }

    @Test
    fun normalizedAcTemperatureAcceptsWholeNumberTemperaturesIncludingDecimalFormatting() {
        assertEquals(16, Commands.normalizedAcTemperature("16 °C"))
        assertEquals(26, Commands.normalizedAcTemperature("26°C"))
        assertEquals(32, Commands.normalizedAcTemperature("32"))
        assertEquals(24, Commands.normalizedAcTemperature("24"))
        assertEquals(24, Commands.normalizedAcTemperature("24.0"))
        assertEquals(24, Commands.normalizedAcTemperature("24.00 °C"))
        assertNull(Commands.normalizedAcTemperature("15 °C"))
        assertNull(Commands.normalizedAcTemperature("33 °C"))
        assertNull(Commands.normalizedAcTemperature("40 °C"))
        assertNull(Commands.normalizedAcTemperature("26.5 °C"))
        assertNull(Commands.normalizedAcTemperature("NaN"))
        assertNull(Commands.normalizedAcTemperature(""))
        assertNull(Commands.normalizedAcTemperature(null))
        assertNull(Commands.normalizedAcTemperature("unknown"))
    }

    @Test
    fun invalidVehicleTemperatureUsesDefaultTargetWithoutBecomingConfirmed() {
        val missingTarget = Commands.acTemperatureTarget(null)
        val invalidTarget = Commands.acTemperatureTarget("40 °C")
        val decimalTarget = Commands.acTemperatureTarget("26.5 °C")
        val textTarget = Commands.acTemperatureTarget("unknown")

        assertEquals(24, missingTarget.value)
        assertEquals(24, invalidTarget.value)
        assertEquals(24, decimalTarget.value)
        assertEquals(24, textTarget.value)
        assertFalse(missingTarget.confirmed)
        assertFalse(invalidTarget.confirmed)
        assertFalse(decimalTarget.confirmed)
        assertFalse(textTarget.confirmed)
    }

    @Test
    fun controlResultWithoutMessageIdCannotBePolled() {
        assertFalse(ControlResult("", JSONObject()).hasPollingId())
        assertFalse(ControlResult("   ", JSONObject()).hasPollingId())
        assertTrue(ControlResult("message-id", JSONObject()).hasPollingId())
    }

    @Test
    fun climateControlsRequireVehicleStatusAndNoCommandInProgress() {
        assertTrue(Commands.canSubmitClimateControl(hasVehicleStatus = true, commandInProgress = false))
        assertFalse(Commands.canSubmitClimateControl(hasVehicleStatus = false, commandInProgress = false))
        assertFalse(Commands.canSubmitClimateControl(hasVehicleStatus = true, commandInProgress = true))
    }

    @Test
    fun climateAdditionalSignalsPreserveRawValuesAndOnlyRecognizeBooleanZeroOrOne() {
        assertEquals("3", ClimateSignalValue.raw(3))
        assertEquals("0", ClimateSignalValue.raw(" 0 "))
        assertNull(ClimateSignalValue.raw(JSONObject.NULL))
        assertNull(ClimateSignalValue.raw("   "))

        assertEquals(true, ClimateSignalValue.boolean(1))
        assertEquals(false, ClimateSignalValue.boolean(0))
        assertEquals(true, ClimateSignalValue.boolean("true"))
        assertEquals(false, ClimateSignalValue.boolean("0"))
        assertNull(ClimateSignalValue.boolean(2))
        assertNull(ClimateSignalValue.boolean(JSONObject.NULL))
    }

    @Test
    fun airCircleUsesOneForInnerAndZeroForOuter() {
        assertEquals(AirCircle.INNER, AirCircle.fromTelemetryValue(1))
        assertEquals(AirCircle.INNER, AirCircle.fromTelemetryValue("1"))
        assertEquals(AirCircle.OUTER, AirCircle.fromTelemetryValue(0))
        assertEquals(AirCircle.OUTER, AirCircle.fromTelemetryValue("0"))
        assertNull(AirCircle.fromTelemetryValue(2))
        assertNull(AirCircle.fromTelemetryValue("true"))
        assertEquals("内循环", AirCircle.INNER.displayLabel)
        assertEquals("外循环", AirCircle.OUTER.displayLabel)
    }

    @Test
    fun airCircleFallsBackToLegacyBooleanOnlyWhenPrimaryIsMissing() {
        assertEquals(AirCircle.INNER, AirCircle.fromVehicleTelemetry(null, true))
        assertEquals(AirCircle.OUTER, AirCircle.fromVehicleTelemetry(JSONObject.NULL, false))
        assertEquals(AirCircle.OUTER, AirCircle.fromVehicleTelemetry(0, true))
        assertNull(AirCircle.fromVehicleTelemetry(2, true))
        assertNull(AirCircle.fromVehicleTelemetry(null, "unknown"))
    }

    @Test
    fun climatePresetsRemainDirectCommandsAndFeedbackDistinguishesEachStage() {
        assertEquals("极速降温", ClimatePresetAction.QUICK_COOL.label)
        assertEquals("快速除味", ClimatePresetAction.DEODORIZE.label)
        assertEquals("空调开启中", ClimateControlFeedbackText.sending("开空调"))
        assertEquals("空调开启已提交", ClimateControlFeedbackText.submitted("开空调"))
        assertEquals("空调已开启", ClimateControlFeedbackText.confirmed("开空调"))
        assertEquals("降温中", ClimateControlFeedbackText.sending("极速降温"))
        assertEquals("除雾已开启", ClimateControlFeedbackText.confirmed("开启除雾"))
        assertEquals("除味失败", ClimateControlFeedbackText.failed("快速除味"))
    }

    @Test
    fun pendingTemperatureUsesLocalTargetUntilRequestTerminates() {
        val pending = PendingClimateTemperature(requestId = 7L, target = 25, baselineVehicleTemperature = 24)
        val sending = ClimateControlRequestState(7L, ClimateControlRequestPhase.SENDING)

        val whileSending = ClimateTemperatureSync.resolve(AcTemperatureTarget(24, confirmed = true), pending, sending)
        assertEquals(25, whileSending.value)
        assertTrue(whileSending.isPending)
        assertFalse(whileSending.clearPending)

        val staleRefresh = ClimateTemperatureSync.resolve(AcTemperatureTarget(24, confirmed = true), pending, sending)
        assertEquals(25, staleRefresh.value)
        assertTrue(staleRefresh.isPending)
        assertFalse(staleRefresh.clearPending)

        val completed = ClimateTemperatureSync.resolve(
            AcTemperatureTarget(25, confirmed = true),
            pending,
            ClimateControlRequestState(7L, ClimateControlRequestPhase.COMPLETED)
        )
        assertEquals(25, completed.value)
        assertFalse(completed.isPending)
        assertTrue(completed.clearPending)

        val notConfirmed = ClimateTemperatureSync.resolve(
            AcTemperatureTarget(24, confirmed = true),
            pending,
            ClimateControlRequestState(7L, ClimateControlRequestPhase.NOT_CONFIRMED)
        )
        assertEquals(24, notConfirmed.value)
        assertTrue(notConfirmed.clearPending)

        val failedWithoutVehicleTemperature = ClimateTemperatureSync.resolve(
            AcTemperatureTarget(Commands.DEFAULT_AC_TEMPERATURE, confirmed = false),
            pending,
            ClimateControlRequestState(7L, ClimateControlRequestPhase.FAILED)
        )
        assertEquals(Commands.DEFAULT_AC_TEMPERATURE, failedWithoutVehicleTemperature.value)
        assertTrue(failedWithoutVehicleTemperature.clearPending)
    }

    @Test
    fun climateOptimisticUpdatesOnlyContainCommandedFields() {
        val acOn = ClimateOptimisticUpdates.acOn()
        assertEquals(true, acOn.acSwitch)
        assertEquals("24 °C", acOn.acSetting)
        assertNull(acOn.windshieldDefrost)

        val acOff = ClimateOptimisticUpdates.acOff()
        assertEquals(false, acOff.acSwitch)
        assertNull(acOff.acSetting)

        val target = ClimateOptimisticUpdates.temperature(18)
        assertEquals(true, target.acSwitch)
        assertEquals("18 °C", target.acSetting)

        val defrost = ClimateOptimisticUpdates.windshieldDefrost()
        assertNull(defrost.acSwitch)
        assertEquals("24 °C", defrost.acSetting)
        assertEquals(true, defrost.windshieldDefrost)
    }

    @Test
    fun climatePresetExpectationsUseAllVerifiedReadbackFields() {
        assertEquals(
            ClimateTelemetryExpectation(
                acSwitch = true,
                temperatureC = 18,
                windLevel = 7,
                circle = AirCircle.INNER,
                windshieldDefogging = false
            ),
            Commands.climateExpectation("quickCool")
        )
        assertEquals(
            ClimateTelemetryExpectation(
                acSwitch = true,
                temperatureC = 24,
                windLevel = 7,
                circle = AirCircle.OUTER,
                windshieldDefogging = false
            ),
            Commands.climateExpectation("deodorize")
        )
        assertNull(Commands.climateExpectation("horn"))
    }

    @Test
    fun acceptedClimatePostQueriesWhenPollingIdIsAvailable() {
        val command = Commands.build("acOn")
        val decision = ClimateControlPostPolicy.decision(
            command,
            postSucceeded = true,
            hasPollingId = true
        )

        assertEquals(ClimateControlPostOutcome.ACCEPTED, decision.outcome)
        assertTrue(decision.shouldQueryControlResult)
        assertEquals("空调开启已提交", ClimateControlFeedbackText.waiting("开空调"))
    }

    @Test
    fun telemetryPendingAndPartialReadbackUseAccurateFeedback() {
        assertEquals(
            "空调响应较慢",
            ClimateControlFeedbackText.responsePending("应用空调设置")
        )
        assertEquals(
            "空调已更新",
            ClimateControlFeedbackText.confirmedAvailableFields("应用空调设置")
        )
    }

    @Test
    fun acceptedClimatePostWithoutPollingIdKeepsCompatibilityPath() {
        val decision = ClimateControlPostPolicy.decision(
            Commands.build("acOff"),
            postSucceeded = true,
            hasPollingId = false
        )

        assertEquals(ClimateControlPostOutcome.ACCEPTED, decision.outcome)
        assertFalse(decision.shouldQueryControlResult)
        assertEquals(
            "空调关闭已提交",
            ClimateControlFeedbackText.submitted("关空调")
        )
    }

    @Test
    fun controlFeedbackAutoDismissesSubmittedAndTerminalStatesButNotSending() {
        assertNull(ControlFeedbackDisplayPolicy.autoDismissDelayMs(ControlFeedbackKind.IN_PROGRESS))
        assertEquals(
            ControlFeedbackDisplayPolicy.SUBMITTED_DURATION_MS,
            ControlFeedbackDisplayPolicy.autoDismissDelayMs(ControlFeedbackKind.SUBMITTED)
        )
        assertEquals(
            ControlFeedbackDisplayPolicy.SUCCESS_DURATION_MS,
            ControlFeedbackDisplayPolicy.autoDismissDelayMs(ControlFeedbackKind.SUCCESS)
        )
        assertEquals(
            ControlFeedbackDisplayPolicy.WARNING_DURATION_MS,
            ControlFeedbackDisplayPolicy.autoDismissDelayMs(ControlFeedbackKind.WARNING)
        )
        assertEquals(
            ControlFeedbackDisplayPolicy.ERROR_DURATION_MS,
            ControlFeedbackDisplayPolicy.autoDismissDelayMs(ControlFeedbackKind.ERROR)
        )
    }

    @Test
    fun climateTelemetryConfirmationRequiresEveryAvailableRequestedField() {
        val detailedExpectation = ClimateTelemetryExpectation(
            acSwitch = true,
            temperatureC = 24,
            windLevel = 4,
            circle = AirCircle.INNER,
            windshieldDefogging = false,
            hasUnavailableFields = true
        )
        val matchingReadback = ClimateTelemetryReadback(
            acSwitch = true,
            temperatureC = 24,
            windLevel = 4,
            circle = AirCircle.INNER,
            windshieldDefogging = false
        )

        assertTrue(
            ClimateTelemetryConfirmationPolicy.matches(
                optimisticUpdate = null,
                telemetryExpectation = detailedExpectation,
                readback = matchingReadback
            )
        )
        assertFalse(
            ClimateTelemetryConfirmationPolicy.matches(
                optimisticUpdate = null,
                telemetryExpectation = detailedExpectation,
                readback = matchingReadback.copy(windLevel = 3)
            )
        )
    }

    @Test
    fun optimisticClimateConfirmationDoesNotTreatMismatchedTelemetryAsSuccess() {
        val target = ClimateOptimisticUpdates.temperature(22)
        assertTrue(
            ClimateTelemetryConfirmationPolicy.matches(
                optimisticUpdate = target,
                telemetryExpectation = null,
                readback = ClimateTelemetryReadback(true, 22, null, null, null)
            )
        )
        assertFalse(
            ClimateTelemetryConfirmationPolicy.matches(
                optimisticUpdate = target,
                telemetryExpectation = null,
                readback = ClimateTelemetryReadback(true, 21, null, null, null)
            )
        )
    }

    @Test
    fun failedClimatePostCannotBeReportedAsCompleted() {
        val decision = ClimateControlPostPolicy.decision(Commands.buildAc(24), postSucceeded = false)

        assertEquals(
            ClimateControlPostOutcome.FAILED,
            decision.outcome
        )
        assertFalse(decision.shouldQueryControlResult)
    }

    @Test
    fun staleClimateRefreshPreservesOptimisticStateUntilFreshRetry() {
        val guard = ClimateOptimisticGuard(3L, ClimateOptimisticUpdates.temperature(21), postCompleted = true)

        assertEquals(
            ClimateTelemetryMergeDecision.PRESERVE_CLIMATE,
            ClimateTelemetryMergePolicy.decide(guard, refreshRevision = 2L, currentRevision = 3L, matchesOptimisticTarget = false)
        )
        assertEquals(
            ClimateTelemetryMergeDecision.PRESERVE_CLIMATE,
            ClimateTelemetryMergePolicy.decide(guard, refreshRevision = 3L, currentRevision = 3L, matchesOptimisticTarget = false)
        )
        val preserved = ClimateTelemetryMergePolicy.consume(
            guard,
            ClimateTelemetryMergeDecision.PRESERVE_CLIMATE
        )
        assertEquals(guard, preserved)
        assertEquals(
            ClimateTelemetryMergeDecision.PRESERVE_CLIMATE,
            ClimateTelemetryMergePolicy.decide(preserved, refreshRevision = 3L, currentRevision = 3L, matchesOptimisticTarget = false)
        )
    }

    @Test
    fun matchingClimateRefreshClearsOptimisticGuardImmediately() {
        val guard = ClimateOptimisticGuard(4L, ClimateOptimisticUpdates.acOn(), postCompleted = true)
        val decision = ClimateTelemetryMergePolicy.decide(
            guard,
            refreshRevision = 4L,
            currentRevision = 4L,
            matchesOptimisticTarget = true
        )

        assertEquals(ClimateTelemetryMergeDecision.APPLY, decision)
        assertNull(ClimateTelemetryMergePolicy.consume(guard, decision))
    }

    @Test
    fun matchingTelemetryCannotClearGuardBeforePostCompletes() {
        val guard = ClimateOptimisticGuard(5L, ClimateOptimisticUpdates.acOff())

        assertEquals(
            ClimateTelemetryMergeDecision.PRESERVE_CLIMATE,
            ClimateTelemetryMergePolicy.decide(
                guard,
                refreshRevision = 5L,
                currentRevision = 5L,
                matchesOptimisticTarget = true
            )
        )
    }

    @Test
    fun climateTemperatureSliderUsesWholeDegreesWithinVerifiedRange() {
        assertEquals(15, ClimateTemperatureSlider.discreteStepCount())
        assertEquals(16, ClimateTemperatureSlider.normalize(15.4f))
        assertEquals(24, ClimateTemperatureSlider.normalize(23.6f))
        assertEquals(32, ClimateTemperatureSlider.normalize(32.8f))
        assertEquals(16f, ClimateTemperatureSlider.clamp(10f))
        assertEquals(32f, ClimateTemperatureSlider.clamp(40f))
    }

    @Test
    fun climateTemperatureSliderCommitsOnlyWhenFinishedValueChanges() {
        assertTrue(ClimateTemperatureSlider.shouldSubmit(25f, current = 24, enabled = true))
        assertFalse(ClimateTemperatureSlider.shouldSubmit(24.4f, current = 24, enabled = true))
        assertFalse(ClimateTemperatureSlider.shouldSubmit(25f, current = 24, enabled = false))
    }

    @Test
    fun staleTelemetryMergeKeepsOptimisticClimateFields() {
        val merged = ClimateOptimisticUpdates.mergeOver(
            ClimateOptimisticUpdates.temperature(21),
            ClimateTelemetrySnapshot(acSwitch = false, acSetting = "24 °C", windshieldDefrost = false)
        )

        assertEquals(true, merged.acSwitch)
        assertEquals("21 °C", merged.acSetting)
        assertEquals(false, merged.windshieldDefrost)
    }
}
