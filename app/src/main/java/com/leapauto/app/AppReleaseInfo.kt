package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 彻底移除座驾主图上方所有状态告警胶囊，还车身与HUD百分之百纯净舒展\n" +
        "2. 紧急修复4x2桌面插件车图充满全屏挤掉左侧数据与底部按键的严重布局Bug\n" +
        "3. 修复插件RemoteViews位图显存过载问题，优化图片缩放与IPC传输体积"
}
