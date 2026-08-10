package app.chalna.capture.gallery

import android.content.Context
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.data.db.ChalnaDatabase
import app.chalna.capture.data.db.LegacyCaptureIndexImporter
import app.chalna.capture.media.AndroidGalleryMedia

object GalleryRepositoryFactory {
    fun create(context: Context, settings: SettingsStore): GalleryRepository {
        val appContext = context.applicationContext
        val database = ChalnaDatabase.get(appContext)
        return GalleryRepository(
            database = database,
            media = AndroidGalleryMedia(appContext),
            settings = settings,
            importer = LegacyCaptureIndexImporter(appContext, database),
        )
    }
}
