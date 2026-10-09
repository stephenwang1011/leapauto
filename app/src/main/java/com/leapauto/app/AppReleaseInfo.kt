package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 小组件背景风格设置回归，支持设置透明度\n2. 修复部分增程车不显示燃油续航问题\n3. APP图标优化"
}
