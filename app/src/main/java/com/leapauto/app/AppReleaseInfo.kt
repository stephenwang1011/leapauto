package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 设置界面恢复【版本信息与检查更新】卡片\n2. 优化开源代码卡片文字展示"
}
