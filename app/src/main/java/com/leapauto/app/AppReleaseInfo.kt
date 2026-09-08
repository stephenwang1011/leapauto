package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 确认健康充电(code=0)与预约充电(code=0)双双请求成功下发车机\n" +
        "2. 清理内部无效探针，仅在真实异常时写入诊断日志，提示清晰友好"
}
