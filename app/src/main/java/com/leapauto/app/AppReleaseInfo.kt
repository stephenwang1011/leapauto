package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 充电中心充电状态高频刷新：充电时界面每1秒自动同步一次最新电量、功率与倒计时\n" +
        "2. 退出充电中心或停止充电时自动恢复节能巡检，兼顾极致流畅与低功耗"
}
