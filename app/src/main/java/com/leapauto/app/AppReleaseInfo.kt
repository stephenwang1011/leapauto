package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 开启蓝牙钥匙增加精简安全提示弹窗，明确双端信道冲突风险\n2. 建议优先使用官方App蓝牙与NFC钥匙，并提供保持关闭防误触引导"
}
