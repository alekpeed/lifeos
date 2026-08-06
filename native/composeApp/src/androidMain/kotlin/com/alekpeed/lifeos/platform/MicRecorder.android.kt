package com.alekpeed.lifeos.platform

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.alekpeed.lifeos.ai.MIC_SAMPLE_RATE
import com.alekpeed.lifeos.ai.wavFromPcm16
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.abs

// Driving the microphone directly, rather than launching the system dictation dialog.
// AudioRecord (not MediaRecorder) because we want raw PCM: no container to unwrap, no
// codec, and the exact 16-bit mono 16 kHz shape the transcription endpoint wants.
//
// A recording is capped so a phone left face-down in a pocket can't quietly fill
// memory with an hour of nothing; the cap is generous enough that no real dictation
// will ever hit it.
internal object MicRecorder {

    private const val MAX_SECONDS = 240
    private val MAX_BYTES = MIC_SAMPLE_RATE * 2 * MAX_SECONDS

    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val pcm = ByteArrayOutputStream()
    @Volatile private var running = false
    @Volatile private var level = 0f

    val isRecording: Boolean get() = running

    fun peak(): Float = level

    @Suppress("MissingPermission")   // the caller checks RECORD_AUDIO before getting here
    fun start(): Boolean {
        if (running) return false
        val minBuf = AudioRecord.getMinBufferSize(
            MIC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false
        val bufSize = maxOf(minBuf * 2, MIC_SAMPLE_RATE)   // ~0.5s of slack

        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
            )
        } catch (e: Exception) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { r.release() }
            return false
        }

        pcm.reset()
        level = 0f
        record = r
        running = true
        try {
            r.startRecording()
        } catch (e: Exception) {
            running = false
            record = null
            runCatching { r.release() }
            return false
        }

        worker = thread(name = "lifeos-mic", isDaemon = true) {
            val buf = ByteArray(4096)
            while (running) {
                val n = try { r.read(buf, 0, buf.size) } catch (e: Exception) { -1 }
                if (n <= 0) break
                synchronized(pcm) {
                    if (pcm.size() < MAX_BYTES) pcm.write(buf, 0, n)
                }
                level = peakOf(buf, n)
            }
        }
        return true
    }

    // Returns the take as a WAV, base64'd, or null if nothing usable was captured.
    fun stop(): String? {
        if (!running) return null
        running = false
        runCatching { worker?.join(600) }
        worker = null
        val r = record
        record = null
        runCatching { r?.stop() }
        runCatching { r?.release() }
        level = 0f

        val bytes = synchronized(pcm) { pcm.toByteArray() }
        pcm.reset()
        // Under ~0.2s is a mis-tap, not speech.
        if (bytes.size < MIC_SAMPLE_RATE / 5 * 2) return null
        return android.util.Base64.encodeToString(wavFromPcm16(bytes), android.util.Base64.NO_WRAP)
    }

    fun cancel() {
        if (!running) return
        running = false
        runCatching { worker?.join(600) }
        worker = null
        val r = record
        record = null
        runCatching { r?.stop() }
        runCatching { r?.release() }
        synchronized(pcm) { pcm.reset() }
        level = 0f
    }

    // Loudest sample in this buffer as 0f..1f, for the level meter.
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
