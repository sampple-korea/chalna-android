package app.chalna.capture.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

data class CaptureItem(
    val id: String,
    val storageDestination: StorageDestination,
    val contentUri: String,
    val privateRef: String? = null,
    val displayName: String,
    val createdAtMillis: Long,
    val durationMillis: Long,
    val quality: CaptureQuality = CaptureQuality.UNKNOWN,
    val audioIncluded: Boolean = false,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val rotationDegrees: Int? = null,
    val codec: String? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val audioKnown: Boolean = true,
    val mimeType: String = "video/mp4",
    val metadataKnown: Boolean = false,
    val state: CaptureRecordState = CaptureRecordState.READY,
    val favorite: Boolean = false,
    val trashedAtMillis: Long? = null,
    val exportedFromId: String? = null,
    val exportedCopyId: String? = null,
    val lastVerifiedAtMillis: Long? = null,
) {
    fun isUsable(): Boolean =
        id.matches(OPAQUE_ID) && contentUri.startsWith("content://") &&
            createdAtMillis > 0 && durationMillis >= 0

    fun toLastCapture(): LastCapture =
        LastCapture(
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
            rotationDegrees = rotationDegrees,
            codec = codec,
            frameRate = frameRate,
            bitrate = bitrate,
            audioKnown = audioKnown,
            mimeType = mimeType,
            state = state,
        )

    companion object {
        private val OPAQUE_ID = Regex("[0-9a-fA-F-]{32,36}")

        fun from(capture: LastCapture): CaptureItem {
            val stableId =
                capture.id.takeIf { it.matches(OPAQUE_ID) } ?: StableCaptureId.from(
                    capture.storageDestination,
                    capture.privateRef ?: capture.uri,
                )
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
                rotationDegrees = capture.rotationDegrees,
                codec = capture.codec,
                frameRate = capture.frameRate,
                bitrate = capture.bitrate,
                audioKnown = capture.audioKnown,
                mimeType = capture.mimeType,
                metadataKnown = capture.state == CaptureRecordState.READY,
                state = capture.state,
            )
        }
    }
}

object StableCaptureId {
    fun from(
        destination: StorageDestination,
        reference: String,
    ): String {
        val bytes =
            MessageDigest
                .getInstance("SHA-256")
                .digest("${destination.name}:$reference".toByteArray(StandardCharsets.UTF_8))
        val digits = "0123456789abcdef"
        return buildString(CAPTURE_ID_LENGTH) {
            bytes.take(CAPTURE_ID_BYTE_COUNT).forEach { byte ->
                val value = byte.toInt() and 0xff
                append(digits[value ushr NIBBLE_BITS])
                append(digits[value and NIBBLE_MASK])
            }
        }
    }

    private const val CAPTURE_ID_LENGTH = 32
    private const val CAPTURE_ID_BYTE_COUNT = 16
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0x0f
}

enum class GallerySort { NEWEST_FIRST, OLDEST_FIRST, LONGEST_FIRST, LARGEST_FIRST }

enum class GalleryScope { ACTIVE, FAVORITES, TRASH }

data class GalleryQuery(
    val sort: GallerySort = GallerySort.NEWEST_FIRST,
    val destination: StorageDestination? = null,
    val scope: GalleryScope = GalleryScope.ACTIVE,
)

object GalleryFilter {
    fun apply(
        items: List<CaptureItem>,
        query: GalleryQuery,
    ): List<CaptureItem> {
        val comparator =
            when (query.sort) {
                GallerySort.NEWEST_FIRST -> compareByDescending<CaptureItem> { it.createdAtMillis }.thenByDescending { it.id }
                GallerySort.OLDEST_FIRST -> compareBy<CaptureItem> { it.createdAtMillis }.thenBy { it.id }
                GallerySort.LONGEST_FIRST -> compareByDescending<CaptureItem> { it.durationMillis }.thenByDescending { it.createdAtMillis }
                GallerySort.LARGEST_FIRST -> compareByDescending<CaptureItem> { it.sizeBytes ?: -1 }.thenByDescending { it.createdAtMillis }
            }
        return items
            .asSequence()
            .filter { query.destination == null || it.storageDestination == query.destination }
            .filter {
                when (query.scope) {
                    GalleryScope.ACTIVE -> it.state != CaptureRecordState.TRASHED
                    GalleryScope.FAVORITES -> it.favorite && it.state != CaptureRecordState.TRASHED
                    GalleryScope.TRASH -> it.state == CaptureRecordState.TRASHED
                }
            }.sortedWith(comparator)
            .toList()
    }

    fun day(
        item: CaptureItem,
        zoneId: ZoneId,
    ): java.time.LocalDate = Instant.ofEpochMilli(item.createdAtMillis).atZone(zoneId).toLocalDate()
}

data class GallerySelection(
    val selectedIds: Set<String> = emptySet(),
) {
    fun toggle(id: String): GallerySelection =
        copy(
            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id,
        )

    fun selectAll(visible: Collection<CaptureItem>): GallerySelection = copy(selectedIds = selectedIds + visible.map(CaptureItem::id))

    fun retainAvailable(items: Collection<CaptureItem>): GallerySelection =
        copy(selectedIds = selectedIds intersect items.mapTo(mutableSetOf(), CaptureItem::id))

    fun clear(): GallerySelection = GallerySelection()
}
