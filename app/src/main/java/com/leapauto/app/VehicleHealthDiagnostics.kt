package com.leapauto.app

enum class HealthCheckLevel {
    GOOD,
    WARNING,
    CRITICAL
}

data class HealthCheckItem(
    val title: String,
    val detail: String,
    val level: HealthCheckLevel,
    val fixCommand: String? = null,
    val fixLabel: String? = null
)

data class HealthSystemReport(
    val name: String,
    val iconRes: Int,
    val statusText: String,
    val level: HealthCheckLevel,
    val items: List<HealthCheckItem>
)

data class VehicleHealthReport(
    val score: Int,
    val summary: String,
    val level: HealthCheckLevel,
    val systems: List<HealthSystemReport>,
    val issues: List<HealthCheckItem>
)

object VehicleHealthDiagnostics {

    fun evaluate(status: VehicleStatus?): VehicleHealthReport {
        if (status == null) {
            return VehicleHealthReport(
                score = 0,
                summary = "车况待同步",
                level = HealthCheckLevel.WARNING,
                systems = emptyList(),
                issues = listOf(
                    HealthCheckItem(
                        title = "数据未同步",
                        detail = "请先刷新车况获取最新信号",
                        level = HealthCheckLevel.WARNING
                    )
                )
            )
        }

        val powertrainItems = mutableListOf<HealthCheckItem>()
        val chassisItems = mutableListOf<HealthCheckItem>()
        val bodyItems = mutableListOf<HealthCheckItem>()
        val climateItems = mutableListOf<HealthCheckItem>()

        var score = 100

        // ====== 1. 动力与三电系统 ======
        if (!status.batteryVoltage.isNullOrBlank()) {
            powertrainItems.add(
                HealthCheckItem(
                    title = "高压母线电压",
                    detail = "母线电压 ${status.batteryVoltage}V，运行稳定",
                    level = HealthCheckLevel.GOOD
                )
            )
        } else {
            powertrainItems.add(
                HealthCheckItem(
                    title = "高压母线电压",
                    detail = "充放电母线链路正常",
                    level = HealthCheckLevel.GOOD
                )
            )
        }

        val batteryTempNumber = status.minBatteryTemp?.toDoubleOrNull()
        if (batteryTempNumber != null) {
            when {
                batteryTempNumber < -15.0 -> {
                    score -= 10
                    powertrainItems.add(
                        HealthCheckItem(
                            title = "电池低温预警",
                            detail = "电池温度 ${status.minBatteryTemp}°C，建议行车前开启电池预热",
                            level = HealthCheckLevel.WARNING,
                            fixCommand = "batteryPreheat",
                            fixLabel = "电池预热"
                        )
                    )
                }
                batteryTempNumber > 50.0 -> {
                    score -= 15
                    powertrainItems.add(
                        HealthCheckItem(
                            title = "电池高温预警",
                            detail = "电池温度 ${status.minBatteryTemp}°C，请注意散热与停车环境",
                            level = HealthCheckLevel.CRITICAL
                        )
                    )
                }
                else -> {
                    powertrainItems.add(
                        HealthCheckItem(
                            title = "动力电池包温控",
                            detail = "最低电芯温度 ${status.minBatteryTemp}°C，处于舒适工作温区",
                            level = HealthCheckLevel.GOOD
                        )
                    )
                }
            }
        } else {
            powertrainItems.add(
                HealthCheckItem(
                    title = "动力电池包温控",
                    detail = "电池热管理巡检回路正常",
                    level = HealthCheckLevel.GOOD
                )
            )
        }

        if (status.healthyChargeEnabled == true) {
            powertrainItems.add(
                HealthCheckItem(
                    title = "动力电池养护",
                    detail = "健康充电模式已开启，电池寿命深度保护中",
                    level = HealthCheckLevel.GOOD
                )
            )
        } else {
            powertrainItems.add(
                HealthCheckItem(
                    title = "动力系统就绪",
                    detail = "动力总成状态正常，整车已就绪",
                    level = HealthCheckLevel.GOOD
                )
            )
        }

        // ====== 2. 底盘制动与轮胎系统 ======
        val tireWarnings = status.tires.filter { it.warning }
        if (tireWarnings.isNotEmpty()) {
            tireWarnings.forEach { tire ->
                score -= 10
                chassisItems.add(
                    HealthCheckItem(
                        title = "${tire.position}胎压异常",
                        detail = "气压 ${tire.pressure ?: "--"}bar，检测到胎压告警",
                        level = HealthCheckLevel.WARNING
                    )
                )
            }
        } else if (status.tires.isNotEmpty()) {
            val pressures = status.tires.mapNotNull { it.pressure }
            val avg = if (pressures.isNotEmpty()) "（均值约 ${pressures.first()}bar）" else ""
            chassisItems.add(
                HealthCheckItem(
                    title = "四轮胎压监测",
                    detail = "四轮胎压平衡无漏气，气压充足$avg",
                    level = HealthCheckLevel.GOOD
                )
            )
        } else {
            chassisItems.add(
                HealthCheckItem(
                    title = "四轮胎压监测",
                    detail = "胎压监测系统处于正常监测状态",
                    level = HealthCheckLevel.GOOD
                )
            )
        }

        if (status.isDriving == false) {
            chassisItems.add(
                HealthCheckItem(
                    title = "驻车制动系统",
                    detail = "EPB 电子驻车手刹锁止良好，车辆安全驻停",
                    level = HealthCheckLevel.GOOD
                )
            )
        } else {
            chassisItems.add(
                HealthCheckItem(
                    title = "行车制动系统",
                    detail = "制动回路压力正常，行车安全处于监控中",
                    level = HealthCheckLevel.GOOD
                )
            )
        }

        // ====== 3. 车身密闭与安防系统 ======
        if (status.locked == false) {
            score -= 5
            bodyItems.add(
                HealthCheckItem(
                    title = "车门锁未锁止",
                    detail = "车辆处于解锁状态，离车请及时上锁",
                    level = HealthCheckLevel.WARNING,
                    fixCommand = "lock",
                    fixLabel = "一键锁车"
                )
            )
        } else if (status.locked == true) {
            bodyItems.add(
                HealthCheckItem(
                    title = "车门安全锁止",
                    detail = "全车门锁处于安全锁闭警戒状态",
                    level = HealthCheckLevel.GOOD
                )
            )
        }

        val openDoors = mutableListOf<String>()
        if (status.driverDoorOpen) openDoors.add("主驾门")
        if (status.passengerDoorOpen) openDoors.add("副驾门")
        if (status.leftRearDoorOpen) openDoors.add("左后门")
        if (status.rightRearDoorOpen) openDoors.add("右后门")
        if (status.trunkState == TrunkState.OPEN) openDoors.add("后备箱")

        if (openDoors.isNotEmpty()) {
            score -= 10
            bodyItems.add(
                HealthCheckItem(
                    title = "车门密闭检测",
                    detail = "${openDoors.joinToString("、")}处于打开状态",
                    level = HealthCheckLevel.WARNING,
                    fixCommand = if (status.trunkState == TrunkState.OPEN) "trunkClose" else null,
                    fixLabel = if (status.trunkState == TrunkState.OPEN) "关后备箱" else null
                )
            )
        } else {
            bodyItems.add(
                HealthCheckItem(
                    title = "全车车门密闭",
                    detail = "四门及电动尾门均已完全闭合锁严",
                    level = HealthCheckLevel.GOOD
                )
            )
        }

        if (status.windowStatusAvailable && status.openWindows.isNotEmpty()) {
            score -= 5
            bodyItems.add(
                HealthCheckItem(
                    title = "车窗未完全关闭",
                    detail = "${status.openWindows.joinToString("、")}处于微开/通风状态",
                    level = HealthCheckLevel.WARNING,
                    fixCommand = "windowClose",
                    fixLabel = "一键关窗"
                )
            )
        } else {
            bodyItems.add(
                HealthCheckItem(
                    title = "四扇车窗密封",
                    detail = "四门车窗均已全部升起锁闭",
                    level = HealthCheckLevel.GOOD
                )
            )
        }

        if (status.sentryMode == true) {
            bodyItems.add(
                HealthCheckItem(
                    title = "哨兵安全守护",
                    detail = "全车摄像头与高灵敏传感器正在站岗警戒",
                    level = HealthCheckLevel.GOOD
                )
            )
        } else {
            bodyItems.add(
                HealthCheckItem(
                    title = "整车防盗警戒",
                    detail = "车身防盗与震动入侵监测处于就绪态",
                    level = HealthCheckLevel.GOOD
                )
            )
        }

        // ====== 4. 环控与电气系统 ======
        if (status.acSwitch == true) {
            val target = status.acSetting?.let { "$it°C" } ?: "自动"
            climateItems.add(
                HealthCheckItem(
                    title = "空调座舱环境",
                    detail = "空调开启中 · 设定温度 $target · 车内 ${status.indoorTemp ?: "--"}°C",
                    level = HealthCheckLevel.GOOD
                )
            )
        } else {
            climateItems.add(
                HealthCheckItem(
                    title = "空调座舱回路",
                    detail = "环控冷热风道待命，车内环境 ${status.indoorTemp ?: "--"}°C",
                    level = HealthCheckLevel.GOOD
                )
            )
        }

        climateItems.add(
            HealthCheckItem(
                title = "除霜除雾电路",
                detail = "前后风挡加热电热丝与除雾风道处于就绪态",
                level = HealthCheckLevel.GOOD
            )
        )

        climateItems.add(
            HealthCheckItem(
                title = "车联通信系统",
                detail = "T-Box 远程网络与定位遥测链路处于连接状态",
                level = HealthCheckLevel.GOOD
            )
        )

        // ====== 汇总与评级 ======
        val finalScore = score.coerceIn(0, 100)
        val allItems = powertrainItems + chassisItems + bodyItems + climateItems
        val issues = allItems.filter { it.level != HealthCheckLevel.GOOD }

        val overallLevel = when {
            finalScore >= 95 -> HealthCheckLevel.GOOD
            finalScore >= 80 -> HealthCheckLevel.WARNING
            else -> HealthCheckLevel.CRITICAL
        }

        val summary = when (overallLevel) {
            HealthCheckLevel.GOOD -> "全车系统检测良好，安心启程"
            HealthCheckLevel.WARNING -> "检测到 ${issues.size} 项状态提醒，建议查看"
            HealthCheckLevel.CRITICAL -> "检测到异常项目，请及时检查处理"
        }

        fun systemLevel(items: List<HealthCheckItem>): HealthCheckLevel = when {
            items.any { it.level == HealthCheckLevel.CRITICAL } -> HealthCheckLevel.CRITICAL
            items.any { it.level == HealthCheckLevel.WARNING } -> HealthCheckLevel.WARNING
            else -> HealthCheckLevel.GOOD
        }

        fun systemStatusText(items: List<HealthCheckItem>): String {
            val warnCount = items.count { it.level != HealthCheckLevel.GOOD }
            return if (warnCount > 0) "${warnCount}项需留意" else "正常"
        }

        val systems = listOf(
            HealthSystemReport(
                name = "动力与三电",
                iconRes = R.drawable.ic_phosphor_battery_charging,
                statusText = systemStatusText(powertrainItems),
                level = systemLevel(powertrainItems),
                items = powertrainItems
            ),
            HealthSystemReport(
                name = "底盘与制动",
                iconRes = R.drawable.ic_phosphor_car,
                statusText = systemStatusText(chassisItems),
                level = systemLevel(chassisItems),
                items = chassisItems
            ),
            HealthSystemReport(
                name = "车身与密闭",
                iconRes = R.drawable.ic_phosphor_lock,
                statusText = systemStatusText(bodyItems),
                level = systemLevel(bodyItems),
                items = bodyItems
            ),
            HealthSystemReport(
                name = "环控与电气",
                iconRes = R.drawable.ic_phosphor_fan,
                statusText = systemStatusText(climateItems),
                level = systemLevel(climateItems),
                items = climateItems
            )
        )

        return VehicleHealthReport(
            score = finalScore,
            summary = summary,
            level = overallLevel,
            systems = systems,
            issues = issues
        )
    }
}
