package app.chalna.capture.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import app.chalna.capture.R

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
    val warning: Color,
    val danger: Color,
    val outline: Color,
)

val NightColors = ChalnaColors(
    Color(0xFF080A12), Color(0xD9151925), Color(0xFF222738), Color(0xFFF7F5FF),
    Color(0xFFA9A9BA), Color(0xFF58D7FF), Color(0xFF896EFF), Color(0xFF64D5B0),
    Color(0xFFFFB35E), Color(0xFFFF657F), Color(0xFF3B4052),
)
val MistColors = ChalnaColors(
    Color(0xFFF4F2F8), Color(0xE6FFFFFF), Color(0xFFFFFFFF), Color(0xFF171520),
    Color(0xFF686372), Color(0xFF325FC4), Color(0xFF684FD1), Color(0xFF19733E),
    Color(0xFFA85800), Color(0xFFB4233D), Color(0xFFD8D3E0),
)

val LocalChalnaColors = staticCompositionLocalOf { NightColors }

object ChalnaTheme {
    val colors: ChalnaColors @Composable get() = LocalChalnaColors.current
}

val ChalnaFontFamily = FontFamily(
    Font(R.font.noto_sans_kr_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_sans_kr_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_sans_kr_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.noto_sans_kr_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
