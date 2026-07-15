package com.alekpeed.lifeos;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// Low-power background geofencing via Google Play Services' GeofencingClient
// (FUTURE_FEATURES.md 13). The OS wakes GeofenceBroadcastReceiver on arrival --
// no continuous GPS, no foreground service, no persistent notification. Geofence
// metadata (title/body/route to show on arrival) is stashed in SharedPreferences
// so the receiver can build the notification even when the app is closed.
@CapacitorPlugin(name = "Geofence")
public class GeofencePlugin extends Plugin {

    static final String PREFS = "lifeos_geofences";
    static final String SEP = "|~|";

    private GeofencingClient client;

    @Override
    public void load() {
        client = LocationServices.getGeofencingClient(getContext());
    }

    private boolean hasFine() {
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBackground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasFine();
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private PendingIntent transitionPendingIntent() {
        Intent intent = new Intent(getContext(), GeofenceBroadcastReceiver.class);
        intent.setAction("com.alekpeed.lifeos.GEOFENCE_EVENT");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(getContext(), 0, intent, flags);
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject r = new JSObject();
        r.put("foreground", hasFine());
        r.put("background", hasBackground());
        call.resolve(r);
    }

    // Best-effort permission request. Background location can only be requested
    // after fine location is granted (Android 11+), so this may need two passes;
    // the JS side re-checks getStatus rather than waiting on a result callback.
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        List<String> perms = new ArrayList<>();
        if (!hasFine()) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackground()) {
            perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        if (!perms.isEmpty() && getActivity() != null) {
            ActivityCompat.requestPermissions(getActivity(), perms.toArray(new String[0]), 7411);
        }
        JSObject r = new JSObject();
        r.put("foreground", hasFine());
        r.put("background", hasBackground());
        call.resolve(r);
    }

    // Replace all registered geofences with the given set. Each item:
    // { id, latitude, longitude, radius?, title?, body?, route? }.
    @PluginMethod
    public void setGeofences(PluginCall call) {
        if (!hasFine()) {
            call.reject("Location permission not granted");
            return;
        }
        JSArray arr = call.getArray("geofences");
        List<Geofence> fences = new ArrayList<>();
        SharedPreferences.Editor ed = prefs().edit();
        ed.clear();
        try {
            List<JSONObject> items = new ArrayList<>();
            if (arr != null) items = arr.toList();
            for (JSONObject o : items) {
                String id = o.getString("id");
                double lat = o.getDouble("latitude");
                double lng = o.getDouble("longitude");
                float radius = (float) o.optDouble("radius", 250);
                String title = o.optString("title", "Life OS");
                String body = o.optString("body", "");
                String route = o.optString("route", "places");
                fences.add(new Geofence.Builder()
                        .setRequestId(id)
                        .setCircularRegion(lat, lng, radius)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                        .build());
                ed.putString(id, title + SEP + body + SEP + route);
            }
        } catch (Exception e) {
            call.reject("Bad geofence data: " + e.getMessage());
            return;
        }
        ed.apply();

        // Clear whatever was registered, then add the fresh set.
        client.removeGeofences(transitionPendingIntent());
        if (fences.isEmpty()) {
            JSObject r = new JSObject();
            r.put("count", 0);
            call.resolve(r);
            return;
        }
        GeofencingRequest req = new GeofencingRequest.Builder()
                .setInitialTrigger(0) // do not fire just for being inside on registration
                .addGeofences(fences)
                .build();
        try {
            final int count = fences.size();
            client.addGeofences(req, transitionPendingIntent())
                    .addOnSuccessListener(unused -> {
                        JSObject r = new JSObject();
                        r.put("count", count);
                        call.resolve(r);
                    })
                    .addOnFailureListener(e -> call.reject("Could not register geofences: " + e.getMessage()));
        } catch (SecurityException e) {
            call.reject("Missing background location permission: " + e.getMessage());
        }
    }

    @PluginMethod
    public void removeAll(PluginCall call) {
        prefs().edit().clear().apply();
        client.removeGeofences(transitionPendingIntent())
                .addOnSuccessListener(unused -> call.resolve())
                .addOnFailureListener(e -> call.reject(e.getMessage()));
    }
}
