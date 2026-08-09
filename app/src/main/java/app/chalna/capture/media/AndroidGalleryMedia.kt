package app.chalna.capture.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.StableCaptureId
import app.chalna.capture.domain.StorageDestination
import app.chalna.capture.gallery.CaptureMetadata
import app.chalna.capture.gallery.CaptureMetadataExtractor
import app.chalna.capture.gallery.GalleryMediaGateway
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidGalleryMedia(private val context: Context) : GalleryMediaGateway, CaptureMetadataExtractor {
    private val resolver get() = context.contentResolver

    override suspend fun exists(item: CaptureItem): Boolean = withContext(Dispatchers.IO) {
        when (item.storageDestination) {
            StorageDestination.CHALNA_VAULT -> vaultFile(item)?.let { it.isFile && it.length() > 0 } == true
            StorageDestination.DEVICE_GALLERY -> runCatching {
                resolver.query(item.contentUri.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                    it.moveToFirst() && !it.isNull(0) && it.getLong(0) >= 0
                } ?: false
            }.getOrDefault(false)
        }
    }

    override suspend fun delete(item: CaptureItem): Boolean = withContext(Dispatchers.IO) {
        when (item.storageDestination) {
            StorageDestination.CHALNA_VAULT -> vaultFile(item)?.let { !it.exists() || it.delete() } == true
            StorageDestination.DEVICE_GALLERY -> runCatching {
                resolver.delete(item.contentUri.toUri(), null, null) > 0
            }.getOrDefault(false)
        }
    }

    override suspend fun exportVaultToDeviceGallery(item: CaptureItem): CaptureItem = withContext(Dispatchers.IO) {
        require(item.storageDestination == StorageDestination.CHALNA_VAULT) { "Only Vault captures can be exported" }
        val source = requireNotNull(vaultFile(item)?.takeIf(File::isFile)) { "Vault capture is missing" }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, item.displayName)
            put(MediaStore.Video.Media.MIME_TYPE, AndroidCaptureDestinationFactory.VIDEO_MIME_TYPE)
            put(MediaStore.Video.Media.RELATIVE_PATH, AndroidCaptureDestinationFactory.DEVICE_RELATIVE_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)) {
            "MediaStore destination could not be created"
        }
        try {
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "MediaStore output could not be opened" }
                source.inputStream().use { input -> input.copyTo(output) }
                output.flush()
            }
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) == 1) {
                "MediaStore export could not be published"
            }
            item.copy(
                id = StableCaptureId.from(StorageDestination.DEVICE_GALLERY, uri.toString()),
                storageDestination = StorageDestination.DEVICE_GALLERY,
                contentUri = uri.toString(),
                privateRef = null,
                sizeBytes = source.length(),
            )
        } catch (failure: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw failure
        }
    }

    override suspend fun discoverKnownDeviceCaptures(): List<CaptureItem> = withContext(Dispatchers.IO) {
        val columns = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )
        val deviceCaptures = runCatching {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                columns,
                "${MediaStore.Video.Media.RELATIVE_PATH}=? AND ${MediaStore.Video.Media.DISPLAY_NAME} GLOB ?",
                arrayOf("${AndroidCaptureDestinationFactory.DEVICE_RELATIVE_PATH}/", "CHALNA_*.mp4"),
                null,
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
                        add(
                            CaptureItem(
                                id = StableCaptureId.from(StorageDestination.DEVICE_GALLERY, uri.toString()),
                                storageDestination = StorageDestination.DEVICE_GALLERY,
                                contentUri = uri.toString(),
                                displayName = cursor.getString(1).orEmpty(),
                                createdAtMillis = cursor.getLong(2).coerceAtLeast(1) * 1_000L,
                                durationMillis = cursor.getLong(3).coerceAtLeast(0),
                                quality = CaptureQuality.AUTO,
                                sizeBytes = cursor.getLong(4).takeIf { it >= 0 },
                                width = cursor.getInt(5).takeIf { it > 0 },
                                height = cursor.getInt(6).takeIf { it > 0 },
                                audioKnown = false,
                            ),
                        )
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
        val vaultCaptures = runCatching {
            val movies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return@runCatching emptyList()
            val vaultRoot = File(movies, AndroidCaptureDestinationFactory.VAULT_DIRECTORY)
            vaultRoot.listFiles()
                .orEmpty()
                .asSequence()
                .filter { it.isFile && it.length() > 0 && it.name.startsWith("CHALNA_") && it.extension.equals("mp4", true) }
                .map { file ->
                    val privateRef = "${AndroidCaptureDestinationFactory.VAULT_DIRECTORY}/${file.name}"
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
                    CaptureItem(
                        id = StableCaptureId.from(StorageDestination.CHALNA_VAULT, privateRef),
                        storageDestination = StorageDestination.CHALNA_VAULT,
                        contentUri = uri,
                        privateRef = privateRef,
                        displayName = file.name,
                        createdAtMillis = file.lastModified().coerceAtLeast(1),
                        durationMillis = 0,
                        quality = CaptureQuality.AUTO,
                        sizeBytes = file.length(),
                        audioKnown = false,
                    )
                }.toList()
        }.getOrDefault(emptyList())
        deviceCaptures + vaultCaptures
    }

    override suspend fun extract(item: CaptureItem): CaptureMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.contentUri.toUri())
            CaptureMetadata(
                durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                sizeBytes = item.sizeBytes ?: querySize(item.contentUri.toUri()),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
            )
        } finally {
            retriever.release()
        }
    }

    private fun querySize(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun vaultFile(item: CaptureItem): File? {
        val ref = item.privateRef ?: return null
        val root = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.canonicalFile ?: return null
        val candidate = File(root, ref).canonicalFile
        return candidate.takeIf { it.path.startsWith(root.path + File.separator) }
    }
}
