package app.chalna.capture.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ChalnaUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun setupDoesNotRequestHardwareBeforeUserAction() {
        val fake = FakeUiDependencies(ChalnaUiState(reducedMotion = true))
        compose.setContent { ChalnaApp(fake) }
        compose.onNodeWithText("Set up Chalna").assertIsDisplayed()
        check(fake.hardwareRequestCount == 0); check(fake.toggleCount == 0)
    }

    @Test fun recordingStateOffersExplicitStop() {
        val fake = FakeUiDependencies(ChalnaUiState(setupComplete = true, ready = true, phase = CapturePhase.RECORDING, durationSeconds = 65, reducedMotion = true))
        compose.setContent { ChalnaApp(fake) }
        compose.onNodeWithText("01:05").assertIsDisplayed(); compose.onNodeWithText("Stop recording").performClick()
        check(fake.toggleCount == 1)
    }

    @Test fun consolidatedSettingsIsReachable() {
        val fake = FakeUiDependencies(ChalnaUiState(setupComplete = true, ready = true, reducedMotion = true))
        compose.setContent { ChalnaApp(fake) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Capture").assertIsDisplayed(); compose.onNodeWithText("System").assertIsDisplayed()
    }

    @Test fun galleryLongPressSelectionHasBatchActions() {
        val fake = FakeUiDependencies(ChalnaScreenshotStates.gallerySelected)
        compose.setContent { ChalnaApp(fake) }
        compose.onNodeWithContentDescription("Gallery").performClick()
        compose.onNodeWithContentDescription("Share").assertIsDisplayed(); compose.onNodeWithContentDescription("Delete").assertIsDisplayed()
    }

    @Test fun productionHomeHasNoDiagnosticsAndNotificationDenialDoesNotBlockReady() {
        val fake = FakeUiDependencies(
            ChalnaUiState(
                setupComplete = true,
                ready = true,
                cameraGranted = true,
                microphoneGranted = true,
                assistantSelected = true,
                notificationsGranted = false,
                reducedMotion = true,
            ),
        )
        compose.setContent { ChalnaApp(fake) }
        compose.onNodeWithText("Ready").assertIsDisplayed()
        check(compose.onAllNodesWithText("Diagnostics").fetchSemanticsNodes().isEmpty())
    }

    @Test fun audioOffSetupNeverRequestsMicrophone() {
        val fake = FakeUiDependencies(
            ChalnaUiState(
                sound = false,
                cameraGranted = true,
                assistantSelected = true,
                reducedMotion = true,
            ),
        )
        compose.setContent { ChalnaApp(fake) }
        compose.onNodeWithText("Finish setup").performClick()
        check(fake.hardwareRequestCount == 0)
        check(fake.microphoneRequestCount == 0)
    }
}

internal class FakeUiDependencies(initial: ChalnaUiState) : UiDependencies {
    override val state = MutableStateFlow(initial)
    var hardwareRequestCount = 0; var microphoneRequestCount = 0; var toggleCount = 0
    override fun toggleCapture() { toggleCount++ }
    override fun requestCamera() { hardwareRequestCount++ }
    override fun requestMicrophone() { hardwareRequestCount++; microphoneRequestCount++ }
    override fun requestNotifications() = Unit
    override fun openAssistantSettings() = Unit
    override fun finishSetup() { state.value = state.value.copy(setupComplete = true) }
    override fun setQuality(value: VideoQuality) { state.value = state.value.copy(quality = value) }
    override fun setAppearance(value: AppearanceMode) { state.value = state.value.copy(appearance = value) }
    override fun setHaptics(value: Boolean) { state.value = state.value.copy(haptics = value) }
    override fun setSound(value: Boolean) { state.value = state.value.copy(sound = value) }
    override fun setAutoStop(seconds: Int) { state.value = state.value.copy(autoStopSeconds = seconds) }
    override fun setReducedMotion(value: Boolean) { state.value = state.value.copy(reducedMotion = value) }
    override fun setMotion(value: MotionMode) { state.value = state.value.copy(motion = value, reducedMotion = value == MotionMode.REDUCED) }
    override fun openLastCapture() = Unit
    override fun reviewSetup() { state.value = state.value.copy(setupComplete = false) }
}
