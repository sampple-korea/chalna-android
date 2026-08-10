package app.chalna.capture.data

import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureRecordState
import app.chalna.capture.domain.StorageDestination
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Read-only representation of the v1.1 AtomicFile index, retained solely for upgrade import. */
data class LegacyCaptureIndexSnapshot(
    val migrationComplete: Boolean = false,
    val items: List<CaptureItem> = emptyList(),
    val malformedRows: Int = 0,
)

object LegacyCaptureIndexCodec {
    private const val VERSION = "CHALNA_INDEX_1"
    private val decoder = Base64.getUrlDecoder()

    fun decode(raw: String): LegacyCaptureIndexSnapshot {
        val lines = raw.lineSequence().filter(String::isNotBlank).toList()
        val header = lines.firstOrNull()?.split('\t') ?: return LegacyCaptureIndexSnapshot()
        if (header.firstOrNull() != VERSION) return LegacyCaptureIndexSnapshot(malformedRows = lines.size)
        var malformed = 0
        val items =
            lines.drop(1).mapNotNull { line ->
                val item =
                    runCatching {
                        val fields = line.split('\t').map(::decodeField)
                        if (fields.size !in 12..13) return@runCatching null
                        CaptureItem(
                            id = fields[0],
                            storageDestination = StorageDestination.valueOf(fields[1]),
                            contentUri = fields[2],
                            privateRef = fields[3].ifBlank { null },
                            displayName = fields[4],
                            createdAtMillis = fields[5].toLong(),
                            durationMillis = fields[6].toLong(),
                            quality = runCatching { CaptureQuality.valueOf(fields[7]) }.getOrDefault(CaptureQuality.UNKNOWN),
                            audioIncluded = fields[8] == "1",
                            sizeBytes = fields[9].toLongOrNull(),
                            width = fields[10].toIntOrNull(),
                            height = fields[11].toIntOrNull(),
                            audioKnown = fields.getOrNull(12)?.let { it == "1" } ?: true,
                            metadataKnown = fields[6].toLongOrNull()?.let { it > 0 } == true,
                            state = CaptureRecordState.READY,
                        ).takeIf(CaptureItem::isUsable)
                    }.getOrNull()
                if (item == null) malformed++
                item
            }
        return LegacyCaptureIndexSnapshot(header.getOrNull(1) == "1", items, malformed)
    }

    private fun decodeField(value: String): String = String(decoder.decode(value), StandardCharsets.UTF_8)
}
