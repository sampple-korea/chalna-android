package app.chalna.capture.assistant

import android.content.Intent
import android.annotation.SuppressLint
import android.os.Build
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
        if (Build.VERSION.SDK_INT >= 36 &&
            Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
        ) {
            disableSystemInvocationEffect()
        }
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

    @SuppressLint("NewApi")
    private fun disableSystemInvocationEffect() {
        runCatching { setInvocationEffectEnabled(false) }
    }
}
