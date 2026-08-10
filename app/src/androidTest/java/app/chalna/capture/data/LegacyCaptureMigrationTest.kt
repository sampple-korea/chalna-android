package app.chalna.capture.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.chalna.capture.data.db.ChalnaDatabase
import app.chalna.capture.data.db.LegacyCaptureIndexImporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

class LegacyCaptureMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: ChalnaDatabase
    private val legacy get() = File(context.filesDir, "capture-index-v1")
    private val backup get() = File(context.filesDir, "capture-index-v1.imported.bak")

    @Before fun setUp() {
        legacy.delete()
        backup.delete()
        database =
            Room
                .inMemoryDatabaseBuilder(context, ChalnaDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After fun tearDown() {
        database.close()
        legacy.delete()
        backup.delete()
    }

    @Test fun importsMixedV111FixtureOnceAndPreservesMalformedCount() =
        runBlocking {
            legacy.writeText(
                "CHALNA_INDEX_1\t1\n" +
                    row("11111111-1111-1111-1111-111111111111", "DEVICE_GALLERY", "content://media/external/video/media/1", "") +
                    "not-valid-base64\n" +
                    row("22222222-2222-2222-2222-222222222222", "CHALNA_VAULT", "content://app.chalna.capture.files/v/2", "Chalna/2.mp4"),
                StandardCharsets.UTF_8,
            )
            val importer = LegacyCaptureIndexImporter(context, database) { 1_700_000_000_000L }
            val first = importer.import(null)
            val second = importer.import(null)
            assertEquals(2, first.importedRows)
            assertEquals(1, first.malformedRows)
            assertTrue(second.alreadyComplete)
            assertEquals(2, database.captureDao().countAll())
            assertTrue(backup.isFile)
            assertTrue(!legacy.exists())
        }

    @Test fun duplicateIdentityIsIgnoredTransactionally() =
        runBlocking {
            val row = row("33333333-3333-3333-3333-333333333333", "DEVICE_GALLERY", "content://media/external/video/media/3", "")
            legacy.writeText("CHALNA_INDEX_1\t1\n$row$row", StandardCharsets.UTF_8)
            val result = LegacyCaptureIndexImporter(context, database).import(null)
            assertEquals(1, database.captureDao().countAll())
            assertEquals(1, result.importedRows)
        }

    @Test fun emptyIndexCompletesWithoutInventingRows() =
        runBlocking {
            legacy.writeText("CHALNA_INDEX_1\t1\n", StandardCharsets.UTF_8)
            val result = LegacyCaptureIndexImporter(context, database).import(null)
            assertEquals(0, result.importedRows)
            assertEquals(0, result.malformedRows)
            assertEquals(0, database.captureDao().countAll())
        }

    @Test fun hundredsOfRowsImportInOneIdempotentTransaction() =
        runBlocking {
            legacy.writeText(
                buildString {
                    append("CHALNA_INDEX_1\t1\n")
                    repeat(250) { index ->
                        val id = "%08x-0000-0000-0000-%012x".format(index, index)
                        append(row(id, "DEVICE_GALLERY", "content://media/external/video/media/${index + 1}", ""))
                    }
                },
                StandardCharsets.UTF_8,
            )
            val importer = LegacyCaptureIndexImporter(context, database)
            val first = importer.import(null)
            val second = importer.import(null)
            assertEquals(250, first.importedRows)
            assertTrue(second.alreadyComplete)
            assertEquals(250, database.captureDao().countAll())
        }

    @Test fun truncatedTailAndUnknownQualityDoNotDiscardValidRows() =
        runBlocking {
            val validId = "55555555-5555-5555-5555-555555555555"
            legacy.writeText(
                "CHALNA_INDEX_1\t1\n" +
                    row(
                        validId,
                        "DEVICE_GALLERY",
                        "content://media/external/video/media/5",
                        "",
                        quality = "FUTURE_QUALITY",
                    ) +
                    "dHJ1bmNhdGVk\n",
                StandardCharsets.UTF_8,
            )
            val result = LegacyCaptureIndexImporter(context, database).import(null)
            assertEquals(1, result.importedRows)
            assertEquals(1, result.malformedRows)
            assertEquals("UNKNOWN", database.captureDao().byId(validId)?.quality)
        }

    @Test fun backupSurvivesMigrationLaunchAndIsRemovedOnlyAfterStabilityWindow() =
        runBlocking {
            var now = 1_700_000_000_000L
            legacy.writeText(
                "CHALNA_INDEX_1\t1\n" +
                    row("44444444-4444-4444-4444-444444444444", "DEVICE_GALLERY", "content://media/external/video/media/4", ""),
                StandardCharsets.UTF_8,
            )
            val importer = LegacyCaptureIndexImporter(context, database) { now }
            importer.import(null)
            backup.setLastModified(now)
            importer.cleanupVerifiedBackup()
            assertTrue(backup.isFile)
            now += 24L * 60L * 60L * 1_000L + 1L
            importer.cleanupVerifiedBackup()
            assertTrue(!backup.exists())
        }

    private fun row(
        id: String,
        destination: String,
        uri: String,
        privateRef: String,
        quality: String = "FHD",
    ): String =
        listOf(
            id,
            destination,
            uri,
            privateRef,
            "$id.mp4",
            "1700000000000",
            "1200",
            quality,
            "1",
            "1024",
            "1920",
            "1080",
            "1",
        ).joinToString("\t") { encoder.encodeToString(it.toByteArray(StandardCharsets.UTF_8)) } + "\n"

    private companion object {
        val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
