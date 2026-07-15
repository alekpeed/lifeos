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
// (geofenced notes-to-self on Places). v10 adds `chordPracticeLogs` (a
// freeform practice-session log, separate from the auto-tracked drill
// stats). v11 adds `aiConversations`/`aiMessages` (the AI Assistant module --
// the API key itself lives in Settings, device-local and unsynced; the
// conversation text is regular synced data like everything else). v12 adds
// `ideas` (a broad, unstructured capture-anything notes list). v13 adds
// `paperIssues` (the Daily Paper's AI editorial history, one record per
// local date + owner, kept so a new editorial can reference recent ones for
// continuity). v14 adds `resurfaceItems`/`resurfaceReviewLogs` (Recall — the
// language-flashcard SRS engine generalized to resurface any record in the
// app). v15 adds `assignmentProgressLogs` (the academic pacing check's
// dated progress log, one entry per logging session -- pacing target/unit/
// checkpoints themselves live directly on the assignment record, no schema
// change needed there). v16 retires Languages and Chords (cut from the app
// to keep scope tight -- the Chords engine lives on as a standalone starting
// point in CHORDS_APP_HANDOFF.md) -- see RETIRED_STORES below; this is the
// first version bump that actually deletes existing object stores rather
// than only skipping their creation for new installs. runUpgrade in db.js
// creates any store in STORES that doesn't yet exist and deletes any store
// in RETIRED_STORES that does, so these bumps are otherwise non-destructive.
// v17 retires Dream Journal (`dreamEntries`, cut from the app 2026-07-13)
// and closes a gap from v7: `libraryStories` (Library of Babel, dropped
// with Languages at v16) was removed from STORES but never added to
// RETIRED_STORES, so it was never actually deleted from existing installs.
// v18 adds `importedTransactions` (Finance's statement-import reconciliation).
// v19 adds `embeddings` (Semantic memory -- one vector per record for the Ask
// module's natural-language search; derived data, device-local, not synced).
export const DB_VERSION = 19;

// Stores that used to exist and are now actively deleted on upgrade, not
// just omitted from STORES going forward. `languageLessons` was retired
// earlier (see the old comment, now removed) by omission only -- it was
// never actually deleted from existing installs' databases; included here
// too so that gap finally closes.
export const RETIRED_STORES = [
  'languageLessons',
  'languagePacks', 'languageDecks', 'languageCards', 'languageReviewLogs',
  'chordProgressions', 'chordSkills', 'chordDrillLogs', 'chordPracticeLogs',
  'libraryStories', 'dreamEntries',
];

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
  // Academic pacing check: dated progress-log entries for a writing-type
  // assignment (pages/words added in one logging session). The target/unit/
  // self-set checkpoints ("6 pages by March 3") live directly on the
  // assignment record itself (pacingTarget/pacingUnit/paceCheckpoints) --
  // only the session-by-session history needs its own store, same shape as
  // readingLogs/cookLogs/habitLogs.
  { name: 'assignmentProgressLogs', keyPath: 'id', indexes: [
    { name: 'assignmentId', keyPath: 'assignmentId' },
    { name: 'date', keyPath: 'date' },
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

  // Languages and Chords (and their data stores: languagePacks/Decks/Cards/
  // ReviewLogs, chordProgressions/Skills/DrillLogs/PracticeLogs) were cut
  // from the app (2026-07-13) to keep scope tight -- see RETIRED_STORES
  // above and CHORDS_APP_HANDOFF.md, which preserves the Chords engine as a
  // standalone starting point for a separate music app.

  { name: 'financeSnapshots', keyPath: 'id', indexes: [
    { name: 'date', keyPath: 'date' },
  ] },
  { name: 'savingsGoals', keyPath: 'id' },
  { name: 'subscriptions', keyPath: 'id', indexes: [
    { name: 'stillInUse', keyPath: 'stillInUse' },
  ] },

  // Semantic memory: one embedding vector per indexed record, keyed by
  // `<store>:<recordId>`. Derived from record text via the Gemini embedding
  // API; used by the Ask module for natural-language search (client-side
  // cosine similarity). Device-local -- excluded from sync (each device
  // rebuilds its own index; vectors are big and cheap to regenerate).
  { name: 'embeddings', keyPath: 'id' },

  // Statement import: a one-time CSV import of bank/card transactions,
  // reconciled against Bills/Subscriptions by name+amount+date proximity.
  // Each row keeps its match decision (`status`: matched/unmatched/ignored)
  // and which record it was matched to, so a re-import of the same file
  // (or an overlapping date range) can be deduped by `importBatchId` +
  // the transaction's own natural key (date+description+amount).
  { name: 'importedTransactions', keyPath: 'id', indexes: [
    { name: 'date', keyPath: 'date' },
    { name: 'importBatchId', keyPath: 'importBatchId' },
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

  // Geofenced notes-to-self on Places: a short note attached to a place that
  // surfaces the next time the existing "Check nearby places" nudge (in
  // places.js) finds you within range of it. Distinct from the place's
  // freeform `notes` textarea -- these are meant to resurface, not just sit.
  { name: 'placeNotes', keyPath: 'id', indexes: [
    { name: 'placeId', keyPath: 'placeId' },
  ] },

  // AI Assistant: conversations with an LLM (Claude first; other providers
  // can plug in the same shape later). The API key is NOT stored here -- it
  // lives in Settings (device-local, excluded from sync), same as every
  // other credential in the app. The conversation text itself is regular
  // synced data, like any other module.
  { name: 'aiConversations', keyPath: 'id', indexes: [
    { name: 'provider', keyPath: 'provider' },
  ] },
  { name: 'aiMessages', keyPath: 'id', indexes: [
    { name: 'conversationId', keyPath: 'conversationId' },
  ] },

  // Ideas: a deliberately unstructured catch-all for anything worth jotting
  // down before it evaporates -- no folders/tags/module to pick first, since
  // that upfront categorization is exactly the friction that keeps stray
  // thoughts from getting captured at all.
  { name: 'ideas', keyPath: 'id', indexes: [
    { name: 'archived', keyPath: 'archived' },
  ] },

  // Daily Paper editorial history: one record per local date + owner (same
  // owner scoping as the Settings-based "today" cache — a signed-in
  // account's id, or 'local-anonymous'), so a new editorial can be given a
  // handful of recent ones for continuity/callbacks without re-reading
  // every module's raw data again.
  { name: 'paperIssues', keyPath: 'id', indexes: [
    { name: 'date', keyPath: 'date' },
    { name: 'owner', keyPath: 'owner' },
  ] },

  // Recall: the language-flashcard SRS engine generalized to resurface any
  // record in the app -- a book highlight, a contact you haven't reached out
  // to, a place you meant to revisit. `key` is the same "<store>:<id>"
  // composite addressing the Knowledge Graph uses (graphKey/resolveGraphNode
  // in api.js), so anything Search can find is schedulable here, reusing
  // that resolution instead of a second title-lookup table. `srs` is the
  // exact { interval, dueDate } shape languageCards already uses.
  { name: 'resurfaceItems', keyPath: 'id', indexes: [
    { name: 'key', keyPath: 'key' },
    { name: 'srsDueDate', keyPath: 'srs.dueDate' },
  ] },
  { name: 'resurfaceReviewLogs', keyPath: 'id', indexes: [
    { name: 'itemId', keyPath: 'itemId' },
    { name: 'date', keyPath: 'date' },
  ] },
];

export const STORE_NAMES = STORES.map((s) => s.name);
