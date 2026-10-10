package com.leapauto.app

/**
 * 集中管理整车各控车模块的拟真响应延迟、乐观状态保护期及阶梯静默轮询时序。
 * 支持机械运动类（车锁/后备箱/车窗/前备箱）与座舱舒适电子类（座椅/方向盘/后视镜/冰箱）分类定制。
 */
object VehicleControlConfirmationPolicy {

    /** 本地乐观状态防回弹保护时长：15 秒 */
    const val OPTIMISTIC_PROTECTION_WINDOW_MS = 15_000L

    /**
     * 各类控车指令的界面拟真物理响应延迟：
     * - 车门锁：800ms 模拟物理机械锁块与继电器吸合
     * - 后备箱：800ms 模拟电尾门锁扣解锁动作
     * - 车窗：500ms 模拟车窗电机启动升降
     * - 前备箱：600ms 模拟电控机盖锁弹开
     * - 座舱舒适类（座椅/方向盘/后视镜/冰箱）：0ms 纯电路通断，极速跟手
     */
    fun responseDelayMs(commandName: String): Long = when {
        commandName == "lock" || commandName == "unlock" -> 800L
        commandName == "trunkOpen" || commandName == "trunkClose" || commandName == "trunk" -> 800L
        commandName == "frunkOpen" || commandName == "frunk" -> 600L
        commandName.startsWith("window") -> 500L
        else -> 0L
    }

    /**
     * 各类控车指令的阶梯静默确认轮询时序检查点（毫秒）：
     * - 车门锁：1.2s ➔ 1.5s ➔ 2.0s ➔ 2.5s
     * - 后备箱（电尾门）：1.5s ➔ 3.0s ➔ 5.0s（电尾门运动耗时较长）
     * - 车窗（电机连续升降）：1.2s ➔ 2.0s ➔ 3.5s ➔ 5.0s
     * - 前备箱：1.2s ➔ 2.0s
     * - 座舱舒适/硬件（座椅/方向盘/后视镜/冰箱）：1.0s ➔ 1.8s ➔ 2.5s
     * - 其他指令：兜底 1.5s ➔ 2.5s
     */
    fun telemetryRefreshScheduleMs(commandName: String): List<Long> = when {
        commandName == "lock" || commandName == "unlock" -> listOf(1_200L, 1_500L, 2_000L, 2_500L)
        commandName == "trunkOpen" || commandName == "trunkClose" || commandName == "trunk" -> listOf(1_500L, 3_000L, 5_000L)
        commandName.startsWith("window") -> listOf(1_200L, 2_000L, 3_500L, 5_000L)
        commandName == "frunkOpen" || commandName == "frunk" -> listOf(1_200L, 2_000L)
        isComfortOrHardwareCommand(commandName) -> listOf(1_000L, 1_800L, 2_500L)
        else -> listOf(1_500L, 2_500L)
    }

    fun isComfortOrHardwareCommand(commandName: String): Boolean =
        commandName.startsWith("driverSeat") ||
        commandName.startsWith("passengerSeat") ||
        commandName.startsWith("leftRearSeat") ||
        commandName.startsWith("rightRearSeat") ||
        commandName.startsWith("steeringWheel") ||
        commandName.startsWith("rearviewMirror") ||
        commandName.startsWith("fridge")

    fun isMechanicalCommand(commandName: String): Boolean =
        commandName == "lock" || commandName == "unlock" ||
        commandName.startsWith("trunk") ||
        commandName.startsWith("frunk") ||
        commandName.startsWith("window")
}
