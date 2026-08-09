package app.chalna.capture.gallery

import app.chalna.capture.data.CaptureExistence
import app.chalna.capture.data.CaptureIndex
import app.chalna.capture.data.KnownCaptureDiscovery
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.GalleryFilter
import app.chalna.capture.domain.GalleryQuery
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.domain.StorageDestination
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CaptureMetadata(
    val durationMillis: Long? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
)

fun interface CaptureMetadataExtractor {
    suspend fun extract(item: CaptureItem): CaptureMetadata
}

class CachingMetadataExtractor(private val delegate: CaptureMetadataExtractor) : CaptureMetadataExtractor {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, CaptureMetadata>()

    override suspend fun extract(item: CaptureItem): CaptureMetadata = mutex.withLock {
        cache[item.id] ?: delegate.extract(item).also { cache[item.id] = it }
    }

    suspend fun invalidate(ids: Set<String>) = mutex.withLock { ids.forEach(cache::remove) }
}

interface GalleryMediaGateway {
    suspend fun exists(item: CaptureItem): Boolean
    suspend fun delete(item: CaptureItem): Boolean
    suspend fun exportVaultToDeviceGallery(item: CaptureItem): CaptureItem
    suspend fun discoverKnownDeviceCaptures(): List<CaptureItem>
}

interface LastCaptureAccess {
    fun current(): LastCapture?
    suspend fun save(capture: LastCapture?)
}

data class DeleteCapturesResult(val deletedIds: Set<String>, val failedIds: Set<String>)
data class ExportCapturesResult(val exported: List<CaptureItem>, val failedIds: Set<String>)

class GalleryRepository(
    private val index: CaptureIndex,
    private val media: GalleryMediaGateway,
    private val lastCapture: LastCaptureAccess,
    private val metadata: CaptureMetadataExtractor? = null,
) {
    suspend fun initialize(): List<CaptureItem> = index.migrate(
        lastCapture = lastCapture.current(),
        discovery = KnownCaptureDiscovery(media::discoverKnownDeviceCaptures),
    )

    suspend fun recordFinalized(capture: LastCapture): CaptureItem = index.recordFinalized(capture)

    suspend fun items(query: GalleryQuery = GalleryQuery()): List<CaptureItem> {
        val beforeCleanup = runCatching { index.list() }.getOrDefault(emptyList())
        var available = runCatching { index.list(CaptureExistence(media::exists)) }
            .getOrElse { beforeCleanup.filter { item -> runCatching { media.exists(item) }.getOrDefault(false) } }
        repairLastCaptureIfRemoved(beforeCleanup, available)

        val currentLast = lastCapture.current()?.takeIf(LastCapture::isUsable)?.let(CaptureItem::from)
            ?.takeIf { candidate -> runCatching { media.exists(candidate) }.getOrDefault(false) }
        if (currentLast != null && available.none { it.id == currentLast.id }) {
            val persisted = runCatching { index.recordFinalized(currentLast.toLastCapture()) }.getOrNull()
            available = available + (persisted ?: currentLast)
        }

        val metadataUpdates = mutableListOf<CaptureItem>()
        val extractor = metadata
        val enriched = available.map { item ->
            if (extractor == null || (item.durationMillis > 0 && item.sizeBytes != null && item.width != null && item.height != null)) item
            else runCatching {
                val value = extractor.extract(item)
                item.copy(
                    durationMillis = value.durationMillis ?: item.durationMillis,
                    sizeBytes = value.sizeBytes ?: item.sizeBytes,
                    width = value.width ?: item.width,
                    height = value.height ?: item.height,
                ).also { if (it != item) metadataUpdates += it }
            }.getOrDefault(item)
        }
        if (metadataUpdates.isNotEmpty()) runCatching { index.updateExisting(metadataUpdates) }
        return GalleryFilter.apply(enriched, query)
    }

    suspend fun delete(ids: Set<String>): DeleteCapturesResult {
        if (ids.isEmpty()) return DeleteCapturesResult(emptySet(), emptySet())
        val current = index.list()
        val targeted = current.filter { it.id in ids }
        val deleted = targeted.filter { runCatching { media.delete(it) }.getOrDefault(false) }.mapTo(mutableSetOf()) { it.id }
        index.remove(deleted)
        val failed = ids - deleted
        val previousLast = lastCapture.current()
        val previousLastId = previousLast?.let { CaptureItem.from(it).id }
        val deletedUris = targeted.filter { it.id in deleted }.mapTo(mutableSetOf(), CaptureItem::contentUri)
        val deletedWasLast = previousLastId?.let { it in deleted } == true ||
            previousLast?.uri?.let { it in deletedUris } == true
        if (deletedWasLast) {
            val fallback = GalleryFilter.apply(index.list(CaptureExistence(media::exists)), GalleryQuery()).firstOrNull()
            lastCapture.save(fallback?.toLastCapture())
        }
        return DeleteCapturesResult(deleted, failed)
    }

    suspend fun exportVault(ids: Set<String>): ExportCapturesResult {
        val existing = index.list()
        val candidates = existing.filter { it.id in ids && it.storageDestination == StorageDestination.CHALNA_VAULT }
        val exported = mutableListOf<CaptureItem>()
        val failed = (ids - candidates.mapTo(mutableSetOf(), CaptureItem::id)).toMutableSet()
        candidates.forEach { source ->
            val copy = runCatching { media.exportVaultToDeviceGallery(source) }.getOrElse {
                failed += source.id
                return@forEach
            }
            val indexed = runCatching { index.recordFinalized(copy.toLastCapture()) }.getOrNull()
            if (indexed != null) exported += indexed
            else {
                runCatching { media.delete(copy) }
                failed += source.id
            }
        }
        return ExportCapturesResult(exported, failed)
    }

    suspend fun shareUris(ids: Set<String>): List<String> = index.list()
        .filter { it.id in ids && it.contentUri.startsWith("content://") }
        .map(CaptureItem::contentUri)

    suspend fun externalOpenUri(id: String): String? = index.list()
        .firstOrNull { it.id == id && it.contentUri.startsWith("content://") }
        ?.contentUri

    private suspend fun repairLastCaptureIfRemoved(before: List<CaptureItem>, after: List<CaptureItem>) {
        val current = lastCapture.current() ?: return
        val currentItem = CaptureItem.from(current)
        val removedFromIndex = before.any { it.id == currentItem.id } && after.none { it.id == currentItem.id }
        val missingOutsideIndex = before.none { it.id == currentItem.id } &&
            !runCatching { media.exists(currentItem) }.getOrDefault(false)
        if (removedFromIndex || missingOutsideIndex) {
            val fallback = GalleryFilter.apply(after, GalleryQuery()).firstOrNull()
            lastCapture.save(fallback?.toLastCapture())
        }
    }
}
