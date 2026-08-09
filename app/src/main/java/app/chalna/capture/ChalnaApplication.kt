package app.chalna.capture

import android.app.Application
import app.chalna.capture.data.SettingsStore

open class ChalnaApplication : Application() {
    val settingsStore: SettingsStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        if (getProcessName() == packageName) settingsStore
    }
}
