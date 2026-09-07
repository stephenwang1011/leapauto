package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复纯电车型电量百分比底部被裁切问题，彻底移除强制高度限制\n" +
        "2. 优化指标子群纵向对齐与紧凑行高，km与WLTC自然对齐大数字顶部\n" +
        "3. 保持续航条与百分比长度严格对齐km与WLTC，双车型完美适配"
}
