package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 优化车门锁控制响应速度：消除桌面插件等待延迟，指令反馈大幅提速\n" +
        "2. 桌面插件与控制中心实时同步门锁状态，落锁与解锁即按即变\n" +
        "3. 锁车防盗安全检测异步后台化，保证操作流畅零阻塞"
}
