package app.chalna.capture.capture

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.chalna.capture.data.SettingsStore
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureCommandResult
import app.chalna.capture.domain.CaptureFailure
import app.chalna.capture.domain.CaptureFailureCode
import app.chalna.capture.domain.CaptureRequest
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.CaptureTrigger
import app.chalna.capture.domain.MonotonicClock
import app.chalna.capture.notifications.CaptureNotifications

interface CaptureCommandDispatcher {
    fun dispatch(
        invocationId: String,
        command: CaptureCommand,
        trigger: CaptureTrigger,
    ): CaptureCommandResult
}

class AndroidCaptureCommandDispatcher(
    context: Context,
    private val states: CaptureStateRepository,
    private val settings: SettingsStore,
    private val monotonicClock: MonotonicClock = AndroidMonotonicClock(),
) : CaptureCommandDispatcher {
    private val appContext = context.applicationContext

    private enum class ProjectionKind { STARTING, STOPPING }

    private data class Projection(
        val kind: ProjectionKind,
        val stateVersion: Long,
    )

    private var projection: Projection? = null

    override fun dispatch(
        invocationId: String,
        command: CaptureCommand,
        trigger: CaptureTrigger,
    ): CaptureCommandResult =
        synchronized(this) {
            val normalizedId = invocationId.takeIf(::validInvocationId) ?: return@synchronized rejectInvalid(invocationId)
            states.receipt(normalizedId)?.let { return@synchronized CaptureCommandResult.Duplicate(normalizedId, it) }
            val actualState = states.state.value
            val currentVersion = states.version
            val activeProjection = projection?.takeIf { it.stateVersion == currentVersion }
            if (activeProjection == null) projection = null
            val resolved = resolve(normalizedId, command, actualState, activeProjection?.kind)
            val permissionFailure = if (resolved is CaptureCommandResult.AcceptedStart) permissionFailure(normalizedId) else null
            if (permissionFailure != null) return@synchronized states.reserve(normalizedId, permissionFailure)
            val reserved = states.reserve(normalizedId, resolved)
            if (reserved is CaptureCommandResult.Duplicate) return@synchronized reserved
            projection =
                when (resolved) {
                    is CaptureCommandResult.AcceptedStart -> Projection(ProjectionKind.STARTING, currentVersion)
                    is CaptureCommandResult.AcceptedStop, is CaptureCommandResult.AcceptedCancelStart ->
                        Projection(ProjectionKind.STOPPING, currentVersion)
                    else -> projection
                }
            if (!resolved.requiresServiceDelivery()) {
                if (resolved is CaptureCommandResult.NoActiveCapture && trigger == CaptureTrigger.NOTIFICATION) {
                    appContext
                        .getSystemService(NotificationManager::class.java)
                        .cancel(CaptureNotifications.ACTIVE_NOTIFICATION_ID)
                }
                return@synchronized resolved
            }
            val request = CaptureRequest(normalizedId, command, trigger, monotonicClock.nowNanos())
            return@synchronized try {
                CaptureTelemetryRegistry.mark(normalizedId, "command_enqueued")
                appContext.startForegroundService(CaptureService.intent(appContext, request))
                resolved
            } catch (failure: RuntimeException) {
                projection = null
                val rejected =
                    CaptureCommandResult.FailedToDispatch(
                        normalizedId,
                        CaptureFailure(
                            code =
                                if (Build.VERSION.SDK_INT >= 31 &&
                                    failure.javaClass.simpleName == "ForegroundServiceStartNotAllowedException"
                                ) {
                                    CaptureFailureCode.FOREGROUND_START_NOT_ALLOWED
                                } else {
                                    CaptureFailureCode.DISPATCH
                                },
                            recoverable = true,
                            diagnostic = failure.javaClass.simpleName,
                        ),
                    )
                states.updateReceipt(normalizedId, rejected)
                rejected
            }
        }

    private fun resolve(
        id: String,
        requested: CaptureCommand,
        state: CaptureState,
        projected: ProjectionKind?,
    ): CaptureCommandResult {
        val projectedStarting =
            projected == ProjectionKind.STARTING &&
                (state is CaptureState.Idle || state is CaptureState.Saved || state is CaptureState.Failed)
        val command =
            when (requested) {
                CaptureCommand.TOGGLE, CaptureCommand.QUICK_TILE_TOGGLE ->
                    when {
                        projectedStarting -> CaptureCommand.CANCEL_START
                        projected == ProjectionKind.STOPPING -> return CaptureCommandResult.AlreadyStopping(id)
                        else ->
                            when (state) {
                                CaptureState.Idle, is CaptureState.Failed, is CaptureState.Saved -> CaptureCommand.START
                                is CaptureState.StartRequested, is CaptureState.StartingForeground,
                                is CaptureState.OpeningCamera, is CaptureState.StartingRecorder,
                                -> CaptureCommand.CANCEL_START
                                is CaptureState.Recording -> CaptureCommand.STOP
                                is CaptureState.CancelRequested, is CaptureState.StopRequested,
                                is CaptureState.StoppingRecorder, is CaptureState.Finalizing,
                                -> CaptureCommand.STOP
                                is CaptureState.Persisting, is CaptureState.Recovering -> return CaptureCommandResult.BusySaving(id)
                            }
                    }
                else -> requested
            }
        return when (command) {
            CaptureCommand.START ->
                if (states.isAnonymousFinalizeGuardActive(id)) {
                    CaptureCommandResult.BusySaving(id)
                } else {
                    CaptureCommandResult.AcceptedStart(id)
                }
            CaptureCommand.CANCEL_START -> CaptureCommandResult.AcceptedCancelStart(id)
            CaptureCommand.STOP, CaptureCommand.AUTO_STOP, CaptureCommand.NOTIFICATION_STOP ->
                when (state) {
                    is CaptureState.Recording -> CaptureCommandResult.AcceptedStop(id)
                    is CaptureState.StartRequested, is CaptureState.StartingForeground,
                    is CaptureState.OpeningCamera, is CaptureState.StartingRecorder,
                    -> CaptureCommandResult.AcceptedCancelStart(id)
                    is CaptureState.CancelRequested, is CaptureState.StopRequested,
                    is CaptureState.StoppingRecorder, is CaptureState.Finalizing,
                    -> CaptureCommandResult.AlreadyStopping(id)
                    is CaptureState.Persisting, is CaptureState.Recovering -> CaptureCommandResult.BusySaving(id)
                    else -> CaptureCommandResult.NoActiveCapture(id)
                }
            CaptureCommand.RECOVERY -> CaptureCommandResult.BusySaving(id)
            CaptureCommand.SERVICE_DESTROYED -> CaptureCommandResult.NoActiveCapture(id)
            CaptureCommand.TOGGLE, CaptureCommand.QUICK_TILE_TOGGLE -> error("Toggle must be resolved atomically")
        }
    }

    private fun permissionFailure(id: String): CaptureCommandResult.Rejected? {
        if (appContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return CaptureCommandResult.Rejected(
                id,
                CaptureFailure(CaptureFailureCode.CAMERA_PERMISSION, true, "camera_permission"),
            )
        }
        if (settings.cachedSnapshotOrNull()?.audioEnabled == true &&
            appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            return CaptureCommandResult.Rejected(
                id,
                CaptureFailure(CaptureFailureCode.MICROPHONE_PERMISSION, true, "microphone_permission"),
            )
        }
        return null
    }

    private fun CaptureCommandResult.requiresServiceDelivery(): Boolean =
        when (this) {
            is CaptureCommandResult.AcceptedStart,
            is CaptureCommandResult.AcceptedStop,
            is CaptureCommandResult.AcceptedCancelStart,
            -> true
            else -> false
        }

    private fun rejectInvalid(raw: String): CaptureCommandResult.FailedToDispatch =
        CaptureCommandResult.FailedToDispatch(
            raw.take(MAX_INVOCATION_ID_LENGTH),
            CaptureFailure(CaptureFailureCode.DISPATCH, false, "invalid_invocation_id"),
        )

    private fun validInvocationId(value: String): Boolean =
        value.length in 1..MAX_INVOCATION_ID_LENGTH && value.all { it.isLetterOrDigit() || it in "-_.:" }

    private companion object {
        const val MAX_INVOCATION_ID_LENGTH = 160
    }
}
