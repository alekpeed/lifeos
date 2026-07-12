const CACHE_VERSION = 'lifeos-v1.06';
const APP_SHELL = [
  './',
  './index.html',
  './manifest.json',
  './css/tokens.css',
  './css/base.css',
  './js/app.js',
  './js/shell.js',
  './js/modules.js',
  './js/data/db.js',
  './js/data/schema.js',
  './js/data/api.js',
  './js/data/events.js',
  './js/data/sync.js',
  './js/data/sync-config.js',
  './js/data/gapi.js',
  './js/data/calendar.js',
  './js/data/photos-picker.js',
  './js/data/applock.js',
  './js/data/sharebox-sync.js',
  './js/data/picker.js',
  './js/data/supabase-config.js',
  './js/data/supabase-client.js',
  './js/data/supabase-auth.js',
  './js/data/sharebox-supabase.js',
  './js/data/profile-supabase.js',
  './js/data/claude-client.js',
  './js/data/gemini-client.js',
  './js/data/telegram-client.js',
  './js/interfaces/registry.js',
  './js/interfaces/manifest.js',
  './js/interfaces/view-library.js',
  './js/interfaces/default/index.js',
  // Vespera. Note: img/hub.png is deliberately NOT precached -- the art is
  // optional (CSS paints a fallback), and cache.addAll() rejects the whole
  // install on any single 404, which would brick offline support whenever
  // the image hasn't been uploaded. It gets runtime-cached on first view.
  './js/interfaces/vespera/index.js',
  './js/interfaces/vespera/style.css',
  // Vespera room signage faces (small; load-bearing for the look, so unlike
  // the optional room art these ARE precached).
  './vendor/fonts/oxanium-600.woff2',
  './vendor/fonts/rajdhani-600.woff2',
  './js/interfaces/default/dom.js',
  './js/interfaces/default/knob.js',
  './js/interfaces/default/leaflet-loader.js',
  './js/interfaces/default/style.css',
  './js/interfaces/default/views/dashboard.js',
  './js/interfaces/default/views/paper.js',
  './js/interfaces/default/views/settings.js',
  './js/interfaces/default/views/tasks.js',
  './js/interfaces/default/views/places.js',
  './js/interfaces/default/views/links.js',
  './js/interfaces/default/views/education.js',
  './js/interfaces/default/views/books.js',
  './js/interfaces/default/views/reader.js',
  './js/interfaces/default/views/recipes.js',
  './js/interfaces/default/views/finance.js',
  './js/interfaces/default/views/documents.js',
  './js/interfaces/default/views/contacts.js',
  './js/interfaces/default/views/milestones.js',
  './js/interfaces/default/views/search.js',
  './js/interfaces/default/views/tools.js',
  './js/interfaces/default/views/habits.js',
  './js/interfaces/default/views/health.js',
  './js/interfaces/default/views/photos.js',
  './js/interfaces/default/views/languages.js',
  './js/interfaces/default/views/chords.js',
  './js/interfaces/default/views/sharebox.js',
  './js/interfaces/default/views/museum.js',
  './js/interfaces/default/views/timecapsules.js',
  './js/interfaces/default/views/collections.js',
  './js/interfaces/default/views/packing.js',
  './js/interfaces/default/views/quartermaster.js',
  './js/interfaces/default/views/skilltree.js',
  './js/interfaces/default/views/entropy.js',
  './js/interfaces/default/views/stationcat.js',
  './js/interfaces/default/views/ghostdays.js',
  './js/interfaces/default/views/starters.js',
  './js/interfaces/default/views/themefromphoto.js',
  './js/interfaces/default/views/dreamjournal.js',
  './js/interfaces/default/views/rabbitholes.js',
  './js/interfaces/default/views/almanac.js',
  './js/interfaces/default/views/lifeasmusic.js',
  './js/interfaces/default/views/knowledge.js',
  './js/interfaces/default/views/orrery.js',
  './js/interfaces/default/views/timemachine.js',
  './js/interfaces/default/views/qrsync.js',
  './js/interfaces/default/views/assistant.js',
  './js/interfaces/default/views/ideas.js',
  './js/interfaces/default/views/recall.js',
  './js/theory/notes.js',
  './js/theory/chords.js',
  './js/theory/voicings.js',
  './js/theory/harmony.js',
  './js/theory/barry.js',
  './js/theory/lessons.js',
  './js/theory/voicelead.js',
  './js/theory/graph.js',
  './js/theory/drills.js',
  './js/audio/synth.js',
  './js/reader/epub.js',
  './vendor/fflate/fflate.module.js',
  './vendor/supabase/supabase.umd.js',
  './vendor/qrcode/qrcode.mjs',
  './vendor/leaflet/leaflet.js',
  './vendor/leaflet/leaflet.css',
  './vendor/leaflet/images/marker-icon.png',
  './vendor/leaflet/images/marker-icon-2x.png',
  './vendor/leaflet/images/marker-shadow.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_VERSION)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((key) => key !== CACHE_VERSION).map((key) => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  );
});

// Cache-first for app shell / same-origin GET requests, with a network fallback
// that opportunistically fills the cache for anything not pre-listed above.
self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached;

      return fetch(request)
        .then((response) => {
          if (response && response.ok) {
            const copy = response.clone();
            caches.open(CACHE_VERSION).then((cache) => cache.put(request, copy));
          }
          return response;
        })
        .catch(() => caches.match('./index.html'));
    })
  );
});
