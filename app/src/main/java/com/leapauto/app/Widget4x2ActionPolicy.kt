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
        Widget4x2Action("windowOpen", "车窗半开", R.drawable.ic_phosphor_wind),
        Widget4x2Action("windowVent", "车窗通风", R.drawable.ic_phosphor_wind),
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
