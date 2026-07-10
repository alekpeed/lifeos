// The shared Data API. Every interface module reads and writes through this
// file — nothing outside js/data/ should import db.js or schema.js directly.
// Keeping that boundary here (rather than per-interface) is what lets a new
// interface be "just a view module" later, per the extensibility requirement.

import * as db from './db.js';
import { STORE_NAMES } from './schema.js';
import { events } from './events.js';

export { events };

// Google Drive sync lives in js/data/ too, behind the same api surface so
// interfaces reach it through ctx.data like everything else.
export { connectDrive, syncNow, disconnectDrive, getSyncState } from './sync.js';

// Google Calendar sync (one-way push of due-soon items) rides the same api
// surface. Independent of Drive sync — either can be used without the other.
export { connectCalendar, syncCalendarNow, disconnectCalendar, getCalendarState } from './calendar.js';

// Sharebox: a space shared with a friend through a Drive folder you both pick.
// Synced separately from personal data through that shared folder.
export { connectSharebox, syncShareboxNow, disconnectSharebox, getShareboxState } from './sharebox-sync.js';

// QR Airgap Sync: device-to-device over the local network, paired by QR
// code, no server/account/internet involved. Grouped under one namespace so
// the view reaches it through ctx.data like everything else.
import * as qrsync from './qrsync.js';
export const QrSync = {
  isSupported: qrsync.isQrSyncSupported,
  createOfferSession: qrsync.createOfferSession,
  acceptOffer: qrsync.acceptOffer,
  completeOffer: qrsync.completeOffer,
  mergeSnapshot: qrsync.mergeSnapshot,
};

// Sharebox v2 (Supabase-backed): the eventual replacement for the Drive path
// above. Grouped under one namespace so the view can flip between backends
// cleanly and so all the Supabase surface reaches interfaces through ctx.data
// like everything else — nothing imports the supabase-* modules directly. Inert
// until supabase-config.js has real credentials (isSupabaseConfigured()).
import * as shareboxV2 from './sharebox-supabase.js';
import * as shareboxAuth from './supabase-auth.js';
import { isSupabaseConfigured } from './supabase-config.js';
export const ShareboxV2 = {
  isSupabaseConfigured,
  // auth
  signInWithGoogle: shareboxAuth.signInWithGoogle,
  signOut: shareboxAuth.signOut,
  getCurrentUser: shareboxAuth.getCurrentUser,
  getSession: shareboxAuth.getSession,
  onAuthChange: shareboxAuth.onAuthChange,
  displayNameOf: shareboxAuth.displayNameOf,
  // spaces & membership
  getMySpaces: shareboxV2.getMySpaces,
  createSpace: shareboxV2.createSpace,
  joinSpace: shareboxV2.joinSpace,
  getMembers: shareboxV2.getMembers,
  // items
  listItems: shareboxV2.listItems,
  addItem: shareboxV2.addItem,
  updateItem: shareboxV2.updateItem,
  removeItem: shareboxV2.removeItem,
  // files
  uploadFile: shareboxV2.uploadFile,
  getFileUrl: shareboxV2.getFileUrl,
  // realtime
  subscribeToItems: shareboxV2.subscribeToItems,
};

function nowIso() {
  return new Date().toISOString();
}

// Records a deletion so Drive sync can propagate it to the other device
// instead of the record resurrecting from the other device's snapshot.
// Keyed by `${store}:${id}` so a delete is uniquely addressable; `extra`
// carries e.g. an attachment's driveFileId for binary cleanup.
async function recordTombstone(store, id, extra = {}) {
  await db.put('_tombstones', { key: `${store}:${id}`, store, id, deletedAt: nowIso(), ...extra });
}

// One CRUD shape (list/get/create/update/remove) is identical across every
// entity store, so it's generated once here rather than hand-written ~25 times.
function createEntityApi(storeName) {
  return {
    list: () => db.getAll(storeName),

    get: (id) => db.get(storeName, id),

    async create(data) {
      const record = {
        id: db.generateId(),
        createdAt: nowIso(),
        updatedAt: nowIso(),
        ...data,
      };
      await db.put(storeName, record);
      events.emit(storeName, { action: 'create', id: record.id });
      return record;
    },

    async update(id, patch) {
      const existing = await db.get(storeName, id);
      if (!existing) throw new Error(`${storeName}: no record with id ${id}`);
      const updated = { ...existing, ...patch, id, updatedAt: nowIso() };
      await db.put(storeName, updated);
      events.emit(storeName, { action: 'update', id });
      return updated;
    },

    async remove(id) {
      await db.remove(storeName, id);
      // The `_tombstones` store itself is never tombstoned; everything else
      // logs its deletion so sync can carry it across devices.
      if (storeName !== '_tombstones') await recordTombstone(storeName, id);
      events.emit(storeName, { action: 'remove', id });
    },

    byIndex: (indexName, query) => db.getAllByIndex(storeName, indexName, query),
  };
}

const entityStores = STORE_NAMES.filter((name) => name !== 'settings');
const entities = Object.fromEntries(entityStores.map((name) => [name, createEntityApi(name)]));

export const Tasks = entities.tasks;
export const Projects = entities.projects;
export const Places = entities.places;
export const BucketListItems = entities.bucketListItems;
export const Links = entities.links;
export const Semesters = entities.semesters;
export const Courses = entities.courses;
export const Assignments = entities.assignments;
export const Bills = entities.bills;
export const BillPayments = entities.billPayments;
export const Books = entities.books;
export const ReadingLogs = entities.readingLogs;
export const Recipes = entities.recipes;
export const CookLogs = entities.cookLogs;
export const LanguagePacks = entities.languagePacks;
export const LanguageDecks = entities.languageDecks;
export const LanguageCards = entities.languageCards;
export const LanguageReviewLogs = entities.languageReviewLogs;
export const ChordProgressions = entities.chordProgressions;
export const ChordSkills = entities.chordSkills;
export const ChordDrillLogs = entities.chordDrillLogs;
export const ShareboxItems = entities.shareboxItems;
export const ShareboxFiles = entities.shareboxFiles;

// Sharebox deletes log to their OWN tombstone store (personal sync clears
// `_tombstones` on every run, which would wipe shared-space deletions). The
// generic entity remove would write to `_tombstones`, so both are overridden.
async function recordShareboxTombstone(store, id, extra = {}) {
  await db.put('_shareboxTombstones', { key: `${store}:${id}`, store, id, deletedAt: nowIso(), ...extra });
}

ShareboxFiles.remove = async (id) => {
  const existing = await db.get('shareboxFiles', id);
  await db.remove('shareboxFiles', id);
  await recordShareboxTombstone('shareboxFiles', id, existing?.driveFileId ? { driveFileId: existing.driveFileId } : {});
  events.emit('shareboxFiles', { action: 'remove', id });
};

ShareboxItems.remove = async (id) => {
  // Cascade: removing an item removes its attached files (and their tombstones).
  const files = await db.getAllByIndex('shareboxFiles', 'itemId', id);
  for (const f of files) await ShareboxFiles.remove(f.id);
  await db.remove('shareboxItems', id);
  await recordShareboxTombstone('shareboxItems', id);
  events.emit('shareboxItems', { action: 'remove', id });
};

export async function createShareboxFile(file, itemId) {
  return ShareboxFiles.create({ itemId, filename: file.name, mimeType: file.type, blob: file, driveFileId: null });
}

export async function getShareboxFilesFor(itemId) {
  return db.getAllByIndex('shareboxFiles', 'itemId', itemId);
}

export function shareboxFileUrl(file) {
  return file?.blob ? URL.createObjectURL(file.blob) : null;
}
export const FinanceSnapshots = entities.financeSnapshots;
export const SavingsGoals = entities.savingsGoals;
export const Subscriptions = entities.subscriptions;
export const Documents = entities.documents;
export const Contacts = entities.contacts;
export const Milestones = entities.milestones;
export const Habits = entities.habits;
export const HabitLogs = entities.habitLogs;
export const HealthLogs = entities.healthLogs;
export const Albums = entities.albums;
export const Attachments = entities.attachments;
export const TimeCapsules = entities.timeCapsules;
export const Collections = entities.collections;
export const CollectionItems = entities.collectionItems;
export const PackingLists = entities.packingLists;
export const PackingItems = entities.packingItems;
export const InventoryItems = entities.inventoryItems;
export const DreamEntries = entities.dreamEntries;
export const RabbitHoles = entities.rabbitHoles;
export const LibraryStories = entities.libraryStories;

// --- Attachments: binary assets (place photos, bill/document PDFs, ...) ---
// Stored locally as a Blob for now; the Drive sync layer will populate
// driveFileId on each record once it uploads them.

export async function createAttachment(file, relatedStore, relatedId) {
  return Attachments.create({
    relatedStore,
    relatedId,
    filename: file.name,
    mimeType: file.type,
    blob: file,
    driveFileId: null,
  });
}

export async function getAttachmentsFor(relatedStore, relatedId) {
  const all = await Attachments.byIndex('relatedStore', relatedStore);
  return all.filter((a) => a.relatedId === relatedId);
}

const attachmentUrls = new Map(); // attachment id -> object URL, so we revoke on demand

export function attachmentUrl(attachment) {
  if (attachmentUrls.has(attachment.id)) return attachmentUrls.get(attachment.id);
  // During a sync pull an attachment's metadata can land locally a moment
  // before its binary finishes downloading; guard against a null blob so
  // callers get null (a broken-image placeholder) rather than a throw.
  if (!attachment?.blob) return null;
  const url = URL.createObjectURL(attachment.blob);
  attachmentUrls.set(attachment.id, url);
  return url;
}

export function revokeAttachmentUrl(id) {
  const url = attachmentUrls.get(id);
  if (url) {
    URL.revokeObjectURL(url);
    attachmentUrls.delete(id);
  }
}

const baseAttachmentsRemove = Attachments.remove.bind(Attachments);
Attachments.remove = async (id) => {
  // Capture driveFileId before deletion so the tombstone can tell a later
  // sync to delete the Drive binary too (best-effort — an orphaned Drive
  // file wastes a little space but never corrupts data).
  const existing = await db.get('attachments', id);
  revokeAttachmentUrl(id);
  await baseAttachmentsRemove(id); // deletes, writes a basic tombstone, emits
  if (existing?.driveFileId) {
    await recordTombstone('attachments', id, { driveFileId: existing.driveFileId });
  }
};

// --- One-time migration: Places used to keep its own lightweight "people"
// store for linked contacts; that's now folded into the full Contacts
// module so a person only ever has one record app-wide. This copies any
// pre-existing linked people (and their photos) into Contacts, repoints
// Places.peopleIds at the new ids, and clears the legacy store. Safe to
// call on every boot — it's a no-op once the legacy store is empty or was
// never created (fresh installs never create it in the first place).
export async function migrateLegacyPeopleToContacts() {
  let legacyPeople;
  try {
    legacyPeople = await db.getAll('people');
  } catch {
    return; // store doesn't exist on this device -- nothing to migrate
  }
  if (!legacyPeople.length) return;

  const idMap = new Map();
  for (const person of legacyPeople) {
    const contact = await Contacts.create({
      name: person.name,
      birthday: person.birthday || null,
      relationship: person.relationship || '',
      notes: person.notes || '',
    });
    idMap.set(person.id, contact.id);
  }

  const legacyPhotos = await Attachments.byIndex('relatedStore', 'people');
  for (const photo of legacyPhotos) {
    const newId = idMap.get(photo.relatedId);
    if (newId) await Attachments.update(photo.id, { relatedStore: 'contacts', relatedId: newId });
  }

  const places = await Places.list();
  for (const place of places) {
    if (!place.peopleIds?.length) continue;
    const remapped = place.peopleIds.map((id) => idMap.get(id) || id);
    await Places.update(place.id, { peopleIds: remapped });
  }

  await db.clear('people');
}

// --- One-time migration: the Japanese module used to own its own
// japaneseDecks/japaneseCards/japaneseReviewLogs stores. Language learning
// is now plug-and-play (multiple packs, Japanese being the first), so those
// become languageDecks/languageCards/languageReviewLogs scoped to a
// "Japanese" LanguagePacks record. Safe to call on every boot -- a no-op
// once the legacy stores are empty or were never created.
export async function migrateLegacyJapaneseToLanguagePacks() {
  let legacyDecks;
  try {
    legacyDecks = await db.getAll('japaneseDecks');
  } catch {
    return; // store doesn't exist on this device -- nothing to migrate
  }
  if (!legacyDecks.length) return;

  const pack = await ensureLanguagePack('ja', 'Japanese', 'ja-JP');

  const deckIdMap = new Map();
  for (const deck of legacyDecks) {
    const newDeck = await LanguageDecks.create({ packId: pack.id, name: deck.name });
    deckIdMap.set(deck.id, newDeck.id);
  }

  const legacyCards = await db.getAll('japaneseCards');
  const cardIdMap = new Map();
  for (const card of legacyCards) {
    const newDeckId = deckIdMap.get(card.deckId);
    if (!newDeckId) continue;
    const newCard = await LanguageCards.create({ deckId: newDeckId, front: card.front, back: card.back, srs: card.srs });
    cardIdMap.set(card.id, newCard.id);
  }

  const legacyLogs = await db.getAll('japaneseReviewLogs');
  for (const log of legacyLogs) {
    const newCardId = cardIdMap.get(log.cardId);
    if (!newCardId) continue;
    await LanguageReviewLogs.create({ cardId: newCardId, date: log.date, quality: log.quality });
  }

  await db.clear('japaneseDecks');
  await db.clear('japaneseCards');
  await db.clear('japaneseReviewLogs');
}

// Creates the pack if it doesn't already exist (matched by code); returns
// the existing or newly-created record either way. Called at boot to
// guarantee Japanese is always available out of the box, and reused by
// the migration above.
//
// Two sync-correctness details live here:
//   • Boot-seeded packs get a DETERMINISTIC id ("languagepack-<code>"), not
//     a random UUID — otherwise every device mints its own "Japanese" pack
//     and the first sync (Drive or QR) leaves both devices with duplicates.
//     With a shared id, the two seeds are the same record and merge cleanly.
//   • Self-healing for installs that already duplicated: if more than one
//     pack shares a code, keep the deterministic winner (oldest createdAt,
//     id as tiebreak — both devices independently pick the SAME winner),
//     re-point decks/stories at it, and delete the rest. The deletions
//     tombstone, so the cleanup itself propagates on the next sync.
export async function ensureLanguagePack(code, name, ttsLocale) {
  const matches = await LanguagePacks.byIndex('code', code);
  if (matches.length > 1) {
    matches.sort((a, b) => (a.createdAt || '').localeCompare(b.createdAt || '') || a.id.localeCompare(b.id));
    const keeper = matches[0];
    for (const dupe of matches.slice(1)) {
      for (const deck of await LanguageDecks.byIndex('packId', dupe.id)) {
        await LanguageDecks.update(deck.id, { packId: keeper.id });
      }
      for (const story of await LibraryStories.byIndex('packId', dupe.id)) {
        await LibraryStories.update(story.id, { packId: keeper.id });
      }
      await LanguagePacks.remove(dupe.id);
    }
    return keeper;
  }
  if (matches.length === 1) return matches[0];
  return LanguagePacks.create({ id: `languagepack-${code}`, code, name, ttsLocale });
}

// --- Settings: plain key-value store, separate shape from the entity stores. ---

const SETTING_DEFAULTS = {
  theme: 'dark',
  accent: 'brass',
  density: 'comfortable',
  activeInterface: 'default',
  wordsPerPageDefault: 275,
  billDueSoonDays: 7,
  documentExpiryDays: 30,
  // How far ahead (days) due items are mirrored into Google Calendar. Read by
  // the Calendar sync engine; overdue-but-open items are always included too.
  calendarHorizonDays: 90,
  // The name shown next to items you post in Sharebox (there are no accounts,
  // so each device sets its own). Empty until you set it.
  shareboxName: '',
  // Synth Sound-tab control style ('auto' picks knobs on precise-pointer
  // devices, detented sliders on touch) and panel skin (the classic-keyboard
  // look applied to the panel + knobs).
  synthControlStyle: 'auto',
  synthPanelSkin: 'meridian',
  // Manual/editable rate table (not a live feed) so the currency converter
  // stays usable fully offline. Rates are "1 <currency> = N base units" with
  // USD as the implicit base; edit them from the Tools module as needed.
  currencyRates: { USD: 1, EUR: 0.92, GBP: 0.79, JPY: 149, CAD: 1.36, AUD: 1.52 },
  savedTimezones: [
    { label: 'Tokyo', tz: 'Asia/Tokyo' },
    { label: 'London', tz: 'Europe/London' },
  ],
};

export const Settings = {
  async get(key) {
    const record = await db.get('settings', key);
    return record ? record.value : SETTING_DEFAULTS[key];
  },
  async set(key, value) {
    await db.put('settings', { key, value });
    events.emit('settings', { action: 'update', id: key });
    return value;
  },
  async getAll() {
    const records = await db.getAll('settings');
    const stored = Object.fromEntries(records.map((r) => [r.key, r.value]));
    return { ...SETTING_DEFAULTS, ...stored };
  },
};

// --- Cross-module query helpers (dashboard "due soon" feed, etc.) ---

function isWithinDays(dateStr, days) {
  if (!dateStr) return false;
  const target = new Date(dateStr);
  const now = new Date();
  const cutoff = new Date(now);
  cutoff.setDate(cutoff.getDate() + days);
  return target <= cutoff;
}

function isOverdue(dateStr) {
  if (!dateStr) return false;
  return new Date(dateStr) < new Date(new Date().toDateString());
}

// Merges tasks, bills, assignments, and documents into one date-sorted feed,
// tagged by source module, for the Dashboard's due-soon strip and overdue
// callout. Bills and documents get their own thresholds (Settings.
// billDueSoonDays / documentExpiryDays) since the brief calls those out as
// separately configurable alert windows.
export async function getDueSoonFeed(days = 7, billDays = days, documentDays = days) {
  const [tasks, bills, assignments, documents] = await Promise.all([
    Tasks.list(),
    Bills.list(),
    Assignments.list(),
    Documents.list(),
  ]);

  const items = [
    ...tasks
      .filter((t) => t.status !== 'done' && isWithinDays(t.dueDate, days))
      .map((t) => ({ module: 'tasks', id: t.id, title: t.title, dueDate: t.dueDate, overdue: isOverdue(t.dueDate) })),
    ...bills
      .filter((b) => !b.paid && isWithinDays(b.dueDate, billDays))
      .map((b) => ({ module: 'bills', id: b.id, title: b.name, dueDate: b.dueDate, overdue: isOverdue(b.dueDate) })),
    ...assignments
      .filter((a) => a.status !== 'done' && isWithinDays(a.dueDate, days))
      .map((a) => ({ module: 'assignments', id: a.id, title: a.title, dueDate: a.dueDate, overdue: isOverdue(a.dueDate) })),
    ...documents
      .filter((d) => isWithinDays(d.expiryDate, documentDays))
      .map((d) => ({ module: 'documents', id: d.id, title: d.title, dueDate: d.expiryDate, overdue: isOverdue(d.expiryDate) })),
  ];

  items.sort((a, b) => new Date(a.dueDate) - new Date(b.dueDate));
  return items;
}

// --- "On this day": anything dated with today's month/day in a past year,
// pulled from the modules where a look-back is actually meaningful. ---

export async function getOnThisDay() {
  const now = new Date();
  const todayMD = `${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const thisYear = String(now.getFullYear());
  const [milestones, places, books] = await Promise.all([Milestones.list(), Places.list(), Books.list()]);

  const items = [];
  for (const m of milestones) {
    if (!m.date) continue;
    const [y, md] = [m.date.slice(0, 4), m.date.slice(5, 10)];
    if (md === todayMD && y !== thisYear) items.push({ year: y, title: m.title, kind: 'Milestone' });
  }
  for (const p of places) {
    for (const d of p.visitDates || []) {
      const [y, md] = [d.slice(0, 4), d.slice(5, 10)];
      if (md === todayMD && y !== thisYear) items.push({ year: y, title: p.name, kind: 'Visited' });
    }
  }
  for (const b of books) {
    for (const [field, kind] of [['finishedDate', 'Finished reading'], ['startedDate', 'Started reading']]) {
      const d = b[field];
      if (!d) continue;
      const [y, md] = [d.slice(0, 4), d.slice(5, 10)];
      if (md === todayMD && y !== thisYear) items.push({ year: y, title: b.title, kind });
    }
  }

  items.sort((a, b) => b.year - a.year);
  return items;
}

// --- "Surprise me": one random thing to do, pulled from the pools where an
// undirected nudge is actually welcome (want-to-go places, unread books,
// untried recipes, open bucket-list goals). ---

export async function getSurpriseMe() {
  const [places, books, recipes, bucketItems, cookLogs] = await Promise.all([
    Places.list(), Books.list(), Recipes.list(), BucketListItems.list(), CookLogs.list(),
  ]);
  const cookedRecipeIds = new Set(cookLogs.map((l) => l.recipeId));

  const pool = [
    ...places.filter((p) => p.listType === 'wantToGo').map((p) => ({ module: 'places', title: p.name, kind: 'Place to visit' })),
    ...books.filter((b) => b.status === 'to_read').map((b) => ({ module: 'books', title: b.title, kind: 'Book to read' })),
    ...recipes.filter((r) => !cookedRecipeIds.has(r.id)).map((r) => ({ module: 'recipes', title: r.title, kind: 'Recipe to try' })),
    ...bucketItems.filter((b) => !b.done).map((b) => ({ module: 'places', title: b.title, kind: 'Bucket-list goal' })),
  ];
  if (!pool.length) return null;
  return pool[Math.floor(Math.random() * pool.length)];
}

// --- Milestones: year-in-review aggregation, pulled from every other module ---

export async function getYearInReview(year) {
  const y = String(year);
  const [tasks, places, books, readingLogs, cookLogs, billPayments, documents, milestones, contacts, assignments, habitLogs, healthLogs] = await Promise.all([
    Tasks.list(), Places.list(), Books.list(), ReadingLogs.list(), CookLogs.list(),
    BillPayments.list(), Documents.list(), Milestones.list(), Contacts.list(), Assignments.list(),
    HabitLogs.list(), HealthLogs.list(),
  ]);

  const tasksCompleted = tasks.filter((t) => t.status === 'done' && t.updatedAt?.startsWith(y)).length;
  const assignmentsCompleted = assignments.filter((a) => a.status === 'done' && a.updatedAt?.startsWith(y)).length;

  const visitsThisYear = places.flatMap((p) => (p.visitDates || []).filter((d) => d.startsWith(y)));
  const placesVisitedCount = new Set(
    places.filter((p) => (p.visitDates || []).some((d) => d.startsWith(y))).map((p) => p.id)
  ).size;

  const booksFinished = books.filter((b) => b.finishedDate?.startsWith(y)).length;
  const pagesRead = readingLogs.filter((l) => l.date?.startsWith(y)).reduce((sum, l) => sum + (l.pagesRead || 0), 0);

  const cookSessions = cookLogs.filter((l) => l.date?.startsWith(y));
  const recipesCookedCount = new Set(cookSessions.map((l) => l.recipeId)).size;

  const billsPaidTotal = billPayments.filter((p) => p.datePaid?.startsWith(y)).reduce((sum, p) => sum + Number(p.amountPaid || 0), 0);
  const documentsAdded = documents.filter((d) => d.createdAt?.startsWith(y)).length;
  const contactsAdded = contacts.filter((c) => c.createdAt?.startsWith(y)).length;
  const milestonesThisYear = milestones.filter((m) => m.date?.startsWith(y)).sort((a, b) => (a.date < b.date ? -1 : 1));

  const habitLogsThisYear = habitLogs.filter((l) => l.date?.startsWith(y));
  const healthLogsThisYear = healthLogs.filter((l) => l.date?.startsWith(y));
  const avgSleepHours = healthLogsThisYear.length
    ? healthLogsThisYear.reduce((sum, l) => sum + (Number(l.sleepHours) || 0), 0) / healthLogsThisYear.length
    : null;

  return {
    year: y,
    tasksCompleted,
    assignmentsCompleted,
    placesVisitedCount,
    totalVisits: visitsThisYear.length,
    booksFinished,
    pagesRead,
    recipesCookedCount,
    cookSessions: cookSessions.length,
    billsPaidTotal,
    documentsAdded,
    contactsAdded,
    milestones: milestonesThisYear,
    habitCheckIns: habitLogsThisYear.length,
    avgSleepHours,
    healthLogCount: healthLogsThisYear.length,
  };
}

// --- Global search: one query across every module with a title-like field.
// Returns { store, module, id, title } so a view can group by module and
// navigate there on click (module list, not deep-linking to the exact
// record -- each view keeps its own selection state internally).

const SEARCH_FIELDS = {
  tasks: (r) => r.title,
  places: (r) => r.name,
  links: (r) => r.title || r.url,
  semesters: (r) => r.name,
  courses: (r) => r.name,
  assignments: (r) => r.title,
  bills: (r) => r.name,
  subscriptions: (r) => r.name,
  books: (r) => [r.title, r.author].filter(Boolean).join(' '),
  recipes: (r) => r.title,
  documents: (r) => r.title,
  contacts: (r) => [r.name, r.company].filter(Boolean).join(' '),
  milestones: (r) => r.title,
  habits: (r) => r.name,
  languageDecks: (r) => r.name,
  chordProgressions: (r) => r.name,
  albums: (r) => r.name,
};

const SEARCH_MODULE_ROUTE = {
  tasks: 'tasks', places: 'places', links: 'links', semesters: 'education', courses: 'education',
  assignments: 'education', bills: 'finance', subscriptions: 'finance', books: 'books',
  recipes: 'recipes', documents: 'documents', contacts: 'contacts', milestones: 'milestones',
  habits: 'habits', languageDecks: 'languages',
  chordProgressions: 'chords', albums: 'photos',
};

export async function globalSearch(query) {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const storeNames = Object.keys(SEARCH_FIELDS);
  const results = await Promise.all(storeNames.map(async (name) => {
    const records = await db.getAll(name);
    return records
      .map((r) => ({ record: r, title: SEARCH_FIELDS[name](r) || '' }))
      .filter(({ title }) => title.toLowerCase().includes(q))
      .map(({ record, title }) => ({ store: name, module: SEARCH_MODULE_ROUTE[name], id: record.id, title: title || '(untitled)' }));
  }));
  return results.flat().slice(0, 200);
}

// --- Knowledge Graph: link any record to any other record ---
//
// Edges live in the graphLinks store, endpoints addressed as "<store>:<id>"
// composite keys. The graph deliberately reuses globalSearch's SEARCH_FIELDS
// / SEARCH_MODULE_ROUTE as its definition of "what is linkable": anything
// findable in Search is linkable in the graph, and nothing else -- one
// list to maintain, two features fed by it.

export const GraphLinks = entities.graphLinks;

export function graphKey(store, id) {
  return `${store}:${id}`;
}

// Every edge touching a record, regardless of which end it's stored on
// (edges are undirected in meaning; from/to is storage order only).
export async function getGraphLinksFor(store, id) {
  const key = graphKey(store, id);
  const [from, to] = await Promise.all([
    db.getAllByIndex('graphLinks', 'fromKey', key),
    db.getAllByIndex('graphLinks', 'toKey', key),
  ]);
  return [...from, ...to];
}

// Create an edge unless one already exists between the same pair (in either
// direction). Returns the existing edge instead of a duplicate.
export async function createGraphLink(fromStore, fromId, toStore, toId) {
  const a = graphKey(fromStore, fromId);
  const b = graphKey(toStore, toId);
  if (a === b) throw new Error('Cannot link a record to itself.');
  const existing = await getGraphLinksFor(fromStore, fromId);
  const dupe = existing.find((l) =>
    (l.fromKey === a && l.toKey === b) || (l.fromKey === b && l.toKey === a));
  if (dupe) return dupe;
  return GraphLinks.create({ fromKey: a, toKey: b });
}

// Resolve a graph endpoint to something renderable. `exists: false` means
// the underlying record was deleted after the link was made -- the caller
// shows a tombstone and offers unlink, rather than the edge silently lying.
export async function resolveGraphNode(key) {
  const sep = key.indexOf(':');
  const store = key.slice(0, sep);
  const id = key.slice(sep + 1);
  const titleOf = SEARCH_FIELDS[store];
  if (!titleOf) return { key, store, id, title: '(unknown type)', module: null, exists: false };
  const record = await db.get(store, id);
  if (!record) return { key, store, id, title: '(deleted)', module: SEARCH_MODULE_ROUTE[store], exists: false };
  return { key, store, id, title: titleOf(record) || '(untitled)', module: SEARCH_MODULE_ROUTE[store], exists: true };
}

// --- Manual JSON export/import: a Drive-independent backup. Attachments'
// Blob fields aren't JSON-serializable, so they're round-tripped through
// data: URLs (readAsDataURL / fetch().blob()) rather than raw base64 math.

function blobToDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

async function dataUrlToBlob(dataUrl) {
  const response = await fetch(dataUrl);
  return response.blob();
}

export async function exportAllData() {
  const data = {};
  for (const name of STORE_NAMES) {
    if (name === 'attachments') continue;
    data[name] = await db.getAll(name);
  }
  const attachments = await db.getAll('attachments');
  data.attachments = await Promise.all(
    attachments.map(async (a) => ({ ...a, blob: await blobToDataUrl(a.blob) }))
  );
  return { app: 'lifeos', exportedAt: nowIso(), version: 1, data };
}

// Wholesale-replaces every store's contents with the imported payload. The
// caller should reload the page afterward -- in-memory view state and
// cached attachment object URLs elsewhere in the app don't know the
// underlying data just changed out from under them.
export async function importAllData(payload) {
  const data = payload?.data;
  if (!data || typeof data !== 'object') throw new Error('Not a recognized Life OS export file');

  for (const name of STORE_NAMES) {
    if (!Array.isArray(data[name])) continue;
    await db.clear(name);
    for (const record of data[name]) {
      if (name === 'attachments' && typeof record.blob === 'string') {
        await db.put(name, { ...record, blob: await dataUrlToBlob(record.blob) });
      } else {
        await db.put(name, record);
      }
    }
  }
}
