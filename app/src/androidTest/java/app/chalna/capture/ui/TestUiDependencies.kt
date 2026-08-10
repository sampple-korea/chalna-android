package app.chalna.capture.ui

import android.graphics.Bitmap
import android.os.CancellationSignal
import android.view.SurfaceView
import kotlinx.coroutines.flow.MutableStateFlow

internal open class TestUiDependencies(initial: ChalnaUiState) : UiDependencies {
    override val state = MutableStateFlow(initial)
    override fun toggleCapture() = Unit
    override fun requestCamera() = Unit
    override fun requestMicrophone() = Unit
    override fun requestNotifications() = Unit
    override fun openAssistantSettings() = Unit
    override fun openAppSettings() = Unit
    override fun openNotificationSettings() = Unit
    override fun refreshSetup() = Unit
    override fun finishSetup() = Unit
    override fun setQuality(value: VideoQuality) = Unit
    override fun setAppearance(value: AppearanceMode) = Unit
    override fun setHaptics(value: Boolean) = Unit
    override fun setSound(value: Boolean) = Unit
    override fun setAutoStop(seconds: Int) = Unit
    override fun setMotion(value: MotionMode) = Unit
    override fun setStorageDestination(value: StorageDestinationUi) = Unit
    override fun openLastCapture() = Unit
    override fun reviewSetup() = Unit
    override fun requestQuickTile() = Unit
    override fun refreshGallery() = Unit
    override fun loadThumbnail(uri: String, sizePx: Int, cancellationSignal: CancellationSignal): Bitmap? = null
    override fun setGalleryFilter(value: GalleryFilter) = Unit
    override fun setGallerySort(value: GallerySortUi) = Unit
    override fun toggleMediaSelection(id: String) = Unit
    override fun selectAllMedia() = Unit
    override fun clearMediaSelection() = Unit
    override fun trashSelectedMedia() = Unit
    override fun restoreSelectedMedia() = Unit
    override fun deleteSelectedMediaPermanently() = Unit
    override fun favoriteSelectedMedia(favorite: Boolean) = Unit
    override fun shareSelectedMedia() = Unit
    override fun exportSelectedMedia(forceCopy: Boolean) = Unit
    override fun emptyTrash() = Unit
    override fun exportAllVault() = Unit
    override fun openPlayer(id: String) = Unit
    override fun closePlayer() = Unit
    override fun bindPlayerView(view: SurfaceView?) = Unit
    override fun togglePlayback() = Unit
    override fun seekPlayer(positionMillis: Long) = Unit
    override fun seekPlayerBy(deltaMillis: Long) = Unit
    override fun setPlayerMuted(muted: Boolean) = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun retryPlayback() = Unit
    override fun shareCurrentMedia() = Unit
    override fun trashCurrentMedia() = Unit
    override fun exportCurrentMedia(forceCopy: Boolean) = Unit
    override fun openCurrentMediaExternally() = Unit
    override fun consumeOperationEvent() = Unit
}
