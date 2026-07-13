// A small, original line-icon set for Test Mode's nav -- one per module.
// Deliberately basic: every icon is built from plain SVG primitives (line,
// circle, rect, polyline), not illustrative artwork, so the nav reads as a
// considered instrument panel rather than a picture book. All icons share
// one 20x20 grid, 1.6 stroke, round caps/joins, and `stroke="currentColor"`
// so they inherit the nav item's own text color (muted by default, accent
// when active) with zero extra CSS.

const S = 'fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"';

function svg(body) {
  return `<svg viewBox="0 0 20 20" width="18" height="18" ${S}>${body}</svg>`;
}

const ICONS = {
  dashboard: svg('<path d="M3 10 10 4l7 6"/><path d="M5 9v7h10V9"/>'),
  orrery: svg('<circle cx="10" cy="10" r="1.4" fill="currentColor" stroke="none"/><circle cx="10" cy="10" r="4"/><circle cx="10" cy="10" r="7.5"/>'),
  paper: svg('<rect x="4" y="3" width="12" height="14" rx="1"/><line x1="6.5" y1="7" x2="13.5" y2="7"/><line x1="6.5" y1="10" x2="13.5" y2="10"/><line x1="6.5" y1="13" x2="11" y2="13"/>'),
  tasks: svg('<rect x="3.5" y="3.5" width="13" height="13" rx="1.5"/><polyline points="6.3,10 8.5,12.2 13.2,7.4"/>'),
  ideas: svg('<path d="M7 15h6"/><path d="M8 17.5h4"/><path d="M10 2.5a5 5 0 0 1 2.6 9.3c-.6.4-1.1 1.1-1.1 1.9v.3H8.5v-.3c0-.8-.4-1.5-1.1-1.9A5 5 0 0 1 10 2.5z"/>'),
  places: svg('<path d="M10 17.5S4.5 12.2 4.5 8a5.5 5.5 0 0 1 11 0c0 4.2-5.5 9.5-5.5 9.5z"/><circle cx="10" cy="8" r="2"/>'),
  links: svg('<path d="M8.3 11.7 11.7 8.3"/><path d="M9 5.5 11 3.6a3 3 0 0 1 4.4 4.1L13.6 9.5"/><path d="M11 14.5 9 16.4a3 3 0 0 1-4.4-4.1l1.8-1.9"/>'),
  education: svg('<path d="M2.5 8 10 4.5 17.5 8 10 11.5z"/><path d="M5.5 9.6v3.6c0 1.1 2 2.3 4.5 2.3s4.5-1.2 4.5-2.3V9.6"/><line x1="17.5" y1="8" x2="17.5" y2="13"/>'),
  finance: svg('<line x1="5" y1="16.5" x2="5" y2="10.5"/><line x1="10" y1="16.5" x2="10" y2="6"/><line x1="15" y1="16.5" x2="15" y2="12.5"/><line x1="3" y1="16.5" x2="17" y2="16.5"/>'),
  books: svg('<path d="M10 5.3C8.8 4.3 6.7 3.8 4.5 3.8v11.4c2.2 0 4.3.5 5.5 1.5"/><path d="M10 5.3c1.2-1 3.3-1.5 5.5-1.5v11.4c-2.2 0-4.3.5-5.5 1.5z"/>'),
  recipes: svg('<path d="M6 3v6a1.5 1.5 0 0 0 3 0V3"/><line x1="7.5" y1="9" x2="7.5" y2="17"/><path d="M13.5 3c-1 0-1.8 1.4-1.8 3.2s.8 3.2 1.8 3.2"/><line x1="13.5" y1="3" x2="13.5" y2="17"/>'),
  documents: svg('<path d="M6 2.5h6l3 3v11.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-13.5a1 1 0 0 1 1-1z"/><path d="M12 2.5V6h3"/>'),
  contacts: svg('<circle cx="10" cy="7" r="3"/><path d="M4 17c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5"/>'),
  milestones: svg('<line x1="5" y1="17.5" x2="5" y2="3"/><path d="M5 3.5h9l-2.5 3.5L14 10.5H5"/>'),
  photos: svg('<rect x="2.5" y="4.5" width="15" height="11" rx="1.2"/><circle cx="7" cy="8.5" r="1.4"/><path d="M4 15l4.5-4.5 3 3 2-2L17 15"/>'),
  sharebox: svg('<circle cx="15.5" cy="5" r="2"/><circle cx="15.5" cy="15" r="2"/><circle cx="4.5" cy="10" r="2"/><line x1="6.3" y1="9" x2="13.7" y2="5.9"/><line x1="6.3" y1="11" x2="13.7" y2="14.1"/>'),
  museum: svg('<path d="M5 3h10v3a5 5 0 0 1-10 0z"/><line x1="10" y1="11" x2="10" y2="15"/><line x1="6.5" y1="17" x2="13.5" y2="17"/><line x1="10" y1="15" x2="10" y2="17"/>'),
  timecapsules: svg('<path d="M5.5 3h9"/><path d="M5.5 17h9"/><path d="M6 3c0 3 2.5 4 4 5.5C8.5 10 6 11 6 14v3"/><path d="M14 3c0 3-2.5 4-4 5.5 1.5 1.5 4 2.5 4 5.5v3"/>'),
  collections: svg('<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="11.5" y="3" width="5.5" height="5.5" rx="1"/><rect x="3" y="11.5" width="5.5" height="5.5" rx="1"/><rect x="11" y="10.5" width="6" height="6" rx="1"/>'),
  packing: svg('<rect x="3" y="6.5" width="14" height="10" rx="1.3"/><path d="M7.5 6.5V5a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1.5"/><line x1="3" y1="11" x2="17" y2="11"/>'),
  quartermaster: svg('<path d="M10 2.8 17 6.4 10 10l-7-3.6z"/><path d="M3 6.8v7l7 3.6 7-3.6v-7"/><line x1="10" y1="10" x2="10" y2="17.2"/>'),
  ghostdays: svg('<rect x="3" y="4.5" width="14" height="12" rx="1.3"/><line x1="3" y1="8" x2="17" y2="8"/><line x1="6.5" y1="2.8" x2="6.5" y2="6"/><line x1="13.5" y1="2.8" x2="13.5" y2="6"/><circle cx="10" cy="12.3" r="1.7"/>'),
  starters: svg('<path d="M3 4.5h14v8.5H8.5L5 16v-3H3z"/>'),
  rabbitholes: svg('<path d="M17 10a7 7 0 1 1-3-5.7"/><path d="M17 4v4h-4"/>'),
  lifeasmusic: svg('<line x1="4" y1="12" x2="4" y2="16"/><line x1="7.5" y1="7" x2="7.5" y2="16"/><line x1="11" y1="4" x2="11" y2="16"/><line x1="14.5" y1="8.5" x2="14.5" y2="16"/><line x1="18" y1="11" x2="18" y2="16"/>'),
  habits: svg('<path d="M4 10a6 6 0 0 1 10.4-4.1"/><polyline points="14.4,2.8 14.4,5.9 11.3,5.9"/><path d="M16 10a6 6 0 0 1-10.4 4.1"/><polyline points="5.6,17.2 5.6,14.1 8.7,14.1"/>'),
  health: svg('<path d="M10 17S3 12.3 3 7.6A3.6 3.6 0 0 1 10 6a3.6 3.6 0 0 1 7 1.6C17 12.3 10 17 10 17z"/>'),
  skilltree: svg('<line x1="10" y1="17.5" x2="10" y2="10"/><line x1="10" y1="10" x2="5" y2="4.5"/><line x1="10" y1="10" x2="15" y2="4.5"/><circle cx="5" cy="3.2" r="1.4"/><circle cx="15" cy="3.2" r="1.4"/><circle cx="10" cy="8.7" r="1.4"/>'),
  dreamjournal: svg('<path d="M15.5 11.8A6 6 0 1 1 8.2 4.5a5 5 0 0 0 7.3 7.3z"/>'),
  almanac: svg('<circle cx="10" cy="10" r="7.2"/><polyline points="10,5.5 10,10 13.2,12"/>'),
  assistant: svg('<rect x="3" y="4" width="14" height="9.5" rx="2"/><path d="M7 13.5v2.8l3.2-2.8"/><circle cx="7.2" cy="8.7" r="0.9" fill="currentColor" stroke="none"/><circle cx="10" cy="8.7" r="0.9" fill="currentColor" stroke="none"/><circle cx="12.8" cy="8.7" r="0.9" fill="currentColor" stroke="none"/>'),
  knowledge: svg('<circle cx="5" cy="5.5" r="2"/><circle cx="15" cy="5.5" r="2"/><circle cx="10" cy="15" r="2"/><line x1="6.6" y1="6.6" x2="9.1" y2="13.3"/><line x1="13.4" y1="6.6" x2="10.9" y2="13.3"/><line x1="7" y1="5.5" x2="13" y2="5.5"/>'),
  recall: svg('<path d="M4 10a6 6 0 1 1 1.8 4.3"/><polyline points="2.3,9 4,10.8 5.8,9"/><circle cx="10" cy="10" r="1.5" fill="currentColor" stroke="none"/>'),
  timemachine: svg('<circle cx="10" cy="11" r="6.5"/><polyline points="10,7.5 10,11 12.7,12.5"/><path d="M6.5 3.3 4 4.7"/><path d="M13.5 3.3 16 4.7"/>'),
  qrsync: svg('<rect x="3" y="3" width="5.5" height="5.5"/><rect x="11.5" y="3" width="5.5" height="5.5"/><rect x="3" y="11.5" width="5.5" height="5.5"/><line x1="12.2" y1="12.2" x2="12.2" y2="17"/><line x1="15" y1="11.5" x2="17" y2="11.5"/><line x1="15" y1="15" x2="17" y2="15"/><line x1="15" y1="17.5" x2="17" y2="17.5"/>'),
  entropy: svg('<circle cx="4.5" cy="5" r="1.1" fill="currentColor" stroke="none"/><circle cx="12" cy="3.5" r="1.1" fill="currentColor" stroke="none"/><circle cx="16" cy="8.5" r="1.1" fill="currentColor" stroke="none"/><circle cx="6" cy="11" r="1.1" fill="currentColor" stroke="none"/><circle cx="13.5" cy="13" r="1.1" fill="currentColor" stroke="none"/><circle cx="4" cy="16" r="1.1" fill="currentColor" stroke="none"/><circle cx="10.5" cy="17" r="1.1" fill="currentColor" stroke="none"/>'),
  stationcat: svg('<path d="M5 8 3.5 3l3.3 2.7a7.6 7.6 0 0 1 6.4 0L16.5 3 15 8"/><path d="M5 8a5 5 0 0 0 10 0c0 4-2.2 6.5-5 6.5S5 12 5 8z"/><circle cx="7.8" cy="8.3" r="0.7" fill="currentColor" stroke="none"/><circle cx="12.2" cy="8.3" r="0.7" fill="currentColor" stroke="none"/>'),
  themefromphoto: svg('<path d="M10 3a7 7 0 1 0 0 14c1 0 1.5-.6 1.5-1.3s-.4-.9-.4-1.5c0-.7.6-1.2 1.4-1.2H14a3 3 0 0 0 3-3A6.5 6.5 0 0 0 10 3z"/><circle cx="6.5" cy="9" r="1" fill="currentColor" stroke="none"/><circle cx="9" cy="6.3" r="1" fill="currentColor" stroke="none"/><circle cx="12.5" cy="7" r="1" fill="currentColor" stroke="none"/>'),
  tools: svg('<path d="M12.4 7.6 15 5a3 3 0 0 0-4-1l-.9 3.5-2 2-1 4-3.6 3.6.9.9L8 14.4l4-1 2-2L14.5 8a3 3 0 0 0-1-3.9z"/>'),
  search: svg('<circle cx="8.8" cy="8.8" r="5"/><line x1="12.6" y1="12.6" x2="17" y2="17"/>'),
  settings: svg('<circle cx="10" cy="10" r="2.6"/><path d="M10 3.5v2.1M10 14.4v2.1M16.5 10h-2.1M5.6 10H3.5M14.6 5.4l-1.5 1.5M6.9 13.1l-1.5 1.5M14.6 14.6l-1.5-1.5M6.9 6.9 5.4 5.4"/>'),
};

const FALLBACK = svg('<circle cx="10" cy="10" r="6.5"/>');

export function moduleIconMarkup(moduleId) {
  return ICONS[moduleId] || FALLBACK;
}
