package com.alekpeed.lifeos.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

// Fires when you arrive at an armed location; posts an arrival notification.
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val label = event.triggeringGeofences?.firstOrNull()?.requestId ?: "a saved place"
            Native.postReminder("You're back", "Arrived at $label")
        }
    }
}
