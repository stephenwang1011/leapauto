package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 统一2x2与4x2桌面小组件深色模式按钮颜色为高光钛白，解决车锁键黑/空调键灰不一致问题\n" +
        "2. 小组件布局底层与渲染链路全面增加着色双保险，消除各大系统桌面偶发偏色"
}
