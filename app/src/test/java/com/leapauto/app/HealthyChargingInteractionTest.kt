package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source contracts only: these tests do not exercise Compose hit testing or vehicle commands. */
class HealthyChargingInteractionTest {
    private fun readSource(name: String): String {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
        return File(projectDir, "app/src/main/java/com/leapauto/app/ui/$name.kt")
            .readText()
            .replace("\r\n", "\n")
            .replace(Regex("//[^\n]*"), "")
    }

    private val source = readSource("HealthyChargingBottomSheet")

    private val actionBar: String
        get() {
            val start = source.indexOf("if (isGunConnected) {")
            require(start >= 0) { "Charging action visibility gate is missing" }
            return blockAt(source, source.indexOf('{', start))
        }

    private val feedbackBlock: String
        get() {
            val start = source.indexOf("LaunchedEffect(controlFeedback)")
            require(start >= 0) { "Feedback effect is missing" }
            return blockAt(source, source.indexOf('{', start))
        }

    @Test
    fun unlockUsesNativeSurfaceClickWithChargingGuard() {
        val nativeClick = Regex("\\bSurface\\s*\\(\\s*onClick\\s*=\\s*\\{")
        val bodies = nativeClick.findAll(actionBar).map { match ->
            blockAt(actionBar, actionBar.indexOf('{', match.range.first))
                .replace(Regex("\\s+"), "")
        }.toList()
        assertEquals("Both actions must bind the native Surface onClick", 2, bodies.size)
        assertTrue(
            "Unlock action must guard while charging and delegate when safe",
            bodies.any { it.contains("if(isCharging)") && it.contains("onControl(\"unlockCharger\")") }
        )
    }

    @Test
    fun startOrStopUsesNativeSurfaceClickAndKeepsSheetOpen() {
        assertNativeAction("mainActionCmd")
    }

    @Test
    fun chargingStateSelectsStopAndIdleSelectsStart() {
        assertTrue(Regex("val\\s+isCharging\\s*=\\s*status\\?\\.chargeState\\s*==\\s*1").containsMatchIn(source))
        assertTrue(
            Regex("val\\s+mainActionCmd\\s*=\\s*if\\s*\\(isCharging\\)\\s*\"stopCharging\"\\s*else\\s*\"startCharging\"")
                .containsMatchIn(actionBar)
        )
        assertEquals(2, Regex("\\bonControl\\s*\\(").findAll(actionBar).count())
        assertFalse("Actions must not use the non-clickable Surface modifier workaround", actionBar.contains(".clickable"))
        assertFalse("Actions must not install another pointer handler", actionBar.contains(".pointerInput"))
        assertFalse("Charging actions must keep the sheet open", actionBar.contains("onDismissRequest"))
        assertFalse("Feedback must keep the sheet open", feedbackBlock.contains("onDismissRequest"))
    }

    @Test
    fun bothActionsRetainBoundedEqualWidthLayouts() {
        val surfaces = Regex("\\bSurface\\s*\\(").findAll(actionBar).toList()
        assertEquals("Both charging actions must remain present", 2, surfaces.size)
        surfaces.forEach { match ->
            val arguments = blockAt(actionBar, actionBar.indexOf('(', match.range.first), '(', ')')
            assertTrue(Regex("\\.weight\\(1f\\)\\s*\\.height\\(42\\.dp\\)").containsMatchIn(arguments))
            assertTrue("Each action must be disabled while sending", arguments.contains("enabled = !controlInProgress"))
        }
    }

    @Test
    fun pendingActionsShowWaitingLabelsAndToastFeedback() {
        assertTrue(actionBar.contains("text = if (controlInProgress) \"处理中…\" else \"解锁充电枪\""))
        assertTrue(actionBar.contains("text = if (controlInProgress) \"处理中…\" else mainActionText"))
        assertTrue(feedbackBlock.contains("feedback.kind != ControlFeedbackKind.IN_PROGRESS"))
        assertTrue(feedbackBlock.contains("Toast.makeText(context, feedback.message, Toast.LENGTH_SHORT).show()"))
    }

    @Test
    fun feedbackRemainsOutsideGunGateAfterStoppingAndDisconnecting() {
        val gateStart = source.indexOf("if (isGunConnected) {")
        val gateOpening = source.indexOf('{', gateStart)
        val afterGate = source.substring(gateOpening + actionBar.length + 2).trimStart()
        assertFalse(actionBar.contains("LaunchedEffect(controlFeedback)"))
        assertTrue("Feedback effect must follow the gun gate unconditionally", afterGate.startsWith("LaunchedEffect(controlFeedback)"))
    }

    @Test
    fun progressDoesNotTriggerToastAndResultShowsToast() {
        assertTrue(feedbackBlock.contains("feedback.kind != ControlFeedbackKind.IN_PROGRESS"))
        assertTrue(feedbackBlock.contains("feedback.message.isNotBlank()"))
    }

    @Test
    fun sendingPausesTelemetryAndGuardsInitialRefresh() {
        assertTrue(source.contains("val latestControlInProgress by rememberUpdatedState(controlInProgress)"))
        val initialRefresh = source.indexOf("LaunchedEffect(Unit)")
        assertTrue(initialRefresh >= 0)
        assertEquals(
            "if(!latestControlInProgress){onRefreshStatus()}",
            blockAt(source, source.indexOf('{', initialRefresh)).replace(Regex("\\s+"), "")
        )
        val polling = source.indexOf("LaunchedEffect(isChargingState, controlInProgress)")
        assertTrue("Changing progress must cancel and restart the polling effect", polling >= 0)
        val body = blockAt(source, source.indexOf('{', polling)).replace(Regex("\\s+"), "")
        assertEquals(
            "if(isChargingState&&!controlInProgress){while(true){delay(3000L)if(!latestControlInProgress){latestRefreshTelemetry()}}}",
            body
        )
    }

    @Test
    fun screenForwardsOnlyDedicatedChargingFeedbackAndProgress() {
        val screen = readSource("LeapAutoScreen")
        assertTrue(screen.contains("chargingControlFeedback: ControlFeedback? = null"))
        assertTrue(screen.contains("chargingControlInProgress: Boolean = false"))
        assertTrue(source.contains("controlFeedback: ControlFeedback? = null"))
        assertTrue(source.contains("controlInProgress: Boolean = false"))
        val call = screen.indexOf("HealthyChargingBottomSheet(")
        assertTrue(call >= 0)
        val arguments = blockAt(screen, screen.indexOf('(', call), '(', ')')
        assertTrue(arguments.contains("controlFeedback = chargingControlFeedback"))
        assertTrue(arguments.contains("controlInProgress = chargingControlInProgress"))
        assertTrue(arguments.contains("onRefreshTelemetry = onRefreshChargingTelemetry"))
        assertTrue(arguments.contains("onTelemetryActiveChange = onChargingTelemetryActiveChange"))
        assertFalse(Regex("onRefreshTelemetry\\s*=\\s*onRefresh\\b").containsMatchIn(arguments))
        assertFalse(source.contains("onRefreshTelemetry: () -> Unit = onRefreshStatus"))
        assertTrue(source.contains("onDispose { onTelemetryActiveChange(false) }"))
        assertFalse("The sheet must not use the global HUD feedback", Regex("controlFeedback\\s*=\\s*controlFeedback\\b").containsMatchIn(arguments))
    }

    private fun assertNativeAction(command: String) {
        val nativeClick = Regex("\\bSurface\\s*\\(\\s*onClick\\s*=\\s*\\{")
        val bodies = nativeClick.findAll(actionBar).map { match ->
            blockAt(actionBar, actionBar.indexOf('{', match.range.first))
                .replace(Regex("\\s+"), "")
        }.toList()
        assertEquals("Both actions must bind the native Surface onClick", 2, bodies.size)
        assertEquals(
            "The action must guard repeat clicks, delegate once to the existing control/PIN path and keep the sheet open",
            1,
            bodies.count { it == "if(!latestControlInProgress){onControl($command)}" }
        )
    }

    // Balanced delimiters keep assertions scoped to the actual action, rather than unrelated buttons.
    private fun blockAt(text: String, opening: Int, open: Char = '{', close: Char = '}'): String {
        require(opening >= 0 && text[opening] == open)
        var depth = 0
        for (index in opening until text.length) {
            when (text[index]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return text.substring(opening + 1, index)
                }
            }
        }
        error("Unbalanced action source")
    }
}
