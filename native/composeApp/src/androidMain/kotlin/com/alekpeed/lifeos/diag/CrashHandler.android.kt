package com.alekpeed.lifeos.diag

import kotlinx.datetime.Clock

// Catches what Compose does not.
//
// A Compose error boundary around each screen cannot work here: composition, effects,
// and the coroutines a screen launches all throw on frames a try/catch at the call site
// never sees. The uncaught-exception handler does see them, and it also catches the
// background threads — sync, the HA poll, an alarm re-arm — that no boundary could.
//
// The previous handler is always chained to. Skipping it would leave the process alive
// in an undefined state after a fatal error, which is worse than the crash: the app
// stays on screen, half its state gone, and the next thing you type disappears.
fun installCrashHandler() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        runCatching {
            Crash.record(
                crashReport(
                    screen = Crash.currentScreen(),
                    thread = thread.name,
                    stamp = Clock.System.now().toString(),
                    chain = causalChain(error),
                ),
            )
        }
        previous?.uncaughtException(thread, error)
    }
}

// Down the `cause` chain, guarded against a self-referencing one — a cycle here would
// hang the process on the way out, turning a crash into a freeze.
private fun causalChain(error: Throwable): List<CrashFrame> {
    val out = mutableListOf<CrashFrame>()
    val seen = mutableSetOf<Throwable>()
    var t: Throwable? = error
    while (t != null && seen.add(t) && out.size < 4) {
        out.add(
            CrashFrame(
                type = t::class.qualifiedName ?: t::class.simpleName ?: "Throwable",
                message = t.message.orEmpty(),
                frames = t.stackTrace.map { it.toString() },
            ),
        )
        t = t.cause
    }
    return out
}
