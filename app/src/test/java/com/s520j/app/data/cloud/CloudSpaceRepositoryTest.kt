package com.s520j.app.data.cloud

import com.s520j.app.data.local.BindState
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.local.SpaceDao
import com.s520j.app.data.local.SpaceEntity
import com.s520j.app.data.prefs.UserPreferences
import com.s520j.app.data.repository.SpaceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 共同空间云端镜像：重点验证「任何异常都不会抛给调用方」，
 * 否则异常会逃逸 viewModelScope 协程导致 APP 闪退（实机反馈 2026-11-08）。
 */
class CloudSpaceRepositoryTest {

    private val api: CloudApi = mockk()
    private val prefs: UserPreferences = mockk()
    private val cloudAuth: CloudAuthRepository = mockk()
    private val dao: SpaceDao = mockk()
    private val db: S520JDatabase = mockk()
    private val spaceRepository: SpaceRepository = mockk()

    private val repo = CloudSpaceRepository(api, prefs, cloudAuth, db, spaceRepository)

    private val self = "user-a"
    private val spaceId = "space-cloud"

    private fun stubBasics() {
        coEvery { cloudAuth.ensureAccessToken() } returns "token"
        coEvery { cloudAuth.resolveSpaceId() } returns null
        coEvery {
            prefs.cloudSessionSnapshot()
        } returns UserPreferences.CloudSession(self, "token", "refresh", 0L, spaceId)
        coEvery { prefs.setCloudSpaceId(any()) } returns Unit
        coEvery { prefs.setPartnerNickname(any()) } returns Unit
        coEvery { prefs.setPartnerAvatar(any()) } returns Unit
        coEvery { spaceRepository.currentSpace() } returns null
        every { db.spaceDao() } returns dao
        coEvery { dao.upsert(any()) } returns Unit
        coEvery { dao.clear() } returns Unit
    }

    private fun ok(data: String) = CloudResponse(0, "", Json.parseToJsonElement(data))

    private fun stubHealthyApi() {
        coEvery { api.call(any(), any(), any()) } answers {
            when (thirdArg<CloudRequest>().action) {
                "space.create" -> ok("""{"space":{"id":"$spaceId","name":"我们的小屋"}}""")
                "space.update" -> ok("""{"space":{"id":"$spaceId","name":"我们的小屋"}}""")
                "space.generateBindCode" -> ok("""{"code":"CODE01","expiresAt":9000000}""")
                "space.get" -> ok(
                    """{"space":{"id":"$spaceId","name":"我们的小屋","intro":"","status":"active","createdBy":"$self"},
                    |"members":[{"userId":"$self","nickname":"A","role":"creator"}],
                    |"bindCodeExpiresAt":9000000}""".trimMargin()
                )
                else -> CloudResponse(1, "未知动作")
            }
        }
    }

    @Test
    fun `创建空间成功后本地镜像记录云端 id 与绑定码`() = runTest {
        stubBasics()
        stubHealthyApi()

        val result = repo.create("我们的小屋", "一起记录")

        val entity = (result as CloudSpaceResult.Success).value
        assertEquals(spaceId, entity.id)
        assertEquals("CODE01", entity.bindCode)
        assertEquals(BindState.INVITE_GENERATED.name, entity.bindState)
        // 没有空间时必须由用户主动创建，并使用用户填写的名称。
        coVerify {
            api.call(any(), any(), match {
                it.action == "space.create" &&
                    it.payload["name"]?.jsonPrimitive?.content == "我们的小屋"
            })
        }
        coVerify { dao.upsert(any()) }
    }

    @Test
    fun `已有单人空间时改名并生成绑定码而不是重复创建`() = runTest {
        stubBasics()
        stubHealthyApi()
        coEvery { cloudAuth.resolveSpaceId() } returns spaceId

        val result = repo.create("我们的小屋", "一起记录")

        assertEquals(true, result is CloudSpaceResult.Success)
        coVerify(exactly = 0) {
            api.call(any(), any(), match { it.action == "space.create" })
        }
        coVerify { api.call(any(), any(), match { it.action == "space.update" }) }
    }

    @Test
    fun `本地库写入失败时返回失败而不是抛出异常`() = runTest {
        stubBasics()
        stubHealthyApi()
        coEvery { dao.upsert(any()) } throws RuntimeException("no such column: bindRequestId")

        val result = repo.create("我们的小屋", "一起记录")

        assertEquals(true, result is CloudSpaceResult.Failure)
    }

    @Test
    fun `云端返回数据格式非法时返回失败而不是抛出异常`() = runTest {
        stubBasics()
        coEvery { api.call(any(), any(), any()) } answers {
            when (thirdArg<CloudRequest>().action) {
                "space.update" -> ok("""{"space":{"id":"$spaceId"}}""")
                "space.generateBindCode" -> ok("""{"code":"CODE01","expiresAt":9000000}""")
                "space.get" -> CloudResponse(0, "", JsonPrimitive("oops"))
                else -> CloudResponse(1, "未知动作")
            }
        }

        val result = repo.create("我们的小屋", "一起记录")

        assertEquals(true, result is CloudSpaceResult.Failure)
    }

    @Test
    fun `刷新失败时返回失败而不是抛出异常`() = runTest {
        stubBasics()
        coEvery { spaceRepository.currentSpace() } returns spaceEntity()
        coEvery { api.call(any(), any(), any()) } throws IllegalStateException("canceled")

        val result = repo.refreshCode()

        assertEquals(true, result is CloudSpaceResult.Failure)
    }

    @Test
    fun `云端没有空间时刷新会清空本地镜像`() = runTest {
        stubBasics()
        coEvery { spaceRepository.currentSpace() } returns spaceEntity()
        coEvery { api.call(any(), any(), any()) } returns ok("""{"space":null}""")

        assertEquals(true, repo.refreshLocal())
        coVerify { dao.clear() }
        coVerify { prefs.setCloudSpaceId(null) }
    }

    @Test
    fun `云端没有空间但本地处于绑定申请中时保留镜像`() = runTest {
        stubBasics()
        coEvery { spaceRepository.currentSpace() } returns spaceEntity().copy(
            bindState = BindState.REQUEST_PENDING.name,
            bindRequestId = "req-1",
            partnerUserId = "user-b"
        )
        coEvery { api.call(any(), any(), any()) } returns ok("""{"space":null}""")

        assertEquals(true, repo.refreshLocal())
        // 加入方在双方确认前云端暂无空间，此时清镜像会丢掉待确认状态。
        coVerify(exactly = 0) { dao.clear() }
        coVerify(exactly = 0) { prefs.setCloudSpaceId(null) }
    }

    @Test
    fun `确认绑定后本地标记我方已确认`() = runTest {
        stubBasics()
        val pending = spaceEntity().copy(
            bindState = BindState.REQUEST_PENDING.name,
            bindRequestId = "req-1",
            createdBy = self
        )
        coEvery { spaceRepository.currentSpace() } returns pending
        coEvery { api.call(any(), any(), any()) } answers {
            when (thirdArg<CloudRequest>().action) {
                "space.confirmBind" -> ok("""{"bound":false,"waitingForPartner":true}""")
                "space.get" -> ok("""{"space":null}""")
                else -> CloudResponse(1, "未知动作")
            }
        }

        val result = repo.confirmBind("req-1")

        assertEquals(false, (result as CloudSpaceResult.Success).value)
        coVerify { dao.upsert(match { it.ownerConfirmedAt != null }) }
    }

    @Test
    fun `本地刷新在云端异常时不抛出异常`() = runTest {
        coEvery { cloudAuth.ensureAccessToken() } throws IllegalStateException("boom")
        every { db.spaceDao() } returns dao
        coEvery { spaceRepository.currentSpace() } returns null

        // 修复前：IllegalStateException 直接逃逸协程 → APP 闪退。
        assertEquals(false, repo.refreshLocal())
    }

    @Test
    fun `修改空间信息调用 space_update 并更新本地镜像`() = runTest {
        stubBasics()
        stubHealthyApi()
        coEvery { spaceRepository.currentSpace() } returns spaceEntity()

        val result = repo.updateInfo("新名字", "新简介")

        assertEquals("新名字", (result as CloudSpaceResult.Success).value.name)
        coVerify {
            api.call(any(), any(), match {
                it.action == "space.update" &&
                    it.payload["name"]?.jsonPrimitive?.content == "新名字" &&
                    it.payload["intro"]?.jsonPrimitive?.content == "新简介"
            })
        }
        coVerify { dao.upsert(match { it.name == "新名字" && it.intro == "新简介" }) }
    }

    @Test
    fun `修改空间信息云端失败时不落本地镜像`() = runTest {
        stubBasics()
        stubHealthyApi()
        coEvery { spaceRepository.currentSpace() } returns spaceEntity()
        coEvery { api.call(any(), any(), any()) } answers {
            when (thirdArg<CloudRequest>().action) {
                "space.update" -> CloudResponse(1, "空间名称不能为空且不超过 20 个字")
                else -> CloudResponse(1, "未知动作")
            }
        }

        val result = repo.updateInfo("新名字", "新简介")

        assertEquals("空间名称不能为空且不超过 20 个字", (result as CloudSpaceResult.Failure).message)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `发起解绑调用解绑接口并刷新云端状态`() = runTest {
        stubBasics()
        coEvery { spaceRepository.currentSpace() } returns spaceEntity().copy(partnerUserId = "user-b")
        coEvery { api.call(any(), any(), any()) } answers {
            when (thirdArg<CloudRequest>().action) {
                "space.unbindRequest" -> ok(
                    """{"request":{"id":"unbind-1","status":"unbind_pending","fromUser":"$self","toUser":"user-b"}}"""
                )
                "space.get" -> ok(boundPayload())
                else -> CloudResponse(1, "未知动作")
            }
        }

        val result = repo.requestUnbind()

        assertEquals(true, result is CloudSpaceResult.Success)
        coVerify { api.call(any(), any(), match { it.action == "space.unbindRequest" }) }
        coVerify { api.call(any(), any(), match { it.action == "space.get" }) }
    }

    @Test
    fun `拒绝解绑携带申请 id 并刷新云端状态`() = runTest {
        stubBasics()
        coEvery { spaceRepository.currentSpace() } returns spaceEntity().copy(partnerUserId = "user-b")
        coEvery { api.call(any(), any(), any()) } answers {
            when (thirdArg<CloudRequest>().action) {
                "space.rejectUnbind" -> ok("""{"rejected":true}""")
                "space.get" -> ok(boundPayload())
                else -> CloudResponse(1, "未知动作")
            }
        }

        val result = repo.rejectUnbind("unbind-1")

        assertEquals(true, result is CloudSpaceResult.Success)
        coVerify {
            api.call(any(), any(), match {
                it.action == "space.rejectUnbind" &&
                    it.payload["requestId"]?.jsonPrimitive?.content == "unbind-1"
            })
        }
        coVerify { api.call(any(), any(), match { it.action == "space.get" }) }
    }

    @Test
    fun `撤回解绑携带申请 id 并刷新云端状态`() = runTest {
        stubBasics()
        coEvery { spaceRepository.currentSpace() } returns spaceEntity().copy(partnerUserId = "user-b")
        coEvery { api.call(any(), any(), any()) } answers {
            when (thirdArg<CloudRequest>().action) {
                "space.cancelUnbind" -> ok("""{"cancelled":true}""")
                "space.get" -> ok(boundPayload())
                else -> CloudResponse(1, "未知动作")
            }
        }

        val result = repo.cancelUnbind("unbind-1")

        assertEquals(true, result is CloudSpaceResult.Success)
        coVerify {
            api.call(any(), any(), match {
                it.action == "space.cancelUnbind" &&
                    it.payload["requestId"]?.jsonPrimitive?.content == "unbind-1"
            })
        }
        coVerify { api.call(any(), any(), match { it.action == "space.get" }) }
    }

    @Test
    fun `修改空间信息遇到网络异常时转为失败而不是抛出`() = runTest {
        stubBasics()
        coEvery { spaceRepository.currentSpace() } returns spaceEntity()
        coEvery { api.call(any(), any(), any()) } throws IOException("network down")

        val result = repo.updateInfo("新名字", "新简介")

        assertEquals(true, (result as CloudSpaceResult.Failure).offline)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `刷新本地时把对方昵称和头像写入 prefs`() = runTest {
        stubBasics()
        coEvery { spaceRepository.currentSpace() } returns null
        coEvery { api.call(any(), any(), any()) } returns ok(boundPayload())

        assertEquals(true, repo.refreshLocal())

        coVerify { prefs.setPartnerNickname("B") }
        coVerify { prefs.setPartnerAvatar("https://example.com/b.png") }
    }

    @Test
    fun `单人空间刷新时不写对方昵称`() = runTest {
        stubBasics()
        coEvery { spaceRepository.currentSpace() } returns null
        coEvery { api.call(any(), any(), any()) } returns ok(
            """{"space":{"id":"$spaceId","name":"我们的小屋","intro":"","status":"active","createdBy":"$self"},
            |"members":[{"userId":"$self","nickname":"A","role":"creator","avatar":null}],
            |"bindCodeExpiresAt":9000000}""".trimMargin()
        )

        assertEquals(true, repo.refreshLocal())

        coVerify(exactly = 0) { prefs.setPartnerNickname(any()) }
        coVerify(exactly = 0) { prefs.setPartnerAvatar(any()) }
    }

    /** 已绑定双人空间的云端响应：解绑相关用例用它断言成功后能刷新回 BOUND。 */
    private fun boundPayload() =
        """{"space":{"id":"$spaceId","name":"我们的小屋","intro":"","status":"active","createdBy":"$self"},
        |"members":[{"userId":"$self","nickname":"A","role":"creator","avatar":"https://example.com/a.png"},
        |{"userId":"user-b","nickname":"B","role":"partner","avatar":"https://example.com/b.png"}]}""".trimMargin()

    private fun spaceEntity() = SpaceEntity(
        id = spaceId,
        name = "我们的小屋",
        intro = "",
        status = "ACTIVE",
        bindState = BindState.INVITE_GENERATED.name,
        bindCode = "OLD123",
        bindCodeExpiresAt = 9_000L,
        createdBy = self,
        partnerUserId = null,
        createdAt = 1_000L,
        updatedAt = 2_000L
    )
}
