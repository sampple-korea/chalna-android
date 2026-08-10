package app.chalna.capture.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Declared because Android's Assistant role requires a valid recognition-service component.
 * Chalna never performs speech recognition, so every request is rejected without opening audio.
 */
class ChalnaRecognitionService : RecognitionService() {
    override fun onStartListening(
        recognizerIntent: Intent,
        listener: Callback,
    ) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback) = Unit
}
