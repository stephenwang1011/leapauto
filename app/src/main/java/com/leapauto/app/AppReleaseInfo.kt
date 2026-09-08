package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 移除首页快捷控车中的电池预热按键及旧版预热弹窗，避免冗余和误触\n" +
        "2. 电池预热与定时预约统一在“充电中心”中管理，对齐官方体验"
}
