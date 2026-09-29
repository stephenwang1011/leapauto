package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 直进直出解除单一车架号限制，面向全系支持直进直出/遥控泊车车型开放\n" +
        "2. 适配 C16、C10、C11、C01、B10 等全系座舱直进直出控制通道\n" +
        "3. 严格遵循车端硬件能力隔离与安全状态机，非支持车型智能提示暂不可用"
}
