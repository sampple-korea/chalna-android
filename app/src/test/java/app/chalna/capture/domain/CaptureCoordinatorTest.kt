package app.chalna.capture.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureCoordinatorTest {
    @Test fun toggleStartsThenDifferentToggleStops() = runTest {
        val engine = Engine()
        val subject = CaptureCoordinator(engine, { 50 })
        assertTrue(subject.dispatch(CaptureRequest("one", CaptureCommand.TOGGLE)) is CaptureState.Recording)
        assertTrue(subject.dispatch(CaptureRequest("two", CaptureCommand.TOGGLE)) is CaptureState.Saved)
        assertEquals(1, engine.starts)
        assertEquals(1, engine.stops)
        assertEquals("content://video/1", subject.lastCapture?.uri)
    }

    @Test fun duplicateInvocationIsIdempotent() = runTest {
        val engine = Engine()
        val subject = CaptureCoordinator(engine)
        subject.dispatch(CaptureRequest("same", CaptureCommand.TOGGLE))
        subject.dispatch(CaptureRequest("same", CaptureCommand.TOGGLE))
        assertEquals(1, engine.starts)
        assertEquals(0, engine.stops)
    }

    @Test fun simultaneousStopAndToggleDoesNotRestartAfterFinalize() = runTest {
        var now = 1_000L
        val engine = Engine()
        val subject = CaptureCoordinator(engine, { now })
        subject.dispatch(CaptureRequest("start", CaptureCommand.TOGGLE))
        subject.dispatch(CaptureRequest("notification-stop", CaptureCommand.STOP))
        subject.dispatch(CaptureRequest("assistant-near-stop", CaptureCommand.TOGGLE))
        assertEquals(1, engine.starts)
        now += 751
        subject.dispatch(CaptureRequest("intentional-later-start", CaptureCommand.TOGGLE))
        assertEquals(2, engine.starts)
    }

    @Test fun blankInvocationIgnored() = runTest {
        val engine = Engine()
        val subject = CaptureCoordinator(engine)
        assertEquals(CaptureState.Idle, subject.dispatch(CaptureRequest("", CaptureCommand.TOGGLE)))
    }

    @Test fun engineFailureBecomesFailedState() = runTest {
        val subject = CaptureCoordinator(object : CaptureEngine {
            override suspend fun start(invocationId: String): Long = error("camera")
            override suspend fun stop(): LastCapture? = null
        })
        assertEquals(CaptureState.Failed("camera"), subject.dispatch(CaptureRequest("x", CaptureCommand.TOGGLE)))
    }

    private class Engine : CaptureEngine {
        var starts = 0
        var stops = 0
        override suspend fun start(invocationId: String): Long { starts++; return 10 }
        override suspend fun stop(): LastCapture { stops++; return LastCapture("content://video/1", 10, 10) }
    }
}
