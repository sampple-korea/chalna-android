package app.chalna.capture.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.chalna.capture.capture.CaptureStateRepository
import app.chalna.capture.capture.CaptureTelemetryRegistry
import app.chalna.capture.data.db.ChalnaDatabase
import app.chalna.capture.data.db.PlaybackStateEntity
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class PlayerSnapshot(
    val item: CaptureItem,
    val phase: PlayerPhase,
    val positionMillis: Long,
    val durationMillis: Long,
    val bufferedMillis: Long,
    val playing: Boolean,
    val muted: Boolean,
    val speed: Float,
    val recordingConflict: Boolean,
)

class PlayerController(
    context: Context,
    private val database: ChalnaDatabase,
    private val captureStates: CaptureStateRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow<PlayerSnapshot?>(null)
    val state: StateFlow<PlayerSnapshot?> = mutableState.asStateFlow()
    private var player: ExoPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var ticker: Job? = null
    private var lastPersistedPosition = -1L
    private var receiverRegistered = false
    private val noisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
            }
        }

    init {
        scope.launch {
            captureStates.state.collectLatest { state ->
                val conflict = state.blocksPlayback()
                if (conflict) pause()
                mutableState.value = mutableState.value?.copy(recordingConflict = conflict)
            }
        }
    }

    suspend fun open(
        item: CaptureItem,
        requestedPositionMillis: Long = 0,
    ) {
        close()
        val savedPosition =
            withContext(Dispatchers.IO) {
                database.playbackStateDao().byCaptureId(item.id)?.positionMillis ?: 0
            }
        val startPosition = requestedPositionMillis.takeIf { it > 0 } ?: savedPosition
        withContext(Dispatchers.Main.immediate) {
            val created =
                ExoPlayer.Builder(appContext).build().apply {
                    setAudioAttributes(AudioAttributes.DEFAULT, true)
                    addListener(listener)
                    setMediaItem(MediaItem.fromUri(item.contentUri))
                    prepare()
                    playWhenReady = false
                    if (startPosition > 0) seekTo(startPosition)
                }
            player = created
            surfaceView?.let(created::setVideoSurfaceView)
            registerNoisyReceiver()
            mutableState.value =
                PlayerSnapshot(
                    item = item,
                    phase = PlayerPhase.PREPARING,
                    positionMillis = startPosition,
                    durationMillis = item.durationMillis,
                    bufferedMillis = 0,
                    playing = false,
                    muted = false,
                    speed = 1f,
                    recordingConflict = captureStates.state.value.blocksPlayback(),
                )
        }
    }

    fun bind(view: SurfaceView?) {
        val current = player
        surfaceView?.keepScreenOn = false
        if (view == null) surfaceView?.let { current?.clearVideoSurfaceView(it) } else current?.setVideoSurfaceView(view)
        surfaceView = view
        updateKeepScreenOn()
    }

    fun togglePlayback(): Boolean {
        val current = player ?: return false
        if (mutableState.value?.recordingConflict == true) return false
        if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
        if (current.isPlaying) current.pause() else current.play()
        publish()
        return true
    }

    fun pause() {
        player?.pause()
        publish()
    }

    fun seekTo(positionMillis: Long) {
        val current = player ?: return
        val duration = current.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        current.seekTo(positionMillis.coerceIn(0, duration))
        publish()
    }

    fun seekBy(deltaMillis: Long) = seekTo((player?.currentPosition ?: 0) + deltaMillis)

    fun setMuted(muted: Boolean) {
        player?.volume = if (muted) 0f else 1f
        publish()
    }

    fun setSpeed(speed: Float) {
        if (speed !in PLAYBACK_SPEEDS) return
        player?.playbackParameters = PlaybackParameters(speed)
        publish()
    }

    fun retry() {
        player?.prepare()
        publish()
    }

    fun onBackground() {
        pause()
        persistPosition(force = true)
    }

    suspend fun close() {
        val current = player
        val closingState = mutableState.value
        val closingPosition = (current?.currentPosition ?: closingState?.positionMillis ?: 0).coerceAtLeast(0)
        releasePlayer(current)
        if (closingState != null) persistPositionNow(closingState, closingPosition)
    }

    /** Main-thread lifecycle fallback when the Activity scope is already being cancelled. */
    fun releaseNow() {
        releasePlayer(player)
    }

    private fun releasePlayer(current: ExoPlayer?) {
        ticker?.cancel()
        ticker = null
        surfaceView?.keepScreenOn = false
        surfaceView?.let { current?.clearVideoSurfaceView(it) }
        current?.removeListener(listener)
        current?.release()
        player = null
        mutableState.value = null
        unregisterNoisyReceiver()
    }

    fun bookmark(): Pair<String, Long>? =
        mutableState.value?.item?.id?.let { id ->
            id to (player?.currentPosition ?: mutableState.value?.positionMillis ?: 0).coerceAtLeast(0)
        }

    private val listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                publish()
                if (playbackState == Player.STATE_ENDED) persistPosition(force = true)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
                if (isPlaying) {
                    startTicker()
                } else {
                    ticker?.cancel()
                    ticker = null
                    persistPosition(force = true)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val current = mutableState.value ?: return
                mutableState.value =
                    current.copy(
                        phase =
                            if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                                PlayerPhase.SOURCE_MISSING
                            } else {
                                PlayerPhase.ERROR
                            },
                        playing = false,
                    )
            }

            override fun onRenderedFirstFrame() {
                CaptureTelemetryRegistry.mark("player", "player_first_frame")
            }
        }

    private fun startTicker() {
        ticker?.cancel()
        ticker =
            scope.launch {
                while (isActive && player?.isPlaying == true) {
                    publish()
                    persistPosition(force = false)
                    delay(250)
                }
            }
    }

    private fun publish() {
        val current = player ?: return
        val existing = mutableState.value ?: return
        val duration = current.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: existing.item.durationMillis
        val phase =
            when (current.playbackState) {
                Player.STATE_IDLE -> if (current.playerError == null) PlayerPhase.PREPARING else PlayerPhase.ERROR
                Player.STATE_BUFFERING -> PlayerPhase.BUFFERING
                Player.STATE_READY -> PlayerPhase.READY
                Player.STATE_ENDED -> PlayerPhase.ENDED
                else -> PlayerPhase.ERROR
            }
        mutableState.value =
            existing.copy(
                phase = phase,
                positionMillis = current.currentPosition.coerceAtLeast(0),
                durationMillis = duration.coerceAtLeast(0),
                bufferedMillis = current.bufferedPosition.coerceAtLeast(0),
                playing = current.isPlaying,
                muted = current.volume == 0f,
                speed = current.playbackParameters.speed,
            )
        updateKeepScreenOn()
    }

    private fun persistPosition(force: Boolean) {
        val snapshot = mutableState.value ?: return
        val position = (player?.currentPosition ?: snapshot.positionMillis).coerceAtLeast(0)
        if (!force && abs(position - lastPersistedPosition) < POSITION_WRITE_INTERVAL_MILLIS) return
        lastPersistedPosition = position
        val stored = if (snapshot.durationMillis > 0 && snapshot.durationMillis - position <= COMPLETION_RESET_MILLIS) 0 else position
        scope.launch(Dispatchers.IO) {
            writePosition(snapshot.item.id, stored)
        }
    }

    private suspend fun persistPositionNow(
        snapshot: PlayerSnapshot,
        position: Long,
    ) = withContext(NonCancellable + Dispatchers.IO) {
        val stored =
            if (snapshot.durationMillis > 0 && snapshot.durationMillis - position <= COMPLETION_RESET_MILLIS) 0 else position
        writePosition(snapshot.item.id, stored)
    }

    private suspend fun writePosition(
        captureId: String,
        position: Long,
    ) {
        try {
            database.playbackStateDao().upsert(
                PlaybackStateEntity(captureId, position, System.currentTimeMillis()),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Unit
        }
    }

    private fun updateKeepScreenOn() {
        surfaceView?.keepScreenOn = player?.isPlaying == true
    }

    private fun registerNoisyReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(noisyReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(noisyReceiver) }
        receiverRegistered = false
    }

    private fun CaptureState.blocksPlayback(): Boolean =
        this is CaptureState.StartRequested ||
            this is CaptureState.StartingForeground || this is CaptureState.OpeningCamera ||
            this is CaptureState.StartingRecorder || this is CaptureState.Recording ||
            this is CaptureState.CancelRequested || this is CaptureState.StopRequested ||
            this is CaptureState.StoppingRecorder || this is CaptureState.Finalizing ||
            this is CaptureState.Persisting

    private companion object {
        val PLAYBACK_SPEEDS = setOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        const val POSITION_WRITE_INTERVAL_MILLIS = 5_000L
        const val COMPLETION_RESET_MILLIS = 5_000L
    }
}
