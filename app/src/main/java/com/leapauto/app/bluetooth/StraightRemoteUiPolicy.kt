package com.leapauto.app.bluetooth

/**
 * 直进直出抽屉面板的纯状态判定策略。
 *
 * 安全红线：就绪判定只允许使用直进直出座舱通道自身的 [straightCanMove]，
 * 严禁复用数字车钥匙解锁通道的 [BleConnectionState.canControl]，
 * 否则会在座舱通道从未建连时给出"假就绪"，导致长按方向键零反馈。
 */
object StraightRemoteUiPolicy {

    /** 抽屉方向键是否允许响应长按。 */
    fun canControl(straightCanMove: Boolean): Boolean = straightCanMove

    /** 抽屉顶部状态文案。 */
    fun statusMessage(
        isMoving: Boolean,
        isMovingForward: Boolean,
        statusText: String,
        straightCanMove: Boolean,
        bluetoothPhase: BleConnectionPhase
    ): String = when {
        isMoving -> if (isMovingForward) "正在向前直进中..." else "正在向后倒车中..."
        statusText.isNotBlank() && statusText != "未连接" -> statusText
        straightCanMove -> "座舱就绪，长按方向键即可挪车"
        bluetoothPhase in setOf(
            BleConnectionPhase.CONNECTING,
            BleConnectionPhase.DISCOVERING,
            BleConnectionPhase.SUBSCRIBING,
            BleConnectionPhase.AUTHENTICATING
        ) -> "正在搜索连接车辆座舱..."
        else -> "等待车辆座舱就绪中..."
    }

    /**
     * 通道就绪态在按住期间失效时，必须立刻撤销本地"正在前进"显示，
     * 避免 UI 伪造移动状态。
     */
    fun shouldResetMoving(isMoving: Boolean, straightCanMove: Boolean): Boolean =
        isMoving && !straightCanMove
}
