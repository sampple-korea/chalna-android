package app.chalna.capture.media

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.core.content.FileProvider
import app.chalna.capture.domain.StorageDestination
import java.io.File

sealed interface CaptureOutputTarget {
    data class DeviceGallery(val options: MediaStoreOutputOptions) : CaptureOutputTarget
    data class Vault(val options: FileOutputOptions, val file: File) : CaptureOutputTarget
}

data class PreparedCaptureOutput(
    val id: String,
    val destination: StorageDestination,
    val displayName: String,
    val target: CaptureOutputTarget,
    val safeContentUri: String? = null,
    val privateRef: String? = null,
)

interface CaptureDestinationFactory {
    fun prepare(destination: StorageDestination, id: String, displayName: String): PreparedCaptureOutput
    fun discard(output: PreparedCaptureOutput, cameraXOutputUri: String?)
}

class AndroidCaptureDestinationFactory(private val context: Context) : CaptureDestinationFactory {
    override fun prepare(
        destination: StorageDestination,
        id: String,
        displayName: String,
    ): PreparedCaptureOutput = when (destination) {
        StorageDestination.DEVICE_GALLERY -> {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME_TYPE)
                put(MediaStore.Video.Media.RELATIVE_PATH, DEVICE_RELATIVE_PATH)
            }
            val options = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            ).setContentValues(values).build()
            PreparedCaptureOutput(id, destination, displayName, CaptureOutputTarget.DeviceGallery(options))
        }
        StorageDestination.CHALNA_VAULT -> {
            val root = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)) {
                "App-specific movie storage is unavailable"
            }
            val directory = File(root, VAULT_DIRECTORY).also { check(it.exists() || it.mkdirs()) }
            val file = File(directory, displayName)
            check(!file.exists()) { "Vault capture destination already exists" }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            PreparedCaptureOutput(
                id = id,
                destination = destination,
                displayName = displayName,
                target = CaptureOutputTarget.Vault(FileOutputOptions.Builder(file).build(), file),
                safeContentUri = uri.toString(),
                privateRef = "$VAULT_DIRECTORY/$displayName",
            )
        }
    }

    override fun discard(output: PreparedCaptureOutput, cameraXOutputUri: String?) {
        when (val target = output.target) {
            is CaptureOutputTarget.Vault -> runCatching { target.file.delete() }
            is CaptureOutputTarget.DeviceGallery -> cameraXOutputUri?.takeIf(String::isNotBlank)?.let { raw ->
                runCatching { context.contentResolver.delete(android.net.Uri.parse(raw), null, null) }
            }
        }
    }

    companion object {
        const val DEVICE_RELATIVE_PATH = "Movies/Chalna"
        const val VAULT_DIRECTORY = "Chalna"
        const val VIDEO_MIME_TYPE = "video/mp4"
    }
}
