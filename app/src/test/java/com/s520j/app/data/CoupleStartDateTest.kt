package com.s520j.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 固定相伴起始日：2025-11-15 00:00:00 Asia/Shanghai，epoch 毫秒值必须精确。 */
class CoupleStartDateTest {

    @Test
    fun `constant equals 2025 november fifteenth midnight shanghai`() {
        // 2025-11-15 00:00:00 +08:00 -> epoch ms
        assertEquals(1763136000000L, CoupleStartDate.FIXED_COUPLE_START_MS)
    }

    @Test
    fun `epoch converts back to local date november fifteenth twenty twenty five`() {
        val ms = CoupleStartDate.FIXED_COUPLE_START_MS
        assertTrue("must be positive epoch ms", ms > 0L)
        // 换算回 LocalDate（Asia/Shanghai 起始日）
        val local = java.time.Instant.ofEpochMilli(ms)
            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
            .toLocalDate()
        assertEquals(2025, local.year)
        assertEquals(11, local.monthValue)
        assertEquals(15, local.dayOfMonth)
    }
}
