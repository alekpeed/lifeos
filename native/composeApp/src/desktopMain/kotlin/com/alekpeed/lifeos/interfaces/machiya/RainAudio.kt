package com.alekpeed.lifeos.interfaces.machiya

import java.util.Random
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.max
import kotlin.math.min

internal class RainAudio {
    @Volatile private var running = false
    private var line: SourceDataLine? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ playLoop() }, "lifeos-machiya-rain").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        line?.stop()
        line?.close()
        line = null
        thread = null
    }

    private fun playLoop() {
        val format = AudioFormat(44_100f, 16, 1, true, false)
        val output = runCatching {
            AudioSystem.getSourceDataLine(format).also {
                it.open(format, 8_820)
                it.start()
            }
        }.getOrNull() ?: run { running = false; return }
        line = output

        val random = Random()
        val bytes = ByteArray(4_410)
        var wash = 0.0
        var drop = 0.0
        while (running) {
            var i = 0
            while (i < bytes.size) {
                val white = random.nextDouble() * 2.0 - 1.0
                wash = wash * 0.965 + white * 0.035
                if (drop < 0.002 && random.nextDouble() < 0.0009) drop = 0.55
                drop *= 0.985
                val closeDrop = drop * (random.nextDouble() * 0.35 + 0.65)
                val sample = ((wash * 0.34 + white * 0.035 + closeDrop) * 8_500.0).toInt()
                val safe = min(32_767, max(-32_768, sample))
                bytes[i] = (safe and 0xff).toByte()
                bytes[i + 1] = ((safe ushr 8) and 0xff).toByte()
                i += 2
            }
            runCatching { output.write(bytes, 0, bytes.size) }.onFailure { running = false }
        }
        runCatching { output.drain() }
        runCatching { output.close() }
    }
}
