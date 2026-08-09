package app.chalna.capture.data

import android.content.Context
import android.util.AtomicFile
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.domain.StorageDestination
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CaptureIndexSnapshot(
    val migrationComplete: Boolean = false,
    val items: List<CaptureItem> = emptyList(),
)

interface CaptureIndexStore {
    suspend fun read(): CaptureIndexSnapshot
    suspend fun write(snapshot: CaptureIndexSnapshot)
}

fun interface CaptureExistence {
    suspend fun exists(item: CaptureItem): Boolean
}

fun interface KnownCaptureDiscovery {
    suspend fun discover(): List<CaptureItem>
}

class CaptureIndex(private val store: CaptureIndexStore) {
    private val mutex = processMutex

    suspend fun migrate(
        lastCapture: LastCapture?,
        discovery: KnownCaptureDiscovery = KnownCaptureDiscovery { emptyList() },
    ): List<CaptureItem> = mutex.withLock {
        val current = store.read()
        if (current.migrationComplete) return@withLock current.items
        val candidates = buildList {
            lastCapture?.takeIf(LastCapture::isUsable)?.let { add(CaptureItem.from(it)) }
            addAll(runCatching { discovery.discover() }.getOrDefault(emptyList()))
        }
        val merged = merge(current.items, candidates)
        store.write(CaptureIndexSnapshot(migrationComplete = true, items = merged))
        merged
    }

    suspend fun recordFinalized(capture: LastCapture): CaptureItem = mutex.withLock {
        require(capture.isUsable()) { "Only a successful finalized capture can be indexed" }
        val item = CaptureItem.from(capture)
        require(item.isUsable()) { "Finalized capture metadata is invalid" }
        val current = store.read()
        store.write(current.copy(items = merge(current.items, listOf(item))))
        item
    }

    suspend fun upsert(items: Collection<CaptureItem>): List<CaptureItem> = mutex.withLock {
        if (items.isEmpty()) return@withLock store.read().items
        require(items.all(CaptureItem::isUsable)) { "Indexed capture metadata is invalid" }
        val current = store.read()
        val merged = merge(current.items, items.toList())
        if (merged != current.items) store.write(current.copy(items = merged))
        merged
    }

    suspend fun updateExisting(items: Collection<CaptureItem>): List<CaptureItem> = mutex.withLock {
        if (items.isEmpty()) return@withLock store.read().items
        require(items.all(CaptureItem::isUsable)) { "Indexed capture metadata is invalid" }
        val current = store.read()
        val ids = current.items.mapTo(mutableSetOf(), CaptureItem::id)
        val merged = merge(current.items, items.filter { it.id in ids })
        if (merged != current.items) store.write(current.copy(items = merged))
        merged
    }

    suspend fun list(existence: CaptureExistence? = null): List<CaptureItem> = mutex.withLock {
        val current = store.read()
        if (existence == null) return@withLock current.items
        val fresh = current.items.filter { runCatching { existence.exists(it) }.getOrDefault(false) }
        if (fresh.size != current.items.size) store.write(current.copy(items = fresh))
        fresh
    }

    suspend fun remove(ids: Set<String>): List<CaptureItem> = mutex.withLock {
        val current = store.read()
        val remaining = current.items.filterNot { it.id in ids }
        if (remaining.size != current.items.size) store.write(current.copy(items = remaining))
        remaining
    }

    private fun merge(existing: List<CaptureItem>, additions: List<CaptureItem>): List<CaptureItem> {
        val byIdentity = LinkedHashMap<String, CaptureItem>()
        (existing + additions).filter(CaptureItem::isUsable).forEach { item ->
            val identity = "${item.storageDestination}:${item.privateRef ?: item.contentUri}"
            val previous = byIdentity[identity]
            if (previous == null || item.createdAtMillis >= previous.createdAtMillis) byIdentity[identity] = item
        }
        return byIdentity.values.distinctBy(CaptureItem::id)
    }

    private companion object {
        val processMutex = Mutex()
    }
}

class FileCaptureIndexStore(context: Context) : CaptureIndexStore {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "capture-index-v1"))

    override suspend fun read(): CaptureIndexSnapshot = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext CaptureIndexSnapshot()
        runCatching { file.openRead().bufferedReader(StandardCharsets.UTF_8).use { CaptureIndexCodec.decode(it.readText()) } }
            .getOrDefault(CaptureIndexSnapshot())
    }

    override suspend fun write(snapshot: CaptureIndexSnapshot) = withContext(Dispatchers.IO) {
        val stream = file.startWrite()
        try {
            val writer = stream.writer(StandardCharsets.UTF_8)
            writer.write(CaptureIndexCodec.encode(snapshot))
            writer.flush()
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }
}

object CaptureIndexCodec {
    private const val VERSION = "CHALNA_INDEX_1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(snapshot: CaptureIndexSnapshot): String = buildString {
        append(VERSION).append('\t').append(if (snapshot.migrationComplete) '1' else '0').append('\n')
        snapshot.items.forEach { item ->
            append(
                listOf(
                    item.id,
                    item.storageDestination.name,
                    item.contentUri,
                    item.privateRef.orEmpty(),
                    item.displayName,
                    item.createdAtMillis.toString(),
                    item.durationMillis.toString(),
                    item.quality.name,
                    if (item.audioIncluded) "1" else "0",
                    item.sizeBytes?.toString().orEmpty(),
                    item.width?.toString().orEmpty(),
                    item.height?.toString().orEmpty(),
                ).joinToString("\t", transform = ::encodeField),
            ).append('\n')
        }
    }

    fun decode(raw: String): CaptureIndexSnapshot {
        val lines = raw.lineSequence().filter(String::isNotBlank).toList()
        val header = lines.firstOrNull()?.split('\t') ?: return CaptureIndexSnapshot()
        if (header.firstOrNull() != VERSION) return CaptureIndexSnapshot()
        val items = lines.drop(1).mapNotNull { line ->
            runCatching {
                val f = line.split('\t').map(::decodeField)
                if (f.size != 12) return@runCatching null
                CaptureItem(
                    id = f[0],
                    storageDestination = StorageDestination.valueOf(f[1]),
                    contentUri = f[2],
                    privateRef = f[3].ifBlank { null },
                    displayName = f[4],
                    createdAtMillis = f[5].toLong(),
                    durationMillis = f[6].toLong(),
                    quality = CaptureQuality.valueOf(f[7]),
                    audioIncluded = f[8] == "1",
                    sizeBytes = f[9].toLongOrNull(),
                    width = f[10].toIntOrNull(),
                    height = f[11].toIntOrNull(),
                ).takeIf(CaptureItem::isUsable)
            }.getOrNull()
        }
        return CaptureIndexSnapshot(header.getOrNull(1) == "1", items)
    }

    private fun encodeField(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)
}
