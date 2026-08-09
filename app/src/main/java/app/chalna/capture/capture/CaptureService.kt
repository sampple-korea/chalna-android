package app.chalna.capture.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.app.NotificationManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.data.CaptureIndex
import app.chalna.capture.data.FileCaptureIndexStore
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureCoordinator
import app.chalna.capture.domain.CaptureRequest
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.notifications.CaptureNotifications
import app.chalna.capture.R
import app.chalna.capture.ChalnaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class CaptureService : Service(), LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: SettingsStore
    private lateinit var coordinator: CaptureCoordinator
    private lateinit var engine: CameraXCaptureEngine
    private lateinit var captureIndex: CaptureIndex
    private lateinit var attemptStore: CaptureAttemptStore
    private var autoStopJob: Job? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        settings = (application as ChalnaApplication).settingsStore
        captureIndex = CaptureIndex(FileCaptureIndexStore(this))
        attemptStore = CaptureAttemptStore(this)
        engine = CameraXCaptureEngine(this, this, settings)
        coordinator = CaptureCoordinator(engine, onStateChanged = CaptureRuntime::publish)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CaptureTelemetryRegistry.mark("service_receive")
        val action = intent?.action
        if (action != ACTION_TOGGLE && action != ACTION_STOP) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_INVOCATION_ID) ?: UUID.randomUUID().toString()
        val startCandidate = isStartCandidate(action)
        if (startCandidate && !hasCameraPermission()) {
            val message = getString(R.string.capture_error_permission)
            CaptureRuntime.publish(CaptureState.Failed(message))
            errorHaptic()
            if (hasNotificationPermission()) {
                getSystemService(NotificationManager::class.java).notify(
                    CaptureNotifications.NOTIFICATION_ID,
                    CaptureNotifications.error(this, message),
                )
            }
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (startCandidate && !foregroundStarted) {
            try {
                beginForeground(includeMicrophone = false)
            } catch (_: RuntimeException) {
                val message = getString(R.string.capture_error_camera)
                CaptureRuntime.publish(CaptureState.Failed(message))
                errorHaptic()
                if (hasNotificationPermission()) {
                    getSystemService(NotificationManager::class.java).notify(
                        CaptureNotifications.NOTIFICATION_ID,
                        CaptureNotifications.error(this, message),
                    )
                }
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        scope.launch {
            val sessionSettings = settings.snapshot()
            val startingCapture = isStartCandidate(action)
            if (startingCapture && sessionSettings.audioEnabled && !hasMicrophonePermission()) {
                failPermission(startId)
                return@launch
            }
            if (startingCapture && sessionSettings.audioEnabled) {
                try {
                    beginForeground(includeMicrophone = true)
                } catch (_: RuntimeException) {
                    failCamera(startId)
                    return@launch
                }
            }
            if (attemptStore.hasAttempt() && CaptureRuntime.state.value !is CaptureState.Starting &&
                CaptureRuntime.state.value !is CaptureState.Recording && CaptureRuntime.state.value !is CaptureState.Saving
            ) {
                runCatching { attemptStore.recover(cancelStaleNotification = false) }
            }
            val state = coordinator.dispatch(CaptureRequest(id, if (action == ACTION_STOP) CaptureCommand.STOP else CaptureCommand.TOGGLE))
            if (state is CaptureState.Recording) {
                getSystemService(NotificationManager::class.java).notify(
                    CaptureNotifications.NOTIFICATION_ID,
                    CaptureNotifications.active(this@CaptureService),
                )
            }
            haptic(state)
            if (state is CaptureState.Recording) scheduleAutoStop(state, sessionSettings.autoStopSeconds)
            if (state is CaptureState.Saved) {
                autoStopJob?.cancel()
                persistFinalized(state.capture)
                if (hasNotificationPermission()) {
                    getSystemService(NotificationManager::class.java).notify(
                        CaptureNotifications.NOTIFICATION_ID,
                        CaptureNotifications.saved(this@CaptureService, state.capture),
                    )
                    endForeground(STOP_FOREGROUND_DETACH)
                } else endForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            } else if (state is CaptureState.Failed || state is CaptureState.Idle) {
                autoStopJob?.cancel()
                endForeground(STOP_FOREGROUND_REMOVE)
                if (state is CaptureState.Failed && hasNotificationPermission()) {
                    getSystemService(NotificationManager::class.java).notify(
                        CaptureNotifications.NOTIFICATION_ID,
                        CaptureNotifications.error(this@CaptureService, userFacingFailure(state.message)),
                    )
                }
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun isStartCandidate(action: String?): Boolean = action == ACTION_TOGGLE && when (coordinator.state) {
        CaptureState.Idle, is CaptureState.Failed, is CaptureState.Saved -> true
        is CaptureState.Starting, is CaptureState.Recording, is CaptureState.Stopping, is CaptureState.Saving -> false
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun beginForeground(includeMicrophone: Boolean) {
        val notification = CaptureNotifications.starting(this)
        if (Build.VERSION.SDK_INT >= 30) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                if (includeMicrophone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            startForeground(CaptureNotifications.NOTIFICATION_ID, notification, type)
        } else startForeground(CaptureNotifications.NOTIFICATION_ID, notification)
        foregroundStarted = true
    }

    private fun endForeground(behavior: Int) {
        if (!foregroundStarted) return
        stopForeground(behavior)
        foregroundStarted = false
    }

    private fun failPermission(startId: Int) {
        val message = getString(R.string.capture_error_permission)
        CaptureRuntime.publish(CaptureState.Failed(message))
        errorHaptic()
        endForeground(STOP_FOREGROUND_REMOVE)
        if (hasNotificationPermission()) {
            getSystemService(NotificationManager::class.java).notify(
                CaptureNotifications.NOTIFICATION_ID,
                CaptureNotifications.error(this, message),
            )
        }
        stopSelfResult(startId)
    }

    private fun failCamera(startId: Int) {
        val message = getString(R.string.capture_error_camera)
        CaptureRuntime.publish(CaptureState.Failed(message))
        errorHaptic()
        endForeground(STOP_FOREGROUND_REMOVE)
        if (hasNotificationPermission()) {
            getSystemService(NotificationManager::class.java).notify(
                CaptureNotifications.NOTIFICATION_ID,
                CaptureNotifications.error(this, message),
            )
        }
        stopSelfResult(startId)
    }

    private fun haptic(state: CaptureState) {
        if (!settings.settings.value.hapticsEnabled) return
        val vibrator = getSystemService(Vibrator::class.java)
        when (state) {
            is CaptureState.Recording -> vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            is CaptureState.Saved -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 32, 70, 32), -1))
            is CaptureState.Failed -> errorHaptic()
            else -> Unit
        }
    }

    private fun errorHaptic() {
        if (!::settings.isInitialized || !settings.settings.value.hapticsEnabled) return
        getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 55, 45, 18), -1))
    }

    private fun scheduleAutoStop(state: CaptureState.Recording, seconds: Int) {
        autoStopJob?.cancel()
        if (seconds <= 0) return
        autoStopJob = scope.launch {
            delay(seconds * 1_000L)
            coordinator.dispatch(CaptureRequest("auto-${state.invocationId}", CaptureCommand.STOP)).also(::haptic)
            val final = coordinator.state
            if (final is CaptureState.Saved) {
                persistFinalized(final.capture)
                if (hasNotificationPermission()) {
                    getSystemService(NotificationManager::class.java).notify(
                        CaptureNotifications.NOTIFICATION_ID,
                        CaptureNotifications.saved(this@CaptureService, final.capture),
                    )
                    endForeground(STOP_FOREGROUND_DETACH)
                } else endForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else if (final is CaptureState.Failed || final is CaptureState.Idle) {
                endForeground(STOP_FOREGROUND_REMOVE)
                if (final is CaptureState.Failed && hasNotificationPermission()) {
                    getSystemService(NotificationManager::class.java).notify(
                        CaptureNotifications.NOTIFICATION_ID,
                        CaptureNotifications.error(this@CaptureService, userFacingFailure(final.message)),
                    )
                }
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        autoStopJob?.cancel()
        if (::engine.isInitialized) engine.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun persistFinalized(capture: app.chalna.capture.domain.LastCapture) {
        runCatching { settings.saveLastCapture(capture) }
        runCatching { captureIndex.recordFinalized(capture) }
    }

    private fun userFacingFailure(message: String): String = when {
        message.contains("permission", ignoreCase = true) -> getString(R.string.capture_error_permission)
        message.contains("space", ignoreCase = true) || message.contains("storage", ignoreCase = true) ->
            getString(R.string.capture_error_storage)
        else -> getString(R.string.capture_error_camera)
    }

    companion object {
        const val ACTION_TOGGLE = "app.chalna.capture.action.TOGGLE"
        const val ACTION_STOP = "app.chalna.capture.action.STOP"
        const val EXTRA_INVOCATION_ID = "invocation_id"
        fun intent(context: Context, action: String, id: String = UUID.randomUUID().toString()) =
            Intent(context, CaptureService::class.java).setAction(action).putExtra(EXTRA_INVOCATION_ID, id)
        fun dispatch(context: Context, id: String): Boolean = dispatch(context, ACTION_TOGGLE, id)

        fun dispatch(context: Context, action: String, id: String): Boolean = try {
            CaptureTelemetryRegistry.mark("command_dispatch")
            context.startForegroundService(intent(context, action, id))
            true
        } catch (_: RuntimeException) {
            CaptureRuntime.publish(CaptureState.Failed(context.getString(R.string.capture_error_camera)))
            false
        }
    }
}
