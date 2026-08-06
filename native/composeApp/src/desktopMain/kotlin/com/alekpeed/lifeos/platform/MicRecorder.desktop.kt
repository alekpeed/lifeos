package com.alekpeed.lifeos.platform

import com.alekpeed.lifeos.ai.MIC_SAMPLE_RATE
import com.alekpeed.lifeos.ai.wavFromPcm16
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.math.abs

// The desktop microphone, via javax.sound.sampled — which is why the desktop build
// can dictate at all now. There is no system dictation dialog on Linux or Windows to
// hand off to, so recording here plus Whisper is the whole feature.
//
// The format is fixed at 16-bit signed little-endian mono 16 kHz to match what gets
// sent for transcription. Any sound card made this century supports it; if the line
// genuinely isn't available (no input device, PulseAudio not running) start() reports
// failure rather than throwing.
internal object MicRecorder {

    private const val MAX_SECONDS = 240
    private val MAX_BYTES = MIC_SAMPLE_RATE * 2 * MAX_SECONDS

    private val format = AudioFormat(MIC_SAMPLE_RATE.toFloat(), 16, 1, true, false)

    private var line: TargetDataLine? = null
    private var worker: Thread? = null
    private val pcm = ByteArrayOutputStream()
    @Volatile private var running = false
    @Volatile private var level = 0f

    val available: Boolean
        get() = runCatching {
            AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, format))
        }.getOrDefault(false)

    fun peak(): Float = level

    fun start(): Boolean {
        if (running) return false
        val info = DataLine.Info(TargetDataLine::class.java, format)
        if (!runCatching { AudioSystem.isLineSupported(info) }.getOrDefault(false)) return false

        val l = runCatching { AudioSystem.getLine(info) as TargetDataLine }.getOrNull() ?: return false
        val opened = runCatching { l.open(format, MIC_SAMPLE_RATE); l.start() }.isSuccess
        if (!opened) {
            runCatching { l.close() }
            return false
        }

        pcm.reset()
        level = 0f
        line = l
        running = true
        worker = thread(name = "lifeos-mic", isDaemon = true) {
            val buf = ByteArray(4096)
            while (running) {
                val n = runCatching { l.read(buf, 0, buf.size) }.getOrDefault(-1)
                if (n <= 0) break
                synchronized(pcm) {
                    if (pcm.size() < MAX_BYTES) pcm.write(buf, 0, n)
                }
                level = peakOf(buf, n)
            }
        }
        return true
    }

    fun stop(): String? {
        if (!running) return null
        running = false
        runCatching { worker?.join(600) }
        worker = null
        val l = line
        line = null
        runCatching { l?.stop() }
        runCatching { l?.close() }
        level = 0f

        val bytes = synchronized(pcm) { pcm.toByteArray() }
        pcm.reset()
        // Under ~0.2s is a mis-click, not speech.
        if (bytes.size < MIC_SAMPLE_RATE / 5 * 2) return null
        return java.util.Base64.getEncoder().encodeToString(wavFromPcm16(bytes))
    }

    fun cancel() {
        if (!running) return
        running = false
        runCatching { worker?.join(600) }
        worker = null
        val l = line
        line = null
        runCatching { l?.stop() }
        runCatching { l?.close() }
        synchronized(pcm) { pcm.reset() }
        level = 0f
    }

    private fun peakOf(buf: ByteArray, n: Int): Float {
        var peak = 0
        var i = 0
        while (i + 1 < n) {
            val s = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
            val v = abs(s.toShort().toInt())
            if (v > peak) peak = v
            i += 2
        }
        return (peak / 32768f).coerceIn(0f, 1f)
    }
}
