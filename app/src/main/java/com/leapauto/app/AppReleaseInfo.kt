package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复登录成功后异常退出及无法保存会话的问题\n" +
        "2. 优化主界面车况卡片渲染性能与布局稳定性\n" +
        "3. 加固本地凭据同步存储与异常恢复机制"
}
