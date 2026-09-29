package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 高德 Web API 升级多 Key 故障转移池，主备双活保障高可用\n" +
        "2. 逆地理编码与实况天气遇单日配额超限（10003）自动无感切换备用 Key\n" +
        "3. 系统设置新增高德 Web 服务配置卡片，支持进阶车主填入专属 Key\n" +
        "4. 解除城市实况天气车架号限制，面向全系车型与全部车辆开放"
}
