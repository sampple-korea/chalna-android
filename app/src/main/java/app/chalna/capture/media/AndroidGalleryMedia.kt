package app.chalna.capture.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureRecordState
import app.chalna.capture.domain.StableCaptureId
import app.chalna.capture.domain.StorageDestination
import app.chalna.capture.gallery.CaptureMetadata
import app.chalna.capture.gallery.GalleryMediaGateway
import app.chalna.capture.gallery.TrashMediaResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AndroidGalleryMedia(
    private val context: Context,
) : GalleryMediaGateway {
    private val resolver get() = context.contentResolver
    private val providerAuthority = "${context.packageName}.files"

    override suspend fun exists(item: CaptureItem): Boolean =
        withContext(Dispatchers.IO) {
            when (item.storageDestination) {
                StorageDestination.CHALNA_VAULT -> vaultFile(item)?.let { it.isFile && it.length() > 0 } == true
                StorageDestination.DEVICE_GALLERY -> validMediaStoreUri(item.contentUri)?.let(::queryPositiveSize) == true
            }
        }

    override suspend fun validate(item: CaptureItem): CaptureItem? =
        withContext(Dispatchers.IO) {
            val uri =
                when (item.storageDestination) {
                    StorageDestination.CHALNA_VAULT -> {
                        val file = vaultFile(item)?.takeIf { it.isFile && it.length() > 0 } ?: return@withContext null
                        FileProvider.getUriForFile(context, providerAuthority, file)
                    }
                    StorageDestination.DEVICE_GALLERY ->
                        validMediaStoreUri(item.contentUri)
                            ?.takeIf(::queryPositiveSize) ?: return@withContext null
                }
            val metadata = extract(uri, item.sizeBytes)
            if (metadata.durationMillis?.let { it > 0 } != true || metadata.sizeBytes?.let { it > 0 } != true) {
                return@withContext null
            }
            item.copy(
                contentUri = uri.toString(),
                durationMillis = metadata.durationMillis,
                sizeBytes = metadata.sizeBytes,
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                codec = metadata.codec,
                frameRate = metadata.frameRate,
                bitrate = metadata.bitrate,
                audioIncluded = metadata.audioIncluded ?: item.audioIncluded,
                audioKnown = metadata.audioIncluded != null,
                mimeType = metadata.mimeType ?: VIDEO_MIME_TYPE,
                metadataKnown = true,
                state = if (item.state == CaptureRecordState.TRASHED) item.state else CaptureRecordState.READY,
            )
        }

    override suspend fun moveToTrash(
        item: CaptureItem,
        trashedAtEpochMillis: Long,
    ): TrashMediaResult =
        withContext(Dispatchers.IO) {
            when (item.storageDestination) {
                StorageDestination.CHALNA_VAULT -> moveVaultToTrash(item)
                StorageDestination.DEVICE_GALLERY -> {
                    if (Build.VERSION.SDK_INT < 30) return@withContext TrashMediaResult.PermanentDeleteRequired
                    val uri = validMediaStoreUri(item.contentUri) ?: return@withContext TrashMediaResult.Failed
                    val values =
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_TRASHED, 1)
                            put(MediaStore.MediaColumns.DATE_EXPIRES, (trashedAtEpochMillis / 1_000L) + TRASH_RETENTION_SECONDS)
                        }
                    if (resolver.update(uri, values, null, null) == 1) {
                        TrashMediaResult.Trashed(item)
                    } else {
                        TrashMediaResult.Failed
                    }
                }
            }
        }

    override suspend fun restore(item: CaptureItem): CaptureItem? =
        withContext(Dispatchers.IO) {
            when (item.storageDestination) {
                StorageDestination.CHALNA_VAULT -> restoreVault(item)
                StorageDestination.DEVICE_GALLERY -> {
                    if (Build.VERSION.SDK_INT < 30) return@withContext null
                    val uri = validMediaStoreUri(item.contentUri) ?: return@withContext null
                    val values =
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_TRASHED, 0)
                            putNull(MediaStore.MediaColumns.DATE_EXPIRES)
                        }
                    item.takeIf { resolver.update(uri, values, null, null) == 1 }
                }
            }
        }

    override suspend fun deletePermanently(item: CaptureItem): Boolean =
        withContext(Dispatchers.IO) {
            when (item.storageDestination) {
                StorageDestination.CHALNA_VAULT -> {
                    val file = vaultFile(item) ?: return@withContext false
                    !file.exists() || file.delete()
                }
                StorageDestination.DEVICE_GALLERY -> {
                    val uri = validMediaStoreUri(item.contentUri) ?: return@withContext false
                    resolver.delete(uri, null, null) > 0 || !queryPositiveSize(uri)
                }
            }
        }

    override suspend fun exportVaultToDeviceGallery(item: CaptureItem): CaptureItem =
        withContext(Dispatchers.IO) {
            require(item.storageDestination == StorageDestination.CHALNA_VAULT && item.state != CaptureRecordState.TRASHED) {
                "Only active Vault captures can be exported"
            }
            val source = requireNotNull(vaultFile(item)?.takeIf(File::isFile)) { "Vault capture is missing" }
            val values =
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, item.displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME_TYPE)
                    put(MediaStore.Video.Media.RELATIVE_PATH, AndroidCaptureDestinationFactory.DEVICE_RELATIVE_PATH)
                    put(MediaStore.Video.Media.DATE_TAKEN, item.createdAtMillis)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            val uri =
                requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)) {
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
                    state = CaptureRecordState.READY,
                    trashedAtMillis = null,
                    exportedFromId = item.id,
                    exportedCopyId = null,
                )
            } catch (cancellation: CancellationException) {
                runCatching { resolver.delete(uri, null, null) }
                throw cancellation
            } catch (failure: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw failure
            }
        }

    fun extract(
        uri: Uri,
        knownSize: Long? = null,
    ): CaptureMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
            val track = extractVideoTrack(uri)
            return CaptureMetadata(
                durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                sizeBytes = knownSize?.takeIf { it > 0 } ?: querySize(uri),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                rotationDegrees = rotation,
                codec = track?.codec,
                frameRate =
                    track?.frameRate
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull(),
                bitrate =
                    track?.bitrate
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                audioIncluded = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)?.let { it == "yes" },
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            )
        } finally {
            retriever.release()
        }
    }

    private fun extractVideoTrack(uri: Uri): VideoTrackMetadata? {
        val descriptor = resolver.openAssetFileDescriptor(uri, "r") ?: return null
        descriptor.use { asset ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (!mime.startsWith("video/")) continue
                    return VideoTrackMetadata(
                        codec = mime,
                        frameRate = format.optionalInt(MediaFormat.KEY_FRAME_RATE)?.toFloat(),
                        bitrate = format.optionalInt(MediaFormat.KEY_BIT_RATE),
                    )
                }
                return null
            } finally {
                extractor.release()
            }
        }
    }

    private fun MediaFormat.optionalInt(key: String): Int? = if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private data class VideoTrackMetadata(
        val codec: String?,
        val frameRate: Float?,
        val bitrate: Int?,
    )

    private fun moveVaultToTrash(item: CaptureItem): TrashMediaResult {
        val source = vaultFile(item)?.takeIf(File::isFile) ?: return TrashMediaResult.Failed
        if (source.parentFile?.name == TRASH_DIRECTORY) return TrashMediaResult.Trashed(item)
        val trash = File(requireNotNull(vaultRoot()), TRASH_DIRECTORY).also { if (!it.exists()) check(it.mkdirs()) }
        val target = File(trash, "${item.id}_${source.name}")
        if (!moveFile(source, target)) return TrashMediaResult.Failed
        val privateRef = "${AndroidCaptureDestinationFactory.VAULT_DIRECTORY}/$TRASH_DIRECTORY/${target.name}"
        val uri = FileProvider.getUriForFile(context, providerAuthority, target)
        return TrashMediaResult.Trashed(item.copy(contentUri = uri.toString(), privateRef = privateRef))
    }

    private fun restoreVault(item: CaptureItem): CaptureItem? {
        val source = vaultFile(item)?.takeIf(File::isFile) ?: return null
        if (source.parentFile?.name != TRASH_DIRECTORY) return item
        val root = vaultRoot() ?: return null
        val originalName = source.name.removePrefix("${item.id}_")
        val target = uniqueFile(root, originalName)
        if (!moveFile(source, target)) return null
        val privateRef = "${AndroidCaptureDestinationFactory.VAULT_DIRECTORY}/${target.name}"
        return item.copy(
            contentUri = FileProvider.getUriForFile(context, providerAuthority, target).toString(),
            privateRef = privateRef,
            displayName = target.name,
        )
    }

    private fun moveFile(
        source: File,
        target: File,
    ): Boolean =
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (_: AtomicMoveNotSupportedException) {
            runCatching { Files.move(source.toPath(), target.toPath()) }.isSuccess
        } catch (_: Exception) {
            false
        }

    private fun uniqueFile(
        directory: File,
        preferredName: String,
    ): File {
        val base = preferredName.substringBeforeLast('.', preferredName)
        val extension = preferredName.substringAfterLast('.', "mp4")
        var candidate = File(directory, preferredName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "${base}_$index.$extension")
            index++
        }
        return candidate
    }

    private fun queryPositiveSize(uri: Uri): Boolean =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                cursor.moveToFirst() && !cursor.isNull(0) && cursor.getLong(0) > 0
            } ?: false
        }.getOrDefault(false)

    private fun querySize(uri: Uri): Long? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 } else null
            }
        }.getOrNull()

    private fun validMediaStoreUri(raw: String): Uri? =
        runCatching { raw.toUri() }.getOrNull()?.takeIf { uri ->
            uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY &&
                uri.pathSegments.firstOrNull() in setOf("external", "external_primary")
        }

    private fun vaultRoot(): File? =
        context
            .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?.let { File(it, AndroidCaptureDestinationFactory.VAULT_DIRECTORY) }
            ?.canonicalFile

    private fun vaultFile(item: CaptureItem): File? {
        val reference = item.privateRef ?: return null
        val movies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.canonicalFile ?: return null
        val allowed = File(movies, AndroidCaptureDestinationFactory.VAULT_DIRECTORY).canonicalFile
        val candidate = File(movies, reference).canonicalFile
        return candidate.takeIf { it.path.startsWith(allowed.path + File.separator) && !Files.isSymbolicLink(it.toPath()) }
    }

    private companion object {
        const val VIDEO_MIME_TYPE = "video/mp4"
        const val TRASH_DIRECTORY = ".trash"
        const val TRASH_RETENTION_SECONDS = 30L * 24L * 60L * 60L
    }
}
