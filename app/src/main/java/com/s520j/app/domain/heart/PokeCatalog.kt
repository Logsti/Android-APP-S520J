package com.s520j.app.domain.heart

/** 自定义碰碰你动作的校验、增删与持久化编码。 */
object PokeCatalog {
    val defaults: List<String> = listOf("拍一拍", "抱抱", "亲亲", "想你")

    const val MAX_NAME_LENGTH = 8

    fun validate(existing: List<String>, candidate: String): PokeAddResult {
        val name = candidate.trim()
        return when {
            name.isEmpty() -> PokeAddResult.EMPTY
            name.length > MAX_NAME_LENGTH -> PokeAddResult.TOO_LONG
            name in defaults || name in existing -> PokeAddResult.DUPLICATE
            else -> PokeAddResult.OK
        }
    }

    fun add(existing: List<String>, candidate: String): List<String> =
        if (validate(existing, candidate) == PokeAddResult.OK) existing + candidate.trim() else existing

    fun remove(existing: List<String>, name: String): List<String> =
        existing.filterNot { it == name.trim() }

    fun encode(list: List<String>): String = list.joinToString("\n")

    fun decode(raw: String?): List<String> =
        raw?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()

    fun all(custom: List<String>): List<String> =
        defaults + custom.filter { it !in defaults }.distinct()
}

enum class PokeAddResult { EMPTY, TOO_LONG, DUPLICATE, OK }
