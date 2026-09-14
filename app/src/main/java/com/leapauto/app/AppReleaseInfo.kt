package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 3D 车模视觉放大 1.6 倍并居中悬浮展台，彻底告别微缩小车感\n" +
        "2. 深度优化三维相机物理焦距与倾角，官方高清车模饱满大气\n" +
        "3. 增加 Release 包 R8 WebGL 防混淆保护，全版本稳定支持 360 度手势旋转"
}
