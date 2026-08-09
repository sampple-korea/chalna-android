package app.chalna.capture.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.chalna.capture.assistant.ChalnaInvocationGlowView
import app.chalna.capture.assistant.InvocationPulseKind
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ChalnaScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeReadyMist() = captureApp("home-ready-mist", ChalnaScreenshotStates.homeReady)
    @Test fun homeRecordingNight() = captureApp("home-recording-night", ChalnaScreenshotStates.homeRecording)
    @Test fun setupMist() = captureApp("setup-mist", ChalnaScreenshotStates.setup)
    @Test fun settingsNight() = captureApp("settings-night", ChalnaScreenshotStates.homeReadyNight) { onNodeWithContentDescription("Settings").performClick() }
    @Test fun galleryNight() = captureApp("gallery-night", ChalnaScreenshotStates.gallery) { onNodeWithContentDescription("Gallery").performClick() }
    @Test fun gallerySelectionNight() = captureApp("gallery-selection-night", ChalnaScreenshotStates.gallerySelected) { onNodeWithContentDescription("Gallery").performClick() }
    @Test fun playerNight() = captureApp("player-night", ChalnaScreenshotStates.player)
    @Test fun homeLargeFontMist() = captureContent("home-large-font-mist", {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) { ChalnaApp(ScreenshotDependencies(ChalnaScreenshotStates.homeReady)) }
    })
    @Test fun glowStart000Night() = glow("glow-start-000-night", CapturePhase.STARTING, 0f, NightColors)
    @Test fun glowStart016Night() = glow("glow-start-016-night", CapturePhase.STARTING, .16f, NightColors)
    @Test fun glowStart034Night() = glow("glow-start-034-night", CapturePhase.STARTING, .34f, NightColors)
    @Test fun glowStart050Night() = glow("glow-start-050-night", CapturePhase.STARTING, .5f, NightColors)
    @Test fun glowStart078Night() = glow("glow-start-078-night", CapturePhase.STARTING, .78f, NightColors)
    @Test fun glowStop050Mist() = glow("glow-stop-050-mist", CapturePhase.STOPPING, .5f, MistColors)
    @Test fun glowError050Night() = glow("glow-error-050-night", CapturePhase.ERROR, .5f, NightColors)
    @Test fun iconMaskCircle() = icon("icon-mask-circle", CircleShape)
    @Test fun iconMaskSquircle() = icon("icon-mask-squircle", RoundedCornerShape(24.dp))
    @Test fun iconMaskSquare() = icon("icon-mask-square", RoundedCornerShape(8.dp))

    private fun glow(name: String, phase: CapturePhase, progress: Float, colors: ChalnaColors) = captureContent(name, {
        val kind = when (phase) { CapturePhase.STOPPING -> InvocationPulseKind.STOP; CapturePhase.ERROR -> InvocationPulseKind.ERROR; else -> InvocationPulseKind.START }
        CompositionLocalProvider(LocalChalnaColors provides colors) { Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            AndroidView(factory = { context -> ChalnaInvocationGlowView(context).also { it.preview(kind, progress) } }, update = { it.preview(kind, progress) }, modifier = Modifier.size(300.dp))
        } }
    })

    private fun icon(name: String, shape: androidx.compose.ui.graphics.Shape) = captureContent(name, {
        CompositionLocalProvider(LocalChalnaColors provides NightColors) { Box(Modifier.fillMaxSize().background(NightColors.background), contentAlignment = Alignment.Center) { Box(Modifier.size(220.dp).clip(shape).background(NightColors.surfaceHigh), contentAlignment = Alignment.Center) { ChalnaIconCanvas(ChalnaIcon.MARK, Modifier.size(130.dp), NightColors.accent) } } }
    })

    private fun captureApp(name: String, state: ChalnaUiState, prepare: ComposeTestRule.() -> Unit = {}) = captureContent(name, { ChalnaApp(ScreenshotDependencies(state)) }, prepare)

    private fun captureContent(name: String, content: @Composable () -> Unit, prepare: ComposeTestRule.() -> Unit = {}) {
        compose.setContent(content); compose.waitForIdle(); compose.prepare(); compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}

private class ScreenshotDependencies(initial: ChalnaUiState) : UiDependencies {
    override val state = MutableStateFlow(initial)
    override fun toggleCapture() = Unit
    override fun requestCamera() = Unit
    override fun requestMicrophone() = Unit
    override fun requestNotifications() = Unit
    override fun openAssistantSettings() = Unit
    override fun finishSetup() = Unit
    override fun setQuality(value: VideoQuality) = Unit
    override fun setAppearance(value: AppearanceMode) = Unit
    override fun setHaptics(value: Boolean) = Unit
    override fun setSound(value: Boolean) = Unit
    override fun setAutoStop(seconds: Int) = Unit
    override fun setReducedMotion(value: Boolean) = Unit
    override fun openLastCapture() = Unit
    override fun reviewSetup() = Unit
}
