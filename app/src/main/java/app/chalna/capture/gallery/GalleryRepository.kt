package app.chalna.capture.gallery

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.data.db.CaptureEntity
import app.chalna.capture.data.db.ChalnaDatabase
import app.chalna.capture.data.db.LegacyCaptureIndexImporter
import app.chalna.capture.data.db.PendingOperationEntity
import app.chalna.capture.data.db.toDomain
import app.chalna.capture.data.db.toEntity
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureRecordState
import app.chalna.capture.domain.GalleryFilter
import app.chalna.capture.domain.GalleryQuery
import app.chalna.capture.domain.GalleryScope
import app.chalna.capture.domain.GallerySort
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.domain.StorageDestination
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CaptureMetadata(
    val durationMillis: Long? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val rotationDegrees: Int? = null,
    val codec: String? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val audioIncluded: Boolean? = null,
    val mimeType: String? = null,
)

fun interface CaptureMetadataExtractor {
    suspend fun extract(item: CaptureItem): CaptureMetadata
}

sealed interface TrashMediaResult {
    data class Trashed(val item: CaptureItem) : TrashMediaResult
    data object PermanentDeleteRequired : TrashMediaResult
    data object Failed : TrashMediaResult
}

interface GalleryMediaGateway {
    suspend fun exists(item: CaptureItem): Boolean
    suspend fun validate(item: CaptureItem): CaptureItem?
    suspend fun moveToTrash(item: CaptureItem, trashedAtEpochMillis: Long): TrashMediaResult
    suspend fun restore(item: CaptureItem): CaptureItem?
    suspend fun deletePermanently(item: CaptureItem): Boolean
    suspend fun exportVaultToDeviceGallery(item: CaptureItem): CaptureItem
}

data class BatchOperationResult(
    val succeededIds: Set<String>,
    val failedIds: Set<String>,
    val produced: List<CaptureItem> = emptyList(),
)

data class StorageSummary(
    val deviceGalleryCount: Int,
    val vaultCount: Int,
    val vaultBytes: Long,
    val trashBytes: Long,
)

class GalleryRepository(
    private val database: ChalnaDatabase,
    private val media: GalleryMediaGateway,
    private val settings: SettingsStore,
    private val importer: LegacyCaptureIndexImporter,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val captureDao get() = database.captureDao()

    suspend fun initialize(): Int {
        importer.import(settings.lastCapture.value)
        reconcile(limit = RECONCILE_OPEN_BATCH)
        importer.cleanupVerifiedBackup()
        return captureDao.countAll()
    }

    fun observeActive(): Flow<List<CaptureItem>> = captureDao.observeActive().map { entities ->
        entities.mapNotNull(CaptureEntity::toDomain)
    }

    fun paging(query: GalleryQuery): Flow<PagingData<CaptureItem>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE / 2, enablePlaceholders = false),
        pagingSourceFactory = { captureDao.pagingSource(buildPagingQuery(query)) },
    ).flow.map { data -> data.map { entity -> requireNotNull(entity.toDomain()) } }

    suspend fun items(query: GalleryQuery = GalleryQuery(), limit: Int = LEGACY_UI_LIMIT): List<CaptureItem> {
        val page = captureDao.activePage(limit).mapNotNull(CaptureEntity::toDomain)
        return GalleryFilter.apply(page, query)
    }

    suspend fun byId(id: String): CaptureItem? {
        if (!id.isOpaqueCaptureId()) return null
        val item = captureDao.byId(id)?.toDomain() ?: return null
        if (!media.exists(item)) {
            removeMissing(item)
            return null
        }
        return item
    }

    suspend fun recordFinalized(capture: LastCapture): CaptureItem {
        require(capture.isUsable()) { "Only validated finalized media can be indexed" }
        val item = CaptureItem.from(capture)
        captureDao.upsert(item.toEntity(LegacyCaptureIndexImporter.CURRENT_DATA_VERSION))
        settings.saveLastCapture(item.toLastCapture())
        return item
    }

    suspend fun recordMetadataPending(capture: LastCapture) {
        val item = CaptureItem.from(capture).copy(
            state = CaptureRecordState.METADATA_PENDING,
            metadataKnown = false,
        )
        runCatching { captureDao.upsert(item.toEntity(LegacyCaptureIndexImporter.CURRENT_DATA_VERSION)) }
        database.pendingOperationDao().upsert(
            PendingOperationEntity(
                id = "metadata-${item.id}",
                captureId = item.id,
                type = "RECONCILE_METADATA",
                payload = null,
                attempts = 0,
                nextAttemptEpochMillis = nowEpochMillis(),
                createdAtEpochMillis = nowEpochMillis(),
            ),
        )
    }

    suspend fun reconcile(limit: Int = RECONCILE_MANUAL_BATCH): BatchOperationResult {
        val candidates = captureDao.reconciliationBatch(nowEpochMillis() - REVERIFY_AFTER_MILLIS, limit)
            .mapNotNull(CaptureEntity::toDomain)
        val succeeded = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        candidates.forEach { item ->
            try {
                val validated = media.validate(item)
                if (validated == null) {
                    removeMissing(item)
                    succeeded += item.id
                } else {
                    captureDao.upsert(
                        validated.copy(lastVerifiedAtMillis = nowEpochMillis()).toEntity(
                            LegacyCaptureIndexImporter.CURRENT_DATA_VERSION,
                        ),
                    )
                    succeeded += item.id
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failed += item.id
            }
        }
        repairLastCapture()
        return BatchOperationResult(succeeded, failed)
    }

    suspend fun setFavorite(ids: Set<String>, favorite: Boolean): BatchOperationResult {
        val valid = captureDao.byIds(ids).mapTo(mutableSetOf(), CaptureEntity::id)
        captureDao.setFavorite(valid, favorite, LegacyCaptureIndexImporter.CURRENT_DATA_VERSION)
        return BatchOperationResult(valid, ids - valid)
    }

    suspend fun trash(ids: Set<String>): BatchOperationResult {
        val items = captureDao.byIds(ids).mapNotNull(CaptureEntity::toDomain)
            .filter { it.state != CaptureRecordState.TRASHED }
        val succeeded = mutableSetOf<String>()
        val failed = (ids - items.mapTo(mutableSetOf(), CaptureItem::id)).toMutableSet()
        items.forEach { item ->
            when (val result = media.moveToTrash(item, nowEpochMillis())) {
                is TrashMediaResult.Trashed -> {
                    captureDao.upsert(
                        result.item.copy(
                            state = CaptureRecordState.TRASHED,
                            trashedAtMillis = nowEpochMillis(),
                        ).toEntity(LegacyCaptureIndexImporter.CURRENT_DATA_VERSION),
                    )
                    succeeded += item.id
                }
                TrashMediaResult.PermanentDeleteRequired, TrashMediaResult.Failed -> failed += item.id
            }
        }
        repairLastCapture()
        return BatchOperationResult(succeeded, failed)
    }

    /** Compatibility for the v1.1 UI; it performs recoverable trash, never a silent permanent delete. */
    suspend fun delete(ids: Set<String>): BatchOperationResult = trash(ids)

    suspend fun restore(ids: Set<String>): BatchOperationResult {
        val items = captureDao.byIds(ids).mapNotNull(CaptureEntity::toDomain)
            .filter { it.state == CaptureRecordState.TRASHED }
        val succeeded = mutableSetOf<String>()
        val failed = (ids - items.mapTo(mutableSetOf(), CaptureItem::id)).toMutableSet()
        items.forEach { item ->
            val restored = try {
                media.restore(item)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (restored == null) failed += item.id else {
                captureDao.upsert(
                    restored.copy(state = CaptureRecordState.READY, trashedAtMillis = null)
                        .toEntity(LegacyCaptureIndexImporter.CURRENT_DATA_VERSION),
                )
                succeeded += item.id
            }
        }
        return BatchOperationResult(succeeded, failed)
    }

    suspend fun deletePermanently(ids: Set<String>): BatchOperationResult {
        val items = captureDao.byIds(ids).mapNotNull(CaptureEntity::toDomain)
        val succeeded = mutableSetOf<String>()
        val failed = (ids - items.mapTo(mutableSetOf(), CaptureItem::id)).toMutableSet()
        items.forEach { item ->
            val deleted = try {
                media.deletePermanently(item)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
            if (deleted) {
                database.withTransaction {
                    captureDao.deleteById(item.id)
                    database.playbackStateDao().delete(item.id)
                }
                succeeded += item.id
            } else failed += item.id
        }
        repairLastCapture()
        return BatchOperationResult(succeeded, failed)
    }

    suspend fun purgeExpiredTrash(retentionDays: Long = 30): BatchOperationResult {
        val cutoff = nowEpochMillis() - retentionDays * MILLIS_PER_DAY
        val ids = captureDao.expiredTrash(cutoff, PURGE_BATCH).mapTo(mutableSetOf(), CaptureEntity::id)
        return deletePermanently(ids)
    }

    suspend fun exportVault(ids: Set<String>, forceCopy: Boolean = false): BatchOperationResult {
        val sources = captureDao.byIds(ids).mapNotNull(CaptureEntity::toDomain)
            .filter { it.storageDestination == StorageDestination.CHALNA_VAULT && it.state != CaptureRecordState.TRASHED }
        val succeeded = mutableSetOf<String>()
        val failed = (ids - sources.mapTo(mutableSetOf(), CaptureItem::id)).toMutableSet()
        val produced = mutableListOf<CaptureItem>()
        sources.forEach { source ->
            val existing = source.exportedCopyId?.let { captureDao.byId(it)?.toDomain() }
            if (!forceCopy && existing != null && media.exists(existing)) {
                succeeded += source.id
                produced += existing
                return@forEach
            }
            if (existing == null && source.exportedCopyId != null) {
                captureDao.linkExport(source.id, null, LegacyCaptureIndexImporter.CURRENT_DATA_VERSION)
            }
            val copy = try {
                media.exportVaultToDeviceGallery(source)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failed += source.id
                return@forEach
            }
            try {
                database.withTransaction {
                    captureDao.upsert(
                        copy.copy(exportedFromId = source.id).toEntity(LegacyCaptureIndexImporter.CURRENT_DATA_VERSION),
                    )
                    captureDao.linkExport(source.id, copy.id, LegacyCaptureIndexImporter.CURRENT_DATA_VERSION)
                }
                succeeded += source.id
                produced += copy
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The copied media is preserved and reconciled instead of being deleted.
                database.pendingOperationDao().upsert(
                    PendingOperationEntity(
                        id = "export-${source.id}",
                        captureId = source.id,
                        type = "RECONCILE_EXPORT",
                        payload = copy.contentUri,
                        attempts = 0,
                        nextAttemptEpochMillis = nowEpochMillis(),
                        createdAtEpochMillis = nowEpochMillis(),
                    ),
                )
                succeeded += source.id
                produced += copy.copy(state = CaptureRecordState.METADATA_PENDING)
            }
        }
        return BatchOperationResult(succeeded, failed, produced)
    }

    suspend fun shareUris(ids: Set<String>): List<String> = captureDao.byIds(ids)
        .mapNotNull(CaptureEntity::toDomain)
        .filter { it.state != CaptureRecordState.TRASHED && media.exists(it) }
        .map(CaptureItem::contentUri)

    suspend fun externalOpenUri(id: String): String? = byId(id)
        ?.takeIf { it.state != CaptureRecordState.TRASHED }
        ?.contentUri

    suspend fun storageSummary(): StorageSummary = StorageSummary(
        deviceGalleryCount = captureDao.countByDestination(StorageDestination.DEVICE_GALLERY.name),
        vaultCount = captureDao.countByDestination(StorageDestination.CHALNA_VAULT.name),
        vaultBytes = captureDao.bytesByDestination(StorageDestination.CHALNA_VAULT.name),
        trashBytes = captureDao.trashBytes(),
    )

    private suspend fun removeMissing(item: CaptureItem) {
        database.withTransaction {
            captureDao.deleteById(item.id)
            database.playbackStateDao().delete(item.id)
        }
    }

    private suspend fun repairLastCapture() {
        val current = settings.lastCapture.value ?: return
        val currentId = CaptureItem.from(current).id
        val exists = captureDao.byId(currentId)?.toDomain()?.let { it.state != CaptureRecordState.TRASHED && media.exists(it) } == true
        if (!exists) settings.saveLastCapture(captureDao.latestActive()?.toDomain()?.toLastCapture())
    }

    private fun buildPagingQuery(query: GalleryQuery): SimpleSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()
        when (query.scope) {
            GalleryScope.ACTIVE -> where += "state != 'TRASHED'"
            GalleryScope.FAVORITES -> {
                where += "state != 'TRASHED'"
                where += "favorite = 1"
            }
            GalleryScope.TRASH -> where += "state = 'TRASHED'"
        }
        query.destination?.let {
            where += "storageDestination = ?"
            args += it.name
        }
        val order = when (query.sort) {
            GallerySort.NEWEST_FIRST -> "createdAtEpochMillis DESC, id DESC"
            GallerySort.OLDEST_FIRST -> "createdAtEpochMillis ASC, id ASC"
            GallerySort.LONGEST_FIRST -> "durationMillis DESC, createdAtEpochMillis DESC"
            GallerySort.LARGEST_FIRST -> "COALESCE(sizeBytes, -1) DESC, createdAtEpochMillis DESC"
        }
        return SimpleSQLiteQuery("SELECT * FROM captures WHERE ${where.joinToString(" AND ")} ORDER BY $order", args.toTypedArray())
    }

    private fun String.isOpaqueCaptureId(): Boolean = matches(Regex("[0-9a-fA-F-]{32,36}"))

    private companion object {
        const val PAGE_SIZE = 48
        const val LEGACY_UI_LIMIT = 500
        const val RECONCILE_OPEN_BATCH = 24
        const val RECONCILE_MANUAL_BATCH = 64
        const val PURGE_BATCH = 128
        const val MILLIS_PER_DAY = 86_400_000L
        const val REVERIFY_AFTER_MILLIS = 7 * MILLIS_PER_DAY
    }
}
