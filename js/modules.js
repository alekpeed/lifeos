// Canonical list of app modules. This is app-level metadata, not presentation:
// every interface builds its own navigation from this list, rendering it
// however it wants (rail, ring, control panel...). Adding a module to the app
// means adding it here (plus its data stores in js/data/schema.js) — no
// interface code needs to know in advance.

export const MODULES = [
  { id: 'dashboard', label: 'Today', group: 'core' },
  { id: 'paper', label: 'Daily Paper', group: 'core' },
  { id: 'tasks', label: 'Tasks', group: 'core' },
  { id: 'places', label: 'Places', group: 'core' },
  { id: 'links', label: 'Links', group: 'core' },
  { id: 'education', label: 'Education', group: 'core' },
  { id: 'finance', label: 'Finance', group: 'core' },
  { id: 'books', label: 'Books', group: 'life' },
  { id: 'recipes', label: 'Recipes', group: 'life' },
  { id: 'documents', label: 'Documents', group: 'life' },
  { id: 'contacts', label: 'Contacts', group: 'life' },
  { id: 'milestones', label: 'Milestones', group: 'life' },
  { id: 'photos', label: 'Photos', group: 'life' },
  { id: 'sharebox', label: 'Sharebox', group: 'life' },
  { id: 'museum', label: 'Museum', group: 'life' },
  { id: 'timecapsules', label: 'Time Capsules', group: 'life' },
  { id: 'collections', label: 'Collections', group: 'life' },
  { id: 'packing', label: 'Packing Lists', group: 'life' },
  { id: 'quartermaster', label: 'Quartermaster', group: 'life' },
  { id: 'ghostdays', label: 'Ghost Days', group: 'life' },
  { id: 'starters', label: 'Conversation Starters', group: 'life' },
  { id: 'rabbitholes', label: 'Rabbit Holes', group: 'life' },
  { id: 'languages', label: 'Languages', group: 'study' },
  { id: 'libraryofbabel', label: 'Library of Babel', group: 'study' },
  { id: 'chords', label: 'Chords', group: 'study' },
  { id: 'lifeasmusic', label: 'Life as Music', group: 'study' },
  { id: 'habits', label: 'Habits', group: 'health' },
  { id: 'health', label: 'Health', group: 'health' },
  { id: 'skilltree', label: 'Skill Trees', group: 'health' },
  { id: 'dreamjournal', label: 'Dream Journal', group: 'health' },
  { id: 'almanac', label: 'The Almanac', group: 'health' },
  { id: 'entropy', label: 'Entropy', group: 'utility' },
  { id: 'stationcat', label: 'Station Cat', group: 'utility' },
  { id: 'themefromphoto', label: 'Theme from Photo', group: 'utility' },
  { id: 'tools', label: 'Tools', group: 'utility' },
  { id: 'search', label: 'Search', group: 'utility' },
  { id: 'settings', label: 'Settings', group: 'utility' },
];

export const MODULE_GROUPS = [
  { id: 'core', label: 'Core' },
  { id: 'life', label: 'Life' },
  { id: 'study', label: 'Study' },
  { id: 'health', label: 'Health' },
  { id: 'utility', label: 'Utility' },
];

export const DEFAULT_MODULE = 'dashboard';

export function isValidModule(id) {
  return MODULES.some((m) => m.id === id);
}
