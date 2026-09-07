package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 全面落地方案A：左右平衡HUD布局，彻底消除右侧大面积空白\n" +
        "2. 续航与电量横向对称展示，纯电/增程双车型完美自适应\n" +
        "3. 地址与更新时间优雅合流，车模顶空开阔舒展，状态胶囊互不干扰"
}
