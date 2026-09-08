package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 参考零跑官方App全新重构充电中心(图1)：3D电池底盘透视模型与健康充电卡片、预约充电卡片、预约电池预热三卡合一\n" +
        "2. 参考官方App全新重构预约充电与电池预热弹窗(图2、图3)：质感双轮盘时间滚轮、周日~周六7天独立胶囊与次日联动"
}
