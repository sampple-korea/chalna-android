package app.chalna.capture.ui

import kotlinx.coroutines.flow.StateFlow

enum class CapturePhase { READY, STARTING, RECORDING, STOPPING, SAVED, ERROR }
enum class VideoQuality { AUTO, FHD, HD }
enum class AppearanceMode { NIGHT, MIST, SYSTEM }

data class ChalnaUiState(
    val setupComplete: Boolean = false,
    val phase: CapturePhase = CapturePhase.READY,
    val durationSeconds: Long = 0,
    val lastSavedName: String? = null,
    val errorMessage: String? = null,
    val quality: VideoQuality = VideoQuality.AUTO,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val haptics: Boolean = true,
    val sound: Boolean = true,
    val autoStopSeconds: Int = 0,
    val reducedMotion: Boolean = false,
    val assistantSelected: Boolean = false,
    val cameraGranted: Boolean = false,
    val microphoneGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val powerSaver: Boolean = false,
    val ready: Boolean = true,
    val diagnosticLines: List<String> = emptyList(),
)

/** Narrow integration seam. Implementations must never bind or open the camera before [toggleCapture]. */
interface UiDependencies {
    val state: StateFlow<ChalnaUiState>
    fun toggleCapture()
    fun requestCamera()
    fun requestMicrophone()
    fun requestNotifications()
    fun openAssistantSettings()
    fun finishSetup()
    fun setQuality(value: VideoQuality)
    fun setAppearance(value: AppearanceMode)
    fun setHaptics(value: Boolean)
    fun setSound(value: Boolean)
    fun setAutoStop(seconds: Int)
    fun setReducedMotion(value: Boolean)
    fun runDiagnosticCapture()
    fun openLastCapture()
    fun copyDiagnostics()
    fun clearDiagnostics()
    fun reviewSetup()
}
