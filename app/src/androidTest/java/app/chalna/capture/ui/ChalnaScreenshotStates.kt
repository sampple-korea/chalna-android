package app.chalna.capture.ui

/** Stable fixtures consumed by CI screenshot capture. */
object ChalnaScreenshotStates {
    val deviceVideo = MediaItemUi(id="device-1", displayName="CHALNA_20260809_172300.mp4", contentUri="content://fixture/device-1", capturedAtMillis=1_786_273_800_000, durationMillis=65_000, sizeBytes=12_400_000, hasAudio=true, width=1920, height=1080)
    val vaultVideo = MediaItemUi(id="vault-1", displayName="Private moment.mp4", contentUri="content://fixture/vault-1", capturedAtMillis=1_786_187_400_000, durationMillis=18_000, sizeBytes=4_100_000, hasAudio=false, width=1280, height=720, destination=StorageDestinationUi.CHALNA_VAULT)
    val secondDeviceVideo = MediaItemUi(id="device-2", displayName="CHALNA_20260809_170500.mp4", contentUri="content://fixture/device-2", capturedAtMillis=1_786_272_300_000, durationMillis=42_000, sizeBytes=8_800_000, hasAudio=true, width=1080, height=1920)
    val homeReady = ChalnaUiState(
        setupComplete = true,
        appearance = AppearanceMode.MIST,
        reducedMotion = true,
        ready = true,
        assistantSelected = true,
        cameraGranted = true,
        microphoneGranted = true,
        notificationsGranted = true,
        lastCapture = deviceVideo,
        lastSavedName = deviceVideo.displayName,
    )
    val homeReadyNight = homeReady.copy(appearance = AppearanceMode.NIGHT)
    val homeRecording = homeReadyNight.copy(phase = CapturePhase.RECORDING, durationSeconds = 65)
    val setup = ChalnaUiState(appearance = AppearanceMode.MIST, reducedMotion = true, cameraGranted = true, microphoneGranted = true)
    val gallery = homeReadyNight.copy(gallery = listOf(deviceVideo, secondDeviceVideo, vaultVideo))
    val galleryMist = gallery.copy(appearance = AppearanceMode.MIST)
    val galleryEmpty = homeReadyNight.copy(lastCapture = null, gallery = emptyList())
    val galleryVault = gallery.copy(galleryFilter = GalleryFilter.CHALNA_VAULT)
    val gallerySelected = gallery.copy(selectedMediaIds = setOf(deviceVideo.id))
    val galleryVaultSelected = gallery.copy(selectedMediaIds = setOf(vaultVideo.id))
    val player = gallery.copy(player = PlayerUiState(deviceVideo, 24_000, 65_000, playing = true))
    val vaultPlayer = gallery.copy(player = PlayerUiState(vaultVideo, 4_000, 18_000))
    val settingsCameraDenied = homeReadyNight.copy(ready = false, cameraGranted = false, cameraPermanentlyDenied = true)
    val settingsAudioOff = homeReadyNight.copy(sound = false, microphoneGranted = false)
    val settingsAssistantMissing = homeReadyNight.copy(ready = false, assistantSelected = false)
    val settingsNotificationOptional = homeReadyNight.copy(notificationsGranted = false, ready = true)
    val settingsVaultMist = homeReady.copy(storageDestination = StorageDestinationUi.CHALNA_VAULT)
}
