package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 实装官方 3D 车模双包协同机制（H5 运行时 + 3D 车体资产解压合并）\n" +
        "2. 补齐三维车模网格材质与门把手部件，消除 3D 引擎模型 404 加载失败\n" +
        "3. 官方高清 3D 车模手势交互，支持 360 度前后左右无死角自由旋转"
}
