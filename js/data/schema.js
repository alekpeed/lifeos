// Object store definitions for the Life OS IndexedDB database.
// This is the single source of truth for schema — db.js reads it to run
// upgrades, api.js reads store names to build the CRUD surface.
//
// Every record store (i.e. everything except `settings`, which is a plain
// key-value store) carries `id`, `createdAt`, and `updatedAt` by convention.
// `updatedAt` is maintained here rather than deferred to the sync layer so
// last-write-wins comparisons have something to read from day one.

export const DB_NAME = 'lifeos';
// v2 added the `_tombstones` store for Drive sync (records deletions so they
// propagate between devices instead of resurrecting from the other device's
// snapshot). v3 adds `chordSkills`/`chordDrillLogs` for the chord practice
// drills. v4 adds the Sharebox stores (`shareboxItems`, `shareboxFiles`,
// `_shareboxTombstones`) for the shared-with-a-friend space. v5 adds
// `timeCapsules`, `collections`/`collectionItems`, `packingLists`/
// `packingItems`, and `inventoryItems`. v6 adds `dreamEntries` and
// `rabbitHoles`. v7 adds `libraryStories`. v8 adds `graphLinks` (the
// Knowledge Graph's link-anything-to-anything edges). v9 adds `placeNotes`
// (geofenced notes-to-self on Places). runUpgrade in db.js creates any
// store that doesn't yet exist, so these bumps are non-destructive for
// existing data.
export const DB_VERSION = 9;

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
  // languageLessons was retired along with the Languages module's Lessons
  // tab, replaced by the Library of Babel module. Removed from the schema
  // list (not just unused) so new installs never create it and global
  // search's SEARCH_FIELDS/SEARCH_MODULE_ROUTE (js/data/api.js) no longer
  // reference it either -- leaving it in either place while removed here
  // would throw "object store not found" for any browser that never had it.

  { name: 'chordProgressions', keyPath: 'id' },

  // Chord practice drills. `chordSkills` is keyed by CONCEPT id (e.g.
  // 'spell:maj7', 'voicing:drop2') — one record per trackable skill, holding
  // SRS state (interval/dueDate) and accuracy counters (attempts/correct).
  // `chordDrillLogs` is the append-only history of every graded answer.
  { name: 'chordSkills', keyPath: 'id' },
  { name: 'chordDrillLogs', keyPath: 'id', indexes: [
    { name: 'date', keyPath: 'date' },
    { name: 'conceptId', keyPath: 'conceptId' },
  ] },

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

  // Sharebox: a small space shared with a friend through a Drive folder you
  // both pick (separate from your private LifeOS/ sync — nothing here touches
  // your other modules). Items are links/notes/files with an urgency flag and
  // a "postedBy" name. File binaries live in `shareboxFiles` (kept out of the
  // generic attachments store so personal sync never uploads shared files to
  // your private Drive). Deletions log to a SEPARATE tombstone store because
  // personal sync clears/rewrites `_tombstones` on every run.
  { name: 'shareboxItems', keyPath: 'id', indexes: [
    { name: 'urgency', keyPath: 'urgency' },
  ] },
  { name: 'shareboxFiles', keyPath: 'id', indexes: [
    { name: 'itemId', keyPath: 'itemId' },
  ] },
  { name: '_shareboxTombstones', keyPath: 'key' },

  // Time Capsules: a sealed note to your future self. `sealedUntil` is a date
  // string; the view treats anything with sealedUntil <= today as openable.
  { name: 'timeCapsules', keyPath: 'id', indexes: [
    { name: 'sealedUntil', keyPath: 'sealedUntil' },
  ] },

  // Collections Tracker: any freeform collection (records, cards, whatever)
  // and its items. Deliberately schemaless beyond name/notes/tags -- no
  // per-collection custom fields, to keep this simple.
  { name: 'collections', keyPath: 'id' },
  { name: 'collectionItems', keyPath: 'id', indexes: [
    { name: 'collectionId', keyPath: 'collectionId' },
  ] },

  // Trip Packing Lists: one list per trip, items checked off as packed.
  { name: 'packingLists', keyPath: 'id' },
  { name: 'packingItems', keyPath: 'id', indexes: [
    { name: 'listId', keyPath: 'listId' },
  ] },

  // Quartermaster: physical inventory + a lending ledger (who has it, since when).
  { name: 'inventoryItems', keyPath: 'id', indexes: [
    { name: 'lentTo', keyPath: 'lentTo' },
  ] },

  // Dream Journal: one entry per dream. Recurring-pattern detection is
  // computed client-side from body/tags text across all entries -- nothing
  // extra stored per entry for it.
  { name: 'dreamEntries', keyPath: 'id', indexes: [
    { name: 'date', keyPath: 'date' },
  ] },

  // Rabbit Hole Journal: research tangents, each with freeform notes and a
  // list of links, closed out (or not) as `status`.
  { name: 'rabbitHoles', keyPath: 'id', indexes: [
    { name: 'status', keyPath: 'status' },
  ] },

  // Knowledge Graph: an edge between any two records anywhere in the app.
  // Endpoints are addressed as "<store>:<id>" composite keys (fromKey/toKey)
  // so one store can hold links between heterogeneous record types without
  // foreign-key machinery. Edges are undirected in meaning -- from/to is
  // storage order only, and queries always check both indexes. Titles are
  // NOT denormalized onto the edge; they're resolved live at render time so
  // a renamed task/book/contact never leaves a stale label in the graph.
  { name: 'graphLinks', keyPath: 'id', indexes: [
    { name: 'fromKey', keyPath: 'fromKey' },
    { name: 'toKey', keyPath: 'toKey' },
  ] },

  // Library of Babel: a story-based reading library per language pack --
  // short graded stories (with an optional translation/gloss) you write or
  // paste in yourself, read in-app, and mark as read. Additive alongside
  // the Languages module's existing Lessons tab (grammar explainers) rather
  // than replacing it -- both are useful, and removing already-shipped
  // functionality isn't this feature's call to make unilaterally.
  { name: 'libraryStories', keyPath: 'id', indexes: [
    { name: 'packId', keyPath: 'packId' },
  ] },

  // Geofenced notes-to-self on Places: a short note attached to a place that
  // surfaces the next time the existing "Check nearby places" nudge (in
  // places.js) finds you within range of it. Distinct from the place's
  // freeform `notes` textarea -- these are meant to resurface, not just sit.
  { name: 'placeNotes', keyPath: 'id', indexes: [
    { name: 'placeId', keyPath: 'placeId' },
  ] },
];

export const STORE_NAMES = STORES.map((s) => s.name);
