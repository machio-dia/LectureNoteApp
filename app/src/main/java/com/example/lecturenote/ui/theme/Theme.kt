package com.example.lecturenote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** アプリ全体で使う色。画面ごとの直書きをやめ、ここに集約する。 */
object AppColors {
    val Accent = Color(0xFF2F5BEA)
    val AccentSoft = Color(0xFFE6ECFF)
    val AccentDeep = Color(0xFF16357F)

    val Ink = Color(0xFF1B1F27)
    val InkSoft = Color(0xFF5B6472)
    val InkFaint = Color(0xFF8A93A2)

    val AppBackground = Color(0xFFF3F4F7)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF1F3F6)
    val Line = Color(0xFFE3E6EB)
    val LineStrong = Color(0xFFC9CED6)

    val Danger = Color(0xFFD92D20)
    val DangerSoft = Color(0xFFFEE9E7)
    val Success = Color(0xFF0E9F6E)
    val SuccessSoft = Color(0xFFDDF7EC)
    val Warn = Color(0xFFB45309)
    val WarnSoft = Color(0xFFFEF3C7)

    val Laser = Color(0xFFE11D48)
    val CoverSheet = Color(0xFF2A3040)

    /** 教科バッジ用の色（教科名から決まる） */
    private val subjectPalette = listOf(
        Color(0xFF2F5BEA), Color(0xFF0E9F6E), Color(0xFFD9480F), Color(0xFF7C3AED),
        Color(0xFF0B7FAB), Color(0xFFC2255C), Color(0xFF5C7C0B), Color(0xFF8D6E0A)
    )

    fun subjectColor(subject: String): Color {
        if (subject.isBlank()) return InkFaint
        val idx = (subject.hashCode() and 0x7FFFFFFF) % subjectPalette.size
        return subjectPalette[idx]
    }
}

private val LightColorScheme = lightColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color.White,
    primaryContainer = AppColors.AccentSoft,
    onPrimaryContainer = AppColors.AccentDeep,
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = AppColors.AccentSoft,
    onSecondaryContainer = AppColors.AccentDeep,
    tertiary = AppColors.Success,
    onTertiary = Color.White,
    tertiaryContainer = AppColors.SuccessSoft,
    onTertiaryContainer = Color(0xFF05603F),
    background = AppColors.AppBackground,
    onBackground = AppColors.Ink,
    surface = AppColors.Surface,
    onSurface = AppColors.Ink,
    surfaceVariant = AppColors.SurfaceMuted,
    onSurfaceVariant = AppColors.InkSoft,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = AppColors.SurfaceMuted,
    outline = AppColors.LineStrong,
    outlineVariant = AppColors.Line,
    error = AppColors.Danger,
    onError = Color.White,
    errorContainer = AppColors.DangerSoft,
    onErrorContainer = Color(0xFF912018)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun LectureNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        shapes = AppShapes,
        content = content
    )
}
