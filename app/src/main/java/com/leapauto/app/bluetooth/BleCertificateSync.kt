package com.leapauto.app.bluetooth

import com.leapauto.app.Session

/** Holds the pre-request credentials while the isolated request session may refresh them. */
class BleCertificateSync(session: Session) {
    val identity = BleSessionIdentity(session.oldAuth?.accountId.orEmpty(), session.selectedVin,
        session.generation, session.deviceId)
    private val oldAuth = session.oldAuth
    private val newAuth = session.newAuth

    fun canCommit(current: Session, refreshed: Session, certificate: BleKeyCertificate): Boolean =
        identity.matches(current.oldAuth?.accountId, current.selectedVin, current.generation, current.deviceId) &&
            current.oldAuth == oldAuth && current.newAuth == newAuth &&
            identity.accountId == refreshed.oldAuth?.accountId && identity.vin == refreshed.selectedVin &&
            identity.generation == refreshed.generation && refreshed.deviceId.isNotBlank() &&
            certificate.vin == identity.vin

    override fun toString(): String = "BleCertificateSync(redacted)"
}
