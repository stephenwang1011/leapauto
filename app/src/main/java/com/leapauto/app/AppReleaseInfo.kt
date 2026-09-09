package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 胎压卡片全新升级：引入极简俯视流线车模，四轮气压直观对齐并支持独立告警高亮\n" +
        "2. 修复充电中心当前电量固定显示为33%的问题，精准同步车辆真实电量\n" +
        "3. 优化首页状态小胶囊间距为4dp，进一步提升顶部视觉精致度\n" +
        "4. 安装包极致瘦身：精简资源与架构配置，体积大幅缩减至约2.4MB"
}
