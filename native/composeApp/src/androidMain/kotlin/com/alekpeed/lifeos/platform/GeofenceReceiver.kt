package com.alekpeed.lifeos.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alekpeed.lifeos.home.arrivalScene
import com.alekpeed.lifeos.home.homeToggle
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Fires when you arrive at an armed location; posts an arrival notification, and runs
// the Home scene you picked, if you picked one (§13.3).
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        // This broadcast can be the process's first moment of life, so both context
        // holders have to be set before anything reads the store.
        NativeHost.appContext = context.applicationContext
        com.alekpeed.lifeos.Storage.appContext = context.applicationContext

        val label = event.triggeringGeofences?.firstOrNull()?.requestId ?: "a saved place"
        Native.postReminder("You're back", "Arrived at $label")

        val scene = runCatching { arrivalScene() }.getOrDefault("")
        if (scene.isBlank()) return

        // goAsync keeps the receiver alive for the round trip. Bounded hard: a hub that
        // is asleep must not hold a broadcast open, and arriving home is not worth an
        // ANR. Nothing is retried — the lights either came on or they did not, and a
        // late second attempt would be worse than none.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000) { homeToggle(scene, on = true) }
            } catch (e: Exception) {
                // A home-automation failure is not worth crashing the arrival notice.
            } finally {
                pending.finish()
            }
        }
    }
}
