package com.s520j.app.domain.tacit

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipRuleTest {

    @Test
    fun `未确认时为待对方确认`() {
        val record = rule(confirm = "等待对方确认")
        assertEquals("待对方确认", RelationshipRule.status(record))
        assertFalse(RelationshipRule.isAcknowledged(record))
    }

    @Test
    fun `双方确认后标记共同认可`() {
        val record = rule(confirm = "双方已确认")
        assertEquals("共同认可", RelationshipRule.status(record))
        assertTrue(RelationshipRule.isAcknowledged(record))
    }

    @Test
    fun `缺少确认字段时默认为待对方确认`() {
        val record = rule(confirm = null)
        assertEquals("待对方确认", RelationshipRule.status(record))
        assertFalse(RelationshipRule.isAcknowledged(record))
    }

    private fun rule(confirm: String?): ContentRecord = ContentRecord(
        id = "r-$confirm",
        module = ContentModule.RELATIONSHIP_RULE,
        spaceId = "space-local",
        ownerId = "u",
        ownerName = "我",
        visibility = Visibility.COUPLE,
        title = "生气先说出来",
        content = "不要冷战",
        eventDate = null,
        mediaIds = emptyList(),
        extra = buildMap { if (confirm != null) put("确认", confirm) },
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        version = 1
    )
}
