// geofence.js — low-power background geofencing (FUTURE_FEATURES.md §13).
//
// Registers OS geofences (via the custom native Geofence plugin, which wraps
// Google Play Services' GeofencingClient) for the Places you've flagged: any
// place with a note-to-self, plus your "want to go" list. Arriving fires an
// on-device notification even with the app closed — no continuous GPS, no
// persistent tracking notification.
//
// NO-BUILD NOTE: no bare plugin import; the plugin is a native local plugin
// reached through window.Capacitor.Plugins.Geofence. Absent in a browser, so
// every function here is a safe no-op and the capability layer hides it.

import { hasCapability } from './capabilities.js';
import { Places, PlaceNotes } from '../data/api.js';

// Android caps geofences at 100 per app; stay comfortably under.
const MAX_GEOFENCES = 90;
const RADIUS_METERS = 250;

function plugin() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    return (c && c.Plugins && c.Plugins.Geofence) || null;
  } catch {
    return null;
  }
}

/** Available only in a native Android build with the geofence plugin present. */
export function canGeofence() {
  return hasCapability('geofence') && !!plugin();
}

/** Current permission state: { foreground, background } booleans. */
export async function geofenceStatus() {
  const p = plugin();
  if (!canGeofence() || !p) return { foreground: false, background: false };
  try {
    return await p.getStatus();
  } catch {
    return { foreground: false, background: false };
  }
}

/** Ask for location permission (fine first, then background). Returns the new status. */
export async function requestGeofencePermission() {
  const p = plugin();
  if (!canGeofence() || !p) return { foreground: false, background: false };
  try {
    return await p.requestPermissions();
  } catch {
    return { foreground: false, background: false };
  }
}

// Build the geofence set from flagged Places: those with a note-to-self, or on
// the "want to go" list. Nearest-first isn't meaningful without a location, so
// we just cap the count deterministically (notes first, then want-to-go).
async function buildGeofences() {
  const [places, notes] = await Promise.all([Places.list(), PlaceNotes.list()]);
  const byId = new Map(places.map((p) => [p.id, p]));
  const notesByPlace = new Map();
  for (const n of notes) {
    if (!notesByPlace.has(n.placeId)) notesByPlace.set(n.placeId, []);
    notesByPlace.get(n.placeId).push(n.text || '');
  }

  const out = [];
  const seen = new Set();
  const add = (place, body) => {
    if (seen.has(place.id)) return;
    if (typeof place.lat !== 'number' || typeof place.lng !== 'number') return;
    seen.add(place.id);
    out.push({
      id: `place-${place.id}`,
      latitude: place.lat,
      longitude: place.lng,
      radius: RADIUS_METERS,
      title: place.name || 'A place you noted',
      body,
      route: 'places',
    });
  };

  // Notes-to-self first (the explicit "remind me when I'm here" signal).
  for (const [placeId, texts] of notesByPlace) {
    const place = byId.get(placeId);
    if (place) add(place, texts.filter(Boolean).join(' · ') || 'You left yourself a note here.');
    if (out.length >= MAX_GEOFENCES) break;
  }
  // Then want-to-go places.
  if (out.length < MAX_GEOFENCES) {
    for (const place of places) {
      if (place.listType === 'wantToGo') add(place, "Somewhere you've wanted to visit.");
      if (out.length >= MAX_GEOFENCES) break;
    }
  }
  return out;
}

/**
 * Re-register OS geofences from the current flagged Places. Also re-establishes
 * them after a reboot (geofences don't survive one), since this runs on boot.
 * No-op on web or without foreground location permission. Returns the count.
 */
export async function syncGeofences() {
  const p = plugin();
  if (!canGeofence() || !p) return 0;
  const status = await geofenceStatus();
  if (!status.foreground) return 0;
  try {
    const geofences = await buildGeofences();
    const res = await p.setGeofences({ geofences });
    return (res && typeof res.count === 'number') ? res.count : geofences.length;
  } catch {
    return 0;
  }
}

/** Remove all registered geofences. */
export async function clearGeofences() {
  const p = plugin();
  if (!canGeofence() || !p) return;
  try {
    await p.removeAll();
  } catch {
    /* no-op */
  }
}
