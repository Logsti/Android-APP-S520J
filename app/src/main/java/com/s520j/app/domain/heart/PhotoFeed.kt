package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord

data class SharedPhoto(
    val key: String,
    val image: String
)

/** 拾光照片墙：分类完全由模块派生，聚合五个带图来源。 */
object PhotoFeed {
    const val ALL = "全部"
    const val COOKING = "做饭"
    const val SHOP = "探店"
    const val TRAVEL = "旅行"
    const val DAILY = "日常"
    const val ROMANTIC = "浪漫时刻"

    val categories: List<String> = listOf(ALL, COOKING, SHOP, TRAVEL, DAILY, ROMANTIC)

    val sources: List<ContentModule> = listOf(
        ContentModule.PHOTO,
        ContentModule.DISH,
        ContentModule.SHOP,
        ContentModule.TRAVEL,
        ContentModule.ROMANTIC_MOMENT
    )

    fun categoryOf(record: ContentRecord): String? = when (record.module) {
        ContentModule.PHOTO -> DAILY
        ContentModule.DISH -> COOKING
        ContentModule.SHOP -> SHOP
        ContentModule.TRAVEL -> TRAVEL
        ContentModule.ROMANTIC_MOMENT -> ROMANTIC
        else -> null
    }

    /** 分类 → 对应模块，供 tab 直达与空状态跳转；「全部」不指向单一模块。 */
    fun moduleOf(category: String): ContentModule? = when (category) {
        COOKING -> ContentModule.DISH
        SHOP -> ContentModule.SHOP
        TRAVEL -> ContentModule.TRAVEL
        DAILY -> ContentModule.PHOTO
        ROMANTIC -> ContentModule.ROMANTIC_MOMENT
        else -> null
    }

    /**
     * 照片墙排序键：记录时间用 createdAt；填写日期用 eventDate，没填写的回落到 createdAt，
     * 避免历史记录因为缺日期被挤到最前或最后。
     */
    fun sortKeyOf(record: ContentRecord, byEventDate: Boolean): Long =
        if (byEventDate) record.eventDate ?: record.createdAt else record.createdAt

    /**
     * 照片墙列表：默认按记录时间倒序（最新在前）。
     * byEventDate 为 true 时按填写的日期排序，ascending 为 true 时正序（最早在前）。
     */
    fun feed(
        records: List<ContentRecord>,
        byEventDate: Boolean = false,
        ascending: Boolean = false
    ): List<ContentRecord> {
        val sorted = records
            .filter { it.module in sources && it.mediaIds.isNotEmpty() }
            .sortedWith(compareBy({ sortKeyOf(it, byEventDate) }, { it.id }))
        return if (ascending) sorted else sorted.reversed()
    }

    fun sharedPhotos(records: List<ContentRecord>): List<SharedPhoto> =
        feed(records).flatMap { record ->
            record.mediaIds.mapIndexed { index, image ->
                SharedPhoto(key = "${record.id}#$index", image = image)
            }
        }

    fun filter(feed: List<ContentRecord>, category: String): List<ContentRecord> =
        if (category == ALL) feed else feed.filter { categoryOf(it) == category }

    /** 照片墙搜索：空关键词原样返回；命中标题或内容，忽略大小写。 */
    fun search(records: List<ContentRecord>, query: String): List<ContentRecord> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return records
        return records.filter {
            it.title.contains(keyword, ignoreCase = true) || it.content.contains(keyword, ignoreCase = true)
        }
    }
}
