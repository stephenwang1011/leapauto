package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 优化页面转场动效：设置与子页面进出采用原生视差微动，丝滑告别生硬突兀\n" +
        "2. 4×2 与 2×2 小组件按键质感跃升：全面换装 35% 高透微晶晶霜与极细切边，通透轻盈不抢戏\n" +
        "3. 小组件续航排版 1:1 对齐主界面：白大字 + 灰单位 + 绿百分比，增加微晶流光能量槽\n" +
        "4. 深度瘦身压缩安装包：剔除历史重复动态库与多余元数据，包体暴降至 2.5MB\n" +
        "5. 3D 车模主页常驻保活与防穿透保护，返回主页秒开秒回零重载"
}
