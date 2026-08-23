package com.alekpeed.lifeos.platform

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.epochMillisAt
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.wakeword.DeviceState
import com.alekpeed.lifeos.wakeword.blockReason
import com.alekpeed.lifeos.wakeword.loadWakeGates
import com.alekpeed.lifeos.wakeword.nextHoursFlip
import com.alekpeed.lifeos.wakeword.wakeDecision
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
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
//
// Which is why the service is GATED (§7 D-2). Because no third-party app can reach the
// hotword DSP, the only lever on battery is when it listens, not how — so the mic and
// the decoder run only while the gates in wakeword/Gating.kt are open, and the service
// otherwise sits as a notification that says why it is quiet. Screen and power changes
// arrive as broadcasts; the hours boundary is one alarm rather than a poll.
//
// The Vosk model stays loaded across a closed gate on purpose. D-2 diagnosed the drain
// as holding the microphone open on the CPU, not the model — and reloading 40 MB from
// disk every time the screen turns on would be its own cost, several times an hour.
class WakeWordService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var speakerModel: SpeakerModel? = null
    private var speech: SpeechService? = null
    @Volatile private var running = false
    @Volatile private var modelReady = false

    // Screen on/off and plug in/out are the two events that move the power gate, and
    // neither can be declared in the manifest — a dynamically registered receiver on a
    // running service is the only way to hear them, which suits: when the service is
    // not running there is nothing to gate.
    private val gateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { applyGate() }
    }
    private var receiverRegistered = false

    private val phrase: String
        get() = Storage.read("WakePhrase")?.trim()?.ifBlank { null } ?: DEFAULT_PHRASE

    // Gate captures on the owner's voice only when the toggle is on AND a voiceprint
    // has actually been enrolled — otherwise there's nothing to verify against.
    private fun voiceGateActive(): Boolean = VoiceId.isOnlyMyVoiceEnabled() && VoiceId.hasVoiceprint()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Set here as well as in onStartCommand: a gate broadcast can arrive before the
        // start command has run, and applyGate reads settings out of the store.
        Storage.appContext = applicationContext
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(ACTION_GATE_TICK)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(gateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(gateReceiver, filter)
        }
        receiverRegistered = true
    }

    // Re-entrant on purpose: Settings restarts the service after a gate changes, so
    // this runs again on an already-running service and simply re-evaluates.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Storage.appContext = applicationContext
        running = true
        startInForeground(statusText())
        if (modelReady) applyGate() else prepareModel()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        cancelFlipAlarm()
        if (receiverRegistered) {
            try { unregisterReceiver(gateReceiver) } catch (e: Exception) {}
            receiverRegistered = false
        }
        stopRecognition()
        try { model?.close() } catch (e: Exception) {}
        model = null
        try { speakerModel?.close() } catch (e: Exception) {}
        speakerModel = null
        super.onDestroy()
    }

    // Fetch the model if needed (off the main thread), then hand back to the gate.
    // Vosk's SpeechService owns its own audio thread; we just create it.
    private fun prepareModel() {
        val ctx = applicationContext
        val needSpeaker = voiceGateActive()
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
            // If "only my voice" is active, make sure the speaker model is present too.
            if (needSpeaker) {
                VoskModels.ensureSpeakerModel(ctx) { pct ->
                    main.post { if (running) updateNotification("Preparing voiceprint model… $pct%") }
                }
            }
            modelReady = true
            main.post { if (running) applyGate() }
        }.also { it.isDaemon = true }.start()
    }

    // ---- the gate ---------------------------------------------------------------

    private fun nowTime() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

    private fun deviceState(): DeviceState {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        // isInteractive is "the screen is on and not dozing", which is the question.
        val screenOn = pm?.isInteractive ?: true
        // The battery-changed broadcast is sticky, so reading it costs a lookup rather
        // than a subscription.
        val battery = runCatching { registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return DeviceState(charging = plugged != 0, screenOn = screenOn)
    }

    // The one place that decides whether the microphone is open. Everything else —
    // broadcasts, the alarm, a settings change — comes through here.
    private fun applyGate() {
        if (!running) return
        val gates = loadWakeGates()
        val decision = wakeDecision(gates, deviceState(), nowTime())
        scheduleFlipAlarm(nextHoursFlip(gates, nowTime()))
        if (decision.listening) {
            if (modelReady && speech == null) startRecognition(applicationContext)
            if (speech != null) updateNotification(statusText())
        } else {
            stopRecognition()
            updateNotification(blockReason(decision))
        }
    }

    // The hours boundary is a known future moment, so it gets one alarm rather than a
    // timer that wakes the CPU to ask what time it is.
    private fun scheduleFlipAlarm(at: kotlinx.datetime.LocalTime?) {
        val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (at == null) { cancelFlipAlarm(); return }
        val now = nowTime()
        val date = if (at > now) today() else today().plusDays(1)
        runCatching {
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                epochMillisAt(date, at.hour, at.minute),
                flipIntent(),
            )
        }
    }

    private fun cancelFlipAlarm() {
        val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(flipIntent()) }
    }

    // Broadcast rather than a service start: the service is already alive whenever this
    // matters, and starting a foreground service from a background alarm is restricted
    // on modern Android. If the service is gone, nothing is listening for it — which is
    // the correct outcome.
    private fun flipIntent(): PendingIntent {
        val intent = Intent(ACTION_GATE_TICK).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(this, GATE_ALARM_ID, intent, flags)
    }

    // Closing the gate stops the microphone and the decoder — the whole of the drain
    // D-2 identified. The model stays in memory; see the note at the top.
    private fun stopRecognition() {
        val svc = speech ?: return
        speech = null
        runCatching { svc.stop() }
        runCatching { svc.shutdown() }
    }

    private fun startRecognition(ctx: Context) {
        try {
            val m = model ?: Model(VoskModels.modelDir(ctx).absolutePath).also { model = it }
            val recognizer = Recognizer(m, SAMPLE_RATE)
            // Attach the speaker model so results carry an "spk" voiceprint we can
            // check against the enrolled owner. Only when the gate is active and the
            // speaker model actually unpacked.
            if (voiceGateActive() && VoskModels.isSpeakerReady(ctx)) {
                try {
                    val sm = speakerModel ?: SpeakerModel(VoskModels.speakerDir(ctx).absolutePath).also { speakerModel = it }
                    recognizer.setSpeakerModel(sm)
                } catch (e: Exception) {
                    // Fall back to plain recognition if the speaker model won't load.
                }
            }
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
        if (after.length < 2) return
        // "Only my voice": reject the capture unless this utterance's voiceprint
        // matches the enrolled owner. If the gate is off, this is a no-op.
        if (voiceGateActive()) {
            val spk = VoiceId.extractSpk(hypothesis)
            if (!VoiceId.matchesOwner(spk)) return
        }
        capture(after)
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
        // Route through the Ideas model so the note appends as a real record and
        // doesn't clobber the JSON blob.
        com.alekpeed.lifeos.ideas.appendIdea(text)
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
        private const val GATE_ALARM_ID = 7802
        // Private to the app: the alarm's only job is to poke this service's own
        // receiver when the listening window opens or closes.
        const val ACTION_GATE_TICK = "com.alekpeed.lifeos.WAKE_GATE_TICK"
        private val WS = Regex("\\s+")
    }
}
