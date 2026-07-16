package com.alekpeed.lifeos.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

// Captures a few seconds of the owner speaking, turns it into a voiceprint, and
// stores it. Runs the recognition + speaker models together so each finalized
// segment yields an "spk" x-vector; we average the vectors we collect and save that
// as the enrolled owner. Everything heavy (model download/load) happens off the main
// thread; status + result callbacks are delivered on the main thread.
object VoiceEnroller {

    private const val SAMPLE_RATE = 16000.0f
    private const val LISTEN_MS = 8000L
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var busy = false

    fun enroll(ctx: Context, onStatus: (String) -> Unit, onResult: (Boolean) -> Unit) {
        if (busy) { main.post { onResult(false) }; return }
        busy = true
        val app = ctx.applicationContext
        Thread {
            main.post { onStatus("Preparing voice models…") }
            val okModel = VoskModels.ensureModel(app) { p -> main.post { onStatus("Downloading recognizer… $p%") } }
            val okSpk = okModel && VoskModels.ensureSpeakerModel(app) { p -> main.post { onStatus("Downloading voiceprint model… $p%") } }
            if (!okSpk) {
                busy = false
                main.post { onStatus("Couldn't set up voice models"); onResult(false) }
                return@Thread
            }
            main.post { if (busy) startCapture(app, onStatus, onResult) }
        }.also { it.isDaemon = true }.start()
    }

    private fun startCapture(ctx: Context, onStatus: (String) -> Unit, onResult: (Boolean) -> Unit) {
        val collected = ArrayList<FloatArray>()
        var model: Model? = null
        var spk: SpeakerModel? = null
        var speech: SpeechService? = null

        fun cleanup() {
            try { speech?.stop(); speech?.shutdown() } catch (e: Exception) {}
            try { model?.close() } catch (e: Exception) {}
            try { spk?.close() } catch (e: Exception) {}
        }

        fun finish() {
            if (!busy) return
            busy = false
            cleanup()
            val print = VoiceId.average(collected)
            if (print != null) {
                VoiceId.saveVoiceprint(print)
                onStatus("Voice enrolled ✓")
                onResult(true)
            } else {
                onStatus("Didn't catch enough — try again in a quiet spot")
                onResult(false)
            }
        }

        try {
            val m = Model(VoskModels.modelDir(ctx).absolutePath); model = m
            val s = SpeakerModel(VoskModels.speakerDir(ctx).absolutePath); spk = s
            val recognizer = Recognizer(m, SAMPLE_RATE).apply { setSpeakerModel(s) }
            val svc = SpeechService(recognizer, SAMPLE_RATE); speech = svc

            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {}
                override fun onResult(hypothesis: String?) {
                    VoiceId.extractSpk(hypothesis)?.let { collected.add(it) }
                    main.post { onStatus("Got ${collected.size} sample(s)…") }
                }
                override fun onFinalResult(hypothesis: String?) {
                    VoiceId.extractSpk(hypothesis)?.let { collected.add(it) }
                    main.post { finish() }
                }
                override fun onError(e: Exception?) { main.post { finish() } }
                override fun onTimeout() { main.post { finish() } }
            }
            svc.startListening(listener)
            onStatus("Say your wake phrase a couple of times…")
            // Stop after a fixed window regardless of silence detection.
            main.postDelayed({ try { speech?.stop() } catch (e: Exception) {}; finish() }, LISTEN_MS)
        } catch (e: Exception) {
            busy = false
            cleanup()
            onStatus("Voice capture unavailable")
            onResult(false)
        }
    }
}
