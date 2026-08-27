package com.alekpeed.lifeos.diag

import com.alekpeed.lifeos.Storage

// What the app was doing when it died, kept so the next launch can say so.
//
// Until this existed a module that threw took the whole app down and left nothing
// behind — no trace, no module name, nothing on screen. The only report available was
// "the Operations screen is an error", which is not the reporter's fault: it is
// everything the app gave them. A local-first app with no server to phone home to has
// to keep its own black box or it has none.
//
// Both keys are reserved (`__`): they never sync, never enter the mutation log and
// never reach a backup. A stack trace is a fact about one device on one build, and
// pushing it to the other machines would be noise at best.

// The module on screen. Written when one opens, cleared when it closes, so the crash
// report can name the screen rather than making someone remember what they tapped.
private const val CURRENT_KEY = "__current_module"

// The last crash, as a rendered report. One slot: the newest crash is the one being
// chased, and a growing log in a flat key-value store is a file that only ever grows.
private const val CRASH_KEY = "__last_crash"

object Crash {

    fun noteScreen(moduleId: String?) {
        runCatching { Storage.write(CURRENT_KEY, moduleId.orEmpty()) }
    }

    fun currentScreen(): String = runCatching { Storage.read(CURRENT_KEY).orEmpty() }.getOrDefault("")

    // Called from the platform's uncaught-exception handler, on a thread that is about
    // to die. Everything here is best-effort by construction: a crash reporter that
    // throws on the way down turns one lost stack trace into two.
    fun record(report: String) {
        runCatching { Storage.write(CRASH_KEY, report) }
    }

    fun last(): String? = runCatching { Storage.read(CRASH_KEY)?.takeIf { it.isNotBlank() } }.getOrNull()

    fun clear() {
        runCatching { Storage.write(CRASH_KEY, "") }
    }
}

// Assembled here rather than in the platform handler so both targets produce the same
// shape and the formatting is testable.
//
// The causal chain matters more than the top frame: Compose wraps a screen's failure in
// its own exception, so an unwrapped report routinely names a Compose internal for a
// null dereference in module code. Following `cause` down is what puts the real line in
// front of whoever reads this.
fun crashReport(
    screen: String,
    thread: String,
    stamp: String,
    chain: List<CrashFrame>,
): String = buildString {
    appendLine("Life OS crash")
    appendLine("when:   $stamp")
    appendLine("screen: ${screen.ifBlank { "(home)" }}")
    appendLine("thread: $thread")
    chain.forEachIndexed { i, frame ->
        appendLine()
        appendLine(if (i == 0) "${frame.type}: ${frame.message}" else "caused by ${frame.type}: ${frame.message}")
        // Enough to place the failure, short enough to read on a phone and paste into a
        // message. The frames below the first few are framework plumbing on every crash.
        frame.frames.take(FRAMES_PER_LEVEL).forEach { appendLine("    at $it") }
        val hidden = frame.frames.size - FRAMES_PER_LEVEL
        if (hidden > 0) appendLine("    … $hidden more")
    }
}.trim()

private const val FRAMES_PER_LEVEL = 8

data class CrashFrame(val type: String, val message: String, val frames: List<String>)
