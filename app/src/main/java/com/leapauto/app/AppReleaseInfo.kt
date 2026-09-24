package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 新增系统级锁屏常驻控制通知：点亮手机锁屏免解锁即显车况（车型、锁闭状态、剩余续航与电量）\n" +
        "2. 锁屏快捷盲按控车：提供车门锁切换、寻车鸣笛、空调启停与数据刷新 4 个快捷动作，操作即时反馈\n" +
        "3. 设置页面提供「锁屏常驻控制」微晶质感开关，与车况刷新、控车完成及登出全局联动"
}
