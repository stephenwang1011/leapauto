package com.leapauto.app.bluetooth

/**
 * Keeps the controller's GATT setup order explicit and independently testable.
 * The current compatibility path negotiates MTU before service discovery, matching
 * the recovered 1PAO 0.10 controller. A missing MTU callback is remembered for one
 * reconnect so that the next connection can continue safely with the default MTU.
 */
internal object BleGattInitializationPolicy {
    const val requestedMtu = 200
    const val mtuCallbackTimeoutMs = 2_500L

    fun initialStep(skipMtuOnce: Boolean): BleGattInitializationStep =
        if (skipMtuOnce) BleGattInitializationStep.DISCOVER_SERVICES
        else BleGattInitializationStep.REQUEST_MTU

    fun afterMtuRequest(accepted: Boolean): BleGattInitializationStep =
        if (accepted) BleGattInitializationStep.WAIT_FOR_MTU
        else BleGattInitializationStep.DISCOVER_SERVICES
}

internal enum class BleGattInitializationStep {
    REQUEST_MTU,
    WAIT_FOR_MTU,
    DISCOVER_SERVICES
}
