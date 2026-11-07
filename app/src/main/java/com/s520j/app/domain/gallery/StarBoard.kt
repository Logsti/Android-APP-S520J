package com.s520j.app.domain.gallery

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord

/** 双五星榜：只记录自己做的饭菜，探店不计入。 */
object StarBoard {
    const val SONG = "松记"
    const val JIN = "锦记"
    const val KEY = "榜单"
    const val RATING_KEY = "星级"

    val boards: List<String> = listOf(SONG, JIN)

    fun boardOf(record: ContentRecord): String =
        record.extra[KEY]?.takeIf { it in boards } ?: SONG

    fun ratingOf(record: ContentRecord): Int =
        record.extra[RATING_KEY]?.toIntOrNull() ?: 0

    fun isFiveStarDish(record: ContentRecord): Boolean =
        record.module == ContentModule.DISH && ratingOf(record) == 5

    fun list(records: List<ContentRecord>, board: String): List<ContentRecord> =
        records
            .filter { isFiveStarDish(it) && boardOf(it) == board }
            .sortedByDescending { it.eventDate ?: it.createdAt }

    fun count(records: List<ContentRecord>, board: String): Int = list(records, board).size
}
