package com.leapauto.app.bluetooth

/** Raw controller calibration parameters; the protocol does not establish units in meters. */
data class BleCalibration(
    val distanceCalibration: Int = 56,
    val coefficientHundredths: Int = 200,
    val unlockCalibration: Int = 8,
    val lockCalibration: Int = 16
) {
    init {
        require(distanceCalibration in BYTE_PROTOCOL_RANGE &&
            coefficientHundredths in COEFFICIENT_PROTOCOL_RANGE &&
            unlockCalibration in BYTE_PROTOCOL_RANGE && lockCalibration in BYTE_PROTOCOL_RANGE) {
            "Bluetooth calibration exceeds the protocol field width"
        }
    }

    fun toProtocolText(): String =
        "$distanceCalibration;${coefficientHundredths / 100}." +
            "${(coefficientHundredths % 100).toString().padStart(2, '0')};" +
            "${unlockCalibration.toString().padStart(2, '0')};${lockCalibration.toString().padStart(2, '0')}"

    fun encoded(): ByteArray = byteArrayOf(
        distanceCalibration.toByte(),
        coefficientHundredths.toByte(),
        (coefficientHundredths ushr 8).toByte(),
        unlockCalibration.toByte(),
        lockCalibration.toByte()
    )

    companion object {
        val BYTE_PROTOCOL_RANGE: IntRange = 0..255
        val COEFFICIENT_PROTOCOL_RANGE: IntRange = 0..65_535
        val DEFAULT = BleCalibration()
        val C16_DEFAULT = BleCalibration(
            distanceCalibration = 69,
            coefficientHundredths = 100,
            unlockCalibration = 4,
            lockCalibration = 21
        )
        private val TEXT = Regex("([0-9]{1,3});([0-9]{1,3})\\.([0-9]{2});([0-9]{1,3});([0-9]{1,3})")

        fun defaultForModel(carType: String?): BleCalibration {
            val model = carType.orEmpty().uppercase()
            return if (model.contains("C16") || model.contains("C10")) {
                C16_DEFAULT
            } else {
                DEFAULT
            }
        }

        fun parseOrNull(value: String): BleCalibration? {
            if (value.length > 32) return null
            val fields = TEXT.matchEntire(value)?.groupValues ?: return null
            return runCatching {
                BleCalibration(
                    fields[1].toInt(), fields[2].toInt() * 100 + fields[3].toInt(),
                    fields[4].toInt(), fields[5].toInt()
                )
            }.getOrNull()
        }
    }
}
