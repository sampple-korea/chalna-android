package app.chalna.capture.capture

import app.chalna.capture.domain.CaptureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CaptureRuntime {
    private val mutableState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = mutableState.asStateFlow()

    fun publish(state: CaptureState) {
        mutableState.value = state
    }
}
