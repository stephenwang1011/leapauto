package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 主页首卡状态行升级：更新时间后优雅点缀实况天气与气温，纯文本微晶融合\n" +
        "2. 4×2 与 2×2 桌面小组件重构：续航大字、单位、百分比与流光能量槽 1:1 对齐主界面\n" +
        "3. 小组件按键质感跃升：换装 35% 高透微晶晶霜与 0.5dp 细切线，通透轻盈不抢戏\n" +
        "4. 设置页面进出动效极简化：纯粹静谧的 180ms 极简微晶淡入淡出，告别复杂抖动\n" +
        "5. 安装包深度瘦身压缩：剔除重复动态库与多余元数据，包体暴降至 2.5MB"
}
