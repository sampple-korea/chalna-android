package app.chalna.capture.ui

import android.view.Surface
import android.graphics.Bitmap
import android.os.CancellationSignal
import kotlinx.coroutines.flow.StateFlow

enum class CapturePhase { READY, STARTING, RECORDING, STOPPING, SAVED, ERROR }
enum class VideoQuality { AUTO, FHD, HD }
enum class AppearanceMode { NIGHT, MIST, SYSTEM }
enum class MotionMode { SYSTEM, FULL, REDUCED }
enum class StorageDestinationUi { DEVICE_GALLERY, CHALNA_VAULT }
enum class GalleryFilter { ALL, DEVICE_GALLERY, CHALNA_VAULT }

data class MediaItemUi(
    val id: String,
    val displayName: String,
    val contentUri: String,
    val mimeType: String = "video/mp4",
    val destination: StorageDestinationUi = StorageDestinationUi.DEVICE_GALLERY,
    val capturedAtMillis: Long,
    val durationMillis: Long = 0,
    val sizeBytes: Long = 0,
    val hasAudio: Boolean = true,
    val width: Int = 0,
    val height: Int = 0,
)

data class PlayerUiState(
    val item: MediaItemUi,
    val positionMillis: Long = 0,
    val durationMillis: Long = item.durationMillis,
    val playing: Boolean = false,
    val muted: Boolean = false,
    val buffering: Boolean = false,
    val message: String? = null,
)

data class ChalnaUiState(
    val setupComplete: Boolean = false,
    val phase: CapturePhase = CapturePhase.READY,
    val durationSeconds: Long = 0,
    val lastCapture: MediaItemUi? = null,
    val lastSavedName: String? = null,
    val errorMessage: String? = null,
    val operationMessage: String? = null,
    val quality: VideoQuality = VideoQuality.AUTO,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val haptics: Boolean = true,
    val sound: Boolean = true,
    val autoStopSeconds: Int = 0,
    val reducedMotion: Boolean = false,
    val motion: MotionMode = MotionMode.SYSTEM,
    val assistantSelected: Boolean = false,
    val cameraGranted: Boolean = false,
    val microphoneGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val cameraPermanentlyDenied: Boolean = false,
    val microphonePermanentlyDenied: Boolean = false,
    val notificationsPermanentlyDenied: Boolean = false,
    val powerSaver: Boolean = false,
    val ready: Boolean = true,
    val gallery: List<MediaItemUi> = emptyList(),
    val galleryFilter: GalleryFilter = GalleryFilter.ALL,
    val storageDestination: StorageDestinationUi = StorageDestinationUi.DEVICE_GALLERY,
    val selectedMediaIds: Set<String> = emptySet(),
    val player: PlayerUiState? = null,
)

/**
 * Narrow UI integration seam. Implementations must not bind/open capture hardware before
 * [toggleCapture]. Player surface binding applies only to already-saved media.
 */
interface UiDependencies {
    val state: StateFlow<ChalnaUiState>
    fun toggleCapture()
    fun requestCamera()
    fun requestMicrophone()
    fun requestNotifications()
    fun openAssistantSettings()
    fun openAppSettings() = Unit
    fun openNotificationSettings() = Unit
    fun refreshSetup() = Unit
    fun finishSetup()
    fun setQuality(value: VideoQuality)
    fun setAppearance(value: AppearanceMode)
    fun setHaptics(value: Boolean)
    fun setSound(value: Boolean)
    fun setAutoStop(seconds: Int)
    fun setReducedMotion(value: Boolean)
    fun setMotion(value: MotionMode) = setReducedMotion(value == MotionMode.REDUCED)
    fun setStorageDestination(value: StorageDestinationUi) = Unit
    fun openLastCapture()
    fun reviewSetup()

    fun refreshGallery() = Unit
    /** Decode a visible thumbnail. Implementations may support MediaStore and private vault URIs. */
    fun loadThumbnail(uri: String, sizePx: Int, cancellationSignal: CancellationSignal): Bitmap? = null
    fun setGalleryFilter(value: GalleryFilter) = Unit
    fun toggleMediaSelection(id: String) = Unit
    fun clearMediaSelection() = Unit
    fun deleteSelectedMedia() = Unit
    fun shareSelectedMedia() = Unit
    fun exportSelectedMedia() = Unit
    fun openPlayer(id: String) = Unit
    fun closePlayer() = Unit
    fun bindPlayerSurface(surface: Surface?) = Unit
    fun togglePlayback() = Unit
    fun seekPlayer(positionMillis: Long) = Unit
    fun setPlayerMuted(muted: Boolean) = Unit
    fun shareCurrentMedia() = Unit
    fun deleteCurrentMedia() = Unit
    fun exportCurrentMedia() = Unit
    fun openCurrentMediaExternally() = openLastCapture()
}
