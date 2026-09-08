package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 新增插枪即时充电与停止充电控制(cmdid=193)，插枪后支持一键立即开充与停止充电\n" +
        "2. 智能插枪状态感知：未插枪时完全隐藏即时控充按钮，插枪后呈现立即充电与解锁拔枪(cmdid=192)"
}
