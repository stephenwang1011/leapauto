package com.leapauto.app.bluetooth

data class BleControlConfirmation(
    val action: BleLockAction,
    val identity: BleSessionIdentity,
    val connectionGeneration: Long
) {
    fun isCurrent(
        currentIdentity: BleSessionIdentity?,
        currentGeneration: Long,
        foreground: Boolean,
        authenticated: Boolean,
        managementVisible: Boolean
    ): Boolean = managementVisible && foreground && authenticated && currentGeneration == connectionGeneration &&
        currentIdentity != null &&
        identity.matches(currentIdentity.accountId, currentIdentity.vin, currentIdentity.generation, currentIdentity.deviceId)
}
