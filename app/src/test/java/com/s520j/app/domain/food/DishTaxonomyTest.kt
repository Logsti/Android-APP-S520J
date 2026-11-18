package com.s520j.app.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DishTaxonomyTest {

    @Test
    fun `二级分类映射到四组`() {
        assertEquals(DishGroup.MEAT, DishTaxonomy.level2ToGroup("猪"))
        assertEquals(DishGroup.MEAT, DishTaxonomy.level2ToGroup("其他水产"))
        assertEquals(DishGroup.VEG, DishTaxonomy.level2ToGroup("叶类蔬菜"))
        assertEquals(DishGroup.VEG, DishTaxonomy.level2ToGroup("奶制品"))
        assertEquals(DishGroup.SOUP, DishTaxonomy.level2ToGroup("汤"))
        assertEquals(DishGroup.SOUP, DishTaxonomy.level2ToGroup("汤羹"))
        assertEquals(DishGroup.STAPLE, DishTaxonomy.level2ToGroup("米面类"))
        assertEquals(DishGroup.STAPLE, DishTaxonomy.level2ToGroup("三明治"))
    }

    @Test
    fun `烘焙甜品干果腌咸不参与随机`() {
        assertNull(DishTaxonomy.level2ToGroup("烘焙"))
        assertNull(DishTaxonomy.level2ToGroup("甜品"))
        assertNull(DishTaxonomy.level2ToGroup("饮品"))
        assertNull(DishTaxonomy.level2ToGroup("干果类"))
        assertNull(DishTaxonomy.level2ToGroup("腌咸制品"))
        assertNull(DishTaxonomy.level2ToGroup("口味"))
        assertNull(DishTaxonomy.level2ToGroup(""))
        assertNull(DishTaxonomy.level2ToGroup(null))
    }

    @Test
    fun `多标签按汤荤素主食优先级取一组`() {
        assertEquals(DishGroup.SOUP, DishTaxonomy.groupForTags(listOf("猪", "叶类蔬菜", "汤")))
        assertEquals(DishGroup.MEAT, DishTaxonomy.groupForTags(listOf("叶类蔬菜", "虾")))
        assertEquals(DishGroup.VEG, DishTaxonomy.groupForTags(listOf("米面类", "菌菇类")))
        assertEquals(DishGroup.STAPLE, DishTaxonomy.groupForTags(listOf("甜品", "馒头")))
        assertNull(DishTaxonomy.groupForTags(listOf("甜品", "饮品")))
    }

    @Test
    fun `四组兜底二级分类与云端顺序一致`() {
        assertEquals(
            listOf(
                "猪", "牛", "羊", "鸡", "鸭", "鹅", "鸽子", "鹌鹑", "火鸡",
                "兔肉", "驴肉", "鹿肉", "牛蛙", "田鸡", "雪蛤",
                "鱼", "虾", "螃蟹", "贝", "其他水产"
            ),
            DishTaxonomy.fallbackCategories[DishGroup.MEAT]
        )
        assertEquals(
            listOf("叶类蔬菜", "根茎蔬菜", "果实类蔬菜", "菌菇类", "花朵植物类", "水果", "蛋类", "豆制品", "奶制品"),
            DishTaxonomy.fallbackCategories[DishGroup.VEG]
        )
        assertEquals(listOf("汤", "汤羹"), DishTaxonomy.fallbackCategories[DishGroup.SOUP])
        assertEquals(
            listOf("饼", "馒头", "馄饨", "粥", "面条", "饭", "饺子", "包子", "三明治", "米面类"),
            DishTaxonomy.fallbackCategories[DishGroup.STAPLE]
        )
        DishTaxonomy.fallbackCategories.getValue(DishGroup.MEAT).forEach { sub ->
            assertEquals(DishGroup.MEAT, DishTaxonomy.level2ToGroup(sub))
        }
    }

    @Test
    fun `云端分类为空时逐组回退兜底`() {
        val merged = DishTaxonomy.categoriesFrom(
            mapOf(
                "meat" to listOf("猪"),
                "veg" to emptyList(),
                "soup" to listOf("汤羹", "汤"),
                "unknown" to listOf("忽略")
            )
        )
        assertEquals(listOf("猪"), merged[DishGroup.MEAT])
        assertEquals(DishTaxonomy.fallbackCategories[DishGroup.VEG], merged[DishGroup.VEG])
        assertEquals(listOf("汤羹", "汤"), merged[DishGroup.SOUP])
        assertEquals(DishTaxonomy.fallbackCategories[DishGroup.STAPLE], merged[DishGroup.STAPLE])

        val allFallback = DishTaxonomy.categoriesFrom(null)
        DishGroup.order.forEach { group ->
            assertEquals(DishTaxonomy.fallbackCategories[group], allFallback[group])
        }
    }

    @Test
    fun `筛选选项带上全部`() {
        assertEquals(
            listOf(DishTaxonomy.ALL, "汤", "汤羹"),
            DishTaxonomy.optionsFor(DishGroup.SOUP)
        )
    }

    @Test
    fun `自建库分组与二级分类映射一致`() {
        assertEquals(90, BuiltinDishes.all.size)
        assertEquals(90, BuiltinDishes.all.map { it.name }.distinct().size)
        BuiltinDishes.all.forEach { dish ->
            assertEquals(dish.name, DishGroup.fromKey(dish.group.key), dish.group)
            assertEquals(dish.name, DishTaxonomy.level2ToGroup(dish.level2), dish.group)
            assertTrue(dish.name, dish.level1.isNotBlank())
        }
        DishGroup.order.forEach { group ->
            assertTrue(group.label, BuiltinDishes.byGroup(group).isNotEmpty())
        }
    }
}
