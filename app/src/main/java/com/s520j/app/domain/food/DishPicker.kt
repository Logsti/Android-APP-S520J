package com.s520j.app.domain.food

import kotlin.random.Random

/** 菜品来源：实时抽页 / 云端池子 / 自建内置库，界面必须显式标注，不能把降级数据伪装成实时。 */
enum class DishSource(val key: String, val label: String) {
    LIVE("live", "实时"),
    POOL("pool", "云端池"),
    BUILTIN("builtin", "内置");

    companion object {
        fun fromKey(key: String?): DishSource =
            entries.firstOrNull { it.key == key?.trim() } ?: BUILTIN
    }
}

data class DishCandidate(
    val name: String,
    val level1: String = "",
    val level2: String = "",
    val group: DishGroup,
    val source: DishSource = DishSource.LIVE
) {
    /** 结果卡片标签：「二级分类 · 分组 · 来源」。 */
    val tag: String
        get() = listOf(level2, group.label, source.label).filter { it.isNotBlank() }.joinToString(" · ")
}

data class DishGroupSelection(
    val group: DishGroup,
    val count: Int,
    val subCategory: String = DishTaxonomy.ALL
)

/** `dish.pick` 请求体（由数据层序列化，领域层保持纯逻辑）。 */
data class DishPickRequest(
    val groups: List<DishGroupSelection>,
    val exclude: List<String> = emptyList(),
    val recent: List<String> = emptyList()
)

data class DishPickResult(
    val items: List<DishCandidate>,
    val quotaRemaining: Int = 0,
    val degraded: Boolean = false,
    val note: String? = null
)

/** 一次点菜的不可变状态：份数、组内筛选、当前一桌、候选池、需要避开的菜名。 */
data class DishPickerState(
    val counts: Map<DishGroup, Int> = DishGroup.order.associateWith { 1 },
    val subs: Map<DishGroup, String> = DishGroup.order.associateWith { DishTaxonomy.ALL },
    val preferNew: Boolean = true,
    val table: List<DishCandidate> = emptyList(),
    val haystack: List<DishCandidate> = emptyList(),
    val avoid: Set<String> = emptySet(),
    val note: String? = null,
    val degraded: Boolean = false,
    val quotaRemaining: Int? = null,
    val offline: Boolean = false
) {
    fun countOf(group: DishGroup): Int = (counts[group] ?: 0).coerceIn(0, DishPicker.MAX_PER_GROUP)

    fun subOf(group: DishGroup): String = subs[group] ?: DishTaxonomy.ALL

    val totalCount: Int get() = DishGroup.order.sumOf { countOf(it) }

    val hasResult: Boolean get() = table.isNotEmpty()
}

object DishPicker {
    const val MAX_PER_GROUP = 4
    const val MAX_EXCLUDE = 500

    private const val EMPTY_MESSAGE = "至少选择 1 道菜"

    fun initialState(preferNew: Boolean = true): DishPickerState =
        DishPickerState(preferNew = preferNew)

    /** 四组都为 0 时返回提示文案，可点菜时返回 null。 */
    fun validation(state: DishPickerState): String? =
        if (state.totalCount < 1) EMPTY_MESSAGE else null

    fun canPick(state: DishPickerState): Boolean = validation(state) == null

    fun updateCount(state: DishPickerState, group: DishGroup, count: Int): DishPickerState =
        clearResult(state).copy(counts = state.counts + (group to count.coerceIn(0, MAX_PER_GROUP)))

    /** 切换二级分类：该组筛选保留，旧结果清空。 */
    fun updateSub(state: DishPickerState, group: DishGroup, subCategory: String): DishPickerState =
        clearResult(state).copy(
            subs = state.subs + (group to DishTaxonomy.subCategoryOf(group, subCategory))
        )

    fun togglePreferNew(state: DishPickerState): DishPickerState =
        state.copy(preferNew = !state.preferNew)

    fun clearResult(state: DishPickerState): DishPickerState = state.copy(
        table = emptyList(),
        haystack = emptyList(),
        note = null,
        degraded = false,
        quotaRemaining = null,
        offline = false
    )

    /** 构建一次完整点菜请求：只带非零分组，排除当前桌与待避开的菜名。 */
    fun request(
        state: DishPickerState,
        exclude: List<String> = emptyList(),
        recent: List<String> = emptyList()
    ): DishPickRequest {
        val groups = DishGroup.order
            .filter { state.countOf(it) > 0 }
            .map { DishGroupSelection(it, state.countOf(it), state.subOf(it)) }
        val excluded = names(exclude + state.avoid + state.table.map { it.name })
        val recentNames = if (state.preferNew) names(recent).filterNot { it in excluded } else emptyList()
        return DishPickRequest(groups, excluded, recentNames)
    }

    /** 单道换菜补抽：只为该组请求 1 道，且避开桌上已有的菜。 */
    fun swapRequest(
        state: DishPickerState,
        target: DishCandidate,
        recent: List<String> = emptyList()
    ): DishPickRequest {
        val onTable = state.table.map { it.name }
        val excluded = names(onTable + state.avoid)
        return DishPickRequest(
            groups = listOf(DishGroupSelection(target.group, 1, state.subOf(target.group))),
            exclude = excluded,
            recent = if (state.preferNew) names(recent).filterNot { it in excluded } else emptyList()
        )
    }

    /** 应用云端 `dish.pick` 结果：按荤/素/汤/主食顺序落桌，并作为本次候选池。 */
    fun applyRemote(state: DishPickerState, result: DishPickResult): DishPickerState {
        val items = result.items.filter { it.name.isNotBlank() }.distinctBy { it.name }
        val ordered = orderByGroup(items)
        return state.copy(
            table = ordered,
            haystack = ordered,
            note = result.note,
            degraded = result.degraded,
            quotaRemaining = result.quotaRemaining,
            offline = false
        )
    }

    /**
     * 云端不可用时的最后一级兜底：只用内置 90 道凑一桌。
     * 份数不静默改变；凑不齐时在 note 里说明实际数量。
     */
    fun fillLocal(
        state: DishPickerState,
        candidates: List<DishCandidate>,
        random: Random = Random.Default,
        offline: Boolean = false
    ): DishPickerState {
        val used = mutableSetOf<String>()
        val picked = mutableListOf<DishCandidate>()
        DishGroup.order.forEach { group ->
            val need = state.countOf(group)
            if (need <= 0) return@forEach
            val sub = state.subOf(group)
            val pool = candidates.filter {
                it.group == group &&
                    it.name !in used &&
                    it.name !in state.avoid
            }
            val matched = if (sub == DishTaxonomy.ALL) pool else pool.filter { it.level2 == sub }
            val chosen = matched.shuffled(random).distinctBy { it.name }.take(need)
            picked += chosen
            used += chosen.map { it.name }
        }
        val table = orderByGroup(picked)
        val requested = state.totalCount
        val note = when {
            table.isEmpty() -> "暂无可用的菜品，请重试"
            table.size < requested -> "菜品范围有限，只凑出 ${table.size}/$requested 道"
            else -> null
        }
        return state.copy(
            table = table,
            haystack = table,
            note = note,
            degraded = true,
            quotaRemaining = 0,
            offline = offline
        )
    }

    /** 本次候选池里可以替换 target 的菜：同组、不在桌上、符合该组筛选。 */
    fun swapCandidates(state: DishPickerState, target: DishCandidate): List<DishCandidate> {
        val onTable = state.table.map { it.name }.toSet()
        val sub = state.subOf(target.group)
        return state.haystack
            .filter { it.group == target.group && it.name !in onTable && it.name !in state.avoid }
            .filter { sub == DishTaxonomy.ALL || it.level2 == sub }
            .distinctBy { it.name }
    }

    /** 优先从候选池换菜；候选不足返回 null，由调用方补抽 1 次。 */
    fun swapFromPool(
        state: DishPickerState,
        target: DishCandidate,
        random: Random = Random.Default
    ): DishPickerState? {
        val candidates = swapCandidates(state, target)
        if (candidates.isEmpty()) return null
        return replace(state, target, candidates.random(random))
    }

    /** 把补抽结果应用到某一格；没有可用新菜时返回 null。 */
    fun applySwapResult(
        state: DishPickerState,
        target: DishCandidate,
        items: List<DishCandidate>
    ): DishPickerState? {
        val onTable = state.table.map { it.name }.toSet()
        val replacement = items.firstOrNull {
            it.group == target.group && it.name.isNotBlank() && it.name !in onTable
        } ?: return null
        return replace(state, target, replacement)
    }

    fun replace(
        state: DishPickerState,
        target: DishCandidate,
        replacement: DishCandidate
    ): DishPickerState = state.copy(
        table = state.table.map { if (it.name == target.name) replacement else it },
        haystack = (state.haystack + replacement).distinctBy { it.name },
        avoid = state.avoid + target.name
    )

    /** 换一桌：清空结果并把当前这桌的菜加入避让集合，下次抽页尽量不重复。 */
    fun newTable(state: DishPickerState): DishPickerState =
        clearResult(state).copy(avoid = state.avoid + state.table.map { it.name })

    private fun orderByGroup(items: List<DishCandidate>): List<DishCandidate> =
        DishGroup.order.flatMap { group -> items.filter { it.group == group } }

    private fun names(values: List<String>): List<String> =
        values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_EXCLUDE)
}
