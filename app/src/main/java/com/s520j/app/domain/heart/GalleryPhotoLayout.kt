package com.s520j.app.domain.heart

/**
 * 拾光照片卡的「一卡多图」排布：固定 2×2 画布，格子编号 0=左上、1=右上、2=左下、3=右下。
 * 纯函数便于单测，UI 只负责按返回的格子序号摆放图片，空格保留背景色。
 */
object GalleryPhotoLayout {
    /** 一条记录最多展示 4 张图（编辑页本就有同样限制）。 */
    const val MAX = 4

    /**
     * 返回需要填充的格子序号，按图片顺序对应：
     * 1 张 → 占满整卡；2 张 → 对角线；3 张 → 上排居中 + 下排两张；4 张 → 四宫格。
     */
    fun cells(count: Int): List<Int> = when {
        count <= 0 -> emptyList()
        count == 1 -> listOf(0)
        count == 2 -> listOf(0, 3)
        count == 3 -> listOf(1, 2, 3)
        else -> listOf(0, 1, 2, 3)
    }
}
