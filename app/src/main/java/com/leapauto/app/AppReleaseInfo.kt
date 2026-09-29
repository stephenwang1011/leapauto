package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复车窗开度比例持久化联动，解决杀掉应用重启后 3D 车模误显示为半开的问题\n" +
        "2. 无论应用冷启动或进程重启，3D 车模与车况卡片严格精准呈现微开/半开/全关设定"
}
