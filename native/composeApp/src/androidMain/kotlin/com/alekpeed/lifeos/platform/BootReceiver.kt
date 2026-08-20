package com.alekpeed.lifeos.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.insight.rearmScheduledReminders

// Android clears every pending alarm on reboot, so a scheduled reminder set before
// a restart would never fire — the reminder itself was still listed in the app,
// which made it look like it had worked. This re-arms the still-future ones as soon
// as the device finishes booting.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // A receiver can run before any Activity has, so the storage layer needs its
        // context wired up here or the reminder list reads back empty.
        val app = context.applicationContext
        Storage.appContext = app
        NativeHost.appContext = app
        runCatching { rearmScheduledReminders(System.currentTimeMillis()) }
    }
}
