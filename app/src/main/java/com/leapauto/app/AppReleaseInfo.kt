package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 增强健康充电控制接口诊断日志追踪：发生异常或响应失败时自动记录入【设置 -> 诊断日志】\n" +
        "2. 记录请求参数、HTTP状态码与服务端原始完整返回，支持一键复制分析"
}
