package app.chalna.capture.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryModelsTest {
    private val oldDevice = item("old", 100, StorageDestination.DEVICE_GALLERY)
    private val newDevice = item("new", 300, StorageDestination.DEVICE_GALLERY)
    private val vault = item("vault", 200, StorageDestination.CHALNA_VAULT)

    @Test fun storageDefaultRemainsBackwardCompatibleDeviceGallery() {
        assertEquals(StorageDestination.DEVICE_GALLERY, CaptureSettings().storageDestination)
        assertEquals(StorageDestination.DEVICE_GALLERY, LastCapture("content://x", 0, 1).storageDestination)
        assertEquals(StorageDestination.DEVICE_GALLERY, StorageDestinationPolicy.fromPersisted(null))
        assertEquals(StorageDestination.DEVICE_GALLERY, StorageDestinationPolicy.fromPersisted("OLD_UNKNOWN_VALUE"))
        assertEquals(StorageDestination.CHALNA_VAULT, StorageDestinationPolicy.fromPersisted("CHALNA_VAULT"))
    }

    @Test fun activeCaptureUsesImmutableDestinationSnapshot() {
        var live = CaptureSettings(storageDestination = StorageDestination.DEVICE_GALLERY, audioEnabled = true)
        val active = CaptureSessionSettings.snapshot(live)
        live = live.copy(storageDestination = StorageDestination.CHALNA_VAULT, audioEnabled = false)
        assertEquals(StorageDestination.DEVICE_GALLERY, active.storageDestination)
        assertTrue(active.audioEnabled)
        assertEquals(StorageDestination.CHALNA_VAULT, live.storageDestination)
    }

    @Test fun newestAndDateDestinationFiltersCompose() {
        val result = GalleryFilter.apply(
            listOf(oldDevice, newDevice, vault),
            GalleryQuery(
                destination = StorageDestination.DEVICE_GALLERY,
                fromMillisInclusive = 50,
                toMillisExclusive = 300,
            ),
        )
        assertEquals(listOf("old"), result.map(CaptureItem::id))
        assertEquals(listOf("new", "vault", "old"), GalleryFilter.apply(listOf(oldDevice, vault, newDevice), GalleryQuery()).map(CaptureItem::id))
    }

    @Test fun oldestSortIsStable() {
        assertEquals(listOf("old", "vault", "new"), GalleryFilter.apply(listOf(newDevice, oldDevice, vault), GalleryQuery(GallerySort.OLDEST_FIRST)).map(CaptureItem::id))
    }

    @Test fun selectionToggleSelectAllRetainAndClearArePure() {
        val empty = GallerySelection()
        val toggled = empty.toggle("old")
        val all = toggled.selectAll(listOf(newDevice, vault))
        assertEquals(setOf("old", "new", "vault"), all.selectedIds)
        assertEquals(setOf("new"), all.retainAvailable(listOf(newDevice)).selectedIds)
        assertTrue(all.clear().selectedIds.isEmpty())
        assertFalse(empty.selectedIds.contains("old"))
    }

    private fun item(id: String, created: Long, destination: StorageDestination) = CaptureItem(
        id, destination, "content://capture/$id", displayName = "$id.mp4",
        createdAtMillis = created, durationMillis = 1,
    )
}
