package app.chalna.capture.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalna.capture.R
import app.chalna.capture.assistant.ChalnaInvocationGlowView
import app.chalna.capture.assistant.InvocationPulseKind
import app.chalna.capture.capture.DebugCaptureTelemetry

class VisualLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompositionLocalProvider(LocalChalnaColors provides NightColors) { VisualLab() } }
    }
}

@Composable
private fun VisualLab() = Column(
    Modifier.fillMaxSize().background(ChalnaTheme.colors.background).verticalScroll(rememberScrollState()).padding(20.dp),
) {
    Heading(stringResource(R.string.visual_lab))
    Body(stringResource(R.string.visual_lab_subtitle))
    SectionTitle(stringResource(R.string.lab_state_frames))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(Modifier.size(154.dp, 190.dp)) {
            InvocationGlow(CapturePhase.READY, true, Modifier.size(122.dp).align(Alignment.CenterHorizontally), deterministicProgress = 0f, enabled = false)
            Spacer(Modifier.height(6.dp)); ChalnaText("SETUP", 12, weight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        CapturePhase.entries.forEach { phase ->
            GlassCard(Modifier.size(154.dp, 190.dp)) {
                InvocationGlow(phase, true, Modifier.size(122.dp).align(Alignment.CenterHorizontally), deterministicProgress = if (phase == CapturePhase.READY) 0f else 1f)
                Spacer(Modifier.height(6.dp)); ChalnaText(phase.name, 12, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
    SectionTitle(stringResource(R.string.lab_glow_timing))
    listOf(InvocationPulseKind.START, InvocationPulseKind.STOP, InvocationPulseKind.ERROR).forEach { kind ->
        ChalnaText(kind.name, 12, ChalnaTheme.colors.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0f, .16f, .34f, .5f, .78f, 1f).forEach { progress ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AndroidView(
                        factory = { context -> ChalnaInvocationGlowView(context).also { it.preview(kind, progress) } },
                        update = { it.preview(kind, progress) },
                        modifier = Modifier.size(118.dp),
                    )
                    ChalnaText("${(progress * 100).toInt()}%", 11, ChalnaTheme.colors.muted)
                }
            }
        }
    }
    SectionTitle(stringResource(R.string.lab_icon_masks))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(CircleShape, RoundedCornerShape(24.dp), RoundedCornerShape(10.dp)).forEach { shape ->
            Box(Modifier.size(92.dp).clip(shape).background(Color(0xFF101526)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_chalna_mark), null, Modifier.fillMaxSize())
            }
        }
    }
    SectionTitle(stringResource(R.string.lab_theme_contrast))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(NightColors, MistColors).forEach { colors ->
            CompositionLocalProvider(LocalChalnaColors provides colors) {
                Column(
                    Modifier.size(158.dp, 112.dp).clip(RoundedCornerShape(18.dp))
                        .background(colors.background).padding(14.dp),
                ) {
                    ChalnaText(if (colors === NightColors) "Night" else "Mist", 15, weight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(6.dp)); Body(stringResource(R.string.setup_body))
                }
            }
        }
    }
    SectionTitle(stringResource(R.string.lab_large_font))
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
        GlassCard { Heading(stringResource(R.string.setup_title)); Body(stringResource(R.string.setup_body)); Spacer(Modifier.height(10.dp)); SettingRow(stringResource(R.string.assistant), stringResource(R.string.assistant_setup_detail), ChalnaIcon.ASSISTANT, false) {} }
    }
    SectionTitle(stringResource(R.string.lab_capture_timing))
    val markers by DebugCaptureTelemetry.snapshot.collectAsStateWithLifecycle()
    if (markers.isEmpty()) {
        Body(stringResource(R.string.lab_no_capture_timing))
    } else {
        val origin = markers.first().elapsedRealtimeNanos
        markers.takeLast(12).forEach { marker ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChalnaText(marker.name, Modifier.weight(1f), 12)
                ChalnaText("+${(marker.elapsedRealtimeNanos - origin) / 1_000_000} ms", 12, ChalnaTheme.colors.muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(stringResource(R.string.lab_clear_timing), DebugCaptureTelemetry::clear)
    }
    Spacer(Modifier.height(32.dp))
}
