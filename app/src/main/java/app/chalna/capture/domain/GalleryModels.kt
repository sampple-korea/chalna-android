package app.chalna.capture.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class CaptureItem(
    val id: String,
    val storageDestination: StorageDestination,
    val contentUri: String,
    val privateRef: String? = null,
    val displayName: String,
    val createdAtMillis: Long,
    val durationMillis: Long,
    val quality: CaptureQuality = CaptureQuality.AUTO,
    val audioIncluded: Boolean = false,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
) {
    fun isUsable(): Boolean = id.isNotBlank() && contentUri.startsWith("content://") &&
        createdAtMillis > 0 && durationMillis >= 0

    fun toLastCapture(): LastCapture = LastCapture(
        uri = contentUri,
        durationMillis = durationMillis,
        createdAtMillis = createdAtMillis,
        displayName = displayName,
        quality = quality,
        audioIncluded = audioIncluded,
        id = id,
        storageDestination = storageDestination,
        privateRef = privateRef,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
    )

    companion object {
        fun from(capture: LastCapture): CaptureItem {
            val stableId = capture.id.ifBlank {
                StableCaptureId.from(capture.storageDestination, capture.privateRef ?: capture.uri)
            }
            return CaptureItem(
                id = stableId,
                storageDestination = capture.storageDestination,
                contentUri = capture.uri,
                privateRef = capture.privateRef,
                displayName = capture.displayName,
                createdAtMillis = capture.createdAtMillis,
                durationMillis = capture.durationMillis,
                quality = capture.quality,
                audioIncluded = capture.audioIncluded,
                sizeBytes = capture.sizeBytes,
                width = capture.width,
                height = capture.height,
            )
        }
    }
}

object StableCaptureId {
    fun from(destination: StorageDestination, reference: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("${destination.name}:$reference".toByteArray(StandardCharsets.UTF_8))
        return bytes.take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

enum class GallerySort { NEWEST_FIRST, OLDEST_FIRST }

data class GalleryQuery(
    val sort: GallerySort = GallerySort.NEWEST_FIRST,
    val destination: StorageDestination? = null,
    val fromMillisInclusive: Long? = null,
    val toMillisExclusive: Long? = null,
)

object GalleryFilter {
    fun apply(items: List<CaptureItem>, query: GalleryQuery): List<CaptureItem> = items.asSequence()
        .filter { query.destination == null || it.storageDestination == query.destination }
        .filter { query.fromMillisInclusive == null || it.createdAtMillis >= query.fromMillisInclusive }
        .filter { query.toMillisExclusive == null || it.createdAtMillis < query.toMillisExclusive }
        .sortedWith(
            if (query.sort == GallerySort.NEWEST_FIRST) {
                compareByDescending<CaptureItem> { it.createdAtMillis }.thenByDescending { it.id }
            } else {
                compareBy<CaptureItem> { it.createdAtMillis }.thenBy { it.id }
            },
        ).toList()
}

data class GallerySelection(val selectedIds: Set<String> = emptySet()) {
    fun toggle(id: String): GallerySelection = copy(
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id,
    )

    fun selectAll(visible: Collection<CaptureItem>): GallerySelection =
        copy(selectedIds = selectedIds + visible.map(CaptureItem::id))

    fun retainAvailable(items: Collection<CaptureItem>): GallerySelection =
        copy(selectedIds = selectedIds intersect items.mapTo(mutableSetOf(), CaptureItem::id))

    fun clear(): GallerySelection = GallerySelection()
}
