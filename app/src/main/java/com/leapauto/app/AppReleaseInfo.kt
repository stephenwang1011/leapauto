package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 升级座舱蓝牙多维特征自适应匹配，支持 ServiceUUID、MD5(VIN) 与已绑定 MAC 瞬时捕获\n" +
        "2. 强化车端 EEE2 接收通知解密解析器，全兼容 0xAA 0xAB/0xAA 0xAC 及纯载荷格式\n" +
        "3. 直进直出扫描解除 128 位单一过滤限制，彻底消除部分机型蓝牙驱动丢包隐患"
}
