package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 预约充电全链路贯通：串联执行schedule/operate(云端入库)、schedule/syncCode(车机编译下发码)与appointment车控原语多通道\n" +
        "2. 诊断日志完整输出各通道原始回传结果，确保车端生效证据全透明"
}
