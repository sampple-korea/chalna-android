package app.chalna.capture.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryModelsTest {
    private val oldDevice = item("11111111-1111-1111-1111-111111111111", 100, StorageDestination.DEVICE_GALLERY)
    private val newDevice = item("22222222-2222-2222-2222-222222222222", 300, StorageDestination.DEVICE_GALLERY)
    private val vault = item("33333333-3333-3333-3333-333333333333", 200, StorageDestination.CHALNA_VAULT)

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

    @Test fun newestDestinationAndScopeFiltersCompose() {
        val result = GalleryFilter.apply(
            listOf(oldDevice, newDevice, vault),
            GalleryQuery(destination = StorageDestination.DEVICE_GALLERY),
        )
        assertEquals(listOf(newDevice.id, oldDevice.id), result.map(CaptureItem::id))
        assertEquals(listOf(newDevice.id, vault.id, oldDevice.id), GalleryFilter.apply(listOf(oldDevice, vault, newDevice), GalleryQuery()).map(CaptureItem::id))
    }

    @Test fun oldestSortIsStable() {
        assertEquals(listOf(oldDevice.id, vault.id, newDevice.id), GalleryFilter.apply(listOf(newDevice, oldDevice, vault), GalleryQuery(GallerySort.OLDEST_FIRST)).map(CaptureItem::id))
    }

    @Test fun selectionToggleSelectAllRetainAndClearArePure() {
        val empty = GallerySelection()
        val toggled = empty.toggle(oldDevice.id)
        val all = toggled.selectAll(listOf(newDevice, vault))
        assertEquals(setOf(oldDevice.id, newDevice.id, vault.id), all.selectedIds)
        assertEquals(setOf(newDevice.id), all.retainAvailable(listOf(newDevice)).selectedIds)
        assertTrue(all.clear().selectedIds.isEmpty())
        assertFalse(empty.selectedIds.contains(oldDevice.id))
    }

    private fun item(id: String, created: Long, destination: StorageDestination) = CaptureItem(
        id, destination, "content://capture/$id", displayName = "$id.mp4",
        createdAtMillis = created, durationMillis = 1,
    )
}
