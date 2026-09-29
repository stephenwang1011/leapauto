package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 优化设置中心交互，开启蓝牙数字钥匙后仅在主页右上角显现状态图标\n" +
        "2. 统一收敛蓝牙钥匙管理入口，点击主页右上角图标即可一键进入配置"
}
