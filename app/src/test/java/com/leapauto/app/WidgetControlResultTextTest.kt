package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetControlResultTextTest {

    @Test
    fun `sensitive widget commands use explicit result text`() {
        assertEquals("解锁指令已发送", WidgetControlResultText.accepted("unlock"))
        assertEquals("后备箱开启指令已发送", WidgetControlResultText.accepted("trunkOpen"))
        assertEquals("后备箱关闭指令已发送", WidgetControlResultText.accepted("trunkClose"))
        assertEquals("哨兵开启指令已发送", WidgetControlResultText.accepted("sentryOn"))
        assertEquals("哨兵关闭指令已发送", WidgetControlResultText.accepted("sentryOff"))
        assertEquals("解锁成功", WidgetControlResultText.completed("unlock"))
        assertEquals("后备箱已打开", WidgetControlResultText.completed("trunkOpen"))
        assertEquals("后备箱已关闭", WidgetControlResultText.completed("trunkClose"))
        assertEquals("哨兵模式已开启", WidgetControlResultText.completed("sentryOn"))
        assertEquals("哨兵模式已关闭", WidgetControlResultText.completed("sentryOff"))
    }

    @Test
    fun `other commands preserve a generic completion result`() {
        assertEquals("控车指令已发送", WidgetControlResultText.accepted("lock"))
        assertEquals("控车完成（车辆已响应）", WidgetControlResultText.completed("lock", "车辆已响应"))
        assertEquals("控车完成", WidgetControlResultText.completed("lock"))
    }

    @Test
    fun trunkAwaitingDoesNotClaimVehicleCompletion() {
        assertEquals(
            "后备箱关闭指令已发送，等待车况确认",
            WidgetControlResultText.awaiting("trunkClose")
        )
        assertEquals(
            "哨兵开启指令已发送，等待车况确认",
            WidgetControlResultText.awaiting("sentryOn")
        )
        assertEquals(
            "哨兵关闭指令已发送，等待车况确认",
            WidgetControlResultText.awaiting("sentryOff")
        )
    }
}
