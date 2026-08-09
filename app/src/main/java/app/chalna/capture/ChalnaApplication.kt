package app.chalna.capture

import android.app.Application
import app.chalna.capture.data.SettingsStore

class ChalnaApplication : Application() {
    val settingsStore: SettingsStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SettingsStore(this) }
}
