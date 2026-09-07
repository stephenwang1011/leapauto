package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复桌面插件雪花旋转畸变超限问题：固定24dp方形视口与中心旋转，彻底消除拉伸跑偏\n" +
        "2. 空调雪花全场景三色旋转联动：制冷显示蓝色并旋转，制热显示橙黄色并旋转，通风显示绿色并旋转\n" +
        "3. 关闭或未开启状态下雪花恢复端正静止，默认黑色展示"
}
