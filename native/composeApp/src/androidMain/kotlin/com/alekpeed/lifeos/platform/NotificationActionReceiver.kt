package com.alekpeed.lifeos.platform

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Handles the action buttons on reminder notifications. "Done" dismisses it.
// "Snooze" dismisses for now (a real timed snooze via AlarmManager is a follow-up).
class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DONE = "com.alekpeed.lifeos.DONE"
        const val ACTION_SNOOZE = "com.alekpeed.lifeos.SNOOZE"
        const val EXTRA_ID = "notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (id != -1) nm?.cancel(id)
    }
}
