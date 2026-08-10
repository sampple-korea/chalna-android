package app.chalna.capture.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.StatFs
import android.view.Display
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.chalna.capture.domain.CaptureEngine
import app.chalna.capture.domain.CaptureFailure
import app.chalna.capture.domain.CaptureFailureCode
import app.chalna.capture.domain.CaptureFileNames
import app.chalna.capture.domain.CaptureOperationException
import app.chalna.capture.domain.CapturePreflight
import app.chalna.capture.domain.CaptureProgress
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureRecordState
import app.chalna.capture.domain.CaptureSessionSettings
import app.chalna.capture.domain.CaptureStart
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.EngineStartRequest
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.domain.MonotonicClock
import app.chalna.capture.domain.StorageDestination
import app.chalna.capture.gallery.GalleryMediaGateway
import app.chalna.capture.media.AndroidCaptureDestinationFactory
import app.chalna.capture.media.AndroidGalleryMedia
import app.chalna.capture.media.CaptureDestinationFactory
import app.chalna.capture.media.CaptureOutputTarget
import app.chalna.capture.media.PreparedCaptureOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class StorageCapacityChecker(
    private val context: Context,
) {
    fun availableBytes(destination: StorageDestination): Long? =
        runCatching {
            val root =
                when (destination) {
                    StorageDestination.CHALNA_VAULT -> context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                    StorageDestination.DEVICE_GALLERY -> context.getExternalFilesDir(null)
                } ?: return null
            StatFs(root.path).availableBytes
        }.getOrNull()

    fun startAllowed(destination: StorageDestination): Boolean = availableBytes(destination)?.let { it >= START_FREE_BYTES } == true

    fun critical(destination: StorageDestination): Boolean = availableBytes(destination)?.let { it < CRITICAL_FREE_BYTES } != false

    companion object {
        const val START_FREE_BYTES = 96L * 1024L * 1024L
        const val CRITICAL_FREE_BYTES = 32L * 1024L * 1024L
    }
}

class AndroidCapturePreflight(
    private val context: Context,
    private val storage: StorageCapacityChecker = StorageCapacityChecker(context),
) : CapturePreflight {
    override suspend fun check(settings: CaptureSessionSettings): CaptureFailure? =
        withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return@withContext CaptureFailure(CaptureFailureCode.CAMERA_PERMISSION, true, "camera_permission")
            }
            if (settings.audioEnabled &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext CaptureFailure(CaptureFailureCode.MICROPHONE_PERMISSION, true, "microphone_permission")
            }
            if (!storage.startAllowed(settings.storageDestination)) {
                return@withContext CaptureFailure(CaptureFailureCode.LOW_STORAGE, true, "preflight_free_space")
            }
            null
        }
}

class CameraXCaptureEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val destinationFactory: CaptureDestinationFactory = AndroidCaptureDestinationFactory(context),
    private val attemptStore: CaptureAttemptStore = CaptureAttemptStore(context),
    private val galleryMedia: GalleryMediaGateway = AndroidGalleryMedia(context),
    private val monotonicClock: MonotonicClock = AndroidMonotonicClock(),
    private val epochClock: () -> Long = System::currentTimeMillis,
    private val storage: StorageCapacityChecker = StorageCapacityChecker(context),
) : CaptureEngine {
    private var provider: ProcessCameraProvider? = null

    @Volatile private var recording: Recording? = null

    @Volatile private var preparedOutput: PreparedCaptureOutput? = null

    @Volatile private var actualStart: CaptureStart? = null

    @Volatile private var cancelledCapture: LastCapture? = null
    private var finalized: CompletableDeferred<LastCapture>? = null
    private var firstStatusRecorded = false
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun start(
        request: EngineStartRequest,
        onStage: (CaptureState) -> Unit,
        onProgress: (CaptureProgress) -> Unit,
    ): CaptureStart {
        check(recording == null && preparedOutput == null) { "Capture already active" }
        cancelledCapture = null
        firstStatusRecorded = false
        onStage(CaptureState.OpeningCamera(request.invocationId))
        val cameraProvider = awaitProvider()
        CaptureTelemetryRegistry.mark(request.invocationId, "camera_provider_ready")
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        if (!cameraProvider.hasCamera(selector)) {
            throw CaptureOperationException(
                CaptureFailure(CaptureFailureCode.CAMERA_UNAVAILABLE, false, "rear_camera_unavailable"),
            )
        }
        val effectiveQuality = selectQuality(cameraProvider, selector, request.settings.preferredQuality)
        val recorder =
            Recorder
                .Builder()
                .setQualitySelector(QualitySelector.from(effectiveQuality))
                .build()
        val video = VideoCapture.withOutput(recorder).apply { targetRotation = currentDisplayRotation() }
        val captureId = UUID.randomUUID().toString()
        val displayName = CaptureFileNames.video(request.createdAtEpochMillis, captureId)
        val output =
            destinationFactory.prepare(
                request.settings.storageDestination,
                captureId,
                displayName,
                request.createdAtEpochMillis,
            )
        preparedOutput = output
        val attemptElapsed = monotonicClock.nowNanos()
        val startedDeferred = CompletableDeferred<CaptureStart>()
        finalized = CompletableDeferred()
        try {
            attemptStore.mark(
                invocationId = request.invocationId,
                output = output,
                settings = request.settings,
                startedAtEpochMillis = request.createdAtEpochMillis,
                startedAtElapsedNanos = attemptElapsed,
            )
            withContext(Dispatchers.Main.immediate) {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, video)
            }
            attemptStore.updateStage(CaptureAttemptStage.CAMERA_BOUND)
            CaptureTelemetryRegistry.mark(request.invocationId, "camera_bind_complete")
            onStage(CaptureState.StartingRecorder(request.invocationId))
            attemptStore.updateStage(CaptureAttemptStage.RECORDER_START_REQUESTED)
            CaptureTelemetryRegistry.mark(request.invocationId, "recorder_start_requested")
            var pending = prepare(recorder, output)
            if (request.settings.audioEnabled) {
                check(
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED,
                ) { "Microphone permission was revoked before recorder start" }
                pending = pending.withAudioEnabled()
            }
            val active =
                pending.start(ContextCompat.getMainExecutor(context)) { event ->
                    handleEvent(
                        request = request,
                        output = output,
                        effectiveQuality = effectiveQuality,
                        event = event,
                        started = startedDeferred,
                        onProgress = onProgress,
                    )
                }
            recording = active
            output.close()
            return withTimeout(START_TIMEOUT_MILLIS) { startedDeferred.await() }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { cancelledCapture = cancelInternal() }
            throw cancellation
        } catch (failure: Exception) {
            withContext(NonCancellable) { cleanupFailedStart(output) }
            throw failure
        }
    }

    override suspend fun stop(): LastCapture {
        val active = recording ?: throw IllegalStateException("No active recording")
        val completion = finalized ?: throw IllegalStateException("Finalize result is unavailable")
        attemptStore.updateStage(CaptureAttemptStage.FINALIZING)
        CaptureTelemetryRegistry.mark(activeInvocationId(), "stop_received")
        active.stop()
        return withTimeout(FINALIZE_TIMEOUT_MILLIS) { completion.await() }
    }

    override suspend fun cancelStart(): LastCapture? {
        cancelledCapture?.let {
            cancelledCapture = null
            return it
        }
        return cancelInternal().also { cancelledCapture = null }
    }

    override suspend fun release() {
        withContext(NonCancellable) {
            recording?.let { active ->
                runCatching { active.stop() }
                withTimeoutOrNull(RELEASE_FINALIZE_TIMEOUT_MILLIS) { finalized?.await() }
                runCatching { active.close() }
            }
            recording = null
            preparedOutput?.close()
            preparedOutput = null
            withContext(Dispatchers.Main.immediate) { provider?.unbindAll() }
            callbackScope.cancel()
        }
    }

    private fun handleEvent(
        request: EngineStartRequest,
        output: PreparedCaptureOutput,
        effectiveQuality: Quality,
        event: VideoRecordEvent,
        started: CompletableDeferred<CaptureStart>,
        onProgress: (CaptureProgress) -> Unit,
    ) {
        when (event) {
            is VideoRecordEvent.Start -> {
                val start = CaptureStart(request.invocationId, epochClock(), monotonicClock.nowNanos())
                actualStart = start
                CaptureTelemetryRegistry.mark(request.invocationId, "video_record_event_start")
                callbackScope.launch { attemptStore.updateStage(CaptureAttemptStage.RECORDING) }
                started.complete(start)
            }
            is VideoRecordEvent.Status -> {
                val stats = event.recordingStats
                if (!firstStatusRecorded) {
                    firstStatusRecorded = true
                    CaptureTelemetryRegistry.mark(request.invocationId, "first_status")
                }
                val durationNanos = stats.recordedDurationNanos.coerceAtLeast(0)
                val bytesRecorded = stats.numBytesRecorded.coerceAtLeast(0)
                callbackScope.launch {
                    onProgress(
                        CaptureProgress(
                            recordedDurationNanos = durationNanos,
                            bytesRecorded = bytesRecorded,
                            storageCritical = storage.critical(request.settings.storageDestination),
                        ),
                    )
                }
            }
            is VideoRecordEvent.Finalize -> finalize(request, output, effectiveQuality, event, started)
            else -> Unit
        }
    }

    private fun finalize(
        request: EngineStartRequest,
        output: PreparedCaptureOutput,
        effectiveQuality: Quality,
        event: VideoRecordEvent.Finalize,
        started: CompletableDeferred<CaptureStart>,
    ) {
        recording = null
        CaptureTelemetryRegistry.mark(request.invocationId, "finalize_received")
        callbackScope.launch {
            withContext(Dispatchers.Main.immediate) { provider?.unbindAll() }
            output.close()
            val stats = event.recordingStats
            val start = actualStart
            val candidate =
                LastCapture(
                    uri = output.contentUri,
                    durationMillis = (stats.recordedDurationNanos / 1_000_000L).coerceAtLeast(0),
                    createdAtMillis = start?.startedAtEpochMillis ?: request.createdAtEpochMillis,
                    displayName = output.displayName,
                    quality = effectiveQuality.toCaptureQuality(),
                    audioIncluded = request.settings.audioEnabled,
                    id = output.id,
                    storageDestination = output.destination,
                    privateRef = output.privateRef,
                    sizeBytes = stats.numBytesRecorded.takeIf { it > 0 },
                    audioKnown = request.settings.audioEnabled,
                    state = CaptureRecordState.METADATA_PENDING,
                )
            val validated =
                try {
                    galleryMedia.validate(
                        app.chalna.capture.domain.CaptureItem
                            .from(candidate),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
            if (validated == null) {
                destinationFactory.discard(output)
                attemptStore.clear()
                val failure =
                    CaptureOperationException(
                        CaptureFailure(CaptureFailureCode.OUTPUT_INVALID, false, "recorded_output_invalid"),
                    )
                if (!started.isCompleted) started.completeExceptionally(failure)
                finalized?.completeExceptionally(failure)
                resetAttemptState()
                return@launch
            }
            val published =
                try {
                    destinationFactory.publish(output)
                    true
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    false
                }
            val capture =
                validated
                    .copy(
                        state = if (published) CaptureRecordState.READY else CaptureRecordState.METADATA_PENDING,
                    ).toLastCapture()
            runCatching { attemptStore.updateStage(CaptureAttemptStage.MEDIA_SAVED) }
            CaptureTelemetryRegistry.mark(request.invocationId, "output_validated")
            // CameraX can report a terminal error after producing a complete, parseable MP4.
            // Validated user media wins over the transport status: preserve and index it, while
            // retaining the error as bounded local telemetry for support diagnostics.
            if (event.hasError()) {
                CaptureTelemetryRegistry.mark(request.invocationId, "finalize_error_salvaged_${event.error}")
            }
            finalized?.complete(capture)
            resetAttemptState(keepFinalized = true)
        }
    }

    private suspend fun cancelInternal(): LastCapture? {
        val active = recording
        if (active == null) {
            preparedOutput?.let { destinationFactory.discard(it) }
            attemptStore.clear()
            resetAttemptState()
            return null
        }
        runCatching { attemptStore.updateStage(CaptureAttemptStage.FINALIZING) }
        runCatching { active.stop() }
        val completed = withTimeoutOrNull(CANCEL_FINALIZE_TIMEOUT_MILLIS) { finalized?.await() }
        runCatching { active.close() }
        return completed
    }

    private suspend fun cleanupFailedStart(output: PreparedCaptureOutput) {
        val active = recording
        var preserved: LastCapture? = null
        if (active != null) {
            runCatching { active.stop() }
            preserved =
                try {
                    withTimeoutOrNull(CANCEL_FINALIZE_TIMEOUT_MILLIS) { finalized?.await() }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
            runCatching { active.close() }
        }
        if (preserved == null) {
            destinationFactory.discard(output)
            attemptStore.clear()
        }
        withContext(Dispatchers.Main.immediate) { provider?.unbindAll() }
        resetAttemptState()
    }

    private fun prepare(
        recorder: Recorder,
        output: PreparedCaptureOutput,
    ): PendingRecording =
        when (val target = output.target) {
            is CaptureOutputTarget.DeviceGallery -> recorder.prepareRecording(context, target.options)
            is CaptureOutputTarget.Vault -> recorder.prepareRecording(context, target.options)
        }

    private fun selectQuality(
        cameraProvider: ProcessCameraProvider,
        selector: CameraSelector,
        preferred: CaptureQuality,
    ): Quality {
        val supported =
            Recorder
                .getVideoCapabilities(cameraProvider.getCameraInfo(selector))
                .getSupportedQualities(DynamicRange.SDR)
        val order =
            when (preferred) {
                CaptureQuality.AUTO, CaptureQuality.FHD -> listOf(Quality.FHD, Quality.HD, Quality.SD)
                CaptureQuality.HD -> listOf(Quality.HD, Quality.SD, Quality.FHD)
                CaptureQuality.SD -> listOf(Quality.SD, Quality.HD, Quality.FHD)
                CaptureQuality.UNKNOWN -> listOf(Quality.FHD, Quality.HD, Quality.SD)
            }
        return order.firstOrNull(supported::contains) ?: supported.firstOrNull()
            ?: throw CaptureOperationException(
                CaptureFailure(CaptureFailureCode.CAMERA_UNAVAILABLE, false, "no_supported_video_quality"),
            )
    }

    private fun Quality.toCaptureQuality(): CaptureQuality =
        when (this) {
            Quality.FHD -> CaptureQuality.FHD
            Quality.HD -> CaptureQuality.HD
            Quality.SD -> CaptureQuality.SD
            else -> CaptureQuality.UNKNOWN
        }

    private fun currentDisplayRotation(): Int =
        context
            .getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation
            ?: Surface.ROTATION_0

    private suspend fun awaitProvider(): ProcessCameraProvider =
        provider ?: suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        val value = future.get().also { provider = it }
                        if (continuation.isActive) continuation.resume(value)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    private fun activeInvocationId(): String = actualStart?.invocationId ?: "capture"

    private fun resetAttemptState(keepFinalized: Boolean = false) {
        recording = null
        preparedOutput = null
        actualStart = null
        firstStatusRecorded = false
        if (!keepFinalized) finalized = null
    }

    private companion object {
        const val START_TIMEOUT_MILLIS = 12_000L
        const val FINALIZE_TIMEOUT_MILLIS = 30_000L
        const val CANCEL_FINALIZE_TIMEOUT_MILLIS = 12_000L
        const val RELEASE_FINALIZE_TIMEOUT_MILLIS = 8_000L
    }
}
