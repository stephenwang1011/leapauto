package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 换装方案2【轻奢暖钛羊绒灰】背景：车规级温润暖灰质感，扎实耐看，久视不累\n" +
        "2. 排版架构零变动，100%保持全部现有组件位置、尺寸、对齐与交互\n" +
        "3. 注入暖钛微渐变光影场，磨砂透光玻璃与白色车模立体呈现"
}
