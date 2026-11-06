package com.s520j.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightScheme = lightColorScheme(
    primary = S520JColors.Primary,
    onPrimary = Color.White,
    primaryContainer = S520JColors.PrimarySoft,
    onPrimaryContainer = S520JColors.TextPrimary,
    secondary = S520JColors.Sand,
    onSecondary = S520JColors.TextPrimary,
    secondaryContainer = S520JColors.Sand,
    onSecondaryContainer = S520JColors.TextPrimary,
    background = S520JColors.Background,
    onBackground = S520JColors.TextPrimary,
    surface = S520JColors.Surface,
    onSurface = S520JColors.TextPrimary,
    surfaceVariant = S520JColors.PrimarySoft,
    onSurfaceVariant = S520JColors.TextSecondary,
    outline = S520JColors.Divider,
    outlineVariant = S520JColors.Divider,
    error = Color(0xFFB3261E),
    onError = Color.White
)

/**
 * fontScale 为用户可调字体大小系数（应用设置），通过覆盖 LocalDensity 全局生效。
 */
@Composable
fun S520JTheme(
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density, fontScale = density.fontScale * fontScale)
    ) {
        MaterialTheme(
            colorScheme = LightScheme,
            typography = S520JTypography,
            shapes = S520JShapes,
            content = content
        )
    }
}
