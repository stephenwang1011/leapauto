package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 纯电车型HUD升级为指标子群：续航条与百分比置于km与WLTC下方，长宽与高度严格对齐大数字\n" +
        "2. 增程纯电双自适应，彻底消除多层堆叠与视觉杂乱\n" +
        "3. 保持车图100%原厂饱满大气比例，状态胶囊通透悬浮"
}
