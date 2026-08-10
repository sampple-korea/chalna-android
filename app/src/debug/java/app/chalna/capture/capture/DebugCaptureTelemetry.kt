package app.chalna.capture.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DebugCaptureTelemetry : CaptureTelemetry {
    private const val MAX_EVENTS = 80
    private val mutableSnapshot = MutableStateFlow<List<CaptureTelemetryEvent>>(emptyList())
    val snapshot: StateFlow<List<CaptureTelemetryEvent>> = mutableSnapshot.asStateFlow()

    override fun mark(event: CaptureTelemetryEvent) {
        mutableSnapshot.value = (mutableSnapshot.value + event).takeLast(MAX_EVENTS)
    }

    fun clear() {
        mutableSnapshot.value = emptyList()
    }
}
