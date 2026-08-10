package app.chalna.capture.gallery

import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.GalleryFilter
import app.chalna.capture.domain.GalleryQuery
import app.chalna.capture.domain.GallerySort
import app.chalna.capture.domain.StableCaptureId
import app.chalna.capture.domain.StorageDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryRepositoryTest {
    @Test fun captureIdentityIncludesDestinationAndExactReference() {
        val reference = "content://media/external/video/media/7"
        val gallery = StableCaptureId.from(StorageDestination.DEVICE_GALLERY, reference)
        val vault = StableCaptureId.from(StorageDestination.CHALNA_VAULT, reference)
        assertNotEquals(gallery, vault)
        assertEquals(gallery, StableCaptureId.from(StorageDestination.DEVICE_GALLERY, reference))
    }

    @Test fun tenThousandMetadataRowsSortDeterministicallyWithoutMediaAccess() {
        val rows = (0 until 10_000).map { index -> item(index) }.shuffled(java.util.Random(7))
        val result = GalleryFilter.apply(rows, GalleryQuery(sort = GallerySort.NEWEST_FIRST))
        assertEquals(10_000, result.size)
        assertEquals(9_999L, result.first().createdAtMillis)
        assertEquals(0L, result.last().createdAtMillis)
    }

    @Test fun partialBatchResultNeverLosesSuccessfulIdentities() {
        val result =
            BatchOperationResult(
                succeededIds = setOf("11111111-1111-1111-1111-111111111111"),
                failedIds = setOf("22222222-2222-2222-2222-222222222222"),
            )
        assertTrue(result.succeededIds.intersect(result.failedIds).isEmpty())
        assertEquals(1, result.succeededIds.size)
        assertEquals(1, result.failedIds.size)
    }

    private fun item(index: Int): CaptureItem {
        val id = "%08x-0000-0000-0000-%012x".format(index, index)
        return CaptureItem(
            id = id,
            storageDestination = if (index % 2 == 0) StorageDestination.DEVICE_GALLERY else StorageDestination.CHALNA_VAULT,
            contentUri = "content://capture/$id",
            privateRef = if (index % 2 == 0) null else "Chalna/$id.mp4",
            displayName = "$id.mp4",
            createdAtMillis = index.toLong(),
            durationMillis = index.toLong(),
            sizeBytes = index.toLong() + 1,
        )
    }
}
