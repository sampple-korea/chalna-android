package app.chalna.capture.ui

import android.app.Activity
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import app.chalna.capture.BuildConfig
import app.chalna.capture.R
import kotlin.math.cos
import kotlin.math.sin

private enum class Route { HOME, CAPTURE, APPEARANCE, DIAGNOSTICS, HELP, ABOUT }

@Composable
fun ChalnaApp(dependencies: UiDependencies) {
    val state by dependencies.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemDark = (context.resources.configuration.uiMode and 0x30) == 0x20
    val colors = when (state.appearance) {
        AppearanceMode.NIGHT -> NightColors
        AppearanceMode.MIST -> MistColors
        AppearanceMode.SYSTEM -> if (systemDark) NightColors else MistColors
    }
    CompositionLocalProvider(LocalChalnaColors provides colors) {
        Box(Modifier.fillMaxSize().background(colors.background).systemBarsPadding()) {
            FluidBackdrop(state.reducedMotion || state.powerSaver)
            if (state.setupComplete) MainShell(state, dependencies) else SetupFlow(state, dependencies)
            if (state.phase == CapturePhase.RECORDING) EdgePulse(state.reducedMotion || state.powerSaver)
        }
    }
}

@Composable
private fun EdgePulse(static: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
    val animate = !static && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val transition = rememberInfiniteTransition(label = "edgePulse")
    val alpha by transition.animateFloat(.28f, if (animate) .82f else .28f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "edgeAlpha")
    val colors = ChalnaTheme.colors
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.sweepGradient(listOf(colors.accent.copy(alpha), colors.accent2.copy(alpha), colors.accent.copy(alpha))),
            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
            size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
            style = Stroke(3.dp.toPx()),
        )
    }
}

@Composable
private fun FluidBackdrop(static: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
    val animate = !static && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val transition = rememberInfiniteTransition(label = "fluid")
    val phase by transition.animateFloat(0f, if (animate) 1f else 0f, infiniteRepeatable(tween(16000), RepeatMode.Restart), label = "phase")
    val c = ChalnaTheme.colors
    Canvas(Modifier.fillMaxSize().blur(42.dp)) {
        val a = phase * 6.283f
        drawCircle(Brush.radialGradient(listOf(c.accent.copy(.30f), Color.Transparent)), size.minDimension * .54f, Offset(size.width * (.22f + .08f * cos(a)), size.height * .18f))
        drawCircle(Brush.radialGradient(listOf(c.accent2.copy(.22f), Color.Transparent)), size.minDimension * .66f, Offset(size.width * (.76f + .06f * sin(a)), size.height * .72f))
    }
}

@Composable
private fun SetupFlow(state: ChalnaUiState, d: UiDependencies) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    val titles = listOf(R.string.setup_title_1, R.string.setup_title_2, R.string.setup_title_3, R.string.setup_title_4)
    val bodies = listOf(R.string.setup_body_1, R.string.setup_body_2, R.string.setup_body_3, R.string.setup_body_4)
    ScreenColumn {
        TextLabel(stringResource(R.string.app_name), 14, ChalnaTheme.colors.accent, FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        StepDots(step)
        Spacer(Modifier.height(28.dp))
        Aura(Modifier.align(Alignment.CenterHorizontally), active = step == 3, reducedMotion = state.reducedMotion)
        Spacer(Modifier.height(32.dp))
        AnimatedContent(step, label = "setup") { value ->
            Column {
                Heading(stringResource(titles[value]))
                Spacer(Modifier.height(12.dp))
                Body(stringResource(bodies[value]))
                Spacer(Modifier.height(24.dp))
                when (value) {
                    1 -> PermissionRows(state, d)
                    2 -> ActionCard(R.string.open_assistant_settings, R.string.assistant_settings_hint, state.assistantSelected, d::openAssistantSettings)
                    3 -> SummaryCard(state)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(if (step == 3) R.string.finish else R.string.continue_label), enabled = step != 1 || (state.cameraGranted && (state.microphoneGranted || !state.sound) && state.notificationsGranted)) {
            if (step == 3) d.finishSetup() else step++
        }
    }
}

@Composable private fun PermissionRows(s: ChalnaUiState, d: UiDependencies) {
    ActionCard(R.string.camera_microphone, R.string.camera_microphone_hint, s.cameraGranted && s.microphoneGranted, d::requestCameraAndMicrophone)
    if (!s.microphoneGranted && s.sound) {
        Spacer(Modifier.height(10.dp))
        SecondaryButton(stringResource(R.string.use_without_audio)) { d.setSound(false) }
    }
    Spacer(Modifier.height(12.dp))
    ActionCard(R.string.notifications, R.string.notifications_hint, s.notificationsGranted, d::requestNotifications)
}

@Composable private fun SummaryCard(s: ChalnaUiState) = GlassCard {
    StatusLine(R.string.camera_microphone, s.cameraGranted && s.microphoneGranted)
    StatusLine(R.string.notifications, s.notificationsGranted)
    StatusLine(R.string.assistant_selected, s.assistantSelected)
}

@Composable private fun MainShell(s: ChalnaUiState, d: UiDependencies) {
    var route by rememberSaveable { mutableStateOf(Route.HOME) }
    BackHandler(enabled = route != Route.HOME) { route = Route.HOME }
    Crossfade(route, label = "route") { current ->
        when (current) {
            Route.HOME -> HomeScreen(s, d) { route = it }
            Route.CAPTURE -> CaptureSettings(s, d) { route = Route.HOME }
            Route.APPEARANCE -> AppearanceScreen(s, d) { route = Route.HOME }
            Route.DIAGNOSTICS -> DiagnosticsScreen(s, d) { route = Route.HOME }
            Route.HELP -> HelpScreen { route = Route.HOME }
            Route.ABOUT -> AboutScreen { route = Route.HOME }
        }
    }
}

@Composable private fun HomeScreen(s: ChalnaUiState, d: UiDependencies, navigate: (Route) -> Unit) = ScreenColumn {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { TextLabel(stringResource(R.string.app_name), 28, ChalnaTheme.colors.text, FontWeight.Bold); Body(stringResource(R.string.local_private)) }
        IconButton(stringResource(R.string.settings)) { navigate(Route.CAPTURE) }
    }
    Spacer(Modifier.height(32.dp))
    Aura(Modifier.align(Alignment.CenterHorizontally), active = s.phase == CapturePhase.RECORDING, reducedMotion = s.reducedMotion || s.powerSaver)
    Spacer(Modifier.height(24.dp))
    TextLabel(phaseTitle(s), 30, ChalnaTheme.colors.text, FontWeight.Bold, Modifier.fillMaxWidth(), TextAlign.Center)
    if (s.phase == CapturePhase.RECORDING || s.phase == CapturePhase.STOPPING) TextLabel(formatDuration(s.durationSeconds), 20, ChalnaTheme.colors.muted, FontWeight.Medium, Modifier.fillMaxWidth(), TextAlign.Center)
    s.lastSavedName?.let { TextLabel(stringResource(R.string.saved_as, it), 14, ChalnaTheme.colors.positive, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center) }
    s.errorMessage?.let { TextLabel(it, 14, ChalnaTheme.colors.danger, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center) }
    Spacer(Modifier.height(22.dp))
    if (s.phase == CapturePhase.RECORDING) {
        PrimaryButton(stringResource(R.string.stop_capture), onClick = d::toggleCapture)
    } else if (s.phase == CapturePhase.SAVED) {
        PrimaryButton(stringResource(R.string.open_video), onClick = d::openLastCapture)
    } else {
        StatusRail(s)
    }
    Spacer(Modifier.height(28.dp))
    NavRow(R.string.capture_settings, R.string.capture_settings_hint) { navigate(Route.CAPTURE) }
    NavRow(R.string.appearance_feedback, R.string.appearance_feedback_hint) { navigate(Route.APPEARANCE) }
    NavRow(R.string.diagnostics, R.string.diagnostics_hint) { navigate(Route.DIAGNOSTICS) }
    NavRow(R.string.help_compatibility, R.string.help_compatibility_hint) { navigate(Route.HELP) }
    NavRow(R.string.about_privacy, R.string.about_privacy_hint) { navigate(Route.ABOUT) }
}

@Composable private fun CaptureSettings(s: ChalnaUiState, d: UiDependencies, back: () -> Unit) = DetailScreen(R.string.capture_settings, back) {
    Section(R.string.audio)
    SwitchRow(R.string.audio, s.sound) { d.setSound(it) }
    Spacer(Modifier.height(20.dp))
    Section(R.string.video_quality)
    VideoQuality.entries.forEach { quality -> ChoiceRow(stringResource(when (quality) { VideoQuality.AUTO -> R.string.quality_auto; VideoQuality.FHD -> R.string.quality_fhd; VideoQuality.HD -> R.string.quality_hd }), s.quality == quality) { d.setQuality(quality) } }
    Spacer(Modifier.height(20.dp)); Section(R.string.auto_stop)
    listOf(0, 15, 30, 60).forEach { seconds -> ChoiceRow(stringResource(if (seconds == 0) R.string.no_limit else R.string.seconds_value, seconds), s.autoStopSeconds == seconds) { d.setAutoStop(seconds) } }
    Spacer(Modifier.height(20.dp)); Section(R.string.capture_behavior)
    InfoCard(R.string.explicit_trigger_only, R.string.explicit_trigger_detail)
    InfoCard(R.string.media_storage, R.string.media_storage_detail)
}

@Composable private fun AppearanceScreen(s: ChalnaUiState, d: UiDependencies, back: () -> Unit) = DetailScreen(R.string.appearance_feedback, back) {
    Section(R.string.appearance)
    AppearanceMode.entries.forEach { ChoiceRow(stringResource(when(it) { AppearanceMode.NIGHT -> R.string.night; AppearanceMode.MIST -> R.string.mist; else -> R.string.system }), s.appearance == it) { d.setAppearance(it) } }
    Spacer(Modifier.height(20.dp)); Section(R.string.feedback)
    SwitchRow(R.string.haptics, s.haptics) { d.setHaptics(it) }
    SwitchRow(R.string.sound, s.sound) { d.setSound(it) }
    SwitchRow(R.string.reduced_motion, s.reducedMotion) { d.setReducedMotion(it) }
}

@Composable private fun DiagnosticsScreen(s: ChalnaUiState, d: UiDependencies, back: () -> Unit) = DetailScreen(R.string.diagnostics, back) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Body(stringResource(R.string.diagnostics_privacy))
    Spacer(Modifier.height(16.dp)); PrimaryButton(stringResource(R.string.run_diagnostic)) { confirm = true }
    if (confirm) {
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Body(stringResource(R.string.diagnostic_real_video_warning))
            Spacer(Modifier.height(12.dp))
            PrimaryButton(stringResource(R.string.confirm_capture)) { confirm = false; d.runDiagnosticCapture() }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.cancel)) { confirm = false }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (s.diagnosticLines.isEmpty()) InfoCard(R.string.no_diagnostics, R.string.no_diagnostics_detail)
    else GlassCard { s.diagnosticLines.forEach { TextLabel(it, 13, ChalnaTheme.colors.text); Spacer(Modifier.height(8.dp)) } }
    Spacer(Modifier.height(12.dp)); SecondaryButton(stringResource(R.string.copy_diagnostics), d::copyDiagnostics)
    Spacer(Modifier.height(8.dp)); SecondaryButton(stringResource(R.string.clear_history), d::clearDiagnostics)
}

@Composable private fun StatusRail(s: ChalnaUiState) = Row(
    Modifier.fillMaxWidth().padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
) {
    TextLabel(stringResource(if (s.sound) R.string.audio_on else R.string.audio_off), 13, ChalnaTheme.colors.muted)
    TextLabel(stringResource(when (s.quality) { VideoQuality.AUTO -> R.string.quality_auto; VideoQuality.FHD -> R.string.quality_fhd; VideoQuality.HD -> R.string.quality_hd }), 13, ChalnaTheme.colors.muted)
    TextLabel(stringResource(if (s.autoStopSeconds == 0) R.string.no_limit else R.string.seconds_value, s.autoStopSeconds), 13, ChalnaTheme.colors.muted)
}

@Composable private fun HelpScreen(back: () -> Unit) = DetailScreen(R.string.help_compatibility, back) {
    InfoCard(R.string.help_assistant, R.string.help_assistant_detail)
    InfoCard(R.string.help_lock_screen, R.string.help_lock_screen_detail)
    InfoCard(R.string.help_oem, R.string.help_oem_detail)
    InfoCard(R.string.help_privacy, R.string.help_privacy_detail)
}

@Composable private fun AboutScreen(back: () -> Unit) = DetailScreen(R.string.about_privacy, back) {
    Aura(Modifier.align(Alignment.CenterHorizontally).size(110.dp), false, true)
    Spacer(Modifier.height(18.dp)); Heading(stringResource(R.string.app_name))
    Body(stringResource(R.string.version_info, BuildConfig.VERSION_NAME))
    Spacer(Modifier.height(24.dp)); Section(R.string.privacy)
    Body(stringResource(R.string.privacy_statement))
    Spacer(Modifier.height(18.dp)); InfoCard(R.string.permissions_used, R.string.permissions_used_detail)
    InfoCard(R.string.build_info, R.string.build_info_detail, BuildConfig.GIT_SHA, BuildConfig.BUILD_DATE_UTC)
}

@Composable private fun DetailScreen(title: Int, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) = ScreenColumn {
    Row(verticalAlignment = Alignment.CenterVertically) { IconButton(stringResource(R.string.back), back); Spacer(Modifier.width(12.dp)); Heading(stringResource(title)) }
    Spacer(Modifier.height(24.dp)); content()
}

@Composable private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp), content = content)

@Composable private fun Aura(modifier: Modifier = Modifier, active: Boolean, reducedMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "aura")
    val pulse by transition.animateFloat(.82f, if (active && !reducedMotion) 1f else .82f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")
    val c = ChalnaTheme.colors
    val description = stringResource(if (active) R.string.aura_recording else R.string.aura_idle)
    Canvas(modifier.size(184.dp).semantics { contentDescription = description }) {
        val radius = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(c.accent.copy(.08f), c.accent.copy(.42f), Color.Transparent)), radius * pulse)
        drawCircle(Brush.sweepGradient(listOf(c.accent, c.accent2, c.accent)), radius * .57f, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(c.surfaceHigh, radius * .43f)
        val path = Path().apply { moveTo(center.x - radius*.12f, center.y); lineTo(center.x, center.y-radius*.14f); lineTo(center.x+radius*.16f, center.y+radius*.15f) }
        drawPath(path, c.text, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable private fun StepDots(step: Int) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(4) { Box(Modifier.height(4.dp).width(if (it == step) 34.dp else 12.dp).clip(CircleShape).background(if (it <= step) ChalnaTheme.colors.accent else ChalnaTheme.colors.outline)) } }
@Composable private fun GlassCard(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ChalnaTheme.colors.surface).border(1.dp, ChalnaTheme.colors.outline, RoundedCornerShape(22.dp)).padding(18.dp), content = content)
@Composable private fun ActionCard(title: Int, body: Int, done: Boolean, click: () -> Unit) = GlassCard { Row(Modifier.clickableNoRipple(click).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { TextLabel(stringResource(title), 16, ChalnaTheme.colors.text, FontWeight.SemiBold); Body(stringResource(body)) }; StatusDot(done) } }
@Composable private fun InfoCard(title: Int, body: Int, vararg args: Any) { Spacer(Modifier.height(10.dp)); GlassCard { TextLabel(stringResource(title), 16, ChalnaTheme.colors.text, FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Body(stringResource(body, *args)) } }
@Composable private fun NavRow(title: Int, body: Int, click: () -> Unit) = Row(Modifier.fillMaxWidth().clickableNoRipple(click).padding(vertical = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { TextLabel(stringResource(title), 16, ChalnaTheme.colors.text, FontWeight.SemiBold); Body(stringResource(body)) }; Chevron() }
@Composable private fun ChoiceRow(label: String, selected: Boolean, click: () -> Unit) = Row(Modifier.fillMaxWidth().clickableNoRipple(click).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) { TextLabel(label, 16, ChalnaTheme.colors.text, modifier = Modifier.weight(1f)); StatusDot(selected) }
@Composable private fun SwitchRow(label: Int, selected: Boolean, click: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().clickableNoRipple { click(!selected) }.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) { TextLabel(stringResource(label), 16, ChalnaTheme.colors.text, modifier = Modifier.weight(1f)); Toggle(selected) }
@Composable private fun StatusLine(label: Int, done: Boolean) = Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) { TextLabel(stringResource(label), 15, ChalnaTheme.colors.text, modifier = Modifier.weight(1f)); TextLabel(stringResource(if(done) R.string.ready else R.string.action_needed), 13, if(done) ChalnaTheme.colors.positive else ChalnaTheme.colors.danger) }
@Composable private fun StatusDot(on: Boolean) = Box(Modifier.size(24.dp).clip(CircleShape).background(if(on) ChalnaTheme.colors.positive else ChalnaTheme.colors.outline))
@Composable private fun Toggle(on: Boolean) = Box(Modifier.width(48.dp).height(28.dp).clip(CircleShape).background(if(on) ChalnaTheme.colors.accent else ChalnaTheme.colors.outline).padding(3.dp), contentAlignment = if(on) Alignment.CenterEnd else Alignment.CenterStart) { Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White)) }
@Composable private fun Chevron() = Canvas(Modifier.size(48.dp).padding(17.dp)) { drawLine(ChalnaTheme.colors.muted, Offset(size.width*.3f, 0f), Offset(size.width*.75f,size.height*.5f), 2.dp.toPx(), StrokeCap.Round); drawLine(ChalnaTheme.colors.muted, Offset(size.width*.75f,size.height*.5f), Offset(size.width*.3f,size.height), 2.dp.toPx(), StrokeCap.Round) }
@Composable private fun IconButton(description: String, click: () -> Unit) = Box(Modifier.size(48.dp).clip(CircleShape).background(ChalnaTheme.colors.surface).clickableNoRipple(click).semantics { contentDescription = description; role = Role.Button }, Alignment.Center) { TextLabel(if(description == stringResource(R.string.back)) "‹" else "⋯", 28, ChalnaTheme.colors.text, FontWeight.Normal) }
@Composable private fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) = Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(if(enabled) Brush.linearGradient(listOf(ChalnaTheme.colors.accent, ChalnaTheme.colors.accent2)) else Brush.linearGradient(listOf(ChalnaTheme.colors.outline, ChalnaTheme.colors.outline))).clickableNoRipple(enabled, onClick).semantics { role = Role.Button }, Alignment.Center) { TextLabel(label, 16, Color.White, FontWeight.Bold) }
@Composable private fun SecondaryButton(label: String, onClick: () -> Unit) = Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, ChalnaTheme.colors.outline, RoundedCornerShape(18.dp)).clickableNoRipple(onClick), Alignment.Center) { TextLabel(label, 15, ChalnaTheme.colors.text, FontWeight.SemiBold) }
@Composable private fun Heading(text: String) = TextLabel(text, 27, ChalnaTheme.colors.text, FontWeight.Bold, Modifier.semantics { heading() })
@Composable private fun Section(id: Int) = TextLabel(stringResource(id), 14, ChalnaTheme.colors.accent, FontWeight.Bold)
@Composable private fun Body(text: String) = TextLabel(text, 15, ChalnaTheme.colors.muted)
@Composable private fun TextLabel(text: String, size: Int, color: Color, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) = androidx.compose.foundation.text.BasicText(text, modifier, style = androidx.compose.ui.text.TextStyle(color = color, fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.4).sp, textAlign = align))
private fun Modifier.clickableNoRipple(enabled: Boolean = true, click: () -> Unit) = clickable(MutableInteractionSource(), indication = null, enabled = enabled, role = Role.Button, onClick = click)
@Composable private fun phaseTitle(s: ChalnaUiState) = stringResource(when(s.phase) { CapturePhase.READY -> R.string.phase_ready; CapturePhase.STARTING -> R.string.phase_starting; CapturePhase.RECORDING -> R.string.phase_recording; CapturePhase.STOPPING -> R.string.phase_stopping; CapturePhase.SAVED -> R.string.phase_saved; CapturePhase.ERROR -> R.string.phase_error })
private fun formatDuration(seconds: Long) = "%02d:%02d".format(seconds / 60, seconds % 60)
