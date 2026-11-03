package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 校准 3D 车模三维投影靶心与镜头视角，实现展台区域水平与垂直黄金对称居中"
}
