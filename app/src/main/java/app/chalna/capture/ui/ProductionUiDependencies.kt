package app.chalna.capture.ui

import android.Manifest
import android.animation.ValueAnimator
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.CancellationSignal
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.chalna.capture.ChalnaApplication
import app.chalna.capture.R
import app.chalna.capture.capture.ChalnaCaptureTileService
import app.chalna.capture.capture.CaptureService
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureFailureCode
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureRecordState
import app.chalna.capture.domain.CaptureSettings
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.CaptureTrigger
import app.chalna.capture.domain.GalleryQuery
import app.chalna.capture.domain.GalleryScope
import app.chalna.capture.domain.GallerySort
import app.chalna.capture.domain.MotionPreference
import app.chalna.capture.domain.StorageDestination
import app.chalna.capture.domain.ThemePreference
import app.chalna.capture.gallery.BatchOperationResult
import app.chalna.capture.gallery.GalleryRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ProductionUiDependencies(
    private val activity: ComponentActivity,
    private val settingsStore: SettingsStore,
) : UiDependencies {
    private val graph = (activity.application as ChalnaApplication).graph
    private val captureStates = graph.captureStates
    private val galleryRepository: GalleryRepository get() = graph.galleryRepository
    private val mutableState = MutableStateFlow(ChalnaUiState())
    override val state: StateFlow<ChalnaUiState> = mutableState
    private val systemSnapshot = MutableStateFlow(readSystemSnapshot())
    private val overlay = MutableStateFlow(UiOverlay())
    private val playerSnapshot = MutableStateFlow<PlayerSnapshot?>(null)
    private val galleryMutex = Mutex()
    private var galleryInitialized = false
    private var galleryJob: Job? = null
    private var operationDismissJob: Job? = null
    private var playerController: PlayerController? = null
    private var pendingOpenId: String? = null
    private var pendingOpenPosition = 0L
    private var cameraRequestedThisSession = false
    private var microphoneRequestedThisSession = false

    private data class SystemSnapshot(
        val assistantAvailable: Boolean,
        val assistantSelected: Boolean,
        val cameraGranted: Boolean,
        val microphoneGranted: Boolean,
        val notificationsGranted: Boolean,
        val cameraPermanentlyDenied: Boolean,
        val microphonePermanentlyDenied: Boolean,
        val powerSaver: Boolean,
    )

    private data class UiOverlay(
        val gallery: List<MediaItemUi> = emptyList(),
        val galleryLoading: Boolean = false,
        val galleryError: Boolean = false,
        val filter: GalleryFilter = GalleryFilter.ALL,
        val sort: GallerySortUi = GallerySortUi.NEWEST,
        val selected: Set<String> = emptySet(),
        val operation: UiOperationEvent? = null,
        val storageSummary: StorageSummaryUi = StorageSummaryUi(),
        val savedVisible: Boolean = false,
    )

    private val cameraPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        cameraRequestedThisSession = true
        refreshSetup()
    }

    private val microphonePermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        microphoneRequestedThisSession = true
        if (granted) updateSettings { copy(audioEnabled = true) }
        refreshSetup()
    }

    private val notificationPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshSetup() }

    private val assistantRole = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshSetup() }

    init {
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    playerController?.onBackground()
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    activity.lifecycleScope.launch { playerController?.close() }
                }
            },
        )
        activity.lifecycleScope.launch {
            captureStates.state.collectLatest { capture ->
                if (capture is CaptureState.Saved) {
                    overlay.value = overlay.value.copy(savedVisible = true)
                    delay(SAVED_DISPLAY_MILLIS)
                    overlay.value = overlay.value.copy(savedVisible = false)
                }
            }
        }
        activity.lifecycleScope.launch {
            combine(
                settingsStore.settings,
                settingsStore.lastCapture,
                captureStates.state,
                systemSnapshot,
                overlay,
                playerSnapshot,
            ) { values ->
                val settings = values[0] as CaptureSettings
                val lastCapture = values[1] as app.chalna.capture.domain.LastCapture?
                val capture = values[2] as CaptureState
                val system = values[3] as SystemSnapshot
                val ui = values[4] as UiOverlay
                val player = values[5] as PlayerSnapshot?
                val ready = system.cameraGranted && system.assistantSelected &&
                    (!settings.audioEnabled || system.microphoneGranted)
                val phase = when {
                    !ready && capture is CaptureState.Idle -> CapturePhase.SETUP_REQUIRED
                    capture is CaptureState.StartRequested || capture is CaptureState.StartingForeground ||
                        capture is CaptureState.OpeningCamera || capture is CaptureState.StartingRecorder -> CapturePhase.STARTING
                    capture is CaptureState.Recording -> CapturePhase.RECORDING
                    capture is CaptureState.CancelRequested || capture is CaptureState.StopRequested ||
                        capture is CaptureState.StoppingRecorder || capture is CaptureState.Finalizing ||
                        capture is CaptureState.Persisting || capture is CaptureState.Recovering -> CapturePhase.STOPPING
                    capture is CaptureState.Saved && ui.savedVisible -> CapturePhase.SAVED
                    capture is CaptureState.Failed -> CapturePhase.ERROR
                    else -> CapturePhase.READY
                }
                val fresh = (capture as? CaptureState.Saved)?.capture?.let(CaptureItem::from)?.toUi()
                val persisted = lastCapture?.let(CaptureItem::from)?.toUi()
                ChalnaUiState(
                    setupComplete = settings.onboardingSeen,
                    phase = phase,
                    durationSeconds = (capture as? CaptureState.Recording)
                        ?.recordedDurationNanos?.div(1_000_000_000L) ?: 0,
                    recordingStartedElapsedNanos = (capture as? CaptureState.Recording)
                        ?.startedAtElapsedNanos ?: 0,
                    lastCapture = fresh ?: persisted,
                    errorCode = (capture as? CaptureState.Failed)?.failure?.code?.name,
                    operationEvent = ui.operation,
                    quality = settings.preferredQuality.toUi(),
                    appearance = settings.theme.toUi(),
                    haptics = settings.hapticsEnabled,
                    sound = settings.audioEnabled,
                    autoStopSeconds = settings.autoStopSeconds,
                    reducedMotion = settings.motion == MotionPreference.REDUCED || !ValueAnimator.areAnimatorsEnabled(),
                    motion = settings.motion.toUi(),
                    assistantSelected = system.assistantSelected,
                    assistantAvailable = system.assistantAvailable,
                    cameraGranted = system.cameraGranted,
                    microphoneGranted = system.microphoneGranted,
                    notificationsGranted = system.notificationsGranted,
                    cameraPermanentlyDenied = system.cameraPermanentlyDenied,
                    microphonePermanentlyDenied = system.microphonePermanentlyDenied,
                    powerSaver = system.powerSaver,
                    ready = ready,
                    gallery = ui.gallery,
                    galleryLoading = ui.galleryLoading,
                    galleryError = ui.galleryError,
                    galleryFilter = ui.filter,
                    gallerySort = ui.sort,
                    storageDestination = settings.storageDestination.toUi(),
                    storageSummary = ui.storageSummary,
                    selectedMediaIds = ui.selected,
                    player = player?.toUi(),
                )
            }.collect(mutableState::emit)
        }
    }

    override fun toggleCapture() {
        graph.captureCommands.dispatch(
            "activity-${UUID.randomUUID()}",
            CaptureCommand.TOGGLE,
            CaptureTrigger.ACTIVITY,
        )
    }

    override fun requestCamera() {
        cameraRequestedThisSession = true
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun requestMicrophone() {
        microphoneRequestedThisSession = true
        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    override fun refreshSetup() {
        systemSnapshot.value = readSystemSnapshot()
    }

    override fun finishSetup() = updateSettings { copy(onboardingSeen = true) }

    override fun setQuality(value: VideoQuality) = updateSettings { copy(preferredQuality = value.toDomain()) }

    override fun setAppearance(value: AppearanceMode) = updateSettings { copy(theme = value.toDomain()) }

    override fun setHaptics(value: Boolean) = updateSettings { copy(hapticsEnabled = value) }

    override fun setSound(value: Boolean) {
        if (value && !granted(Manifest.permission.RECORD_AUDIO)) {
            if (systemSnapshot.value.microphonePermanentlyDenied) openAppSettings() else requestMicrophone()
        } else {
            updateSettings { copy(audioEnabled = value) }
        }
    }

    override fun setAutoStop(seconds: Int) = updateSettings { copy(autoStopSeconds = seconds) }

    override fun setMotion(value: MotionMode) = updateSettings { copy(motion = value.toDomain()) }

    override fun setStorageDestination(value: StorageDestinationUi) = updateSettings {
        copy(storageDestination = value.toDomain())
    }

    override fun openLastCapture() {
        state.value.lastCapture?.let { openPlayer(it.id) }
    }

    override fun reviewSetup() = updateSettings { copy(onboardingSeen = false) }

    override fun requestQuickTile() {
        ChalnaCaptureTileService.requestAdd(activity) { result ->
            val message = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                R.string.quick_tile_added
            } else {
                R.string.quick_tile_add_failed
            }
            publishOperation(UiOperationEvent.Succeeded(message))
        }
    }

    override fun refreshGallery() {
        galleryJob?.cancel()
        galleryJob = activity.lifecycleScope.launch {
            val current = overlay.value
            overlay.value = current.copy(galleryLoading = current.gallery.isEmpty(), galleryError = false)
            try {
                val items = withContext(Dispatchers.IO) {
                    galleryMutex.withLock {
                        if (!galleryInitialized) {
                            galleryRepository.initialize()
                            galleryInitialized = true
                        }
                        galleryRepository.items(current.query())
                    }
                }.map { it.toUi() }
                val available = items.mapTo(mutableSetOf(), MediaItemUi::id)
                overlay.value.gallery.filter { it.id !in available }.forEach { ThumbnailMemoryCache.remove(it.contentUri) }
                overlay.value = overlay.value.copy(
                    gallery = items,
                    galleryLoading = false,
                    galleryError = false,
                    selected = overlay.value.selected intersect available,
                )
                refreshStorageSummary()
                pendingOpenId?.let { id ->
                    items.firstOrNull { it.id == id }?.let { openPlayerItem(it, pendingOpenPosition) }
                    pendingOpenId = null
                    pendingOpenPosition = 0
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                overlay.value = overlay.value.copy(galleryLoading = false, galleryError = true)
            }
        }
    }

    override fun loadThumbnail(uri: String, sizePx: Int, cancellationSignal: CancellationSignal): Bitmap? {
        if (cancellationSignal.isCanceled || sizePx <= 0) return null
        val parsed = runCatching { uri.toUri() }.getOrNull() ?: return null
        val platform = runCatching {
            activity.contentResolver.loadThumbnail(parsed, Size(sizePx, sizePx), cancellationSignal)
        }.getOrNull()
        if (platform != null || cancellationSignal.isCanceled) return platform
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
        refreshGallery()
    }

    override fun setGallerySort(value: GallerySortUi) {
        overlay.value = overlay.value.copy(sort = value, selected = emptySet())
        refreshGallery()
    }

    override fun toggleMediaSelection(id: String) {
        if (!id.isOpaqueCaptureId()) return
        val selected = overlay.value.selected
        overlay.value = overlay.value.copy(selected = if (id in selected) selected - id else selected + id)
    }

    override fun selectAllMedia() {
        overlay.value = overlay.value.copy(selected = overlay.value.gallery.mapTo(mutableSetOf(), MediaItemUi::id))
    }

    override fun clearMediaSelection() {
        overlay.value = overlay.value.copy(selected = emptySet())
    }

    override fun trashSelectedMedia() = runBatch("trash") { galleryRepository.trash(overlay.value.selected) }

    override fun restoreSelectedMedia() = runBatch("restore") { galleryRepository.restore(overlay.value.selected) }

    override fun deleteSelectedMediaPermanently() = runBatch("delete") {
        galleryRepository.deletePermanently(overlay.value.selected)
    }

    override fun favoriteSelectedMedia(favorite: Boolean) = runBatch("favorite") {
        galleryRepository.setFavorite(overlay.value.selected, favorite)
    }

    override fun shareSelectedMedia() = share(overlay.value.selected)

    override fun exportSelectedMedia(forceCopy: Boolean) = runBatch("export") {
        galleryRepository.exportVault(overlay.value.selected, forceCopy)
    }

    override fun emptyTrash() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val ids = galleryRepository.items(GalleryQuery(scope = GalleryScope.TRASH), 500)
                .mapTo(mutableSetOf(), CaptureItem::id)
            withContext(Dispatchers.Main.immediate) {
                overlay.value = overlay.value.copy(selected = ids)
                deleteSelectedMediaPermanently()
            }
        }
    }

    override fun exportAllVault() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val ids = galleryRepository.items(
                GalleryQuery(destination = StorageDestination.CHALNA_VAULT),
                10_000,
            ).mapTo(mutableSetOf(), CaptureItem::id)
            val result = galleryRepository.exportVault(ids)
            withContext(Dispatchers.Main.immediate) { handleBatchResult("export", result) }
        }
    }

    override fun openPlayer(id: String) {
        if (!id.isOpaqueCaptureId()) return
        val item = overlay.value.gallery.firstOrNull { it.id == id } ?: state.value.lastCapture?.takeIf { it.id == id }
        if (item != null) openPlayerItem(item, 0) else openCaptureWhenReady(id)
    }

    fun openCaptureWhenReady(id: String, positionMillis: Long = 0) {
        if (!id.isOpaqueCaptureId()) return
        activity.lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) { galleryRepository.byId(id) }
            if (item == null) {
                publishOperation(UiOperationEvent.Failed(R.string.file_not_found))
            } else {
                openPlayerItem(item.toUi(), positionMillis.coerceAtLeast(0))
            }
        }
    }

    fun currentPlayerBookmark(): Pair<String, Long>? = playerController?.bookmark()

    override fun closePlayer() {
        activity.lifecycleScope.launch { playerController?.close() }
    }

    override fun bindPlayerView(view: SurfaceView?) {
        playerController?.bind(view)
    }

    override fun togglePlayback() {
        if (playerController?.togglePlayback() == false) {
            publishOperation(UiOperationEvent.Failed(R.string.player_recording_conflict))
        }
    }

    override fun seekPlayer(positionMillis: Long) {
        playerController?.seekTo(positionMillis)
    }

    override fun seekPlayerBy(deltaMillis: Long) {
        playerController?.seekBy(deltaMillis)
    }

    override fun setPlayerMuted(muted: Boolean) {
        playerController?.setMuted(muted)
    }

    override fun setPlaybackSpeed(speed: Float) {
        playerController?.setSpeed(speed)
    }

    override fun retryPlayback() {
        playerController?.retry()
    }

    override fun shareCurrentMedia() {
        state.value.player?.item?.id?.let { share(setOf(it)) }
    }

    override fun trashCurrentMedia() {
        val id = state.value.player?.item?.id ?: return
        overlay.value = overlay.value.copy(selected = setOf(id))
        closePlayer()
        trashSelectedMedia()
    }

    override fun exportCurrentMedia(forceCopy: Boolean) {
        val id = state.value.player?.item?.id ?: return
        overlay.value = overlay.value.copy(selected = setOf(id))
        exportSelectedMedia(forceCopy)
    }

    override fun openCurrentMediaExternally() {
        val id = state.value.player?.item?.id ?: return
        activity.lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { galleryRepository.externalOpenUri(id) }
            if (uri == null) {
                publishOperation(UiOperationEvent.Failed(R.string.file_not_found))
                return@launch
            }
            runCatching {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri.toUri(), VIDEO_MIME_TYPE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }.onFailure { publishOperation(UiOperationEvent.Failed(R.string.no_video_viewer)) }
        }
    }

    override fun consumeOperationEvent() {
        overlay.value = overlay.value.copy(operation = null)
    }

    private fun openPlayerItem(item: MediaItemUi, position: Long) {
        activity.lifecycleScope.launch {
            val domain = withContext(Dispatchers.IO) { galleryRepository.byId(item.id) }
            if (domain == null) {
                publishOperation(UiOperationEvent.Failed(R.string.file_not_found))
                refreshGallery()
                return@launch
            }
            val controller = playerController ?: PlayerController(
                activity,
                graph.database,
                captureStates,
                activity.lifecycleScope,
            ).also { created ->
                playerController = created
                launch { created.state.collectLatest { playerSnapshot.value = it } }
            }
            controller.open(domain, position)
        }
    }

    private fun runBatch(name: String, operation: suspend () -> BatchOperationResult) {
        val ids = overlay.value.selected
        if (ids.isEmpty()) return
        publishOperation(UiOperationEvent.Started(name, ids.size), dismiss = false)
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { operation() }
            handleBatchResult(name, result)
        }
    }

    private fun handleBatchResult(name: String, result: BatchOperationResult) {
        result.succeededIds.forEach { id ->
            overlay.value.gallery.firstOrNull { it.id == id }?.let { ThumbnailMemoryCache.remove(it.contentUri) }
        }
        overlay.value = overlay.value.copy(selected = result.failedIds)
        val event = when {
            result.failedIds.isEmpty() -> UiOperationEvent.Succeeded(operationSuccessMessage(name))
            result.succeededIds.isNotEmpty() -> UiOperationEvent.PartiallyFailed(
                R.string.operation_partially_failed,
                result.succeededIds.size,
                result.failedIds.size,
            )
            else -> UiOperationEvent.Failed(operationFailureMessage(name))
        }
        publishOperation(event)
        refreshGallery()
    }

    private fun share(ids: Set<String>) {
        if (ids.isEmpty()) return
        activity.lifecycleScope.launch {
            val uris = withContext(Dispatchers.IO) { galleryRepository.shareUris(ids) }.map { it.toUri() }
            if (uris.isEmpty()) {
                publishOperation(UiOperationEvent.Failed(R.string.gallery_share_failed))
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
            runCatching { activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.share))) }
                .onFailure { publishOperation(UiOperationEvent.Failed(R.string.gallery_share_failed)) }
        }
    }

    private fun refreshStorageSummary() {
        overlay.value = overlay.value.copy(storageSummary = overlay.value.storageSummary.copy(loading = true))
        activity.lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) { galleryRepository.storageSummary() }
            overlay.value = overlay.value.copy(
                storageSummary = StorageSummaryUi(
                    loading = false,
                    deviceGalleryCount = summary.deviceGalleryCount,
                    vaultCount = summary.vaultCount,
                    vaultBytes = summary.vaultBytes,
                    trashBytes = summary.trashBytes,
                ),
            )
        }
    }

    private fun publishOperation(event: UiOperationEvent, dismiss: Boolean = true) {
        operationDismissJob?.cancel()
        overlay.value = overlay.value.copy(operation = event)
        if (dismiss) {
            operationDismissJob = activity.lifecycleScope.launch {
                delay(OPERATION_MESSAGE_MILLIS)
                overlay.value = overlay.value.copy(operation = null)
            }
        }
    }

    private fun readSystemSnapshot(): SystemSnapshot {
        val roleManager = activity.getSystemService(RoleManager::class.java)
        val roleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
        return SystemSnapshot(
            assistantAvailable = roleAvailable,
            assistantSelected = roleAvailable && roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT),
            cameraGranted = granted(Manifest.permission.CAMERA),
            microphoneGranted = granted(Manifest.permission.RECORD_AUDIO),
            notificationsGranted = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
            cameraPermanentlyDenied = permissionPermanentlyDenied(
                Manifest.permission.CAMERA,
                cameraRequestedThisSession,
            ),
            microphonePermanentlyDenied = permissionPermanentlyDenied(
                Manifest.permission.RECORD_AUDIO,
                microphoneRequestedThisSession,
            ),
            powerSaver = activity.getSystemService(PowerManager::class.java).isPowerSaveMode,
        )
    }

    private fun permissionPermanentlyDenied(permission: String, requestedThisSession: Boolean): Boolean {
        if (granted(permission)) return false
        return requestedThisSession && !activity.shouldShowRequestPermissionRationale(permission)
    }

    private fun updateSettings(transform: CaptureSettings.() -> CaptureSettings) {
        activity.lifecycleScope.launch { settingsStore.update { it.transform() } }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun UiOverlay.query(): GalleryQuery = GalleryQuery(
        sort = when (sort) {
            GallerySortUi.NEWEST -> GallerySort.NEWEST_FIRST
            GallerySortUi.OLDEST -> GallerySort.OLDEST_FIRST
            GallerySortUi.LONGEST -> GallerySort.LONGEST_FIRST
            GallerySortUi.LARGEST -> GallerySort.LARGEST_FIRST
        },
        destination = when (filter) {
            GalleryFilter.DEVICE_GALLERY -> StorageDestination.DEVICE_GALLERY
            GalleryFilter.CHALNA_VAULT -> StorageDestination.CHALNA_VAULT
            else -> null
        },
        scope = when (filter) {
            GalleryFilter.FAVORITES -> GalleryScope.FAVORITES
            GalleryFilter.TRASH -> GalleryScope.TRASH
            else -> GalleryScope.ACTIVE
        },
    )

    private fun CaptureItem.toUi(): MediaItemUi = MediaItemUi(
        id = id,
        displayName = displayName.ifBlank { activity.getString(R.string.untitled_video) },
        contentUri = contentUri,
        mimeType = mimeType,
        destination = storageDestination.toUi(),
        capturedAtMillis = createdAtMillis,
        durationMillis = durationMillis,
        sizeBytes = sizeBytes ?: 0,
        hasAudio = audioIncluded.takeIf { audioKnown },
        width = width ?: 0,
        height = height ?: 0,
        rotationDegrees = rotationDegrees ?: 0,
        codec = codec,
        frameRate = frameRate,
        favorite = favorite,
        trashed = state == CaptureRecordState.TRASHED,
    )

    private fun PlayerSnapshot.toUi(): PlayerUiState = PlayerUiState(
        item = item.toUi(),
        phase = phase,
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        bufferedMillis = bufferedMillis,
        playing = playing,
        muted = muted,
        playbackSpeed = speed,
        recordingConflict = recordingConflict,
        keepScreenOn = playing,
    )

    private fun CaptureQuality.toUi(): VideoQuality = when (this) {
        CaptureQuality.FHD -> VideoQuality.FHD
        CaptureQuality.HD, CaptureQuality.SD -> VideoQuality.HD
        CaptureQuality.AUTO, CaptureQuality.UNKNOWN -> VideoQuality.AUTO
    }

    private fun VideoQuality.toDomain(): CaptureQuality = when (this) {
        VideoQuality.AUTO -> CaptureQuality.AUTO
        VideoQuality.FHD -> CaptureQuality.FHD
        VideoQuality.HD -> CaptureQuality.HD
    }

    private fun ThemePreference.toUi(): AppearanceMode = when (this) {
        ThemePreference.SYSTEM -> AppearanceMode.SYSTEM
        ThemePreference.NIGHT -> AppearanceMode.NIGHT
        ThemePreference.MIST -> AppearanceMode.MIST
    }

    private fun AppearanceMode.toDomain(): ThemePreference = when (this) {
        AppearanceMode.SYSTEM -> ThemePreference.SYSTEM
        AppearanceMode.NIGHT -> ThemePreference.NIGHT
        AppearanceMode.MIST -> ThemePreference.MIST
    }

    private fun MotionPreference.toUi(): MotionMode = when (this) {
        MotionPreference.SYSTEM -> MotionMode.SYSTEM
        MotionPreference.FULL -> MotionMode.FULL
        MotionPreference.REDUCED -> MotionMode.REDUCED
    }

    private fun MotionMode.toDomain(): MotionPreference = when (this) {
        MotionMode.SYSTEM -> MotionPreference.SYSTEM
        MotionMode.FULL -> MotionPreference.FULL
        MotionMode.REDUCED -> MotionPreference.REDUCED
    }

    private fun StorageDestination.toUi(): StorageDestinationUi = when (this) {
        StorageDestination.DEVICE_GALLERY -> StorageDestinationUi.DEVICE_GALLERY
        StorageDestination.CHALNA_VAULT -> StorageDestinationUi.CHALNA_VAULT
    }

    private fun StorageDestinationUi.toDomain(): StorageDestination = when (this) {
        StorageDestinationUi.DEVICE_GALLERY -> StorageDestination.DEVICE_GALLERY
        StorageDestinationUi.CHALNA_VAULT -> StorageDestination.CHALNA_VAULT
    }

    private fun String.isOpaqueCaptureId(): Boolean = matches(Regex("[0-9a-fA-F-]{32,36}"))

    private fun operationSuccessMessage(name: String): Int = when (name) {
        "trash" -> R.string.gallery_trash_succeeded
        "restore" -> R.string.gallery_restore_succeeded
        "delete" -> R.string.gallery_delete_succeeded
        "favorite" -> R.string.gallery_favorite_succeeded
        "export" -> R.string.gallery_export_succeeded
        else -> R.string.operation_succeeded
    }

    private fun operationFailureMessage(name: String): Int = when (name) {
        "trash", "delete" -> R.string.gallery_delete_failed
        "restore" -> R.string.gallery_restore_failed
        "favorite" -> R.string.gallery_favorite_failed
        "export" -> R.string.gallery_export_failed
        else -> R.string.operation_failed
    }

    private companion object {
        const val VIDEO_MIME_TYPE = "video/mp4"
        const val SAVED_DISPLAY_MILLIS = 2_100L
        const val OPERATION_MESSAGE_MILLIS = 3_500L
    }
}
