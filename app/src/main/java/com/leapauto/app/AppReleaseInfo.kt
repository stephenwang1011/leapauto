package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 实装黄金透视参数：相机焦距调至 4.5，车身体积放大 1.6 倍达 75% 占宽，细节纤毫毕现\n" +
        "2. 镜头仰俯角精调至 -0.15 并上移 16dp，车身优雅居中悬浮，消除卡片空隙\n" +
        "3. 官方高清 3D 车模手势交互，支持 360 度前后左右无死角自由旋转与视角把玩"
}
