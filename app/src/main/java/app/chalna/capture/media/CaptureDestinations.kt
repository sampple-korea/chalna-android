package app.chalna.capture.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.core.content.FileProvider
import app.chalna.capture.domain.StorageDestination
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface CaptureOutputTarget {
    data class DeviceGallery(
        val options: FileDescriptorOutputOptions,
        val uri: Uri,
        val descriptor: ParcelFileDescriptor,
    ) : CaptureOutputTarget

    data class Vault(val options: FileOutputOptions, val file: File) : CaptureOutputTarget
}

data class PreparedCaptureOutput(
    val id: String,
    val destination: StorageDestination,
    val displayName: String,
    val target: CaptureOutputTarget,
    val contentUri: String,
    val privateRef: String? = null,
) : Closeable {
    override fun close() {
        (target as? CaptureOutputTarget.DeviceGallery)?.descriptor?.close()
    }
}

interface CaptureDestinationFactory {
    suspend fun prepare(
        destination: StorageDestination,
        id: String,
        displayName: String,
        createdAtEpochMillis: Long,
    ): PreparedCaptureOutput

    suspend fun publish(output: PreparedCaptureOutput)
    suspend fun discard(output: PreparedCaptureOutput)
}

class AndroidCaptureDestinationFactory(private val context: Context) : CaptureDestinationFactory {
    override suspend fun prepare(
        destination: StorageDestination,
        id: String,
        displayName: String,
        createdAtEpochMillis: Long,
    ): PreparedCaptureOutput = withContext(Dispatchers.IO) {
        when (destination) {
            StorageDestination.DEVICE_GALLERY -> prepareDeviceGallery(id, displayName, createdAtEpochMillis)
            StorageDestination.CHALNA_VAULT -> prepareVault(id, displayName)
        }
    }

    override suspend fun publish(output: PreparedCaptureOutput) = withContext(Dispatchers.IO) {
        when (output.target) {
            is CaptureOutputTarget.DeviceGallery -> check(
                context.contentResolver.update(
                    output.target.uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null,
                ) == 1,
            ) { "MediaStore capture could not be published" }
            is CaptureOutputTarget.Vault -> Unit
        }
    }

    override suspend fun discard(output: PreparedCaptureOutput) = withContext(Dispatchers.IO) {
        output.close()
        when (val target = output.target) {
            is CaptureOutputTarget.Vault -> target.file.delete()
            is CaptureOutputTarget.DeviceGallery -> context.contentResolver.delete(target.uri, null, null)
        }
    }

    private fun prepareDeviceGallery(
        id: String,
        displayName: String,
        createdAtEpochMillis: Long,
    ): PreparedCaptureOutput {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME_TYPE)
            put(MediaStore.Video.Media.RELATIVE_PATH, DEVICE_RELATIVE_PATH)
            put(MediaStore.Video.Media.DATE_TAKEN, createdAtEpochMillis)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values),
        ) { "MediaStore destination could not be created" }
        return try {
            val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(uri, "rw")) {
                "MediaStore file descriptor could not be opened"
            }
            PreparedCaptureOutput(
                id = id,
                destination = StorageDestination.DEVICE_GALLERY,
                displayName = displayName,
                target = CaptureOutputTarget.DeviceGallery(
                    FileDescriptorOutputOptions.Builder(descriptor).build(),
                    uri,
                    descriptor,
                ),
                contentUri = uri.toString(),
            )
        } catch (failure: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw failure
        }
    }

    private fun prepareVault(id: String, displayName: String): PreparedCaptureOutput {
        val root = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)) {
            "App-specific movie storage is unavailable"
        }
        check(Environment.getExternalStorageState(root) == Environment.MEDIA_MOUNTED) {
            "App-specific movie storage is unavailable"
        }
        val directory = File(root, VAULT_DIRECTORY).also { check(it.exists() || it.mkdirs()) }
        val file = File(directory, displayName)
        check(file.canonicalPath.startsWith(directory.canonicalPath + File.separator))
        check(!file.exists()) { "Vault capture destination already exists" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return PreparedCaptureOutput(
            id = id,
            destination = StorageDestination.CHALNA_VAULT,
            displayName = displayName,
            target = CaptureOutputTarget.Vault(FileOutputOptions.Builder(file).build(), file),
            contentUri = uri.toString(),
            privateRef = "$VAULT_DIRECTORY/$displayName",
        )
    }

    companion object {
        const val DEVICE_RELATIVE_PATH = "Movies/Chalna"
        const val VAULT_DIRECTORY = "Chalna"
        const val VIDEO_MIME_TYPE = "video/mp4"
    }
}
