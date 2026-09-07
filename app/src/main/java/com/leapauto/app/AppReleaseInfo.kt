package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 新增【健康充电上限与电池健康管理】底部毛玻璃抽屉：点击主页续航电量或充电卡片即刻弹出\n" +
        "2. 支持50%~100%滑块与80%/90%/100%常用档位快捷点选，结合健康充电开关一键下发到车辆\n" +
        "3. 主界面排版架构零变动，新增实时动力电池工况透视与官方三元锂/磷酸铁锂养护指南"
}
