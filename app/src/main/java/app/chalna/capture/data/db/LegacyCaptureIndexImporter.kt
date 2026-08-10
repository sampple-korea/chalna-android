package app.chalna.capture.data.db

import android.content.Context
import androidx.room.withTransaction
import app.chalna.capture.data.LegacyCaptureIndexCodec
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.LastCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

data class LegacyImportResult(
    val importedRows: Int,
    val malformedRows: Int,
    val alreadyComplete: Boolean,
)

class LegacyCaptureIndexImporter(
    context: Context,
    private val database: ChalnaDatabase,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val legacy = File(context.applicationContext.filesDir, LEGACY_NAME)
    private val backup = File(context.applicationContext.filesDir, BACKUP_NAME)

    suspend fun import(lastCapture: LastCapture?): LegacyImportResult =
        withContext(Dispatchers.IO) {
            database.migrationMarkerDao().byName(MARKER)?.let {
                return@withContext LegacyImportResult(it.importedRows, it.malformedRows, true)
            }
            val decoded =
                if (legacy.isFile) {
                    runCatching { LegacyCaptureIndexCodec.decode(legacy.readText(StandardCharsets.UTF_8)) }
                        .getOrDefault(
                            app.chalna.capture.data
                                .LegacyCaptureIndexSnapshot(malformedRows = 1),
                        )
                } else {
                    app.chalna.capture.data
                        .LegacyCaptureIndexSnapshot()
                }
            val candidates =
                buildList {
                    addAll(decoded.items)
                    lastCapture?.takeIf(LastCapture::isUsable)?.let { add(CaptureItem.from(it)) }
                }.distinctBy { "${it.storageDestination}:${it.privateRef ?: it.contentUri}" }
            database.withTransaction {
                database.captureDao().insertAllIgnoringDuplicates(candidates.map { it.toEntity(CURRENT_DATA_VERSION) })
                database.migrationMarkerDao().insert(
                    MigrationMarkerEntity(MARKER, nowEpochMillis(), candidates.size, decoded.malformedRows),
                )
            }
            val marker = requireNotNull(database.migrationMarkerDao().byName(MARKER))
            if (legacy.isFile && !backup.exists()) check(legacy.renameTo(backup))
            LegacyImportResult(marker.importedRows, marker.malformedRows, false)
        }

    suspend fun cleanupVerifiedBackup() =
        withContext(Dispatchers.IO) {
            val marker = database.migrationMarkerDao().byName(MARKER) ?: return@withContext
            val backupAge = nowEpochMillis() - backup.lastModified()
            if (marker.importedRows <= database.captureDao().countAll() && backup.isFile &&
                backupAge >= BACKUP_STABILITY_WINDOW_MILLIS
            ) {
                backup.delete()
            }
        }

    companion object {
        const val CURRENT_DATA_VERSION = 4
        private const val LEGACY_NAME = "capture-index-v1"
        private const val BACKUP_NAME = "capture-index-v1.imported.bak"
        private const val MARKER = "legacy_capture_index_v1"
        private const val BACKUP_STABILITY_WINDOW_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
