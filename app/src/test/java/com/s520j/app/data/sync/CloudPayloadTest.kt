package com.s520j.app.data.sync

import com.s520j.app.data.local.ContentEntity
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 8B.1 云端负载映射：本地实体 ↔ sync.pull/push 的 JSON 结构。 */
class CloudPayloadTest {

    private val entity = ContentEntity(
        id = "rec-1",
        module = "DIARY",
        spaceId = "space-local",
        ownerId = "user-1",
        ownerName = "小鹿",
        visibility = "couple",
        title = "今天",
        content = "一起吃了面",
        eventDate = 1_700_000_000_000L,
        mediaIds = "m1|m2",
        extraJson = "星级=5;心情=好",
        createdAt = 1_600_000_000_000L,
        updatedAt = 1_610_000_000_000L,
        deletedAt = null,
        version = 3
    )

    @Test
    fun `mediaRefs 随记录编码并可在解码后还原`() {
        val withRefs = entity.copy(mediaRefs = "asset-1|asset-2")
        val json = CloudPayload.encodeRecord(withRefs, "cloud-space-1")
        assertEquals(2, json["mediaRefs"]!!.jsonArray.size)

        val decoded = CloudPayload.decodeRecord(json, "cloud-space-1")
        assertEquals(listOf("asset-1", "asset-2"), com.s520j.app.data.local.FieldCodec.decodeList(decoded!!.mediaRefs))
    }

    @Test
    fun `decodeRevokedIds 解析撤销列表并忽略空值`() {
        val data = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"records":[],"revokedIds":["rec-1","","rec-2"],"cursor":10}"""
        ).jsonObject

        assertEquals(listOf("rec-1", "rec-2"), CloudPayload.decodeRevokedIds(data))
    }

    @Test
    fun `decodeRevokedIds 缺失字段时返回空列表`() {
        val data = kotlinx.serialization.json.Json.parseToJsonElement("""{"records":[]}""").jsonObject

        assertTrue(CloudPayload.decodeRevokedIds(data).isEmpty())
    }

    @Test
    fun `encodeRecord 输出服务端可读字段`() {        val json = CloudPayload.encodeRecord(entity, "cloud-space-1")
        assertEquals("rec-1", json["id"]!!.jsonPrimitive.content)
        assertEquals("DIARY", json["module"]!!.jsonPrimitive.content)
        assertEquals("cloud-space-1", json["spaceId"]!!.jsonPrimitive.content)
        assertEquals("couple", json["visibility"]!!.jsonPrimitive.content)
        assertEquals("今天", json["title"]!!.jsonPrimitive.content)
        assertEquals(3, json["version"]!!.jsonPrimitive.int)
        assertEquals(2, json["mediaIds"]!!.jsonArray.size)
        assertEquals("5", json["extra"]!!.jsonObject["星级"]!!.jsonPrimitive.content)
    }

    @Test
    fun `encodeRecord 对仅自己可见不写 spaceId`() {
        val json = CloudPayload.encodeRecord(entity.copy(visibility = "private"), "cloud-space-1")
        assertNull(json["spaceId"])
    }

    @Test
    fun `encodeRecord 大写 COUPLE 也应附带 spaceId 并归一化为小写`() {
        val json = CloudPayload.encodeRecord(entity.copy(visibility = "COUPLE"), "cloud-space-1")
        assertEquals("cloud-space-1", json["spaceId"]!!.jsonPrimitive.content)
        assertEquals("couple", json["visibility"]!!.jsonPrimitive.content)
    }

    @Test
    fun `decodeRecord 大写 visibility 归一化为小写`() {
        val json = CloudPayload.encodeRecord(entity.copy(visibility = "COUPLE"), "cloud-space-1")
        val restored = CloudPayload.decodeRecord(json, null)
        assertEquals("couple", restored!!.visibility)
    }

    @Test
    fun `decodeRecord 还原本地实体`() {
        val json = CloudPayload.encodeRecord(entity, "cloud-space-1")
        val restored = CloudPayload.decodeRecord(json, "fallback")
        assertNotNull(restored)
        assertEquals("rec-1", restored!!.id)
        assertEquals("DIARY", restored.module)
        assertEquals("cloud-space-1", restored.spaceId)
        assertEquals("m1|m2", restored.mediaIds)
        assertEquals("星级=5;心情=好", restored.extraJson)
        assertEquals(3, restored.version)
    }

    @Test
    fun `decodeRecord 缺少 id 时返回 null`() {
        assertNull(CloudPayload.decodeRecord(kotlinx.serialization.json.JsonObject(emptyMap()), null))
    }

    @Test
    fun `种子演示数据不参与同步`() {
        assertTrue(CloudPayload.isSeed("seed-diary-123"))
        assertFalse(CloudPayload.isSeed("rec-1"))
        assertFalse(CloudPayload.isSeed(""))
    }
}
