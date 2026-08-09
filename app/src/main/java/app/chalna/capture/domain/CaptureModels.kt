package app.chalna.capture.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class CaptureQuality { AUTO, FHD, HD }

enum class ThemePreference { SYSTEM, NIGHT, MIST }

enum class MotionPreference { SYSTEM, FULL, REDUCED }

data class CaptureSettings(
    val audioEnabled: Boolean = true,
    val preferredQuality: CaptureQuality = CaptureQuality.AUTO,
    val hapticsEnabled: Boolean = true,
    val autoStopSeconds: Int = 0,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val motion: MotionPreference = MotionPreference.SYSTEM,
    val setupComplete: Boolean = false,
)

sealed interface CaptureState {
    data object Idle : CaptureState
    data class Starting(val invocationId: String) : CaptureState
    data class Recording(val invocationId: String, val startedAtMillis: Long) : CaptureState
    data class Stopping(val invocationId: String) : CaptureState
    data class Saving(val invocationId: String) : CaptureState
    data class Saved(val capture: LastCapture) : CaptureState
    data class Failed(val message: String) : CaptureState
}

enum class CaptureCommand { TOGGLE, STOP }

data class CaptureRequest(val invocationId: String, val command: CaptureCommand)

data class LastCapture(
    val uri: String,
    val durationMillis: Long,
    val createdAtMillis: Long,
) {
    fun isUsable(): Boolean = uri.startsWith("content://") && durationMillis >= 0 && createdAtMillis > 0
}

object CaptureFileNames {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneOffset.UTC)
    fun video(atMillis: Long): String = "CHALNA_${formatter.format(Instant.ofEpochMilli(atMillis))}.mp4"
}

object QualityFallback {
    fun ordered(preferred: CaptureQuality, available: Set<CaptureQuality>): List<CaptureQuality> {
        val order = when (preferred) {
            CaptureQuality.AUTO, CaptureQuality.FHD -> listOf(CaptureQuality.FHD, CaptureQuality.HD)
            CaptureQuality.HD -> listOf(CaptureQuality.HD, CaptureQuality.FHD)
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
        else -> CaptureState.Failed("Recording was interrupted by process restart")
    }
}
