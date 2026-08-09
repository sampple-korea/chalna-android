package app.chalna.capture.capture

fun interface CaptureTelemetry {
    fun mark(name: String)
}

/** Release builds retain no timing history; debug builds install a bounded in-memory sink. */
object CaptureTelemetryRegistry {
    @Volatile
    private var sink: CaptureTelemetry = CaptureTelemetry { }

    fun install(value: CaptureTelemetry) {
        sink = value
    }

    fun mark(name: String) {
        try {
            sink.mark(name)
        } catch (_: RuntimeException) {
            // Developer telemetry must never affect capture dispatch or finalization.
        }
    }
}
