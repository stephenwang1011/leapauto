package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复预约充电cmdid=361重复拼接为361,361导致的类型转换错误：全面清理Query重复参数，纯粹通过FormBody传参\n" +
        "2. 深度闭环预约充电与预约电池预热参数通道，下发流程进一步精细化"
}
