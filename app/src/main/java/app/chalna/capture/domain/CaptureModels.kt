package app.chalna.capture.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CancellationException

enum class CaptureQuality { AUTO, FHD, HD, SD, UNKNOWN }

enum class ThemePreference { SYSTEM, NIGHT, MIST }

enum class MotionPreference { SYSTEM, FULL, REDUCED }

enum class StorageDestination { DEVICE_GALLERY, CHALNA_VAULT }

object StorageDestinationPolicy {
    fun fromPersisted(value: String?): StorageDestination = value?.let {
        runCatching { StorageDestination.valueOf(it) }.getOrNull()
    } ?: StorageDestination.DEVICE_GALLERY
}

data class CaptureSettings(
    val audioEnabled: Boolean = true,
    val preferredQuality: CaptureQuality = CaptureQuality.AUTO,
    val hapticsEnabled: Boolean = true,
    val autoStopSeconds: Int = 0,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val motion: MotionPreference = MotionPreference.SYSTEM,
    val onboardingSeen: Boolean = false,
    val storageDestination: StorageDestination = StorageDestination.DEVICE_GALLERY,
)

data class CaptureSessionSettings(
    val audioEnabled: Boolean,
    val preferredQuality: CaptureQuality,
    val storageDestination: StorageDestination,
    val autoStopSeconds: Int,
) {
    companion object {
        fun snapshot(settings: CaptureSettings): CaptureSessionSettings = CaptureSessionSettings(
            audioEnabled = settings.audioEnabled,
            preferredQuality = settings.preferredQuality,
            storageDestination = settings.storageDestination,
            autoStopSeconds = settings.autoStopSeconds,
        )
    }
}

enum class CaptureTrigger {
    ASSISTANT,
    KEYGUARD,
    ACTIVITY,
    NOTIFICATION,
    QUICK_TILE,
    AUTO_STOP,
    RECOVERY,
    SYSTEM,
}

enum class CaptureCommand {
    START,
    STOP,
    TOGGLE,
    CANCEL_START,
    AUTO_STOP,
    NOTIFICATION_STOP,
    QUICK_TILE_TOGGLE,
    RECOVERY,
    SERVICE_DESTROYED,
}

data class CaptureRequest(
    val invocationId: String,
    val command: CaptureCommand,
    val trigger: CaptureTrigger,
    val receivedElapsedNanos: Long,
)

enum class CaptureFailureCode {
    CAMERA_PERMISSION,
    MICROPHONE_PERMISSION,
    CAMERA_UNAVAILABLE,
    CAMERA_BUSY,
    FOREGROUND_START_NOT_ALLOWED,
    FOREGROUND_TYPE_MISSING,
    STORAGE_UNAVAILABLE,
    LOW_STORAGE,
    RECORDER_START,
    FINALIZE,
    OUTPUT_INVALID,
    DISPATCH,
    INTERRUPTED,
    INTERNAL,
}

data class CaptureFailure(
    val code: CaptureFailureCode,
    val recoverable: Boolean,
    val diagnostic: String? = null,
)

sealed interface CaptureState {
    data object Idle : CaptureState
    data class StartRequested(val invocationId: String) : CaptureState
    data class StartingForeground(val invocationId: String) : CaptureState
    data class OpeningCamera(val invocationId: String) : CaptureState
    data class StartingRecorder(val invocationId: String) : CaptureState
    data class Recording(
        val invocationId: String,
        val startedAtEpochMillis: Long,
        val startedAtElapsedNanos: Long,
        val recordedDurationNanos: Long = 0,
        val bytesRecorded: Long = 0,
    ) : CaptureState
    data class CancelRequested(val invocationId: String) : CaptureState
    data class StopRequested(val invocationId: String) : CaptureState
    data class StoppingRecorder(val invocationId: String) : CaptureState
    data class Finalizing(val invocationId: String) : CaptureState
    data class Persisting(val capture: LastCapture) : CaptureState
    data class Saved(val capture: LastCapture, val completedAtElapsedNanos: Long) : CaptureState
    data class Recovering(val invocationId: String) : CaptureState
    data class Failed(val failure: CaptureFailure) : CaptureState
}

sealed interface CaptureCommandResult {
    val invocationId: String

    data class AcceptedStart(override val invocationId: String) : CaptureCommandResult
    data class AcceptedStop(override val invocationId: String) : CaptureCommandResult
    data class AcceptedCancelStart(override val invocationId: String) : CaptureCommandResult
    data class AlreadyStopping(override val invocationId: String) : CaptureCommandResult
    data class BusySaving(override val invocationId: String) : CaptureCommandResult
    data class NoActiveCapture(override val invocationId: String) : CaptureCommandResult
    data class Duplicate(override val invocationId: String, val original: CaptureCommandResult?) : CaptureCommandResult
    data class Rejected(override val invocationId: String, val failure: CaptureFailure) : CaptureCommandResult
    data class FailedToDispatch(override val invocationId: String, val failure: CaptureFailure) : CaptureCommandResult
}

enum class CaptureRecordState { READY, METADATA_PENDING, TRASHED }

data class LastCapture(
    val uri: String,
    val durationMillis: Long,
    val createdAtMillis: Long,
    val displayName: String = "",
    val quality: CaptureQuality = CaptureQuality.UNKNOWN,
    val audioIncluded: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
    val storageDestination: StorageDestination = StorageDestination.DEVICE_GALLERY,
    val privateRef: String? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val rotationDegrees: Int? = null,
    val codec: String? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val audioKnown: Boolean = true,
    val mimeType: String = "video/mp4",
    val state: CaptureRecordState = CaptureRecordState.READY,
) {
    fun isUsable(): Boolean = id.isNotBlank() && uri.startsWith("content://") &&
        durationMillis > 0 && createdAtMillis > 0 && sizeBytes?.let { it > 0 } != false
}

data class CaptureStart(
    val invocationId: String,
    val startedAtEpochMillis: Long,
    val startedAtElapsedNanos: Long,
)

data class CaptureProgress(
    val recordedDurationNanos: Long,
    val bytesRecorded: Long,
    val storageCritical: Boolean = false,
)

interface EpochClock {
    fun nowMillis(): Long
}

interface MonotonicClock {
    fun nowNanos(): Long
}

object CaptureFileNames {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneOffset.UTC)
    fun video(atMillis: Long, uniqueSuffix: String): String {
        val safeSuffix = uniqueSuffix.filter(Char::isLetterOrDigit).take(8).ifBlank { "capture" }
        return "CHALNA_${formatter.format(Instant.ofEpochMilli(atMillis))}_$safeSuffix.mp4"
    }
}

object QualityFallback {
    fun ordered(preferred: CaptureQuality, available: Set<CaptureQuality>): List<CaptureQuality> {
        val order = when (preferred) {
            CaptureQuality.AUTO, CaptureQuality.FHD -> listOf(CaptureQuality.FHD, CaptureQuality.HD, CaptureQuality.SD)
            CaptureQuality.HD -> listOf(CaptureQuality.HD, CaptureQuality.SD, CaptureQuality.FHD)
            CaptureQuality.SD -> listOf(CaptureQuality.SD, CaptureQuality.HD, CaptureQuality.FHD)
            CaptureQuality.UNKNOWN -> listOf(CaptureQuality.FHD, CaptureQuality.HD, CaptureQuality.SD)
        }
        return order.filter(available::contains)
    }
}

data class Readiness(
    val cameraPermission: Boolean,
    val microphonePermission: Boolean,
    val audioEnabled: Boolean,
    val assistantSelected: Boolean,
) {
    val canCapture: Boolean get() = cameraPermission && (!audioEnabled || microphonePermission)
    val fullyReady: Boolean get() = canCapture && assistantSelected
    val missing: Set<Requirement> get() = buildSet {
        if (!cameraPermission) add(Requirement.CAMERA_PERMISSION)
        if (audioEnabled && !microphonePermission) add(Requirement.MICROPHONE_PERMISSION)
        if (!assistantSelected) add(Requirement.ASSISTANT_SELECTION)
    }
}

enum class Requirement { CAMERA_PERMISSION, MICROPHONE_PERMISSION, ASSISTANT_SELECTION }

object ProcessRecovery {
    fun recovered(previous: CaptureState): CaptureState = when (previous) {
        CaptureState.Idle -> CaptureState.Idle
        is CaptureState.Failed -> previous
        is CaptureState.Saved -> CaptureState.Idle
        else -> CaptureState.Failed(CaptureFailure(CaptureFailureCode.INTERRUPTED, true, "process_restart"))
    }
}

inline fun <T> captureOperation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Exception) {
    Result.failure(failure)
}
