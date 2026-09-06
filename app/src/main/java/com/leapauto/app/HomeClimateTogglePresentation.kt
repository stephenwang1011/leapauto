package com.leapauto.app

enum class HomeClimateToggleIcon {
    ON,
    OFF
}

data class HomeClimateTogglePresentation(
    val icon: HomeClimateToggleIcon,
    val contentDescription: String,
    val command: String?,
    val enabled: Boolean
)

object HomeClimateTogglePresentationMapper {
    fun from(acSwitch: Boolean?, controlBusy: Boolean): HomeClimateTogglePresentation {
        val enabled = acSwitch != null && !controlBusy
        val isOn = acSwitch == true

        return HomeClimateTogglePresentation(
            icon = if (isOn) HomeClimateToggleIcon.ON else HomeClimateToggleIcon.OFF,
            contentDescription = when (acSwitch) {
                true -> "关闭空调"
                false -> "开启空调"
                null -> "空调状态未同步"
            },
            command = if (!enabled) null else if (isOn) "acOff" else "acOn",
            enabled = enabled
        )
    }
}
