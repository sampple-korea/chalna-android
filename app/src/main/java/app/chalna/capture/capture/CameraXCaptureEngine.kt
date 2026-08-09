package app.chalna.capture.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.view.WindowManager
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.domain.CaptureEngine
import app.chalna.capture.domain.CaptureFileNames
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureSessionSettings
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.media.AndroidCaptureDestinationFactory
import app.chalna.capture.media.CaptureDestinationFactory
import app.chalna.capture.media.CaptureOutputTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import java.util.UUID

class CameraXCaptureEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val settingsStore: SettingsStore,
    private val destinationFactory: CaptureDestinationFactory = AndroidCaptureDestinationFactory(context),
) : CaptureEngine {
    private var provider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private var finalized: CompletableDeferred<LastCapture?>? = null
    private var startedAt = 0L
    @Volatile private var discardOnFinalize = false

    override suspend fun start(invocationId: String): Long {
        check(recording == null) { "Capture already active" }
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) { "Camera permission required" }
        val settings = CaptureSessionSettings.snapshot(settingsStore.settings.value)
        if (settings.audioEnabled) check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone permission required" }
        val cameraProvider = awaitProvider()
        CaptureTelemetryRegistry.mark("camera_provider_ready")
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        check(cameraProvider.hasCamera(selector)) { "Rear camera unavailable" }
        val qualities = when (settings.preferredQuality) {
            CaptureQuality.AUTO, CaptureQuality.FHD -> listOf(Quality.FHD, Quality.HD, Quality.SD)
            CaptureQuality.HD -> listOf(Quality.HD, Quality.SD, Quality.FHD)
        }
        val supported = Recorder.getVideoCapabilities(cameraProvider.getCameraInfo(selector))
            .getSupportedQualities(DynamicRange.SDR)
        val effectiveQuality = qualities.firstOrNull(supported::contains) ?: supported.firstOrNull()
            ?: error("No stable video quality is available")
        val recorder = Recorder.Builder().setQualitySelector(
            QualitySelector.fromOrderedList(
                qualities.distinct(),
                androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
            ),
        ).build()
        val video = VideoCapture.withOutput(recorder)
        video.targetRotation = currentDisplayRotation()
        val displayName = CaptureFileNames.video(System.currentTimeMillis())
        val output = destinationFactory.prepare(
            destination = settings.storageDestination,
            id = UUID.randomUUID().toString(),
            displayName = displayName,
        )
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, video)
        CaptureTelemetryRegistry.mark("camera_bind_complete")
        finalized = CompletableDeferred()
        discardOnFinalize = false
        val started = CompletableDeferred<Long>()
        startedAt = 0L
        return try {
            var pending = when (val target = output.target) {
                is CaptureOutputTarget.DeviceGallery -> recorder.prepareRecording(context, target.options)
                is CaptureOutputTarget.Vault -> recorder.prepareRecording(context, target.options)
            }
            if (settings.audioEnabled) pending = pending.withAudioEnabled()
            recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        CaptureTelemetryRegistry.mark("video_record_event_start")
                        startedAt = System.currentTimeMillis()
                        started.complete(startedAt)
                    }
                    is VideoRecordEvent.Finalize -> {
                        CaptureTelemetryRegistry.mark("video_record_event_finalize")
                        recording = null
                        cameraProvider.unbindAll()
                        val discard = discardOnFinalize
                        discardOnFinalize = false
                        if (!event.hasError() && startedAt > 0 && !discard) {
                            finalized?.complete(
                                LastCapture(
                                    output.safeContentUri ?: event.outputResults.outputUri.toString(),
                                    System.currentTimeMillis() - startedAt,
                                    startedAt,
                                    output.displayName,
                                    effectiveQuality.toCaptureQuality(),
                                    settings.audioEnabled,
                                    id = output.id,
                                    storageDestination = output.destination,
                                    privateRef = output.privateRef,
                                    sizeBytes = (output.target as? CaptureOutputTarget.Vault)?.file?.length()?.takeIf { it > 0 },
                                ),
                            )
                        } else {
                            destinationFactory.discard(output, event.outputResults.outputUri.toString())
                            val finalizeFailure = IllegalStateException("CameraX finalize error ${event.error}")
                            started.completeExceptionally(finalizeFailure)
                            finalized?.completeExceptionally(finalizeFailure)
                        }
                    }
                }
            }
            withTimeout(10_000) { started.await() }
        } catch (failure: Throwable) {
            discardOnFinalize = true
            val active = recording
            runCatching { active?.stop() }
            runCatching { withTimeout(5_000) { finalized?.await() } }
            runCatching { active?.close() }
            destinationFactory.discard(output, null)
            provider?.unbindAll()
            throw failure
        }
    }

    override suspend fun stop(): LastCapture? {
        CaptureTelemetryRegistry.mark("stop_request")
        val active = recording ?: return null
        val result = finalized ?: return null
        active.stop()
        return withTimeout(15_000) { result.await() }
    }

    fun release() {
        discardOnFinalize = true
        recording?.close()
        recording = null
        provider?.unbindAll()
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int =
        context.getSystemService(WindowManager::class.java).defaultDisplay.rotation

    private fun Quality.toCaptureQuality(): CaptureQuality = when (this) {
        Quality.FHD -> CaptureQuality.FHD
        else -> CaptureQuality.HD
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = provider ?: suspendCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try { continuation.resume(future.get().also { provider = it }) }
            catch (t: Throwable) { continuation.resumeWithException(t) }
        }, ContextCompat.getMainExecutor(context))
    }
}
