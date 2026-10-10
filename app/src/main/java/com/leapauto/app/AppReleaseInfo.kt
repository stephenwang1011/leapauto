package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 优化车门锁响应时序（800ms拟真响应 + 1.2s/1.5s/2.0s/2.5s极速早退轮询）\n2. 建立 15 秒车锁状态防回弹保护，彻底解决解锁/锁车后状态未及时刷新问题"
}
