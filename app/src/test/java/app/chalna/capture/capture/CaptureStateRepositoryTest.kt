package app.chalna.capture.capture

import app.chalna.capture.domain.CaptureCommandResult
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.domain.MonotonicClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStateRepositoryTest {
    @Test fun sameInvocationIdOneHundredTimesHasOneAuthoritativeReceipt() {
        val repository = CaptureStateRepository(FakeClock())
        val id = "11111111-1111-1111-1111-111111111111"
        val results = (0 until 100).map {
            repository.reserve(id, CaptureCommandResult.AcceptedStart(id))
        }
        assertTrue(results.first() is CaptureCommandResult.AcceptedStart)
        assertEquals(99, results.drop(1).count { it is CaptureCommandResult.Duplicate })
    }

    @Test fun stateVersionAdvancesOnEveryAuthoritativeTransition() {
        val repository = CaptureStateRepository(FakeClock())
        val initial = repository.version
        repository.publish(CaptureState.StartRequested("a"))
        repository.publish(CaptureState.OpeningCamera("a"))
        assertEquals(initial + 2, repository.version)
    }

    @Test fun finalizeGuardUsesMonotonicClockAndExpires() {
        val clock = FakeClock(1_000_000_000L)
        val repository = CaptureStateRepository(clock)
        repository.publish(
            CaptureState.Saved(
                LastCapture(
                    uri = "content://media/external/video/media/1",
                    durationMillis = 1,
                    createdAtMillis = 1,
                    id = "22222222-2222-2222-2222-222222222222",
                    sizeBytes = 1,
                ),
                clock.nowNanos(),
            ),
        )
        assertTrue(repository.isAnonymousFinalizeGuardActive("anonymous-a"))
        clock.value += 600_000_000L
        assertTrue(!repository.isAnonymousFinalizeGuardActive("anonymous-a"))
    }

    private class FakeClock(var value: Long = 0) : MonotonicClock {
        override fun nowNanos(): Long = value
    }
}
