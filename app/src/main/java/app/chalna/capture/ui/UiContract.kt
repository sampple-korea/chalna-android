package app.chalna.capture.ui

import android.graphics.Bitmap
import android.os.CancellationSignal
import android.view.SurfaceView
import kotlinx.coroutines.flow.StateFlow

enum class CapturePhase { SETUP_REQUIRED, READY, STARTING, RECORDING, STOPPING, SAVED, ERROR }
enum class VideoQuality { AUTO, FHD, HD }
enum class AppearanceMode { NIGHT, MIST, SYSTEM }
enum class MotionMode { SYSTEM, FULL, REDUCED }
enum class StorageDestinationUi { DEVICE_GALLERY, CHALNA_VAULT }
enum class GalleryFilter { ALL, DEVICE_GALLERY, CHALNA_VAULT, FAVORITES, TRASH }
enum class GallerySortUi { NEWEST, OLDEST, LONGEST, LARGEST }
enum class PlayerPhase { PREPARING, BUFFERING, READY, ENDED, ERROR, SOURCE_MISSING }

sealed interface UiOperationEvent {
    data class Started(val operation: String, val total: Int) : UiOperationEvent
    data class Progress(val operation: String, val completed: Int, val total: Int) : UiOperationEvent
    data class Succeeded(val messageResource: Int) : UiOperationEvent
    data class PartiallyFailed(val messageResource: Int, val succeeded: Int, val failed: Int) : UiOperationEvent
    data class Failed(val messageResource: Int) : UiOperationEvent
}

data class MediaItemUi(
    val id: String,
    val displayName: String,
    val contentUri: String,
    val mimeType: String = "video/mp4",
    val destination: StorageDestinationUi = StorageDestinationUi.DEVICE_GALLERY,
    val capturedAtMillis: Long,
    val durationMillis: Long = 0,
    val sizeBytes: Long = 0,
    val hasAudio: Boolean? = true,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val codec: String? = null,
    val frameRate: Float? = null,
    val favorite: Boolean = false,
    val trashed: Boolean = false,
)

data class PlayerUiState(
    val item: MediaItemUi,
    val phase: PlayerPhase = PlayerPhase.PREPARING,
    val positionMillis: Long = 0,
    val durationMillis: Long = item.durationMillis,
    val bufferedMillis: Long = 0,
    val playing: Boolean = false,
    val muted: Boolean = false,
    val playbackSpeed: Float = 1f,
    val recordingConflict: Boolean = false,
    val keepScreenOn: Boolean = false,
)

data class StorageSummaryUi(
    val loading: Boolean = true,
    val deviceGalleryCount: Int = 0,
    val vaultCount: Int = 0,
    val vaultBytes: Long = 0,
    val trashBytes: Long = 0,
)

data class ChalnaUiState(
    val setupComplete: Boolean = false,
    val phase: CapturePhase = CapturePhase.SETUP_REQUIRED,
    val durationSeconds: Long = 0,
    val recordingStartedElapsedNanos: Long = 0,
    val lastCapture: MediaItemUi? = null,
    val errorCode: String? = null,
    val operationEvent: UiOperationEvent? = null,
    val quality: VideoQuality = VideoQuality.AUTO,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val haptics: Boolean = true,
    val sound: Boolean = true,
    val autoStopSeconds: Int = 0,
    val reducedMotion: Boolean = false,
    val motion: MotionMode = MotionMode.SYSTEM,
    val assistantSelected: Boolean = false,
    val assistantAvailable: Boolean = true,
    val cameraGranted: Boolean = false,
    val microphoneGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val cameraPermanentlyDenied: Boolean = false,
    val microphonePermanentlyDenied: Boolean = false,
    val powerSaver: Boolean = false,
    val ready: Boolean = false,
    val gallery: List<MediaItemUi> = emptyList(),
    val galleryLoading: Boolean = false,
    val galleryError: Boolean = false,
    val galleryFilter: GalleryFilter = GalleryFilter.ALL,
    val gallerySort: GallerySortUi = GallerySortUi.NEWEST,
    val storageDestination: StorageDestinationUi = StorageDestinationUi.DEVICE_GALLERY,
    val storageSummary: StorageSummaryUi = StorageSummaryUi(),
    val selectedMediaIds: Set<String> = emptySet(),
    val player: PlayerUiState? = null,
)

interface UiDependencies {
    val state: StateFlow<ChalnaUiState>
    fun toggleCapture()
    fun requestCamera()
    fun requestMicrophone()
    fun requestNotifications()
    fun openAssistantSettings()
    fun openAppSettings()
    fun openNotificationSettings()
    fun refreshSetup()
    fun finishSetup()
    fun setQuality(value: VideoQuality)
    fun setAppearance(value: AppearanceMode)
    fun setHaptics(value: Boolean)
    fun setSound(value: Boolean)
    fun setAutoStop(seconds: Int)
    fun setMotion(value: MotionMode)
    fun setStorageDestination(value: StorageDestinationUi)
    fun openLastCapture()
    fun reviewSetup()
    fun requestQuickTile()

    fun refreshGallery()
    fun loadThumbnail(uri: String, sizePx: Int, cancellationSignal: CancellationSignal): Bitmap?
    fun setGalleryFilter(value: GalleryFilter)
    fun setGallerySort(value: GallerySortUi)
    fun toggleMediaSelection(id: String)
    fun selectAllMedia()
    fun clearMediaSelection()
    fun trashSelectedMedia()
    fun restoreSelectedMedia()
    fun deleteSelectedMediaPermanently()
    fun favoriteSelectedMedia(favorite: Boolean)
    fun shareSelectedMedia()
    fun exportSelectedMedia(forceCopy: Boolean = false)
    fun emptyTrash()
    fun exportAllVault()
    fun openPlayer(id: String)
    fun closePlayer()
    fun bindPlayerView(view: SurfaceView?)
    fun togglePlayback()
    fun seekPlayer(positionMillis: Long)
    fun seekPlayerBy(deltaMillis: Long)
    fun setPlayerMuted(muted: Boolean)
    fun setPlaybackSpeed(speed: Float)
    fun retryPlayback()
    fun shareCurrentMedia()
    fun trashCurrentMedia()
    fun exportCurrentMedia(forceCopy: Boolean = false)
    fun openCurrentMediaExternally()
    fun consumeOperationEvent()
}
