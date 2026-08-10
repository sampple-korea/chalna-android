package app.chalna.capture.capture

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.chalna.capture.ChalnaApplication
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureCommandResult
import app.chalna.capture.domain.CaptureFailure
import app.chalna.capture.domain.CaptureFailureCode
import app.chalna.capture.domain.CaptureRecordState
import app.chalna.capture.domain.CaptureRequest
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.CaptureTrigger
import app.chalna.capture.domain.EpochClock
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.gallery.GalleryRepository
import app.chalna.capture.media.AndroidGalleryMedia
import app.chalna.capture.notifications.CaptureNotifications
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureService : Service(), LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private lateinit var settings: SettingsStore
    private lateinit var gallery: GalleryRepository
    private lateinit var states: CaptureStateRepository
    private lateinit var attempts: CaptureAttemptStore
    private lateinit var engine: CameraXCaptureEngine
    private lateinit var actor: CaptureCommandActor
    private var autoStopJob: Job? = null
    private var recoveryJob: Job? = null
    private var foregroundStarted = false
    private var previousState: CaptureState = CaptureState.Idle
    private var thermalRegistered = false
    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        if (status >= PowerManager.THERMAL_STATUS_CRITICAL) requestSafetyStop("thermal")
        else if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            CaptureTelemetryRegistry.mark(activeInvocationId(), "thermal_severe")
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        val graph = (application as ChalnaApplication).graph
        settings = graph.settingsStore
        gallery = graph.galleryRepository
        states = graph.captureStates
        attempts = CaptureAttemptStore(this)
        engine = CameraXCaptureEngine(this, this, attemptStore = attempts)
        actor = CaptureCommandActor(
            scope = scope,
            states = states,
            engine = engine,
            settingsSnapshot = settings::snapshot,
            preflight = AndroidCapturePreflight(this),
            epochClock = object : EpochClock { override fun nowMillis(): Long = System.currentTimeMillis() },
            monotonicClock = AndroidMonotonicClock(),
            effects = object : CaptureActorEffects {
                override suspend fun onState(state: CaptureState) = handleState(state)

                override suspend fun persist(capture: LastCapture): Boolean {
                    return if (capture.state == CaptureRecordState.READY) {
                        gallery.recordFinalized(capture)
                        attempts.clear()
                        CaptureTelemetryRegistry.mark(activeInvocationId(), "database_persisted")
                        true
                    } else {
                        gallery.recordMetadataPending(capture)
                        false
                    }
                }
            },
        )
        recoveryJob = scope.launch(Dispatchers.IO) {
            if (!attempts.hasAttempt()) return@launch
            val recovery = CaptureRecoveryManager(
                applicationContext,
                attempts,
                gallery,
                AndroidGalleryMedia(applicationContext),
            ).recover()
            withContext(Dispatchers.Main.immediate) {
                when (recovery) {
                    is CaptureRecoveryResult.Salvaged -> states.publish(
                        CaptureState.Saved(recovery.item.toLastCapture(), SystemClock.elapsedRealtimeNanos()),
                    )
                    is CaptureRecoveryResult.Deferred -> states.publish(
                        CaptureState.Failed(CaptureFailure(CaptureFailureCode.INTERRUPTED, true, "recovery_deferred")),
                    )
                    is CaptureRecoveryResult.Failed -> states.publish(
                        CaptureState.Failed(CaptureFailure(CaptureFailureCode.INTERRUPTED, true, "recovery_failed")),
                    )
                    CaptureRecoveryResult.NothingToRecover, is CaptureRecoveryResult.RemovedCorrupt -> states.publish(CaptureState.Idle)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = intent?.toCaptureRequest()
        if (intent?.action != ACTION_COMMAND || request == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        CaptureTelemetryRegistry.mark(request.invocationId, "service_receive")
        val receipt = states.receipt(request.invocationId)
        if ((request.command == CaptureCommand.NOTIFICATION_STOP && !isCaptureActive(states.state.value)) ||
            receipt is CaptureCommandResult.NoActiveCapture ||
            receipt is CaptureCommandResult.AlreadyStopping && !isCaptureActive(states.state.value)
        ) {
            getSystemService(NotificationManager::class.java)
                .cancel(CaptureNotifications.ACTIVE_NOTIFICATION_ID)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val needsForeground = request.command == CaptureCommand.RECOVERY ||
            receipt is CaptureCommandResult.AcceptedStart || isCaptureActive(states.state.value)
        if (needsForeground && !foregroundStarted) {
            val includeMicrophone = settings.settings.value.audioEnabled && hasPermission(Manifest.permission.RECORD_AUDIO)
            try {
                beginForeground(includeMicrophone)
            } catch (failure: RuntimeException) {
                val captureFailure = CaptureFailure(
                    code = if (failure.javaClass.simpleName == "MissingForegroundServiceTypeException") {
                        CaptureFailureCode.FOREGROUND_TYPE_MISSING
                    } else if (failure.javaClass.simpleName == "ForegroundServiceStartNotAllowedException") {
                        CaptureFailureCode.FOREGROUND_START_NOT_ALLOWED
                    } else {
                        CaptureFailureCode.DISPATCH
                    },
                    recoverable = true,
                    diagnostic = failure.javaClass.simpleName,
                )
                states.publish(CaptureState.Failed(captureFailure))
                states.updateReceipt(request.invocationId, CaptureCommandResult.FailedToDispatch(request.invocationId, captureFailure))
                errorHaptic()
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        scope.launch {
            recoveryJob?.join()
            if (request.command == CaptureCommand.RECOVERY) {
                finishAfterRecovery(startId)
            } else {
                actor.submit(request)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun finishAfterRecovery(startId: Int) {
        when (val state = states.state.value) {
            is CaptureState.Saved -> handleState(state)
            is CaptureState.Failed -> handleState(state)
            else -> {
                endForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
    }

    private suspend fun handleState(state: CaptureState) {
        val old = previousState
        previousState = state
        when (state) {
            is CaptureState.StartRequested,
            is CaptureState.StartingForeground,
            is CaptureState.OpeningCamera,
            is CaptureState.StartingRecorder -> Unit
            is CaptureState.Recording -> {
                registerThermal()
                getSystemService(NotificationManager::class.java).notify(
                    CaptureNotifications.ACTIVE_NOTIFICATION_ID,
                    CaptureNotifications.active(this, state.startedAtElapsedNanos),
                )
                if (old !is CaptureState.Recording) {
                    successStartHaptic()
                    scheduleAutoStop(state)
                }
            }
            is CaptureState.CancelRequested,
            is CaptureState.StopRequested,
            is CaptureState.StoppingRecorder,
            is CaptureState.Finalizing -> {
                autoStopJob?.cancel()
                getSystemService(NotificationManager::class.java).notify(
                    CaptureNotifications.ACTIVE_NOTIFICATION_ID,
                    CaptureNotifications.saving(this),
                )
            }
            is CaptureState.Persisting -> getSystemService(NotificationManager::class.java).notify(
                CaptureNotifications.ACTIVE_NOTIFICATION_ID,
                CaptureNotifications.saving(this),
            )
            is CaptureState.Saved -> {
                cleanupRecordingObservers()
                savedHaptic()
                endForeground(STOP_FOREGROUND_REMOVE)
                if (hasNotificationPermission()) {
                    getSystemService(NotificationManager::class.java).notify(
                        CaptureNotifications.RESULT_NOTIFICATION_ID,
                        CaptureNotifications.saved(this, state.capture),
                    )
                }
                CaptureTelemetryRegistry.mark(activeInvocationId(), "saved_notification")
                stopSelf()
            }
            is CaptureState.Failed -> {
                cleanupRecordingObservers()
                errorHaptic()
                endForeground(STOP_FOREGROUND_REMOVE)
                if (hasNotificationPermission()) {
                    getSystemService(NotificationManager::class.java).notify(
                        CaptureNotifications.RESULT_NOTIFICATION_ID,
                        CaptureNotifications.error(this, state.failure),
                    )
                }
                stopSelf()
            }
            CaptureState.Idle -> if (old is CaptureState.CancelRequested) {
                cleanupRecordingObservers()
                cancelHaptic()
                endForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            is CaptureState.Recovering -> Unit
        }
    }

    private fun beginForeground(includeMicrophone: Boolean) {
        val notification = CaptureNotifications.starting(this)
        if (Build.VERSION.SDK_INT >= 30) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                if (includeMicrophone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            startForeground(CaptureNotifications.ACTIVE_NOTIFICATION_ID, notification, type)
        } else {
            startForeground(CaptureNotifications.ACTIVE_NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
        CaptureTelemetryRegistry.mark(activeInvocationId(), "service_foreground")
    }

    private fun endForeground(behavior: Int) {
        if (!foregroundStarted) return
        stopForeground(behavior)
        foregroundStarted = false
    }

    private fun scheduleAutoStop(state: CaptureState.Recording) {
        autoStopJob?.cancel()
        val seconds = settings.settings.value.autoStopSeconds
        if (seconds <= 0) return
        autoStopJob = scope.launch {
            delay(seconds * 1_000L)
            actor.submit(
                CaptureRequest(
                    invocationId = "auto-${state.invocationId}",
                    command = CaptureCommand.AUTO_STOP,
                    trigger = CaptureTrigger.AUTO_STOP,
                    receivedElapsedNanos = SystemClock.elapsedRealtimeNanos(),
                ),
            )
        }
    }

    private fun requestSafetyStop(reason: String) {
        val current = states.state.value as? CaptureState.Recording ?: return
        actor.submit(
            CaptureRequest(
                invocationId = "$reason-${current.invocationId}",
                command = CaptureCommand.AUTO_STOP,
                trigger = CaptureTrigger.SYSTEM,
                receivedElapsedNanos = SystemClock.elapsedRealtimeNanos(),
            ),
        )
    }

    private fun registerThermal() {
        if (thermalRegistered) return
        getSystemService(PowerManager::class.java).addThermalStatusListener(mainExecutor, thermalListener)
        thermalRegistered = true
    }

    private fun cleanupRecordingObservers() {
        autoStopJob?.cancel()
        autoStopJob = null
        if (thermalRegistered) {
            getSystemService(PowerManager::class.java).removeThermalStatusListener(thermalListener)
            thermalRegistered = false
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (states.state.value is CaptureState.Recording) requestSafetyStop("task")
        super.onTaskRemoved(rootIntent)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        requestSafetyStop("timeout")
        super.onTimeout(startId, fgsType)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cleanupRecordingObservers()
        scope.launch {
            withContext(NonCancellable) { actor.destroy() }
            CaptureTelemetryRegistry.mark(activeInvocationId(), "resource_released")
            serviceJob.cancel()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun successStartHaptic() = vibrate(VibrationEffect.createOneShot(48, VibrationEffect.DEFAULT_AMPLITUDE))

    private fun savedHaptic() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 64, 34), -1))

    private fun cancelHaptic() = vibrate(VibrationEffect.createOneShot(24, 96))

    private fun errorHaptic() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 52, 42, 20), -1))

    private fun vibrate(effect: VibrationEffect) {
        if (!::settings.isInitialized || !settings.settings.value.hapticsEnabled || !systemHapticsEnabled()) return
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (vibrator.hasVibrator()) vibrator.vibrate(effect)
    }

    private fun systemHapticsEnabled(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val setting = Settings.System.HAPTIC_FEEDBACK_ENABLED
        Settings.System.getInt(contentResolver, setting, 1) == 1
    }.getOrDefault(true)

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 || hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun activeInvocationId(): String = when (val state = states.state.value) {
        is CaptureState.StartRequested -> state.invocationId
        is CaptureState.StartingForeground -> state.invocationId
        is CaptureState.OpeningCamera -> state.invocationId
        is CaptureState.StartingRecorder -> state.invocationId
        is CaptureState.Recording -> state.invocationId
        is CaptureState.CancelRequested -> state.invocationId
        is CaptureState.StopRequested -> state.invocationId
        is CaptureState.StoppingRecorder -> state.invocationId
        is CaptureState.Finalizing -> state.invocationId
        else -> "capture"
    }

    private fun isCaptureActive(state: CaptureState): Boolean = state is CaptureState.StartRequested ||
        state is CaptureState.StartingForeground || state is CaptureState.OpeningCamera ||
        state is CaptureState.StartingRecorder || state is CaptureState.Recording ||
        state is CaptureState.CancelRequested || state is CaptureState.StopRequested ||
        state is CaptureState.StoppingRecorder || state is CaptureState.Finalizing ||
        state is CaptureState.Persisting

    private fun Intent.toCaptureRequest(): CaptureRequest? {
        val id = getStringExtra(EXTRA_INVOCATION_ID)?.takeIf { it.length in 1..160 } ?: return null
        val command = getStringExtra(EXTRA_COMMAND)?.let { runCatching { CaptureCommand.valueOf(it) }.getOrNull() }
            ?: return null
        val trigger = getStringExtra(EXTRA_TRIGGER)?.let { runCatching { CaptureTrigger.valueOf(it) }.getOrNull() }
            ?: return null
        val received = getLongExtra(EXTRA_RECEIVED_ELAPSED_NANOS, -1L).takeIf { it >= 0 } ?: return null
        return CaptureRequest(id, command, trigger, received)
    }

    companion object {
        const val ACTION_COMMAND = "app.chalna.capture.action.CAPTURE_COMMAND"
        const val ACTION_TOGGLE = "app.chalna.capture.action.TOGGLE"
        const val ACTION_STOP = "app.chalna.capture.action.STOP"
        const val EXTRA_INVOCATION_ID = "invocation_id"
        private const val EXTRA_COMMAND = "capture_command"
        private const val EXTRA_TRIGGER = "capture_trigger"
        private const val EXTRA_RECEIVED_ELAPSED_NANOS = "received_elapsed_nanos"

        fun intent(context: Context, request: CaptureRequest): Intent = Intent(context, CaptureService::class.java)
            .setAction(ACTION_COMMAND)
            .putExtra(EXTRA_INVOCATION_ID, request.invocationId)
            .putExtra(EXTRA_COMMAND, request.command.name)
            .putExtra(EXTRA_TRIGGER, request.trigger.name)
            .putExtra(EXTRA_RECEIVED_ELAPSED_NANOS, request.receivedElapsedNanos)

        fun intent(context: Context, action: String, id: String = UUID.randomUUID().toString()): Intent {
            val command = if (action == ACTION_STOP) CaptureCommand.NOTIFICATION_STOP else CaptureCommand.TOGGLE
            val trigger = if (action == ACTION_STOP) CaptureTrigger.NOTIFICATION else CaptureTrigger.ACTIVITY
            return intent(
                context,
                CaptureRequest(id, command, trigger, SystemClock.elapsedRealtimeNanos()),
            )
        }

        fun dispatch(context: Context, id: String): CaptureCommandResult =
            dispatch(context, ACTION_TOGGLE, id)

        fun dispatch(context: Context, action: String, id: String): CaptureCommandResult {
            val app = context.applicationContext as ChalnaApplication
            return app.graph.captureCommands.dispatch(
                id,
                if (action == ACTION_STOP) CaptureCommand.NOTIFICATION_STOP else CaptureCommand.TOGGLE,
                if (action == ACTION_STOP) CaptureTrigger.NOTIFICATION else CaptureTrigger.ACTIVITY,
            )
        }
    }
}
