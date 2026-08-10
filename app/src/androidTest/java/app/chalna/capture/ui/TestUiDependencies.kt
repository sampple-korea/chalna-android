package app.chalna.capture.ui

import android.graphics.Bitmap
import android.os.CancellationSignal
import android.view.SurfaceView
import androidx.paging.PagingData
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal open class TestUiDependencies(initial: ChalnaUiState) : UiDependencies {
    override val state = MutableStateFlow(initial)
    override val galleryPaging: Flow<PagingData<GalleryPagingItem>> = state.map { snapshot ->
        val entries = buildList {
            var day: java.time.LocalDate? = null
            snapshot.gallery.forEach { item ->
                val itemDay = Instant.ofEpochMilli(item.capturedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                if (itemDay != day) add(GalleryPagingItem.Day(itemDay))
                add(GalleryPagingItem.Media(item))
                day = itemDay
            }
        }
        PagingData.from(entries)
    }
    open override fun toggleCapture() = Unit
    open override fun requestCamera() = Unit
    open override fun requestMicrophone() = Unit
    override fun requestNotifications() = Unit
    override fun openAssistantSettings() = Unit
    override fun openAppSettings() = Unit
    override fun openNotificationSettings() = Unit
    override fun refreshSetup() = Unit
    open override fun finishSetup() = Unit
    open override fun setQuality(value: VideoQuality) = Unit
    open override fun setAppearance(value: AppearanceMode) = Unit
    open override fun setHaptics(value: Boolean) = Unit
    open override fun setSound(value: Boolean) = Unit
    open override fun setAutoStop(seconds: Int) = Unit
    open override fun setMotion(value: MotionMode) = Unit
    override fun setStorageDestination(value: StorageDestinationUi) = Unit
    override fun openLastCapture() = Unit
    open override fun reviewSetup() = Unit
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
