package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 升级预约充电与预热双通道调度：优先直通车控原语通道（对齐XFX-CDN-CROSS-NODE与oppwd解密），并提供schedule/operate独立通道备选\n" +
        "2. 增加schedule/list与getappointment双重云端反查日志，确保鉴权与接口形态全链路透明可溯"
}
