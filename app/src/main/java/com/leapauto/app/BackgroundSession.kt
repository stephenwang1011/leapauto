package com.leapauto.app

/** Immutable auth values are shared; the mutable request container is always private to one task. */
internal fun Session.requestSnapshot(): Session = synchronized(this) {
    Session(
        deviceId = deviceId, phone = phone, smDeviceId = smDeviceId,
        oldAuth = oldAuth, newAuth = newAuth, selectedVin = selectedVin,
        selectedCarType = selectedCarType, selectedNickname = selectedNickname,
        selectedYear = selectedYear, route = route, hvacCapability = hvacCapability,
        generation = generation
    )
}

internal class BackgroundSession(source: Session, val epoch: Long) {
    private val original = source.requestSnapshot()
    val request = original.requestSnapshot()
    val vin: String get() = original.selectedVin

    fun matches(current: Session, currentEpoch: Long): Boolean =
        epoch == currentEpoch && current.generation == original.generation &&
            current.selectedVin == vin && current.deviceId == original.deviceId &&
            current.oldAuth?.accountId == original.oldAuth?.accountId &&
            current.newAuth?.accountId == original.newAuth?.accountId

    fun canMergeCredentials(current: Session): Boolean = synchronized(current) {
        matches(current, epoch) && current.oldAuth == original.oldAuth &&
            current.newAuth == original.newAuth && request.deviceId == original.deviceId &&
            request.oldAuth?.accountId == original.oldAuth?.accountId &&
            request.newAuth?.accountId == original.newAuth?.accountId
    }

    fun credentialsChanged(): Boolean = request.oldAuth != original.oldAuth || request.newAuth != original.newAuth

    fun mergeCredentials(current: Session): Boolean = synchronized(current) {
        if (!canMergeCredentials(current)) return false
        current.oldAuth = request.oldAuth
        current.newAuth = request.newAuth
        true
    }
}
