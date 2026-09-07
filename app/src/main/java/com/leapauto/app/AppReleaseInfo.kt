package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 空调控制大升级：新增内外循环手动分段切换（内循环/外循环）\n" +
        "2. 新增出风方向手动调节（全车环绕出风/前风挡除雾），双温区设定温展示\n" +
        "3. 新增座舱舒适状态面板：主副驾座椅加热/通风档位与方向盘加热状态完整遥测"
}
