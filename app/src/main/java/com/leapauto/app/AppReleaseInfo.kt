package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 紧急修复4x2桌面插件车图充满全屏挤掉左侧数据与底部按键的严重布局Bug\n" +
        "2. 修复插件RemoteViews位图显存过载问题，优化图片缩放与IPC传输体积\n" +
        "3. 完整恢复桌面插件左侧续航进度条与底部5项控车操作按钮"
}
