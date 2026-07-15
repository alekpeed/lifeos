package com.alekpeed.lifeos;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

// Wakes on a geofence ENTER (registered by GeofencePlugin) even with the app
// closed, and posts an arrival notification. Tapping it deep-links into the app
// (lifeos://open/<route>), which native-boot's deep-link router handles.
public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String CHANNEL = "lifeos_geofence";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) return;
        if (event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER) return;

        List<Geofence> triggered = event.getTriggeringGeofences();
        if (triggered == null || triggered.isEmpty()) return;

        SharedPreferences prefs = context.getSharedPreferences(GeofencePlugin.PREFS, Context.MODE_PRIVATE);
        ensureChannel(context);

        for (Geofence g : triggered) {
            String id = g.getRequestId();
            String title = "Life OS";
            String body = "You've arrived somewhere you noted.";
            String route = "places";
            String meta = prefs.getString(id, null);
            if (meta != null) {
                String[] parts = meta.split(java.util.regex.Pattern.quote(GeofencePlugin.SEP), -1);
                if (parts.length >= 1 && !parts[0].isEmpty()) title = parts[0];
                if (parts.length >= 2 && !parts[1].isEmpty()) body = parts[1];
                if (parts.length >= 3 && !parts[2].isEmpty()) route = parts[2];
            }
            postNotification(context, id, title, body, route);
        }
    }

    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                        new NotificationChannel(CHANNEL, "Arrival nudges", NotificationManager.IMPORTANCE_DEFAULT));
            }
        }
    }

    private void postNotification(Context context, String id, String title, String body, String route) {
        Intent open = new Intent(context, MainActivity.class);
        open.setAction(Intent.ACTION_VIEW);
        open.setData(Uri.parse("lifeos://open/" + route));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(context, id.hashCode(), open, flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id.hashCode(), b.build());
    }
}
