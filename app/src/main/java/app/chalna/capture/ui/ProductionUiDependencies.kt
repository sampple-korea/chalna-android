package app.chalna.capture.ui

import android.Manifest
import android.animation.ValueAnimator
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import app.chalna.capture.BuildConfig
import app.chalna.capture.capture.CaptureRuntime
import app.chalna.capture.capture.CaptureService
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.data.LastCaptureValidator
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.MotionPreference
import app.chalna.capture.domain.ThemePreference
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProductionUiDependencies(
    private val activity: ComponentActivity,
    private val settingsStore: SettingsStore,
) : UiDependencies {
    private val mutableState = MutableStateFlow(ChalnaUiState())
    override val state: StateFlow<ChalnaUiState> = mutableState
    private val tick = MutableStateFlow(System.currentTimeMillis())
    private val diagnosticsVisible = MutableStateFlow(true)

    private val cameraPermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshSnapshot() }
    private val microphonePermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshSnapshot() }
    private val notificationPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshSnapshot() }
    private val assistantRole = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshSnapshot() }

    init {
        activity.lifecycleScope.launch {
            while (isActive) {
                tick.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
        activity.lifecycleScope.launch {
            combine(settingsStore.settings, settingsStore.lastCapture, CaptureRuntime.state, tick, diagnosticsVisible) { settings, lastCapture, capture, now, showDiagnostics ->
                val roleManager = activity.getSystemService(RoleManager::class.java)
                val assistantSelected = roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
                    roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
                val cameraGranted = granted(Manifest.permission.CAMERA)
                val microphoneGranted = granted(Manifest.permission.RECORD_AUDIO)
                val notificationsGranted = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
                val phase = when (capture) {
                    CaptureState.Idle -> CapturePhase.READY
                    is CaptureState.Starting -> CapturePhase.STARTING
                    is CaptureState.Recording -> CapturePhase.RECORDING
                    is CaptureState.Stopping, is CaptureState.Saving -> CapturePhase.STOPPING
                    is CaptureState.Saved -> CapturePhase.SAVED
                    is CaptureState.Failed -> CapturePhase.ERROR
                }
                val elapsed = (capture as? CaptureState.Recording)
                    ?.let { ((now - it.startedAtMillis).coerceAtLeast(0) / 1_000) } ?: 0
                val appearance = when (settings.theme) {
                    ThemePreference.SYSTEM -> AppearanceMode.SYSTEM
                    ThemePreference.NIGHT -> AppearanceMode.NIGHT
                    ThemePreference.MIST -> AppearanceMode.MIST
                }
                val quality = when (settings.preferredQuality) {
                    CaptureQuality.AUTO -> VideoQuality.AUTO
                    CaptureQuality.FHD -> VideoQuality.FHD
                    CaptureQuality.HD -> VideoQuality.HD
                }
                val environment = listOf(
                    "state=${capture.javaClass.simpleName}",
                    "cameraPermission=$cameraGranted",
                    "microphone=${if (settings.audioEnabled) microphoneGranted else "audioOff"}",
                    "notifications=$notificationsGranted",
                    "assistantRole=$assistantSelected",
                    "version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
                val markers = CaptureRuntime.diagnosticSnapshot().takeLast(12).map {
                    "${it.name} @ ${it.elapsedRealtimeMillis}ms"
                }
                val diagnostics = if (showDiagnostics) environment + markers else emptyList()
                ChalnaUiState(
                    setupComplete = settings.setupComplete,
                    phase = phase,
                    durationSeconds = elapsed,
                    lastSavedName = ((capture as? CaptureState.Saved)?.capture ?: lastCapture)?.displayName?.ifBlank { null },
                    errorMessage = (capture as? CaptureState.Failed)?.message,
                    quality = quality,
                    appearance = appearance,
                    haptics = settings.hapticsEnabled,
                    sound = settings.audioEnabled,
                    autoStopSeconds = settings.autoStopSeconds,
                    reducedMotion = settings.motion == MotionPreference.REDUCED || !ValueAnimator.areAnimatorsEnabled(),
                    assistantSelected = assistantSelected,
                    cameraGranted = cameraGranted,
                    microphoneGranted = microphoneGranted,
                    notificationsGranted = notificationsGranted,
                    powerSaver = activity.getSystemService(PowerManager::class.java).isPowerSaveMode,
                    diagnosticLines = diagnostics,
                )
            }.collect { mutableState.value = it }
        }
    }

    override fun toggleCapture() {
        val action = if (CaptureRuntime.state.value is CaptureState.Recording) CaptureService.ACTION_STOP else CaptureService.ACTION_TOGGLE
        CaptureService.dispatch(activity, action, "activity-${UUID.randomUUID()}")
    }

    override fun requestCamera() = cameraPermission.launch(Manifest.permission.CAMERA)

    override fun requestMicrophone() = microphonePermission.launch(Manifest.permission.RECORD_AUDIO)

    override fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else refreshSnapshot()
    }

    override fun openAssistantSettings() {
        val manager = activity.getSystemService(RoleManager::class.java)
        if (manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            assistantRole.launch(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
        } else {
            activity.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        }
    }

    override fun finishSetup() = updateSettings { copy(setupComplete = true) }

    override fun setQuality(value: VideoQuality) = updateSettings {
        copy(preferredQuality = when (value) {
            VideoQuality.AUTO -> CaptureQuality.AUTO
            VideoQuality.FHD -> CaptureQuality.FHD
            VideoQuality.HD -> CaptureQuality.HD
        })
    }

    override fun setAppearance(value: AppearanceMode) = updateSettings {
        copy(theme = when (value) {
            AppearanceMode.SYSTEM -> ThemePreference.SYSTEM
            AppearanceMode.NIGHT -> ThemePreference.NIGHT
            AppearanceMode.MIST -> ThemePreference.MIST
        })
    }

    override fun setHaptics(value: Boolean) = updateSettings { copy(hapticsEnabled = value) }
    override fun setSound(value: Boolean) = updateSettings { copy(audioEnabled = value) }
    override fun setAutoStop(seconds: Int) = updateSettings { copy(autoStopSeconds = seconds) }
    override fun setReducedMotion(value: Boolean) = updateSettings {
        copy(motion = if (value) MotionPreference.REDUCED else MotionPreference.SYSTEM)
    }

    override fun runDiagnosticCapture() = toggleCapture()
    override fun openLastCapture() {
        val capture = (CaptureRuntime.state.value as? CaptureState.Saved)?.capture ?: settingsStore.lastCapture.value ?: return
        if (!LastCaptureValidator(activity.contentResolver).exists(capture)) {
            activity.lifecycleScope.launch { settingsStore.saveLastCapture(null) }
            CaptureRuntime.publish(CaptureState.Failed("Saved video can no longer be found"))
            return
        }
        runCatching { activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(capture.uri.toUri(), "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        ) }.onFailure { CaptureRuntime.publish(CaptureState.Failed("No video viewer is available")) }
    }

    override fun copyDiagnostics() {
        val text = state.value.diagnosticLines.joinToString("\n")
        activity.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Chalna diagnostics", text))
    }
    override fun clearDiagnostics() {
        CaptureRuntime.clearDiagnostics()
        diagnosticsVisible.value = false
    }

    private fun updateSettings(transform: app.chalna.capture.domain.CaptureSettings.() -> app.chalna.capture.domain.CaptureSettings) {
        activity.lifecycleScope.launch { settingsStore.update { it.transform() } }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun refreshSnapshot() {
        tick.value = System.currentTimeMillis()
        diagnosticsVisible.value = true
    }
}
