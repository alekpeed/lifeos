package com.alekpeed.lifeos.sync

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.platform.NetworkKind
import com.alekpeed.lifeos.platform.networkKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Sync without being asked.
//
// Until this existed the backend protected nothing unless you remembered to open
// Settings and press a button — there was exactly one call site in the whole app. The
// data most worth having off the device is the data you add while away from it, which
// is precisely when you are not thinking about Settings.
//
// Every user write already funnels through SyncMeta.record, so that is the trigger: one
// hook, both platforms, and it inherits the existing filter that keeps internal keys out.
object AutoSync {
    private const val K_ENABLED = "AutoSyncEnabled"
    private const val K_ON_MOBILE = "AutoSyncOnMobileData"

    // Writes arrive in bursts — a text field saves on every keystroke — so a sync per
    // write would be one request per character. This waits for a short quiet period and
    // sends once for the whole burst: immediate in human terms, one request in practice.
    // Each new write restarts the window, so a pause in typing is what actually fires it.
    private const val QUIET_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Mutex()
    private var pending: Job? = null

    // Applying a remote record writes to Storage, which would trigger another sync, which
    // would apply again. The flag breaks that loop: writes made *by* a sync are not local
    // changes. It is set for the whole run rather than per write, because applyRemote
    // writes many keys.
    @Volatile
    private var applying = false

    // A change that arrives while a sync is already running would otherwise be dropped:
    // its debounce timer gets cancelled, and the run in flight may have already collected
    // its records. This marks the run stale so it goes round once more instead.
    @Volatile
    private var dirty = false

    var enabled: Boolean
        get() = Storage.read(K_ENABLED) != "0"          // on unless deliberately turned off
        set(v) = Storage.write(K_ENABLED, if (v) "1" else "0")

    var onMobileData: Boolean
        get() = Storage.read(K_ON_MOBILE) == "1"        // off unless deliberately allowed
        set(v) = Storage.write(K_ON_MOBILE, if (v) "1" else "0")

    // Why a sync would not happen right now, or null if it would.
    fun blockedReason(): String? = when {
        !enabled -> "Auto-sync is off"
        !SupabaseAuth.isSignedIn() -> "Not signed in"
        networkKind() == NetworkKind.NONE -> "No connection"
        networkKind() == NetworkKind.METERED && !onMobileData -> "On mobile data"
        else -> null
    }

    // Called for every local change. Cheap and non-blocking: it only restarts a timer.
    fun onLocalChange() {
        if (applying || !enabled) return
        dirty = true
        pending?.cancel()
        pending = scope.launch {
            delay(QUIET_MS)
            run()
        }
    }

    // App opened, or came back to the foreground. Pulls anything the other device did,
    // and pushes anything that never made it out last time.
    fun onForeground() {
        if (!enabled) return
        scope.launch { run() }
    }

    // Going to the background is the last safe moment to get a change off the device, so
    // any waiting burst is sent now instead of after its quiet period.
    fun onBackground() {
        if (!enabled) return
        pending?.cancel()
        scope.launch { run() }
    }

    // What the Settings row shows.
    var lastResult: String = ""
        private set

    // Attachments go 25 to a sync run, so a first sync over a large library needs several.
    // Bounded rather than "until empty": a blob that fails every attempt would otherwise
    // spin forever, and 40 rounds is 1,000 files.
    private const val MAX_ROUNDS = 40

    private suspend fun run() {
        if (blockedReason() != null) return
        // One sync at a time — a foreground sync and a debounced write landing together
        // would push the same records twice and race on lastSyncAt. Losing the race does
        // not lose the change: the loser marks the run stale and the holder repeats.
        if (!gate.tryLock()) {
            dirty = true
            return
        }
        try {
            applying = true
            try {
                var passes = 0
                do {
                    dirty = false
                    syncUntilDrained()
                    passes += 1
                } while (dirty && passes < 3 && blockedReason() == null)
            } finally {
                applying = false
            }
        } finally {
            gate.unlock()
        }
    }

    // One sync, repeated while attachment batches are still moving.
    private suspend fun syncUntilDrained() {
        var round = 0
        while (round < MAX_ROUNDS) {
            round += 1
            val summary = SupabaseSync.syncNow().getOrElse {
                lastResult = it.message ?: "Sync failed"
                return
            }
            lastResult = buildString {
                append("Synced ${summary.pushed} up, ${summary.applied} down")
                if (summary.blobsUp > 0 || summary.blobsDown > 0) {
                    append(" · files ↑${summary.blobsUp} ↓${summary.blobsDown}")
                }
                if (summary.blobsFailed > 0) append(" · ${summary.blobsFailed} file(s) failed")
            }
            // Keep going only while a round actually moved files. Remaining-but-stalled
            // means every one of them failed, and another round would fail identically.
            val moved = summary.blobsUp > 0 || summary.blobsDown > 0
            if (summary.blobsRemaining == 0 || !moved) {
                if (summary.blobsRemaining > 0) {
                    lastResult += " · ${summary.blobsRemaining} file(s) still waiting"
                }
                break
            }
            // A breath between rounds so a long drain doesn't monopolise the radio.
            delay(300)
        }
    }
}
