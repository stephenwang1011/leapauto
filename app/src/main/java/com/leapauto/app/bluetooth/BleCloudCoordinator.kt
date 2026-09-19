package com.leapauto.app.bluetooth

import android.content.Context
import android.os.Build
import com.leapauto.app.LeapmotorApi
import com.leapauto.app.SessionStore
import com.leapauto.app.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BleCloudUiState(
    val profile: BleVehicleProfile = BleVehicleProfile(),
    val metadataLoading: Boolean = false,
    val metadataMessage: String = "尚未同步车辆蓝牙信息"
)

/** Cloud preferences never authorize vehicle actions; callers own the local PIN/confirmation flow. */
class BleCloudCoordinator internal constructor(
    private val profiles: BleVehicleProfileStore,
    private val loadSession: () -> Session,
    private val lifecycleScope: CoroutineScope,
    private val currentIdentity: () -> BleSessionIdentity?,
    private val localBinding: () -> BleManagedKey?,
    private val record: (BleDiagnosticEvent, Int?, Int?) -> Unit,
    private val gateway: BleCloudGateway,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    constructor(context: Context, lifecycleScope: CoroutineScope, currentIdentity: () -> BleSessionIdentity?,
        localBinding: () -> BleManagedKey?, record: (BleDiagnosticEvent, Int?, Int?) -> Unit) :
        this(BleVehicleProfileStore(context), SessionStore(context)::load, lifecycleScope, currentIdentity,
            localBinding, record, AndroidBleCloudGateway)
    private val value = MutableStateFlow(BleCloudUiState())
    val state = value.asStateFlow()
    private var identity: BleSessionIdentity? = null
    private var generation = 0L
    private var metadataJob: Job? = null
    private var uploadJob: Job? = null
    private var revision = 0L

    fun attach(next: BleSessionIdentity) {
        if (identity == next) return
        detach()
        identity = next
        val profile = profiles.load(next)
        value.value = BleCloudUiState(profile, metadataMessage =
            if (BleVehicleProfile.freshMetadata(profile.metadata, System.currentTimeMillis()) != null)
                "已缓存车辆蓝牙信息" else "尚未同步车辆蓝牙信息")
    }

    fun detach() {
        generation++
        metadataJob?.cancel()
        uploadJob?.cancel()
        metadataJob = null
        uploadJob = null
        identity?.let { profiles.save(it, value.value.profile.interrupted()) }
        identity = null
        value.value = BleCloudUiState()
    }

    fun refreshMetadata() {
        val scope = identity ?: return
        if (currentIdentity() != scope || metadataJob?.isActive == true) return
        val epoch = generation
        val requestSession = loadSession()
        if (!scope.matches(requestSession.oldAuth?.accountId, requestSession.selectedVin,
                requestSession.generation, requestSession.deviceId)) return
        value.value = value.value.copy(metadataLoading = true, metadataMessage = "正在同步车辆蓝牙信息")
        record(BleDiagnosticEvent.VEHICLE_METADATA_STARTED, null, null)
        metadataJob = lifecycleScope.launch {
            try {
                val metadata = withContext(dispatcher) { gateway.metadata(requestSession) }
                if (!isCurrent(scope, epoch)) return@launch
                val saved = commit(value.value.profile.copy(metadata = metadata))
                value.value = value.value.copy(metadataMessage = when {
                    !saved -> "车辆蓝牙信息保存失败"
                    metadata == null -> "云端未提供车辆蓝牙地址"
                    else -> "车辆蓝牙信息已同步"
                })
                record(BleDiagnosticEvent.VEHICLE_METADATA_RESULT, if (saved && metadata != null) 1 else 0, null)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isCurrent(scope, epoch)) {
                    value.value = value.value.copy(metadataMessage = "车辆蓝牙信息同步失败，可重试")
                    record(BleDiagnosticEvent.VEHICLE_METADATA_RESULT, -1, null)
                }
            } finally {
                if (isCurrent(scope, epoch)) value.value = value.value.copy(metadataLoading = false)
            }
        }
    }

    fun saveConfiguration(configuration: BlePassiveConfiguration, applyLocal: () -> Unit): Boolean {
        val next = value.value.profile.copy(uploadedConfiguration = configuration.copy(calibration = BleCalibration.DEFAULT),
            cloudState = value.value.profile.cloudState.copy(configuration = BleCloudSaveStatus.PENDING))
        return saveLocally(next, applyLocal)
    }

    fun saveCalibration(calibration: BleCalibration?, applyLocal: () -> Unit): Boolean {
        val next = value.value.profile.copy(calibration = calibration, calibrationRequested = true,
            cloudState = value.value.profile.cloudState.copy(calibration = BleCloudSaveStatus.PENDING))
        return saveLocally(next, applyLocal)
    }

    private fun saveLocally(next: BleVehicleProfile, applyLocal: () -> Unit): Boolean {
        if (identity == null || currentIdentity() != identity) return false
        val previous = value.value.profile
        if (!commit(next)) return false
        revision++
        uploadJob?.cancel()
        try {
            applyLocal()
        } catch (_: Exception) {
            val rollback = previous.interrupted()
            if (!commit(rollback)) value.value = value.value.copy(profile = rollback)
            return false
        }
        retryUploads()
        return true
    }

    fun retryUploads() {
        val scope = identity ?: return
        if (currentIdentity() != scope) return
        val epoch = generation
        val requestedRevision = revision
        uploadJob?.cancel()
        uploadJob = lifecycleScope.launch {
            // A superseded blocking request finishes before a newer preference is uploaded.
            uploads.withLock {
                if (!isCurrent(scope, epoch) || revision != requestedRevision) return@withLock
                uploadPreference(scope, epoch, requestedRevision, calibration = false)
                if (isCurrent(scope, epoch) && revision == requestedRevision) {
                    uploadPreference(scope, epoch, requestedRevision, calibration = true)
                }
            }
        }
    }

    private suspend fun uploadPreference(scope: BleSessionIdentity, epoch: Long, expectedRevision: Long, calibration: Boolean) {
        val profile = value.value.profile
        val status = if (calibration) profile.cloudState.calibration else profile.cloudState.configuration
        if (status !in setOf(BleCloudSaveStatus.PENDING, BleCloudSaveStatus.FAILED, BleCloudSaveStatus.SAVING)) return
        if (calibration && !profile.calibrationRequested || !calibration && profile.uploadedConfiguration == null) return
        if (!BleCloudUploadPolicy.matchesLocal(profile, localBinding(), calibration)) {
            setStatus(calibration, BleCloudSaveStatus.FAILED)
            return
        }
        val requestSession = loadSession()
        if (!scope.matches(requestSession.oldAuth?.accountId, requestSession.selectedVin,
                requestSession.generation, requestSession.deviceId)) return
        if (!setStatus(calibration, BleCloudSaveStatus.SAVING)) return
        record(BleDiagnosticEvent.CLOUD_SAVE_STARTED, if (calibration) 1 else 0, null)
        try {
            withContext(dispatcher) {
                if (calibration) gateway.calibration(requestSession, profile.calibration)
                else gateway.configuration(requestSession, requireNotNull(profile.uploadedConfiguration))
            }
            if (isCurrent(scope, epoch) && revision == expectedRevision) {
                val saved = setStatus(calibration, BleCloudSaveStatus.SAVED)
                record(BleDiagnosticEvent.CLOUD_SAVE_RESULT, if (calibration) 1 else 0, if (saved) 1 else 0)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (isCurrent(scope, epoch) && revision == expectedRevision) {
                setStatus(calibration, BleCloudSaveStatus.FAILED)
                record(BleDiagnosticEvent.CLOUD_SAVE_RESULT, if (calibration) 1 else 0, 0)
            }
        }
    }

    private fun setStatus(calibration: Boolean, status: BleCloudSaveStatus): Boolean {
        val current = value.value.profile
        val cloud = if (calibration) current.cloudState.copy(calibration = status)
            else current.cloudState.copy(configuration = status)
        if (commit(current.copy(cloudState = cloud))) return true
        val failed = if (calibration) current.cloudState.copy(calibration = BleCloudSaveStatus.FAILED)
            else current.cloudState.copy(configuration = BleCloudSaveStatus.FAILED)
        value.value = value.value.copy(profile = current.copy(cloudState = failed))
        return false
    }

    private fun commit(profile: BleVehicleProfile): Boolean {
        val scope = identity ?: return false
        if (currentIdentity() != scope || !profiles.save(scope, profile)) return false
        value.value = value.value.copy(profile = profile)
        return true
    }

    private fun isCurrent(scope: BleSessionIdentity, epoch: Long): Boolean =
        generation == epoch && identity == scope && currentIdentity() == scope && loadSession().let {
            scope.matches(it.oldAuth?.accountId, it.selectedVin, it.generation, it.deviceId)
        }

    companion object {
        private val uploads = Mutex()
    }
}

internal interface BleCloudGateway {
    fun metadata(session: Session): BleVehicleMetadata?
    fun configuration(session: Session, configuration: BlePassiveConfiguration)
    fun calibration(session: Session, calibration: BleCalibration?)
}

private object AndroidBleCloudGateway : BleCloudGateway {
    override fun metadata(session: Session): BleVehicleMetadata? = LeapmotorApi(session).getBluetoothVehicleMetadata()
    override fun configuration(session: Session, configuration: BlePassiveConfiguration) =
        LeapmotorApi(session).uploadBluetoothConfiguration(configuration)
    override fun calibration(session: Session, calibration: BleCalibration?) =
        LeapmotorApi(session).uploadBluetoothCalibration(calibration?.toProtocolText(), Build.MODEL)
}

internal object BleCloudUploadPolicy {
    fun matchesLocal(profile: BleVehicleProfile, binding: BleManagedKey?, calibration: Boolean): Boolean =
        if (calibration) binding == null || binding.desired.calibration == profile.effectiveCalibration
        else binding != null && profile.uploadedConfiguration == binding.desired.copy(calibration = BleCalibration.DEFAULT)
}
