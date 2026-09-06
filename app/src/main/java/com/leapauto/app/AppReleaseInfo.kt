package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 车模增加底盘接地微暗影，纯电能量条增加充电呼吸光效\n" +
        "2. 空调卡片增加运行冷暖微氛围底色，车况卡片高度对齐\n" +
        "3. 周能耗环形图内部优化为双层表盘排版展示总能耗"
}
