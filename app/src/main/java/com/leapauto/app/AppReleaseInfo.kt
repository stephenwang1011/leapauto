package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 充电中心顶部指标升级为三列对称展示：最低电池温度、实时充电功率与格式化充电剩余时间\n" +
        "2. 智能适配充电与未充电状态，充电中功率与剩余时间高亮显示"
}
