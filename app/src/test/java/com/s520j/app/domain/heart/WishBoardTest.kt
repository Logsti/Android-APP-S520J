package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 心愿状态机：迁移守卫、视角分组、旧数据「提出人」解析。 */
class WishBoardTest {

    private val selfId = "u-me"
    private val partnerId = "u-partner"

    // ---------- 合法迁移 ----------

    @Test
    fun `待回应由非提出人答应进入已答应并写入答应人`() {
        val wish = wish(status = WishBoard.PENDING, proposer = partnerId)

        val updated = WishBoard.accept(wish, selfId, now = 100L)

        assertEquals(WishBoard.ACCEPTED, WishBoard.statusOf(updated))
        assertEquals(selfId, updated.extra[WishBoard.KEY_ACCEPTER])
        assertEquals(partnerId, updated.extra[WishBoard.KEY_PROPOSER])
        assertEquals(100L, updated.updatedAt)
        // version 是服务端下发的乐观锁基线，本地迁移不得自增，否则推送会被判冲突。
        assertEquals(wish.version, updated.version)
    }

    @Test
    fun `待回应由非提出人拒绝进入被拒绝`() {
        val wish = wish(status = WishBoard.PENDING, proposer = partnerId)

        val updated = WishBoard.reject(wish, selfId, now = 200L)

        assertEquals(WishBoard.REJECTED, WishBoard.statusOf(updated))
        assertEquals(200L, updated.updatedAt)
        assertEquals(wish.extra[WishBoard.KEY_PROPOSER], updated.extra[WishBoard.KEY_PROPOSER])
        assertEquals(wish.version, updated.version)
    }

    @Test
    fun `已答应可以标记实现`() {
        val wish = wish(status = WishBoard.ACCEPTED, proposer = partnerId)

        val updated = WishBoard.realize(wish, now = 300L)

        assertEquals(WishBoard.REALIZED, WishBoard.statusOf(updated))
        assertEquals(300L, updated.updatedAt)
        assertEquals(wish.version, updated.version)
    }

    // ---------- 非法迁移 ----------

    @Test
    fun `提出人不能自己答应或拒绝`() {
        val wish = wish(status = WishBoard.PENDING, proposer = selfId)

        assertTrue(WishBoard.accept(wish, selfId) === wish)
        assertTrue(WishBoard.reject(wish, selfId) === wish)
        assertEquals(WishBoard.PENDING, WishBoard.statusOf(WishBoard.accept(wish, selfId)))
    }

    @Test
    fun `待回应不能直接标记实现`() {
        val wish = wish(status = WishBoard.PENDING, proposer = partnerId)

        assertTrue(WishBoard.realize(wish) === wish)
    }

    @Test
    fun `已实现与被拒绝不再接受任何变更`() {
        val realized = wish(status = WishBoard.REALIZED, proposer = partnerId)
        val rejected = wish(status = WishBoard.REJECTED, proposer = partnerId)

        assertTrue(WishBoard.accept(realized, selfId) === realized)
        assertTrue(WishBoard.reject(realized, selfId) === realized)
        assertTrue(WishBoard.realize(realized) === realized)

        assertTrue(WishBoard.accept(rejected, selfId) === rejected)
        assertTrue(WishBoard.reject(rejected, selfId) === rejected)
        assertTrue(WishBoard.realize(rejected) === rejected)
    }

    @Test
    fun `守卫判断与状态一致`() {
        val byPartner = wish("对方提出", WishBoard.PENDING, partnerId)
        val byMe = wish("我提出", WishBoard.PENDING, selfId)
        val accepted = wish("已答应", WishBoard.ACCEPTED, partnerId)
        val rejected = wish("被拒绝", WishBoard.REJECTED, partnerId)

        assertTrue(WishBoard.canAccept(byPartner, selfId))
        assertFalse(WishBoard.canAccept(byMe, selfId))
        assertFalse(WishBoard.canAccept(accepted, selfId))
        assertTrue(WishBoard.canRealize(accepted))
        assertFalse(WishBoard.canRealize(byPartner))
        assertFalse(WishBoard.canRealize(rejected))
    }

    // ---------- 视角分组 ----------

    @Test
    fun `待回应按提出人拆成待我回应与待对方回应`() {
        val byPartner = wish("对方提出", WishBoard.PENDING, partnerId)
        val byMe = wish("我提出", WishBoard.PENDING, selfId)

        assertEquals(WishBoard.WAITING_ME, WishBoard.viewOf(byPartner, selfId))
        assertEquals(WishBoard.WAITING_PARTNER, WishBoard.viewOf(byMe, selfId))
    }

    @Test
    fun `五个视角分组齐备且计数正确`() {
        val records = listOf(
            wish("待我回应", WishBoard.PENDING, partnerId),
            wish("待对方回应", WishBoard.PENDING, selfId),
            wish("已答应", WishBoard.ACCEPTED, selfId),
            wish("已实现", WishBoard.REALIZED, partnerId),
            wish("失效", WishBoard.REJECTED, partnerId)
        )

        val grouped = WishBoard.groupByView(records, selfId)

        assertEquals(WishBoard.views, grouped.keys.toList())
        assertEquals(listOf("待我回应"), grouped[WishBoard.WAITING_ME]?.map { it.title })
        assertEquals(listOf("待对方回应"), grouped[WishBoard.WAITING_PARTNER]?.map { it.title })
        assertEquals(listOf("已答应"), grouped[WishBoard.ACCEPTED]?.map { it.title })
        assertEquals(listOf("已实现"), grouped[WishBoard.REALIZED]?.map { it.title })
        assertEquals(listOf("失效"), grouped[WishBoard.INVALID]?.map { it.title })
        assertTrue(WishBoard.groupByView(emptyList(), selfId).values.all { it.isEmpty() })
    }

    @Test
    fun `默认视角优先待我回应再待对方回应最后已答应`() {
        val waitingMe = wish("a", WishBoard.PENDING, partnerId)
        val waitingPartner = wish("b", WishBoard.PENDING, selfId)
        val accepted = wish("c", WishBoard.ACCEPTED, selfId)

        assertEquals(
            WishBoard.WAITING_ME,
            WishBoard.defaultView(WishBoard.groupByView(listOf(waitingMe, waitingPartner, accepted), selfId))
        )
        assertEquals(
            WishBoard.WAITING_PARTNER,
            WishBoard.defaultView(WishBoard.groupByView(listOf(waitingPartner, accepted), selfId))
        )
        assertEquals(
            WishBoard.ACCEPTED,
            WishBoard.defaultView(WishBoard.groupByView(listOf(accepted), selfId))
        )
        assertEquals(WishBoard.ACCEPTED, WishBoard.defaultView(emptyMap()))
    }

    // ---------- 旧数据「提出人」解析 ----------

    @Test
    fun `旧数据我或空回退记录归属人`() {
        val mine = wish("我提出", WishBoard.PENDING, "我", ownerId = selfId)
        val blank = wish("没说谁提", WishBoard.PENDING, "", ownerId = selfId)
        val legacy = wish("旧数据", WishBoard.PENDING, "我", ownerId = partnerId)

        assertTrue(WishBoard.isProposedBySelf(mine, selfId))
        assertEquals(selfId, WishBoard.proposerIdOf(mine, selfId))
        assertTrue(WishBoard.isProposedBySelf(blank, selfId))
        assertEquals(selfId, WishBoard.proposerIdOf(blank, selfId))
        // 旧值「我」跟随 ownerId：对方创建的记录仍然视为对方提出。
        assertFalse(WishBoard.isProposedBySelf(legacy, selfId))
        assertEquals(partnerId, WishBoard.proposerIdOf(legacy, selfId))
        assertEquals(WishBoard.WAITING_ME, WishBoard.viewOf(legacy, selfId))
    }

    @Test
    fun `旧数据TA始终视为对方`() {
        val wish = wish("TA 提出", WishBoard.PENDING, "TA", ownerId = selfId)

        assertFalse(WishBoard.isProposedBySelf(wish, selfId))
        assertEquals(WishBoard.WAITING_ME, WishBoard.viewOf(wish, selfId))
        assertEquals("小锦鲤", WishBoard.proposerLabel(wish, selfId, "小锦鲤"))
    }

    @Test
    fun `新数据按 userId 直接比较`() {
        val mine = wish("我提出", WishBoard.PENDING, selfId, ownerId = selfId)
        val partner = wish("TA 提出", WishBoard.PENDING, partnerId, ownerId = partnerId)

        assertTrue(WishBoard.isProposedBySelf(mine, selfId))
        assertFalse(WishBoard.isProposedBySelf(partner, selfId))
        assertEquals("我", WishBoard.proposerLabel(mine, selfId, "小锦鲤"))
        assertEquals("小锦鲤", WishBoard.proposerLabel(partner, selfId, "小锦鲤"))
    }

    @Test
    fun `答应人展示按当前用户判定`() {
        val mine = wish("我答应", WishBoard.ACCEPTED, partnerId).copy(
            extra = mapOf(
                WishBoard.KEY_STATUS to WishBoard.ACCEPTED,
                WishBoard.KEY_PROPOSER to partnerId,
                WishBoard.KEY_ACCEPTER to selfId
            )
        )
        val partnerAccepted = wish("TA 答应", WishBoard.ACCEPTED, selfId)

        assertEquals("我", WishBoard.accepterLabel(mine, selfId, "小锦鲤"))
        assertEquals("小锦鲤", WishBoard.accepterLabel(partnerAccepted, selfId, "小锦鲤"))
    }

    // ---------- 历史 API ----------

    @Test
    fun `withStatus 只写状态不改版本`() {
        val wish = wish("旧入口", WishBoard.ACCEPTED, partnerId, version = 2)

        val realized = WishBoard.withStatus(wish, WishBoard.REALIZED)

        assertEquals(WishBoard.REALIZED, WishBoard.statusOf(realized))
        assertEquals(partnerId, realized.extra[WishBoard.KEY_PROPOSER])
        assertEquals(wish.version, realized.version)
        assertEquals(WishBoard.PENDING, WishBoard.statusOf(WishBoard.withStatus(wish, "随便写")))
    }

    @Test
    fun `业务状态为四项且包含被拒绝`() {
        assertEquals(listOf("待回应", "已答应", "已实现", "被拒绝"), WishBoard.statuses)
        assertEquals(WishBoard.REJECTED, WishBoard.statuses.last())
        assertEquals(WishBoard.PENDING, WishBoard.statusOf(wish("无状态", "未知值", partnerId)))
    }

    private fun wish(
        title: String = "看海",
        status: String,
        proposer: String,
        ownerId: String = selfId,
        version: Int = 1
    ): ContentRecord = ContentRecord(
        id = "wish-$title",
        module = ContentModule.WISH,
        spaceId = "space-local",
        ownerId = ownerId,
        ownerName = "小明",
        visibility = Visibility.COUPLE,
        title = title,
        content = "",
        eventDate = null,
        mediaIds = emptyList(),
        extra = mapOf(
            WishBoard.KEY_STATUS to status,
            WishBoard.KEY_PROPOSER to proposer
        ),
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        version = version
    )
}
