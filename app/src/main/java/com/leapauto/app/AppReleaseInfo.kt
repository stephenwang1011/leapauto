package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 桌面插件(小组件)全面接入液态玻璃微反光微描边：多档透明度背景新增1dp半透明高光边框\n" +
        "2. 桌面小组件与App内卡片在深浅色模式下实现高度统一的晶莹水晶悬浮质感"
}
