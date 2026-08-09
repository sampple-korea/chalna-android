package app.chalna.capture.ui

/** Stable fixtures consumed by CI screenshot capture. */
object ChalnaScreenshotStates {
    val homeReady = ChalnaUiState(setupComplete = true, appearance = AppearanceMode.MIST, reducedMotion = true)
    val homeRecording = ChalnaUiState(setupComplete = true, phase = CapturePhase.RECORDING, durationSeconds = 65, appearance = AppearanceMode.NIGHT, reducedMotion = true)
    val homeSaved = ChalnaUiState(setupComplete = true, phase = CapturePhase.SAVED, lastSavedName = "CHALNA_20260809_172300.mp4", appearance = AppearanceMode.MIST, reducedMotion = true)
    val homeError = ChalnaUiState(setupComplete = true, phase = CapturePhase.ERROR, errorMessage = "Camera unavailable", appearance = AppearanceMode.NIGHT, reducedMotion = true)
}
