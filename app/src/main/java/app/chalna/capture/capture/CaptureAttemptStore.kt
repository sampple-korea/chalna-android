package app.chalna.capture.capture

import android.content.Context
import android.util.AtomicFile
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureSessionSettings
import app.chalna.capture.domain.StorageDestination
import app.chalna.capture.media.PreparedCaptureOutput
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class CaptureAttemptStage {
    OUTPUT_CREATED,
    CAMERA_BOUND,
    RECORDER_START_REQUESTED,
    RECORDING,
    FINALIZING,
    MEDIA_SAVED,
}

data class CaptureAttempt(
    val invocationId: String,
    val captureId: String,
    val destination: StorageDestination,
    val contentUri: String,
    val privateRef: String?,
    val displayName: String,
    val startedAtEpochMillis: Long,
    val startedAtElapsedNanos: Long,
    val requestedAudio: Boolean,
    val requestedQuality: CaptureQuality,
    val stage: CaptureAttemptStage,
)

/** Durable journal for the single output that may outlive an abrupt process death. */
class CaptureAttemptStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))

    fun hasAttempt(): Boolean = file.baseFile.isFile

    suspend fun mark(
        invocationId: String,
        output: PreparedCaptureOutput,
        settings: CaptureSessionSettings,
        startedAtEpochMillis: Long,
        startedAtElapsedNanos: Long,
    ) = write(
        CaptureAttempt(
            invocationId = invocationId,
            captureId = output.id,
            destination = output.destination,
            contentUri = output.contentUri,
            privateRef = output.privateRef,
            displayName = output.displayName,
            startedAtEpochMillis = startedAtEpochMillis,
            startedAtElapsedNanos = startedAtElapsedNanos,
            requestedAudio = settings.audioEnabled,
            requestedQuality = settings.preferredQuality,
            stage = CaptureAttemptStage.OUTPUT_CREATED,
        ),
    )

    suspend fun updateStage(stage: CaptureAttemptStage) = journalMutex.withLock {
        val current = readUnsafe() ?: return@withLock
        writeUnsafe(current.copy(stage = stage))
    }

    suspend fun read(): CaptureAttempt? = journalMutex.withLock { readUnsafe() }

    suspend fun clear(): Boolean = journalMutex.withLock {
        withContext(Dispatchers.IO) {
            file.delete()
            !file.baseFile.exists()
        }
    }

    private suspend fun write(attempt: CaptureAttempt) = journalMutex.withLock { writeUnsafe(attempt) }

    private suspend fun writeUnsafe(attempt: CaptureAttempt) = withContext(Dispatchers.IO) {
        val stream = file.startWrite()
        try {
            val data = DataOutputStream(stream)
            data.writeInt(FORMAT_VERSION)
            data.writeUTF(attempt.invocationId)
            data.writeUTF(attempt.captureId)
            data.writeUTF(attempt.destination.name)
            data.writeUTF(attempt.contentUri)
            data.writeUTF(attempt.privateRef.orEmpty())
            data.writeUTF(attempt.displayName)
            data.writeLong(attempt.startedAtEpochMillis)
            data.writeLong(attempt.startedAtElapsedNanos)
            data.writeBoolean(attempt.requestedAudio)
            data.writeUTF(attempt.requestedQuality.name)
            data.writeUTF(attempt.stage.name)
            data.flush()
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    private suspend fun readUnsafe(): CaptureAttempt? = withContext(Dispatchers.IO) {
        if (!file.baseFile.isFile) return@withContext null
        runCatching {
            file.openRead().use { input ->
                DataInputStream(input).use { data ->
                    check(data.readInt() == FORMAT_VERSION)
                    CaptureAttempt(
                        invocationId = data.readUTF(),
                        captureId = data.readUTF(),
                        destination = StorageDestination.valueOf(data.readUTF()),
                        contentUri = data.readUTF(),
                        privateRef = data.readUTF().ifBlank { null },
                        displayName = data.readUTF(),
                        startedAtEpochMillis = data.readLong(),
                        startedAtElapsedNanos = data.readLong(),
                        requestedAudio = data.readBoolean(),
                        requestedQuality = runCatching { CaptureQuality.valueOf(data.readUTF()) }
                            .getOrDefault(CaptureQuality.UNKNOWN),
                        stage = CaptureAttemptStage.valueOf(data.readUTF()),
                    )
                }
            }
        }.getOrNull()
    }

    private companion object {
        const val FILE_NAME = "active-capture-attempt-v2"
        const val FORMAT_VERSION = 2
        val journalMutex = Mutex()
    }
}
