package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 彻底优化蓝牙钥匙后台探查退避机制，将 45 秒长休眠压缩至 8~12 秒均衡自愈\n" +
        "2. 探查窗口提升至 4~5 秒，极大提升走近车辆拉车门时的极速秒连成功率\n" +
        "3. 强化多维广播指纹与 RPA 随机私有地址自适应穿透识别"
}
