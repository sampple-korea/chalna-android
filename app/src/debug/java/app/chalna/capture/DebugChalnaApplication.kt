package app.chalna.capture

import android.os.Build
import android.os.StrictMode
import app.chalna.capture.capture.CaptureTelemetryRegistry
import app.chalna.capture.capture.DebugCaptureTelemetry

class DebugChalnaApplication : ChalnaApplication() {
    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        val vmPolicy =
            StrictMode.VmPolicy
                .Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
        if (Build.VERSION.SDK_INT >= 31) vmPolicy.detectIncorrectContextUse()
        StrictMode.setVmPolicy(vmPolicy.penaltyLog().build())
        CaptureTelemetryRegistry.install(DebugCaptureTelemetry)
    }
}
