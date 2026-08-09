package app.chalna.capture.ui

import android.graphics.Bitmap
import android.app.Activity
import android.graphics.SurfaceTexture
import android.util.LruCache
import android.text.format.Formatter
import android.os.CancellationSignal
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
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
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@Composable
internal fun SetupScreen(state: ChalnaUiState, d: UiDependencies) = ScreenColumn {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChalnaIconCanvas(ChalnaIcon.MARK, Modifier.size(34.dp), ChalnaTheme.colors.accent)
        Spacer(Modifier.width(10.dp)); Heading(stringResource(R.string.app_name))
    }
    Spacer(Modifier.height(26.dp))
    Heading(stringResource(R.string.setup_title))
    Spacer(Modifier.height(8.dp)); Body(stringResource(R.string.setup_body))
    Spacer(Modifier.height(22.dp))
    GlassCard {
        SettingRow(stringResource(R.string.camera), stringResource(if (state.cameraPermanentlyDenied) R.string.open_app_settings_detail else R.string.camera_setup_detail), ChalnaIcon.CAMERA, state.cameraGranted) { if (state.cameraPermanentlyDenied) d.openAppSettings() else d.requestCamera() }
        SettingRow(stringResource(R.string.audio), if (state.sound) stringResource(R.string.audio_setup_on) else stringResource(R.string.audio_setup_off), ChalnaIcon.MIC, !state.sound || state.microphoneGranted) {
            if (state.microphonePermanentlyDenied) d.openAppSettings() else if (state.sound && !state.microphoneGranted) d.requestMicrophone() else d.setSound(!state.sound)
        }
        SettingRow(stringResource(R.string.assistant), stringResource(R.string.assistant_setup_detail), ChalnaIcon.ASSISTANT, state.assistantSelected, d::openAssistantSettings)
        SettingRow(stringResource(R.string.notifications_optional), stringResource(if (state.notificationsPermanentlyDenied) R.string.open_notification_settings_detail else R.string.notifications_optional_detail), ChalnaIcon.BELL, state.notificationsGranted) { if (state.notificationsPermanentlyDenied) d.openNotificationSettings() else d.requestNotifications() }
    }
    Spacer(Modifier.height(24.dp))
    val essentialReady = state.cameraGranted && state.assistantSelected && (!state.sound || state.microphoneGranted)
    PrimaryButton(stringResource(R.string.finish_setup), essentialReady, d::finishSetup)
}

@Composable
internal fun HomeScreen(state: ChalnaUiState, d: UiDependencies, navigate: (ChalnaRoute) -> Unit) = ScreenColumn {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        ChalnaIconCanvas(ChalnaIcon.MARK, Modifier.size(30.dp), ChalnaTheme.colors.accent)
        Spacer(Modifier.width(9.dp)); ChalnaText(stringResource(R.string.app_name), 23, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(ChalnaIcon.GALLERY, stringResource(R.string.gallery)) { d.refreshGallery(); navigate(ChalnaRoute.GALLERY) }
        IconButton(ChalnaIcon.SETTINGS, stringResource(R.string.settings)) { navigate(ChalnaRoute.SETTINGS) }
    }
    Spacer(Modifier.height(36.dp))
    InvocationGlow(state.phase, state.reducedMotion || state.powerSaver, Modifier.size(210.dp).align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(26.dp))
    ChalnaText(
        if (!state.ready && state.phase == CapturePhase.READY) stringResource(R.string.phase_setup_required) else phaseTitle(state.phase),
        28,
        weight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        align = TextAlign.Center,
    )
    if (state.phase == CapturePhase.RECORDING || state.phase == CapturePhase.STOPPING) {
        ChalnaText(formatDuration(state.durationSeconds * 1000), 18, ChalnaTheme.colors.muted, FontWeight.Medium, Modifier.fillMaxWidth(), TextAlign.Center)
    }
    state.errorMessage?.let { ChalnaText(it, 14, ChalnaTheme.colors.danger, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }, align = TextAlign.Center) }
    Spacer(Modifier.height(22.dp))
    when {
        state.phase == CapturePhase.RECORDING -> PrimaryButton(stringResource(R.string.stop_capture), onClick = d::toggleCapture)
        !state.ready -> PrimaryButton(stringResource(R.string.review_setup), onClick = d::reviewSetup)
        else -> ChalnaText(
            stringResource(R.string.home_trigger_hint),
            15,
            ChalnaTheme.colors.muted,
            modifier = Modifier.fillMaxWidth(),
            align = TextAlign.Center,
        )
    }
    val latest = state.lastCapture
    if (latest != null) {
        Spacer(Modifier.height(30.dp)); SectionTitle(stringResource(R.string.latest_capture))
        GlassCard(Modifier.clickableNoRipple(Role.Button) { d.openPlayer(latest.id); navigate(ChalnaRoute.PLAYER) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaThumbnail(latest, d, Modifier.size(92.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                    ChalnaText(formatMediaDate(latest.capturedAtMillis), 13, ChalnaTheme.colors.muted)
                    if (latest.durationMillis > 0) ChalnaText(formatDuration(latest.durationMillis), 13, ChalnaTheme.colors.muted)
                }
                ChalnaIconCanvas(ChalnaIcon.PLAY, Modifier.size(24.dp), ChalnaTheme.colors.accent)
            }
        }
    }
}

@Composable
internal fun SettingsScreen(state: ChalnaUiState, d: UiDependencies, back: () -> Unit, navigate: (ChalnaRoute) -> Unit) = ScreenColumn {
    TopBar(stringResource(R.string.settings), back)
    SectionTitle(stringResource(R.string.capture))
    Column {
        ChalnaText(stringResource(R.string.save_to), 14, ChalnaTheme.colors.muted, modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StorageDestinationUi.entries.forEach { destination -> ChoiceChip(destinationLabel(destination), state.storageDestination == destination) { d.setStorageDestination(destination) } }
        }
        Body(stringResource(if (state.storageDestination == StorageDestinationUi.CHALNA_VAULT) R.string.vault_storage_detail else R.string.device_gallery_storage_detail))
        ChalnaText(stringResource(R.string.existing_videos_not_moved), 12, ChalnaTheme.colors.muted)
        Spacer(Modifier.height(12.dp)); ToggleRow(stringResource(R.string.include_audio), state.sound, d::setSound)
        ChalnaText(stringResource(R.string.video_quality), 14, ChalnaTheme.colors.muted, modifier = Modifier.padding(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoQuality.entries.forEach { quality -> ChoiceChip(qualityLabel(quality), state.quality == quality) { d.setQuality(quality) } }
        }
        ChalnaText(stringResource(R.string.auto_stop), 14, ChalnaTheme.colors.muted, modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 15, 30, 60).forEach { seconds ->
                ChoiceChip(if (seconds == 0) stringResource(R.string.no_limit) else pluralStringResource(R.plurals.seconds_value, seconds, seconds), state.autoStopSeconds == seconds) { d.setAutoStop(seconds) }
            }
        }
    }; Hairline()
    SectionTitle(stringResource(R.string.appearance_feedback))
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppearanceMode.entries.forEach { mode -> ChoiceChip(appearanceLabel(mode), state.appearance == mode) { d.setAppearance(mode) } }
        }
        Spacer(Modifier.height(8.dp)); ToggleRow(stringResource(R.string.haptics), state.haptics, d::setHaptics)
        ChalnaText(stringResource(R.string.motion_effects), 14, ChalnaTheme.colors.muted, modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MotionMode.entries.forEach { mode -> ChoiceChip(motionLabel(mode), state.motion == mode) { d.setMotion(mode) } }
        }
    }; Hairline()
    SectionTitle(stringResource(R.string.access_setup))
    Column {
        SettingRow(stringResource(R.string.camera), stringResource(if (state.cameraGranted) R.string.allowed else R.string.action_needed), ChalnaIcon.CAMERA, state.cameraGranted) { if (state.cameraPermanentlyDenied) d.openAppSettings() else d.requestCamera() }
        SettingRow(
            stringResource(R.string.microphone),
            stringResource(if (!state.sound) R.string.audio_off else if (state.microphoneGranted) R.string.allowed else R.string.action_needed),
            ChalnaIcon.MIC,
            !state.sound || state.microphoneGranted,
        ) { if (state.microphonePermanentlyDenied) d.openAppSettings() else d.requestMicrophone() }
        SettingRow(stringResource(R.string.assistant), stringResource(if (state.assistantSelected) R.string.selected else R.string.action_needed), ChalnaIcon.ASSISTANT, state.assistantSelected, d::openAssistantSettings)
        SettingRow(stringResource(R.string.notifications_optional), stringResource(if (state.notificationsGranted) R.string.allowed else R.string.optional), ChalnaIcon.BELL, state.notificationsGranted, d::openNotificationSettings)
    }; Hairline()
    SectionTitle(stringResource(R.string.support))
    Column {
        SettingRow(stringResource(R.string.help), stringResource(R.string.help_short), ChalnaIcon.ASSISTANT) { navigate(ChalnaRoute.HELP) }
        SettingRow(stringResource(R.string.privacy), stringResource(R.string.privacy_short), ChalnaIcon.MARK) { navigate(ChalnaRoute.PRIVACY) }
    }
    Spacer(Modifier.height(16.dp)); ChalnaText(stringResource(R.string.version_info, BuildConfig.VERSION_NAME), 12, ChalnaTheme.colors.muted, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
}

@Composable
internal fun GalleryScreen(state: ChalnaUiState, d: UiDependencies, back: () -> Unit, open: (String) -> Unit) {
    val selected = state.selectedMediaIds
    var confirmBatchDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (selected.isEmpty()) TopBar(stringResource(R.string.gallery), back) else TopBar(pluralStringResource(R.plurals.items_selected, selected.size, selected.size), d::clearMediaSelection) {
            IconButton(ChalnaIcon.SHARE, stringResource(R.string.share), onClick = d::shareSelectedMedia)
            if (state.gallery.any { it.id in selected && it.destination == StorageDestinationUi.CHALNA_VAULT }) IconButton(ChalnaIcon.EXPORT, stringResource(R.string.export), onClick = d::exportSelectedMedia)
            IconButton(ChalnaIcon.DELETE, stringResource(R.string.delete)) { confirmBatchDelete = true }
        }
        if (confirmBatchDelete) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChalnaText(stringResource(R.string.delete_selected_confirm), 14, modifier = Modifier.weight(1f))
                Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.cancel)) { confirmBatchDelete = false } }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { PrimaryButton(stringResource(R.string.delete)) { confirmBatchDelete = false; d.deleteSelectedMedia() } }
            }
        }
        state.operationMessage?.let {
            ChalnaText(it, 13, ChalnaTheme.colors.danger, modifier = Modifier.padding(vertical = 6.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GalleryFilter.entries.forEach { filter -> ChoiceChip(filterLabel(filter), state.galleryFilter == filter) { d.setGalleryFilter(filter) } }
        }
        val visible = state.gallery.filter { when (state.galleryFilter) { GalleryFilter.ALL -> true; GalleryFilter.DEVICE_GALLERY -> it.destination == StorageDestinationUi.DEVICE_GALLERY; GalleryFilter.CHALNA_VAULT -> it.destination == StorageDestinationUi.CHALNA_VAULT } }
        if (visible.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ChalnaText(stringResource(R.string.gallery_empty_title), 18, weight = FontWeight.SemiBold, align = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                ChalnaText(stringResource(R.string.gallery_empty_body), 14, ChalnaTheme.colors.muted, align = TextAlign.Center)
            }
        }
        else {
            val grouped = visible.sortedByDescending { it.capturedAtMillis }.groupBy { dayKey(it.capturedAtMillis) }
            LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (day, media) ->
                    item(span = { GridItemSpan(maxLineSpan) }) { ChalnaText(dayLabel(day), 14, ChalnaTheme.colors.muted, FontWeight.SemiBold, Modifier.padding(top = 14.dp, bottom = 4.dp)) }
                    items(media, key = { it.id }) { item -> MediaGridItem(item, d, item.id in selected, selected.isNotEmpty(), { d.toggleMediaSelection(item.id) }) { open(item.id) } }
                }
            }
        }
    }
}

@Composable
private fun MediaGridItem(item: MediaItemUi, d: UiDependencies, selected: Boolean, selectionMode: Boolean, select: () -> Unit, open: () -> Unit) {
    val description = galleryItemDescription(item)
    Column(
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onLongClick = select, onClick = { if (selectionMode) select() else open() },
        ).semantics {
            this.selected = selected
            contentDescription = description
        },
    ) {
        Box {
            MediaThumbnail(item, d, Modifier.fillMaxWidth().aspectRatio(1.18f))
            if (selected) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(ChalnaTheme.colors.accent), contentAlignment = Alignment.Center) { ChalnaIconCanvas(ChalnaIcon.CHECK, Modifier.size(16.dp), Color(0xFF071018)) }
            Row(Modifier.align(Alignment.BottomEnd).padding(7.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(.68f)).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.destination == StorageDestinationUi.CHALNA_VAULT) { ChalnaIconCanvas(ChalnaIcon.MARK, Modifier.size(11.dp), Color.White); Spacer(Modifier.width(3.dp)) }
                if (item.durationMillis > 0) ChalnaText(formatDuration(item.durationMillis), 11, Color.White, FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun PlayerScreen(player: PlayerUiState, d: UiDependencies, back: () -> Unit) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var detailsVisible by rememberSaveable { mutableStateOf(false) }
    var controls by rememberSaveable { mutableStateOf(true) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    val touchExploration = LocalAccessibilityManager.current?.isTouchExplorationEnabled == true
    DisposableEffect(fullscreen, activity) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) } }
    }
    LaunchedEffect(controls, player.playing, more, confirmDelete) {
        if (controls && player.playing && !more && !confirmDelete && !touchExploration) { delay(2_500); controls = false }
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (controls) Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(ChalnaIcon.BACK, stringResource(R.string.back), onClick = back); Spacer(Modifier.weight(1f))
            IconButton(ChalnaIcon.SHARE, stringResource(R.string.share), onClick = d::shareCurrentMedia)
            IconButton(ChalnaIcon.MORE, stringResource(R.string.more)) { more = !more; controls = true }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val videoRatio = if (player.item.width > 0 && player.item.height > 0) player.item.width.toFloat() / player.item.height else 16f / 9f
            val containerRatio = maxWidth / maxHeight
            val videoModifier = if (containerRatio > videoRatio) {
                Modifier.fillMaxHeight().aspectRatio(videoRatio).align(Alignment.Center)
            } else {
                Modifier.fillMaxWidth().aspectRatio(videoRatio).align(Alignment.Center)
            }
            PlayerSurface(d, videoModifier)
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { controls = !controls } })
        }
        if (controls) Column(Modifier.fillMaxWidth().background(Color.Black.copy(.82f)).padding(14.dp)) {
            player.message?.let {
                ChalnaText(it, 13, Color(0xFFFF9B8B), modifier = Modifier.padding(bottom = 6.dp))
            }
            Scrubber(player.positionMillis, player.durationMillis, d::seekPlayer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(if (player.playing) ChalnaIcon.PAUSE else ChalnaIcon.PLAY, stringResource(if (player.playing) R.string.pause else R.string.play), onClick = d::togglePlayback)
                ChalnaText("${formatDuration(player.positionMillis)} / ${formatDuration(player.durationMillis)}", 13, Color.White.copy(.78f), modifier = Modifier.weight(1f))
                IconButton(if (player.muted) ChalnaIcon.MUTED else ChalnaIcon.VOLUME, stringResource(if (player.muted) R.string.unmute else R.string.mute)) { d.setPlayerMuted(!player.muted) }
                IconButton(ChalnaIcon.FULLSCREEN, stringResource(R.string.fullscreen), fullscreen) { fullscreen = !fullscreen }
            }
            if (more) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (player.item.destination == StorageDestinationUi.CHALNA_VAULT) PlayerAction(R.string.export, ChalnaIcon.EXPORT, d::exportCurrentMedia)
                    PlayerAction(R.string.open_external, ChalnaIcon.EXTERNAL, d::openCurrentMediaExternally)
                    PlayerAction(R.string.details, ChalnaIcon.INFO) { detailsVisible = !detailsVisible }
                    PlayerAction(R.string.delete, ChalnaIcon.DELETE) { confirmDelete = true }
                }
                if (detailsVisible) { Spacer(Modifier.height(8.dp)); PlayerDetails(player.item) }
            }
            if (confirmDelete) {
                Spacer(Modifier.height(10.dp)); ChalnaText(stringResource(R.string.delete_confirm), 14, Color.White, FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.cancel)) { confirmDelete = false } }
                    Box(Modifier.weight(1f)) { PrimaryButton(stringResource(R.string.delete)) { confirmDelete = false; d.deleteCurrentMedia() } }
                }
            }
        }
    }
}

@Composable private fun PlayerAction(label: Int, icon: ChalnaIcon, action: () -> Unit) = Column(Modifier.width(82.dp).heightIn(min=64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.10f)).clickableNoRipple(Role.Button,onClick=action).padding(8.dp), horizontalAlignment=Alignment.CenterHorizontally) { ChalnaIconCanvas(icon,Modifier.size(22.dp),Color.White);Spacer(Modifier.height(4.dp));ChalnaText(stringResource(label),11,Color.White,align=TextAlign.Center) }

@Composable private fun PlayerSurface(d: UiDependencies, modifier: Modifier) {
    AndroidView(modifier = modifier.background(Color.Black), factory = { context -> TextureView(context).apply {
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            private var surface: Surface? = null
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) { surface = Surface(texture); d.bindPlayerSurface(surface) }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { d.bindPlayerSurface(null); surface?.release(); surface=null; return true }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
    } })
    DisposableEffect(d) { onDispose { d.bindPlayerSurface(null) } }
}

@Composable private fun Scrubber(position: Long, duration: Long, seek: (Long) -> Unit) {
    val fraction = if (duration <= 0) 0f else (position.toFloat()/duration).coerceIn(0f,1f)
    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).pointerInput(duration) { detectTapGestures { offset -> if(duration>0) seek((duration*(offset.x/size.width).coerceIn(0f,1f)).toLong()) } }.semantics {
        contentDescription = "${formatDuration(position)} / ${formatDuration(duration)}"
        progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 0f..duration.coerceAtLeast(1).toFloat())
        setProgress { target -> seek(target.toLong().coerceIn(0, duration)); true }
    }) {
        val accent = ChalnaTheme.colors.accent
        androidx.compose.foundation.Canvas(Modifier.width(maxWidth).fillMaxHeight()) {
            val y = size.height/2f; drawLine(Color.White.copy(.28f), androidx.compose.ui.geometry.Offset(0f,y), androidx.compose.ui.geometry.Offset(size.width,y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(0f,y), androidx.compose.ui.geometry.Offset(size.width*fraction,y), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(accent, 7.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width*fraction,y))
        }
    }
}

@Composable private fun PlayerDetails(item: MediaItemUi) {
    val context = LocalContext.current
    val storage = stringResource(if (item.destination == StorageDestinationUi.CHALNA_VAULT) R.string.chalna_vault else R.string.device_gallery)
    val audio = stringResource(if (item.hasAudio) R.string.audio_included else R.string.audio_not_included)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailLine(R.string.detail_file, item.displayName)
        DetailLine(R.string.detail_date, formatMediaDate(item.capturedAtMillis))
        if (item.durationMillis > 0) DetailLine(R.string.detail_duration, formatDuration(item.durationMillis))
        if (item.width > 0 && item.height > 0) DetailLine(R.string.detail_resolution, "${item.width}×${item.height}")
        if (item.sizeBytes > 0) DetailLine(R.string.detail_size, Formatter.formatFileSize(context, item.sizeBytes))
        DetailLine(R.string.detail_storage, storage)
        DetailLine(R.string.detail_audio, audio)
        DetailLine(R.string.detail_type, item.mimeType)
    }
}

@Composable private fun DetailLine(label: Int, value: String) {
    ChalnaText("${stringResource(label)}  $value", 12, Color.White.copy(.76f))
}

@Composable internal fun HelpScreen(d: UiDependencies, back: () -> Unit) = ScreenColumn {
    TopBar(stringResource(R.string.help), back)
    Spacer(Modifier.height(12.dp))
    HelpSection(R.string.help_use, R.string.help_use_detail)
    Hairline()
    HelpSection(R.string.help_assistant, R.string.help_assistant_detail)
    PrimaryButton(stringResource(R.string.change_assistant), onClick = d::openAssistantSettings)
    Spacer(Modifier.height(18.dp)); Hairline()
    HelpSection(R.string.help_lock_screen, R.string.help_lock_screen_detail)
    Hairline()
    HelpSection(R.string.help_storage, R.string.help_storage_detail)
}
@Composable private fun HelpSection(title: Int, detail: Int) = Column(Modifier.padding(vertical = 18.dp)) {
    ChalnaText(stringResource(title), 16, weight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp)); Body(stringResource(detail))
}
@Composable internal fun PrivacyScreen(back: () -> Unit) = ScreenColumn { TopBar(stringResource(R.string.privacy), back); Spacer(Modifier.height(12.dp)); Heading(stringResource(R.string.private_by_design));Spacer(Modifier.height(8.dp));Body(stringResource(R.string.privacy_statement));Spacer(Modifier.height(16.dp));GlassCard { ChalnaText(stringResource(R.string.explicit_trigger_only),16,weight=FontWeight.SemiBold);Body(stringResource(R.string.explicit_trigger_detail));Spacer(Modifier.height(16.dp));ChalnaText(stringResource(R.string.permissions_used),16,weight=FontWeight.SemiBold);Body(stringResource(R.string.permissions_used_detail)) } }

@Composable internal fun MediaThumbnail(item: MediaItemUi, d: UiDependencies, modifier: Modifier = Modifier) {
    val signal = remember(item.contentUri) { CancellationSignal() }
    DisposableEffect(signal) { onDispose { signal.cancel() } }
    val bitmap by produceState<Bitmap?>(ThumbnailCache.get(item.contentUri), item.contentUri) {
        if (value == null) value = withContext(Dispatchers.IO) {
            d.loadThumbnail(item.contentUri, 512, signal)?.also { ThumbnailCache.put(item.contentUri, it) }
        }
    }
    Box(modifier.background(ChalnaTheme.colors.surfaceHigh), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: ChalnaIconCanvas(ChalnaIcon.MARK, Modifier.size(34.dp), ChalnaTheme.colors.muted)
    }
}

private object ThumbnailCache : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16L).coerceAtMost(24L * 1024L * 1024L).toInt()) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

@Composable private fun phaseTitle(phase: CapturePhase) = stringResource(when(phase){CapturePhase.READY->R.string.phase_ready;CapturePhase.STARTING->R.string.phase_starting;CapturePhase.RECORDING->R.string.phase_recording;CapturePhase.STOPPING->R.string.phase_stopping;CapturePhase.SAVED->R.string.phase_saved;CapturePhase.ERROR->R.string.phase_error})
@Composable private fun qualityLabel(value: VideoQuality)=stringResource(when(value){VideoQuality.AUTO->R.string.quality_auto;VideoQuality.FHD->R.string.quality_fhd;VideoQuality.HD->R.string.quality_hd})
@Composable private fun appearanceLabel(value: AppearanceMode)=stringResource(when(value){AppearanceMode.SYSTEM->R.string.system;AppearanceMode.NIGHT->R.string.night;AppearanceMode.MIST->R.string.mist})
@Composable private fun motionLabel(value: MotionMode)=stringResource(when(value){MotionMode.SYSTEM->R.string.motion_system;MotionMode.FULL->R.string.motion_full;MotionMode.REDUCED->R.string.motion_reduced})
@Composable private fun filterLabel(value: GalleryFilter)=stringResource(when(value){GalleryFilter.ALL->R.string.filter_all;GalleryFilter.DEVICE_GALLERY->R.string.device_gallery;GalleryFilter.CHALNA_VAULT->R.string.chalna_vault})
@Composable private fun destinationLabel(value: StorageDestinationUi)=stringResource(when(value){StorageDestinationUi.DEVICE_GALLERY->R.string.device_gallery;StorageDestinationUi.CHALNA_VAULT->R.string.chalna_vault})
private fun formatMediaDate(millis:Long)=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(millis))
private fun dayKey(millis: Long): Long = Calendar.getInstance().run {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    timeInMillis
}
@Composable private fun dayLabel(day: Long): String {
    val today = dayKey(System.currentTimeMillis())
    return when (day) {
        today -> stringResource(R.string.today)
        today - 86_400_000L -> stringResource(R.string.yesterday)
        else -> DateFormat.getDateInstance(DateFormat.LONG).format(Date(day))
    }
}
@Composable private fun galleryItemDescription(item: MediaItemUi): String = stringResource(
    R.string.gallery_item_description,
    formatMediaDate(item.capturedAtMillis),
    if (item.durationMillis > 0) formatDuration(item.durationMillis) else stringResource(R.string.duration_unknown),
    stringResource(if (item.destination == StorageDestinationUi.CHALNA_VAULT) R.string.chalna_vault else R.string.device_gallery),
)
