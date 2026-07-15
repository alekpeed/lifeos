// Canonical list of app modules. This is app-level metadata, not presentation:
// every interface builds its own navigation from this list, rendering it
// however it wants (rail, ring, control panel...). Adding a module to the app
// means adding it here (plus its data stores in js/data/schema.js) — no
// interface code needs to know in advance.
//
// `remote: true` marks a module as part of the mobile remote's curated
// on-the-go set (see MOBILE_INTERFACES_SPEC.md's "Draft: what actually
// ships on the remote" -- this flag is that list, encoded). Modules without
// it still exist and work identically everywhere else; they're just not
// offered in the remote's nav. Purely a UI-surface distinction, not a data
// one -- see PROJECT_SPEC.md's "Device philosophy" section for why.

export const MODULES = [
  { id: 'dashboard', label: 'Today', group: 'core', remote: true },
  { id: 'orrery', label: 'Orrery', group: 'core' },
  { id: 'paper', label: 'Daily Paper', group: 'core', remote: true },
  { id: 'tasks', label: 'Tasks', group: 'core', remote: true },
  { id: 'ideas', label: 'Ideas', group: 'core', remote: true },
  { id: 'places', label: 'Places', group: 'core', remote: true },
  { id: 'links', label: 'Links', group: 'core', remote: true },
  { id: 'education', label: 'Education', group: 'core' },
  { id: 'finance', label: 'Finance', group: 'core' },
  { id: 'books', label: 'Books', group: 'memory' },
  { id: 'recipes', label: 'Recipes', group: 'people', remote: true },
  { id: 'documents', label: 'Documents', group: 'people', remote: true },
  { id: 'contacts', label: 'Contacts', group: 'people', remote: true },
  { id: 'milestones', label: 'Milestones', group: 'memory' },
  { id: 'photos', label: 'Photos', group: 'memory', remote: true },
  { id: 'sharebox', label: 'Sharebox', group: 'people' },
  { id: 'museum', label: 'Museum', group: 'memory' },
  { id: 'timecapsules', label: 'Time Capsules', group: 'memory' },
  { id: 'collections', label: 'Collections', group: 'memory' },
  { id: 'packing', label: 'Packing Lists', group: 'people', remote: true },
  { id: 'quartermaster', label: 'Quartermaster', group: 'people', remote: true },
  { id: 'ghostdays', label: 'Ghost Days', group: 'memory' },
  { id: 'rabbitholes', label: 'Rabbit Holes', group: 'memory' },
  // Life as Music: no longer a nav destination -- see js/audio/lifemusic.js
  // (ambient background loop, toggled in Settings) as of 2026-07-13.
  { id: 'habits', label: 'Habits', group: 'health', remote: true },
  { id: 'health', label: 'Health', group: 'health', remote: true },
  { id: 'skilltree', label: 'Skill Trees', group: 'health' },
  { id: 'almanac', label: 'The Almanac', group: 'health' },
  // Insight: the app surfacing/connecting/reasoning over your own data --
  // things you couldn't get by just opening a module's list.
  { id: 'briefing', label: 'Briefing', group: 'insight', remote: true },
  { id: 'assistant', label: 'AI Assistant', group: 'insight' },
  { id: 'ask', label: 'Ask', group: 'insight' },
  { id: 'knowledge', label: 'Knowledge Graph', group: 'insight' },
  { id: 'recall', label: 'Recall', group: 'insight', remote: true },
  { id: 'notifications', label: 'Notifications', group: 'insight', remote: true },
  { id: 'entropy', label: 'Entropy', group: 'insight' },
  { id: 'timemachine', label: 'Time Machine', group: 'insight' },
  // System & Tools: app machinery, everyday utilities, config, cosmetic.
  { id: 'search', label: 'Search', group: 'system', remote: true },
  { id: 'tools', label: 'Tools', group: 'system', remote: true },
  { id: 'qrsync', label: 'QR Sync', group: 'system', remote: true },
  { id: 'themefromphoto', label: 'Theme from Photo', group: 'system' },
  { id: 'stationcat', label: 'Station Cat', group: 'system' },
  { id: 'settings', label: 'Settings', group: 'system', remote: true },
];

export const MODULE_GROUPS = [
  { id: 'core', label: 'Core' },
  { id: 'memory', label: 'Memory & Keepsakes' },
  { id: 'people', label: 'People & Logistics' },
  { id: 'health', label: 'Health' },
  { id: 'insight', label: 'Insight' },
  { id: 'system', label: 'System & Tools' },
];

export const DEFAULT_MODULE = 'dashboard';

export function isValidModule(id) {
  return MODULES.some((m) => m.id === id);
}

// The mobile remote's curated module set -- see the `remote` flag above.
export function getRemoteModules() {
  return MODULES.filter((m) => m.remote);
}

export function isRemoteModule(id) {
  return MODULES.some((m) => m.id === id && m.remote);
}
