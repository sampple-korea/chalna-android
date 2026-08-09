package app.chalna.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import app.chalna.capture.ui.ChalnaApp
import app.chalna.capture.ui.ProductionUiDependencies

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val dependencies = ProductionUiDependencies(this, (application as ChalnaApplication).settingsStore)
        setContent { ChalnaApp(dependencies) }
    }
}
