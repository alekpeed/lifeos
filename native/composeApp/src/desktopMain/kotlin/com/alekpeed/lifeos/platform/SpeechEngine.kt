package com.alekpeed.lifeos.platform

import java.util.concurrent.TimeUnit

// Best-effort read-aloud. There's no TTS engine bundled with the JVM, so this shells
// out to whatever the OS already has: speech-dispatcher (spd-say) is what most Linux
// desktop environments already carry for accessibility, eSpeak NG is the common
// fallback, and Windows has System.Speech in every install, reachable from
// PowerShell with nothing extra to install. If none of that is found, `available`
// is false and speak() quietly does nothing — the same degrade every other Native
// function makes on a machine that can't do it.
//
// ProcessBuilder with a list of arguments never goes through a shell, so the spoken
// text needs no escaping on Linux regardless of what punctuation is in it. Windows is
// the one path that pipes the text through stdin instead, since it's embedded in a
// PowerShell script string rather than passed as a bare argument.
internal object SpeechEngine {
    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    // Probed once per process and cached: which command actually runs here.
    private val engine: List<String>? by lazy { detect() }

    val available: Boolean get() = engine != null

    @Volatile private var current: Process? = null

    fun speak(text: String) {
        val cmd = engine ?: return
        stop()
        val t = text.trim()
        if (t.isEmpty()) return
        try {
            if (isWindows) {
                val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
                current = p
                p.outputStream.use { it.write(t.toByteArray(Charsets.UTF_8)) }
                drain(p)
            } else {
                val p = ProcessBuilder(cmd + t).redirectErrorStream(true).start()
                current = p
                drain(p)
            }
        } catch (e: Exception) {
            current = null
        }
    }

    fun stop() {
        current?.let { runCatching { it.destroy() } }
        current = null
    }

    // Drain the process's combined output on a daemon thread so it can't block on a
    // full pipe — nothing in the app reads what these commands print.
    private fun drain(p: Process) {
        Thread({ runCatching { p.inputStream.readBytes() } }, "lifeos-tts-drain").apply { isDaemon = true }.start()
    }

    private fun detect(): List<String>? {
        if (isWindows) {
            // Assumed available rather than probed: System.Speech ships with every
            // desktop Windows install, so there's no separate binary to go looking for.
            return listOf(
                "powershell", "-NoProfile", "-Command",
                "Add-Type -AssemblyName System.Speech; " +
                    "\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                    "\$s.Speak([Console]::In.ReadToEnd())",
            )
        }
        // Each candidate's --version is run so a missing binary fails fast here,
        // rather than surfacing later as a mysterious silent speak().
        return listOf(listOf("spd-say"), listOf("espeak-ng"), listOf("espeak")).firstOrNull { cmd ->
            runCatching {
                val p = ProcessBuilder(cmd[0], "--version").redirectErrorStream(true).start()
                val exited = p.waitFor(2, TimeUnit.SECONDS)
                if (!exited) p.destroyForcibly()
                exited
            }.getOrDefault(false)
        }
    }
}
