package com.s520j.app.domain.us

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSettingTest {

    @Test
    fun `没有数据时回退默认值且全部开启`() {
        val setting = ReminderSettingCodec.decode(null)
        assertEquals("20:00", setting.time)
        assertTrue(setting.importantDay)
        assertTrue(setting.calendar)
        assertTrue(setting.task)
        assertEquals(3, setting.enabledCount)
    }

    @Test
    fun `空字符串回退默认值`() {
        assertEquals(ReminderSetting(), ReminderSettingCodec.decode(""))
    }

    @Test
    fun `编码后可以还原时间与开关`() {
        val setting = ReminderSetting(time = "08:00", importantDay = true, calendar = false, task = true)
        assertEquals(setting, ReminderSettingCodec.decode(ReminderSettingCodec.encode(setting)))
    }

    @Test
    fun `修改开关后数量正确`() {
        val setting = ReminderSetting(importantDay = false, calendar = false, task = true)
        assertEquals(1, setting.enabledCount)
        assertFalse(setting.importantDay)
        assertFalse(setting.calendar)
        assertTrue(setting.task)
    }

    @Test
    fun `非法时间回退默认时间`() {
        val setting = ReminderSettingCodec.decode("99:99|1|0|1")
        assertEquals("20:00", setting.time)
        assertFalse(setting.calendar)
    }

    @Test
    fun `时间选项只包含三个固定档位`() {
        assertEquals(listOf("08:00", "12:00", "20:00"), ReminderSettingCodec.TIME_OPTIONS)
    }
}
