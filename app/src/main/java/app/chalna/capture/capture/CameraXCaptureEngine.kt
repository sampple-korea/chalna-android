package app.chalna.capture.capture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
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
import app.chalna.capture.domain.LastCapture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraXCaptureEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val settingsStore: SettingsStore,
) : CaptureEngine {
    private var provider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private var finalized: CompletableDeferred<LastCapture?>? = null
    private var startedAt = 0L
    @Volatile private var discardOnFinalize = false

    override suspend fun start(invocationId: String): Long {
        CaptureRuntime.record("engine_start_request")
        check(recording == null) { "Capture already active" }
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) { "Camera permission required" }
        val settings = settingsStore.settings.value
        if (settings.audioEnabled) check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone permission required" }
        val cameraProvider = awaitProvider()
        CaptureRuntime.record("camera_provider_ready")
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
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, video)
        CaptureRuntime.record("camera_bind_complete")
        val displayName = CaptureFileNames.video(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Chalna")
        }
        val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        finalized = CompletableDeferred()
        discardOnFinalize = false
        val started = CompletableDeferred<Long>()
        startedAt = 0L
        var pending = recorder.prepareRecording(context, output)
        if (settings.audioEnabled) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    startedAt = System.currentTimeMillis()
                    CaptureRuntime.record("video_record_event_start")
                    started.complete(startedAt)
                }
                is VideoRecordEvent.Finalize -> {
                    CaptureRuntime.record("video_record_event_finalize")
                    recording = null
                    cameraProvider.unbindAll()
                    val discard = discardOnFinalize
                    discardOnFinalize = false
                    if (!event.hasError() && startedAt > 0 && !discard) {
                        finalized?.complete(
                            LastCapture(
                                event.outputResults.outputUri.toString(),
                                System.currentTimeMillis() - startedAt,
                                startedAt,
                                displayName,
                                effectiveQuality.toCaptureQuality(),
                                settings.audioEnabled,
                            ),
                        )
                    } else {
                        val uri = event.outputResults.outputUri
                        if (uri != android.net.Uri.EMPTY) runCatching { context.contentResolver.delete(uri, null, null) }
                        val failure = IllegalStateException("CameraX finalize error ${event.error}")
                        started.completeExceptionally(failure)
                        finalized?.completeExceptionally(failure)
                    }
                }
            }
        }
        return try {
            withTimeout(10_000) { started.await() }
        } catch (failure: Throwable) {
            discardOnFinalize = true
            recording?.stop()
            runCatching { withTimeout(5_000) { finalized?.await() } }
            provider?.unbindAll()
            throw failure
        }
    }

    override suspend fun stop(): LastCapture? {
        CaptureRuntime.record("stop_request")
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
