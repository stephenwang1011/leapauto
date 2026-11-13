package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 修复定时空调到时间之后没有关闭窗户的问题\n2. 优化定时空调时间选择跟手问题，同时添加震动效果\n3. 优化桌面组件高度和车图扁平比（若无生效删除组件重新添加）"
}
