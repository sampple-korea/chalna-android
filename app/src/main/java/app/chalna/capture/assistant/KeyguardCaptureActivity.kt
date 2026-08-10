package app.chalna.capture.assistant

import android.app.Activity
import android.os.Bundle
import app.chalna.capture.ChalnaApplication
import app.chalna.capture.capture.CaptureService
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureTrigger
import java.util.UUID

class KeyguardCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(false)
        val invocationId =
            intent
                .getStringExtra(CaptureService.EXTRA_INVOCATION_ID)
                ?.takeIf { it.length <= 160 }
                ?: "keyguard-${UUID.randomUUID()}"
        (application as ChalnaApplication).graph.captureCommands.dispatch(
            invocationId,
            CaptureCommand.TOGGLE,
            CaptureTrigger.KEYGUARD,
        )
        finishAndRemoveTask()
    }
}
