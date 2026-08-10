package app.chalna.capture

import android.app.Application
import app.chalna.capture.capture.AndroidCaptureCommandDispatcher
import app.chalna.capture.capture.CaptureRuntime
import app.chalna.capture.capture.CaptureStateRepository
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.gallery.GalleryRepositoryFactory
import app.chalna.capture.data.db.ChalnaDatabase
import app.chalna.capture.notifications.CaptureNotifications

open class ChalnaApplication : Application() {
    val graph: ChalnaGraph by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ChalnaGraph(this) }
    val settingsStore: SettingsStore get() = graph.settingsStore

    override fun onCreate() {
        super.onCreate()
        CaptureRuntime.install(graph.captureStates)
        graph.settingsStore
        CaptureNotifications.ensureChannels(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        graph.onTrimMemory(level)
    }
}

class ChalnaGraph(private val application: Application) {
    val captureStates by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { CaptureStateRepository() }
    val settingsStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SettingsStore(application) }
    val captureCommands by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidCaptureCommandDispatcher(application, captureStates, settingsStore)
    }
    val database by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ChalnaDatabase.get(application) }
    val galleryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GalleryRepositoryFactory.create(application, settingsStore)
    }

    fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        val runningLow = android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        if (level >= runningLow) {
            app.chalna.capture.ui.ThumbnailMemoryCache.trim(level)
        }
    }
}
