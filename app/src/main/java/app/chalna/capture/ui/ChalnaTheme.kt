package app.chalna.capture.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ChalnaColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accent2: Color,
    val positive: Color,
    val danger: Color,
    val outline: Color,
)

val NightColors = ChalnaColors(
    Color(0xFF080A12), Color(0xD9151925), Color(0xFF222738), Color(0xFFF7F5FF),
    Color(0xFFA9A9BA), Color(0xFFB7A1FF), Color(0xFF70E2D2), Color(0xFF86E6A7),
    Color(0xFFFF7D8E), Color(0xFF3B4052),
)
val MistColors = ChalnaColors(
    Color(0xFFF4F2F8), Color(0xE6FFFFFF), Color(0xFFFFFFFF), Color(0xFF171520),
    Color(0xFF686372), Color(0xFF684FD1), Color(0xFF0B8E83), Color(0xFF19733E),
    Color(0xFFB4233D), Color(0xFFD8D3E0),
)

val LocalChalnaColors = staticCompositionLocalOf { NightColors }

object ChalnaTheme {
    val colors: ChalnaColors @Composable get() = LocalChalnaColors.current
}
