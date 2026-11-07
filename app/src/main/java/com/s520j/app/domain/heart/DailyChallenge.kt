package com.s520j.app.domain.heart

/** 今日心动挑战的按天状态：日期 + 换一换偏移 + 双方确认。 */
data class ChallengeState(
    val epochDay: Long,
    val offset: Int,
    val selfDone: Boolean,
    val partnerDone: Boolean
) {
    val bothDone: Boolean get() = selfDone && partnerDone

    fun promptIndex(promptCount: Int): Int {
        require(promptCount > 0) { "挑战文案不能为空" }
        return Math.floorMod(epochDay + offset, promptCount.toLong()).toInt()
    }

    fun rotate(): ChallengeState = copy(offset = offset + 1)

    fun toggleSelf(): ChallengeState = copy(selfDone = !selfDone)

    fun togglePartner(): ChallengeState = copy(partnerDone = !partnerDone)

    fun encode(): String = "$epochDay|$offset|$selfDone|$partnerDone"

    companion object {
        /** 同一天沿用已存状态，跨天重置为未完成。 */
        fun forDay(stored: ChallengeState?, epochDay: Long): ChallengeState =
            stored?.takeIf { it.epochDay == epochDay } ?: ChallengeState(epochDay, 0, false, false)

        fun decode(raw: String?): ChallengeState? {
            val parts = raw?.split("|") ?: return null
            if (parts.size != 4) return null
            val epochDay = parts[0].toLongOrNull() ?: return null
            val offset = parts[1].toIntOrNull() ?: return null
            val selfDone = parts[2].toBooleanStrictOrNull() ?: return null
            val partnerDone = parts[3].toBooleanStrictOrNull() ?: return null
            return ChallengeState(epochDay, offset, selfDone, partnerDone)
        }
    }
}
