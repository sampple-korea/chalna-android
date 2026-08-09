package app.chalna.capture.capture

import app.chalna.capture.domain.CaptureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.SystemClock

data class RuntimeDiagnostic(
    val name: String,
    val elapsedRealtimeMillis: Long,
    val wallClockMillis: Long,
)

object CaptureRuntime {
    private val mutableState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = mutableState.asStateFlow()
    private val diagnostics = ArrayDeque<RuntimeDiagnostic>()

    fun publish(state: CaptureState) {
        mutableState.value = state
        record("state_${state.javaClass.simpleName}")
    }

    @Synchronized
    fun record(name: String) {
        diagnostics.addLast(
            RuntimeDiagnostic(name.take(48), SystemClock.elapsedRealtime(), System.currentTimeMillis()),
        )
        while (diagnostics.size > 20) diagnostics.removeFirst()
    }

    @Synchronized
    fun diagnosticSnapshot(): List<RuntimeDiagnostic> = diagnostics.toList()

    @Synchronized
    fun clearDiagnostics() {
        diagnostics.clear()
    }
}
