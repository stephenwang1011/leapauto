package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 3D 车模主页常驻保活架构：进出设置/空调/位置页面秒开秒回，彻底告别重复加载\n" +
        "2. 修复主页顶部安全间距：适配各类挖孔与全面屏，杜绝状态栏与车辆昵称/设置按钮重叠\n" +
        "3. 优化设置与子页面背景渲染：顶部标题栏与下半部分背景色 100% 连贯一体\n" +
        "4. 3D 车模定妆照截帧时序与空图拦截：全车加载完毕后截帧，坏图自动降级官方 2D 图\n" +
        "5. 驻车详情新增实况气象与爱车洗车建议一体化微晶卡片"
}
