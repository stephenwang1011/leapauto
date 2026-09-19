package com.leapauto.app.bluetooth

import com.leapauto.app.OldAuth
import com.leapauto.app.Session
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleCloudCoordinatorTest {
    private val identity = BleSessionIdentity("synthetic-account", "LTEST000000000001", 7, "synthetic-device")
    private val calibration = BleCalibration(61, 175, 12, 24)
    private val metadata = BleVehicleMetadata("02:00:00:00:00:01", "2.0", 1_800_000_000_000L)

    @Test
    fun attachAndMetadataRefreshNeverUploadUntilAnExplicitSave() = runBlocking {
        val harness = Harness(this)
        try {
            harness.coordinator.attach(identity)
            harness.coordinator.retryUploads()
            harness.awaitIdle()
            assertTrue(harness.gateway.configurationCalls.isEmpty())
            assertTrue(harness.gateway.calibrationCalls.isEmpty())
            assertEquals(0, harness.gateway.metadataCalls)

            harness.coordinator.refreshMetadata()
            harness.awaitIdle()
            assertEquals(1, harness.gateway.metadataCalls)
            assertEquals(metadata, harness.coordinator.state.value.profile.metadata)
            assertTrue(harness.gateway.configurationCalls.isEmpty())
            assertTrue(harness.gateway.calibrationCalls.isEmpty())

            var localSaves = 0
            assertTrue(harness.coordinator.saveCalibration(calibration) { localSaves++ })
            harness.awaitIdle()
            assertEquals(1, localSaves)
            assertEquals(listOf(calibration), harness.gateway.calibrationCalls)
            assertEquals(BleCloudSaveStatus.SAVED, harness.coordinator.state.value.profile.cloudState.calibration)
            assertNull(harness.binding)
            assertTrue(harness.gateway.configurationCalls.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun restoringPendingPreferencesDoesNotStartAnUnrequestedUpload() = runBlocking {
        val harness = Harness(this)
        try {
            val pending = BleVehicleProfile(calibration = calibration, calibrationRequested = true,
                cloudState = BleCloudSyncState(calibration = BleCloudSaveStatus.PENDING))
            assertTrue(harness.profiles.save(identity, pending))
            harness.coordinator.attach(identity)
            harness.awaitIdle()
            assertEquals(pending, harness.coordinator.state.value.profile)
            assertTrue(harness.gateway.calibrationCalls.isEmpty())
            assertEquals(0, harness.gateway.metadataCalls)
        } finally {
            harness.close()
        }
    }

    @Test
    fun staleMetadataCannotCommitAfterAnyVisibleOrPersistedIdentityChange() = runBlocking {
        val changedIdentities = listOf(identity.copy(accountId = "other-account"),
            identity.copy(vin = "LTEST000000000002"), identity.copy(generation = 8), identity.copy(deviceId = "other-device"))
        for (changed in changedIdentities) {
            for (persistedOnly in listOf(false, true)) {
                val harness = Harness(this)
                try {
                    harness.coordinator.attach(identity)
                    harness.gateway.onMetadata = {
                        if (persistedOnly) harness.session = session(changed) else harness.visibleIdentity = changed
                        metadata
                    }
                    harness.coordinator.refreshMetadata()
                    harness.awaitIdle()
                    assertNull(harness.coordinator.state.value.profile.metadata)
                    assertNull(harness.profiles.load(identity).metadata)
                    assertTrue(harness.events.none { it == BleDiagnosticEvent.VEHICLE_METADATA_RESULT })
                } finally {
                    harness.close()
                }
            }
        }
    }

    @Test
    fun supersededBlockingUploadCannotConfirmOrOverwriteTheNewerPreference() = runBlocking {
        val harness = Harness(this, Dispatchers.IO)
        val firstGate = RequestGate()
        val secondGate = RequestGate()
        val first = BlePassiveConfiguration(enabled = true)
        val second = BlePassiveConfiguration(enabled = true, autoLock = true)
        try {
            harness.binding = binding()
            harness.coordinator.attach(identity)
            harness.gateway.onConfiguration = { configuration ->
                if (configuration == first) firstGate.block() else secondGate.block()
            }
            assertTrue(harness.saveConfiguration(first))
            firstGate.awaitStarted()
            assertTrue(harness.saveConfiguration(second))
            assertEquals(second, harness.coordinator.state.value.profile.uploadedConfiguration)
            assertEquals(BleCloudSaveStatus.PENDING, harness.coordinator.state.value.profile.cloudState.configuration)
            firstGate.release()
            secondGate.awaitStarted()
            assertEquals(listOf(first, second), harness.gateway.configurationCalls)
            assertEquals(second, harness.coordinator.state.value.profile.uploadedConfiguration)
            assertEquals(BleCloudSaveStatus.SAVING, harness.coordinator.state.value.profile.cloudState.configuration)
            assertTrue(harness.events.none { it == BleDiagnosticEvent.CLOUD_SAVE_RESULT })
            secondGate.release()
            harness.awaitIdle()
            assertEquals(second, harness.profiles.load(identity).uploadedConfiguration)
            assertEquals(BleCloudSaveStatus.SAVED, harness.coordinator.state.value.profile.cloudState.configuration)
            assertEquals(1, harness.events.count { it == BleDiagnosticEvent.CLOUD_SAVE_RESULT })
        } finally {
            firstGate.release()
            secondGate.release()
            harness.close()
        }
    }

    @Test
    fun failedInitialPersistenceDoesNotApplyLocallyOrSendAnUpload() = runBlocking {
        val harness = Harness(this)
        try {
            harness.coordinator.attach(identity)
            harness.storage.rejectedWrites = setOf(1)
            var localSaves = 0
            assertFalse(harness.coordinator.saveCalibration(calibration) { localSaves++ })
            harness.awaitIdle()
            assertEquals(0, localSaves)
            assertTrue(harness.gateway.calibrationCalls.isEmpty())
            assertEquals(BleVehicleProfile(), harness.coordinator.state.value.profile)
        } finally {
            harness.close()
        }
    }

    @Test
    fun localFailureRollsBackAndDoesNotLeaveTheCancelledOldUploadSaving() = runBlocking {
        val harness = Harness(this, Dispatchers.IO)
        val gate = RequestGate()
        val previous = BlePassiveConfiguration(enabled = true)
        val rejected = BlePassiveConfiguration(enabled = true, autoUnlock = true)
        try {
            harness.binding = binding()
            harness.coordinator.attach(identity)
            harness.gateway.onConfiguration = { gate.block() }
            assertTrue(harness.saveConfiguration(previous))
            gate.awaitStarted()
            assertEquals(BleCloudSaveStatus.SAVING, harness.coordinator.state.value.profile.cloudState.configuration)
            assertFalse(harness.coordinator.saveConfiguration(rejected) { error("Synthetic local rejection") })
            gate.release()
            harness.awaitIdle()
            assertEquals(previous, harness.coordinator.state.value.profile.uploadedConfiguration)
            assertEquals(BleCloudSaveStatus.FAILED, harness.coordinator.state.value.profile.cloudState.configuration)
            assertEquals(previous, harness.profiles.load(identity).uploadedConfiguration)
            assertEquals(BleCloudSaveStatus.FAILED, harness.profiles.load(identity).cloudState.configuration)
            assertEquals(listOf(previous), harness.gateway.configurationCalls)
            assertEquals(previous, harness.binding?.desired)
            assertTrue(harness.events.none { it == BleDiagnosticEvent.CLOUD_SAVE_RESULT })
        } finally {
            gate.release()
            harness.close()
        }
    }

    @Test
    fun statusPersistenceFailuresRemainRetryableWithoutClaimingSaved() = runBlocking {
        for (failedWrite in listOf(2, 3)) {
            val harness = Harness(this)
            try {
                harness.coordinator.attach(identity)
                harness.storage.rejectedWrites = setOf(failedWrite)
                assertTrue(harness.coordinator.saveCalibration(calibration) {})
                harness.awaitIdle()
                assertEquals(BleCloudSaveStatus.FAILED, harness.coordinator.state.value.profile.cloudState.calibration)
                assertEquals(if (failedWrite == 2) 0 else 1, harness.gateway.calibrationCalls.size)
                harness.storage.rejectedWrites = emptySet()
                harness.coordinator.retryUploads()
                harness.awaitIdle()
                assertEquals(BleCloudSaveStatus.SAVED, harness.coordinator.state.value.profile.cloudState.calibration)
            } finally {
                harness.close()
            }
        }
    }

    @Test
    fun cloudSuccessNeverAcknowledgesOrChangesTheManagedVehicleApplication() = runBlocking {
        val harness = Harness(this)
        val previouslyApplied = BlePassiveConfiguration()
        val requested = BlePassiveConfiguration(enabled = true, autoUnlock = true)
        try {
            harness.binding = binding().request(previouslyApplied, identity.generation).confirmed(1, previouslyApplied)
            harness.coordinator.attach(identity)
            assertTrue(harness.saveConfiguration(requested))
            val localAfterSave = requireNotNull(harness.binding)
            harness.awaitIdle()
            assertEquals(BleCloudSaveStatus.SAVED, harness.coordinator.state.value.profile.cloudState.configuration)
            assertEquals(localAfterSave, harness.binding)
            assertEquals(previouslyApplied, harness.binding?.applied)
            assertEquals(1L, harness.binding?.confirmedRevision)
            assertTrue(requireNotNull(harness.binding).pending)
        } finally {
            harness.close()
        }
    }

    @Test
    fun explicitCalibrationDeletionUploadsNullWithoutEnablingAKey() = runBlocking {
        val harness = Harness(this)
        try {
            harness.coordinator.attach(identity)
            assertTrue(harness.coordinator.saveCalibration(null) {})
            harness.awaitIdle()
            assertEquals(listOf<BleCalibration?>(null), harness.gateway.calibrationCalls)
            assertNull(harness.coordinator.state.value.profile.calibration)
            assertEquals(BleCalibration.DEFAULT, harness.coordinator.state.value.profile.effectiveCalibration)
            assertNull(harness.binding)
            assertTrue(harness.gateway.configurationCalls.isEmpty())
        } finally {
            harness.close()
        }
    }

    private fun session(scope: BleSessionIdentity) = Session(
        deviceId = scope.deviceId, selectedVin = scope.vin, generation = scope.generation,
        oldAuth = OldAuth(scope.accountId, "synthetic-token", "synthetic-refresh", "3600", 0L)
    )

    private fun binding() = BleManagedKey(identity.accountId, identity.vin,
        BleNearbyDevice(metadata.address, "Synthetic vehicle", -55, 9), "ab".repeat(32), deviceId = identity.deviceId)

    private inner class Harness(parentScope: CoroutineScope, dispatcher: CoroutineDispatcher = Dispatchers.Unconfined) {
        val storage = MemoryStorage()
        val profiles = BleVehicleProfileStore(storage)
        val gateway = FakeGateway()
        val events = CopyOnWriteArrayList<BleDiagnosticEvent>()
        var visibleIdentity: BleSessionIdentity? = identity
        var session = session(identity)
        var binding: BleManagedKey? = null
        private val job = SupervisorJob(parentScope.coroutineContext[Job])
        val coordinator = BleCloudCoordinator(profiles, { session },
            CoroutineScope(parentScope.coroutineContext + job), { visibleIdentity }, { binding },
            { event, _, _ -> events += event }, gateway, dispatcher)

        fun saveConfiguration(configuration: BlePassiveConfiguration): Boolean = coordinator.saveConfiguration(configuration) {
            binding = requireNotNull(binding).request(configuration, identity.generation)
        }

        suspend fun awaitIdle() = withTimeout(5_000L) { job.children.toList().joinAll() }

        suspend fun close() = withTimeout(5_000L) { job.cancelAndJoin() }
    }

    private inner class FakeGateway : BleCloudGateway {
        var metadataCalls = 0
        val configurationCalls = CopyOnWriteArrayList<BlePassiveConfiguration>()
        val calibrationCalls = CopyOnWriteArrayList<BleCalibration?>()
        var onMetadata: () -> BleVehicleMetadata? = { metadata }
        var onConfiguration: (BlePassiveConfiguration) -> Unit = {}

        override fun metadata(session: Session): BleVehicleMetadata? {
            metadataCalls++
            return onMetadata()
        }

        override fun configuration(session: Session, configuration: BlePassiveConfiguration) {
            configurationCalls += configuration
            onConfiguration(configuration)
        }

        override fun calibration(session: Session, calibration: BleCalibration?) {
            calibrationCalls += calibration
        }
    }

    private class MemoryStorage : BleManagedKeyStorage {
        private val values = linkedMapOf<String, String>()
        var rejectedWrites: Set<Int> = emptySet()
        private var writes = 0

        override fun keys(): Set<String> = values.keys.toSet()
        override fun read(key: String): String? = values[key]
        override fun write(values: Map<String, String>): Boolean {
            writes++
            if (writes in rejectedWrites) return false
            this.values.putAll(values)
            return true
        }
        override fun remove(key: String): Boolean = values.remove(key) != null
    }

    private class RequestGate {
        private val started = CountDownLatch(1)
        private val released = CountDownLatch(1)

        fun block() {
            started.countDown()
            check(released.await(5, TimeUnit.SECONDS)) { "Synthetic gateway gate timed out" }
        }

        suspend fun awaitStarted() = withContext(Dispatchers.IO) {
            assertTrue("Synthetic gateway did not start", started.await(5, TimeUnit.SECONDS))
        }

        fun release() = released.countDown()
    }
}
