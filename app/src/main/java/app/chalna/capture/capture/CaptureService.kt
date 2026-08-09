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
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureCoordinator
import app.chalna.capture.domain.CaptureRequest
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.notifications.CaptureNotifications
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
    private var autoStopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        settings = SettingsStore(this)
        engine = CameraXCaptureEngine(this, this, settings)
        coordinator = CaptureCoordinator(engine, onStateChanged = CaptureRuntime::publish)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CaptureRuntime.record("service_receive")
        val action = intent?.action
        if (action != ACTION_TOGGLE && action != ACTION_STOP) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_INVOCATION_ID) ?: UUID.randomUUID().toString()
        if (action == ACTION_TOGGLE && !hasRequiredPermissions()) {
            val message = "Required camera, audio, or notification permission is missing"
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
        if (action == ACTION_TOGGLE) {
            try {
                beginForeground()
            } catch (failure: RuntimeException) {
                val message = failure.message ?: "Foreground capture could not start"
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
            val state = coordinator.dispatch(CaptureRequest(id, if (action == ACTION_STOP) CaptureCommand.STOP else CaptureCommand.TOGGLE))
            haptic(state)
            if (state is CaptureState.Recording) scheduleAutoStop(state)
            if (state is CaptureState.Saved) {
                autoStopJob?.cancel()
                settings.saveLastCapture(state.capture)
                getSystemService(NotificationManager::class.java).notify(
                    CaptureNotifications.NOTIFICATION_ID,
                    CaptureNotifications.saved(this@CaptureService, state.capture),
                )
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelfResult(startId)
            } else if (state is CaptureState.Failed || state is CaptureState.Idle) {
                autoStopJob?.cancel()
                if (state is CaptureState.Failed && hasNotificationPermission()) {
                    getSystemService(NotificationManager::class.java).notify(
                        CaptureNotifications.NOTIFICATION_ID,
                        CaptureNotifications.error(this@CaptureService, state.message),
                    )
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun hasRequiredPermissions(): Boolean {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false
        if (!hasNotificationPermission()) return false
        return !settings.settings.value.audioEnabled || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun beginForeground() {
        val notification = CaptureNotifications.active(this)
        if (Build.VERSION.SDK_INT >= 30) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                if (settings.settings.value.audioEnabled) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            startForeground(CaptureNotifications.NOTIFICATION_ID, notification, type)
        } else startForeground(CaptureNotifications.NOTIFICATION_ID, notification)
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

    private fun scheduleAutoStop(state: CaptureState.Recording) {
        autoStopJob?.cancel()
        val seconds = settings.settings.value.autoStopSeconds
        if (seconds <= 0) return
        autoStopJob = scope.launch {
            delay(seconds * 1_000L)
            coordinator.dispatch(CaptureRequest("auto-${state.invocationId}", CaptureCommand.STOP)).also(::haptic)
            val final = coordinator.state
            if (final is CaptureState.Saved) {
                settings.saveLastCapture(final.capture)
                getSystemService(NotificationManager::class.java).notify(
                    CaptureNotifications.NOTIFICATION_ID,
                    CaptureNotifications.saved(this@CaptureService, final.capture),
                )
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            } else if (final is CaptureState.Failed || final is CaptureState.Idle) {
                if (final is CaptureState.Failed && hasNotificationPermission()) {
                    getSystemService(NotificationManager::class.java).notify(
                        CaptureNotifications.NOTIFICATION_ID,
                        CaptureNotifications.error(this@CaptureService, final.message),
                    )
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
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

    companion object {
        const val ACTION_TOGGLE = "app.chalna.capture.action.TOGGLE"
        const val ACTION_STOP = "app.chalna.capture.action.STOP"
        const val EXTRA_INVOCATION_ID = "invocation_id"
        fun intent(context: Context, action: String, id: String = UUID.randomUUID().toString()) =
            Intent(context, CaptureService::class.java).setAction(action).putExtra(EXTRA_INVOCATION_ID, id)
        fun dispatch(context: Context, id: String): Boolean = dispatch(context, ACTION_TOGGLE, id)

        fun dispatch(context: Context, action: String, id: String): Boolean = try {
            context.startForegroundService(intent(context, action, id))
            true
        } catch (failure: RuntimeException) {
            CaptureRuntime.publish(CaptureState.Failed(failure.message ?: "Foreground capture start was rejected"))
            false
        }
    }
}
