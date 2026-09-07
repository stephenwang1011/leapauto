package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复预约充电与预约电池预热请求通道：对齐车端appointment接口form-urlencoded协议，解决Content-Type不被支持问题\n" +
        "2. 健康充电控制已成功验证闭环（code=0），预约充电与预热功能持续优化完善"
}
