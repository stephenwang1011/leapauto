package com.leapauto.app

import org.json.JSONObject

/**
 * 后备箱遥测状态。未知状态不能被当作已关闭或已打开，否则会把一次点击
 * 误转换成相反的控车命令。
 */
enum class TrunkState {
    UNKNOWN,
    CLOSED,
    OPEN;

    fun persistedValue(): Boolean? = when (this) {
        CLOSED -> false
        OPEN -> true
        UNKNOWN -> null
    }

    companion object {
        fun fromPersisted(value: Boolean?): TrunkState = when (value) {
            false -> CLOSED
            true -> OPEN
            null -> UNKNOWN
        }
    }
}

/** Shared signal decoder for the app and widget. */
object TrunkStateMapper {
    /**
     * 0/false is confirmed closed in the checked-in signal samples. Boolean true
     * and numeric/string 1 are treated as the matching open value; other non-zero
     * values remain UNKNOWN instead of being guessed as open.
     */
    fun fromSignal(value: Any?): TrunkState = when (value) {
        null, JSONObject.NULL -> TrunkState.UNKNOWN
        is Boolean -> if (value) TrunkState.OPEN else TrunkState.CLOSED
        is Number -> fromNumber(value)
        else -> fromText(value.toString())
    }

    private fun fromInteger(value: Int): TrunkState = when (value) {
        0 -> TrunkState.CLOSED
        1 -> TrunkState.OPEN
        else -> TrunkState.UNKNOWN
    }

    private fun fromNumber(value: Number): TrunkState {
        val numeric = value.toDouble()
        if (!numeric.isFinite() || numeric != numeric.toInt().toDouble()) {
            return TrunkState.UNKNOWN
        }
        return fromInteger(numeric.toInt())
    }

    private fun fromText(value: String): TrunkState = when (value.trim().lowercase()) {
        "0", "false", "closed", "close", "关闭" -> TrunkState.CLOSED
        "1", "true", "open", "opened", "opening", "打开", "开启" -> TrunkState.OPEN
        else -> TrunkState.UNKNOWN
    }
}

data class TrunkControlPresentation(
    val command: String?,
    val contentDescription: String
)

object TrunkControlPresentationMapper {
    fun fromState(state: TrunkState): TrunkControlPresentation = when (state) {
        TrunkState.CLOSED -> TrunkControlPresentation("trunkOpen", "打开后备箱")
        TrunkState.OPEN -> TrunkControlPresentation("trunkClose", "关闭后备箱")
        TrunkState.UNKNOWN -> TrunkControlPresentation(null, "后备箱状态未知，打开 App 刷新")
    }
}
