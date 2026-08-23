package com.alekpeed.lifeos.platform

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alekpeed.lifeos.push.applyDone
import com.alekpeed.lifeos.push.applySnooze

// The action buttons on a reminder notification, and what they do to the record the
// notification is about (§7 D-5 phase 2).
//
// Until now both buttons did the same thing — cancel the notification — so a "Done"
// that changed nothing was the whole feature. The notification now carries a SUBJECT
// ("<storage key>|<record id>"); the resolution itself lives in commonMain
// (push/Actions.kt) so it can be tested without a device and reused by an FCM action.
//
// A subject that names nothing still dismisses: a notification can outlive the record
// it was about, and the button labels are chosen from the same subject, so a
// dismiss-only notification says "Dismiss".
class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DONE = "com.alekpeed.lifeos.DONE"
        const val ACTION_SNOOZE = "com.alekpeed.lifeos.SNOOZE"
        const val EXTRA_ID = "notif_id"
        const val EXTRA_SUBJECT = "subject"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // A button can be pressed with the app long since killed, so this broadcast may
        // be the process's first moment of life. Both context holders have to be set or
        // the write below silently does nothing — which is the old behaviour wearing a
        // new label.
        NativeHost.appContext = context.applicationContext
        com.alekpeed.lifeos.Storage.appContext = context.applicationContext

        val subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
        runCatching {
            when (intent.action) {
                ACTION_DONE -> applyDone(subject)
                ACTION_SNOOZE -> applySnooze(subject)
                else -> false
            }
        }

        val id = intent.getIntExtra(EXTRA_ID, -1)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (id != -1) nm?.cancel(id)
    }
}
