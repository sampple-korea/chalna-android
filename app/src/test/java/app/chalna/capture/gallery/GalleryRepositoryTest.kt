package app.chalna.capture.gallery

import app.chalna.capture.data.CaptureIndex
import app.chalna.capture.data.CaptureIndexSnapshot
import app.chalna.capture.data.CaptureIndexStore
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.domain.StableCaptureId
import app.chalna.capture.domain.StorageDestination
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryRepositoryTest {
    @Test fun successfulVaultExportIndexesCopyButKeepsOriginal() = runTest {
        val fixture = Fixture(listOf(vault("v")))
        val result = fixture.repository.exportVault(setOf("v"))
        assertEquals(1, result.exported.size)
        assertTrue(result.failedIds.isEmpty())
        assertEquals(StorageDestination.DEVICE_GALLERY, result.exported.single().storageDestination)
        assertEquals(setOf("v", result.exported.single().id), fixture.index.list().mapTo(mutableSetOf(), CaptureItem::id))
    }

    @Test fun failedOrPartialExportNeverIndexesDestination() = runTest {
        val fixture = Fixture(listOf(vault("v")))
        fixture.media.exportFailures += "v"
        assertEquals(setOf("v"), fixture.repository.exportVault(setOf("v")).failedIds)
        assertEquals(listOf("v"), fixture.index.list().map(CaptureItem::id))
    }

    @Test fun repeatedExportResultIsDeduplicatedByStorageReference() = runTest {
        val fixture = Fixture(listOf(vault("v")))
        fixture.repository.exportVault(setOf("v"))
        fixture.repository.exportVault(setOf("v"))
        assertEquals(2, fixture.index.list().size)
    }

    @Test fun mixedBatchExportKeepsCompletedCopyAndReportsFailedSource() = runTest {
        val fixture = Fixture(listOf(vault("first"), vault("second")))
        fixture.media.exportFailures += "second"
        val result = fixture.repository.exportVault(setOf("first", "second"))
        assertEquals(listOf("first"), result.exported.map { it.displayName.removeSuffix(".mp4") })
        assertEquals(setOf("second"), result.failedIds)
        assertEquals(3, fixture.index.list().size)
    }

    @Test fun deleteUpdatesIndexAndFallsBackLastCaptureToNewestRemaining() = runTest {
        val older = device("older", 100)
        val newest = device("newest", 200)
        val fixture = Fixture(listOf(older, newest), newest.toLastCapture())
        val result = fixture.repository.delete(setOf("newest"))
        assertEquals(setOf("newest"), result.deletedIds)
        assertEquals("older", fixture.last.saved?.id)
        assertEquals(listOf("older"), fixture.index.list().map(CaptureItem::id))
    }

    @Test fun deleteFailureRetainsIndexAndLastCapture() = runTest {
        val only = device("only", 100)
        val fixture = Fixture(listOf(only), only.toLastCapture())
        fixture.media.deleteFailure += "only"
        val result = fixture.repository.delete(setOf("only"))
        assertEquals(setOf("only"), result.failedIds)
        assertEquals("only", fixture.last.saved?.id)
        assertEquals(listOf("only"), fixture.index.list().map(CaptureItem::id))
    }

    @Test fun deletingFinalItemClearsLastCapture() = runTest {
        val fixture = Fixture(listOf(device("only", 100)), device("only", 100).toLastCapture())
        fixture.repository.delete(setOf("only"))
        assertNull(fixture.last.saved)
    }

    @Test fun vaultShareAndExternalOpenAlwaysUseSafeContentUri() = runTest {
        val fixture = Fixture(listOf(vault("v")))
        assertEquals(listOf("content://vault/v"), fixture.repository.shareUris(setOf("v")))
        assertEquals("content://vault/v", fixture.repository.externalOpenUri("v"))
    }

    @Test fun metadataExtractorCachesByStableId() = runTest {
        var calls = 0
        val item = device("one", 100).copy(sizeBytes = null, width = null, height = null)
        val fixture = Fixture(listOf(item), extractor = CachingMetadataExtractor {
            calls++
            CaptureMetadata(durationMillis = 30, sizeBytes = 5, width = 10, height = 20)
        })
        val first = fixture.repository.items()
        fixture.repository.items()
        assertEquals(1, calls)
        assertEquals(5L, first.single().sizeBytes)
        assertEquals(5L, fixture.index.list().single().sizeBytes)
        assertEquals(10, fixture.index.list().single().width)
        assertEquals(20, fixture.index.list().single().height)
        assertEquals(30L, fixture.index.list().single().durationMillis)
    }

    @Test fun staleCleanupRemovesIndexAndRepairsLastCapture() = runTest {
        val stale = device("stale", 200)
        val fallback = device("fallback", 100)
        val fixture = Fixture(listOf(stale, fallback), stale.toLastCapture())
        fixture.media.remove("stale")
        assertEquals(listOf("fallback"), fixture.repository.items().map(CaptureItem::id))
        assertEquals(listOf("fallback"), fixture.index.list().map(CaptureItem::id))
        assertEquals("fallback", fixture.last.saved?.id)
    }

    @Test fun currentFinalizeRemainsDiscoverableWhenIndexWriteFails() = runTest {
        val current = device("current", 500)
        val failingIndex = CaptureIndex(object : CaptureIndexStore {
            override suspend fun read(): CaptureIndexSnapshot = CaptureIndexSnapshot(migrationComplete = true)
            override suspend fun write(snapshot: CaptureIndexSnapshot) = error("disk full")
        })
        val media = FakeMedia(listOf(current))
        val repository = GalleryRepository(failingIndex, media, FakeLast(current.toLastCapture()))
        assertEquals(listOf("current"), repository.items().map(CaptureItem::id))
    }

    private class Fixture(
        items: List<CaptureItem>,
        lastCapture: LastCapture? = null,
        extractor: CaptureMetadataExtractor? = null,
    ) {
        val store = MemoryStore(CaptureIndexSnapshot(true, items))
        val index = CaptureIndex(store)
        val media = FakeMedia(items)
        val last = FakeLast(lastCapture)
        val repository = GalleryRepository(index, media, last, extractor)
    }

    private class MemoryStore(var snapshot: CaptureIndexSnapshot) : CaptureIndexStore {
        override suspend fun read(): CaptureIndexSnapshot = snapshot
        override suspend fun write(snapshot: CaptureIndexSnapshot) { this.snapshot = snapshot }
    }

    private class FakeLast(initial: LastCapture?) : LastCaptureAccess {
        var saved = initial
        override fun current(): LastCapture? = saved
        override suspend fun save(capture: LastCapture?) { saved = capture }
    }

    private class FakeMedia(items: List<CaptureItem>) : GalleryMediaGateway {
        private val available = items.associateByTo(mutableMapOf(), CaptureItem::id)
        val deleteFailure = mutableSetOf<String>()
        val exportFailures = mutableSetOf<String>()
        fun remove(id: String) { available.remove(id) }
        override suspend fun exists(item: CaptureItem): Boolean = item.id in available
        override suspend fun delete(item: CaptureItem): Boolean =
            if (item.id in deleteFailure) false else available.remove(item.id) != null
        override suspend fun exportVaultToDeviceGallery(item: CaptureItem): CaptureItem {
            if (item.id in exportFailures) error("copy failed before publish")
            val uri = "content://media/exported-${item.id}"
            return item.copy(
                id = StableCaptureId.from(StorageDestination.DEVICE_GALLERY, uri),
                storageDestination = StorageDestination.DEVICE_GALLERY,
                contentUri = uri,
                privateRef = null,
            ).also { available[it.id] = it }
        }
        override suspend fun discoverKnownDeviceCaptures(): List<CaptureItem> = emptyList()
    }
}

private fun vault(id: String) = CaptureItem(
    id, StorageDestination.CHALNA_VAULT, "content://vault/$id", "Chalna/$id.mp4", "$id.mp4", 100, 10,
)

private fun device(id: String, created: Long) = CaptureItem(
    id, StorageDestination.DEVICE_GALLERY, "content://media/$id", displayName = "$id.mp4",
    createdAtMillis = created, durationMillis = 10,
)
