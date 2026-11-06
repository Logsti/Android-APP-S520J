package com.s520j.app.data.model

/** 可见范围：仅两档（技术文档最终确认摘要）。 */
enum class Visibility {
    PRIVATE,
    COUPLE;

    val label: String
        get() = if (this == PRIVATE) "仅自己可见" else "共同可见"

    val shortLabel: String
        get() = if (this == PRIVATE) "仅自己" else "共同"

    companion object {
        fun from(raw: String?): Visibility = if (raw == "COUPLE") COUPLE else PRIVATE
    }
}

enum class ModuleGroup(val label: String) {
    HEART("心动"),
    GALLERY("拾光"),
    TACIT("默契")
}

/** 内容模块注册表：统一驱动列表页/编辑页。 */
enum class ContentModule(
    val group: ModuleGroup,
    val label: String,
    val defaultVisibility: Visibility,
    val needsPhoto: Boolean = false,
    val supportsRating: Boolean = false
) {
    QUICK_RECORD(ModuleGroup.HEART, "快速记录", Visibility.COUPLE, needsPhoto = true),
    WISH(ModuleGroup.HEART, "心愿", Visibility.COUPLE),
    CALENDAR_EVENT(ModuleGroup.HEART, "日历事项", Visibility.COUPLE),
    RECENT_MOMENT(ModuleGroup.HEART, "最近的美好", Visibility.COUPLE, needsPhoto = true),

    PHOTO(ModuleGroup.GALLERY, "共同照片", Visibility.COUPLE, needsPhoto = true),
    DISH(ModuleGroup.GALLERY, "一起做过的菜", Visibility.COUPLE, needsPhoto = true, supportsRating = true),
    SHOP(ModuleGroup.GALLERY, "一起去过的店", Visibility.COUPLE, needsPhoto = true, supportsRating = true),
    COLLECTED_MOMENT(ModuleGroup.GALLERY, "收藏的瞬间", Visibility.COUPLE, needsPhoto = true),

    IMPORTANT_DAY(ModuleGroup.TACIT, "重要日子", Visibility.COUPLE, needsPhoto = true),
    PROMISE(ModuleGroup.TACIT, "彼此的承诺", Visibility.COUPLE),
    DEED(ModuleGroup.TACIT, "为彼此做过", Visibility.PRIVATE, needsPhoto = true),
    ROMANTIC_MOMENT(ModuleGroup.TACIT, "浪漫时刻", Visibility.COUPLE, needsPhoto = true),
    RELATIONSHIP_RULE(ModuleGroup.TACIT, "相处之道", Visibility.COUPLE),
    LIKE(ModuleGroup.TACIT, "TA 的喜欢", Visibility.PRIVATE),
    DISLIKE(ModuleGroup.TACIT, "TA 不喜欢", Visibility.PRIVATE),
    NOTE(ModuleGroup.TACIT, "我们的笔记", Visibility.PRIVATE);

    companion object {
        fun from(raw: String): ContentModule = entries.firstOrNull { it.name == raw } ?: NOTE
        fun ofGroup(group: ModuleGroup): List<ContentModule> = entries.filter { it.group == group }
    }
}

/** 与云端一致的内容记录（本地 Room 缓存投影）。 */
data class ContentRecord(
    val id: String,
    val module: ContentModule,
    val spaceId: String?,
    val ownerId: String,
    val ownerName: String,
    val visibility: Visibility,
    val title: String,
    val content: String,
    val eventDate: Long?,
    val mediaIds: List<String>,
    val extra: Map<String, String>,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val version: Int
)

/** 日历事项展示模型。 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val dateMillis: Long,
    val type: String,
    val note: String,
    val visibility: Visibility
)

/** 通知（仅 APP 内，无系统推送）。 */
data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean
)
