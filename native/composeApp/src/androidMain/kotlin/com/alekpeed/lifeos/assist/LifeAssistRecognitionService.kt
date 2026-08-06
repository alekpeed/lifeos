package com.alekpeed.lifeos.assist

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

// The assistant-role descriptor requires a recognition service to exist. Life OS
// doesn't do speech-to-text here (the wake-word service handles listening
// separately), so this is a minimal, valid stub that declines cleanly — enough to
// satisfy the OS so the assistant registration is accepted.
class LifeAssistRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        try {
            listener?.error(SpeechRecognizer.ERROR_CLIENT)
        } catch (e: Exception) {
        }
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}
