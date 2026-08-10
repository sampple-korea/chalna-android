package app.chalna.capture.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.chalna.capture.ChalnaApplication
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureTrigger
import java.util.UUID

/**
 * Official ACTION_ASSIST fallback for platform variants that do not expose a
 * VoiceInteractionService as an Assistant role candidate. The system-only component ignores all
 * assist context and dispatches through the same authoritative capture command path.
 */
class AssistFallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(false)
        if (intent?.action == Intent.ACTION_ASSIST) {
            (application as ChalnaApplication).graph.captureCommands.dispatch(
                invocationId = "assist-activity-${UUID.randomUUID()}",
                command = CaptureCommand.TOGGLE,
                trigger = CaptureTrigger.ASSISTANT,
            )
        }
        finishAndRemoveTask()
    }
}
