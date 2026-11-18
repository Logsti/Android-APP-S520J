package com.s520j.app.domain.food

/**
 * 自建菜库（离线与首屏兜底），与云端 cloudbase/functions/_shared/dishes.js 1:1 对应。
 *
 * 每道菜都带一级分类、二级分类与四组标签，二级分类名与 ShowAPI 官方分类一致；
 * 分组只按二级分类推导，四组都覆盖常用二级分类，保证离线也能凑出一桌。
 */
object BuiltinDishes {

    val all: List<DishCandidate> = listOf(
    DishCandidate(
        name = "红烧肉",
        level1 = "肉类",
        level2 = "猪",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "糖醋排骨",
        level1 = "肉类",
        level2 = "猪",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "回锅肉",
        level1 = "肉类",
        level2 = "猪",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "鱼香肉丝",
        level1 = "肉类",
        level2 = "猪",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "农家小炒肉",
        level1 = "肉类",
        level2 = "猪",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "梅菜扣肉",
        level1 = "肉类",
        level2 = "猪",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "蚂蚁上树",
        level1 = "肉类",
        level2 = "猪",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "黑椒牛柳",
        level1 = "肉类",
        level2 = "牛",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "番茄牛腩",
        level1 = "肉类",
        level2 = "牛",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "水煮牛肉",
        level1 = "肉类",
        level2 = "牛",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "土豆炖牛肉",
        level1 = "肉类",
        level2 = "牛",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "蚝油牛肉",
        level1 = "肉类",
        level2 = "牛",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "孜然牛肉",
        level1 = "肉类",
        level2 = "牛",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "手抓羊肉",
        level1 = "肉类",
        level2 = "羊",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "葱爆羊肉",
        level1 = "肉类",
        level2 = "羊",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "红烧羊排",
        level1 = "肉类",
        level2 = "羊",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "烤羊腿",
        level1 = "肉类",
        level2 = "羊",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "宫保鸡丁",
        level1 = "肉类",
        level2 = "鸡",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "三杯鸡",
        level1 = "肉类",
        level2 = "鸡",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "白切鸡",
        level1 = "肉类",
        level2 = "鸡",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "可乐鸡翅",
        level1 = "肉类",
        level2 = "鸡",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "黄焖鸡",
        level1 = "肉类",
        level2 = "鸡",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "辣子鸡",
        level1 = "肉类",
        level2 = "鸡",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "口水鸡",
        level1 = "肉类",
        level2 = "鸡",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "啤酒鸭",
        level1 = "肉类",
        level2 = "鸭",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "香酥鸭",
        level1 = "肉类",
        level2 = "鸭",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "盐水鸭",
        level1 = "肉类",
        level2 = "鸭",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "烧鹅",
        level1 = "肉类",
        level2 = "鹅",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "烤乳鸽",
        level1 = "肉类",
        level2 = "鸽子",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "香炸鹌鹑",
        level1 = "肉类",
        level2 = "鹌鹑",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "麻辣兔丁",
        level1 = "肉类",
        level2 = "兔肉",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "泡椒牛蛙",
        level1 = "肉类",
        level2 = "牛蛙",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "清蒸鲈鱼",
        level1 = "水产",
        level2 = "鱼",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "红烧带鱼",
        level1 = "水产",
        level2 = "鱼",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "酸菜鱼",
        level1 = "水产",
        level2 = "鱼",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "油焖大虾",
        level1 = "水产",
        level2 = "虾",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "白灼虾",
        level1 = "水产",
        level2 = "虾",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "清蒸大闸蟹",
        level1 = "水产",
        level2 = "螃蟹",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "蒜蓉扇贝",
        level1 = "水产",
        level2 = "贝",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "葱烧海参",
        level1 = "水产",
        level2 = "其他水产",
        group = DishGroup.MEAT,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "清炒油麦菜",
        level1 = "蔬菜水果",
        level2 = "叶类蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "蒜蓉空心菜",
        level1 = "蔬菜水果",
        level2 = "叶类蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "手撕包菜",
        level1 = "蔬菜水果",
        level2 = "叶类蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "上汤娃娃菜",
        level1 = "蔬菜水果",
        level2 = "叶类蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "醋溜土豆丝",
        level1 = "蔬菜水果",
        level2 = "根茎蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "清炒藕片",
        level1 = "蔬菜水果",
        level2 = "根茎蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "红烧萝卜",
        level1 = "蔬菜水果",
        level2 = "根茎蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "西红柿炒鸡蛋",
        level1 = "蔬菜水果",
        level2 = "果实类蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "地三鲜",
        level1 = "蔬菜水果",
        level2 = "果实类蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "干煸四季豆",
        level1 = "蔬菜水果",
        level2 = "果实类蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "虎皮青椒",
        level1 = "蔬菜水果",
        level2 = "果实类蔬菜",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "香菇青菜",
        level1 = "蔬菜水果",
        level2 = "菌菇类",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "干锅杏鲍菇",
        level1 = "蔬菜水果",
        level2 = "菌菇类",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "平菇炒蛋",
        level1 = "蔬菜水果",
        level2 = "菌菇类",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "清炒西兰花",
        level1 = "蔬菜水果",
        level2 = "花朵植物类",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "凉拌黄花菜",
        level1 = "蔬菜水果",
        level2 = "花朵植物类",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "拔丝苹果",
        level1 = "蔬菜水果",
        level2 = "水果",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "水果沙拉",
        level1 = "蔬菜水果",
        level2 = "水果",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "番茄炒蛋",
        level1 = "蛋奶豆制品",
        level2 = "蛋类",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "蒸水蛋",
        level1 = "蛋奶豆制品",
        level2 = "蛋类",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "韭菜炒鸡蛋",
        level1 = "蛋奶豆制品",
        level2 = "蛋类",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "麻婆豆腐",
        level1 = "蛋奶豆制品",
        level2 = "豆制品",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "家常豆腐",
        level1 = "蛋奶豆制品",
        level2 = "豆制品",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "香煎豆腐",
        level1 = "蛋奶豆制品",
        level2 = "豆制品",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "炸鲜奶",
        level1 = "蛋奶豆制品",
        level2 = "奶制品",
        group = DishGroup.VEG,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "番茄蛋汤",
        level1 = "汤粥主食",
        level2 = "汤",
        group = DishGroup.SOUP,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "紫菜蛋花汤",
        level1 = "汤粥主食",
        level2 = "汤",
        group = DishGroup.SOUP,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "冬瓜排骨汤",
        level1 = "汤粥主食",
        level2 = "汤",
        group = DishGroup.SOUP,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "玉米排骨汤",
        level1 = "汤粥主食",
        level2 = "汤",
        group = DishGroup.SOUP,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "酸辣汤",
        level1 = "汤粥主食",
        level2 = "汤",
        group = DishGroup.SOUP,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "菌菇汤",
        level1 = "汤粥主食",
        level2 = "汤",
        group = DishGroup.SOUP,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "西湖牛肉羹",
        level1 = "汤粥主食",
        level2 = "汤羹",
        group = DishGroup.SOUP,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "银耳莲子羹",
        level1 = "汤粥主食",
        level2 = "汤羹",
        group = DishGroup.SOUP,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "南瓜羹",
        level1 = "汤粥主食",
        level2 = "汤羹",
        group = DishGroup.SOUP,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "宋嫂鱼羹",
        level1 = "汤粥主食",
        level2 = "汤羹",
        group = DishGroup.SOUP,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "干炒牛河",
        level1 = "米面干果腌咸",
        level2 = "米面类",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "皮蛋瘦肉粥",
        level1 = "汤粥主食",
        level2 = "粥",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "小米南瓜粥",
        level1 = "汤粥主食",
        level2 = "粥",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "蛋炒饭",
        level1 = "汤粥主食",
        level2 = "饭",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "扬州炒饭",
        level1 = "汤粥主食",
        level2 = "饭",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "番茄鸡蛋面",
        level1 = "汤粥主食",
        level2 = "面条",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "红烧牛肉面",
        level1 = "汤粥主食",
        level2 = "面条",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "葱油拌面",
        level1 = "汤粥主食",
        level2 = "面条",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "韭菜猪肉饺子",
        level1 = "汤粥主食",
        level2 = "饺子",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "白菜猪肉饺子",
        level1 = "汤粥主食",
        level2 = "饺子",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "鲜肉包",
        level1 = "汤粥主食",
        level2 = "包子",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "奶香馒头",
        level1 = "汤粥主食",
        level2 = "馒头",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "葱油饼",
        level1 = "汤粥主食",
        level2 = "饼",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "鲜虾馄饨",
        level1 = "汤粥主食",
        level2 = "馄饨",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    ),
    DishCandidate(
        name = "火腿三明治",
        level1 = "汤粥主食",
        level2 = "三明治",
        group = DishGroup.STAPLE,
        source = DishSource.BUILTIN
    )
    )

    fun byGroup(group: DishGroup): List<DishCandidate> = all.filter { it.group == group }

    fun byGroup(group: DishGroup, subCategory: String): List<DishCandidate> {
        val matched = all.filter { it.group == group && it.level2 == subCategory }
        return if (matched.isEmpty()) byGroup(group) else matched
    }
}
