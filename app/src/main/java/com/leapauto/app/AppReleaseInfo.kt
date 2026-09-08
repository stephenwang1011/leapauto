package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 确认充电计划(190)下发圆满成功获得T-Box真实流水号(msgID)，车端指令正式闭环\n" +
        "2. 清理海外版无效反查路由，优化执行结果提示并剔除假错误日志"
}
