package app.chalna.capture.assistant

import android.app.Activity
import android.os.Bundle
import app.chalna.capture.capture.CaptureService
import java.util.UUID

class KeyguardCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(false)
        CaptureService.dispatch(this, intent.getStringExtra(CaptureService.EXTRA_INVOCATION_ID) ?: UUID.randomUUID().toString())
        finishAndRemoveTask()
    }
}
