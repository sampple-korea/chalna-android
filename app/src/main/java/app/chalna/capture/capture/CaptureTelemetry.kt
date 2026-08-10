package app.chalna.capture.capture

import android.os.SystemClock

data class CaptureTelemetryEvent(
    val invocationId: String,
    val name: String,
    val elapsedRealtimeNanos: Long,
)

fun interface CaptureTelemetry {
    fun mark(event: CaptureTelemetryEvent)
}

/** Release builds retain no detailed timing history; debug installs a bounded in-memory sink. */
object CaptureTelemetryRegistry {
    @Volatile private var sink: CaptureTelemetry = CaptureTelemetry { }

    fun install(value: CaptureTelemetry) {
        sink = value
    }

    fun mark(
        invocationId: String,
        name: String,
    ) {
        try {
            sink.mark(
                CaptureTelemetryEvent(
                    invocationId = invocationId.take(160),
                    name = name.take(64),
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                ),
            )
        } catch (_: RuntimeException) {
            // Developer telemetry cannot affect capture dispatch or finalization.
        }
    }
}
