package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 换装通用标准地图图钉矢量图标（空心水滴图钉），告别实心黑坨，辨识度显著提升\n" +
        "2. 定位微图标与地址文字统一调整为优雅黑，风格沉稳克制\n" +
        "3. 更新Mock燃油数据：燃油续航调整为300km，油箱百分比调整为50%（健康绿状态）"
}
