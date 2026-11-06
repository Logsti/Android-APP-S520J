package com.s520j.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 卡片圆角 18~24dp，大型卡片 24~30dp。 */
val S520JShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

val CardCorner = RoundedCornerShape(20.dp)
val LargeCardCorner = RoundedCornerShape(26.dp)
