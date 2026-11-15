package com.s520j.app.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolverTest {

    private fun recordJson(
        id: String = "rec-1",
        title: String = "本机版本",
        version: Int = 3,
        deletedAt: Long? = null
    ) = buildJsonObject {
        put("id", id)
        put("module", "WISH")
        put("visibility", "couple")
        put("title", title)
        put("content", "内容")
        put("createdAt", 1000L)
        put("updatedAt", 1000L)
        if (deletedAt != null) put("deletedAt", deletedAt) else put("deletedAt", kotlinx.serialization.json.JsonNull)
        put("version", version)
    }.toString()

    @Test
    fun `保留本机版本时沿用本机内容并把版本对齐云端基线`() {
        val resolved = ConflictResolver.keepLocal(
            localJson = recordJson(title = "本机版本", version = 3),
            serverJson = recordJson(title = "云端版本", version = 5),
            now = 2_000L,
            fallbackSpaceId = "space-cloud"
        )
        assertEquals("本机版本", resolved?.title)
        // baseVersion 必须等于云端当前版本，否则会再次冲突。
        assertEquals(5, resolved?.version)
        assertEquals(2_000L, resolved?.updatedAt)
        assertNull(resolved?.deletedAt)
    }

    @Test
    fun `保留本机版本时若本机已删除则恢复为未删除`() {
        val resolved = ConflictResolver.keepLocal(
            localJson = recordJson(version = 2, deletedAt = 500L),
            serverJson = recordJson(version = 2),
            now = 2_000L,
            fallbackSpaceId = null
        )
        assertNull(resolved?.deletedAt)
        assertEquals(2, resolved?.version)
    }

    @Test
    fun `缺少 id 的快照无法解析`() {
        val resolved = ConflictResolver.keepLocal(
            localJson = """{"title":"无 id"}""",
            serverJson = recordJson(),
            now = 2_000L,
            fallbackSpaceId = null
        )
        assertNull(resolved)
    }

    @Test
    fun `本机快照自带的空间归属优先于默认空间`() {
        val resolved = ConflictResolver.keepLocal(
            localJson = recordJson().let {
                Json.parseToJsonElement(it).jsonObject
            }.let { obj ->
                kotlinx.serialization.json.JsonObject(
                    obj + ("spaceId" to kotlinx.serialization.json.JsonPrimitive("space-cloud"))
                )
            }.toString(),
            serverJson = recordJson(),
            now = 2_000L,
            fallbackSpaceId = "space-fallback"
        )
        assertEquals("space-cloud", resolved?.spaceId)
        assertTrue(resolved!!.version > 0)
    }
}
