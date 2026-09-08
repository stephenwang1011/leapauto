package com.leapauto.app

/**
 * 零跑车辆预约充电计划周期（cycles）编解码工具类。
 *
 * 零跑车联网及车载 T-Box 底层采用 7 位 0/1 掩码字符串规范（如 "1,1,1,1,1,1,1"）：
 * 索引 0 ~ 6 分别代表 周一、周二、周三、周四、周五、周六、周日。
 * 1 代表选中开启，0 代表未选中。
 */
object ChargePlanCyclesHelper {

    /**
     * 将车端返回的 7 位 0/1 掩码（如 "1,1,1,1,1,0,0"）或星期数字列表（如 "1,2,3,4,5"）
     * 解析为周一至周日选中的天数集合（1=周一, 2=周二 ... 7=周日）。
     */
    fun parseToDaySet(cycles: String?): Set<Int> {
        if (cycles.isNullOrBlank()) return (1..7).toSet()
        val parts = cycles.split(",").map { it.trim() }
        // 1. 7 位 0/1 掩码格式：如 "1,1,1,1,1,0,0" 或 "1,0,1,0,1,0,1"
        if (parts.size == 7 && parts.all { it == "0" || it == "1" }) {
            val days = parts.mapIndexedNotNull { index, flag ->
                if (flag == "1") index + 1 else null
            }.toSet()
            return days.ifEmpty { (1..7).toSet() }
        }
        // 2. 星期数字列表格式：如 "1,2,3,4,5"
        val dayIndices = parts.mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.toSet()
        return dayIndices.ifEmpty { (1..7).toSet() }
    }

    /**
     * 将选中的星期集合（1..7）转换为零跑车端 T-Box 识别的原生 7 位 0/1 掩码。
     * 例如工作日 -> "1,1,1,1,1,0,0"，全周 -> "1,1,1,1,1,1,1"，周末 -> "0,0,0,0,0,1,1"。
     */
    fun toVehicleMask(daySet: Set<Int>): String {
        val days = daySet.ifEmpty { (1..7).toSet() }
        return (1..7).joinToString(",") { if (days.contains(it)) "1" else "0" }
    }

    /**
     * 将任意格式的 cycles 字符串归一化为车端原生 7 位 0/1 掩码。
     */
    fun toVehicleMask(cycles: String?): String {
        return toVehicleMask(parseToDaySet(cycles))
    }

    /**
     * 友好中文描述（如“每天 (全周循环)”、“工作日 (周一至周五)”、“周末 (周六、周日)”）。
     */
    fun formatSummary(cycles: String?): String = formatSummary(parseToDaySet(cycles))

    fun formatSummary(days: Set<Int>): String {
        val effective = days.ifEmpty { (1..7).toSet() }
        return when {
            effective == (1..7).toSet() -> "每天 (全周循环)"
            effective == setOf(1, 2, 3, 4, 5) -> "工作日 (周一至周五)"
            effective == setOf(6, 7) -> "周末 (周六、周日)"
            effective.isEmpty() -> "未选择"
            else -> {
                val names = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
                effective.sorted().mapNotNull { names[it]?.let { name -> "周$name" } }.joinToString("、")
            }
        }
    }
}
