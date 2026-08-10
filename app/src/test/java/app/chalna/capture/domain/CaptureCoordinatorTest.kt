package app.chalna.capture.domain

import app.chalna.capture.capture.CaptureActorEffects
import app.chalna.capture.capture.CaptureCommandActor
import app.chalna.capture.capture.CaptureStateRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureCoordinatorTest {
    @Test fun serializedStartThenStopFinalizesExactlyOnce() = runTest {
        val engine = FakeEngine()
        val fixture = actor(engine)
        fixture.actor.submit(request("11111111-1111-1111-1111-111111111111", CaptureCommand.START))
        advanceUntilIdle()
        assertTrue(fixture.states.state.value is CaptureState.Recording)
        fixture.actor.submit(request("22222222-2222-2222-2222-222222222222", CaptureCommand.STOP))
        advanceUntilIdle()
        assertTrue(fixture.states.state.value is CaptureState.Saved)
        assertEquals(1, engine.starts)
        assertEquals(1, engine.stops)
        assertEquals(1, fixture.persisted.size)
    }

    @Test fun secondInvocationDuringStartingCancelsAndReleasesCamera() = runTest {
        val engine = FakeEngine(blockStart = true)
        val fixture = actor(engine)
        fixture.actor.submit(request("11111111-1111-1111-1111-111111111111", CaptureCommand.START))
        advanceUntilIdle()
        fixture.actor.submit(request("22222222-2222-2222-2222-222222222222", CaptureCommand.CANCEL_START))
        advanceUntilIdle()
        assertEquals(CaptureState.Idle, fixture.states.state.value)
        assertEquals(1, engine.cancels)
        assertEquals(0, engine.stops)
    }

    @Test fun cancellationExceptionIsNotConvertedToFailure() = runTest {
        val engine = FakeEngine(blockStart = true)
        val fixture = actor(engine)
        fixture.actor.submit(request("11111111-1111-1111-1111-111111111111", CaptureCommand.START))
        advanceUntilIdle()
        fixture.actor.submit(request("22222222-2222-2222-2222-222222222222", CaptureCommand.CANCEL_START))
        advanceUntilIdle()
        assertTrue(fixture.states.state.value !is CaptureState.Failed)
    }

    private fun kotlinx.coroutines.test.TestScope.actor(engine: FakeEngine): Fixture {
        val states = CaptureStateRepository(FakeMonotonicClock())
        val persisted = mutableListOf<LastCapture>()
        val actor = CaptureCommandActor(
            scope = this,
            states = states,
            engine = engine,
            settingsSnapshot = { CaptureSettings(audioEnabled = false) },
            preflight = CapturePreflight { null },
            epochClock = object : EpochClock { override fun nowMillis() = 1_700_000_000_000L },
            monotonicClock = FakeMonotonicClock(),
            effects = object : CaptureActorEffects {
                override suspend fun onState(state: CaptureState) = Unit
                override suspend fun persist(capture: LastCapture): Boolean {
                    persisted += capture
                    return true
                }
            },
        )
        return Fixture(actor, states, persisted)
    }

    private data class Fixture(
        val actor: CaptureCommandActor,
        val states: CaptureStateRepository,
        val persisted: MutableList<LastCapture>,
    )

    private class FakeEngine(private val blockStart: Boolean = false) : CaptureEngine {
        var starts = 0
        var stops = 0
        var cancels = 0

        override suspend fun start(
            request: EngineStartRequest,
            onStage: (CaptureState) -> Unit,
            onProgress: (CaptureProgress) -> Unit,
        ): CaptureStart {
            starts++
            onStage(CaptureState.OpeningCamera(request.invocationId))
            if (blockStart) awaitCancellation()
            onStage(CaptureState.StartingRecorder(request.invocationId))
            return CaptureStart(request.invocationId, request.createdAtEpochMillis, 1_000_000L)
        }

        override suspend fun stop(): LastCapture {
            stops++
            return validCapture()
        }

        override suspend fun cancelStart(): LastCapture? {
            cancels++
            return null
        }

        override suspend fun release() = Unit
    }

    private class FakeMonotonicClock : MonotonicClock {
        private var value = 1_000_000L
        override fun nowNanos(): Long = value.also { value += 1_000_000L }
    }

    private fun request(id: String, command: CaptureCommand) = CaptureRequest(
        invocationId = id,
        command = command,
        trigger = CaptureTrigger.ASSISTANT,
        receivedElapsedNanos = 1,
    )

    private fun validCapture() = LastCapture(
        uri = "content://media/external/video/media/1",
        durationMillis = 1_000,
        createdAtMillis = 1_700_000_000_000L,
        id = "33333333-3333-3333-3333-333333333333",
        displayName = "CHALNA_test.mp4",
        sizeBytes = 1_024,
    )
}
