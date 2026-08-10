package app.chalna.capture.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.text.format.Formatter
import android.view.SurfaceView
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import app.chalna.capture.BuildConfig
import app.chalna.capture.R
import app.chalna.capture.domain.CaptureFailureCode
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun SetupScreen(state: ChalnaUiState, dependencies: UiDependencies) = ScreenColumn {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChalnaIconCanvas(ChalnaIcon.MARK, Modifier.size(34.dp), ChalnaTheme.colors.accent)
        Spacer(Modifier.width(10.dp))
        Heading(stringResource(R.string.app_name))
    }
    Spacer(Modifier.height(30.dp))
    Heading(stringResource(R.string.setup_title))
    Spacer(Modifier.height(8.dp))
    Body(stringResource(R.string.setup_body))
    Spacer(Modifier.height(22.dp))
    SetupRow(
        title = stringResource(R.string.camera),
        detail = stringResource(R.string.camera_setup_detail),
        icon = ChalnaIcon.CAMERA,
        complete = state.cameraGranted,
        action = if (state.cameraGranted) null else if (state.cameraPermanentlyDenied) dependencies::openAppSettings else dependencies::requestCamera,
    )
    Hairline()
    SetupRow(
        title = stringResource(R.string.audio),
        detail = stringResource(if (state.sound) R.string.audio_setup_on else R.string.audio_setup_off),
        icon = ChalnaIcon.MIC,
        complete = !state.sound || state.microphoneGranted,
        action = when {
            !state.sound -> ({ dependencies.setSound(true) })
            state.microphoneGranted -> ({ dependencies.setSound(false) })
            state.microphonePermanentlyDenied -> dependencies::openAppSettings
            else -> dependencies::requestMicrophone
        },
    )
    Hairline()
    SetupRow(
        title = stringResource(R.string.assistant),
        detail = stringResource(R.string.assistant_setup_detail),
        icon = ChalnaIcon.ASSISTANT,
        complete = state.assistantSelected,
        action = if (state.assistantSelected) null else dependencies::openAssistantSettings,
    )
    Hairline()
    SetupRow(
        title = stringResource(R.string.notifications_optional),
        detail = stringResource(R.string.notifications_optional_detail),
        icon = ChalnaIcon.BELL,
        complete = state.notificationsGranted,
        optional = true,
        action = if (state.notificationsGranted) null else dependencies::requestNotifications,
    )
    Spacer(Modifier.height(26.dp))
    val essentialReady = state.cameraGranted && state.assistantSelected && (!state.sound || state.microphoneGranted)
    PrimaryButton(stringResource(R.string.finish_setup), essentialReady, dependencies::finishSetup)
}

@Composable
private fun SetupRow(
    title: String,
    detail: String,
    icon: ChalnaIcon,
    complete: Boolean,
    optional: Boolean = false,
    action: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp)
            .then(if (action == null) Modifier else Modifier.clickableNoRipple(Role.Button, onClick = action))
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChalnaIconCanvas(icon, Modifier.size(23.dp), ChalnaTheme.colors.accent)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            ChalnaText(title, 16, weight = FontWeight.SemiBold)
            ChalnaText(detail, 13, ChalnaTheme.colors.muted)
        }
        if (optional && !complete) ChalnaText(stringResource(R.string.optional), 12, ChalnaTheme.colors.muted)
        else StatusPill(complete)
    }
}

@Composable
internal fun HomeScreen(state: ChalnaUiState, dependencies: UiDependencies, navigate: (ChalnaRoute) -> Unit) = ScreenColumn {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        ChalnaIconCanvas(ChalnaIcon.MARK, Modifier.size(30.dp), ChalnaTheme.colors.accent)
        Spacer(Modifier.width(9.dp))
        ChalnaText(stringResource(R.string.app_name), Modifier.weight(1f), 23, weight = FontWeight.Bold)
        IconButton(ChalnaIcon.GALLERY, stringResource(R.string.gallery)) {
            dependencies.refreshGallery()
            navigate(ChalnaRoute.GALLERY)
        }
        IconButton(ChalnaIcon.SETTINGS, stringResource(R.string.settings)) { navigate(ChalnaRoute.SETTINGS) }
    }
    Spacer(Modifier.height(32.dp))
    InvocationGlow(
        state.phase,
        state.reducedMotion || state.powerSaver,
        Modifier.size(202.dp).align(Alignment.CenterHorizontally),
        enabled = state.phase != CapturePhase.SETUP_REQUIRED,
    )
    Spacer(Modifier.height(24.dp))
    ChalnaText(
        phaseTitle(state.phase),
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        28,
        weight = FontWeight.Bold,
        align = TextAlign.Center,
    )
    if (state.phase == CapturePhase.RECORDING || state.phase == CapturePhase.STOPPING) {
        ChalnaText(
            formatDuration(state.durationSeconds * 1_000L),
            Modifier.fillMaxWidth(),
            19,
            ChalnaTheme.colors.muted,
            FontWeight.Medium,
            TextAlign.Center,
        )
    }
    if (state.phase == CapturePhase.ERROR) {
        ChalnaText(
            captureErrorText(state.errorCode),
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
            14,
            ChalnaTheme.colors.danger,
            align = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(20.dp))
    when (state.phase) {
        CapturePhase.RECORDING -> PrimaryButton(stringResource(R.string.stop_capture), onClick = dependencies::toggleCapture)
        CapturePhase.SETUP_REQUIRED -> PrimaryButton(stringResource(R.string.review_setup), onClick = dependencies::reviewSetup)
        CapturePhase.ERROR -> PrimaryButton(stringResource(R.string.retry), onClick = dependencies::toggleCapture)
        CapturePhase.READY -> ChalnaText(
            stringResource(R.string.home_trigger_hint),
            Modifier.fillMaxWidth(),
            15,
            ChalnaTheme.colors.muted,
            align = TextAlign.Center,
        )
        else -> Unit
    }
    state.lastCapture?.let { latest ->
        Spacer(Modifier.height(30.dp))
        SectionTitle(stringResource(R.string.latest_capture))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 94.dp).clickableNoRipple(Role.Button) {
                dependencies.openPlayer(latest.id)
                navigate(ChalnaRoute.PLAYER)
            }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaThumbnail(latest, dependencies, Modifier.size(88.dp).clip(RoundedCornerShape(13.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                ChalnaText(formatMediaDate(latest.capturedAtMillis), 13, ChalnaTheme.colors.muted)
                if (latest.durationMillis > 0) ChalnaText(formatDuration(latest.durationMillis), 14, weight = FontWeight.Medium)
            }
            ChalnaIconCanvas(ChalnaIcon.PLAY, Modifier.size(23.dp), ChalnaTheme.colors.accent)
        }
    }
}

@Composable
internal fun SettingsScreen(
    state: ChalnaUiState,
    dependencies: UiDependencies,
    back: () -> Unit,
    navigate: (ChalnaRoute) -> Unit,
) = ScreenColumn {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    TopBar(stringResource(R.string.settings), back)
    SectionTitle(stringResource(R.string.capture))
    SelectionSettingRow(
        stringResource(R.string.save_to),
        destinationLabel(state.storageDestination),
        ChalnaIcon.GALLERY,
        expanded == "storage",
    ) { expanded = if (expanded == "storage") null else "storage" }
    if (expanded == "storage") SelectionList(
        StorageDestinationUi.entries.map { it to destinationLabel(it) },
        state.storageDestination,
    ) { dependencies.setStorageDestination(it); expanded = null }
    Body(stringResource(if (state.storageDestination == StorageDestinationUi.CHALNA_VAULT) R.string.vault_storage_detail else R.string.device_gallery_storage_detail))
    ChalnaText(stringResource(R.string.existing_videos_not_moved), 12, ChalnaTheme.colors.muted)
    ToggleRow(stringResource(R.string.include_audio), state.sound, dependencies::setSound)
    SelectionSettingRow(
        stringResource(R.string.video_quality),
        qualityLabel(state.quality),
        ChalnaIcon.QUALITY,
        expanded == "quality",
    ) { expanded = if (expanded == "quality") null else "quality" }
    if (expanded == "quality") SelectionList(VideoQuality.entries.map { it to qualityLabel(it) }, state.quality) {
        dependencies.setQuality(it); expanded = null
    }
    SelectionSettingRow(
        stringResource(R.string.auto_stop),
        autoStopLabel(state.autoStopSeconds),
        ChalnaIcon.TIMER,
        expanded == "auto",
    ) { expanded = if (expanded == "auto") null else "auto" }
    if (expanded == "auto") SelectionList(listOf(0, 15, 30, 60).map { it to autoStopLabel(it) }, state.autoStopSeconds) {
        dependencies.setAutoStop(it); expanded = null
    }
    StorageSummary(state, dependencies)
    Hairline()
    SectionTitle(stringResource(R.string.appearance_feedback))
    SelectionSettingRow(stringResource(R.string.theme), appearanceLabel(state.appearance), ChalnaIcon.THEME, expanded == "theme") {
        expanded = if (expanded == "theme") null else "theme"
    }
    if (expanded == "theme") SelectionList(AppearanceMode.entries.map { it to appearanceLabel(it) }, state.appearance) {
        dependencies.setAppearance(it); expanded = null
    }
    ToggleRow(stringResource(R.string.haptics), state.haptics, dependencies::setHaptics)
    SelectionSettingRow(stringResource(R.string.motion_effects), motionLabel(state.motion), ChalnaIcon.MOTION, expanded == "motion") {
        expanded = if (expanded == "motion") null else "motion"
    }
    if (expanded == "motion") SelectionList(MotionMode.entries.map { it to motionLabel(it) }, state.motion) {
        dependencies.setMotion(it); expanded = null
    }
    Hairline()
    SectionTitle(stringResource(R.string.access_setup))
    SettingRow(
        stringResource(R.string.assistant),
        stringResource(if (state.assistantSelected) R.string.status_selected else R.string.action_needed),
        ChalnaIcon.ASSISTANT,
        state.assistantSelected,
        dependencies::openAssistantSettings,
    )
    SettingRow(
        stringResource(R.string.camera),
        stringResource(if (state.cameraGranted) R.string.allowed else R.string.action_needed),
        ChalnaIcon.CAMERA,
        state.cameraGranted,
    ) { if (state.cameraPermanentlyDenied) dependencies.openAppSettings() else dependencies.requestCamera() }
    SettingRow(
        stringResource(R.string.microphone),
        stringResource(if (!state.sound) R.string.audio_off else if (state.microphoneGranted) R.string.allowed else R.string.action_needed),
        ChalnaIcon.MIC,
        !state.sound || state.microphoneGranted,
    ) { if (state.microphonePermanentlyDenied) dependencies.openAppSettings() else dependencies.requestMicrophone() }
    SettingRow(
        stringResource(R.string.notifications_optional),
        stringResource(if (state.notificationsGranted) R.string.allowed else R.string.optional),
        ChalnaIcon.BELL,
        state.notificationsGranted,
        dependencies::openNotificationSettings,
    )
    SettingRow(
        stringResource(R.string.quick_tile_label),
        stringResource(R.string.quick_tile_settings_detail),
        ChalnaIcon.TILE,
        onClick = dependencies::requestQuickTile,
    )
    Hairline()
    SectionTitle(stringResource(R.string.support))
    SettingRow(stringResource(R.string.help), stringResource(R.string.help_short), ChalnaIcon.ASSISTANT) { navigate(ChalnaRoute.HELP) }
    SettingRow(stringResource(R.string.privacy), stringResource(R.string.privacy_short), ChalnaIcon.INFO) { navigate(ChalnaRoute.PRIVACY) }
    Spacer(Modifier.height(16.dp))
    ChalnaText(
        stringResource(R.string.version_info, BuildConfig.VERSION_NAME),
        Modifier.fillMaxWidth(),
        12,
        ChalnaTheme.colors.muted,
        align = TextAlign.Center,
    )
}

@Composable
private fun SelectionSettingRow(title: String, value: String, icon: ChalnaIcon, expanded: Boolean, action: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp).clickableNoRipple(Role.Button, onClick = action).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChalnaIconCanvas(icon, Modifier.size(21.dp), ChalnaTheme.colors.accent)
        Spacer(Modifier.width(14.dp))
        ChalnaText(title, Modifier.weight(1f), 16)
        ChalnaText(value, 14, if (expanded) ChalnaTheme.colors.accent else ChalnaTheme.colors.muted)
    }
}

@Composable
private fun <T> SelectionList(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) = Column(
    Modifier.fillMaxWidth().padding(start = 42.dp, bottom = 8.dp),
) {
    options.forEach { (value, label) ->
        val active = value == selected
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickableNoRipple(Role.RadioButton) { onSelect(value) }
                .semantics { role = Role.RadioButton; this.selected = active },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(18.dp).clip(CircleShape).border(
                    1.5.dp,
                    if (active) ChalnaTheme.colors.accent else ChalnaTheme.colors.outline,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (active) Box(Modifier.size(8.dp).clip(CircleShape).background(ChalnaTheme.colors.accent))
            }
            Spacer(Modifier.width(12.dp))
            ChalnaText(label, 14, if (active) ChalnaTheme.colors.text else ChalnaTheme.colors.muted)
        }
    }
}

@Composable
private fun StorageSummary(state: ChalnaUiState, dependencies: UiDependencies) {
    val summary = state.storageSummary
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        ChalnaText(stringResource(R.string.storage_summary), 14, weight = FontWeight.SemiBold)
        if (summary.loading) Body(stringResource(R.string.loading)) else {
            ChalnaText(
                stringResource(R.string.storage_summary_counts, summary.deviceGalleryCount, summary.vaultCount),
                13,
                ChalnaTheme.colors.muted,
            )
            ChalnaText(
                stringResource(
                    R.string.storage_summary_bytes,
                    Formatter.formatFileSize(LocalContext.current, summary.vaultBytes),
                    Formatter.formatFileSize(LocalContext.current, summary.trashBytes),
                ),
                13,
                ChalnaTheme.colors.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.export_all_vault), dependencies::exportAllVault) }
                Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.empty_trash), dependencies::emptyTrash) }
            }
        }
    }
}

@Composable
internal fun GalleryScreen(state: ChalnaUiState, dependencies: UiDependencies, back: () -> Unit, open: (String) -> Unit) {
    val selected = state.selectedMediaIds
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPermanent by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = selected.isNotEmpty() || controlsVisible || confirmPermanent) {
        when {
            confirmPermanent -> confirmPermanent = false
            selected.isNotEmpty() -> dependencies.clearMediaSelection()
            else -> controlsVisible = false
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (selected.isEmpty()) {
            TopBar(stringResource(R.string.gallery), back) {
                IconButton(ChalnaIcon.FILTER, stringResource(R.string.filter), controlsVisible) { controlsVisible = !controlsVisible }
                IconButton(ChalnaIcon.SORT, stringResource(R.string.sort), controlsVisible) { controlsVisible = !controlsVisible }
            }
        } else {
            TopBar(pluralStringResource(R.plurals.items_selected, selected.size, selected.size), dependencies::clearMediaSelection) {
                IconButton(ChalnaIcon.SELECT_ALL, stringResource(R.string.select_all), onClick = dependencies::selectAllMedia)
                if (state.galleryFilter == GalleryFilter.TRASH) {
                    IconButton(ChalnaIcon.RESTORE, stringResource(R.string.restore), onClick = dependencies::restoreSelectedMedia)
                    IconButton(ChalnaIcon.DELETE, stringResource(R.string.delete_permanently)) { confirmPermanent = true }
                } else {
                    IconButton(ChalnaIcon.FAVORITE, stringResource(R.string.favorite)) { dependencies.favoriteSelectedMedia(true) }
                    IconButton(ChalnaIcon.SHARE, stringResource(R.string.share), onClick = dependencies::shareSelectedMedia)
                    if (state.gallery.any { it.id in selected && it.destination == StorageDestinationUi.CHALNA_VAULT }) {
                        IconButton(ChalnaIcon.EXPORT, stringResource(R.string.export)) { dependencies.exportSelectedMedia() }
                    }
                    IconButton(ChalnaIcon.DELETE, stringResource(R.string.move_to_trash), onClick = dependencies::trashSelectedMedia)
                }
            }
        }
        if (controlsVisible) GalleryControls(state, dependencies)
        if (confirmPermanent) ConfirmationRow(
            stringResource(R.string.delete_permanently_confirm),
            { confirmPermanent = false },
        ) {
            confirmPermanent = false
            dependencies.deleteSelectedMediaPermanently()
        }
        state.operationEvent?.let { OperationBanner(it) }
        when {
            state.galleryLoading && state.gallery.isEmpty() -> GalleryMessage(R.string.loading, null)
            state.galleryError && state.gallery.isEmpty() -> GalleryMessage(R.string.gallery_error, R.string.retry) { dependencies.refreshGallery() }
            state.gallery.isEmpty() -> GalleryMessage(R.string.gallery_empty_title, R.string.gallery_empty_body)
            else -> GalleryGrid(state.gallery, selected, dependencies, open)
        }
    }
}

@Composable
private fun GalleryControls(state: ChalnaUiState, dependencies: UiDependencies) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        ChalnaText(stringResource(R.string.filter), 12, ChalnaTheme.colors.muted, FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GalleryFilter.entries.forEach { filter -> ChoiceChip(filterLabel(filter), state.galleryFilter == filter) { dependencies.setGalleryFilter(filter) } }
        }
        ChalnaText(stringResource(R.string.sort), Modifier.padding(top = 10.dp), 12, ChalnaTheme.colors.muted, FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GallerySortUi.entries.forEach { sort -> ChoiceChip(sortLabel(sort), state.gallerySort == sort) { dependencies.setGallerySort(sort) } }
        }
    }
}

@Composable
private fun ConfirmationRow(message: String, cancel: () -> Unit, confirm: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        ChalnaText(message, 14, weight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.cancel), cancel) }
            Box(Modifier.weight(1f)) { PrimaryButton(stringResource(R.string.delete), onClick = confirm) }
        }
    }
}

@Composable
private fun OperationBanner(event: UiOperationEvent) {
    val text = when (event) {
        is UiOperationEvent.Started -> stringResource(R.string.operation_started, event.total)
        is UiOperationEvent.Progress -> stringResource(R.string.operation_progress, event.completed, event.total)
        is UiOperationEvent.Succeeded -> stringResource(event.messageResource)
        is UiOperationEvent.PartiallyFailed -> stringResource(event.messageResource, event.succeeded, event.failed)
        is UiOperationEvent.Failed -> stringResource(event.messageResource)
    }
    ChalnaText(
        text,
        Modifier.fillMaxWidth().padding(vertical = 7.dp).semantics { liveRegion = LiveRegionMode.Polite },
        13,
        if (event is UiOperationEvent.Failed || event is UiOperationEvent.PartiallyFailed) ChalnaTheme.colors.warning else ChalnaTheme.colors.positive,
    )
}

@Composable
private fun GalleryMessage(title: Int, body: Int?, action: (() -> Unit)? = null) = Box(
    Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ChalnaText(stringResource(title), 18, weight = FontWeight.SemiBold, align = TextAlign.Center)
        body?.let { Spacer(Modifier.height(8.dp)); ChalnaText(stringResource(it), 14, ChalnaTheme.colors.muted, align = TextAlign.Center) }
        action?.let { Spacer(Modifier.height(12.dp)); SecondaryButton(stringResource(R.string.retry), it) }
    }
}

@Composable
private fun GalleryGrid(
    items: List<MediaItemUi>,
    selected: Set<String>,
    dependencies: UiDependencies,
    open: (String) -> Unit,
) {
    val grouped = items.groupBy { dayKey(it.capturedAtMillis) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        grouped.forEach { (day, media) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "day-$day") {
                ChalnaText(dayLabel(day), Modifier.padding(top = 14.dp, bottom = 4.dp), 14, ChalnaTheme.colors.muted, FontWeight.SemiBold)
            }
            items(media, key = MediaItemUi::id) { item ->
                MediaGridItem(
                    item,
                    dependencies,
                    item.id in selected,
                    selected.isNotEmpty(),
                    { dependencies.toggleMediaSelection(item.id) },
                    { open(item.id) },
                )
            }
        }
    }
}

@Composable
private fun MediaGridItem(
    item: MediaItemUi,
    dependencies: UiDependencies,
    selected: Boolean,
    selectionMode: Boolean,
    select: () -> Unit,
    open: () -> Unit,
) {
    val description = galleryItemDescription(item)
    Column(
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onLongClick = select,
            onClick = { if (selectionMode) select() else open() },
        ).semantics { this.selected = selected; contentDescription = description },
    ) {
        Box {
            MediaThumbnail(item, dependencies, Modifier.fillMaxWidth().aspectRatio(item.thumbnailRatio()))
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(ChalnaTheme.colors.accent),
                contentAlignment = Alignment.Center,
            ) { ChalnaIconCanvas(ChalnaIcon.CHECK, Modifier.size(16.dp), Color(0xFF071018)) }
            if (item.favorite) ChalnaIconCanvas(
                ChalnaIcon.FAVORITE,
                Modifier.align(Alignment.TopStart).padding(8.dp).size(17.dp),
                Color.White,
            )
            Row(
                Modifier.align(Alignment.BottomEnd).padding(7.dp).clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(.68f)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.destination == StorageDestinationUi.CHALNA_VAULT) {
                    ChalnaIconCanvas(ChalnaIcon.MARK, Modifier.size(11.dp), Color.White)
                    Spacer(Modifier.width(3.dp))
                }
                if (item.durationMillis > 0) ChalnaText(formatDuration(item.durationMillis), 11, Color.White, FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(7.dp))
    }
}

@Composable
internal fun PlayerScreen(player: PlayerUiState, dependencies: UiDependencies, back: () -> Unit) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var controls by rememberSaveable { mutableStateOf(true) }
    var more by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity
    val touchExploration = context.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
    BackHandler(enabled = fullscreen || more || details || confirmDelete) {
        when {
            confirmDelete -> confirmDelete = false
            details -> details = false
            more -> more = false
            else -> fullscreen = false
        }
    }
    DisposableEffect(fullscreen, activity) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) } }
    }
    LaunchedEffect(controls, player.playing, more, details, confirmDelete) {
        if (controls && player.playing && !more && !details && !confirmDelete && !touchExploration) {
            delay(2_700L)
            controls = false
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ratio = player.item.videoRatio()
            val container = maxWidth / maxHeight
            val surfaceModifier = if (container > ratio) {
                Modifier.fillMaxHeight().aspectRatio(ratio).align(Alignment.Center)
            } else {
                Modifier.fillMaxWidth().aspectRatio(ratio).align(Alignment.Center)
            }
            PlayerSurface(dependencies, player.keepScreenOn, surfaceModifier)
            Box(
                Modifier.fillMaxSize().testTag("player_touch_surface").pointerInput(Unit) {
                    detectTapGestures { controls = !controls }
                },
            )
            PlayerStatusOverlay(player)
        }
        if (controls) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(ChalnaIcon.BACK, stringResource(R.string.back), onClick = back)
                    Spacer(Modifier.weight(1f))
                    IconButton(ChalnaIcon.SHARE, stringResource(R.string.share), onClick = dependencies::shareCurrentMedia)
                    IconButton(ChalnaIcon.MORE, stringResource(R.string.more), more) { more = !more }
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().background(Color.Black.copy(.78f)).padding(12.dp)) {
                    PlayerScrubber(player, dependencies::seekPlayer)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(ChalnaIcon.REWIND, stringResource(R.string.rewind_10)) { dependencies.seekPlayerBy(-10_000L) }
                        IconButton(
                            if (player.playing) ChalnaIcon.PAUSE else ChalnaIcon.PLAY,
                            stringResource(if (player.playing) R.string.pause else R.string.play),
                            onClick = dependencies::togglePlayback,
                        )
                        IconButton(ChalnaIcon.FORWARD, stringResource(R.string.forward_10)) { dependencies.seekPlayerBy(10_000L) }
                        ChalnaText(
                            "${formatDuration(player.positionMillis)} / ${formatDuration(player.durationMillis)}",
                            Modifier.weight(1f),
                            13,
                            Color.White.copy(.82f),
                        )
                        IconButton(
                            if (player.muted) ChalnaIcon.MUTED else ChalnaIcon.VOLUME,
                            stringResource(if (player.muted) R.string.unmute else R.string.mute),
                        ) { dependencies.setPlayerMuted(!player.muted) }
                        IconButton(ChalnaIcon.FULLSCREEN, stringResource(R.string.fullscreen), fullscreen) { fullscreen = !fullscreen }
                    }
                    if (more) PlayerMoreActions(player, dependencies, { details = true }) { confirmDelete = true }
                }
            }
        }
        if (details) PlayerDetailsPanel(player.item) { details = false }
        if (confirmDelete) PlayerDeleteDialog(
            cancel = { confirmDelete = false },
            confirm = { confirmDelete = false; dependencies.trashCurrentMedia() },
        )
    }
}

@Composable
private fun PlayerStatusOverlay(player: PlayerUiState) {
    val message = when {
        player.recordingConflict -> R.string.player_recording_conflict
        player.phase == PlayerPhase.PREPARING -> R.string.player_preparing
        player.phase == PlayerPhase.BUFFERING -> R.string.player_buffering
        player.phase == PlayerPhase.ERROR -> R.string.player_error
        player.phase == PlayerPhase.SOURCE_MISSING -> R.string.file_not_found
        else -> null
    }
    message?.let {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ChalnaText(stringResource(it), 14, Color.White.copy(.84f), align = TextAlign.Center)
        }
    }
}

@Composable
private fun PlayerMoreActions(
    player: PlayerUiState,
    dependencies: UiDependencies,
    details: () -> Unit,
    delete: () -> Unit,
) {
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (player.item.destination == StorageDestinationUi.CHALNA_VAULT) {
                PlayerAction(R.string.export, ChalnaIcon.EXPORT) { dependencies.exportCurrentMedia() }
            }
            PlayerAction(R.string.open_external, ChalnaIcon.EXTERNAL, dependencies::openCurrentMediaExternally)
            PlayerAction(R.string.details, ChalnaIcon.INFO, details)
            PlayerAction(R.string.move_to_trash, ChalnaIcon.DELETE, delete)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                ChoiceChip(stringResource(R.string.playback_speed_value, speed), player.playbackSpeed == speed) {
                    dependencies.setPlaybackSpeed(speed)
                }
            }
        }
    }
}

@Composable
private fun PlayerAction(label: Int, icon: ChalnaIcon, action: () -> Unit) = Column(
    Modifier.width(88.dp).heightIn(min = 64.dp).clickableNoRipple(Role.Button, onClick = action).padding(6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    ChalnaIconCanvas(icon, Modifier.size(22.dp), Color.White)
    Spacer(Modifier.height(5.dp))
    ChalnaText(stringResource(label), 11, Color.White.copy(.84f), align = TextAlign.Center)
}

@Composable
private fun PlayerSurface(dependencies: UiDependencies, keepScreenOn: Boolean, modifier: Modifier) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context -> SurfaceView(context).also(dependencies::bindPlayerView) },
        update = { view -> view.keepScreenOn = keepScreenOn; dependencies.bindPlayerView(view) },
    )
    DisposableEffect(dependencies) { onDispose { dependencies.bindPlayerView(null) } }
}

@Composable
private fun PlayerScrubber(player: PlayerUiState, seek: (Long) -> Unit) {
    val duration = player.durationMillis.coerceAtLeast(1L)
    val fraction = (player.positionMillis.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (player.bufferedMillis.toFloat() / duration).coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(48.dp)
            .pointerInput(duration) {
                detectTapGestures { offset -> seek((duration * (offset.x / size.width).coerceIn(0f, 1f)).toLong()) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures { change, _ ->
                    seek((duration * (change.position.x / size.width).coerceIn(0f, 1f)).toLong())
                }
            }
            .semantics {
                contentDescription = "${formatDuration(player.positionMillis)} / ${formatDuration(player.durationMillis)}"
                progressBarRangeInfo = ProgressBarRangeInfo(player.positionMillis.toFloat(), 0f..duration.toFloat())
                setProgress { target -> seek(target.toLong().coerceIn(0, duration)); true }
            },
    ) {
        val accent = ChalnaTheme.colors.accent
        androidx.compose.foundation.Canvas(Modifier.width(maxWidth).fillMaxHeight()) {
            val y = size.height / 2f
            drawLine(Color.White.copy(.24f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White.copy(.42f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * buffered, y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * fraction, y), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(accent, 7.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * fraction, y))
        }
    }
}

@Composable
private fun PlayerDetailsPanel(item: MediaItemUi, close: () -> Unit) = Box(
    Modifier.fillMaxSize().background(Color.Black.copy(.55f)).clickableNoRipple(onClick = close),
    contentAlignment = Alignment.BottomCenter,
) {
    Column(
        Modifier.fillMaxWidth().background(ChalnaTheme.colors.surfaceHigh).safeDrawingPadding().padding(20.dp)
            .clickableNoRipple(onClick = {}),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Heading(stringResource(R.string.details), Modifier.weight(1f))
            IconButton(ChalnaIcon.CLOSE, stringResource(R.string.close), onClick = close)
        }
        DetailLine(R.string.detail_file, item.displayName)
        DetailLine(R.string.detail_date, formatMediaDate(item.capturedAtMillis))
        if (item.durationMillis > 0) DetailLine(R.string.detail_duration, formatDuration(item.durationMillis))
        if (item.width > 0 && item.height > 0) DetailLine(R.string.detail_resolution, "${item.width}×${item.height}")
        if (item.rotationDegrees != 0) DetailLine(R.string.detail_rotation, "${item.rotationDegrees}°")
        if (item.sizeBytes > 0) DetailLine(R.string.detail_size, Formatter.formatFileSize(LocalContext.current, item.sizeBytes))
        DetailLine(R.string.detail_storage, destinationLabel(item.destination))
        item.hasAudio?.let { DetailLine(R.string.detail_audio, stringResource(if (it) R.string.audio_included else R.string.audio_not_included)) }
        item.codec?.let { DetailLine(R.string.detail_codec, it) }
        item.frameRate?.let { DetailLine(R.string.detail_frame_rate, stringResource(R.string.frame_rate_value, it)) }
        DetailLine(R.string.detail_type, item.mimeType)
    }
}

@Composable
private fun PlayerDeleteDialog(cancel: () -> Unit, confirm: () -> Unit) = Box(
    Modifier.fillMaxSize().background(Color.Black.copy(.62f)),
    contentAlignment = Alignment.Center,
) {
    Column(
        Modifier.fillMaxWidth(.86f).clip(RoundedCornerShape(20.dp)).background(ChalnaTheme.colors.surfaceHigh).padding(20.dp),
    ) {
        ChalnaText(stringResource(R.string.delete_confirm), 17, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.cancel), cancel) }
            Box(Modifier.weight(1f)) { PrimaryButton(stringResource(R.string.move_to_trash), onClick = confirm) }
        }
    }
}

@Composable
private fun DetailLine(label: Int, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    ChalnaText(stringResource(label), Modifier.width(112.dp), 13, ChalnaTheme.colors.muted)
    ChalnaText(value, Modifier.weight(1f), 13)
}

@Composable
internal fun HelpScreen(dependencies: UiDependencies, back: () -> Unit) = ScreenColumn {
    TopBar(stringResource(R.string.help), back)
    HelpSection(R.string.help_use, R.string.help_use_detail)
    Hairline()
    HelpSection(R.string.help_assistant, R.string.help_assistant_detail)
    PrimaryButton(stringResource(R.string.change_assistant), onClick = dependencies::openAssistantSettings)
    Spacer(Modifier.height(16.dp))
    SecondaryButton(stringResource(R.string.quick_tile_label), dependencies::requestQuickTile)
    Spacer(Modifier.height(18.dp))
    Hairline()
    HelpSection(R.string.help_lock_screen, R.string.help_lock_screen_detail)
    Hairline()
    HelpSection(R.string.help_storage, R.string.help_storage_detail)
    Hairline()
    HelpSection(R.string.help_galaxy, R.string.help_galaxy_detail)
}

@Composable
private fun HelpSection(title: Int, detail: Int) = Column(Modifier.padding(vertical = 18.dp)) {
    ChalnaText(stringResource(title), 16, weight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Body(stringResource(detail))
}

@Composable
internal fun PrivacyScreen(back: () -> Unit) = ScreenColumn {
    TopBar(stringResource(R.string.privacy), back)
    Spacer(Modifier.height(14.dp))
    Heading(stringResource(R.string.privacy_facts_title))
    Spacer(Modifier.height(10.dp))
    PrivacyFact(R.string.privacy_storage_fact)
    PrivacyFact(R.string.privacy_network_fact)
    PrivacyFact(R.string.privacy_no_early_recording_fact)
    PrivacyFact(R.string.privacy_trigger_fact)
    PrivacyFact(R.string.privacy_vault_uninstall_fact)
}

@Composable
private fun PrivacyFact(resource: Int) = Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
    ChalnaIconCanvas(ChalnaIcon.CHECK, Modifier.size(18.dp), ChalnaTheme.colors.positive)
    Spacer(Modifier.width(12.dp))
    Body(stringResource(resource), Modifier.weight(1f))
}

@Composable
internal fun MediaThumbnail(item: MediaItemUi, dependencies: UiDependencies, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(ChalnaTheme.colors.surfaceHigh), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val requestedPx = with(density) { maxOf(maxWidth, maxHeight).roundToPx().coerceIn(96, 720) }
        val key = "${item.contentUri}:$requestedPx"
        val signal = remember(key) { CancellationSignal() }
        DisposableEffect(signal) { onDispose { signal.cancel() } }
        val bitmap by produceState<Bitmap?>(ThumbnailMemoryCache.get(key), key) {
            if (value == null) value = withContext(Dispatchers.IO) {
                dependencies.loadThumbnail(item.contentUri, requestedPx, signal)?.also { ThumbnailMemoryCache.put(key, it) }
            }
        }
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Box(Modifier.fillMaxSize().background(ChalnaTheme.colors.outline.copy(.22f)))
    }
}

internal object ThumbnailMemoryCache : android.util.LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 20L).coerceAtMost(20L * 1024L * 1024L).toInt(),
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount

    fun trim(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) evictAll()
        else trimToSize(maxSize() / 2)
    }
}

@Composable
private fun phaseTitle(phase: CapturePhase): String = stringResource(
    when (phase) {
        CapturePhase.SETUP_REQUIRED -> R.string.phase_setup_required
        CapturePhase.READY -> R.string.phase_ready
        CapturePhase.STARTING -> R.string.phase_starting
        CapturePhase.RECORDING -> R.string.phase_recording
        CapturePhase.STOPPING -> R.string.phase_stopping
        CapturePhase.SAVED -> R.string.phase_saved
        CapturePhase.ERROR -> R.string.phase_error
    },
)

@Composable
private fun captureErrorText(code: String?): String = stringResource(
    when (code) {
        CaptureFailureCode.CAMERA_BUSY.name -> R.string.capture_error_camera_busy
        CaptureFailureCode.CAMERA_PERMISSION.name -> R.string.capture_error_camera_permission
        CaptureFailureCode.MICROPHONE_PERMISSION.name -> R.string.capture_error_microphone_permission
        CaptureFailureCode.LOW_STORAGE.name -> R.string.capture_error_storage
        CaptureFailureCode.STORAGE_UNAVAILABLE.name -> R.string.capture_error_storage_unavailable
        else -> R.string.capture_error_camera
    },
)

@Composable private fun qualityLabel(value: VideoQuality) = stringResource(when (value) { VideoQuality.AUTO -> R.string.quality_auto; VideoQuality.FHD -> R.string.quality_fhd; VideoQuality.HD -> R.string.quality_hd })
@Composable private fun appearanceLabel(value: AppearanceMode) = stringResource(when (value) { AppearanceMode.SYSTEM -> R.string.system; AppearanceMode.NIGHT -> R.string.night; AppearanceMode.MIST -> R.string.mist })
@Composable private fun motionLabel(value: MotionMode) = stringResource(when (value) { MotionMode.SYSTEM -> R.string.motion_system; MotionMode.FULL -> R.string.motion_full; MotionMode.REDUCED -> R.string.motion_reduced })
@Composable private fun destinationLabel(value: StorageDestinationUi) = stringResource(if (value == StorageDestinationUi.CHALNA_VAULT) R.string.chalna_vault else R.string.device_gallery)
@Composable private fun filterLabel(value: GalleryFilter) = stringResource(when (value) { GalleryFilter.ALL -> R.string.filter_all; GalleryFilter.DEVICE_GALLERY -> R.string.device_gallery; GalleryFilter.CHALNA_VAULT -> R.string.chalna_vault; GalleryFilter.FAVORITES -> R.string.favorites; GalleryFilter.TRASH -> R.string.recently_deleted })
@Composable private fun sortLabel(value: GallerySortUi) = stringResource(when (value) { GallerySortUi.NEWEST -> R.string.sort_newest; GallerySortUi.OLDEST -> R.string.sort_oldest; GallerySortUi.LONGEST -> R.string.sort_longest; GallerySortUi.LARGEST -> R.string.sort_largest })
@Composable private fun autoStopLabel(seconds: Int) = if (seconds == 0) stringResource(R.string.no_limit) else pluralStringResource(R.plurals.seconds_value, seconds, seconds)

@Composable
private fun formatMediaDate(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(millis, locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(millis)) }
}

private fun dayKey(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now(ZoneId.systemDefault())
    return when (day) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> DateFormat.getDateInstance(DateFormat.LONG, LocalConfiguration.current.locales[0])
            .format(Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    }
}

@Composable
private fun galleryItemDescription(item: MediaItemUi): String = stringResource(
    R.string.gallery_item_description,
    formatMediaDate(item.capturedAtMillis),
    if (item.durationMillis > 0) formatDuration(item.durationMillis) else stringResource(R.string.duration_unknown),
    destinationLabel(item.destination),
)

private fun MediaItemUi.videoRatio(): Float {
    if (width <= 0 || height <= 0) return 16f / 9f
    return if (rotationDegrees % 180 == 0) width.toFloat() / height else height.toFloat() / width
}

private fun MediaItemUi.thumbnailRatio(): Float = videoRatio().coerceIn(.72f, 1.55f)
