package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickRecordTypesTest {

    @Test
    fun `六个采集类型按顺序排列且不再包含文字`() {
        assertEquals(listOf("照片", "做饭", "探店", "旅行", "浪漫时刻", "随手记"), QuickRecordTypes.all)
        assertEquals(6, QuickRecordTypes.all.size)
        assertFalse(QuickRecordTypes.all.contains("文字"))
    }

    @Test
    fun `快速记录主入口为五项且不含随手记`() {
        assertEquals(listOf("照片", "做饭", "探店", "旅行", "浪漫时刻"), QuickRecordTypes.primary)
        assertEquals(5, QuickRecordTypes.primary.size)
        assertFalse(QuickRecordTypes.primary.contains(QuickRecordTypes.JOT))
    }

    @Test
    fun `六种类型均要求照片`() {
        QuickRecordTypes.all.forEach { assertTrue(it, QuickRecordTypes.needsPhoto(it)) }
        assertFalse(QuickRecordTypes.needsPhoto(null))
        assertFalse(QuickRecordTypes.needsPhoto("未知"))
    }

    @Test
    fun `类型路由到唯一目标模块`() {
        assertEquals(ContentModule.PHOTO, QuickRecordTypes.targetModuleOf("照片"))
        assertEquals(ContentModule.DISH, QuickRecordTypes.targetModuleOf("做饭"))
        assertEquals(ContentModule.SHOP, QuickRecordTypes.targetModuleOf("探店"))
        assertEquals(ContentModule.TRAVEL, QuickRecordTypes.targetModuleOf("旅行"))
        assertEquals(ContentModule.ROMANTIC_MOMENT, QuickRecordTypes.targetModuleOf("浪漫时刻"))
        assertEquals(ContentModule.JOT, QuickRecordTypes.targetModuleOf("随手记"))
        assertNull(QuickRecordTypes.targetModuleOf("文字"))
        assertNull(QuickRecordTypes.targetModuleOf(null))
    }

    @Test
    fun `类型归一化去除空白并拒绝未知值`() {
        assertEquals("旅行", QuickRecordTypes.normalize(" 旅行 "))
        assertNull(QuickRecordTypes.normalize("文字"))
    }
}
