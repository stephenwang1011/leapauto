package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 周能耗分布卡片圆环中心增加总能耗展示\n" +
        "2. 优化周能耗分布卡片视觉效果，字体统一加粗展示\n" +
        "3. 修复登录成功后异常退出及无法保存会话的问题"
}
