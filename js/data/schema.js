// Object store definitions for the Life OS IndexedDB database.
// This is the single source of truth for schema — db.js reads it to run
// upgrades, api.js reads store names to build the CRUD surface.
//
// Every record store (i.e. everything except `settings`, which is a plain
// key-value store) carries `id`, `createdAt`, and `updatedAt` by convention.
// `updatedAt` is maintained here rather than deferred to the sync layer so
// last-write-wins comparisons have something to read from day one.

export const DB_NAME = 'lifeos';
// v2 adds the `_tombstones` store for Drive sync (records deletions so they
// propagate between devices instead of resurrecting from the other device's
// snapshot). runUpgrade in db.js creates any store that doesn't yet exist,
// so this bump is non-destructive for existing data.
export const DB_VERSION = 2;

export const STORES = [
  { name: 'settings', keyPath: 'key' },

  { name: 'tasks', keyPath: 'id', indexes: [
    { name: 'projectId', keyPath: 'projectId' },
    { name: 'status', keyPath: 'status' },
    { name: 'dueDate', keyPath: 'dueDate' },
  ] },
  { name: 'projects', keyPath: 'id', indexes: [
    { name: 'archived', keyPath: 'archived' },
  ] },

  { name: 'places', keyPath: 'id', indexes: [
    { name: 'listType', keyPath: 'listType' },
  ] },
  { name: 'bucketListItems', keyPath: 'id' },

  { name: 'links', keyPath: 'id', indexes: [
    { name: 'type', keyPath: 'type' },
    { name: 'status', keyPath: 'status' },
  ] },

  { name: 'semesters', keyPath: 'id' },
  { name: 'courses', keyPath: 'id', indexes: [
    { name: 'semesterId', keyPath: 'semesterId' },
  ] },
  { name: 'assignments', keyPath: 'id', indexes: [
    { name: 'courseId', keyPath: 'courseId' },
    { name: 'dueDate', keyPath: 'dueDate' },
    { name: 'status', keyPath: 'status' },
  ] },

  { name: 'bills', keyPath: 'id', indexes: [
    { name: 'dueDate', keyPath: 'dueDate' },
    { name: 'paid', keyPath: 'paid' },
    { name: 'category', keyPath: 'category' },
  ] },
  { name: 'billPayments', keyPath: 'id', indexes: [
    { name: 'billId', keyPath: 'billId' },
  ] },

  { name: 'books', keyPath: 'id', indexes: [
    { name: 'status', keyPath: 'status' },
  ] },
  { name: 'readingLogs', keyPath: 'id', indexes: [
    { name: 'bookId', keyPath: 'bookId' },
    { name: 'date', keyPath: 'date' },
  ] },

  { name: 'recipes', keyPath: 'id' },
  { name: 'cookLogs', keyPath: 'id', indexes: [
    { name: 'recipeId', keyPath: 'recipeId' },
  ] },

  // Plug-and-play language learning: one or more "packs" (Japanese, Spanish,
  // ...), each owning its own decks/cards (flashcard SRS) and lessons
  // (grammar/syntax/morphology explainers). Adding a language later is a
  // new LanguagePacks record, not a schema change.
  { name: 'languagePacks', keyPath: 'id', indexes: [
    { name: 'code', keyPath: 'code' },
  ] },
  { name: 'languageDecks', keyPath: 'id', indexes: [
    { name: 'packId', keyPath: 'packId' },
  ] },
  { name: 'languageCards', keyPath: 'id', indexes: [
    { name: 'deckId', keyPath: 'deckId' },
    { name: 'srsDueDate', keyPath: 'srs.dueDate' },
  ] },
  { name: 'languageReviewLogs', keyPath: 'id', indexes: [
    { name: 'cardId', keyPath: 'cardId' },
    { name: 'date', keyPath: 'date' },
  ] },
  { name: 'languageLessons', keyPath: 'id', indexes: [
    { name: 'packId', keyPath: 'packId' },
  ] },

  { name: 'chordProgressions', keyPath: 'id' },

  { name: 'financeSnapshots', keyPath: 'id', indexes: [
    { name: 'date', keyPath: 'date' },
  ] },
  { name: 'savingsGoals', keyPath: 'id' },
  { name: 'subscriptions', keyPath: 'id', indexes: [
    { name: 'stillInUse', keyPath: 'stillInUse' },
  ] },

  { name: 'documents', keyPath: 'id', indexes: [
    { name: 'expiryDate', keyPath: 'expiryDate' },
  ] },

  { name: 'contacts', keyPath: 'id', indexes: [
    { name: 'birthday', keyPath: 'birthday' },
  ] },

  { name: 'milestones', keyPath: 'id', indexes: [
    { name: 'date', keyPath: 'date' },
  ] },

  { name: 'habits', keyPath: 'id' },
  { name: 'habitLogs', keyPath: 'id', indexes: [
    { name: 'habitId', keyPath: 'habitId' },
    { name: 'date', keyPath: 'date' },
  ] },

  { name: 'healthLogs', keyPath: 'id', indexes: [
    { name: 'date', keyPath: 'date' },
  ] },

  { name: 'albums', keyPath: 'id' },

  // Binary assets (place/contact photos, bill/document PDFs, book covers, recipe photos).
  // `blob` holds a local offline-first copy; `driveFileId` is populated once the
  // sync layer uploads it. `relatedStore`/`relatedId` point back to the owning record.
  { name: 'attachments', keyPath: 'id', indexes: [
    { name: 'relatedStore', keyPath: 'relatedStore' },
    { name: 'relatedId', keyPath: 'relatedId' },
  ] },

  // Deletion log for Drive sync. Each record: { key: `${store}:${id}`,
  // store, id, deletedAt, driveFileId? }. Keyed by the composite `key` so a
  // deletion is uniquely addressable across stores. Never surfaced in the
  // UI; travels in each device's snapshot so deletes propagate.
  { name: '_tombstones', keyPath: 'key' },
];

export const STORE_NAMES = STORES.map((s) => s.name);
