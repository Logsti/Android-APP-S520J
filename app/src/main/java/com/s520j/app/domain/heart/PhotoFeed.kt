package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord

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

    fun feed(records: List<ContentRecord>): List<ContentRecord> =
        records.filter { it.module in sources && it.mediaIds.isNotEmpty() }
            .sortedByDescending { it.createdAt }

    fun filter(feed: List<ContentRecord>, category: String): List<ContentRecord> =
        if (category == ALL) feed else feed.filter { categoryOf(it) == category }
}
