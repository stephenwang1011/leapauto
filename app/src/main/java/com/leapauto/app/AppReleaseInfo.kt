package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 优化首页快捷控制栏排布，将直进直出按钮移至快捷功能末尾\n" +
        "2. 保持首屏空间聚焦日常高频锁控与常用车身控制"
}
