package com.s520j.app.domain.tacit

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.ModuleGroup
import com.s520j.app.data.model.Visibility
import com.s520j.app.domain.heart.PhotoFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterBoardTest {

    private val me = "user-me"
    private val partner = "user-partner"

    @Test
    fun `新增默认写给伴侣且只允许保存TO或FROM`() {
        assertEquals(LetterBoard.TO, LetterBoard.directionForSave(null, LetterBoard.TO))
        assertEquals(LetterBoard.FROM, LetterBoard.directionForSave(null, LetterBoard.FROM))
        assertEquals(LetterBoard.TO, LetterBoard.directionForSave(null, "UNKNOWN"))
        assertEquals(LetterBoard.TO, LetterBoard.directionOf(letter()))
    }

    @Test
    fun `创建者与对方按各自视角看到相反方向及真实昵称`() {
        val toPartner = letter(ownerId = me, ownerName = "小明", direction = LetterBoard.TO)
        val fromPartner = letter(ownerId = me, ownerName = "小明", direction = LetterBoard.FROM)

        assertEquals("写给 小锦鲤", LetterBoard.directionLabel(toPartner, me, "小锦鲤"))
        assertEquals("来自 小明", LetterBoard.directionLabel(toPartner, partner, "任意"))
        assertEquals("来自 小锦鲤", LetterBoard.directionLabel(fromPartner, me, "小锦鲤"))
        assertEquals("写给 小明", LetterBoard.directionLabel(fromPartner, partner, "任意"))
    }

    @Test
    fun `未绑定或创建者昵称为空时使用TA兜底`() {
        val mine = letter(ownerId = me, direction = LetterBoard.TO)
        val theirs = letter(ownerId = partner, ownerName = "", direction = LetterBoard.TO)

        assertEquals("写给 TA", LetterBoard.directionLabel(mine, me, ""))
        assertEquals("来自 TA", LetterBoard.directionLabel(theirs, me, "小锦鲤"))
        assertEquals("全部", LetterBoard.filterLabel(LetterBoard.ALL, "小锦鲤"))
        assertEquals("写给 TA", LetterBoard.filterLabel(LetterBoard.TO, ""))
    }

    @Test
    fun `筛选使用查看者方向并按信件日期倒序`() {
        val records = listOf(
            letter(id = "old", ownerId = me, direction = LetterBoard.TO, createdAt = 1L, eventDate = 10L),
            letter(id = "incoming", ownerId = partner, direction = LetterBoard.TO, createdAt = 4L, eventDate = 40L),
            letter(id = "new", ownerId = me, direction = LetterBoard.FROM, createdAt = 2L, eventDate = 30L),
            letter(id = "outgoing", ownerId = partner, direction = LetterBoard.FROM, createdAt = 3L, eventDate = 20L),
            letter(id = "fallback", ownerId = me, direction = null, createdAt = 50L, eventDate = null)
        )

        assertEquals(
            listOf("fallback", "incoming", "new", "outgoing", "old"),
            LetterBoard.letters(records, me, LetterBoard.ALL).map { it.id }
        )
        assertEquals(
            listOf("fallback", "outgoing", "old"),
            LetterBoard.letters(records, me, LetterBoard.TO).map { it.id }
        )
        assertEquals(
            listOf("incoming", "new"),
            LetterBoard.letters(records, me, LetterBoard.FROM).map { it.id }
        )
    }

    @Test
    fun `列表只接收情书且编辑不因当前选择翻转原始方向`() {
        val outgoing = letter(ownerId = me, direction = LetterBoard.TO)
        val incoming = letter(ownerId = me, direction = LetterBoard.FROM)

        assertEquals(LetterBoard.TO, LetterBoard.directionForSave(outgoing, LetterBoard.FROM))
        assertEquals(LetterBoard.FROM, LetterBoard.directionForSave(incoming, LetterBoard.TO))
        assertTrue(LetterBoard.letters(listOf(outgoing), partner, LetterBoard.ALL).size == 1)
        assertTrue(LetterBoard.letters(listOf(outgoing.copy(module = ContentModule.JOT)), partner, LetterBoard.ALL).isEmpty())
    }

    @Test
    fun `LETTER属于默契共同可见带图模块且不进入照片墙`() {
        assertEquals(ModuleGroup.TACIT, ContentModule.LETTER.group)
        assertEquals(Visibility.COUPLE, ContentModule.LETTER.defaultVisibility)
        assertTrue(ContentModule.LETTER.needsPhoto)
        assertFalse(ContentModule.LETTER in PhotoFeed.sources)
        assertNull(PhotoFeed.categoryOf(letter()))
        assertTrue(PhotoFeed.feed(listOf(letter(mediaIds = listOf("photo")))).isEmpty())
    }

    private fun letter(
        id: String = "letter-1",
        ownerId: String = me,
        ownerName: String = "小明",
        direction: String? = null,
        createdAt: Long = 0L,
        eventDate: Long? = null,
        mediaIds: List<String> = emptyList()
    ): ContentRecord = ContentRecord(
        id = id,
        module = ContentModule.LETTER,
        spaceId = "space-1",
        ownerId = ownerId,
        ownerName = ownerName,
        visibility = Visibility.COUPLE,
        title = id,
        content = "正文",
        eventDate = eventDate,
        mediaIds = mediaIds,
        extra = direction?.let { mapOf(LetterBoard.KEY_DIRECTION to it) }.orEmpty(),
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        version = 1
    )
}
