package app.chalna.capture.assistant

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import app.chalna.capture.ChalnaApplication
import app.chalna.capture.capture.CaptureService
import app.chalna.capture.capture.CaptureTelemetryRegistry
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureTrigger

class ChalnaVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        setDisabledShowContext(
            VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
        ) {
            disableSystemInvocationEffect()
        }
    }

    override fun onPrepareToShowSession(
        args: Bundle,
        flags: Int,
    ) {
        super.onPrepareToShowSession(args, flags)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val sessionKey = AssistantInvocationRegistry.sessionKey(args) ?: return
        val invocationId = AssistantInvocationRegistry.invocationId(args)
        CaptureTelemetryRegistry.mark(invocationId, "invocation_received")
        val result =
            (application as ChalnaApplication).graph.captureCommands.dispatch(
                invocationId,
                CaptureCommand.TOGGLE,
                CaptureTrigger.ASSISTANT,
            )
        AssistantInvocationRegistry.put(sessionKey, AssistantInvocation(invocationId, result))
    }

    override fun onShowSessionFailed(args: Bundle) {
        val key = AssistantInvocationRegistry.sessionKey(args)
        val invocation = key?.let(AssistantInvocationRegistry::take)
        invocation?.let { CaptureTelemetryRegistry.mark(it.id, "session_show_failed") }
        super.onShowSessionFailed(args)
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        val id = "keyguard-${java.util.UUID.randomUUID()}"
        startActivity(
            Intent(this, KeyguardCaptureActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                ).putExtra(CaptureService.EXTRA_INVOCATION_ID, id),
        )
    }

    @SuppressLint("NewApi")
    private fun disableSystemInvocationEffect() {
        runCatching { setInvocationEffectEnabled(false) }
    }
}
