package com.s520j.app.domain.food

/**
 * 客户端菜谱分类镜像：与云端 `dishTaxonomy.js` 保持完全一致。
 *
 * 只按 ShowAPI 官方一级/二级分类做确定性映射，不按菜名、口味或食材猜测；
 * 同一道菜命中多组时按「汤 ＞ 荤菜 ＞ 素菜 ＞ 主食」只取一组。
 */
enum class DishGroup(val key: String, val label: String) {
    MEAT("meat", "荤菜"),
    VEG("veg", "素菜"),
    SOUP("soup", "汤"),
    STAPLE("staple", "主食");

    companion object {
        /** 展示与请求都用这个顺序：荤菜、素菜、汤、主食。 */
        val order: List<DishGroup> = listOf(MEAT, VEG, SOUP, STAPLE)

        fun fromKey(key: String?): DishGroup? =
            entries.firstOrNull { it.key == key?.trim() }
    }
}

object DishTaxonomy {

    /** 二级分类筛选里的「全部」。 */
    const val ALL = "全部"

    private val soupTags = setOf("汤", "汤羹")
    private val meatLevel1 = setOf("肉类", "水产")
    private val vegLevel1 = setOf("蔬菜水果", "蛋奶豆制品")
    private val stapleTags = setOf("米面类", "饼", "馒头", "馄饨", "粥", "面条", "饭", "饺子", "包子", "三明治")

    /** 官方分类树兜底表：接口不可用时保证点菜仍可用。 */
    val builtinTaxonomy: Map<String, List<String>> = linkedMapOf(
        "肉类" to listOf(
            "猪", "牛", "羊", "鸡", "鸭", "鹅", "鸽子", "鹌鹑", "火鸡",
            "兔肉", "驴肉", "鹿肉", "牛蛙", "田鸡", "雪蛤"
        ),
        "水产" to listOf("鱼", "虾", "螃蟹", "贝", "其他水产"),
        "蔬菜水果" to listOf("叶类蔬菜", "根茎蔬菜", "果实类蔬菜", "菌菇类", "花朵植物类", "水果"),
        "蛋奶豆制品" to listOf("蛋类", "豆制品", "奶制品"),
        "汤粥主食" to listOf("饼", "馒头", "馄饨", "粥", "面条", "饭", "汤羹", "饺子", "汤", "包子", "三明治"),
        "米面干果腌咸" to listOf("干果类", "米面类", "腌咸制品"),
        "烘焙甜品饮料" to listOf("甜品", "饮品", "烘焙"),
        "热门专题" to listOf("特色食品", "菜式", "特殊场合", "视频专题", "功效", "人群"),
        "口味特色" to listOf("异国风味", "口味", "器具", "烹调方法")
    )

    private val level2Level1: Map<String, String> = buildMap {
        builtinTaxonomy.forEach { (level1, list) -> list.forEach { put(it, level1) } }
    }

    /**
     * 四组可用二级分类兜底：与云端 `groupsFromTaxonomy(BUILTIN_TAXONOMY)` 的顺序一致。
     * 云端顺序来自对象遍历顺序，主食里「米面类」排在一级分类「米面干果腌咸」的位置。
     */
    val fallbackCategories: Map<DishGroup, List<String>> = mapOf(
        DishGroup.MEAT to listOf(
            "猪", "牛", "羊", "鸡", "鸭", "鹅", "鸽子", "鹌鹑", "火鸡",
            "兔肉", "驴肉", "鹿肉", "牛蛙", "田鸡", "雪蛤",
            "鱼", "虾", "螃蟹", "贝", "其他水产"
        ),
        DishGroup.VEG to listOf(
            "叶类蔬菜", "根茎蔬菜", "果实类蔬菜", "菌菇类", "花朵植物类", "水果",
            "蛋类", "豆制品", "奶制品"
        ),
        DishGroup.SOUP to listOf("汤", "汤羹"),
        DishGroup.STAPLE to listOf(
            "饼", "馒头", "馄饨", "粥", "面条", "饭", "饺子", "包子", "三明治", "米面类"
        )
    )

    /** 二级分类 → 四组；烘焙甜品饮料、干果类、腌咸制品等返回 null。 */
    fun level2ToGroup(level2: String?): DishGroup? {
        val name = level2?.trim().orEmpty()
        if (name.isEmpty()) return null
        if (name in soupTags) return DishGroup.SOUP
        if (name in stapleTags) return DishGroup.STAPLE
        val level1 = level2Level1[name] ?: return null
        return when {
            level1 in meatLevel1 -> DishGroup.MEAT
            level1 in vegLevel1 -> DishGroup.VEG
            else -> null
        }
    }

    /** 多标签取组：汤 ＞ 荤菜 ＞ 素菜 ＞ 主食。 */
    fun groupForTags(tags: List<String>): DishGroup? {
        val hit = tags.mapNotNull { level2ToGroup(it) }.toSet()
        return listOf(DishGroup.SOUP, DishGroup.MEAT, DishGroup.VEG, DishGroup.STAPLE)
            .firstOrNull { it in hit }
    }

    /**
     * 云端下发的四组二级分类，空组回退内置兜底。
     * 传入 Map<String, List<String>>（key 为组 key），未知组忽略。
     */
    fun categoriesFrom(raw: Map<String, List<String>>?): Map<DishGroup, List<String>> {
        val result = linkedMapOf<DishGroup, List<String>>()
        DishGroup.order.forEach { group ->
            val list = raw?.get(group.key).orEmpty().filter { it.isNotBlank() }.distinct()
            result[group] = if (list.isEmpty()) fallbackCategories.getValue(group) else list
        }
        return result
    }

    /** 界面筛选顺序：全部 + 该组可用二级分类。 */
    fun optionsFor(group: DishGroup, categories: Map<DishGroup, List<String>> = fallbackCategories): List<String> =
        listOf(ALL) + categories[group].orEmpty()

    fun subCategoryOf(group: DishGroup, subCategory: String?): String =
        subCategory?.trim().orEmpty().ifEmpty { ALL }
}
