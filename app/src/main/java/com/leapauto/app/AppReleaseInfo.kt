package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 设置中心新增蓝牙数字钥匙总开关，默认关闭以保持界面清爽与极致省电\n" +
        "2. 开启后联动显现蓝牙钥匙管理卡片与主页右上角蓝牙状态胶囊图标"
}
