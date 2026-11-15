package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord

/** 采集类型 → 唯一归属模块；同时承担旧数据的一次性迁移。 */
object QuickRecordRouter {

    private const val LEGACY_TEXT_TYPE = "文字"
    private const val LEGACY_ROMANTIC_TYPE = "浪漫"

    /** 将一条快速记录转换为其目标模块；非快速记录返回 null。 */
    fun route(record: ContentRecord): ContentRecord? {
        if (record.module != ContentModule.QUICK_RECORD) return null
        return record.copy(
            module = targetForLegacyType(record.extra["type"]),
            extra = record.extra.filterKeys { it != "type" && it != "category" }
        )
    }

    /**
     * 一次性迁移：先把既有 RECENT_MOMENT 整体搬入 ROMANTIC_MOMENT，
     * 再把 QUICK_RECORD 按类型路由。保留 id / 时间 / 图片 / 可见性。
     */
    fun migrate(records: List<ContentRecord>): List<ContentRecord> = records.map { record ->
        when (record.module) {
            ContentModule.RECENT_MOMENT -> record.copy(module = ContentModule.ROMANTIC_MOMENT)
            ContentModule.QUICK_RECORD -> route(record) ?: record
            else -> record
        }
    }

    private fun targetForLegacyType(rawType: String?): ContentModule = when (rawType?.trim()) {
        QuickRecordTypes.PHOTO -> ContentModule.PHOTO
        QuickRecordTypes.COOKING -> ContentModule.DISH
        QuickRecordTypes.SHOP -> ContentModule.SHOP
        QuickRecordTypes.TRAVEL -> ContentModule.TRAVEL
        QuickRecordTypes.ROMANTIC, LEGACY_ROMANTIC_TYPE -> ContentModule.ROMANTIC_MOMENT
        QuickRecordTypes.JOT -> ContentModule.JOT
        LEGACY_TEXT_TYPE -> ContentModule.NOTE
        else -> ContentModule.RECENT_MOMENT
    }
}
