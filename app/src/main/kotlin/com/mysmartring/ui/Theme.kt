package com.mysmartring.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme

/**
 * 深色配色，參考 Claude desktop：暖灰黑的底、較亮一階的卡片、奶油色文字，加上陶土橘作為強調色。
 * 只有深色一種，不跟隨系統。
 */
object AppColors {
    val Background = Color(0xFF262624)
    val Card = Color(0xFF30302E)
    val CardLow = Color(0xFF2B2B29)
    val Outline = Color(0xFF4A4944)
    val TextPrimary = Color(0xFFFAF9F5)
    val TextSecondary = Color(0xFFBFBDB4)
    val Accent = Color(0xFFD97757)
    val Teal = Color(0xFF86B3A8)
    val Amber = Color(0xFFE0B15A)
    val Error = Color(0xFFE57373)

    /** 睡眠階段：在深色底上都要夠亮，彼此也要分得出來 */
    val SleepDeep = Color(0xFF7C8CE0)
    val SleepLight = Color(0xFFE0A458)
    val SleepRem = Color(0xFF5FB5A5)
    val SleepAwake = Color(0xFFD96B6B)

    val HeartRate = Color(0xFFF0766C)
    val BloodOxygen = Color(0xFF6FB1E8)
}

private val AppColorScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.Background,
    primaryContainer = Color(0xFF5A3A2E),
    onPrimaryContainer = AppColors.TextPrimary,
    secondary = AppColors.Teal,
    onSecondary = AppColors.Background,
    secondaryContainer = Color(0xFF3A4A46),
    onSecondaryContainer = AppColors.TextPrimary,
    tertiary = AppColors.Amber,
    onTertiary = AppColors.Background,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Background,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.Card,
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceContainerLowest = Color(0xFF1F1E1D),
    surfaceContainerLow = AppColors.CardLow,
    surfaceContainer = AppColors.CardLow,
    surfaceContainerHigh = AppColors.Card,
    surfaceContainerHighest = AppColors.Card,
    outline = AppColors.Outline,
    outlineVariant = AppColors.Outline,
    error = AppColors.Error,
    onError = AppColors.Background,
)

/** 大數字與標題用襯線體，呼應 Claude 的版面；內文維持系統字體，中文才清楚 */
private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Serif),
        displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Serif),
        displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif),
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun MySmartRingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, typography = AppTypography, shapes = AppShapes) {
        // 圖表也跟著 Material 主題走，才不會在深色底上出現看不清的預設色
        ProvideVicoTheme(rememberM3VicoTheme(), content)
    }
}
