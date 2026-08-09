package app.chalna.capture.capture

import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.AtomicFile
import app.chalna.capture.domain.StorageDestination
import app.chalna.capture.media.AndroidCaptureDestinationFactory
import app.chalna.capture.media.PreparedCaptureOutput
import app.chalna.capture.notifications.CaptureNotifications
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Durable identity for the one output that may be incomplete after abrupt process death. */
class CaptureAttemptStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = AtomicFile(File(appContext.filesDir, FILE_NAME))

    fun hasAttempt(): Boolean = file.baseFile.exists()

    suspend fun mark(output: PreparedCaptureOutput) = recoveryMutex.withLock {
        withContext(Dispatchers.IO) {
            val stream = file.startWrite()
            try {
                DataOutputStream(stream).apply {
                    writeUTF(output.id)
                    writeUTF(output.destination.name)
                    writeUTF(output.displayName)
                    writeUTF(output.privateRef.orEmpty())
                    flush()
                }
                file.finishWrite(stream)
            } catch (failure: Throwable) {
                file.failWrite(stream)
                throw failure
            }
        }
    }

    suspend fun clear(): Boolean = recoveryMutex.withLock {
        withContext(Dispatchers.IO) {
            file.delete()
            !file.baseFile.exists()
        }
    }

    suspend fun recover(cancelStaleNotification: Boolean = true) = recoveryMutex.withLock {
        withContext(Dispatchers.IO) {
            val attempt = readAttempt()
            if (attempt != null && attempt.id.isNotBlank() && attempt.displayName.isValidCaptureName()) {
                runCatching {
                    when (attempt.destination) {
                        StorageDestination.CHALNA_VAULT -> deleteVaultAttempt(attempt.privateRef)
                        StorageDestination.DEVICE_GALLERY -> deletePendingMediaStoreAttempt(attempt.displayName)
                    }
                }
            }
            file.delete()
            if (cancelStaleNotification) {
                appContext.getSystemService(NotificationManager::class.java)
                    .cancel(CaptureNotifications.NOTIFICATION_ID)
            }
        }
    }

    private fun readAttempt(): Attempt? = runCatching {
        file.openRead().use { input ->
            DataInputStream(input).use { data ->
                Attempt(
                    id = data.readUTF(),
                    destination = StorageDestination.valueOf(data.readUTF()),
                    displayName = data.readUTF(),
                    privateRef = data.readUTF().ifBlank { null },
                )
            }
        }
    }.getOrNull()

    private fun deleteVaultAttempt(privateRef: String?) {
        if (privateRef.isNullOrBlank()) return
        val root = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.canonicalFile ?: return
        val candidate = File(root, privateRef).canonicalFile
        if (candidate.path.startsWith(root.path + File.separator)) candidate.delete()
    }

    private fun deletePendingMediaStoreAttempt(displayName: String?) {
        if (!displayName.isValidCaptureName()) return
        val resolver = appContext.contentResolver
        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID),
            "${MediaStore.Video.Media.DISPLAY_NAME}=? AND ${MediaStore.Video.Media.RELATIVE_PATH}=? AND ${MediaStore.Video.Media.IS_PENDING}=1",
            arrayOf(displayName, "${AndroidCaptureDestinationFactory.DEVICE_RELATIVE_PATH}/"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                resolver.delete(
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)),
                    null,
                    null,
                )
            }
        }
    }

    private fun String?.isValidCaptureName(): Boolean =
        this != null && startsWith("CHALNA_") && endsWith(".mp4", ignoreCase = true) &&
            none { it == '/' || it == '\\' }

    private data class Attempt(
        val id: String,
        val destination: StorageDestination,
        val displayName: String,
        val privateRef: String?,
    )

    private companion object {
        const val FILE_NAME = "active-capture-attempt"
        val recoveryMutex = Mutex()
    }
}
