package com.s520j.app.data.export

import com.s520j.app.data.local.SpaceEntity
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import com.s520j.app.data.prefs.SessionProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 导出 JSON 的字段完整性与转义用例（纯 JVM）。 */
class ExportBuilderTest {

    private val exportedAt = 1_700_000_000_000L

    private val space = SpaceEntity(
        id = "space-1",
        name = "我们的小屋",
        intro = "一起生活",
        status = "FROZEN",
        bindState = "FROZEN",
        bindCode = null,
        bindCodeExpiresAt = null,
        createdBy = "user-owner",
        partnerUserId = "user-partner",
        createdAt = 1L,
        updatedAt = 2L,
        pendingAction = "UNBIND",
        bindRequestedBy = "user-owner",
        unbindRequestedBy = "user-partner",
        ownerConfirmedAt = 3L,
        partnerConfirmedAt = 4L
    )

    private val session = SessionProfile(
        userId = "user-owner",
        nickname = "小满",
        accountId = "ACC00001",
        partnerNickname = "阿满",
        coupleStartAt = 88_888L,
        loggedIn = true
    )

    private fun record(id: String, title: String, deletedAt: Long? = null) = ContentRecord(
        id = id,
        module = ContentModule.NOTE,
        spaceId = space.id,
        ownerId = session.userId,
        ownerName = session.nickname,
        visibility = Visibility.COUPLE,
        title = title,
        content = "内容",
        eventDate = 10L,
        mediaIds = listOf("media-1", "media-2"),
        extra = mapOf("k" to "v"),
        createdAt = 11L,
        updatedAt = 12L,
        deletedAt = deletedAt,
        version = 2
    )

    private fun root(
        records: List<ContentRecord> = listOf(record("rec-1", "记录一")),
        spaceEntity: SpaceEntity? = space
    ): JsonObject = Json.parseToJsonElement(
        ExportBuilder.build(spaceEntity, session, records, exportedAt)
    ).jsonObject

    @Test
    fun topLevelFieldsArePresent() {
        val root = root()
        assertEquals("1", root["version"]!!.jsonPrimitive.content)
        assertEquals(exportedAt.toString(), root["exportedAt"]!!.jsonPrimitive.content)
        assertEquals("false", root["includesImages"]!!.jsonPrimitive.content)
        assertNotNull(root["space"])
        assertNotNull(root["session"])
        assertNotNull(root["records"])
    }

    @Test
    fun titleIsEscapedCorrectly() {
        val tricky = "他说\"你好\"\\C:\\path\n第二行\t制表"
        val root = root(records = listOf(record("rec-1", tricky)))
        assertEquals(tricky, root["records"]!!.jsonArray[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun spaceFieldsAreComplete() {
        val spaceJson = root()["space"]!!.jsonObject
        val expectedKeys = setOf(
            "id", "name", "intro", "status", "bindState", "bindCode", "bindCodeExpiresAt",
            "createdBy", "partnerUserId", "createdAt", "updatedAt", "pendingAction",
            "bindRequestedBy", "unbindRequestedBy", "ownerConfirmedAt", "partnerConfirmedAt"
        )
        assertEquals(expectedKeys, spaceJson.keys)
        assertEquals("space-1", spaceJson["id"]!!.jsonPrimitive.content)
        assertEquals("我们的小屋", spaceJson["name"]!!.jsonPrimitive.content)
        assertEquals("FROZEN", spaceJson["bindState"]!!.jsonPrimitive.content)
        assertEquals("UNBIND", spaceJson["pendingAction"]!!.jsonPrimitive.content)
        assertEquals("3", spaceJson["ownerConfirmedAt"]!!.jsonPrimitive.content)
        assertEquals("4", spaceJson["partnerConfirmedAt"]!!.jsonPrimitive.content)
        assertEquals("null", spaceJson["bindCode"]!!.jsonPrimitive.content)
    }

    @Test
    fun sessionFieldsAreComplete() {
        val sessionJson = root()["session"]!!.jsonObject
        assertEquals("小满", sessionJson["nickname"]!!.jsonPrimitive.content)
        assertEquals("阿满", sessionJson["partnerNickname"]!!.jsonPrimitive.content)
        assertEquals("88888", sessionJson["coupleStartAt"]!!.jsonPrimitive.content)
    }

    @Test
    fun recordFieldsAreComplete() {
        val recordJson = root()["records"]!!.jsonArray[0].jsonObject
        val expectedKeys = setOf(
            "id", "module", "moduleLabel", "spaceId", "ownerName", "visibility", "title",
            "content", "eventDate", "mediaIds", "extra", "createdAt", "updatedAt",
            "deletedAt", "version"
        )
        assertEquals(expectedKeys, recordJson.keys)
        assertEquals("NOTE", recordJson["module"]!!.jsonPrimitive.content)
        assertEquals("我们的笔记", recordJson["moduleLabel"]!!.jsonPrimitive.content)
        assertEquals("COUPLE", recordJson["visibility"]!!.jsonPrimitive.content)
        assertEquals("space-1", recordJson["spaceId"]!!.jsonPrimitive.content)
        assertEquals(2, recordJson["mediaIds"]!!.jsonArray.size)
        assertEquals("v", recordJson["extra"]!!.jsonObject["k"]!!.jsonPrimitive.content)
    }

    @Test
    fun recycledRecordIsIncluded() {
        val recycled = record("rec-2", "已删除的记录", deletedAt = 99L)
        val root = root(records = listOf(record("rec-1", "保留"), recycled))
        val records = root["records"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, records.size)
        val deleted = records.first { it["id"]!!.jsonPrimitive.content == "rec-2" }
        assertEquals("99", deleted["deletedAt"]!!.jsonPrimitive.content)
    }

    @Test
    fun includesImagesIsAlwaysFalse() {
        assertFalse(root()["includesImages"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun missingSpaceExportsAsJsonNull() {
        val root = root(spaceEntity = null)
        assertTrue(root["space"] is JsonNull)
        // 记录自身的 spaceId 原样导出，不因空间缺失而丢失。
        assertEquals(
            "space-1",
            root["records"]!!.jsonArray[0].jsonObject["spaceId"]!!.jsonPrimitive.content
        )
    }
}
