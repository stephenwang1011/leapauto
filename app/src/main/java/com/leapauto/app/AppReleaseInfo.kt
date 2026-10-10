package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 3D车模全面支持前机盖开闭三维动画，支持电动按键控车与手动开启智能感应\n2. 门窗状态检测同步增加前机盖安全监控\n3. 实装同座椅加热与通风物理互斥逻辑"
}
