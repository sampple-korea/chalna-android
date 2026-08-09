package app.chalna.capture.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureSettings
import app.chalna.capture.domain.MotionPreference
import app.chalna.capture.domain.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.settingsDataStore by preferencesDataStore("capture_settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings: StateFlow<CaptureSettings> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p ->
            CaptureSettings(
                audioEnabled = p[AUDIO] ?: true,
                preferredQuality = p[QUALITY]?.let { runCatching { CaptureQuality.valueOf(it) }.getOrNull() } ?: CaptureQuality.AUTO,
                hapticsEnabled = p[HAPTICS] ?: true,
                autoStopSeconds = p[AUTO_STOP] ?: 0,
                theme = p[THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() } ?: ThemePreference.SYSTEM,
                motion = p[MOTION]?.let { runCatching { MotionPreference.valueOf(it) }.getOrNull() } ?: MotionPreference.SYSTEM,
                setupComplete = p[SETUP_COMPLETE] ?: false,
            )
        }.stateIn(scope, SharingStarted.Eagerly, CaptureSettings())

    suspend fun update(value: CaptureSettings) = store.edit {
        it[AUDIO] = value.audioEnabled
        it[QUALITY] = value.preferredQuality.name
        it[HAPTICS] = value.hapticsEnabled
        it[AUTO_STOP] = value.autoStopSeconds
        it[THEME] = value.theme.name
        it[MOTION] = value.motion.name
        it[SETUP_COMPLETE] = value.setupComplete
    }

    suspend fun update(transform: (CaptureSettings) -> CaptureSettings) = update(transform(settings.value))

    private companion object {
        val AUDIO = booleanPreferencesKey("audio_enabled")
        val QUALITY = stringPreferencesKey("preferred_quality")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val AUTO_STOP = intPreferencesKey("auto_stop_seconds")
        val THEME = stringPreferencesKey("theme")
        val MOTION = stringPreferencesKey("motion")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    }
}
