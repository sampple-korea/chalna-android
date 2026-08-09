package app.chalna.capture.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureSettings
import app.chalna.capture.domain.MotionPreference
import app.chalna.capture.domain.ThemePreference
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.domain.StorageDestinationPolicy
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
                storageDestination = StorageDestinationPolicy.fromPersisted(p[STORAGE_DESTINATION]),
            )
        }.stateIn(scope, SharingStarted.Eagerly, CaptureSettings())

    val lastCapture: StateFlow<LastCapture?> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p ->
            val uri = p[LAST_URI] ?: return@map null
            LastCapture(
                uri = uri,
                durationMillis = p[LAST_DURATION] ?: 0,
                createdAtMillis = p[LAST_CREATED] ?: 0,
                displayName = p[LAST_NAME].orEmpty(),
                quality = p[LAST_QUALITY]?.let { runCatching { CaptureQuality.valueOf(it) }.getOrNull() } ?: CaptureQuality.AUTO,
                audioIncluded = p[LAST_AUDIO] ?: false,
                id = p[LAST_ID].orEmpty(),
                storageDestination = StorageDestinationPolicy.fromPersisted(p[LAST_DESTINATION]),
                privateRef = p[LAST_PRIVATE_REF],
                sizeBytes = p[LAST_SIZE],
                width = p[LAST_WIDTH],
                height = p[LAST_HEIGHT],
            ).takeIf(LastCapture::isUsable)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun update(value: CaptureSettings) = store.edit {
        it[AUDIO] = value.audioEnabled
        it[QUALITY] = value.preferredQuality.name
        it[HAPTICS] = value.hapticsEnabled
        it[AUTO_STOP] = value.autoStopSeconds
        it[THEME] = value.theme.name
        it[MOTION] = value.motion.name
        it[SETUP_COMPLETE] = value.setupComplete
        it[STORAGE_DESTINATION] = value.storageDestination.name
    }

    suspend fun update(transform: (CaptureSettings) -> CaptureSettings) = update(transform(settings.value))

    suspend fun saveLastCapture(capture: LastCapture?) = store.edit {
        if (capture == null) {
            it.remove(LAST_URI)
            it.remove(LAST_DURATION)
            it.remove(LAST_CREATED)
            it.remove(LAST_NAME)
            it.remove(LAST_QUALITY)
            it.remove(LAST_AUDIO)
            it.remove(LAST_ID)
            it.remove(LAST_DESTINATION)
            it.remove(LAST_PRIVATE_REF)
            it.remove(LAST_SIZE)
            it.remove(LAST_WIDTH)
            it.remove(LAST_HEIGHT)
        } else {
            it[LAST_URI] = capture.uri
            it[LAST_DURATION] = capture.durationMillis
            it[LAST_CREATED] = capture.createdAtMillis
            it[LAST_NAME] = capture.displayName
            it[LAST_QUALITY] = capture.quality.name
            it[LAST_AUDIO] = capture.audioIncluded
            it[LAST_ID] = capture.id
            it[LAST_DESTINATION] = capture.storageDestination.name
            capture.privateRef?.let { value -> it[LAST_PRIVATE_REF] = value } ?: it.remove(LAST_PRIVATE_REF)
            capture.sizeBytes?.let { value -> it[LAST_SIZE] = value } ?: it.remove(LAST_SIZE)
            capture.width?.let { value -> it[LAST_WIDTH] = value } ?: it.remove(LAST_WIDTH)
            capture.height?.let { value -> it[LAST_HEIGHT] = value } ?: it.remove(LAST_HEIGHT)
        }
    }

    private companion object {
        val AUDIO = booleanPreferencesKey("audio_enabled")
        val QUALITY = stringPreferencesKey("preferred_quality")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val AUTO_STOP = intPreferencesKey("auto_stop_seconds")
        val THEME = stringPreferencesKey("theme")
        val MOTION = stringPreferencesKey("motion")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val LAST_URI = stringPreferencesKey("last_capture_uri")
        val LAST_DURATION = longPreferencesKey("last_capture_duration")
        val LAST_CREATED = longPreferencesKey("last_capture_created")
        val LAST_NAME = stringPreferencesKey("last_capture_name")
        val LAST_QUALITY = stringPreferencesKey("last_capture_quality")
        val LAST_AUDIO = booleanPreferencesKey("last_capture_audio")
        val STORAGE_DESTINATION = stringPreferencesKey("storage_destination")
        val LAST_ID = stringPreferencesKey("last_capture_id")
        val LAST_DESTINATION = stringPreferencesKey("last_capture_destination")
        val LAST_PRIVATE_REF = stringPreferencesKey("last_capture_private_ref")
        val LAST_SIZE = longPreferencesKey("last_capture_size")
        val LAST_WIDTH = intPreferencesKey("last_capture_width")
        val LAST_HEIGHT = intPreferencesKey("last_capture_height")
    }
}
