package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复健康充电控制请求协议：对齐车端网关'state'开关参数及Query/Body全通道传参\n" +
        "2. 保持诊断日志全量捕获，支持下发结果实时追踪"
}
