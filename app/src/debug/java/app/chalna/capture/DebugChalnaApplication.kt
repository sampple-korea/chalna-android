package app.chalna.capture

import app.chalna.capture.capture.CaptureTelemetryRegistry
import app.chalna.capture.capture.DebugCaptureTelemetry

class DebugChalnaApplication : ChalnaApplication() {
    override fun onCreate() {
        super.onCreate()
        CaptureTelemetryRegistry.install(DebugCaptureTelemetry)
    }
}
