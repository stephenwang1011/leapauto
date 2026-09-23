package com.leapauto.app

data class Widget4x2Action(
    val id: String,
    val label: String,
    val iconRes: Int
)

object Widget4x2ActionPolicy {
    val ALL_AVAILABLE_ACTIONS = listOf(
        Widget4x2Action("unlock", "解锁", R.drawable.ic_phosphor_lock_open),
        Widget4x2Action("lock", "上锁", R.drawable.ic_phosphor_lock),
        Widget4x2Action("sentry", "哨兵模式", R.drawable.ic_sentry),
        Widget4x2Action("ac", "空调", R.drawable.ic_phosphor_fan),
        Widget4x2Action("trunk", "后备箱", R.drawable.ic_phosphor_trunk_open),
        Widget4x2Action("frunk", "前备箱", R.drawable.ic_phosphor_trunk_open),
        Widget4x2Action("windowOpen", "车窗半开", R.drawable.ic_window_half),
        Widget4x2Action("windowVent", "车窗通风", R.drawable.ic_window_vent),
        Widget4x2Action("windowClose", "一键关窗", R.drawable.ic_phosphor_wind),
        Widget4x2Action("sunshade", "遮阳帘", R.drawable.ic_phosphor_sun),
        Widget4x2Action("horn", "鸣笛寻车", R.drawable.ic_phosphor_bell_ringing)
    )

    val DEFAULT_ACTIONS = listOf("unlock", "lock", "sentry", "ac", "trunk")

    const val MAX_ACTIONS = 5
    const val MIN_ACTIONS = 4

    fun findAction(id: String): Widget4x2Action? =
        ALL_AVAILABLE_ACTIONS.firstOrNull { it.id == id }

    fun resolve(saved: List<String>?): List<String> {
        val valid = saved.orEmpty().filter { id -> ALL_AVAILABLE_ACTIONS.any { it.id == id } }
        return when {
            valid.size >= 5 -> valid.take(5)
            valid.size == 4 -> valid
            else -> DEFAULT_ACTIONS
        }
    }
}

object WidgetWindowTogglePolicy {
    /**
     * 当小组件配置了“车窗半开”或“车窗通风”按键时：
     * 若当前车窗为关闭状态，点击发送对应开窗指令；
     * 若当前车窗已开（微开或半开），再次点击自动转换为“一键关窗”指令。
     */
    fun resolveCommand(command: String, isWindowOpen: Boolean): String {
        return if ((command == "windowOpen" || command == "windowVent") && isWindowOpen) {
            "windowClose"
        } else {
            command
        }
    }

    fun contentDescription(command: String, isWindowOpen: Boolean): String = when (command) {
        "windowVent" -> if (isWindowOpen) "一键关窗（当前车窗已开）" else "车窗通风"
        "windowOpen" -> if (isWindowOpen) "一键关窗（当前车窗已开）" else "车窗半开"
        "windowClose" -> "一键关窗"
        else -> command
    }
}
