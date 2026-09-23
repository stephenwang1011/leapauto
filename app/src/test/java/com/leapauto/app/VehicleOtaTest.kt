package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleOtaTest {

    @Test
    fun `parse up to date ota response`() {
        val json = JSONObject("""
            {
                "currentVersion": "Leapmotor OS 1.3.20",
                "hasNewVersion": false,
                "status": 0
            }
        """.trimIndent())
        val info = VehicleOtaInfo.fromJson(json)
        assertEquals("Leapmotor OS 1.3.20", info.currentVersion)
        assertFalse(info.hasNewVersion)
        assertEquals(OtaStatus.UP_TO_DATE, info.status)
        assertTrue(info.isUpToDate)
    }

    @Test
    fun `parse real official fota getCurrentVersion response`() {
        val json = JSONObject("""
            {
                "vin": "LFZ63AZ55SH023503",
                "versionNo": "3.06.40",
                "logContent": "本次OTA新增功能：\n1.城市领航辅助\n2.一键泊车",
                "updateTime": "2026.05.24"
            }
        """.trimIndent())
        val info = VehicleOtaInfo.fromJson(json)
        assertEquals("3.06.40", info.currentVersion)
        assertEquals("本次OTA新增功能：\n1.城市领航辅助\n2.一键泊车", info.releaseNotes)
        assertEquals("2026.05.24", info.updateTime)
        assertFalse(info.hasNewVersion)
        assertEquals(OtaStatus.UP_TO_DATE, info.status)
        assertTrue(info.isUpToDate)
    }

    @Test
    fun `parse update available ota response with release notes`() {
        val json = JSONObject("""
            {
                "curVersion": "Leapmotor OS 1.3.20",
                "newVersion": "Leapmotor OS 1.4.00",
                "hasUpdate": true,
                "releaseNotes": "1. 优化能耗算法\n2. 新增哨兵模式低功耗",
                "taskId": "802914",
                "packageSize": 2000000000,
                "status": 1
            }
        """.trimIndent())
        val info = VehicleOtaInfo.fromJson(json)
        assertEquals("Leapmotor OS 1.3.20", info.currentVersion)
        assertEquals("Leapmotor OS 1.4.00", info.newVersion)
        assertTrue(info.hasNewVersion)
        assertEquals("802914", info.taskId)
        assertEquals("1.86 GB", info.packageSize)
        assertEquals(OtaStatus.UPDATE_AVAILABLE, info.status)
        assertFalse(info.isUpToDate)
    }

    @Test
    fun `parse downloading and downloaded ota states`() {
        val downloadingJson = JSONObject("""
            {
                "version": "1.3.20",
                "targetVersion": "1.4.00",
                "status": 2,
                "progress": 45
            }
        """.trimIndent())
        val downloading = VehicleOtaInfo.fromJson(downloadingJson)
        assertEquals(OtaStatus.DOWNLOADING, downloading.status)
        assertTrue(downloading.isDownloading)
        assertEquals(45, downloading.progressPercent)

        val downloadedJson = JSONObject("""
            {
                "version": "1.3.20",
                "targetVersion": "1.4.00",
                "status": 3,
                "progress": 100
            }
        """.trimIndent())
        val downloaded = VehicleOtaInfo.fromJson(downloadedJson)
        assertEquals(OtaStatus.DOWNLOADED, downloaded.status)
        assertTrue(downloaded.isDownloaded)
    }

    @Test
    fun `commands build generates valid FOTA control payloads`() {
        val download = Commands.build("fotaDownload:802914")
        assertEquals("390", download.cmdid)
        assertEquals("""{"taskId":"802914"}""", download.stateJson)
        assertEquals("下载固件", download.label)

        val install = Commands.build("fotaInstall:802914")
        assertEquals("391", install.cmdid)
        assertEquals("""{"taskId":"802914"}""", install.stateJson)
        assertEquals("安装固件", install.label)

        val schedule = Commands.build("fotaSchedule:802914:02:00:00")
        assertEquals("392", schedule.cmdid)
        assertEquals("""{"taskId":"802914","scheduleTime":"02:00:00"}""", schedule.stateJson)
        assertEquals("预约安装固件", schedule.label)
    }

    @Test
    fun `control feedback policy provides clear messages for fota commands`() {
        assertEquals("正在启动固件下载...", ControlFeedbackFormatter.inProgress("fotaDownload:802914", null))
        assertEquals("正在发送固件升级指令...", ControlFeedbackFormatter.inProgress("fotaInstall:802914", null))
        assertEquals("正在提交定时升级预约...", ControlFeedbackFormatter.inProgress("fotaSchedule:802914:02:00:00", null))

        assertEquals("固件下载已启动", ControlFeedbackFormatter.success("fotaDownload:802914", null))
        assertEquals("整车升级指令已发送", ControlFeedbackFormatter.success("fotaInstall:802914", null))
        assertEquals("定时升级已预约成功", ControlFeedbackFormatter.success("fotaSchedule:802914:02:00:00", null))
    }

    @Test
    fun `sub account ota policy correctly rejects sub account and allows owner account`() {
        assertFalse(VehicleOtaPolicy.canOperateOta(isSharedAccount = true))
        assertTrue(VehicleOtaPolicy.canOperateOta(isSharedAccount = false))

        assertEquals("当前账号为授权子账号，车机 OTA 更新需车主主账号操作", VehicleOtaPolicy.SUB_ACCOUNT_OTA_UNSUPPORTED_MESSAGE)
        assertEquals("子账号无车机 OTA 更新权限", VehicleOtaPolicy.SUB_ACCOUNT_OTA_HINT_TITLE)
        assertTrue(VehicleOtaPolicy.SUB_ACCOUNT_OTA_HINT_DESC.contains("车主主账号可用"))
        assertTrue(VehicleOtaPolicy.SUB_ACCOUNT_OTA_HINT_DESC.contains("请使用车主手机号登录"))
    }
}
