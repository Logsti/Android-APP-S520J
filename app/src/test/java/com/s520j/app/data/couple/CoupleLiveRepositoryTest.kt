package com.s520j.app.data.couple

import com.s520j.app.data.cloud.CloudApi
import com.s520j.app.data.cloud.CloudAuthRepository
import com.s520j.app.data.cloud.CloudRequest
import com.s520j.app.data.cloud.CloudResponse
import com.s520j.app.data.prefs.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 挑战与事件接口的解析与降级：任何网络异常都不能抛给调用方。 */
class CoupleLiveRepositoryTest {

    private val api: CloudApi = mockk()
    private val prefs: UserPreferences = mockk()
    private val cloudAuth: CloudAuthRepository = mockk(relaxed = true)
    private val repo = CoupleLiveRepository(api, cloudAuth, prefs)

    private fun ok(data: String) = CloudResponse(0, "", Json.parseToJsonElement(data))

    private fun stubToken() {
        coEvery { cloudAuth.ensureAccessToken() } returns "token"
        coEvery { prefs.eventCursorSnapshot() } returns 0L
        coEvery { prefs.setEventCursor(any()) } returns Unit
    }

    @Test
    fun `getChallenge 解析双方标记`() = runTest {
        stubToken()
        coEvery { api.call(any(), any(), any()) } returns ok(
            """{"challenge":{"day":100,"selfDone":true,"partnerDone":false,"doneAt":null}}"""
        )

        val challenge = repo.getChallenge(100)

        assertEquals(100L, challenge?.day)
        assertEquals(true, challenge?.selfDone)
        assertEquals(false, challenge?.partnerDone)
        assertNull(challenge?.doneAt)
        coVerify {
            api.call(any(), any(), match {
                it.action == "challenge.get" && it.payload["day"]?.jsonPrimitive?.content == "100"
            })
        }
    }

    @Test
    fun `markChallenge 返回达成时间`() = runTest {
        stubToken()
        coEvery { api.call(any(), any(), any()) } returns ok(
            """{"challenge":{"day":100,"selfDone":true,"partnerDone":true,"doneAt":1699999999}}"""
        )

        val challenge = repo.markChallenge(100)

        assertEquals(1699999999L, challenge?.doneAt)
        assertEquals(true, challenge?.partnerDone)
        coVerify { api.call(any(), any(), match { it.action == "challenge.mark" }) }
    }

    @Test
    fun `sendPoke 成功返回 true`() = runTest {
        stubToken()
        coEvery { api.call(any(), any(), any()) } returns ok("""{"id":"evt-1","createdAt":1}""")

        assertTrue(repo.sendPoke("抱抱"))
        coVerify {
            api.call(any(), any(), match {
                it.action == "event.send" && it.payload["body"]?.jsonPrimitive?.content == "抱抱"
            })
        }
    }

    @Test
    fun `sendPoke 网络异常时返回 false 而不是抛出`() = runTest {
        stubToken()
        coEvery { api.call(any(), any(), any()) } throws IOException("down")

        assertEquals(false, repo.sendPoke("抱抱"))
    }

    @Test
    fun `pullEvents 解析事件并推进游标`() = runTest {
        stubToken()
        coEvery { api.call(any(), any(), any()) } returns ok(
            """{"events":[{"id":"e1","type":"poke","body":"抱抱","fromName":"许愿树","createdAt":10},
            |{"id":"e2","type":"challenge_done","body":"","fromName":"许愿树","createdAt":20}],
            |"cursor":20,"hasMore":false}""".trimMargin()
        )

        val events = repo.pullEvents()

        assertEquals(2, events.size)
        assertEquals(CoupleEvent.TYPE_POKE, events[0].type)
        assertEquals("许愿树", events[0].fromName)
        assertEquals(CoupleEvent.TYPE_CHALLENGE_DONE, events[1].type)
        coVerify { prefs.setEventCursor(20L) }
    }

    @Test
    fun `未登录时拉取事件返回空列表且不改变游标`() = runTest {
        coEvery { prefs.eventCursorSnapshot() } returns 0L
        coEvery { cloudAuth.ensureAccessToken() } returns null

        assertTrue(repo.pullEvents().isEmpty())
        coVerify(exactly = 0) { prefs.setEventCursor(any()) }
    }

    @Test
    fun `业务失败时不解析出挑战`() = runTest {
        stubToken()
        coEvery { api.call(any(), any(), any()) } returns CloudResponse(400, "请先绑定共同空间")

        assertNull(repo.getChallenge(100))
    }
}
