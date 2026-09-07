package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 桌面插件(4x2与2x2)全面对齐App主界面雪花旋转动效：3秒/圈匀速线性平滑自转，杜绝卡顿与跳帧\n" +
        "2. 修复深色/浅色模式下哨兵按钮主题色过滤与背景同步问题\n" +
        "3. 规范24dp居中方形视口，消除旋转拉伸与畸变"
}
