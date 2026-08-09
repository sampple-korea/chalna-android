package app.chalna.capture.ui

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import app.chalna.capture.assistant.ChalnaInvocationGlowView
import app.chalna.capture.assistant.InvocationPulseKind
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ChalnaScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeReadyMist() = captureApp("home-ready-mist", ChalnaScreenshotStates.homeReady)
    @Test fun homeRecordingNight() = captureApp("home-recording-night", ChalnaScreenshotStates.homeRecording)
    @Test fun setupMist() = captureApp("setup-mist", ChalnaScreenshotStates.setup)
    @Test fun settingsNight() = captureApp("settings-night", ChalnaScreenshotStates.homeReadyNight) { onNodeWithContentDescription("설정").performClick() }
    @Test fun galleryNight() = captureApp("gallery-night", ChalnaScreenshotStates.gallery) { onNodeWithContentDescription("갤러리").performClick() }
    @Test fun gallerySelectionNight() = captureApp("gallery-selection-night", ChalnaScreenshotStates.gallerySelected) { onNodeWithContentDescription("갤러리").performClick() }
    @Test fun galleryEmptyNight() = captureApp("gallery-empty-night", ChalnaScreenshotStates.galleryEmpty) { onNodeWithContentDescription("갤러리").performClick() }
    @Test fun galleryVaultFilterNight() = captureApp("gallery-vault-filter-night", ChalnaScreenshotStates.galleryVault) { onNodeWithContentDescription("갤러리").performClick() }
    @Test fun galleryVaultSelectionNight() = captureApp("gallery-vault-selection-night", ChalnaScreenshotStates.galleryVaultSelected) { onNodeWithContentDescription("갤러리").performClick() }
    @Test fun galleryDeleteConfirmationNight() = captureApp("gallery-delete-confirmation-night", ChalnaScreenshotStates.gallerySelected) { onNodeWithContentDescription("갤러리").performClick(); onNodeWithContentDescription("삭제").performClick() }
    @Test fun galleryMist() = captureApp("gallery-mist", ChalnaScreenshotStates.galleryMist) { onNodeWithContentDescription("갤러리").performClick() }
    @Test fun playerNight() = captureApp("player-night", ChalnaScreenshotStates.player)
    @Test fun playerControlsHiddenNight() = captureApp("player-controls-hidden-night", ChalnaScreenshotStates.player) { onNodeWithTag("player_touch_surface").performClick() }
    @Test fun playerInfoNight() = captureApp("player-info-night", ChalnaScreenshotStates.player) { onNodeWithContentDescription("더보기").performClick(); onNodeWithText("세부정보").performClick() }
    @Test fun playerDeleteConfirmationNight() = captureApp("player-delete-confirmation-night", ChalnaScreenshotStates.player) { onNodeWithContentDescription("더보기").performClick(); onNodeWithText("삭제").performClick() }
    @Test fun playerVaultNight() = captureApp("player-vault-night", ChalnaScreenshotStates.vaultPlayer) { onNodeWithContentDescription("더보기").performClick() }
    @Test fun settingsCameraDeniedNight() = captureApp("settings-camera-denied-night", ChalnaScreenshotStates.settingsCameraDenied) { onNodeWithContentDescription("설정").performClick(); onAllNodesWithText("시스템")[1].performScrollTo() }
    @Test fun settingsAudioOffNight() = captureApp("settings-audio-off-night", ChalnaScreenshotStates.settingsAudioOff) { onNodeWithContentDescription("설정").performClick() }
    @Test fun settingsAssistantMissingNight() = captureApp("settings-assistant-missing-night", ChalnaScreenshotStates.settingsAssistantMissing) { onNodeWithContentDescription("설정").performClick(); onAllNodesWithText("시스템")[1].performScrollTo() }
    @Test fun settingsNotificationOptionalNight() = captureApp("settings-notification-optional-night", ChalnaScreenshotStates.settingsNotificationOptional) { onNodeWithContentDescription("설정").performClick(); onAllNodesWithText("시스템")[1].performScrollTo() }
    @Test fun settingsVaultMist() = captureApp("settings-vault-mist", ChalnaScreenshotStates.settingsVaultMist) { onNodeWithContentDescription("설정").performClick() }
    @Test fun homeLargeFontMist() = captureContent("home-large-font-mist", {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { ChalnaApp(ScreenshotDependencies(ChalnaScreenshotStates.homeReady)) }
    })
    @Test fun galleryLargeFontNight() = captureLargeApp("gallery-large-font-night", ChalnaScreenshotStates.gallery) { onNodeWithContentDescription("갤러리").performClick() }
    @Test fun settingsLargeFontMist() = captureLargeApp("settings-large-font-mist", ChalnaScreenshotStates.settingsVaultMist) { onNodeWithContentDescription("설정").performClick() }
    @Test fun glowStart000Night() = glow("glow-start-000-night", CapturePhase.STARTING, 0f, NightColors)
    @Test fun glowStart016Night() = glow("glow-start-016-night", CapturePhase.STARTING, .16f, NightColors)
    @Test fun glowStart034Night() = glow("glow-start-034-night", CapturePhase.STARTING, .34f, NightColors)
    @Test fun glowStart050Night() = glow("glow-start-050-night", CapturePhase.STARTING, .5f, NightColors)
    @Test fun glowStart078Night() = glow("glow-start-078-night", CapturePhase.STARTING, .78f, NightColors)
    @Test fun glowStop050Mist() = glow("glow-stop-050-mist", CapturePhase.STOPPING, .5f, MistColors)
    @Test fun glowError050Night() = glow("glow-error-050-night", CapturePhase.ERROR, .5f, NightColors)
    @Test fun glowTallColorfulNight() = perimeterGlow("glow-start-tall-colorful-night", InvocationPulseKind.START, .5f, 250, 560, true)
    @Test fun glowCompactRoundedNight() = perimeterGlow("glow-start-compact-rounded-night", InvocationPulseKind.START, .34f, 220, 390, false)
    @Test fun glowWideCutoutMist() = perimeterGlow("glow-stop-wide-cutout-mist", InvocationPulseKind.STOP, .5f, 380, 230, true)
    @Test fun iconMaskCircle() = icon("icon-mask-circle", CircleShape)
    @Test fun iconMaskSquircle() = icon("icon-mask-squircle", RoundedCornerShape(24.dp))
    @Test fun iconMaskSquare() = icon("icon-mask-square", RoundedCornerShape(8.dp))
    @Test fun iconMaskMonochrome() = icon("icon-mask-monochrome", RoundedCornerShape(24.dp), monochrome = true)

    private fun glow(name: String, phase: CapturePhase, progress: Float, colors: ChalnaColors) = captureContent(name, {
        val kind = when (phase) { CapturePhase.STOPPING -> InvocationPulseKind.STOP; CapturePhase.ERROR -> InvocationPulseKind.ERROR; else -> InvocationPulseKind.START }
        CompositionLocalProvider(LocalChalnaColors provides colors) { Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            AndroidView(factory = { context -> ChalnaInvocationGlowView(context).also { it.preview(kind, progress) } }, update = { it.preview(kind, progress) }, modifier = Modifier.size(300.dp))
        } }
    })

    private fun perimeterGlow(name: String, kind: InvocationPulseKind, progress: Float, width: Int, height: Int, colorful: Boolean) = captureContent(name, {
        CompositionLocalProvider(LocalChalnaColors provides NightColors) {
            Box(Modifier.fillMaxSize().background(NightColors.background), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(width.dp, height.dp).clip(RoundedCornerShape(28.dp)).background(
                        if (colorful) Brush.linearGradient(listOf(Color(0xFF152A4A), Color(0xFF472957), Color(0xFF173C38)))
                        else Brush.linearGradient(listOf(Color(0xFF0B0F19), Color(0xFF151B29))),
                    ),
                ) {
                    AndroidView(factory = { context -> ChalnaInvocationGlowView(context).also { it.preview(kind, progress) } }, update = { it.preview(kind, progress) }, modifier = Modifier.fillMaxSize())
                    if (name.contains("cutout")) Box(Modifier.align(Alignment.TopCenter).size(76.dp, 22.dp).clip(RoundedCornerShape(bottomStart = 13.dp, bottomEnd = 13.dp)).background(Color.Black))
                }
            }
        }
    })

    private fun icon(name: String, shape: androidx.compose.ui.graphics.Shape, monochrome: Boolean = false) = captureContent(name, {
        CompositionLocalProvider(LocalChalnaColors provides NightColors) { Box(Modifier.fillMaxSize().background(NightColors.background), contentAlignment = Alignment.Center) { Box(Modifier.size(220.dp).clip(shape).background(NightColors.surfaceHigh), contentAlignment = Alignment.Center) { Image(painterResource(if (monochrome) app.chalna.capture.R.drawable.ic_chalna_monochrome else app.chalna.capture.R.drawable.ic_chalna_mark), null, Modifier.fillMaxSize(), colorFilter = if (monochrome) ColorFilter.tint(Color.White) else null) } } }
    })

    private fun captureApp(name: String, state: ChalnaUiState, prepare: ComposeTestRule.() -> Unit = {}) = captureContent(name, { ChalnaApp(ScreenshotDependencies(state)) }, prepare)

    private fun captureLargeApp(name: String, state: ChalnaUiState, prepare: ComposeTestRule.() -> Unit = {}) = captureContent(name, {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { ChalnaApp(ScreenshotDependencies(state)) }
    }, prepare)

    private fun captureContent(name: String, content: @Composable () -> Unit, prepare: ComposeTestRule.() -> Unit = {}) {
        compose.setContent {
            val baseContext = LocalContext.current
            val localizedConfiguration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.KOREA) }
            val localizedContext = baseContext.createConfigurationContext(localizedConfiguration)
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
            ) { content() }
        }
        compose.waitForIdle(); compose.prepare(); compose.waitForIdle()
        val bitmap = captureRootWithRetry()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun captureRootWithRetry(): Bitmap {
        var lastTimeout: ComposeTimeoutException? = null
        repeat(SCREENSHOT_CAPTURE_ATTEMPTS) {
            try {
                return compose.onRoot().captureToImage().asAndroidBitmap()
            } catch (timeout: ComposeTimeoutException) {
                lastTimeout = timeout
                compose.waitForIdle()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
        }
        throw requireNotNull(lastTimeout)
    }

    private companion object {
        const val SCREENSHOT_CAPTURE_ATTEMPTS = 3
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
