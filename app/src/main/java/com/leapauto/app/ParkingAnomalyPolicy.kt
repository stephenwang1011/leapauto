package com.leapauto.app

/** Timing and command gate for the one-shot post-lock parking check. */
object ParkingAnomalyPolicy {
    const val POST_LOCK_CHECK_DELAY_MS = 5_000L

    fun shouldCheck(commandName: String, commandAccepted: Boolean): Boolean =
        commandAccepted && commandName.equals("lock", ignoreCase = true)
}
