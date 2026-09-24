package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 全新升级微晶自适应应用图标（Adaptive Icon）：彻底消灭桌面强制白框，极光能量流光通透呈现\n" +
        "2. 新增下拉控制中心快捷磁贴：支持寻车（锁屏免解锁即控）、车锁与空调状态高亮感知与一键添加\n" +
        "3. 4×2 与 2×2 小组件按键质感跃升：换装 35% 高透微晶晶霜与 0.5dp 细切线，通透轻盈不抢戏\n" +
        "4. 桌面小组件续航排版 1:1 对齐主界面：白大字 + 灰单位 + 绿百分比，配微晶流光能量胶囊槽\n" +
        "5. 安装包深度瘦身压缩：剔除重复动态库与多余无用资源，包体暴降至 2.5MB"
}
