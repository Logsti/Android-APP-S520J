package com.s520j.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 色值唯一来源：docs《前端外观设计需求文档》§2.1。
 * 任何页面不得硬编码颜色，只能引用此处 token。
 */
object S520JColors {
    val Background = Color(0xFFF8F6EE)
    val Surface = Color(0xFFFFFEFA)
    val Primary = Color(0xFF7D9676)
    val PrimarySoft = Color(0xFFD9E5D2)
    val Sand = Color(0xFFEEE4CE)
    val TextPrimary = Color(0xFF344033)
    val TextSecondary = Color(0xFF858778)
    val Divider = Color(0xFFE8E2D5)
    val AccentPink = Color(0xFFF2E2D9)

    // 心动页视觉升级（对照参考图补充）
    val HeroStart = Color(0xFFA9BD93)
    val HeroEnd = Color(0xFFD8CFA7)
    val WarmBrown = Color(0xFF6B4E3D)
    val DeepGreen = Color(0xFF4F6B45)
    val ChipPeach = Color(0xFFF5E3D2)
    val PureWhite = Color(0xFFFFFFFF)
}
