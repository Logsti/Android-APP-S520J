package com.s520j.app.data.sync

import com.s520j.app.data.cloud.CloudApi
import com.s520j.app.data.cloud.CloudRequest
import com.s520j.app.data.cloud.CloudResponse
import com.s520j.app.data.local.NotificationDao
import com.s520j.app.data.local.NotificationEntity
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.prefs.UserPreferences
import com.s520j.app.notifications.CoupleNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationSyncRepositoryTest {

    private val api: CloudApi = mockk()
    private val db: S520JDatabase = mockk()
    private val dao: NotificationDao = mockk()
    private val prefs: UserPreferences = mockk()
    private val notifier: CoupleNotifier = mockk(relaxUnitFun = true)

    private val stored = linkedMapOf<String, NotificationEntity>()
    private val baselines = mutableSetOf<String>()

    @Before
    fun setUp() {
        every { db.notificationDao() } returns dao
        coEvery { dao.insertIfAbsent(any()) } answers {
            val entity = firstArg<NotificationEntity>()
            if (stored.putIfAbsent(entity.id, entity) == null) 1L else -1L
        }
        coEvery { prefs.hasNotificationBaseline(any()) } answers { firstArg() in baselines }
        coEvery { prefs.markNotificationBaseline(any()) } answers {
            baselines += firstArg<String>()
            Unit
        }
    }

    private fun repository() = NotificationSyncRepository(api, db, prefs, notifier)

    private fun respond(items: String) {
        coEvery {
            api.call("api/notification", "Bearer token", match { it.action == "notification.list" })
        } returns CloudResponse(0, "", Json.parseToJsonElement("""{"items":$items}"""))
    }

    @Test
    fun `设备账号首次拉取只归档已读历史且不发送系统通知`() = runTest {
        respond("""[{"id":"ntf-old","title":"旧心愿","body":"历史动态","read":false,"createdAt":123,"type":"wish.proposed"}]""")

        repository().pull("token", "user-a")

        assertTrue(baselines.contains("user-a"))
        assertTrue(stored.getValue("ntf-old").read)
        assertEquals(123L, stored.getValue("ntf-old").createdAt)
        verify(exactly = 0) { notifier.notifyUpdate(any(), any(), any()) }
        coVerify {
            api.call("api/notification", "Bearer token", match<CloudRequest> {
                it.action == "notification.list"
            })
        }
    }

    @Test
    fun `基线后新事件同ID同文案进入未读和系统通知且重复拉取不重复`() = runTest {
        baselines += "user-a"
        respond("""[{"id":"ntf-new","title":"心愿已答应","body":"小红答应了心愿","read":true,"createdAt":456,"type":"wish.accepted"}]""")

        repository().pull("token", "user-a")
        repository().pull("token", "user-a")

        val local = stored.getValue("ntf-new")
        assertFalse("云端 read 不得覆盖基线后的本机未读", local.read)
        assertEquals(1, stored.size)
        verify(exactly = 1) { notifier.notifyUpdate("ntf-new", "心愿已答应", "小红答应了心愿") }
    }

    @Test
    fun `本机已读不被云端覆盖且重建仓库后仍不重复提醒`() = runTest {
        baselines += "user-a"
        stored["ntf-read"] = NotificationEntity("ntf-read", "原标题", "原正文", 100L, read = true)
        respond("""[{"id":"ntf-read","title":"云端标题","body":"云端正文","read":false,"createdAt":100,"type":"wish.proposed"}]""")

        repository().pull("token", "user-a")
        repository().pull("token", "user-a")

        assertEquals(NotificationEntity("ntf-read", "原标题", "原正文", 100L, read = true), stored["ntf-read"])
        verify(exactly = 0) { notifier.notifyUpdate(any(), any(), any()) }
    }

    @Test
    fun `同一设备不同账号分别建立首次基线`() = runTest {
        baselines += "user-a"
        respond("""[{"id":"ntf-b","title":"空间动态","body":"绑定申请","read":false,"createdAt":1,"type":"space.bind_requested"}]""")

        repository().pull("token", "user-b")

        assertTrue(stored.getValue("ntf-b").read)
        assertTrue(baselines.containsAll(setOf("user-a", "user-b")))
        verify(exactly = 0) { notifier.notifyUpdate(any(), any(), any()) }
    }

    @Test
    fun `本地异常提醒按相同ID去重并保留应用内未读`() = runTest {
        val repo = repository()

        repo.showLocal("local-sync-1", "同步需要检查", "连续失败，请检查网络", 900L)
        repo.showLocal("local-sync-1", "同步需要检查", "连续失败，请检查网络", 900L)

        assertFalse(stored.getValue("local-sync-1").read)
        verify(exactly = 1) { notifier.notifyUpdate("local-sync-1", "同步需要检查", "连续失败，请检查网络") }
    }
}
