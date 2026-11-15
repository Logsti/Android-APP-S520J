package com.s520j.app.data.cloud

import com.s520j.app.data.prefs.SessionProfile
import com.s520j.app.data.prefs.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 云端账号仓库：`space.get` 在账号还没有空间时返回 `{ "space": null }`，
 * 解析必须容错，且**不能**自动帮用户创建空间（实机反馈 2026-11-09：
 * 登录后自动出现「昵称+的小屋」，用户无法自行创建并获得绑定码）。
 */
class CloudAuthRepositoryTest {

    private val api: CloudApi = mockk()
    private val prefs: UserPreferences = mockk()

    private val repo = CloudAuthRepository(api, prefs)

    private fun ok(data: String) =
        CloudResponse(0, "", Json.parseToJsonElement(data))

    private fun stubSession() {
        coEvery {
            prefs.cloudSessionSnapshot()
        } returns UserPreferences.CloudSession("user-a", "token", "refresh", Long.MAX_VALUE, null)
        coEvery { prefs.setCloudSpaceId(any()) } returns Unit
        coEvery { prefs.sessionSnapshot() } returns SessionProfile(
            userId = "user-a",
            nickname = "小明",
            accountId = "acc",
            partnerNickname = "TA",
            coupleStartAt = 0L,
            loggedIn = true
        )
    }

    @Test
    fun `云端没有空间时不自动创建`() = runTest {
        stubSession()
        coEvery { api.call(any(), any(), any()) } answers {
            when (thirdArg<CloudRequest>().action) {
                "space.get" -> ok("""{"space":null}""")
                else -> CloudResponse(1, "不应调用该动作")
            }
        }

        val spaceId = repo.resolveSpaceId()

        assertNull(spaceId)
        coVerify(exactly = 0) {
            api.call(any(), any(), match { it.action == "space.create" })
        }
        coVerify(exactly = 0) { prefs.setCloudSpaceId(any()) }
    }

    @Test
    fun `云端已有空间时返回空间 id`() = runTest {
        stubSession()
        coEvery { api.call(any(), any(), any()) } answers {
            when (thirdArg<CloudRequest>().action) {
                "space.get" -> ok("""{"space":{"id":"space-old","name":"我们的小屋"}}""")
                else -> CloudResponse(1, "不应调用该动作")
            }
        }

        assertEquals("space-old", repo.resolveSpaceId())
        coVerify { prefs.setCloudSpaceId("space-old") }
    }

    @Test
    fun `updateAvatar 调用 users_updateAvatar 并携带 base64 数据 URL`() = runTest {
        stubSession()
        val dataUrl = "data:image/jpeg;base64,/9j/4AAQSkZJRg=="
        coEvery { api.call(any(), any(), any()) } answers {
            when (thirdArg<CloudRequest>().action) {
                "users.updateAvatar" -> ok("""{"ok":true,"avatar":"$dataUrl"}""")
                else -> CloudResponse(1, "不应调用该动作")
            }
        }

        val result = repo.updateAvatar(dataUrl)

        assertEquals(true, result is CloudAuthResult.Success)
        coVerify {
            api.call(any(), any(), match {
                it.action == "users.updateAvatar" &&
                    it.payload["avatar"]?.jsonPrimitive?.content == dataUrl
            })
        }
    }

    @Test
    fun `同步查询空间保留云端权限错误码`() = runTest {
        stubSession()
        coEvery { api.call(any(), any(), any()) } returns CloudResponse(403, "没有权限")

        val error = try {
            repo.resolveSpaceIdForSync("token")
            null
        } catch (e: CloudRequestException) {
            e
        }

        assertEquals(403, error?.code)
        assertEquals("没有权限", error?.message)
    }

    @Test
    fun `updateAvatar 未登录时返回失败`() = runTest {
        coEvery { prefs.cloudSessionSnapshot() } returns null

        val result = repo.updateAvatar("data:image/jpeg;base64,abc")

        assertEquals(true, result is CloudAuthResult.Failure)
        coVerify(exactly = 0) { api.call(any(), any(), any()) }
    }

    @Test
    fun `刷新凭证遇到服务异常时保留登录态`() = runTest {
        coEvery { prefs.cloudSessionSnapshot() } returns UserPreferences.CloudSession(
            "user-a", "expired-token", "refresh", 0L, null
        )
        coEvery { api.call(any(), any(), any()) } returns CloudResponse(500, "服务暂时不可用")
        coEvery { prefs.clearCloudSession() } returns Unit

        assertNull(repo.ensureAccessToken())

        coVerify(exactly = 0) { prefs.clearCloudSession() }
    }
}
