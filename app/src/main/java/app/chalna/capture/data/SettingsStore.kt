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
import app.chalna.capture.domain.CaptureRecordState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CompletableDeferred

private val Context.settingsDataStore by preferencesDataStore("capture_settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialSettingsLoaded = CompletableDeferred<Unit>()
    val settings: StateFlow<CaptureSettings> = store.data
        .catch { failure ->
            if (failure is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw failure
        }
        .map { p ->
            CaptureSettings(
                audioEnabled = p[AUDIO] ?: true,
                preferredQuality = p[QUALITY]?.let { runCatching { CaptureQuality.valueOf(it) }.getOrNull() } ?: CaptureQuality.AUTO,
                hapticsEnabled = p[HAPTICS] ?: true,
                autoStopSeconds = p[AUTO_STOP] ?: 0,
                theme = p[THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() } ?: ThemePreference.SYSTEM,
                motion = p[MOTION]?.let { runCatching { MotionPreference.valueOf(it) }.getOrNull() } ?: MotionPreference.SYSTEM,
                onboardingSeen = p[ONBOARDING_SEEN] ?: p[LEGACY_SETUP_COMPLETE] ?: false,
                storageDestination = StorageDestinationPolicy.fromPersisted(p[STORAGE_DESTINATION]),
            )
        }.onEach { initialSettingsLoaded.complete(Unit) }
        .stateIn(scope, SharingStarted.Eagerly, CaptureSettings())

    suspend fun snapshot(): CaptureSettings {
        initialSettingsLoaded.await()
        return settings.value
    }

    val lastCapture: StateFlow<LastCapture?> = store.data
        .catch { failure ->
            if (failure is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw failure
        }
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
                rotationDegrees = p[LAST_ROTATION],
                codec = p[LAST_CODEC],
                frameRate = p[LAST_FRAME_RATE]?.toFloatOrNull(),
                bitrate = p[LAST_BITRATE],
                audioKnown = p[LAST_AUDIO_KNOWN] ?: true,
                mimeType = p[LAST_MIME] ?: "video/mp4",
                state = p[LAST_STATE]?.let { runCatching { CaptureRecordState.valueOf(it) }.getOrNull() }
                    ?: CaptureRecordState.READY,
            ).takeIf(LastCapture::isUsable)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun update(value: CaptureSettings) = store.edit {
        it[AUDIO] = value.audioEnabled
        it[QUALITY] = value.preferredQuality.name
        it[HAPTICS] = value.hapticsEnabled
        it[AUTO_STOP] = value.autoStopSeconds
        it[THEME] = value.theme.name
        it[MOTION] = value.motion.name
        it[ONBOARDING_SEEN] = value.onboardingSeen
        it.remove(LEGACY_SETUP_COMPLETE)
        it[STORAGE_DESTINATION] = value.storageDestination.name
    }

    suspend fun update(transform: (CaptureSettings) -> CaptureSettings) = update(transform(snapshot()))

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
            it.remove(LAST_AUDIO_KNOWN)
            it.remove(LAST_ROTATION)
            it.remove(LAST_CODEC)
            it.remove(LAST_FRAME_RATE)
            it.remove(LAST_BITRATE)
            it.remove(LAST_MIME)
            it.remove(LAST_STATE)
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
            capture.rotationDegrees?.let { value -> it[LAST_ROTATION] = value } ?: it.remove(LAST_ROTATION)
            capture.codec?.let { value -> it[LAST_CODEC] = value } ?: it.remove(LAST_CODEC)
            capture.frameRate?.let { value -> it[LAST_FRAME_RATE] = value.toString() } ?: it.remove(LAST_FRAME_RATE)
            capture.bitrate?.let { value -> it[LAST_BITRATE] = value } ?: it.remove(LAST_BITRATE)
            it[LAST_AUDIO_KNOWN] = capture.audioKnown
            it[LAST_MIME] = capture.mimeType
            it[LAST_STATE] = capture.state.name
        }
    }

    private companion object {
        val AUDIO = booleanPreferencesKey("audio_enabled")
        val QUALITY = stringPreferencesKey("preferred_quality")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val AUTO_STOP = intPreferencesKey("auto_stop_seconds")
        val THEME = stringPreferencesKey("theme")
        val MOTION = stringPreferencesKey("motion")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val LEGACY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
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
        val LAST_AUDIO_KNOWN = booleanPreferencesKey("last_capture_audio_known")
        val LAST_ROTATION = intPreferencesKey("last_capture_rotation")
        val LAST_CODEC = stringPreferencesKey("last_capture_codec")
        val LAST_FRAME_RATE = stringPreferencesKey("last_capture_frame_rate")
        val LAST_BITRATE = intPreferencesKey("last_capture_bitrate")
        val LAST_MIME = stringPreferencesKey("last_capture_mime")
        val LAST_STATE = stringPreferencesKey("last_capture_state")
    }
}
