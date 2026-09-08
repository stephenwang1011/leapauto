package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 预约充电全流程圆满成功：锁定100%成功的operate(入库)+syncCode(车端同步码)双通关链路，移除冗余老接口\n" +
        "2. 修正诊断日志记录机制：下发成功(code=0)时不再记录错误日志，界面与交互纯净稳定"
}
