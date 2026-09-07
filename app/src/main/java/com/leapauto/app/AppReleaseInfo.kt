package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 位置信息胶囊配色精修：定位微图标与地址文字统一调整为优雅黑，风格克制沉稳\n" +
        "2. 更新Mock燃油数据：燃油续航调整为300km，油箱百分比调整为50%（健康绿状态）\n" +
        "3. 修复增程车型纯电与燃油微胶囊变色逻辑，全面联动电量/油量动态变色（绿/橙/红）"
}
