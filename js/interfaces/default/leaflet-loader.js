// Lazy-loads the vendored Leaflet library (self-hosted under vendor/leaflet/
// — no CDN dependency, so the map still works offline once cached by the
// service worker) the first time a view actually needs a map.

let loadPromise = null;

export function loadLeaflet() {
  if (loadPromise) return loadPromise;

  loadPromise = new Promise((resolve, reject) => {
    if (window.L) { resolve(window.L); return; }

    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'vendor/leaflet/leaflet.css';
    document.head.appendChild(link);

    const script = document.createElement('script');
    script.src = 'vendor/leaflet/leaflet.js';
    script.onload = () => {
      window.L.Icon.Default.mergeOptions({
        iconUrl: 'vendor/leaflet/images/marker-icon.png',
        iconRetinaUrl: 'vendor/leaflet/images/marker-icon-2x.png',
        shadowUrl: 'vendor/leaflet/images/marker-shadow.png',
      });
      resolve(window.L);
    };
    script.onerror = () => reject(new Error('Failed to load Leaflet (offline and not yet cached?)'));
    document.head.appendChild(script);
  });

  return loadPromise;
}
