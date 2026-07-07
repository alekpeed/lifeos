const CACHE_VERSION = 'lifeos-v19';
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
  './js/interfaces/registry.js',
  './js/interfaces/manifest.js',
  './js/interfaces/default/index.js',
  './js/interfaces/default/dom.js',
  './js/interfaces/default/leaflet-loader.js',
  './js/interfaces/default/style.css',
  './js/interfaces/default/views/dashboard.js',
  './js/interfaces/default/views/settings.js',
  './js/interfaces/default/views/tasks.js',
  './js/interfaces/default/views/places.js',
  './js/interfaces/default/views/links.js',
  './js/interfaces/default/views/education.js',
  './js/interfaces/default/views/books.js',
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
  './js/theory/notes.js',
  './js/theory/chords.js',
  './js/theory/voicings.js',
  './js/theory/harmony.js',
  './js/theory/barry.js',
  './js/theory/lessons.js',
  './js/audio/synth.js',
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
