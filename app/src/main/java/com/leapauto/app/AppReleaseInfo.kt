package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 优化蓝牙钥匙后台探查机制，解除单一 MAC 过滤并支持 ServiceUUID 动态识别\n" +
        "2. 增加探查未命中直接物理直连自愈兜底，彻底消灭“车旁无法检测到广播”问题\n" +
        "3. 全系车型首页首屏直进直出极速直达，完善数字钥匙凭证缺位静默同步闭环"
}
