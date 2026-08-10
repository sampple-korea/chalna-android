package app.chalna.capture.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "captures",
    indices = [
        Index(value = ["contentUri"], unique = true),
        Index(value = ["privateRef"], unique = true),
        Index(value = ["createdAtEpochMillis"]),
        Index(value = ["storageDestination", "state"]),
        Index(value = ["favorite", "state"]),
        Index(value = ["trashedAtEpochMillis"]),
    ],
)
data class CaptureEntity(
    @PrimaryKey val id: String,
    val storageDestination: String,
    val contentUri: String,
    val privateRef: String?,
    val displayName: String,
    val createdAtEpochMillis: Long,
    val durationMillis: Long,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val rotationDegrees: Int?,
    val quality: String,
    val codec: String?,
    val frameRate: Float?,
    val bitrate: Int?,
    val audioIncluded: Boolean,
    val audioKnown: Boolean,
    val mimeType: String,
    val metadataKnown: Boolean,
    val state: String,
    val favorite: Boolean,
    val trashedAtEpochMillis: Long?,
    val exportedFromId: String?,
    val exportedCopyId: String?,
    val lastVerifiedEpochMillis: Long?,
    val createdVersion: Int,
    val updatedVersion: Int,
)

@Entity(
    tableName = "pending_operations",
    indices = [Index(value = ["captureId", "type"], unique = true), Index(value = ["nextAttemptEpochMillis"])],
)
data class PendingOperationEntity(
    @PrimaryKey val id: String,
    val captureId: String,
    val type: String,
    val payload: String?,
    val attempts: Int,
    val nextAttemptEpochMillis: Long,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val captureId: String,
    val positionMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "migration_markers")
data class MigrationMarkerEntity(
    @PrimaryKey val name: String,
    val completedAtEpochMillis: Long,
    val importedRows: Int,
    val malformedRows: Int,
)
