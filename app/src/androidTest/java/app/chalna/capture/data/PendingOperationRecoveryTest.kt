package app.chalna.capture.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.chalna.capture.data.db.ChalnaDatabase
import app.chalna.capture.data.db.LegacyCaptureIndexImporter
import app.chalna.capture.data.db.PendingOperationEntity
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureRecordState
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.domain.StableCaptureId
import app.chalna.capture.domain.StorageDestination
import app.chalna.capture.gallery.GalleryMediaGateway
import app.chalna.capture.gallery.GalleryRepository
import app.chalna.capture.gallery.TrashMediaResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class PendingOperationRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: ChalnaDatabase
    private lateinit var repository: GalleryRepository
    private lateinit var media: FakeMedia

    @Before fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(context, ChalnaDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        media = FakeMedia()
        repository =
            GalleryRepository(
                database = database,
                media = media,
                settings = SettingsStore(context),
                importer = LegacyCaptureIndexImporter(context, database),
                nowEpochMillis = { NOW },
            )
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun metadataPendingCaptureBecomesReadyWithoutDeletingMedia() =
        runBlocking {
            val capture = capture("11111111-1111-1111-1111-111111111111", StorageDestination.DEVICE_GALLERY)
            media.validUris += capture.uri

            repository.recordMetadataPending(capture.copy(state = CaptureRecordState.METADATA_PENDING))
            assertEquals(1, database.pendingOperationDao().count())

            repository.reconcilePendingOperations()

            assertEquals(CaptureRecordState.READY.name, database.captureDao().byId(capture.id)?.state)
            assertEquals(0, database.pendingOperationDao().count())
            assertEquals(0, media.deletes)
        }

    @Test fun completedVaultExportIsIndexedAndLinkedAfterTransactionFailure() =
        runBlocking {
            val source = capture("22222222-2222-2222-2222-222222222222", StorageDestination.CHALNA_VAULT)
            media.validUris += source.uri
            repository.recordFinalized(source)
            val exportedUri = "content://media/external/video/media/42"
            val exportedId = StableCaptureId.from(StorageDestination.DEVICE_GALLERY, exportedUri)
            media.validUris += exportedUri
            database.pendingOperationDao().upsert(
                PendingOperationEntity(
                    id = "export-${source.id}",
                    captureId = source.id,
                    type = "RECONCILE_EXPORT",
                    payload = exportedUri,
                    attempts = 0,
                    nextAttemptEpochMillis = NOW,
                    createdAtEpochMillis = NOW,
                ),
            )

            repository.reconcilePendingOperations()

            assertNotNull(database.captureDao().byId(exportedId))
            assertEquals(exportedId, database.captureDao().byId(source.id)?.exportedCopyId)
            assertEquals(0, database.pendingOperationDao().count())
            assertEquals(0, media.deletes)
        }

    private fun capture(
        id: String,
        destination: StorageDestination,
    ) = LastCapture(
        uri =
            if (destination == StorageDestination.DEVICE_GALLERY) {
                "content://media/external/video/media/7"
            } else {
                "content://${context.packageName}.files/vault/$id"
            },
        durationMillis = 1_200,
        createdAtMillis = NOW,
        displayName = "$id.mp4",
        quality = CaptureQuality.FHD,
        audioIncluded = true,
        id = id,
        storageDestination = destination,
        privateRef = if (destination == StorageDestination.CHALNA_VAULT) "Chalna/$id.mp4" else null,
        sizeBytes = 4_096,
    )

    private class FakeMedia : GalleryMediaGateway {
        val validUris = mutableSetOf<String>()
        var deletes = 0

        override suspend fun exists(item: CaptureItem): Boolean = item.contentUri in validUris

        override suspend fun validate(item: CaptureItem): CaptureItem? =
            item
                .takeIf { it.contentUri in validUris }
                ?.copy(
                    durationMillis = 1_200,
                    sizeBytes = 4_096,
                    metadataKnown = true,
                    state = CaptureRecordState.READY,
                )

        override suspend fun moveToTrash(
            item: CaptureItem,
            trashedAtEpochMillis: Long,
        ): TrashMediaResult = TrashMediaResult.Trashed(item)

        override suspend fun restore(item: CaptureItem): CaptureItem = item

        override suspend fun deletePermanently(item: CaptureItem): Boolean {
            deletes++
            return validUris.remove(item.contentUri)
        }

        override suspend fun exportVaultToDeviceGallery(item: CaptureItem): CaptureItem = error("Not used")
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
