// Canonical list of app modules. This is app-level metadata, not presentation:
// every interface builds its own navigation from this list, rendering it
// however it wants (rail, ring, control panel...). Adding a module to the app
// means adding it here (plus its data stores in js/data/schema.js) — no
// interface code needs to know in advance.

export const MODULES = [
  { id: 'dashboard', label: 'Today', group: 'core' },
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
  { id: 'japanese', label: 'Japanese', group: 'study' },
  { id: 'chords', label: 'Chords', group: 'study' },
  { id: 'habits', label: 'Habits', group: 'health' },
  { id: 'health', label: 'Health', group: 'health' },
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
