package app.chalna.capture.ui

import android.Manifest
import android.animation.ValueAnimator
import android.app.role.RoleManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.PowerManager
import android.provider.Settings
import android.util.Size
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem as PlaybackMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.chalna.capture.R
import app.chalna.capture.capture.CaptureRuntime
import app.chalna.capture.capture.CaptureService
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureSettings
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.GalleryQuery
import app.chalna.capture.domain.MotionPreference
import app.chalna.capture.domain.StorageDestination
import app.chalna.capture.domain.ThemePreference
import app.chalna.capture.gallery.GalleryRepositoryFactory
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ProductionUiDependencies(
    private val activity: ComponentActivity,
    private val settingsStore: SettingsStore,
) : UiDependencies {
    private val mutableState = MutableStateFlow(ChalnaUiState())
    override val state: StateFlow<ChalnaUiState> = mutableState
    private val tick = MutableStateFlow(System.currentTimeMillis())
    private val galleryRepository = GalleryRepositoryFactory.create(activity, settingsStore)
    private val galleryRefreshMutex = Mutex()
    private var galleryRefreshJob: Job? = null
    private val permissionHistory = activity.getSharedPreferences(PERMISSION_HISTORY, 0)
    private val player = ExoPlayer.Builder(activity).build()
    private var pendingOpenId: String? = null

    private data class UiOverlay(
        val filter: GalleryFilter = GalleryFilter.ALL,
        val selected: Set<String> = emptySet(),
        val gallery: List<MediaItemUi> = emptyList(),
        val galleryReady: Boolean = false,
        val playerId: String? = null,
        val operationMessage: String? = null,
        val savedShownAtMillis: Long? = null,
        val cameraBlocked: Boolean = false,
        val microphoneBlocked: Boolean = false,
        val notificationsBlocked: Boolean = false,
    )

    private val overlay = MutableStateFlow(UiOverlay())

    private val cameraPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPermissionState() }

    private val microphonePermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) updateSettings { copy(audioEnabled = true) }
        refreshPermissionState()
    }

    private val notificationPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPermissionState() }

    private val assistantRole = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshSetup() }

    init {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    tick.value = System.currentTimeMillis()
                }
            },
        )
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    player.pause()
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    player.release()
                }
            },
        )
        activity.lifecycleScope.launch {
            while (isActive) {
                tick.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
        activity.lifecycleScope.launch {
            withContext(Dispatchers.IO) { galleryRepository.initialize() }
            refreshGalleryInternal()
        }
        activity.lifecycleScope.launch {
            settingsStore.lastCapture.collectLatest { refreshGalleryInternal() }
        }
        activity.lifecycleScope.launch {
            CaptureRuntime.state.collectLatest { capture ->
                if (capture is CaptureState.Saved) {
                    overlay.value = overlay.value.copy(savedShownAtMillis = System.currentTimeMillis())
                    refreshGalleryInternal()
                }
            }
        }
        activity.lifecycleScope.launch {
            combine(settingsStore.settings, settingsStore.lastCapture, CaptureRuntime.state, tick, overlay) {
                    settings,
                    lastCapture,
                    capture,
                    now,
                    ui,
                ->
                val roleManager = activity.getSystemService(RoleManager::class.java)
                val assistantSelected = roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
                    roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
                val cameraGranted = granted(Manifest.permission.CAMERA)
                val microphoneGranted = granted(Manifest.permission.RECORD_AUDIO)
                val notificationsGranted = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
                val ready = cameraGranted && assistantSelected && (!settings.audioEnabled || microphoneGranted)
                val phase = when (capture) {
                    CaptureState.Idle -> CapturePhase.READY
                    is CaptureState.Starting -> CapturePhase.STARTING
                    is CaptureState.Recording -> CapturePhase.RECORDING
                    is CaptureState.Stopping, is CaptureState.Saving -> CapturePhase.STOPPING
                    is CaptureState.Saved -> if (
                        ui.savedShownAtMillis != null && now - ui.savedShownAtMillis >= SAVED_DISPLAY_MILLIS
                    ) CapturePhase.READY else CapturePhase.SAVED
                    is CaptureState.Failed -> CapturePhase.ERROR
                }
                val elapsed = (capture as? CaptureState.Recording)
                    ?.let { ((now - it.startedAtMillis).coerceAtLeast(0) / 1_000) }
                    ?: 0
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
                val freshSaved = (capture as? CaptureState.Saved)?.capture?.let(CaptureItem::from)?.toUi()
                val lastId = lastCapture?.let(CaptureItem::from)?.id
                val indexedLast = ui.gallery.firstOrNull { it.id == lastId || it.contentUri == lastCapture?.uri }
                val latest = freshSaved ?: indexedLast
                val playerItem = ui.playerId?.let { id -> ui.gallery.firstOrNull { it.id == id } }
                ChalnaUiState(
                    setupComplete = settings.setupComplete,
                    phase = phase,
                    durationSeconds = elapsed,
                    lastCapture = latest,
                    errorMessage = (capture as? CaptureState.Failed)?.message?.let(::localizedCaptureFailure),
                    operationMessage = ui.operationMessage,
                    quality = quality,
                    appearance = appearance,
                    haptics = settings.hapticsEnabled,
                    sound = settings.audioEnabled,
                    autoStopSeconds = settings.autoStopSeconds,
                    reducedMotion = settings.motion == MotionPreference.REDUCED || !ValueAnimator.areAnimatorsEnabled(),
                    motion = settings.motion.toUi(),
                    assistantSelected = assistantSelected,
                    cameraGranted = cameraGranted,
                    microphoneGranted = microphoneGranted,
                    notificationsGranted = notificationsGranted,
                    cameraPermanentlyDenied = ui.cameraBlocked,
                    microphonePermanentlyDenied = ui.microphoneBlocked,
                    notificationsPermanentlyDenied = ui.notificationsBlocked,
                    powerSaver = activity.getSystemService(PowerManager::class.java).isPowerSaveMode,
                    ready = ready,
                    gallery = ui.gallery,
                    galleryFilter = ui.filter,
                    storageDestination = settings.storageDestination.toUi(),
                    selectedMediaIds = ui.selected,
                    player = playerItem?.let(::playerState),
                )
            }.collect { mutableState.value = it }
        }
        refreshPermissionState()
    }

    override fun toggleCapture() {
        val action = if (CaptureRuntime.state.value is CaptureState.Recording) {
            CaptureService.ACTION_STOP
        } else {
            CaptureService.ACTION_TOGGLE
        }
        CaptureService.dispatch(activity, action, "activity-${UUID.randomUUID()}")
    }

    override fun requestCamera() {
        markPermissionRequested(KEY_CAMERA_REQUESTED)
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun requestMicrophone() {
        markPermissionRequested(KEY_MICROPHONE_REQUESTED)
        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            markPermissionRequested(KEY_NOTIFICATIONS_REQUESTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            refreshPermissionState()
        }
    }

    override fun openAssistantSettings() {
        val manager = activity.getSystemService(RoleManager::class.java)
        if (manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            assistantRole.launch(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
        } else {
            runCatching { activity.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
                .onFailure { openAppSettings() }
        }
    }

    override fun openAppSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData("package:${activity.packageName}".toUri()),
        )
    }

    override fun openNotificationSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
        )
    }

    override fun finishSetup() = updateSettings { copy(setupComplete = true) }

    override fun setQuality(value: VideoQuality) = updateSettings {
        copy(
            preferredQuality = when (value) {
                VideoQuality.AUTO -> CaptureQuality.AUTO
                VideoQuality.FHD -> CaptureQuality.FHD
                VideoQuality.HD -> CaptureQuality.HD
            },
        )
    }

    override fun setAppearance(value: AppearanceMode) = updateSettings {
        copy(
            theme = when (value) {
                AppearanceMode.SYSTEM -> ThemePreference.SYSTEM
                AppearanceMode.NIGHT -> ThemePreference.NIGHT
                AppearanceMode.MIST -> ThemePreference.MIST
            },
        )
    }

    override fun setHaptics(value: Boolean) = updateSettings { copy(hapticsEnabled = value) }

    override fun setSound(value: Boolean) {
        if (value && !granted(Manifest.permission.RECORD_AUDIO)) {
            requestMicrophone()
        } else {
            updateSettings { copy(audioEnabled = value) }
        }
    }

    override fun setAutoStop(seconds: Int) = updateSettings { copy(autoStopSeconds = seconds) }

    override fun setMotion(value: MotionMode) = updateSettings {
        copy(
            motion = when (value) {
                MotionMode.SYSTEM -> MotionPreference.SYSTEM
                MotionMode.FULL -> MotionPreference.FULL
                MotionMode.REDUCED -> MotionPreference.REDUCED
            },
        )
    }

    override fun setReducedMotion(value: Boolean) = setMotion(if (value) MotionMode.REDUCED else MotionMode.SYSTEM)

    override fun setStorageDestination(value: StorageDestinationUi) = updateSettings {
        copy(storageDestination = value.toDomain())
    }

    override fun openLastCapture() {
        val capture = state.value.lastCapture ?: return
        openPlayer(capture.id)
    }

    override fun reviewSetup() = updateSettings { copy(setupComplete = false) }

    override fun refreshGallery() {
        galleryRefreshJob?.cancel()
        galleryRefreshJob = activity.lifecycleScope.launch { refreshGalleryInternal() }
    }

    override fun loadThumbnail(
        uri: String,
        sizePx: Int,
        cancellationSignal: CancellationSignal,
    ): Bitmap? {
        if (cancellationSignal.isCanceled) return null
        val parsed = Uri.parse(uri)
        val platformThumbnail = runCatching {
            activity.contentResolver.loadThumbnail(parsed, Size(sizePx, sizePx), cancellationSignal)
        }.getOrNull()
        if (platformThumbnail != null || cancellationSignal.isCanceled) return platformThumbnail
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(activity, parsed)
            if (cancellationSignal.isCanceled) null else retriever.getScaledFrameAtTime(
                -1,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                sizePx,
                sizePx,
            )
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    override fun setGalleryFilter(value: GalleryFilter) {
        overlay.value = overlay.value.copy(filter = value, selected = emptySet())
    }

    override fun toggleMediaSelection(id: String) {
        val selected = overlay.value.selected
        overlay.value = overlay.value.copy(selected = if (id in selected) selected - id else selected + id)
    }

    override fun clearMediaSelection() {
        overlay.value = overlay.value.copy(selected = emptySet())
    }

    override fun deleteSelectedMedia() {
        val ids = overlay.value.selected
        if (ids.isEmpty()) return
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { galleryRepository.delete(ids) }
            overlay.value = overlay.value.copy(selected = result.failedIds, operationMessage = null)
            refreshGalleryInternal()
            if (result.failedIds.isNotEmpty()) publishOperationError(R.string.gallery_delete_failed)
        }
    }

    override fun shareSelectedMedia() {
        share(overlay.value.selected)
    }

    override fun exportSelectedMedia() {
        export(overlay.value.selected)
    }

    override fun openPlayer(id: String) {
        val item = overlay.value.gallery.firstOrNull { it.id == id }
            ?: state.value.lastCapture?.takeIf { it.id == id }
            ?: return
        preparePlayer(item)
    }

    fun openCaptureWhenReady(id: String) {
        if (id.isBlank()) return
        pendingOpenId = id
        overlay.value.gallery.firstOrNull { it.id == id }?.let {
            pendingOpenId = null
            preparePlayer(it)
        } ?: refreshGallery()
    }

    override fun closePlayer() {
        player.pause()
        player.stop()
        player.clearMediaItems()
        player.clearVideoSurface()
        overlay.value = overlay.value.copy(playerId = null, operationMessage = null)
    }

    override fun bindPlayerSurface(surface: Surface?) {
        if (surface == null) player.clearVideoSurface() else player.setVideoSurface(surface)
    }

    override fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
        tick.value = System.currentTimeMillis()
    }

    override fun seekPlayer(positionMillis: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        player.seekTo(positionMillis.coerceIn(0, duration))
        tick.value = System.currentTimeMillis()
    }

    override fun setPlayerMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
        tick.value = System.currentTimeMillis()
    }

    override fun shareCurrentMedia() {
        overlay.value.playerId?.let { share(setOf(it)) }
    }

    override fun deleteCurrentMedia() {
        val id = overlay.value.playerId ?: return
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { galleryRepository.delete(setOf(id)) }
            if (id in result.deletedIds) closePlayer() else publishOperationError(R.string.gallery_delete_failed)
            refreshGalleryInternal()
        }
    }

    override fun exportCurrentMedia() {
        overlay.value.playerId?.let { export(setOf(it)) }
    }

    override fun openCurrentMediaExternally() {
        val id = overlay.value.playerId ?: return
        activity.lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { galleryRepository.externalOpenUri(id) }
            if (uri == null) {
                publishOperationError(R.string.file_not_found)
                refreshGalleryInternal()
                return@launch
            }
            runCatching {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(uri), VIDEO_MIME_TYPE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }.onFailure { publishOperationError(R.string.no_video_viewer) }
        }
    }

    override fun refreshSetup() {
        refreshPermissionState()
        refreshGallery()
    }

    private suspend fun refreshGalleryInternal() {
        val items = withContext(Dispatchers.IO) {
            galleryRefreshMutex.withLock { galleryRepository.items(GalleryQuery()) }
        }.map { it.toUi() }
        val current = overlay.value
        val availableIds = items.mapTo(mutableSetOf(), MediaItemUi::id)
        val nextPlayer = current.playerId?.takeIf { it in availableIds }
        if (current.playerId != null && nextPlayer == null) {
            player.stop()
            player.clearMediaItems()
            player.clearVideoSurface()
        }
        overlay.value = current.copy(
            gallery = items,
            galleryReady = true,
            selected = current.selected intersect availableIds,
            playerId = nextPlayer,
        )
        pendingOpenId?.let { id ->
            items.firstOrNull { it.id == id }?.let { item ->
                pendingOpenId = null
                preparePlayer(item)
            }
        }
    }

    private fun preparePlayer(item: MediaItemUi) {
        runCatching {
            player.setMediaItem(PlaybackMediaItem.fromUri(item.contentUri))
            player.prepare()
            player.playWhenReady = false
            overlay.value = overlay.value.copy(playerId = item.id, selected = emptySet(), operationMessage = null)
        }.onFailure { publishOperationError(R.string.player_open_failed) }
    }

    private fun share(ids: Set<String>) {
        if (ids.isEmpty()) return
        activity.lifecycleScope.launch {
            val uris = withContext(Dispatchers.IO) { galleryRepository.shareUris(ids) }.map(Uri::parse)
            if (uris.isEmpty()) {
                publishOperationError(R.string.gallery_share_failed)
                return@launch
            }
            val shareIntent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }.apply {
                type = VIDEO_MIME_TYPE
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(activity.contentResolver, activity.getString(R.string.gallery), uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
            }
            runCatching {
                activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.share)))
            }.onFailure { publishOperationError(R.string.gallery_share_failed) }
        }
    }

    private fun export(ids: Set<String>) {
        if (ids.isEmpty()) return
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { galleryRepository.exportVault(ids) }
            overlay.value = overlay.value.copy(selected = result.failedIds, operationMessage = null)
            refreshGalleryInternal()
            if (result.failedIds.isNotEmpty()) publishOperationError(R.string.gallery_export_failed)
        }
    }

    private fun playerState(item: MediaItemUi): PlayerUiState {
        val actualDuration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: item.durationMillis
        return PlayerUiState(
            item = item,
            positionMillis = player.currentPosition.coerceAtLeast(0),
            durationMillis = actualDuration.coerceAtLeast(0),
            playing = player.isPlaying,
            muted = player.volume == 0f,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            message = overlay.value.operationMessage,
        )
    }

    private fun updateSettings(transform: CaptureSettings.() -> CaptureSettings) {
        activity.lifecycleScope.launch { settingsStore.update { it.transform() } }
    }

    private fun refreshPermissionState() {
        overlay.value = overlay.value.copy(
            cameraBlocked = permanentlyDenied(Manifest.permission.CAMERA, KEY_CAMERA_REQUESTED),
            microphoneBlocked = permanentlyDenied(Manifest.permission.RECORD_AUDIO, KEY_MICROPHONE_REQUESTED),
            notificationsBlocked = Build.VERSION.SDK_INT >= 33 && permanentlyDenied(
                Manifest.permission.POST_NOTIFICATIONS,
                KEY_NOTIFICATIONS_REQUESTED,
            ),
        )
        tick.value = System.currentTimeMillis()
    }

    private fun permanentlyDenied(permission: String, historyKey: String): Boolean =
        permissionHistory.getBoolean(historyKey, false) && !granted(permission) &&
            !activity.shouldShowRequestPermissionRationale(permission)

    private fun markPermissionRequested(key: String) {
        permissionHistory.edit().putBoolean(key, true).apply()
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun publishOperationError(messageResource: Int) {
        overlay.value = overlay.value.copy(operationMessage = activity.getString(messageResource))
    }

    private fun localizedCaptureFailure(message: String): String = when {
        message.contains("permission", ignoreCase = true) -> activity.getString(R.string.capture_error_permission)
        message.contains("space", ignoreCase = true) || message.contains("storage", ignoreCase = true) ->
            activity.getString(R.string.capture_error_storage)
        else -> activity.getString(R.string.capture_error_camera)
    }

    private fun CaptureItem.toUi() = MediaItemUi(
        id = id,
        displayName = displayName.ifBlank { activity.getString(R.string.untitled_video) },
        contentUri = contentUri,
        destination = storageDestination.toUi(),
        capturedAtMillis = createdAtMillis,
        durationMillis = durationMillis,
        sizeBytes = sizeBytes ?: 0,
        hasAudio = audioIncluded,
        width = width ?: 0,
        height = height ?: 0,
    )

    private fun StorageDestination.toUi() = if (this == StorageDestination.CHALNA_VAULT) {
        StorageDestinationUi.CHALNA_VAULT
    } else {
        StorageDestinationUi.DEVICE_GALLERY
    }

    private fun StorageDestinationUi.toDomain() = if (this == StorageDestinationUi.CHALNA_VAULT) {
        StorageDestination.CHALNA_VAULT
    } else {
        StorageDestination.DEVICE_GALLERY
    }

    private fun MotionPreference.toUi() = when (this) {
        MotionPreference.SYSTEM -> MotionMode.SYSTEM
        MotionPreference.FULL -> MotionMode.FULL
        MotionPreference.REDUCED -> MotionMode.REDUCED
    }

    private companion object {
        const val VIDEO_MIME_TYPE = "video/mp4"
        const val PERMISSION_HISTORY = "permission_request_history"
        const val KEY_CAMERA_REQUESTED = "camera_requested"
        const val KEY_MICROPHONE_REQUESTED = "microphone_requested"
        const val KEY_NOTIFICATIONS_REQUESTED = "notifications_requested"
        const val SAVED_DISPLAY_MILLIS = 2_400L
    }
}
