package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 彻底隔离直进直出专属座舱 BLE 链路，完全对齐官方 xp/a91 状态机架构\n" +
        "2. 实现专属 EEED 扫描、握手与 AA AE 01 01 0A 5 字节座舱认证闭环\n" +
        "3. 严格使用单字节动作控制帧（1=前进/2=后退/3=停止），0% 误触车门开锁\n" +
        "4. 实时监听车端 0;2;0;0;0; 就绪状态，只有车辆真正就绪才允许按压挪车"
}
