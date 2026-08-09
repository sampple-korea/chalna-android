package app.chalna.capture.ui

/** Stable fixtures consumed by CI screenshot capture. */
object ChalnaScreenshotStates {
    val deviceVideo = MediaItemUi(id="device-1", displayName="CHALNA_20260809_172300.mp4", contentUri="content://fixture/device-1", capturedAtMillis=1_786_273_800_000, durationMillis=65_000, sizeBytes=12_400_000, hasAudio=true, width=1920, height=1080)
    val vaultVideo = MediaItemUi(id="vault-1", displayName="Private moment.mp4", contentUri="content://fixture/vault-1", capturedAtMillis=1_786_187_400_000, durationMillis=18_000, sizeBytes=4_100_000, hasAudio=false, width=1280, height=720, destination=StorageDestinationUi.CHALNA_VAULT)
    val homeReady = ChalnaUiState(setupComplete = true, appearance = AppearanceMode.MIST, reducedMotion = true, ready = true, lastCapture = deviceVideo, lastSavedName = deviceVideo.displayName)
    val homeReadyNight = homeReady.copy(appearance = AppearanceMode.NIGHT)
    val homeRecording = homeReadyNight.copy(phase = CapturePhase.RECORDING, durationSeconds = 65)
    val setup = ChalnaUiState(appearance = AppearanceMode.MIST, reducedMotion = true, cameraGranted = true, microphoneGranted = true)
    val gallery = homeReadyNight.copy(gallery = listOf(deviceVideo, vaultVideo))
    val gallerySelected = gallery.copy(selectedMediaIds = setOf(deviceVideo.id))
    val player = gallery.copy(player = PlayerUiState(deviceVideo, 24_000, 65_000, playing = true))
}
