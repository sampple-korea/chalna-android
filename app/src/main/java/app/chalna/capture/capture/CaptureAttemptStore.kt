package app.chalna.capture.capture

import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import app.chalna.capture.domain.StorageDestination
import app.chalna.capture.media.AndroidCaptureDestinationFactory
import app.chalna.capture.media.PreparedCaptureOutput
import app.chalna.capture.notifications.CaptureNotifications
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Durable identity for the one output that may be incomplete after abrupt process death. */
class CaptureAttemptStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun hasAttempt(): Boolean = preferences.contains(KEY_ID)

    suspend fun mark(output: PreparedCaptureOutput) = withContext(Dispatchers.IO) {
        check(
            preferences.edit()
                .putString(KEY_ID, output.id)
                .putString(KEY_DESTINATION, output.destination.name)
                .putString(KEY_DISPLAY_NAME, output.displayName)
                .putString(KEY_PRIVATE_REF, output.privateRef)
                .commit(),
        ) { "Capture recovery marker could not be stored" }
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        preferences.edit().clear().commit()
    }

    suspend fun recover(cancelStaleNotification: Boolean = true) = recoveryMutex.withLock {
        withContext(Dispatchers.IO) {
            val id = preferences.getString(KEY_ID, null) ?: return@withContext
            val destination = preferences.getString(KEY_DESTINATION, null)
                ?.let { runCatching { StorageDestination.valueOf(it) }.getOrNull() }
            val displayName = preferences.getString(KEY_DISPLAY_NAME, null)
            val privateRef = preferences.getString(KEY_PRIVATE_REF, null)
            if (id.isNotBlank() && displayName.isValidCaptureName()) {
                runCatching {
                    when (destination) {
                        StorageDestination.CHALNA_VAULT -> deleteVaultAttempt(privateRef)
                        StorageDestination.DEVICE_GALLERY -> deletePendingMediaStoreAttempt(displayName)
                        null -> Unit
                    }
                }
            }
            preferences.edit().clear().commit()
            if (cancelStaleNotification) {
                appContext.getSystemService(NotificationManager::class.java)
                    .cancel(CaptureNotifications.NOTIFICATION_ID)
            }
        }
    }

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

    private companion object {
        const val PREFERENCES = "active_capture_attempt"
        const val KEY_ID = "id"
        const val KEY_DESTINATION = "destination"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PRIVATE_REF = "private_ref"
        val recoveryMutex = Mutex()
    }
}
