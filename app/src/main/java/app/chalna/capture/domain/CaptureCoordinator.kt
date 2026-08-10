package app.chalna.capture.domain

import kotlinx.coroutines.CancellationException

data class EngineStartRequest(
    val invocationId: String,
    val settings: CaptureSessionSettings,
    val createdAtEpochMillis: Long,
)

interface CaptureEngine {
    suspend fun start(
        request: EngineStartRequest,
        onStage: (CaptureState) -> Unit,
        onProgress: (CaptureProgress) -> Unit,
    ): CaptureStart

    suspend fun stop(): LastCapture
    suspend fun cancelStart(): LastCapture?
    suspend fun release()
}

fun interface CapturePreflight {
    suspend fun check(settings: CaptureSessionSettings): CaptureFailure?
}

fun Throwable.toCaptureFailure(defaultCode: CaptureFailureCode): CaptureFailure {
    if (this is CancellationException) throw this
    val diagnosticName = javaClass.simpleName.take(80)
    val code = when {
        diagnosticName.contains("CameraAccess", true) || message?.contains("busy", true) == true ->
            CaptureFailureCode.CAMERA_BUSY
        message?.contains("permission", true) == true -> CaptureFailureCode.CAMERA_PERMISSION
        message?.contains("space", true) == true || message?.contains("storage", true) == true ->
            CaptureFailureCode.LOW_STORAGE
        else -> defaultCode
    }
    return CaptureFailure(code, code !in setOf(CaptureFailureCode.OUTPUT_INVALID, CaptureFailureCode.INTERNAL), diagnosticName)
}
