package com.s520j.app.data.backup

import com.s520j.app.data.local.ContentEntity
import com.s520j.app.data.local.FieldCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 备份编解码：字段可完整往返，坏文件不能污染本地数据。 */
class BackupCodecTest {

    private fun entity(id: String = "rec-1") = ContentEntity(
        id = id,
        module = "DISH",
        spaceId = null,
        ownerId = "user-1",
        ownerName = "许愿树",
        visibility = "PRIVATE",
        title = "番茄炒蛋",
        content = "很好吃",
        eventDate = 1_700_000_000_000,
        mediaIds = FieldCodec.encodeList(listOf("/data/a.jpg")),
        mediaRefs = FieldCodec.encodeList(listOf("asset-1")),
        extraJson = FieldCodec.encodeMap(mapOf("星级" to "5")),
        createdAt = 100L,
        updatedAt = 200L,
        deletedAt = null,
        version = 3
    )

    @Test
    fun `备份写入后可完整还原字段`() {
        val json = BackupCodec.encode(listOf(entity()), createdAt = 1_000L)

        val restored = BackupCodec.decode(json)

        assertNotNull(restored)
        assertEquals(1, restored!!.size)
        val rec = restored.first()
        assertEquals("rec-1", rec.id)
        assertEquals("番茄炒蛋", rec.title)
        assertEquals(3, rec.version)
        assertEquals(listOf("asset-1"), FieldCodec.decodeList(rec.mediaRefs))
        assertEquals("5", FieldCodec.decodeMap(rec.extraJson)["星级"])
    }

    @Test
    fun `版本不匹配时拒绝导入`() {
        val json = """{"version":99,"records":[]}"""
        assertNull(BackupCodec.decode(json))
    }

    @Test
    fun `损坏文件返回 null`() {
        assertNull(BackupCodec.decode("{not json"))
    }

    @Test
    fun `文件名带时间戳便于排序`() {
        assertEquals("s520j-backup-123.json", BackupCodec.fileName(123L))
    }
}
