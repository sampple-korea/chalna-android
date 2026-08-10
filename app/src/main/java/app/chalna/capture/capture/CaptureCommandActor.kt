package app.chalna.capture.capture

import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureFailure
import app.chalna.capture.domain.CaptureFailureCode
import app.chalna.capture.domain.CapturePreflight
import app.chalna.capture.domain.CaptureRequest
import app.chalna.capture.domain.CaptureSettings
import app.chalna.capture.domain.CaptureSessionSettings
import app.chalna.capture.domain.CaptureStart
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.EngineStartRequest
import app.chalna.capture.domain.EpochClock
import app.chalna.capture.domain.LastCapture
import app.chalna.capture.domain.MonotonicClock
import app.chalna.capture.domain.toCaptureFailure
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface CaptureActorEffects {
    suspend fun onState(state: CaptureState)
    suspend fun persist(capture: LastCapture): Boolean
}

class CaptureCommandActor(
    private val scope: CoroutineScope,
    private val states: CaptureStateRepository,
    private val engine: app.chalna.capture.domain.CaptureEngine,
    private val settingsSnapshot: suspend () -> CaptureSettings,
    private val preflight: CapturePreflight,
    private val epochClock: EpochClock,
    private val monotonicClock: MonotonicClock,
    private val effects: CaptureActorEffects,
) {
    private sealed interface Message {
        data class Command(val request: CaptureRequest) : Message
        data class Stage(val state: CaptureState) : Message
        data class Started(val request: CaptureRequest, val start: CaptureStart) : Message
        data class StartFailed(val request: CaptureRequest, val failure: CaptureFailure) : Message
        data class StartCancelled(val request: CaptureRequest, val shortCapture: LastCapture?) : Message
        data class Progress(val durationNanos: Long, val bytes: Long, val storageCritical: Boolean) : Message
        data class Finalized(val request: CaptureRequest, val capture: LastCapture) : Message
        data class StopFailed(val request: CaptureRequest, val failure: CaptureFailure) : Message
        data class Persisted(val capture: LastCapture, val indexed: Boolean) : Message
        data object Destroy : Message
    }

    private val channel = Channel<Message>(Channel.UNLIMITED)
    private val closed = AtomicBoolean(false)
    private var operationJob: Job? = null
    private var activeRequest: CaptureRequest? = null
    private var cancelRequested = false
    private var storageStopIssued = false
    private val consumer = scope.launch {
        for (message in channel) handle(message)
    }

    fun submit(request: CaptureRequest) {
        if (!closed.get()) channel.trySend(Message.Command(request))
    }

    suspend fun destroy() {
        if (!closed.compareAndSet(false, true)) return
        channel.send(Message.Destroy)
        consumer.join()
    }

    private suspend fun handle(message: Message) {
        when (message) {
            is Message.Command -> handleCommand(message.request)
            is Message.Stage -> if (states.state.value !is CaptureState.CancelRequested) transition(message.state)
            is Message.Started -> handleStarted(message)
            is Message.StartFailed -> fail(message.failure)
            is Message.StartCancelled -> handleCancelled(message)
            is Message.Progress -> handleProgress(message)
            is Message.Finalized -> persist(message.capture)
            is Message.StopFailed -> fail(message.failure)
            is Message.Persisted -> saved(message.capture, message.indexed)
            Message.Destroy -> {
                operationJob?.cancel()
                engine.release()
                channel.close()
            }
        }
    }

    private suspend fun handleCommand(request: CaptureRequest) {
        CaptureTelemetryRegistry.mark(request.invocationId, "command_started")
        val resolved = when (request.command) {
            CaptureCommand.TOGGLE, CaptureCommand.QUICK_TILE_TOGGLE -> when (states.state.value) {
                CaptureState.Idle, is CaptureState.Failed, is CaptureState.Saved -> CaptureCommand.START
                is CaptureState.StartRequested, is CaptureState.StartingForeground,
                is CaptureState.OpeningCamera, is CaptureState.StartingRecorder -> CaptureCommand.CANCEL_START
                is CaptureState.Recording -> CaptureCommand.STOP
                is CaptureState.CancelRequested, is CaptureState.StopRequested,
                is CaptureState.StoppingRecorder, is CaptureState.Finalizing -> CaptureCommand.STOP
                is CaptureState.Persisting, is CaptureState.Recovering -> return
            }
            else -> request.command
        }
        when (resolved) {
            CaptureCommand.START -> start(request)
            CaptureCommand.CANCEL_START -> cancelStart(request)
            CaptureCommand.STOP, CaptureCommand.AUTO_STOP, CaptureCommand.NOTIFICATION_STOP -> stop(request)
            CaptureCommand.RECOVERY -> Unit
            CaptureCommand.SERVICE_DESTROYED -> handle(Message.Destroy)
            CaptureCommand.TOGGLE, CaptureCommand.QUICK_TILE_TOGGLE -> error("Unresolved toggle")
        }
    }

    private suspend fun start(request: CaptureRequest) {
        if (states.state.value !is CaptureState.Idle && states.state.value !is CaptureState.Failed &&
            states.state.value !is CaptureState.Saved
        ) return
        val snapshot = CaptureSessionSettings.snapshot(settingsSnapshot())
        val failed = preflight.check(snapshot)
        if (failed != null) {
            fail(failed)
            return
        }
        activeRequest = request
        cancelRequested = false
        storageStopIssued = false
        transition(CaptureState.StartRequested(request.invocationId))
        transition(CaptureState.StartingForeground(request.invocationId))
        operationJob = scope.launch {
            try {
                val start = engine.start(
                    EngineStartRequest(request.invocationId, snapshot, epochClock.nowMillis()),
                    onStage = { channel.trySend(Message.Stage(it)) },
                    onProgress = { progress ->
                        channel.trySend(
                            Message.Progress(
                                progress.recordedDurationNanos,
                                progress.bytesRecorded,
                                progress.storageCritical,
                            ),
                        )
                    },
                )
                channel.send(Message.Started(request, start))
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    val short = try {
                        engine.cancelStart()
                    } catch (_: Exception) {
                        null
                    }
                    channel.send(Message.StartCancelled(request, short))
                }
                throw cancellation
            } catch (failure: Exception) {
                channel.send(Message.StartFailed(request, failure.toCaptureFailure(CaptureFailureCode.RECORDER_START)))
            }
        }
    }

    private suspend fun cancelStart(request: CaptureRequest) {
        when (states.state.value) {
            is CaptureState.StartRequested, is CaptureState.StartingForeground,
            is CaptureState.OpeningCamera, is CaptureState.StartingRecorder -> {
                cancelRequested = true
                transition(CaptureState.CancelRequested(request.invocationId))
                operationJob?.cancel()
            }
            is CaptureState.Recording -> stop(request)
            else -> Unit
        }
    }

    private suspend fun stop(request: CaptureRequest) {
        when (states.state.value) {
            is CaptureState.Recording -> launchStop(request)
            is CaptureState.StartRequested, is CaptureState.StartingForeground,
            is CaptureState.OpeningCamera, is CaptureState.StartingRecorder -> cancelStart(request)
            else -> Unit
        }
    }

    private suspend fun handleStarted(message: Message.Started) {
        val request = message.request
        transition(
            CaptureState.Recording(
                request.invocationId,
                message.start.startedAtEpochMillis,
                message.start.startedAtElapsedNanos,
            ),
        )
        if (cancelRequested) launchStop(request)
    }

    private suspend fun handleCancelled(message: Message.StartCancelled) {
        val short = message.shortCapture
        if (short != null && short.isUsable()) persist(short) else {
            activeRequest = null
            cancelRequested = false
            transition(CaptureState.Idle)
        }
    }

    private suspend fun launchStop(request: CaptureRequest) {
        transition(CaptureState.StopRequested(request.invocationId))
        transition(CaptureState.StoppingRecorder(request.invocationId))
        transition(CaptureState.Finalizing(request.invocationId))
        operationJob = scope.launch {
            try {
                channel.send(Message.Finalized(request, engine.stop()))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                channel.send(Message.StopFailed(request, failure.toCaptureFailure(CaptureFailureCode.FINALIZE)))
            }
        }
    }

    private suspend fun handleProgress(message: Message.Progress) {
        val current = states.state.value as? CaptureState.Recording ?: return
        if (message.storageCritical && !storageStopIssued) {
            storageStopIssued = true
            launchStop(
                CaptureRequest(
                    invocationId = "storage-${current.invocationId}",
                    command = CaptureCommand.AUTO_STOP,
                    trigger = app.chalna.capture.domain.CaptureTrigger.SYSTEM,
                    receivedElapsedNanos = monotonicClock.nowNanos(),
                ),
            )
        }
    }

    private suspend fun persist(capture: LastCapture) {
        transition(CaptureState.Persisting(capture))
        operationJob = scope.launch {
            val indexed = try {
                effects.persist(capture)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
            channel.send(Message.Persisted(capture, indexed))
        }
    }

    private suspend fun saved(capture: LastCapture, indexed: Boolean) {
        val finalCapture = if (indexed) capture else capture.copy(state = app.chalna.capture.domain.CaptureRecordState.METADATA_PENDING)
        transition(CaptureState.Saved(finalCapture, monotonicClock.nowNanos()))
        activeRequest = null
        cancelRequested = false
    }

    private suspend fun fail(failure: CaptureFailure) {
        transition(CaptureState.Failed(failure))
        activeRequest = null
        cancelRequested = false
    }

    private suspend fun transition(state: CaptureState) {
        states.publish(state)
        effects.onState(state)
    }
}
