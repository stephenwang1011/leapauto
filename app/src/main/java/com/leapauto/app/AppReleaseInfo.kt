package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 增加同座椅加热与通风物理互斥逻辑，开启一项自动切断另一项\n2. 全面接入全车控车统一时序调度（机械动作拟真响应 + 极速阶梯早退轮询）\n3. 扩展全车控车 15 秒防回弹保护盾，彻底解决后备箱/车窗/车锁状态延迟与闪烁问题"
}
