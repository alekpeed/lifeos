package com.alekpeed.lifeos.platform

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

// Low-power arrival geofence. Arms a circle at the device's current location; the
// system fires GeofenceReceiver on entry even when the app is closed. Needs fine +
// background location. A working scaffold — background-location grant flow and
// per-place coordinates (vs "here") are on-device follow-ups.
object Geofences {
    private const val RADIUS_M = 120f

    private fun pendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, GeofenceReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, 0, intent, flags)
    }

    fun armHere(ctx: Context?, label: String) {
        ctx ?: return
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            NativeHost.activity?.requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                9003,
            )
            return
        }
        val fused = LocationServices.getFusedLocationProviderClient(ctx)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()
        try {
            fused.getCurrentLocation(request, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    loc ?: return@addOnSuccessListener
                    val geofence = Geofence.Builder()
                        .setRequestId(label)
                        .setCircularRegion(loc.latitude, loc.longitude, RADIUS_M)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                        .build()
                    val geoRequest = GeofencingRequest.Builder()
                        .setInitialTrigger(0)
                        .addGeofence(geofence)
                        .build()
                    try {
                        LocationServices.getGeofencingClient(ctx).addGeofences(geoRequest, pendingIntent(ctx))
                    } catch (e: SecurityException) {
                    }
                }
        } catch (e: SecurityException) {
        }
    }

    fun clear(ctx: Context?) {
        ctx ?: return
        try {
            LocationServices.getGeofencingClient(ctx).removeGeofences(pendingIntent(ctx))
        } catch (e: Exception) {
        }
    }
}
