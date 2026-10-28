package com.leapauto.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Wiring contracts complement pure concurrency tests; no HTTP or Android lifecycle is executed. */
class BackgroundRefreshContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory }
    private val activity = source("MainActivity")
    private fun source(name: String) = File(root, "app/src/main/java/com/leapauto/app/$name.kt").readText()
    private fun method(name: String): String = activity.substringAfter("private fun $name(")
        .substringBefore("\n    private fun ")

    @Test fun `telemetry uses private API and never enters general refresh or resource flow`() {
        assertTrue(activity.contains("onRefreshChargingTelemetry = ::refreshChargingTelemetry"))
        val body = method("refreshChargingTelemetry")
        assertTrue(body.contains("chargingTelemetryWorker.submit"))
        assertTrue(body.contains("LeapmotorApi(snapshot.request, allowCredentialRefresh = false).getVehicleState()"))
        assertTrue(body.contains("ChargingTelemetry.merge"))
        listOf("refreshStatus(", "parseStatus(", "syncVehicleImage(", "refreshVehicleList(",
            "syncChargePlan", "syncThemes", "controlFeedback =", "busy(", "worker.execute", "statusUpdatedAtEpochMs =")
            .forEach { assertFalse("Silent refresh contains $it", body.contains(it)) }
        assertTrue(body.contains("revision != chargingControlRevision"))
        assertTrue(body.contains("chargingTelemetryGate.finish(ticket)"))
    }

    @Test fun `ancillary requests use bounded resource lane and snapshot API`() {
        assertTrue(activity.contains("LeapmotorApi(startupSnapshot.request, allowCredentialRefresh = false)"))
        listOf("syncThemesAndEnsureActive", "refreshVehicleList", "syncVehicleImage").forEach { name ->
            val body = method(name)
            assertTrue(name, body.contains("resourceWorker.submit"))
            assertTrue(name, body.contains("LeapmotorApi(snapshot.request, allowCredentialRefresh = false)"))
            assertFalse(name, body.contains("worker.execute"))
            assertFalse(name, body.contains("sessionStore.save(session)"))
        }
        assertTrue(source("VehicleImageCache").contains("updateCurrent = false"))
    }

    @Test fun `lifecycle cancellation and CAS persistence are explicitly wired`() {
        assertTrue(method("logout").contains("invalidateBackgroundTasks()"))
        assertTrue(method("switchVehicle").contains("invalidateBackgroundTasks()"))
        assertTrue(method("setChargingTelemetryActive").contains("cancelChargingTelemetry()"))
        val store = source("SessionStore").substringAfter("internal fun mergeBackgroundCredentials")
            .substringBefore("fun clear()")
        assertTrue(store.contains("snapshot.canMergeCredentials(live)"))
        assertTrue(store.contains("snapshot.canMergeCredentials(load())"))
        assertFalse(Regex("\\bsave\\(snapshot").containsMatchIn(store))
    }

    @Test fun `all three telemetry sources allocate before HTTP and share main thread watermark`() {
        listOf("refreshChargingTelemetry", "refreshStatus", "readChargingTelemetry").forEach { name ->
            val body = method(name)
            val allocation = body.indexOf("chargingTelemetryOrder.begin(")
            val query = body.indexOf(".getVehicleState()")
            assertTrue(name, allocation >= 0 && allocation < query)
            assertTrue(name, body.contains("chargingTelemetryOrder.accept("))
            assertTrue(name, body.indexOf("chargingTelemetryOrder.accept(") > body.indexOf("runOnMain"))
        }
        val full = method("refreshStatus")
        assertTrue(full.contains("mergeFullRefresh(status, refreshedStatus, chargingAccepted)"))
        assertTrue(full.contains("if (refreshEpoch != backgroundEpoch) return@runOnMain"))
        assertTrue(full.contains("if (chargingAccepted) {"))
    }
}
