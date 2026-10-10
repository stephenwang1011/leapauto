package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 实装同座椅加热与通风物理互斥逻辑，开启一项自动切断另一项\n2. 全面扩展全车控车 15 秒防回弹保护，彻底解决后备箱、车窗与车锁状态延迟闪烁\n3. 优化桌面小组件后台控车同步机制，状态反馈更及时"
}
