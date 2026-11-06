package com.s520j.app.data

/**
 * 固定相伴起始日：2025-11-15 00:00:00 Asia/Shanghai（用户实际确定的纪念日）。
 * 所有新注册用户的相伴天数从此起算，不再使用注册当天。
 */
object CoupleStartDate {
    /** 2025-11-15 00:00:00 +08:00 的 epoch 毫秒值。 */
    const val FIXED_COUPLE_START_MS: Long = 1763136000000L
}
