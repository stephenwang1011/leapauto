package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 直进直出全面接入物理蓝牙射频通道，修复长按前进后退车辆无响应问题\n" +
        "2. 实现车规级 250ms 连续心跳脉冲与松手刹停，保障遥控移动安全平稳\n" +
        "3. 开启直进直出自动同步 3 秒车况高频刷新，实时捕捉手刹解除与实际车速\n" +
        "4. 彻底修复车窗全开却误显为微开 15% 的问题，实现状态枚举与全开比例准确呈现\n" +
        "5. 蓝牙锁控成功后自动异步对齐官方 uploadRecords 审计上报闭环"
}
