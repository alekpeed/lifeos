package com.alekpeed.lifeos.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.alekpeed.lifeos.Storage

// Always-on wake word. A foreground service that runs the on-device recognizer in a
// restart loop; when it hears the trigger word it captures whatever follows into
// Ideas. This is a working scaffold — continuous SpeechRecognizer use is battery-
// and OEM-sensitive, so real-world tuning (trigger phrase, duty cycle, a lighter
// hotword engine) is on-device follow-up work.
class WakeWordService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Storage.appContext = applicationContext
        startInForeground()
        running = true
        startListening()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        recognizer = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "lifeos_wakeword"
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Wake word", NotificationManager.IMPORTANCE_LOW))
        }
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        val n = builder
            .setContentTitle("Life OS is listening")
            .setContentText("Say “life …” to capture a note")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7801, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(7801, n)
        }
    }

    private fun startListening() {
        if (!running) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            restart()
        }
    }

    private fun restart() {
        if (!running) return
        handler.postDelayed({ startListening() }, 500)
    }

    private fun handleText(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val heard = matches.firstOrNull()?.lowercase() ?: return
        val trigger = "life"
        val idx = heard.indexOf(trigger)
        if (idx >= 0) {
            val after = heard.substring(idx + trigger.length).trim().trimStart(' ', ',', '.')
            if (after.length >= 2) capture(after)
        }
    }

    private fun capture(text: String) {
        val clean = text.trim().replace("\n", " ")
        if (clean.isEmpty()) return
        val existing = Storage.read("Ideas").orEmpty()
        Storage.write("Ideas", if (existing.isBlank()) clean else "$existing\n$clean")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) { restart() }
        override fun onResults(results: Bundle?) { handleText(results); restart() }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
