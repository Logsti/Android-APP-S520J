package com.s520j.app.domain.heart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 今日挑战的云端合并规则：各自标记不可撤销、跨天重置、偏移保留。 */
class ChallengeStateTest {

    @Test
    fun `标记自己完成后不可撤销`() {
        val state = ChallengeState(epochDay = 100, offset = 2, selfDone = false, partnerDone = false)

        val marked = state.markSelfDone()

        assertTrue(marked.selfDone)
        assertEquals(2, marked.offset)
        assertFalse(marked.partnerDone)
        // 已完成后再次调用保持完成（幂等）。
        assertTrue(marked.markSelfDone().selfDone)
    }

    @Test
    fun `云端合并同一天时保留换一换偏移并使用云端标记`() {
        val local = ChallengeState(epochDay = 100, offset = 3, selfDone = true, partnerDone = false)

        val merged = local.mergeCloud(epochDay = 100, selfDone = true, partnerDone = true)

        assertEquals(100L, merged.epochDay)
        assertEquals(3, merged.offset)
        assertTrue(merged.selfDone)
        assertTrue(merged.partnerDone)
        assertTrue(merged.bothDone)
    }

    @Test
    fun `云端合并跨天时按新的一天的云端标记重建`() {
        val local = ChallengeState(epochDay = 100, offset = 1, selfDone = true, partnerDone = true)

        val merged = local.mergeCloud(epochDay = 101, selfDone = false, partnerDone = false)

        assertEquals(101L, merged.epochDay)
        assertEquals(1, merged.offset)
        assertFalse(merged.selfDone)
        assertFalse(merged.partnerDone)
        assertFalse(merged.bothDone)
    }

    @Test
    fun `同一天沿用已存状态跨天重置`() {
        val stored = ChallengeState(epochDay = 200, offset = 2, selfDone = true, partnerDone = true)

        assertEquals(stored, ChallengeState.forDay(stored, 200))
        assertEquals(ChallengeState(201, 0, false, false), ChallengeState.forDay(stored, 201))
    }
}
