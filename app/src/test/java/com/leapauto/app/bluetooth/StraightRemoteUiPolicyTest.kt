package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StraightRemoteUiPolicyTest {

    @Test
    fun `control is allowed only by the straight cabin channel`() {
        assertTrue(StraightRemoteUiPolicy.canControl(straightCanMove = true))
        assertFalse(StraightRemoteUiPolicy.canControl(straightCanMove = false))
    }

    @Test
    fun `moving state overrides every other status`() {
        assertEquals(
            "正在向前直进中...",
            StraightRemoteUiPolicy.statusMessage(
                isMoving = true,
                isMovingForward = true,
                statusText = "座舱通道未就绪",
                straightCanMove = false,
                bluetoothPhase = BleConnectionPhase.IDLE
            )
        )
        assertEquals(
            "正在向后倒车中...",
            StraightRemoteUiPolicy.statusMessage(
                isMoving = true,
                isMovingForward = false,
                statusText = "",
                straightCanMove = true,
                bluetoothPhase = BleConnectionPhase.READY
            )
        )
    }

    @Test
    fun `controller status text wins over the fallback ready copy`() {
        assertEquals(
            "座舱通道未就绪，请等待连接完成后再按住方向键",
            StraightRemoteUiPolicy.statusMessage(
                isMoving = false,
                isMovingForward = false,
                statusText = "座舱通道未就绪，请等待连接完成后再按住方向键",
                straightCanMove = false,
                bluetoothPhase = BleConnectionPhase.IDLE
            )
        )
        assertEquals(
            "搜索座舱超时，请确认车辆已进入直进直出就绪状态",
            StraightRemoteUiPolicy.statusMessage(
                isMoving = false,
                isMovingForward = false,
                statusText = "搜索座舱超时，请确认车辆已进入直进直出就绪状态",
                straightCanMove = false,
                bluetoothPhase = BleConnectionPhase.IDLE
            )
        )
    }

    @Test
    fun `ready copy is used only when the straight channel is ready`() {
        assertEquals(
            "座舱就绪，长按方向键即可挪车",
            StraightRemoteUiPolicy.statusMessage(
                isMoving = false,
                isMovingForward = false,
                statusText = "未连接",
                straightCanMove = true,
                bluetoothPhase = BleConnectionPhase.IDLE
            )
        )
        assertEquals(
            "等待车辆座舱就绪中...",
            StraightRemoteUiPolicy.statusMessage(
                isMoving = false,
                isMovingForward = false,
                statusText = "未连接",
                straightCanMove = false,
                bluetoothPhase = BleConnectionPhase.IDLE
            )
        )
    }

    @Test
    fun `searching copy is used while the straight channel is still connecting`() {
        assertEquals(
            "正在搜索连接车辆座舱...",
            StraightRemoteUiPolicy.statusMessage(
                isMoving = false,
                isMovingForward = false,
                statusText = "",
                straightCanMove = false,
                bluetoothPhase = BleConnectionPhase.CONNECTING
            )
        )
        assertEquals(
            "正在搜索连接车辆座舱...",
            StraightRemoteUiPolicy.statusMessage(
                isMoving = false,
                isMovingForward = false,
                statusText = "",
                straightCanMove = false,
                bluetoothPhase = BleConnectionPhase.AUTHENTICATING
            )
        )
    }

    @Test
    fun `moving display is reset when readiness drops`() {
        assertTrue(StraightRemoteUiPolicy.shouldResetMoving(isMoving = true, straightCanMove = false))
        assertFalse(StraightRemoteUiPolicy.shouldResetMoving(isMoving = true, straightCanMove = true))
        assertFalse(StraightRemoteUiPolicy.shouldResetMoving(isMoving = false, straightCanMove = false))
    }
}
