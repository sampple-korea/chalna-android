package app.chalna.capture.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Upsert
    suspend fun upsert(entity: CaptureEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CaptureEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringDuplicates(entities: List<CaptureEntity>): List<Long>

    @Query("SELECT * FROM captures WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): CaptureEntity?

    @Query("SELECT * FROM captures WHERE id IN (:ids)")
    suspend fun byIds(ids: Set<String>): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE state != 'TRASHED' ORDER BY createdAtEpochMillis DESC, id DESC")
    fun observeActive(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE state != 'TRASHED' ORDER BY createdAtEpochMillis DESC, id DESC LIMIT :limit")
    suspend fun activePage(limit: Int): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE state != 'TRASHED' ORDER BY createdAtEpochMillis DESC, id DESC LIMIT 1")
    suspend fun latestActive(): CaptureEntity?

    @RawQuery(observedEntities = [CaptureEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, CaptureEntity>

    @Query("UPDATE captures SET favorite = :favorite, updatedVersion = :version WHERE id IN (:ids)")
    suspend fun setFavorite(ids: Set<String>, favorite: Boolean, version: Int): Int

    @Query("UPDATE captures SET state = 'TRASHED', trashedAtEpochMillis = :trashedAt, updatedVersion = :version WHERE id = :id")
    suspend fun markTrashed(id: String, trashedAt: Long, version: Int): Int

    @Query("UPDATE captures SET state = 'READY', trashedAtEpochMillis = NULL, updatedVersion = :version WHERE id = :id")
    suspend fun restore(id: String, version: Int): Int

    @Query("UPDATE captures SET lastVerifiedEpochMillis = :verifiedAt, updatedVersion = :version WHERE id = :id")
    suspend fun markVerified(id: String, verifiedAt: Long, version: Int): Int

    @Query("UPDATE captures SET exportedCopyId = :copyId, updatedVersion = :version WHERE id = :sourceId")
    suspend fun linkExport(sourceId: String, copyId: String?, version: Int): Int

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM captures WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Set<String>): Int

    @Query("SELECT * FROM captures WHERE lastVerifiedEpochMillis IS NULL OR lastVerifiedEpochMillis < :before ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    suspend fun reconciliationBatch(before: Long, limit: Int): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE state = 'TRASHED' AND trashedAtEpochMillis <= :before ORDER BY trashedAtEpochMillis ASC LIMIT :limit")
    suspend fun expiredTrash(before: Long, limit: Int): List<CaptureEntity>

    @Query("SELECT COUNT(*) FROM captures")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM captures WHERE storageDestination = :destination AND state != 'TRASHED'")
    suspend fun countByDestination(destination: String): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM captures WHERE storageDestination = :destination AND state != 'TRASHED'")
    suspend fun bytesByDestination(destination: String): Long

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM captures WHERE state = 'TRASHED'")
    suspend fun trashBytes(): Long
}

@Dao
interface PendingOperationDao {
    @Upsert
    suspend fun upsert(entity: PendingOperationEntity)

    @Query("SELECT * FROM pending_operations WHERE nextAttemptEpochMillis <= :now ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<PendingOperationEntity>

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("SELECT COUNT(*) FROM pending_operations")
    suspend fun count(): Int
}

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE captureId = :captureId LIMIT 1")
    suspend fun byCaptureId(captureId: String): PlaybackStateEntity?

    @Upsert
    suspend fun upsert(entity: PlaybackStateEntity)

    @Query("DELETE FROM playback_state WHERE captureId = :captureId")
    suspend fun delete(captureId: String): Int
}

@Dao
interface MigrationMarkerDao {
    @Query("SELECT * FROM migration_markers WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): MigrationMarkerEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MigrationMarkerEntity): Long
}
