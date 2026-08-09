package app.chalna.capture.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ChalnaScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeReadyMist() = capture("home-ready-mist", ChalnaScreenshotStates.homeReady)
    @Test fun homeRecordingNight() = capture("home-recording-night", ChalnaScreenshotStates.homeRecording)
    @Test fun homeSavedMist() = capture("home-saved-mist", ChalnaScreenshotStates.homeSaved)
    @Test fun homeErrorNight() = capture("home-error-night", ChalnaScreenshotStates.homeError)

    private fun capture(name: String, state: ChalnaUiState) {
        val fake = ScreenshotDependencies(state)
        compose.setContent { ChalnaApp(fake) }
        compose.waitForIdle()
        val bitmap = runCatching { compose.onRoot().captureToImage().asAndroidBitmap() }
            .getOrElse {
                Thread.sleep(500)
                compose.onRoot().captureToImage().asAndroidBitmap()
            }
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots",
        ).apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
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
    override fun runDiagnosticCapture() = Unit
    override fun openLastCapture() = Unit
    override fun copyDiagnostics() = Unit
    override fun clearDiagnostics() = Unit
}
