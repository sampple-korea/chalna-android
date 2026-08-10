package app.chalna.capture.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CaptureEntity::class,
        PendingOperationEntity::class,
        PlaybackStateEntity::class,
        MigrationMarkerEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ChalnaDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    abstract fun pendingOperationDao(): PendingOperationDao

    abstract fun playbackStateDao(): PlaybackStateDao

    abstract fun migrationMarkerDao(): MigrationMarkerDao

    companion object {
        @Volatile private var instance: ChalnaDatabase? = null

        fun get(context: Context): ChalnaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        ChalnaDatabase::class.java,
                        "chalna.db",
                    ).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}
