package com.leapauto.app.bluetooth

object BleAccessPolicy {
    fun isSupportedKeyType(keyType: Int?): Boolean = keyType == 0 || keyType == 1

    fun isCertificateReady(certificate: BleKeyCertificate?, selectedVin: String): Boolean =
        certificate != null && selectedVin.isNotBlank() && certificate.vin == selectedVin &&
            isSupportedKeyType(certificate.keyType)

    fun canScan(phase: BleConnectionPhase, certificateLoading: Boolean): Boolean =
        !certificateLoading && phase in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.FAILED)

    fun canSyncCertificate(phase: BleConnectionPhase, certificateLoading: Boolean): Boolean =
        !certificateLoading && phase in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.FAILED)

    fun canConnect(phase: BleConnectionPhase, certificateLoading: Boolean, certificateReady: Boolean): Boolean =
        certificateReady && !certificateLoading &&
            phase in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.SCANNING, BleConnectionPhase.FAILED)

    fun canStartCloudLockControl(phase: BleConnectionPhase, cloudBusy: Boolean): Boolean =
        !cloudBusy && phase != BleConnectionPhase.SENDING

    fun canApplyConfiguration(
        state: BleConnectionState,
        binding: BleManagedKey?,
        certificate: BleKeyCertificate?,
        configuration: BlePassiveConfiguration
    ): Boolean = !state.isBusy && state.phase != BleConnectionPhase.SCANNING && binding != null &&
        (!configuration.enabled || (certificate != null && binding.matchesCertificate(certificate))) &&
        (!configuration.enabled || !configuration.buttonEnabled || (binding.device.protocolMinor ?: 0) >= 9)

    fun certificateMessage(certificate: BleKeyCertificate?, selectedVin: String): String = when {
        certificate == null -> "尚未同步钥匙"
        certificate.vin != selectedVin || selectedVin.isBlank() -> "钥匙与当前车辆不匹配，请重新同步"
        !isSupportedKeyType(certificate.keyType) -> "当前钥匙类型暂不支持连接"
        certificate.keyType == 1 -> "SM2 钥匙已就绪"
        else -> "钥匙已就绪"
    }
}
