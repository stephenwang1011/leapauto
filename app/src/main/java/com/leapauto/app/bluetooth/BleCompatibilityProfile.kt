package com.leapauto.app.bluetooth

/**
 * Keeps the recovered 1PAO protocol choices together so authentication text,
 * configuration bytes and transport chunking cannot silently drift apart.
 */
enum class BleCompatibilityProfile(
    val authenticationText: BleAuthenticationTextProfile,
    val chunkProfile: BleChunkProfile,
    val diagnosticCode: Int
) {
    LEGACY(BleAuthenticationTextProfile.LEGACY_VERSIONED, BleChunkProfile.LEGACY_160, 0),
    ONE_PAO_V010(BleAuthenticationTextProfile.ONE_PAO_V010, BleChunkProfile.ONE_PAO_V010, 1);

    fun encodeConfiguration(
        configuration: BlePassiveConfiguration,
        protocolMinor: Int,
        supportsButton: Boolean = false
    ): ByteArray =
        when (this) {
            LEGACY -> configuration.encoded(protocolMinor)
            ONE_PAO_V010 -> configuration.reconnectFields(supportsButton)
        }

    companion object {
        /**
         * No successful legacy vehicle authentication has been observed in this app.
         * Use the recovered fixed-layout protocol while retaining LEGACY as an explicit
         * compatibility option for future vehicle-confirmed bindings.
         */
        fun forConnection(): BleCompatibilityProfile = ONE_PAO_V010
    }
}
