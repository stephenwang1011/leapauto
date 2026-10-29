package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 适配不同主题的深色模式，可在主题中心界面下自由调节亮度，默认已经调节好\n2. 修复部分车型定时空调无法工作问题\n3. UI 样式优化和 bug 修复"
}
