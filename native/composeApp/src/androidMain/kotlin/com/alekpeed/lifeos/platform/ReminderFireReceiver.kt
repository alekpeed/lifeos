package com.alekpeed.lifeos.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Fires when a scheduled reminder's alarm goes off (even if the app is closed) and
// posts the actual notification. Kept separate from NotificationActionReceiver,
// which only handles the Done/Snooze buttons on an already-shown notification.
class ReminderFireReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }

    override fun onReceive(context: Context, intent: Intent) {
        NativeHost.appContext = context.applicationContext
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        Native.postReminder(title, body)
    }
}
