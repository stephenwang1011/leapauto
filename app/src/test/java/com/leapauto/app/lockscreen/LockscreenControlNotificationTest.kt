package com.leapauto.app.lockscreen

import com.leapauto.app.R
import com.leapauto.app.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LockscreenControlNotificationTest {

    private fun projectDirectory(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile ?: return@generateSequence null }
            .first { File(it, "app").isDirectory }
    }

    @Test
    fun `manifest declares LockscreenActionReceiver with proper intent filter and unexported`() {
        val projectDir = projectDirectory()
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml").readText()

        assertTrue("Manifest must declare LockscreenActionReceiver", manifest.contains("android:name=\".lockscreen.LockscreenActionReceiver\""))
        assertTrue("Receiver must not be exported for security", manifest.contains("android:exported=\"false\""))
        assertTrue("Receiver must filter action", manifest.contains("android:name=\"com.leapauto.app.action.LOCKSCREEN_CONTROL\""))
    }

    @Test
    fun `settings screen provides lockscreen control card with frosted glass style and switch`() {
        val projectDir = projectDirectory()
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("LockscreenControlCard"))
        assertTrue(screenSource.contains("锁屏常驻控制"))
        assertTrue(screenSource.contains("在手机锁屏常驻控制条与实时续航，免解锁一键开关车门锁、寻车鸣笛与空调"))
        assertTrue(screenSource.contains("frostedGlassCard"))
        assertTrue(screenSource.contains("glassCardBorder"))
    }

    @Test
    fun `session store maintains lockscreen control enabled flag and constants`() {
        assertEquals("lockscreen_control_enabled", com.leapauto.app.SessionStore.LOCKSCREEN_CONTROL_ENABLED)
    }

    @Test
    fun `notification manager constants match specification`() {
        assertEquals("lockscreen_control", LockscreenControlNotificationManager.CHANNEL_ID)
        assertEquals(2001, LockscreenControlNotificationManager.NOTIFICATION_ID)
        assertEquals("com.leapauto.app.action.LOCKSCREEN_CONTROL", LockscreenControlNotificationManager.ACTION_LOCKSCREEN_CONTROL)
        assertEquals("command", LockscreenControlNotificationManager.EXTRA_COMMAND)
    }

    @Test
    fun `formatTitle formats vehicle name and lock status accurately`() {
        assertEquals("C16 · 已上锁", LockscreenNotificationFormatter.formatTitle("C16", true))
        assertEquals("C10 · 未上锁", LockscreenNotificationFormatter.formatTitle("C10", false))
        assertEquals("零跑汽车 · 已连接", LockscreenNotificationFormatter.formatTitle(null, null))
        assertEquals("零跑汽车 · 已上锁", LockscreenNotificationFormatter.formatTitle("", true))
    }

    @Test
    fun `formatBody handles null snapshot, custom statusText, and electric vs range extender`() {
        assertEquals("等待数据同步 · 锁屏控车已就绪", LockscreenNotificationFormatter.formatBody(null))
        assertEquals("正在落锁...", LockscreenNotificationFormatter.formatBody(null, "正在落锁..."))

        val evSnapshot = SessionStore.WidgetSnapshot(
            vin = "VIN123",
            carType = "C16",
            range = "373",
            soc = 72,
            updated = "今天 16:09",
            powerType = SessionStore.VehiclePowerType.PURE_ELECTRIC,
            statusLabel = "车窗未关闭",
            locked = true
        )
        assertEquals("车窗未关闭 · 续航 373km (72%) · 今天 16:09", LockscreenNotificationFormatter.formatBody(evSnapshot))

        val erevSnapshot = SessionStore.WidgetSnapshot(
            vin = "VIN456",
            carType = "C11增程",
            range = "1020",
            soc = 85,
            updated = "今天 17:30",
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER,
            locked = false
        )
        assertEquals("综合续航 1020km · 电量 85% · 今天 17:30", LockscreenNotificationFormatter.formatBody(erevSnapshot))

        // When statusText is provided during command flight, it prefixes range
        assertEquals("正在落锁... · 续航 373km (72%)", LockscreenNotificationFormatter.formatBody(evSnapshot, "正在落锁..."))
    }

    @Test
    fun `resolve actions map command, label, and icons dynamically`() {
        // Locked vehicle -> action is unlock
        val unlockAction = LockscreenNotificationFormatter.resolveLockAction(locked = true)
        assertEquals("unlock", unlockAction.command)
        assertEquals("解锁", unlockAction.label)
        assertEquals(R.drawable.ic_phosphor_lock_open, unlockAction.iconRes)

        // Unlocked vehicle -> action is lock
        val lockAction = LockscreenNotificationFormatter.resolveLockAction(locked = false)
        assertEquals("lock", lockAction.command)
        assertEquals("落锁", lockAction.label)
        assertEquals(R.drawable.ic_phosphor_lock, lockAction.iconRes)

        // AC active -> action is acOff
        val acOffAction = LockscreenNotificationFormatter.resolveAcAction(acEnabled = true)
        assertEquals("acOff", acOffAction.command)
        assertEquals("关空调", acOffAction.label)

        // AC off -> action is acOn
        val acOnAction = LockscreenNotificationFormatter.resolveAcAction(acEnabled = false)
        assertEquals("acOn", acOnAction.command)
        assertEquals("开空调", acOnAction.label)

        // Horn & Refresh
        val horn = LockscreenNotificationFormatter.resolveHornAction()
        assertEquals("horn", horn.command)
        assertEquals("寻车", horn.label)

        val refresh = LockscreenNotificationFormatter.resolveRefreshAction()
        assertEquals("refresh", refresh.command)
        assertEquals("刷新", refresh.label)
    }
}
