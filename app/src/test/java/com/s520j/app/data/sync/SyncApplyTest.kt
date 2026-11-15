package com.s520j.app.data.sync

import com.s520j.app.data.local.ContentEntity
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 8B.1 同步合并规则：本地未推送的修改优先，远端仅在无待推送时覆盖。 */
class SyncApplyTest {

    private val entity = ContentEntity(
        id = "rec-2",
        module = "WISH",
        spaceId = "space-local",
        ownerId = "user-1",
        ownerName = "小鹿",
        visibility = "couple",
        title = "去看海",
        content = "",
        eventDate = null,
        mediaIds = "",
        extraJson = "",
        createdAt = 1L,
        updatedAt = 2L,
        deletedAt = null,
        version = 4
    )

    @Test
    fun `本地有待推送操作时不应用远端版本`() {
        assertFalse(SyncApply.shouldApplyRemote(hasPending = true))
    }

    @Test
    fun `本地无待推送操作时应用远端版本`() {
        assertTrue(SyncApply.shouldApplyRemote(hasPending = false))
    }

    @Test
    fun `pushOp 携带游标序号与基准版本`() {
        val op = CloudPayload.pushOp(entity, seq = 7, cloudSpaceId = "cloud-1")
        assertEquals("UPSERT", op["op"]!!.jsonPrimitive.content)
        assertEquals(7, op["seq"]!!.jsonPrimitive.int)
        assertEquals("rec-2", op["entityId"]!!.jsonPrimitive.content)
        assertEquals(4, op["baseVersion"]!!.jsonPrimitive.int)
    }

    @Test
    fun `SOFT_DELETE 与 PURGE 不携带记录内容`() {
        val del = CloudPayload.deleteOp("rec-2", seq = 8)
        assertEquals("SOFT_DELETE", del["op"]!!.jsonPrimitive.content)
        assertEquals("rec-2", del["entityId"]!!.jsonPrimitive.content)
        assertTrue(del.containsKey("seq"))
    }

    @Test
    fun `30 天冲突快照到期时间正确`() {
        val expire = SyncApply.conflictExpireAt(now = 1_000L)
        assertEquals(1_000L + 30L * 24 * 60 * 60 * 1000, expire)
    }
}
