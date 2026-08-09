package app.chalna.capture.gallery

import android.content.Context
import app.chalna.capture.data.CaptureIndex
import app.chalna.capture.data.FileCaptureIndexStore
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.media.AndroidGalleryMedia

object GalleryRepositoryFactory {
    fun create(context: Context, settings: SettingsStore): GalleryRepository {
        val media = AndroidGalleryMedia(context.applicationContext)
        return GalleryRepository(
            index = CaptureIndex(FileCaptureIndexStore(context.applicationContext)),
            media = media,
            lastCapture = object : LastCaptureAccess {
                override fun current(): LastCapture? = settings.lastCapture.value
                override suspend fun save(capture: LastCapture?) {
                    settings.saveLastCapture(capture)
                }
            },
            metadata = CachingMetadataExtractor(media),
        )
    }
}
