// Object store definitions for the Life OS IndexedDB database.
// This is the single source of truth for schema — db.js reads it to run
// upgrades, api.js reads store names to build the CRUD surface.
//
// Every record store (i.e. everything except `settings`, which is a plain
// key-value store) carries `id`, `createdAt`, and `updatedAt` by convention.
// `updatedAt` is maintained here rather than deferred to the sync layer so
// last-write-wins comparisons have something to read from day one.

export const DB_NAME = 'lifeos';
export const DB_VERSION = 1;

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
  { name: 'people', keyPath: 'id', indexes: [
    { name: 'birthday', keyPath: 'birthday' },
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

  { name: 'japaneseDecks', keyPath: 'id' },
  { name: 'japaneseCards', keyPath: 'id', indexes: [
    { name: 'deckId', keyPath: 'deckId' },
    { name: 'srsDueDate', keyPath: 'srs.dueDate' },
  ] },
  { name: 'japaneseReviewLogs', keyPath: 'id', indexes: [
    { name: 'cardId', keyPath: 'cardId' },
    { name: 'date', keyPath: 'date' },
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

  { name: 'contacts', keyPath: 'id' },

  { name: 'milestones', keyPath: 'id', indexes: [
    { name: 'date', keyPath: 'date' },
  ] },

  // Binary assets (place photos, bill/document PDFs, book covers, recipe photos).
  // `blob` holds a local offline-first copy; `driveFileId` is populated once the
  // sync layer uploads it. `relatedStore`/`relatedId` point back to the owning record.
  { name: 'attachments', keyPath: 'id', indexes: [
    { name: 'relatedStore', keyPath: 'relatedStore' },
    { name: 'relatedId', keyPath: 'relatedId' },
  ] },
];

export const STORE_NAMES = STORES.map((s) => s.name);
