package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复直进直出控制报文编码，彻底解决点击前进误触发车辆解锁问题\n" +
        "2. 控制明文严格对齐官方单字节动作规范，杜绝与车门锁协议重叠冲突\n" +
        "3. 优化长按按压与松手刹停调度，消除 BLE 数据包堆叠并提升挪车响应度\n" +
        "4. 直进直出抽屉唤醒时自动探测挂载专属 EEED 座舱通道"
}
