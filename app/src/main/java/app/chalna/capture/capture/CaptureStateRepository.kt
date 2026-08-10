package app.chalna.capture.capture

import android.os.SystemClock
import app.chalna.capture.domain.CaptureCommandResult
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.MonotonicClock
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()
}

class CaptureStateRepository(
    private val monotonicClock: MonotonicClock = AndroidMonotonicClock(),
    private val receiptCapacity: Int = 128,
) {
    private val mutableState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = mutableState.asStateFlow()
    private val transitionCounter = AtomicLong(0)
    val version: Long get() = transitionCounter.get()
    private val receipts = LinkedHashMap<String, CaptureCommandResult>()
    private var lastSavedElapsedNanos = Long.MIN_VALUE

    internal fun publish(value: CaptureState) {
        mutableState.value = value
        transitionCounter.incrementAndGet()
        if (value is CaptureState.Saved) lastSavedElapsedNanos = value.completedAtElapsedNanos
    }

    @Synchronized
    fun reserve(invocationId: String, result: CaptureCommandResult): CaptureCommandResult {
        receipts[invocationId]?.let { return CaptureCommandResult.Duplicate(invocationId, it) }
        receipts[invocationId] = result
        trimReceipts()
        return result
    }

    @Synchronized
    fun updateReceipt(invocationId: String, result: CaptureCommandResult) {
        receipts[invocationId] = result
        trimReceipts()
    }

    @Synchronized
    fun receipt(invocationId: String): CaptureCommandResult? = receipts[invocationId]

    fun isAnonymousFinalizeGuardActive(invocationId: String): Boolean =
        invocationId.startsWith("anonymous-") && lastSavedElapsedNanos != Long.MIN_VALUE &&
            monotonicClock.nowNanos() - lastSavedElapsedNanos in 0 until ANONYMOUS_FINALIZE_GUARD_NANOS

    private fun trimReceipts() {
        while (receipts.size > receiptCapacity) receipts.remove(receipts.keys.first())
    }

    private companion object {
        const val ANONYMOUS_FINALIZE_GUARD_NANOS = 550_000_000L
    }
}

/** Compatibility read-only facade for code being migrated from v1.1. */
object CaptureRuntime {
    @Volatile private var repository: CaptureStateRepository? = null
    val state: StateFlow<CaptureState>
        get() = requireNotNull(repository) { "Capture state repository is not installed" }.state

    internal fun install(value: CaptureStateRepository) {
        repository = value
    }
}
