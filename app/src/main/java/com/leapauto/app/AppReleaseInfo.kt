package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 优先采用零跑C16实车验证成功的独立日程接口(schedule/operate)：免操作密码直接下发谷电预约充电\n" +
        "2. 深度闭环预约充电与预约电池预热，下发结果与本地设置100%保持同步"
}
