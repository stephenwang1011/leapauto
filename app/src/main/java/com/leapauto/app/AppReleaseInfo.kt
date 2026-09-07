package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复增程车型纯电与燃油微胶囊变色逻辑，全面联动电量/油量动态变色（绿/橙/红）\n" +
        "2. 胶囊内图标同步动态着色，消除生硬写死绿底与图标发灰违和感\n" +
        "3. 续航单位km紧跟大数字后方加粗展示，完美对齐数字底部基线"
}
