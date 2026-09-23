package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlFeedbackFormatterTest {

    @Test
    fun `formats in progress text concisely for core vehicle commands`() {
        assertEquals("解锁中...", ControlFeedbackFormatter.inProgress("unlock", "解锁"))
        assertEquals("上锁中...", ControlFeedbackFormatter.inProgress("lock", "上锁"))
        assertEquals("开后备箱中...", ControlFeedbackFormatter.inProgress("trunkOpen", "开后备箱"))
        assertEquals("关后备箱中...", ControlFeedbackFormatter.inProgress("trunkClose", "关后备箱"))
        assertEquals("车窗微开中...", ControlFeedbackFormatter.inProgress("windowVent", "车窗微开"))
        assertEquals("开窗中...", ControlFeedbackFormatter.inProgress("windowOpen", "开窗"))
        assertEquals("关窗中...", ControlFeedbackFormatter.inProgress("windowClose", "关窗"))
        assertEquals("开前备箱中...", ControlFeedbackFormatter.inProgress("frunkOpen", "开前备箱"))
        assertEquals("关前备箱中...", ControlFeedbackFormatter.inProgress("frunkClose", "关前备箱"))
    }

    @Test
    fun `formats in progress text concisely for climate and comfort controls`() {
        assertEquals("正在开启制冷...", ControlFeedbackFormatter.inProgress("acOn", "开空调"))
        assertEquals("正在关闭空调...", ControlFeedbackFormatter.inProgress("acOff", "关空调"))
        assertEquals("正在开启极速降温...", ControlFeedbackFormatter.inProgress("quickCool", "极速降温"))
        assertEquals("正在开启制热...", ControlFeedbackFormatter.inProgress("quickHeat", "一键制热"))
        assertEquals("正在开启前挡除霜...", ControlFeedbackFormatter.inProgress("defrost", "前挡除霜"))
        assertEquals("正在开启主驾座椅加热...", ControlFeedbackFormatter.inProgress("driverSeatHeating_2", "主驾座椅加热"))
        assertEquals("正在关闭主驾座椅加热...", ControlFeedbackFormatter.inProgress("driverSeatHeating_0", "主驾加热关闭"))
        assertEquals("正在开启主驾座椅通风...", ControlFeedbackFormatter.inProgress("driverSeatVentilation_2", "主驾座椅通风"))
        assertEquals("正在关闭主驾座椅通风...", ControlFeedbackFormatter.inProgress("driverSeatVentilation_0", "主驾通风关闭"))
        assertEquals("正在开启方向盘加热...", ControlFeedbackFormatter.inProgress("steeringWheelHeating_2", "方向盘加热"))
    }

    @Test
    fun `formats in progress text concisely for fridge controls`() {
        assertEquals("开启冰箱中...", ControlFeedbackFormatter.inProgress("fridgeOn", "开启冰箱"))
        assertEquals("关闭冰箱中...", ControlFeedbackFormatter.inProgress("fridgeOff", "关闭冰箱"))
        assertEquals("调节冰箱中...", ControlFeedbackFormatter.inProgress("fridge", "冰箱设置"))
    }

    @Test
    fun `formats success feedback text with concise outcome for core commands`() {
        assertEquals("解锁成功", ControlFeedbackFormatter.success("unlock", "解锁"))
        assertEquals("上锁成功", ControlFeedbackFormatter.success("lock", "上锁"))
        assertEquals("后备箱已开启", ControlFeedbackFormatter.success("trunkOpen", "开后备箱"))
        assertEquals("后备箱已关闭", ControlFeedbackFormatter.success("trunkClose", "关后备箱"))
        assertEquals("车窗已微开", ControlFeedbackFormatter.success("windowVent", "车窗微开"))
        assertEquals("车窗已开启", ControlFeedbackFormatter.success("windowOpen", "开窗"))
        assertEquals("车窗已关闭", ControlFeedbackFormatter.success("windowClose", "关窗"))
        assertEquals("前备箱已开启", ControlFeedbackFormatter.success("frunkOpen", "开前备箱"))
        assertEquals("前备箱已关闭", ControlFeedbackFormatter.success("frunkClose", "关前备箱"))
    }

    @Test
    fun `formats success feedback text with concise outcome for climate and fridge controls`() {
        assertEquals("制冷已开启", ControlFeedbackFormatter.success("acOn", "开空调"))
        assertEquals("空调已关闭", ControlFeedbackFormatter.success("acOff", "关空调"))
        assertEquals("极速降温已开启", ControlFeedbackFormatter.success("quickCool", "极速降温"))
        assertEquals("制热已开启", ControlFeedbackFormatter.success("quickHeat", "一键制热"))
        assertEquals("前挡除霜已开启", ControlFeedbackFormatter.success("defrost", "前挡除霜"))
        assertEquals("主驾座椅加热已打开", ControlFeedbackFormatter.success("driverSeatHeating_2", "主驾座椅加热"))
        assertEquals("主驾座椅加热已关闭", ControlFeedbackFormatter.success("driverSeatHeating_0", "主驾加热关闭"))
        assertEquals("主驾座椅通风已打开", ControlFeedbackFormatter.success("driverSeatVentilation_2", "主驾座椅通风"))
        assertEquals("主驾座椅通风已关闭", ControlFeedbackFormatter.success("driverSeatVentilation_0", "主驾通风关闭"))
        assertEquals("温度已调至 25°C", ControlFeedbackFormatter.success(null, "温度已调至 25°C"))
        assertEquals("风量已调至 4挡", ControlFeedbackFormatter.success(null, "风量已调至 4挡"))
        assertEquals("已切换至内循环", ControlFeedbackFormatter.success(null, "已切换至内循环"))
        assertEquals("冰箱已开启", ControlFeedbackFormatter.success("fridgeOn", "开启冰箱"))
        assertEquals("车载冰箱已开启 · 冷藏 4°C", ControlFeedbackFormatter.success("fridgeOn", "开启车载冰箱（制冷 4°C 标准）"))
        assertEquals("车载冰箱已开启 · 保温 50°C", ControlFeedbackFormatter.success("fridgeOn", "开启车载冰箱（制热 50°C 标准）"))
        assertEquals("冰箱已关闭", ControlFeedbackFormatter.success("fridgeOff", "关闭冰箱"))
        assertEquals("车载冰箱已关闭", ControlFeedbackFormatter.success("fridgeOff", "关闭车载冰箱"))
        assertEquals("冰箱设置成功", ControlFeedbackFormatter.success("fridge", "冰箱设置"))
    }

    @Test
    fun `handles unknown or empty commands gracefully`() {
        assertEquals("自定义操作中...", ControlFeedbackFormatter.inProgress("custom", "自定义操作"))
        assertEquals("自定义操作成功", ControlFeedbackFormatter.success("custom", "自定义操作"))
        assertEquals("处理中...", ControlFeedbackFormatter.inProgress(null, null))
        assertEquals("操作成功", ControlFeedbackFormatter.success(null, null))
    }
}
