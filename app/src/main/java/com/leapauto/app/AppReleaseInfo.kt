package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 续航数字与km同排加粗展示，km紧跟在数字后方并在底部平齐贴地\n" +
        "2. 核心数据组整体上提5dp，将视觉间距收紧至精致的7~8dp呼吸感\n" +
        "3. 换装通用标准空心水滴地图图钉矢量图标，位置信息与图标统一调整为沉稳黑"
}
