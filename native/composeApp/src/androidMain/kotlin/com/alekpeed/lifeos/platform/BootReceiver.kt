package com.alekpeed.lifeos.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alekpeed.lifeos.alarms.rearmAllAlarms

// Puts the alarms back after the OS throws them away.
//
// Android drops every pending AlarmManager entry on reboot, and again when the app is
// replaced by an update. Before this, the only re-arm was at app open — so restarting
// the phone at night meant the morning's task, bill and capsule notifications simply
// never arrived, and the app looked like a day with nothing due rather than a broken
// one. That is the worst shape a bug can take in something you are meant to trust to
// remember for you.
//
// Two actions, both needed and for different reasons:
//
//   · BOOT_COMPLETED — the reboot case. Not LOCKED_BOOT_COMPLETED: that one arrives
//     before the user has unlocked the device, when credential-encrypted storage is
//     still sealed, so the sweep would read an empty store and arm nothing. Waiting for
//     the unlocked broadcast costs a few seconds and is the only one that can work.
//   · MY_PACKAGE_REPLACED — an app update clears alarms too, and unlike a reboot it can
//     happen overnight without anyone touching the phone.
//
// The work is small (four blob reads and a handful of alarm calls) but it is disk work
// on the main thread of a broadcast, so it runs inside goAsync on a worker. Ten seconds
// is the budget a manifest receiver gets before the system considers it stuck; this
// finishes in milliseconds, and if it somehow does not, finish() in the finally block
// is what keeps a slow sweep from becoming an ANR.
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        // A boot broadcast lands in a cold process with no Activity: nothing has set
        // these up yet, and every sweep reads the store.
        NativeHost.appContext = context.applicationContext
        com.alekpeed.lifeos.Storage.appContext = context.applicationContext

        val pending = goAsync()
        Thread {
            try {
                rearmAllAlarms()
            } finally {
                pending.finish()
            }
        }.start()
    }
}
