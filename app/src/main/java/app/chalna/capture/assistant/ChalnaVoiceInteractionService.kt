package app.chalna.capture.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionService
import app.chalna.capture.capture.CaptureService
import java.util.UUID

class ChalnaVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        setDisabledShowContext(
            VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT,
        )
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        startActivity(
            Intent(this, KeyguardCaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra(CaptureService.EXTRA_INVOCATION_ID, "keyguard-${UUID.randomUUID()}"),
        )
    }

    override fun onPrepareToShowSession(args: Bundle, flags: Int) {
        super.onPrepareToShowSession(args, flags)
    }
}
