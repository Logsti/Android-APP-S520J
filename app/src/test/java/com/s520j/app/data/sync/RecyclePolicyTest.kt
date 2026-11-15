package com.s520j.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 8B.3 回收站清理规则：删除满 30 天后由 WorkManager 自动彻底清理。 */
class RecyclePolicyTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `删除不足 30 天不清理`() {
        assertFalse(RecyclePolicy.shouldPurge(deletedAt = 0L, now = 29 * day))
    }

    @Test
    fun `删除满 30 天清理`() {
        assertTrue(RecyclePolicy.shouldPurge(deletedAt = 0L, now = 30 * day))
    }

    @Test
    fun `删除超过 30 天清理`() {
        assertTrue(RecyclePolicy.shouldPurge(deletedAt = 0L, now = 45 * day))
    }

    @Test
    fun `未删除内容永不清理`() {
        assertFalse(RecyclePolicy.shouldPurge(deletedAt = null, now = 400 * day))
    }
}
