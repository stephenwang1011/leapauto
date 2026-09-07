package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 续航单位km紧跟大数字后方加粗展示，完美对齐数字底部基线\n" +
        "2. 纯电续航与单位km全面联动电量变色逻辑（绿/橙/红），与进度条及百分比统一\n" +
        "3. 更新时间在左、位置信息在右，双列规整对齐，卡片气场饱满优雅"
}
