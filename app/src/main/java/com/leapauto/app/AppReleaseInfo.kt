package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 新增首次未确认动力模式弹窗，基于硬件信号智能预选\n2. 设置页新增动力模式（纯电/增程）常驻切换卡片\n3. 彻底兼顾纯电车型单电量条与增程车型油电双续航展示"
}
