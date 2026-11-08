package com.s520j.app.domain.tacit

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromiseTest {

    @Test
    fun `默认状态为进行中`() {
        val record = promise(status = null, fulfilledAt = null)
        assertEquals("进行中", Promise.status(record))
        assertFalse(Promise.isFulfilled(record))
        assertNull(Promise.fulfilledAt(record))
    }

    @Test
    fun `已兑现时返回兑现时间`() {
        val ts = 1_700_000_000_000L
        val record = promise(status = "已兑现", fulfilledAt = ts)
        assertEquals("已兑现", Promise.status(record))
        assertTrue(Promise.isFulfilled(record))
        assertEquals(ts, Promise.fulfilledAt(record))
    }

    @Test
    fun `状态不是已兑现时忽略兑现时间`() {
        val record = promise(status = "进行中", fulfilledAt = 123L)
        assertFalse(Promise.isFulfilled(record))
    }

    private fun promise(status: String?, fulfilledAt: Long?): ContentRecord = ContentRecord(
        id = "p-$status",
        module = ContentModule.PROMISE,
        spaceId = "space-local",
        ownerId = "u",
        ownerName = "我",
        visibility = Visibility.COUPLE,
        title = "一起存旅行基金",
        content = "每月各存一点",
        eventDate = null,
        mediaIds = emptyList(),
        extra = buildMap {
            if (status != null) put("状态", status)
            if (fulfilledAt != null) put("兑现时间", fulfilledAt.toString())
        },
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        version = 1
    )
}
