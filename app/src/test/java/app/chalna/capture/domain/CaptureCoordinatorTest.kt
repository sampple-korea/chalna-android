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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CaptureCoordinatorTest {
    @Test fun serializedStartThenStopFinalizesExactlyOnce() =
        runTest {
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
            fixture.actor.destroy()
        }

    @Test fun secondInvocationDuringStartingCancelsAndReleasesCamera() =
        runTest {
            val engine = FakeEngine(blockStart = true)
            val fixture = actor(engine)
            fixture.actor.submit(request("11111111-1111-1111-1111-111111111111", CaptureCommand.START))
            advanceUntilIdle()
            fixture.actor.submit(request("22222222-2222-2222-2222-222222222222", CaptureCommand.CANCEL_START))
            advanceUntilIdle()
            assertEquals(CaptureState.Idle, fixture.states.state.value)
            assertEquals(1, engine.cancels)
            assertEquals(0, engine.stops)
            fixture.actor.destroy()
        }

    @Test fun cancellationExceptionIsNotConvertedToFailure() =
        runTest {
            val engine = FakeEngine(blockStart = true)
            val fixture = actor(engine)
            fixture.actor.submit(request("11111111-1111-1111-1111-111111111111", CaptureCommand.START))
            advanceUntilIdle()
            fixture.actor.submit(request("22222222-2222-2222-2222-222222222222", CaptureCommand.CANCEL_START))
            advanceUntilIdle()
            assertTrue(fixture.states.state.value !is CaptureState.Failed)
            fixture.actor.destroy()
        }

    @Test fun simultaneousManualAndNotificationStopsFinalizeOnce() =
        runTest {
            val engine = FakeEngine()
            val fixture = actor(engine)
            fixture.actor.submit(request(id(1), CaptureCommand.START))
            advanceUntilIdle()
            fixture.actor.submit(request(id(2), CaptureCommand.STOP))
            fixture.actor.submit(request(id(3), CaptureCommand.NOTIFICATION_STOP))
            advanceUntilIdle()
            assertTrue(fixture.states.state.value is CaptureState.Saved)
            assertEquals(1, engine.stops)
            fixture.actor.destroy()
        }

    @Test fun oneHundredDistinctAlternatingInvocationsNeverOverlapRecorder() =
        runTest {
            val engine = FakeEngine()
            val fixture = actor(engine)
            repeat(50) { index ->
                fixture.actor.submit(request(id(index * 2), CaptureCommand.TOGGLE))
                advanceUntilIdle()
                fixture.actor.submit(request(id(index * 2 + 1), CaptureCommand.TOGGLE))
                advanceUntilIdle()
            }
            assertEquals(50, engine.starts)
            assertEquals(50, engine.stops)
            assertEquals(1, engine.maximumActiveRecorders)
            fixture.actor.destroy()
        }

    @Test fun engineStartAndStopFailuresBecomeTypedFailedState() =
        runTest {
            val startFailure = actor(FakeEngine(failStart = true))
            startFailure.actor.submit(request(id(1), CaptureCommand.START))
            advanceUntilIdle()
            assertTrue(startFailure.states.state.value is CaptureState.Failed)
            startFailure.actor.destroy()

            val engine = FakeEngine(failStop = true)
            val stopFailure = actor(engine)
            stopFailure.actor.submit(request(id(2), CaptureCommand.START))
            advanceUntilIdle()
            stopFailure.actor.submit(request(id(3), CaptureCommand.STOP))
            advanceUntilIdle()
            assertTrue(stopFailure.states.state.value is CaptureState.Failed)
            assertEquals(1, engine.stops)
            stopFailure.actor.destroy()
        }

    private fun kotlinx.coroutines.test.TestScope.actor(engine: FakeEngine): Fixture {
        val states = CaptureStateRepository(FakeMonotonicClock())
        val persisted = mutableListOf<LastCapture>()
        val actor =
            CaptureCommandActor(
                scope = this,
                states = states,
                engine = engine,
                settingsSnapshot = { CaptureSettings(audioEnabled = false) },
                preflight = CapturePreflight { null },
                epochClock =
                    object : EpochClock {
                        override fun nowMillis() = 1_700_000_000_000L
                    },
                monotonicClock = FakeMonotonicClock(),
                effects =
                    object : CaptureActorEffects {
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

    private inner class FakeEngine(
        private val blockStart: Boolean = false,
        private val failStart: Boolean = false,
        private val failStop: Boolean = false,
    ) : CaptureEngine {
        var starts = 0
        var stops = 0
        var cancels = 0
        var maximumActiveRecorders = 0
        private var activeRecorders = 0

        override suspend fun start(
            request: EngineStartRequest,
            onStage: (CaptureState) -> Unit,
            onProgress: (CaptureProgress) -> Unit,
        ): CaptureStart {
            starts++
            activeRecorders++
            maximumActiveRecorders = maxOf(maximumActiveRecorders, activeRecorders)
            onStage(CaptureState.OpeningCamera(request.invocationId))
            if (blockStart) awaitCancellation()
            if (failStart) {
                activeRecorders--
                error("start failed")
            }
            onStage(CaptureState.StartingRecorder(request.invocationId))
            return CaptureStart(request.invocationId, request.createdAtEpochMillis, 1_000_000L)
        }

        override suspend fun stop(): LastCapture {
            stops++
            if (failStop) error("stop failed")
            activeRecorders--
            return validCapture()
        }

        override suspend fun cancelStart(): LastCapture? {
            cancels++
            activeRecorders = (activeRecorders - 1).coerceAtLeast(0)
            return null
        }

        override suspend fun release() = Unit
    }

    private class FakeMonotonicClock : MonotonicClock {
        private var value = 1_000_000L

        override fun nowNanos(): Long = value.also { value += 1_000_000L }
    }

    private fun request(
        id: String,
        command: CaptureCommand,
    ) = CaptureRequest(
        invocationId = id,
        command = command,
        trigger = CaptureTrigger.ASSISTANT,
        receivedElapsedNanos = 1,
    )

    private fun id(index: Int): String = "%08x-0000-0000-0000-%012x".format(index, index)

    private fun validCapture() =
        LastCapture(
            uri = "content://media/external/video/media/1",
            durationMillis = 1_000,
            createdAtMillis = 1_700_000_000_000L,
            id = "33333333-3333-3333-3333-333333333333",
            displayName = "CHALNA_test.mp4",
            sizeBytes = 1_024,
        )
}
