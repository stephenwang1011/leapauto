package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 续航指标间距极致收敛：大数字与下方电量进度条间隙收紧至3~4dp，排版极度紧致一体\n" +
        "2. 续航数字与km同排加粗展示，km紧跟在数字后方并在底部平齐贴地\n" +
        "3. 纯电续航与单位km全面联动电量变色逻辑（绿/橙/红），与进度条及百分比统一"
}
