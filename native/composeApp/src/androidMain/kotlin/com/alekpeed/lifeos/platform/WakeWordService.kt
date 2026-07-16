package com.alekpeed.lifeos.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.alekpeed.lifeos.Storage
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

// Always-on wake word, powered by Vosk (offline, on-device). Unlike the system
// SpeechRecognizer, this runs one continuous lightweight decoder instead of a
// spin-up/tear-down loop — no network, no restart gaps, much lighter on battery.
// When it hears the wake phrase it captures whatever follows into Ideas. The model
// is fetched once on first enable (VoskModels), so the very first start may show a
// brief "preparing" state.
//
// This is still software hotword spotting on the CPU, not the phone's dedicated
// low-power hotword chip (that's reserved for the system assistant) — see the
// handoff doc's wake-word notes.
class WakeWordService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var speech: SpeechService? = null
    @Volatile private var running = false

    private val phrase: String
        get() = Storage.read("WakePhrase")?.trim()?.ifBlank { null } ?: DEFAULT_PHRASE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Storage.appContext = applicationContext
        running = true
        startInForeground(statusText())
        prepareAndStart()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        try { speech?.stop(); speech?.shutdown() } catch (e: Exception) {}
        speech = null
        try { model?.close() } catch (e: Exception) {}
        model = null
        super.onDestroy()
    }

    // Fetch the model if needed (off the main thread), then start listening on the
    // main thread. Vosk's SpeechService owns its own audio thread; we just create it.
    private fun prepareAndStart() {
        val ctx = applicationContext
        Thread {
            val ok = VoskModels.ensureModel(ctx) { pct ->
                main.post { if (running) updateNotification("Preparing voice model… $pct%") }
            }
            if (!ok) {
                main.post {
                    if (running) { updateNotification("Voice model unavailable — tap to retry later"); stopSelf() }
                }
                return@Thread
            }
            main.post { if (running) startRecognition(ctx) }
        }.also { it.isDaemon = true }.start()
    }

    private fun startRecognition(ctx: Context) {
        try {
            val m = Model(VoskModels.modelDir(ctx).absolutePath)
            model = m
            val recognizer = Recognizer(m, SAMPLE_RATE)
            val svc = SpeechService(recognizer, SAMPLE_RATE)
            speech = svc
            svc.startListening(listener)
            updateNotification(statusText())
        } catch (e: Exception) {
            // Mic permission missing, audio device busy, etc. — fail quietly.
            updateNotification("Voice listening unavailable")
            stopSelf()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {}
        override fun onResult(hypothesis: String?) { handle(hypothesis) }
        override fun onFinalResult(hypothesis: String?) { handle(hypothesis) }
        override fun onError(e: Exception?) {}
        override fun onTimeout() {}
    }

    private fun handle(hypothesis: String?) {
        val text = hypothesis?.let {
            try { JSONObject(it).optString("text") } catch (e: Exception) { null }
        }?.trim().orEmpty()
        if (text.isEmpty()) return
        val after = extractAfterPhrase(text, phrase) ?: return
        if (after.length >= 2) capture(after)
    }

    // Whole-word match: returns the words that follow the wake phrase (possibly
    // empty if the phrase was said alone), or null if the phrase wasn't heard.
    // Word-boundary matching means "belief" / "wildlife" can't trip a "life" phrase.
    private fun extractAfterPhrase(text: String, phrase: String): String? {
        val words = text.lowercase().split(WS).filter { it.isNotBlank() }
        val p = phrase.lowercase().split(WS).filter { it.isNotBlank() }
        if (p.isEmpty() || words.size < p.size) return null
        for (i in 0..words.size - p.size) {
            if (p.indices.all { words[i + it] == p[it] }) {
                return words.subList(i + p.size, words.size).joinToString(" ").trim()
            }
        }
        return null
    }

    private fun capture(text: String) {
        val clean = text.trim().replace("\n", " ")
        if (clean.isEmpty()) return
        val existing = Storage.read("Ideas").orEmpty()
        Storage.write("Ideas", if (existing.isBlank()) clean else "$existing\n$clean")
    }

    private fun statusText(): String = "Say “${phrase} …” to capture a note"

    private fun startInForeground(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Wake word", NotificationManager.IMPORTANCE_LOW))
        }
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try { nm.notify(NOTIF_ID, buildNotification(text)) } catch (e: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder
            .setContentTitle("Life OS is listening")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "lifeos_wakeword"
        private const val NOTIF_ID = 7801
        private const val SAMPLE_RATE = 16000.0f
        private const val DEFAULT_PHRASE = "hey life"
        private val WS = Regex("\\s+")
    }
}
