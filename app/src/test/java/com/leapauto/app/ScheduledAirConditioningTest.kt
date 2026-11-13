package com.leapauto.app

import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledAirConditioningTest {
    private val deviceId = "test-device-id"
    private val now = LocalDateTime.of(2026, 10, 22, 11, 55)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private fun plan(enabled: Boolean = true) = ScheduledAcPlan(
        enabled = enabled,
        startTime = "19:54",
        days = "1,0,1,1,0,0,0",
        temperatureC = 32,
        windLevel = 7,
        mode = ScheduledAcMode.HOT,
        circle = AirCircle.INNER,
        windshieldDefogging = true
    )

    @Test
    fun `enabled schedule matches the official captured fields`() {
        val command = Commands.buildScheduledAc(plan(), deviceId, now)

        assertEquals("171", command.cmdid)
        val controls = JSONObject(command.stateJson).getJSONArray("controls")
        assertEquals(1, controls.length())
        val item = controls.getJSONObject(0)
        assertEquals("1", item.getString("on"))
        assertEquals("android_${deviceId}${now}00", item.getString("set_id"))
        assertEquals("2026-10-22 19:54:00", item.getString("start_time"))
        assertEquals(now.toString(), item.getString("update_time"))
        assertEquals(listOf(1, 3, 4), (0 until item.getJSONArray("days").length()).map {
            item.getJSONArray("days").getInt(it)
        })
        assertEquals("32", item.getString("temperature"))
        assertEquals("7", item.getString("windlevel"))
        assertEquals("hot", item.getString("mode"))
        assertEquals("in", item.getString("circle"))
        assertEquals("1", item.getString("wshld"))
        assertEquals("all", item.getString("position"))
        assertEquals("manual", item.getString("operate"))
    }

    @Test
    fun `cancellation matches official empty controls and preserves local preferences`() {
        val saved = plan(enabled = false)
        val command = Commands.buildScheduledAc(saved, deviceId, now)
        assertEquals("171", command.cmdid)
        assertEquals("""{"controls":[]}""", command.stateJson)
        assertEquals("19:54", saved.startTime)
        assertEquals(32, saved.temperatureC)
        assertEquals("1,0,1,1,0,0,0", saved.days)
        assertFalse(Commands.parseScheduledAc(command.stateJson)!!.hasSchedule)
    }

    @Test
    fun `disabled appointment is not equivalent to an empty schedule`() {
        val payload = JSONObject(Commands.buildScheduledAc(plan(), deviceId, now).stateJson)
        payload.getJSONArray("controls").getJSONObject(0).put("on", "0")
        val state = Commands.parseScheduledAc(payload)!!
        assertTrue(state.hasSchedule)
        assertFalse(state.plan.enabled)
    }

    @Test
    fun `editor displays server fields instead of local cached fields`() {
        val remote = plan().copy(startTime = "06:20", temperatureC = 20, windLevel = 3)
        assertEquals(remote, ScheduledAcState(true, remote).editorPlan(plan(false)))
    }

    @Test
    fun `empty server schedule disables editor while preserving preferences`() {
        val local = plan()
        val state = Commands.parseScheduledAc("""{"controls":[]}""")!!
        assertEquals(local.copy(enabled = false), state.editorPlan(local))
    }

    @Test
    fun `past start time rolls over to the next day`() {
        val past = Commands.scheduledAcStartTime("08:00", now)
        val exact = Commands.scheduledAcStartTime("11:55", now)

        assertEquals("2026-10-23 08:00:00", past)
        assertEquals("2026-10-23 11:55:00", exact)
    }

    @Test
    fun `sunday maps to zero in the vehicle day list`() {
        val item = JSONObject(
            Commands.buildScheduledAc(
                plan().copy(days = "1,0,0,0,0,0,1"),
                deviceId,
                now
            ).stateJson
        ).getJSONArray("controls").getJSONObject(0)

        val days = (0 until item.getJSONArray("days").length()).map {
            item.getJSONArray("days").getInt(it)
        }
        assertEquals(listOf(0, 1), days)
    }

    @Test
    fun `query payload parses back into the same plan`() {
        val state = Commands.parseScheduledAc(
            """{"controls":[{"circle":"in","days":[1,3,4],""" +
                """"mode":"hot","on":"1","operate":"manual","position":"all",""" +
                """"set_id":"android_${deviceId}${now}00","start_time":"2026-10-22 19:54:00",""" +
                """"temperature":"32","update_time":"$now","windlevel":"7","wshld":"1"}]}"""
        )

        assertTrue(state!!.hasSchedule)
        assertEquals("19:54", state.plan.startTime)
        assertEquals("1,0,1,1,0,0,0", state.plan.days)
        assertEquals(32, state.plan.temperatureC)
        assertEquals(7, state.plan.windLevel)
        assertEquals(ScheduledAcMode.HOT, state.plan.mode)
        assertEquals(AirCircle.INNER, state.plan.circle)
        assertTrue(state.plan.windshieldDefogging)
        assertTrue(state.plan.enabled)
    }

    @Test
    fun `empty controls report no schedule instead of a parse failure`() {
        val state = Commands.parseScheduledAc("""{"controls":[]}""")

        assertFalse(state!!.hasSchedule)
    }

    @Test
    fun `malformed or blank payloads keep the local plan`() {
        assertNull(Commands.parseScheduledAc(null))
        assertNull(Commands.parseScheduledAc(""))
        assertNull(Commands.parseScheduledAc("not-json"))
        assertNull(Commands.parseScheduledAc("""{"data":{}}"""))
    }

    @Test
    fun `out of range values are coerced before sending`() {
        val item = JSONObject(
            Commands.buildScheduledAc(
                plan().copy(temperatureC = 40, windLevel = 12),
                deviceId,
                now
            ).stateJson
        ).getJSONArray("controls").getJSONObject(0)

        assertEquals("32", item.getString("temperature"))
        assertEquals("7", item.getString("windlevel"))
    }

    @Test
    fun `plan defaults closeWindows and closeSunshade to true and wire payload strictly excludes extra fields`() {
        val defaultPlan = ScheduledAcPlan()
        assertTrue("关闭车窗默认开启", defaultPlan.closeWindows)
        assertTrue("关闭遮阳帘默认开启", defaultPlan.closeSunshade)

        val command = Commands.buildScheduledAc(defaultPlan.copy(enabled = true), deviceId, now)
        val item = JSONObject(command.stateJson).getJSONArray("controls").getJSONObject(0)
        assertFalse("下发车端的 cmdid=171 协议载荷不得包含车端未定义字段 closeWindows", item.has("closeWindows"))
        assertFalse("下发车端的 cmdid=171 协议载荷不得包含车端未定义字段 closeSunshade", item.has("closeSunshade"))
    }

    @Test
    fun `editorPlan preserves user local preferences for closeWindows and closeSunshade`() {
        val remotePlan = plan().copy(startTime = "08:00")
        val localPreferences = plan().copy(closeWindows = false, closeSunshade = false)

        val editorPlan = ScheduledAcState(hasSchedule = true, plan = remotePlan).editorPlan(localPreferences)
        assertEquals("08:00", editorPlan.startTime)
        assertFalse("编辑器应保留本地用户关闭车窗选项", editorPlan.closeWindows)
        assertFalse("编辑器应保留本地用户关闭遮阳帘选项", editorPlan.closeSunshade)
    }
}
