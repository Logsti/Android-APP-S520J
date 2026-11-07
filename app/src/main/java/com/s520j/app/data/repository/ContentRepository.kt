package com.s520j.app.data.repository

import com.s520j.app.data.local.ContentEntity
import com.s520j.app.data.local.OutboxEntity
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.local.toEntity
import com.s520j.app.data.local.toRecord
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import com.s520j.app.domain.heart.QuickRecordRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val db: S520JDatabase
) {
    private val contentDao get() = db.contentDao()

    fun observeModule(module: ContentModule): Flow<List<ContentRecord>> =
        contentDao.observeByModule(module.name).map { list -> list.map(ContentEntity::toRecord) }

    fun observeAll(): Flow<List<ContentRecord>> =
        contentDao.observeAll().map { list -> list.map(ContentEntity::toRecord) }

    fun observeRecycleBin(): Flow<List<ContentRecord>> =
        contentDao.observeRecycleBin().map { list -> list.map(ContentEntity::toRecord) }

    fun observeOutboxCount(): Flow<Int> = db.outboxDao().observeCount()

    suspend fun get(id: String): ContentRecord? = contentDao.findById(id)?.toRecord()

    suspend fun save(record: ContentRecord) {
        contentDao.upsert(record.copy(updatedAt = System.currentTimeMillis()).toEntity())
        db.outboxDao().enqueue(OutboxEntity(entityId = record.id, op = "UPSERT", createdAt = System.currentTimeMillis()))
    }

    /** 删除即进回收站（30 天规则在回收站用例中处理）。 */
    suspend fun softDelete(id: String) {
        val entity = contentDao.findById(id) ?: return
        contentDao.upsert(entity.copy(deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        db.outboxDao().enqueue(OutboxEntity(entityId = id, op = "SOFT_DELETE", createdAt = System.currentTimeMillis()))
    }

    suspend fun restore(id: String) {
        val entity = contentDao.findById(id) ?: return
        contentDao.upsert(entity.copy(deletedAt = null, updatedAt = System.currentTimeMillis()))
        db.outboxDao().enqueue(OutboxEntity(entityId = id, op = "RESTORE", createdAt = System.currentTimeMillis()))
    }

    /** 彻底删除需双方确认，本地 Mock 阶段直接落库并记录操作。 */
    suspend fun purge(id: String) {
        contentDao.hardDelete(id)
        db.outboxDao().enqueue(OutboxEntity(entityId = id, op = "PURGE", createdAt = System.currentTimeMillis()))
    }

    suspend fun count(): Int = contentDao.count()

    /** 一次性迁移：旧快速记录按类型路由，RECENT_MOMENT 整体搬入浪漫时刻。 */
    suspend fun migrateTaxonomyV2() {
        val originals = contentDao.listAll().map { it.toRecord() }
        val migrated = QuickRecordRouter.migrate(originals)
        val changed = migrated.filterIndexed { index, record ->
            record.module != originals[index].module || record.extra != originals[index].extra
        }
        if (changed.isNotEmpty()) {
            contentDao.upsertAll(changed.map { it.toEntity() })
        }
    }

    suspend fun seedIfEmpty(ownerId: String, ownerName: String) {
        if (contentDao.count() > 0) return
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        contentDao.upsertAll(SeedData.records(now, day, ownerId, ownerName).map { it.toEntity() })
    }
}

private object SeedData {
    fun records(now: Long, day: Long, ownerId: String, ownerName: String): List<ContentRecord> = listOf(
        rec(ContentModule.WISH, "去看一次海", "想和你一起在海边看日出。", now, ownerId, ownerName,
            eventDate = now + day * 20, extra = mapOf("提出人" to ownerName, "状态" to "待回应")),
        rec(ContentModule.WISH, "学会做提拉米苏", "一起做一次甜点。", now - day * 2, ownerId, ownerName,
            eventDate = now + day * 9, extra = mapOf("提出人" to "TA", "状态" to "已答应")),
        rec(ContentModule.CALENDAR_EVENT, "一起去看展", "周末下午，美术馆新展。", now, ownerId, ownerName,
            eventDate = now, extra = mapOf("type" to "共同计划")),
        rec(ContentModule.CALENDAR_EVENT, "纪念日", "认识的第 520 天。", now - day * 30, ownerId, ownerName,
            eventDate = now - day * 30, extra = mapOf("type" to "纪念日")),
        rec(ContentModule.ROMANTIC_MOMENT, "一起做晚饭", "番茄牛腩，厨房很暖。", now - day, ownerId, ownerName,
            eventDate = now - day),
        rec(ContentModule.ROMANTIC_MOMENT, "夜里的散步", "绕小区走了三圈。", now - day * 3, ownerId, ownerName,
            eventDate = now - day * 3),
        rec(ContentModule.TRAVEL, "海边的风", "第一次一起看海。", now - day * 5, ownerId, ownerName,
            eventDate = now - day * 5),
        rec(ContentModule.PHOTO, "窗台的绿植", "新买的小盆栽。", now - day * 8, ownerId, ownerName,
            eventDate = now - day * 8),
        rec(ContentModule.PHOTO, "生日蛋糕", "今年一起过的第一个生日。", now - day * 12, ownerId, ownerName,
            eventDate = now - day * 12),
        rec(ContentModule.PHOTO, "深夜的汤面", "楼下小店的宵夜。", now - day * 15, ownerId, ownerName,
            eventDate = now - day * 15),
        rec(ContentModule.DISH, "番茄牛腩", "小火炖了两个小时。", now - day, ownerId, ownerName,
            eventDate = now - day, extra = mapOf("星级" to "5", "是否再做" to "愿意", "榜单" to "松记")),
        rec(ContentModule.DISH, "提拉米苏", "第一次做甜点。", now - day * 6, ownerId, ownerName,
            eventDate = now - day * 6, extra = mapOf("星级" to "4", "是否再做" to "愿意", "榜单" to "锦记")),
        rec(ContentModule.DISH, "糖醋排骨", "收汁的时候特别香。", now - day * 9, ownerId, ownerName,
            eventDate = now - day * 9, extra = mapOf("星级" to "5", "榜单" to "锦记")),
        rec(ContentModule.SHOP, "转角咖啡", "手冲很稳，位置安静。", now - day * 4, ownerId, ownerName,
            eventDate = now - day * 4, extra = mapOf("地址" to "老街 12 号", "星级" to "5")),
        rec(ContentModule.SHOP, "小面馆", "浇头给得足。", now - day * 11, ownerId, ownerName,
            eventDate = now - day * 11, extra = mapOf("地址" to "小区后门", "星级" to "4")),
        rec(ContentModule.COLLECTED_MOMENT, "一起看的第一场雨", "伞太小，都淋湿了。", now - day * 7, ownerId, ownerName,
            eventDate = now - day * 7),
        rec(ContentModule.COLLECTED_MOMENT, "车站的告别", "站台上挥手很久。", now - day * 20, ownerId, ownerName,
            eventDate = now - day * 20),
        rec(ContentModule.IMPORTANT_DAY, "确认关系", "每年都要一起过。", now - day * 400, ownerId, ownerName,
            eventDate = now - day * 400, extra = mapOf("每年重复" to "是")),
        rec(ContentModule.PROMISE, "一起存一笔旅行基金", "每月各存一点。", now - day * 9, ownerId, ownerName,
            extra = mapOf("状态" to "进行中", "发起人" to ownerName)),
        rec(ContentModule.ROMANTIC_MOMENT, "凌晨的便利店", "买了两支雪糕。", now - day * 2, ownerId, ownerName,
            eventDate = now - day * 2),
        rec(ContentModule.RELATIONSHIP_RULE, "生气先说出来", "不要冷战，当晚说清楚。", now - day * 15, ownerId, ownerName,
            extra = mapOf("确认" to "双方已确认")),
        rec(ContentModule.LIKE, "喜欢的花：白色洋桔梗", "看到会买。", now - day * 30, ownerId, ownerName,
            visibility = Visibility.PRIVATE),
        rec(ContentModule.DISLIKE, "不喜欢香菜", "点菜要提前说。", now - day * 31, ownerId, ownerName,
            visibility = Visibility.PRIVATE),
        rec(ContentModule.NOTE, "关于搬家", "明年想换个有阳台的房子。", now - day * 5, ownerId, ownerName,
            visibility = Visibility.PRIVATE)
    )

    private fun rec(
        module: ContentModule,
        title: String,
        content: String,
        createdAt: Long,
        ownerId: String,
        ownerName: String,
        eventDate: Long? = null,
        extra: Map<String, String> = emptyMap(),
        visibility: Visibility = module.defaultVisibility
    ) = ContentRecord(
        id = "seed-${module.name.lowercase()}-${title.hashCode()}",
        module = module,
        spaceId = "space-local",
        ownerId = ownerId,
        ownerName = ownerName,
        visibility = visibility,
        title = title,
        content = content,
        eventDate = eventDate,
        mediaIds = emptyList(),
        extra = extra,
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        version = 1
    )
}
