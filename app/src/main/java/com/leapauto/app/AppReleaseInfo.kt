package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 升级零跑官方权威充电计划协议(cmdid=190)：对齐RemoteActionCtlChargePlan全套原生字段\n" +
        "2. 升级电池预热预约权威协议(cmdid=161)：controls数组级封装与车端appointment双通道精准对接"
}
