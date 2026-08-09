package app.chalna.capture.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Transient invocation signal: cached atmosphere, chromatic bloom, then a hot core. */
@Composable
internal fun InvocationGlow(
    phase: CapturePhase,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    deterministicProgress: Float? = null,
) {
    val active = phase != CapturePhase.READY && phase != CapturePhase.SAVED
    val transition = updateTransition(phase, label = "invocationGlow")
    val reveal by transition.animateFloat(
        transitionSpec = {
            keyframes {
                durationMillis = when (targetState) {
                    CapturePhase.STARTING -> 560
                    CapturePhase.STOPPING -> 420
                    CapturePhase.ERROR -> 360
                    else -> 280
                }
                0f at 0
                1.12f at 210
                1f at durationMillis
            }
        }, label = "glowReveal",
    ) { target -> if (target == CapturePhase.READY || target == CapturePhase.SAVED) 0f else 1f }
    val breathing = if (phase == CapturePhase.RECORDING && !reducedMotion && deterministicProgress == null) {
        val infinite = rememberInfiniteTransition(label = "glowBreath")
        val value by infinite.animateFloat(.92f, 1.06f, infiniteRepeatable(keyframes { durationMillis = 1450; .92f at 0; 1.06f at 620; .98f at 1120; .92f at 1450 }, RepeatMode.Restart), label = "breath")
        value
    } else 1f
    val strength = deterministicProgress ?: reveal
    val colors = ChalnaTheme.colors
    Canvas(
        modifier.clearAndSetSemantics { }.drawWithCache {
            val radius = size.minDimension / 2f
            val atmosphere = when (phase) {
                CapturePhase.ERROR -> colors.danger
                CapturePhase.STOPPING -> colors.accent2
                else -> colors.accent
            }
            val outer = Brush.radialGradient(listOf(atmosphere.copy(.25f), colors.accent2.copy(.10f), Color.Transparent), radius = radius)
            val bloom = Brush.sweepGradient(listOf(colors.accent, Color(0xFFFF79B0), colors.accent2, colors.accent))
            onDrawBehind {
                if (active || strength > .01f) {
                    drawCircle(outer, radius * strength.coerceAtLeast(0f) * breathing)
                    drawCircle(bloom, radius * .57f * strength.coerceAtLeast(.05f), alpha = (.55f * strength).coerceIn(0f,1f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Screen)
                    drawCircle(atmosphere.copy(alpha = (.32f * strength).coerceIn(0f,1f)), radius * .43f * breathing)
                    drawCircle(Color.White.copy(alpha = (.94f * strength).coerceIn(0f,1f)), radius * .16f)
                    drawCircle(Color.White.copy(alpha = (.45f * strength).coerceIn(0f,1f)), radius * .25f, center = Offset(center.x - radius*.05f, center.y - radius*.06f), blendMode = BlendMode.Screen)
                } else {
                    drawCircle(colors.surfaceHigh, radius * .32f)
                    drawCircle(colors.outline, radius * .32f, style = Stroke(2.dp.toPx()))
                }
            }
        },
    ) {}
}
