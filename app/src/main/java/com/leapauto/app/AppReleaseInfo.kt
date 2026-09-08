package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 补齐独立日程接口(schedule/operate与schedule/list)车端必需的'model'车型与'type'整型参数\n" +
        "2. 深度闭环预约充电与预约电池预热参数字典，支持谷电时段自动开充"
}
