package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule

/** 快速记录五入口：仅作采集类型，保存时路由到唯一归属模块。 */
object QuickRecordTypes {
    const val PHOTO = "照片"
    const val COOKING = "做饭"
    const val SHOP = "探店"
    const val TRAVEL = "旅行"
    const val ROMANTIC = "浪漫时刻"

    val all: List<String> = listOf(PHOTO, COOKING, SHOP, TRAVEL, ROMANTIC)

    /** 五种类型都必须带照片；类型级门禁只用于入口校验。 */
    fun needsPhoto(type: String?): Boolean = normalize(type) != null

    fun normalize(type: String?): String? = type?.trim()?.takeIf { it in all }

    /** 采集类型 → 唯一归属模块；未知类型返回 null。 */
    fun targetModuleOf(type: String?): ContentModule? = when (normalize(type)) {
        PHOTO -> ContentModule.PHOTO
        COOKING -> ContentModule.DISH
        SHOP -> ContentModule.SHOP
        TRAVEL -> ContentModule.TRAVEL
        ROMANTIC -> ContentModule.ROMANTIC_MOMENT
        else -> null
    }
}
