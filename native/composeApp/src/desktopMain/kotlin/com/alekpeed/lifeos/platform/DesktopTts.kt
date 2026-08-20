package com.alekpeed.lifeos.platform

// Read-aloud on desktop.
//
// The JVM ships no speech engine, so this drives whatever the OS already has:
// speech-dispatcher or eSpeak on Linux, `say` on macOS, System.Speech via PowerShell
// on Windows. All three are offline and keyless, which is the whole point — the
// briefing should read itself on a plane.
//
// The text is always passed as a process ARGUMENT, never through a shell, so a note
// containing quotes or a semicolon can't turn into a command. Windows is the one case
// that needs a script string; there the text is escaped and length-capped instead.
internal object DesktopTts {

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

    @Volatile
    private var speaking: Process? = null

    // First engine on this machine that actually exists, resolved once.
    private val engine: String? by lazy {
        when {
            isMac -> "say".takeIf { onPath(it) }
            isWindows -> "powershell".takeIf { onPath(it) }
            else -> listOf("spd-say", "espeak-ng", "espeak").firstOrNull { onPath(it) }
        }
    }

    val available: Boolean get() = engine != null

    private fun onPath(cmd: String): Boolean = try {
        val which = if (isWindows) listOf("where", cmd) else listOf("which", cmd)
        ProcessBuilder(which)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor() == 0
    } catch (e: Exception) {
        false
    }

    fun speak(text: String) {
        val eng = engine ?: return
        val body = text.trim().take(20_000)
        if (body.isEmpty()) return
        stop()
        try {
            val cmd = when (eng) {
                // spd-say returns immediately and speaks in the background; -w waits so
                // stopSpeaking has a live process to kill.
                "spd-say" -> listOf("spd-say", "-w", "--", body)
                "espeak-ng", "espeak" -> listOf(eng, "--", body)
                "say" -> listOf("say", "--", body)
                else -> listOf(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Add-Type -AssemblyName System.Speech; " +
                        "(New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('${psEscape(body)}')",
                )
            }
            speaking = ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (e: Exception) {
            speaking = null
        }
    }

    fun stop() {
        try {
            speaking?.destroy()
        } catch (e: Exception) {
            // already gone
        }
        speaking = null
    }

    // Single quotes are the only PowerShell-literal metacharacter that matters; doubling
    // one escapes it. Newlines become spaces so the -Command string stays one line.
    private fun psEscape(s: String): String =
        s.replace("'", "''").replace('\n', ' ').replace('\r', ' ')
}
