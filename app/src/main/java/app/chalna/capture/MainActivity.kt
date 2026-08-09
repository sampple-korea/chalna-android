package app.chalna.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import app.chalna.capture.ui.ChalnaApp
import app.chalna.capture.ui.ProductionUiDependencies

class MainActivity : ComponentActivity() {
    private lateinit var dependencies: ProductionUiDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        dependencies = ProductionUiDependencies(this, (application as ChalnaApplication).settingsStore)
        setContent { ChalnaApp(dependencies) }
        savedInstanceState?.getString(STATE_PLAYER_ID)?.let { id ->
            dependencies.openCaptureWhenReady(id, savedInstanceState.getLong(STATE_PLAYER_POSITION))
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_CAPTURE) {
            intent.getStringExtra(EXTRA_CAPTURE_ID)?.let(dependencies::openCaptureWhenReady)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        dependencies.currentPlayerBookmark()?.let { (id, position) ->
            outState.putString(STATE_PLAYER_ID, id)
            outState.putLong(STATE_PLAYER_POSITION, position)
        }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val ACTION_OPEN_CAPTURE = "app.chalna.capture.action.OPEN_CAPTURE"
        const val EXTRA_CAPTURE_ID = "capture_id"
        private const val STATE_PLAYER_ID = "player_id"
        private const val STATE_PLAYER_POSITION = "player_position"
    }
}
