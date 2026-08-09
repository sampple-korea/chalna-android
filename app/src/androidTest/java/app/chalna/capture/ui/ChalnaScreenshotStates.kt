package app.chalna.capture.ui

/** Stable fixtures consumed by CI screenshot capture. */
object ChalnaScreenshotStates {
    val homeReady = ChalnaUiState(setupComplete = true, appearance = AppearanceMode.MIST, reducedMotion = true)
    val homeReadyNight = homeReady.copy(appearance = AppearanceMode.NIGHT)
    val homeStarting = homeReadyNight.copy(phase = CapturePhase.STARTING)
    val homeRecording = ChalnaUiState(setupComplete = true, phase = CapturePhase.RECORDING, durationSeconds = 65, appearance = AppearanceMode.NIGHT, reducedMotion = true)
    val homeSaving = homeRecording.copy(phase = CapturePhase.STOPPING)
    val homeSaved = ChalnaUiState(setupComplete = true, phase = CapturePhase.SAVED, lastSavedName = "CHALNA_20260809_172300.mp4", appearance = AppearanceMode.MIST, reducedMotion = true)
    val homeError = ChalnaUiState(setupComplete = true, phase = CapturePhase.ERROR, errorMessage = "Camera unavailable", appearance = AppearanceMode.NIGHT, reducedMotion = true)
    val setupCamera = ChalnaUiState(appearance = AppearanceMode.MIST, reducedMotion = true)
    val setupPartial = setupCamera.copy(cameraGranted = true)
    val setupAssistant = setupCamera.copy(cameraGranted = true, microphoneGranted = true, notificationsGranted = true)
    val diagnosticsData = homeReadyNight.copy(diagnosticLines = listOf("state=Idle", "cameraPermission=true", "assistant_callback @ 42000ms", "service_receive @ 42012ms"))
}
