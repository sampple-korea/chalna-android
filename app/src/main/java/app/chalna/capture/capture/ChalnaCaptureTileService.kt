package app.chalna.capture.capture

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.chalna.capture.ChalnaApplication
import app.chalna.capture.R
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.CaptureTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class ChalnaCaptureTileService : TileService() {
    private var listeningJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        val states = (application as ChalnaApplication).graph.captureStates
        listeningJob = scope.launch { states.state.collectLatest(::render) }
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val action = Runnable { dispatchToggle() }
        if (isLocked) unlockAndRun(action) else action.run()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun dispatchToggle() {
        (application as ChalnaApplication).graph.captureCommands.dispatch(
            invocationId = "tile-${UUID.randomUUID()}",
            command = CaptureCommand.QUICK_TILE_TOGGLE,
            trigger = CaptureTrigger.QUICK_TILE,
        )
    }

    private fun render(state: CaptureState) {
        val tile = qsTile ?: return
        when (state) {
            is CaptureState.Recording -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.quick_tile_stop)
            }
            is CaptureState.StartRequested, is CaptureState.StartingForeground,
            is CaptureState.OpeningCamera, is CaptureState.StartingRecorder,
            is CaptureState.CancelRequested, is CaptureState.StopRequested,
            is CaptureState.StoppingRecorder, is CaptureState.Finalizing,
            is CaptureState.Persisting, is CaptureState.Recovering,
            -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = getString(R.string.quick_tile_busy)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.quick_tile_start)
            }
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_chalna)
        tile.updateTile()
    }

    companion object {
        private const val UNSUPPORTED_RESULT = -1

        fun requestAdd(
            context: android.content.Context,
            callback: (Int) -> Unit,
        ) {
            if (Build.VERSION.SDK_INT < 33) {
                callback(UNSUPPORTED_RESULT)
                return
            }
            context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(context, ChalnaCaptureTileService::class.java),
                context.getString(R.string.quick_tile_label),
                Icon.createWithResource(context, R.drawable.ic_qs_chalna),
                context.mainExecutor,
                callback,
            )
        }

        fun requestRefresh(context: android.content.Context) {
            requestListeningState(context, ComponentName(context, ChalnaCaptureTileService::class.java))
        }
    }
}
