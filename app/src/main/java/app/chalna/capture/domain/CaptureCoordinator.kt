package app.chalna.capture.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CaptureEngine {
    suspend fun start(invocationId: String): Long
    suspend fun stop(): LastCapture?
}

class CaptureCoordinator(
    private val engine: CaptureEngine,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val dedupeCapacity: Int = 64,
    private val onStateChanged: (CaptureState) -> Unit = {},
) {
    private val mutex = Mutex()
    private val handled = LinkedHashSet<String>()
    @Volatile var state: CaptureState = CaptureState.Idle
        private set
    @Volatile var lastCapture: LastCapture? = null
        private set

    suspend fun dispatch(request: CaptureRequest): CaptureState = mutex.withLock {
        if (!remember(request.invocationId)) return state
        when (request.command) {
            CaptureCommand.TOGGLE -> when (state) {
                CaptureState.Idle, is CaptureState.Failed, is CaptureState.Saved -> start(request.invocationId)
                is CaptureState.Recording -> stop(request.invocationId)
                is CaptureState.Starting, is CaptureState.Stopping, is CaptureState.Saving -> state
            }
            CaptureCommand.STOP -> when (state) {
                is CaptureState.Recording -> stop(request.invocationId)
                else -> state
            }
        }
    }

    private suspend fun start(id: String): CaptureState {
        update(CaptureState.Starting(id))
        update(try {
            val start = engine.start(id).takeIf { it > 0 } ?: clockMillis()
            CaptureState.Recording(id, start)
        } catch (t: Throwable) {
            CaptureState.Failed(t.message ?: t.javaClass.simpleName)
        })
        return state
    }

    private suspend fun stop(id: String): CaptureState {
        update(CaptureState.Stopping(id))
        update(CaptureState.Saving(id))
        update(try {
            val saved = engine.stop()?.takeIf(LastCapture::isUsable)
                ?: error("Recording could not be finalized")
            lastCapture = saved
            CaptureState.Saved(saved)
        } catch (t: Throwable) {
            CaptureState.Failed(t.message ?: t.javaClass.simpleName)
        })
        return state
    }

    private fun update(value: CaptureState) {
        state = value
        onStateChanged(value)
    }

    private fun remember(id: String): Boolean {
        if (id.isBlank() || handled.contains(id)) return false
        handled += id
        while (handled.size > dedupeCapacity) handled.remove(handled.first())
        return true
    }
}
