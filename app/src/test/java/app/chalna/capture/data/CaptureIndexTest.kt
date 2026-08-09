package app.chalna.capture.data

import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.domain.StorageDestination
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureIndexTest {
    @Test fun codecRoundTripPreservesPrivateMetadata() {
        val original = CaptureIndexSnapshot(
            migrationComplete = true,
            items = listOf(item("one", StorageDestination.CHALNA_VAULT, "Chalna/a b.mp4")),
        )
        assertEquals(original, CaptureIndexCodec.decode(CaptureIndexCodec.encode(original)))
    }

    @Test fun migrationCombinesLegacyLastCaptureAndConstrainedDiscoveryOnlyOnce() = runTest {
        val store = MemoryStore()
        val index = CaptureIndex(store)
        var discoveries = 0
        val legacy = LastCapture("content://media/legacy", 10, 20, "CHALNA_legacy.mp4")
        val first = index.migrate(legacy, KnownCaptureDiscovery {
            discoveries++
            listOf(item("known", StorageDestination.DEVICE_GALLERY))
        })
        val second = index.migrate(null, KnownCaptureDiscovery { error("must not run") })
        assertEquals(2, first.size)
        assertEquals(first, second)
        assertEquals(1, discoveries)
        assertTrue(store.value.migrationComplete)
    }

    @Test fun staleCleanupIsDurable() = runTest {
        val store = MemoryStore(CaptureIndexSnapshot(true, listOf(item("keep"), item("stale"))))
        val result = CaptureIndex(store).list(CaptureExistence { it.id != "stale" })
        assertEquals(listOf("keep"), result.map(CaptureItem::id))
        assertEquals(result, store.value.items)
    }

    @Test fun finalizedDuplicateReferenceDoesNotCreateDuplicate() = runTest {
        val store = MemoryStore()
        val index = CaptureIndex(store)
        val one = LastCapture("content://media/1", 1, 10, id = "first")
        val two = one.copy(id = "second", createdAtMillis = 20)
        index.recordFinalized(one)
        index.recordFinalized(two)
        assertEquals(listOf("second"), index.list().map(CaptureItem::id))
    }

    @Test fun invalidOrFinalizingCaptureCannotBeIndexed() = runTest {
        val index = CaptureIndex(MemoryStore())
        val result = runCatching { index.recordFinalized(LastCapture("", 0, 0)) }
        assertTrue(result.isFailure)
        assertTrue(index.list().isEmpty())
    }

    @Test fun malformedRowsAreIgnoredWithoutLosingHeader() {
        val decoded = CaptureIndexCodec.decode("CHALNA_INDEX_1\t1\nnot-valid-base64\n")
        assertTrue(decoded.migrationComplete)
        assertTrue(decoded.items.isEmpty())
    }

    private class MemoryStore(initial: CaptureIndexSnapshot = CaptureIndexSnapshot()) : CaptureIndexStore {
        var value = initial
        override suspend fun read(): CaptureIndexSnapshot = value
        override suspend fun write(snapshot: CaptureIndexSnapshot) { value = snapshot }
    }

    private fun item(
        id: String,
        destination: StorageDestination = StorageDestination.DEVICE_GALLERY,
        privateRef: String? = null,
    ) = CaptureItem(
        id = id,
        storageDestination = destination,
        contentUri = "content://capture/$id",
        privateRef = privateRef,
        displayName = "$id.mp4",
        createdAtMillis = 10,
        durationMillis = 1,
    )
}
