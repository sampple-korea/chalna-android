package app.chalna.capture.capture

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DebugCaptureMarker(
    val name: String,
    val elapsedRealtimeMillis: Long,
    val timestampMillis: Long,
)

object DebugCaptureTelemetry : CaptureTelemetry {
    private const val CAPACITY = 20
    private val markers = ArrayDeque<DebugCaptureMarker>()
    private val mutableSnapshot = MutableStateFlow<List<DebugCaptureMarker>>(emptyList())
    val snapshot: StateFlow<List<DebugCaptureMarker>> = mutableSnapshot.asStateFlow()

    @Synchronized
    override fun mark(name: String) {
        markers.addLast(
            DebugCaptureMarker(
                name = name.take(48),
                elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                timestampMillis = System.currentTimeMillis(),
            ),
        )
        while (markers.size > CAPACITY) markers.removeFirst()
        mutableSnapshot.value = markers.toList()
    }

    @Synchronized
    fun clear() {
        markers.clear()
        mutableSnapshot.value = emptyList()
    }
}
