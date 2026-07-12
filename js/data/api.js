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

// Google Photos import via the Picker API — a one-shot pick, not a
// persistent connection like Drive/Calendar (see photos-picker.js for why).
export { pickGooglePhotos } from './photos-picker.js';
export { isAppLockAvailable, enrollAppLock, verifyAppLock } from './applock.js';
export { parseAppleHealthExport } from './apple-health-import.js';

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

// Account: app-wide identity (email/password + Google), independent of
// Sharebox. Shares the same underlying Supabase auth session as ShareboxV2
// above (one supabase-auth.js module, one signed-in user) -- signing in here
// also signs you into Sharebox, and vice versa. `profiles` is its own table
// (sql/supabase-accounts-schema.sql) distinct from Sharebox's per-space
// display name.
import * as profileSupabase from './profile-supabase.js';
export const Account = {
  isSupabaseConfigured,
  signInWithGoogle: shareboxAuth.signInWithGoogle,
  signUpWithEmail: shareboxAuth.signUpWithEmail,
  signInWithEmail: shareboxAuth.signInWithEmail,
  sendPasswordReset: shareboxAuth.sendPasswordReset,
  updatePassword: shareboxAuth.updatePassword,
  signOut: shareboxAuth.signOut,
  getCurrentUser: shareboxAuth.getCurrentUser,
  getSession: shareboxAuth.getSession,
  onAuthChange: shareboxAuth.onAuthChange,
  displayNameOf: shareboxAuth.displayNameOf,
  getProfile: profileSupabase.getProfile,
  updateDisplayName: profileSupabase.updateDisplayName,
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
export const AssignmentProgressLogs = entities.assignmentProgressLogs;

// --- Academic pacing check: compares real logged progress
// (AssignmentProgressLogs) against a self-set pacing checkpoint stored
// directly on the assignment (paceCheckpoints -- your own stated intention,
// e.g. "6 pages by March 3"; the app never invents one). Pure/synchronous
// so both the Education view and the Daily Paper's source packet can share
// the exact same gap calculation. ---

function localTodayStr() {
  return new Date().toISOString().slice(0, 10);
}

// The most recent checkpoint whose date has already passed, compared
// against everything logged so far. Returns null if there's no checkpoint
// due yet (nothing to compare against). A non-positive gap means on track
// or ahead -- callers decide whether that's worth surfacing.
export function pacingStatusFor(assignment, logs) {
  const due = (assignment.paceCheckpoints || [])
    .filter((cp) => cp.date <= localTodayStr())
    .sort((a, b) => b.date.localeCompare(a.date));
  if (!due.length) return null;
  const checkpoint = due[0];
  const loggedTotal = logs.reduce((sum, l) => sum + (Number(l.unitsAdded) || 0), 0);
  return { checkpoint, loggedTotal, gap: checkpoint.targetByThen - loggedTotal };
}

// Every open (not-done) assignment with a real, positive gap against its
// own most recent past checkpoint. Used by the Daily Paper's source packet --
// only ever a real logged number vs. a number you typed in yourself, never
// an invented pacing curve.
export async function getAssignmentPacingGaps() {
  const [assignments, allLogs, courses] = await Promise.all([Assignments.list(), AssignmentProgressLogs.list(), Courses.list()]);
  const courseById = new Map(courses.map((c) => [c.id, c]));
  const logsByAssignment = new Map();
  for (const log of allLogs) {
    if (!logsByAssignment.has(log.assignmentId)) logsByAssignment.set(log.assignmentId, []);
    logsByAssignment.get(log.assignmentId).push(log);
  }
  const gaps = [];
  for (const a of assignments) {
    if (a.status === 'done') continue;
    const status = pacingStatusFor(a, logsByAssignment.get(a.id) || []);
    if (status && status.gap > 0) gaps.push({ assignment: a, course: courseById.get(a.courseId), ...status });
  }
  return gaps;
}

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
export const ChordPracticeLogs = entities.chordPracticeLogs;
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
export const PlaceNotes = entities.placeNotes;
export const AiConversations = entities.aiConversations;
export const AiMessages = entities.aiMessages;
export const Ideas = entities.ideas;
export const PaperIssues = entities.paperIssues;

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
  // Temporary: default a no-preference session (new device, or a "Clear
  // site data" that wiped IndexedDB along with the cache) into Vespera
  // instead of Equator while it's the one being actively worked on -- flip
  // back to 'default' once Vespera is done getting shaped. Anyone who has
  // explicitly picked an interface via Settings has a real stored value
  // here and is unaffected.
  activeInterface: 'vespera',
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
  // Currency rate cache for the Tools converter. Populated from a live feed
  // (open.er-api.com, keyless, ~160 currencies) when online, but stored here
  // (and editable) so the converter still works fully offline from the
  // last-fetched or manually-entered values. Rates are "1 USD = N
  // <currency>". Seeded with ~50 major currencies so most people don't need
  // to add their own; a live refresh (auto-triggered when stale, or via the
  // Refresh button) covers essentially all of these plus anything else you
  // add manually.
  currencyRates: {
    USD: 1, EUR: 0.92, GBP: 0.79, JPY: 149, CHF: 0.88, CAD: 1.36, AUD: 1.52, NZD: 1.64,
    CNY: 7.24, HKD: 7.82, SGD: 1.34, INR: 83.3, KRW: 1330, MXN: 17.0, BRL: 5.1, ZAR: 18.5,
    SEK: 10.4, NOK: 10.6, DKK: 6.87, PLN: 4.0, TRY: 32.0, RUB: 92.0, THB: 35.5, IDR: 15700,
    MYR: 4.7, PHP: 56.5, VND: 24500, ILS: 3.7, AED: 3.6725, SAR: 3.75, EGP: 48.0, NGN: 1550,
    KES: 129, PKR: 278, BDT: 110, CZK: 23.2, HUF: 355, RON: 4.57, BGN: 1.8, UAH: 39.5,
    ARS: 880, CLP: 950, COP: 3900, PEN: 3.75, TWD: 31.5, QAR: 3.64, KWD: 0.307, BHD: 0.376,
    OMR: 0.3845, JOD: 0.709, ISK: 137,
  },
  // ISO timestamp of the last successful live-rate fetch, or null if the
  // rates above are still just the hardcoded/manual defaults.
  currencyRatesFetchedAt: null,
  savedTimezones: [
    { label: 'Tokyo', tz: 'Asia/Tokyo' },
    { label: 'London', tz: 'Europe/London' },
  ],
  // Home location for the Dashboard/Daily Paper weather blurb (Open-Meteo,
  // keyless). Null until the user opts in via "Use my location".
  weatherLocation: null, // { lat, lng, label }
  // Last-fetched weather, cached so it survives offline/reload; see
  // getWeather() below for the staleness policy.
  weatherCache: null,
  // Coin IDs (CoinGecko's slugs, e.g. "bitcoin", "ethereum") for the
  // Finance > Crypto ticker tab. Empty until the user adds one.
  cryptoWatchlist: [],
  // Last-fetched prices, cached so the tab still shows something offline;
  // see getCryptoPrices() below for the staleness policy.
  cryptoPricesCache: null,
  // Last-fetched DJIA quote; see getDjiaPrice() below for staleness policy.
  djiaCache: null,
  // AI Assistant: which provider is active (see AI_PROVIDERS below) and each
  // provider's own device-local key/model, kept out of Drive/cloud sync.
  // Both providers' keys can be filled in at once -- the toggle in Settings
  // just switches which one the app actually calls, so swapping back and
  // forth never requires re-entering a key.
  aiProvider: 'gemini',
  geminiApiKey: '',
  geminiModel: 'gemini-2.5-flash',
  anthropicApiKey: '',
  anthropicModel: 'claude-sonnet-5',
  // Telegram: a bot you create yourself (via @BotFather) and your own chat
  // ID, so the app can message you. Send-only -- there's no listener for
  // incoming messages. Both empty until you add them in Settings.
  telegramBotToken: '',
  telegramChatId: '',
  // Daily Paper's "On the Docket" checkmarks -- which local calendar date
  // they belong to, so a stale array from a previous day is ignored instead
  // of carried over (the reset "at midnight" is just this date no longer
  // matching todayStr(), computed from the device's own local clock).
  paperChecklistDate: '',
  paperChecklistChecked: [],
  // Daily Paper's AI-written editorial -- cached per local date (same
  // pattern as the checklist above) so opening the paper repeatedly in one
  // day doesn't re-call Claude each time. Empty/blank until generated.
  paperEditorialDate: '',
  paperEditorialText: '',
  paperEditorialOwner: '',
  // Milestones' AI-written yearly recap narrative -- cached per year (not
  // per day like the Daily Paper) and per account, same reasoning as above:
  // don't re-call the AI provider every time the Yearly Recap tab renders,
  // only when the selected year or signed-in account actually changes.
  recapNarrativeYear: '',
  recapNarrativeText: '',
  recapNarrativeOwner: '',
  // App Lock: gates the app behind the device's own biometric/PIN
  // authenticator (WebAuthn platform authenticator) before any data
  // renders. Off until the user enrolls in Settings; appLockCredentialId
  // is the enrolled passkey's ID, empty until enrollment.
  appLockEnabled: false,
  appLockCredentialId: '',
  // Rules & automation engine v1: a small, fixed set of built-in rules
  // (not a general rule-builder), each off by default -- an automation
  // mutates your data on your behalf, so it should never turn itself on.
  // See runAutomations() below.
  automationHabitMilestoneEnabled: false,
  automationDocumentRenewalEnabled: false,
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

// --- Weather: current conditions for the Dashboard/Daily Paper (Open-Meteo,
// keyless). Opt-in -- weatherLocation stays null until the user sets it. ---

const OPEN_METEO_URL = 'https://api.open-meteo.com/v1/forecast';
const WEATHER_STALE_MS = 60 * 60 * 1000;

const WEATHER_CODES = {
  0: { label: 'Clear sky', icon: '☀️' }, 1: { label: 'Mainly clear', icon: '🌤️' },
  2: { label: 'Partly cloudy', icon: '⛅' }, 3: { label: 'Overcast', icon: '☁️' },
  45: { label: 'Fog', icon: '🌫️' }, 48: { label: 'Fog', icon: '🌫️' },
  51: { label: 'Light drizzle', icon: '🌦️' }, 53: { label: 'Drizzle', icon: '🌦️' }, 55: { label: 'Heavy drizzle', icon: '🌧️' },
  61: { label: 'Light rain', icon: '🌦️' }, 63: { label: 'Rain', icon: '🌧️' }, 65: { label: 'Heavy rain', icon: '🌧️' },
  71: { label: 'Light snow', icon: '🌨️' }, 73: { label: 'Snow', icon: '🌨️' }, 75: { label: 'Heavy snow', icon: '❄️' },
  80: { label: 'Rain showers', icon: '🌦️' }, 81: { label: 'Rain showers', icon: '🌧️' }, 82: { label: 'Violent showers', icon: '⛈️' },
  95: { label: 'Thunderstorm', icon: '⛈️' }, 96: { label: 'Thunderstorm, hail', icon: '⛈️' }, 99: { label: 'Thunderstorm, hail', icon: '⛈️' },
};

export function describeWeatherCode(code) {
  return WEATHER_CODES[code] || { label: 'Unknown', icon: '🌡️' };
}

// Returns null if no location is set. Otherwise returns cached/live weather;
// never throws -- a fetch failure just falls back to whatever's cached.
export async function getWeather() {
  const location = await Settings.get('weatherLocation');
  if (!location) return null;

  const cache = await Settings.get('weatherCache');
  const sameSpot = cache && cache.lat === location.lat && cache.lng === location.lng;
  const fresh = sameSpot && (Date.now() - new Date(cache.fetchedAt).getTime()) < WEATHER_STALE_MS;
  if (fresh) return cache;

  try {
    const url = `${OPEN_METEO_URL}?latitude=${location.lat}&longitude=${location.lng}&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&temperature_unit=fahrenheit&timezone=auto`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    const result = {
      lat: location.lat, lng: location.lng, label: location.label,
      tempF: data.current?.temperature_2m ?? null,
      code: data.current?.weather_code ?? null,
      highF: data.daily?.temperature_2m_max?.[0] ?? null,
      lowF: data.daily?.temperature_2m_min?.[0] ?? null,
      fetchedAt: new Date().toISOString(),
    };
    await Settings.set('weatherCache', result);
    return result;
  } catch {
    return sameSpot ? cache : null;
  }
}

// --- Crypto price tickers (CoinGecko, keyless). Opt-in via a watchlist of
// coin IDs; empty watchlist means nothing is fetched. ---

const COINGECKO_URL = 'https://api.coingecko.com/api/v3/simple/price';
const CRYPTO_STALE_MS = 5 * 60 * 1000;

// Returns { coinId: { usd, usd_24h_change } }, or {} if the watchlist is
// empty. Never throws -- a fetch failure falls back to the cache.
export async function getCryptoPrices() {
  const watchlist = await Settings.get('cryptoWatchlist');
  if (!watchlist.length) return {};

  const cache = await Settings.get('cryptoPricesCache');
  const sameList = cache && cache.coins.join(',') === watchlist.join(',');
  const fresh = sameList && (Date.now() - new Date(cache.fetchedAt).getTime()) < CRYPTO_STALE_MS;
  if (fresh) return cache.prices;

  try {
    const url = `${COINGECKO_URL}?ids=${watchlist.map(encodeURIComponent).join(',')}&vs_currencies=usd&include_24hr_change=true`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const prices = await res.json();
    await Settings.set('cryptoPricesCache', { coins: watchlist, prices, fetchedAt: new Date().toISOString() });
    return prices;
  } catch {
    return sameList ? cache.prices : {};
  }
}

// --- DJIA ticker (Stooq, keyless -- no stock API key needed since this is
// just the one index, not arbitrary tickers). ---

const STOOQ_DJIA_URL = 'https://stooq.com/q/l/?s=^dji&f=sd2t2ohlc&h&e=csv';
const DJIA_STALE_MS = 5 * 60 * 1000;

// Returns { price, changePct, fetchedAt } or the last cache on failure/offline.
export async function getDjiaPrice() {
  const cache = await Settings.get('djiaCache');
  if (cache && (Date.now() - new Date(cache.fetchedAt).getTime()) < DJIA_STALE_MS) return cache;

  try {
    const res = await fetch(STOOQ_DJIA_URL);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const csv = await res.text();
    const [, dataLine] = csv.trim().split('\n');
    const [, , , open, , , close] = dataLine.split(',');
    const price = Number(close);
    const changePct = open && Number(open) ? ((Number(close) - Number(open)) / Number(open)) * 100 : null;
    const result = { price, changePct, fetchedAt: new Date().toISOString() };
    await Settings.set('djiaCache', result);
    return result;
  } catch {
    return cache || null;
  }
}

// --- AI Assistant (provider-switchable, direct browser-to-API) ---
// Conversations/messages are regular synced data (aiConversations/aiMessages
// above); each provider's own API key stays in Settings, device-local and
// unsynced.
//
// Provider note: this started Claude-only, then switched to Gemini so the
// browser could keep calling it directly with no backend (Anthropic and
// Gemini both support direct-browser calls; OpenAI's API structurally does
// not -- it sends no CORS headers for browser-origin requests at all,
// unlike Anthropic's documented opt-in, so an OpenAI entry here would need
// a proxy server in front of it, not just a key). Rather than hardcode one
// active provider, aiProvider in Settings picks between the two working
// ones -- a small toggle in Settings > AI Assistant -- so switching back
// and forth costs nothing and neither client needs deleting.
import { sendClaudeMessage } from './claude-client.js';
import { sendGeminiMessage } from './gemini-client.js';

export const AI_PROVIDERS = {
  gemini: { label: 'Gemini', send: sendGeminiMessage, keySetting: 'geminiApiKey', modelSetting: 'geminiModel', keyPlaceholder: 'AIza…', modelDefault: 'gemini-2.5-flash' },
  claude: { label: 'Claude', send: sendClaudeMessage, keySetting: 'anthropicApiKey', modelSetting: 'anthropicModel', keyPlaceholder: 'sk-ant-…', modelDefault: 'claude-sonnet-5' },
};

// Resolves the active provider's id/label/send fn plus its own key+model, in
// one call -- every AI-calling function below goes through this instead of
// each re-reading Settings and re-picking a client.
export async function getActiveAiProvider() {
  const id = (await Settings.get('aiProvider')) || 'gemini';
  const meta = AI_PROVIDERS[id] || AI_PROVIDERS.gemini;
  const [apiKey, model] = await Promise.all([Settings.get(meta.keySetting), Settings.get(meta.modelSetting)]);
  return { id, ...meta, apiKey, model };
}

export async function createAiConversation(title) {
  const { id } = await getActiveAiProvider();
  return AiConversations.create({ title: title || 'New conversation', provider: id });
}

export async function getAiMessages(conversationId) {
  const messages = await AiMessages.byIndex('conversationId', conversationId);
  return messages.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
}

// Persists the user's message, calls the active provider with the full
// conversation history, persists the reply, and returns the reply message.
// Throws the provider's own error (bad key, rate limit, ...) if the call
// fails -- the user's message stays saved either way, so nothing is lost on
// a failure.
export async function sendAiMessage(conversationId, userText) {
  await AiMessages.create({ conversationId, role: 'user', content: userText });

  const [{ send, apiKey, model }, history] = await Promise.all([
    getActiveAiProvider(),
    getAiMessages(conversationId),
  ]);
  const providerMessages = history.map((m) => ({ role: m.role, content: m.content }));
  const { text } = await send(apiKey, providerMessages, { model });

  return AiMessages.create({ conversationId, role: 'assistant', content: text });
}

// --- Library of Babel: AI-generated stories, gated the same way as the
// AI Assistant chat -- each device needs the active provider's own key in
// Settings (no shared/sponsored path here), so this naturally stays
// per-person if other people ever pick up the same pack. ---

function parseGeneratedStory(text) {
  const storyMatch = text.match(/STORY:\s*([\s\S]*?)(?:\nTRANSLATION:|$)/i);
  const translationMatch = text.match(/TRANSLATION:\s*([\s\S]*)$/i);
  const titleMatch = text.match(/TITLE:\s*(.+)/i);
  return {
    title: titleMatch ? titleMatch[1].trim() : 'Generated story',
    body: (storyMatch ? storyMatch[1] : text).trim(),
    translation: translationMatch ? translationMatch[1].trim() : '',
  };
}

// Generates one short story at `level` for the given language, avoiding
// recent titles so the shelf doesn't repeat itself. Throws (key missing,
// rate limit, etc.) rather than swallowing errors -- the caller decides how
// to surface that.
export async function generateLibraryStory(packName, level, recentTitles = []) {
  const { send, apiKey, model } = await getActiveAiProvider();
  const avoid = recentTitles.length ? ` Avoid repeating these previous titles/themes: ${recentTitles.join(', ')}.` : '';
  const prompt = `Write a very short story in ${packName} for a ${level} learner of the language.${avoid} `
    + `Keep vocabulary and grammar appropriate for that level. Respond in exactly this format, nothing else:\n\n`
    + `TITLE: <short title in ${packName}>\nSTORY:\n<the story, in ${packName}>\nTRANSLATION:\n<full English translation>`;
  const { text } = await send(apiKey, [{ role: 'user', content: prompt }], { model, maxTokens: 1200 });
  return parseGeneratedStory(text);
}

// Nudges a pack's tracked difficulty level after read-story feedback.
// Clamped at the ends of LEVELS (views/languages.js) rather than passed in,
// since the ordering itself is fixed there.
export function adjustLevel(levels, currentLevel, feedback) {
  const i = Math.max(0, levels.indexOf(currentLevel));
  if (feedback === 'easy') return levels[Math.min(levels.length - 1, i + 1)];
  if (feedback === 'hard') return levels[Math.max(0, i - 1)];
  return levels[i];
}

// --- Daily Paper: AI-written editorial (provider-switchable, direct
// browser-to-API, same per-device key as the Assistant chat -- no
// sponsored/shared path). Caching (which local date it belongs to) lives in
// Settings and is handled by the caller (views/paper.js), same as the
// checklist above -- this function just does the one call given an
// already-built summary.

export async function generateDailyEditorial(summary) {
  const { send, apiKey, model } = await getActiveAiProvider();
  const prompt = `You are writing the editorial at the top of a private personal daily paper. `
    + `Use only the facts in the source packet below. Never invent an event, deadline, habit result, weather condition, motive, feeling, or causal claim. `
    + `If data is unavailable, ignore it. Prioritize overdue work, today's concrete obligations, and genuine progress. `
    + `Be warm, perceptive, and concise—not chirpy, scolding, or generic. Write 3-5 sentences in second person ("you"). `
    + `Mention one or two exact specifics naturally, then offer a practical focus for the day. `
    + `The packet may include a handful of your own recent past editorials, oldest first, purely for continuity. `
    + `Only reference one when a genuine callback adds real value — a promise kept, a pattern continuing, follow-through on something flagged before. `
    + `Most days should stand entirely on their own with no callback at all; never force one, never repeat old phrasing verbatim, and never invent what a past entry said beyond what's shown. `
    + `Return plain prose only: no title, markdown, bullets, greeting, or sign-off.\n\nSOURCE PACKET\n${summary}`;
  const { text } = await send(apiKey, [{ role: 'user', content: prompt }], { model, maxTokens: 400 });
  return text.trim();
}

// Persists (or updates, if regenerated) the finalized editorial for one
// local date + owner into the durable history used for continuity, kept
// separate from the Settings-based "today" cache above (which just tracks
// what to show right now without re-calling the AI). One record per
// date+owner -- a same-day regenerate replaces it rather than appending a
// duplicate.
export async function saveEditorialIssue(date, owner, text, provider) {
  const existing = (await PaperIssues.byIndex('date', date)).find((r) => r.owner === owner);
  if (existing) return PaperIssues.update(existing.id, { text, provider });
  return PaperIssues.create({ date, owner, text, provider });
}

// The last `limit` editorials for this owner, oldest first, excluding
// today's (there isn't one yet when this is called, but excluding it keeps
// this safe to call from anywhere).
export async function getRecentEditorials(owner, excludeDate, limit = 5) {
  const all = await PaperIssues.byIndex('owner', owner);
  return all
    .filter((r) => r.date !== excludeDate && r.text)
    .sort((a, b) => b.date.localeCompare(a.date))
    .slice(0, limit)
    .reverse();
}

// --- Milestones: AI-written yearly recap narrative (provider-switchable,
// same reasoning as the Daily Paper editorial above -- a bounded, factual
// packet in, a grounded narrative out, no invention). Caching (which year/
// account it belongs to) lives in Settings and is handled by the caller
// (views/milestones.js), same shape as the Daily Paper's date-based cache.

export async function generateYearlyRecapNarrative(summary) {
  const { send, apiKey, model } = await getActiveAiProvider();
  const prompt = `You are writing the opening narrative for someone's personal year-in-review, drawn from their own life-tracking app. `
    + `Use only the facts in the source packet below. Never invent an event, achievement, feeling, or detail not explicitly present. `
    + `If a milestone list is present, weave in one or two of the most notable ones by name naturally -- don't just recite the stat totals back. `
    + `If data is sparse or a category is empty, don't dwell on it or apologize for it. `
    + `Be warm and reflective, not a corporate "wrapped" summary and not overly sentimental. Write 4-6 sentences in second person ("you"). `
    + `Return plain prose only: no title, markdown, bullets, greeting, or sign-off.\n\nSOURCE PACKET\n${summary}`;
  const { text } = await send(apiKey, [{ role: 'user', content: prompt }], { model, maxTokens: 500 });
  return text.trim();
}

// --- Telegram (send-only) ---

import { sendTelegramMessage } from './telegram-client.js';

export async function sendDigestToTelegram(text) {
  const [botToken, chatId] = await Promise.all([
    Settings.get('telegramBotToken'),
    Settings.get('telegramChatId'),
  ]);
  return sendTelegramMessage(botToken, chatId, text);
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

// --- AI-suggested knowledge-graph edges: beyond manual linking, ask the
// active AI provider to propose genuinely non-obvious connections from a
// focus record. Same anti-hallucination discipline as the Daily Paper/
// Milestones narrative: the AI is only ever given a closed, numbered
// candidate list drawn from real records and can only pick indices from
// it -- never free text, so it can't invent a connection to something that
// doesn't exist. Manual/button-triggered, not automatic, to keep API calls
// (and their cost) opt-in rather than firing on every graph view.

const SUGGEST_CANDIDATE_CAP = 120; // keeps the prompt bounded; most-recent records first

async function listAllLinkable(excludeKey, excludeKeys) {
  const storeNames = Object.keys(SEARCH_FIELDS);
  const results = await Promise.all(storeNames.map(async (name) => {
    const records = await db.getAll(name);
    return records
      .map((r) => ({ record: r, title: SEARCH_FIELDS[name](r) || '' }))
      .filter(({ title }) => title)
      .map(({ record, title }) => ({
        store: name, module: SEARCH_MODULE_ROUTE[name], id: record.id,
        key: graphKey(name, record.id), title,
        updatedAt: record.updatedAt || record.createdAt || '',
      }));
  }));
  return results.flat()
    .filter((r) => r.key !== excludeKey && !excludeKeys.has(r.key))
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, SUGGEST_CANDIDATE_CAP);
}

// Returns [] (not an error) when there's no AI key configured or nothing
// linkable yet -- the caller treats an empty result as "nothing to show."
export async function suggestGraphEdges(focusKey) {
  const focus = await resolveGraphNode(focusKey);
  if (!focus.exists) return [];

  const existingLinks = await getGraphLinksFor(focus.store, focus.id);
  const linkedKeys = new Set(existingLinks.map((l) => (l.fromKey === focusKey ? l.toKey : l.fromKey)));
  const candidates = await listAllLinkable(focusKey, linkedKeys);
  if (!candidates.length) return [];

  const { send, apiKey, model } = await getActiveAiProvider();
  if (!apiKey) return [];

  const list = candidates.map((c, i) => `${i}. [${c.module}] ${c.title}`).join('\n');
  const prompt = `You are looking at a personal database. The focus record is: "${focus.title}" (${focus.module}).\n\n`
    + `Below is a numbered list of OTHER records in the same database. Suggest up to 5 that have a genuine, non-obvious connection to the focus record worth linking -- not just the same category, not a coincidental word match, a real relationship a person would actually want drawn between them.\n\n`
    + `Only choose from the numbered list below; never invent an item that isn't listed. If nothing has a real connection, return nothing.\n\n`
    + `Respond with one line per suggestion, exactly in this format, nothing else:\nINDEX: one-sentence reason\n\n`
    + `CANDIDATES\n${list}`;
  const { text } = await send(apiKey, [{ role: 'user', content: prompt }], { model, maxTokens: 300 });

  const suggestions = [];
  for (const line of text.split('\n')) {
    const m = line.match(/^\s*(\d+)\s*[:.]\s*(.+)$/);
    if (!m) continue;
    const idx = Number(m[1]);
    if (!Number.isInteger(idx) || idx < 0 || idx >= candidates.length) continue;
    suggestions.push({ ...candidates[idx], reason: m[2].trim() });
  }
  return suggestions.slice(0, 5);
}

// --- Camera-to-data capture: photograph a document/bill/ID and have the
// active AI provider's vision input auto-fill a new Documents record
// instead of typing it by hand. Same closed-input discipline as everywhere
// else the AI touches this app: the model only ever sees the one image and
// is told explicitly to use null for anything not clearly legible, never to
// guess -- the caller (documents.js) always shows the result as an editable
// draft, not a silently-trusted final record.

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(',')[1]);
    reader.onerror = () => reject(reader.error || new Error('Could not read the image.'));
    reader.readAsDataURL(file);
  });
}

function parseJsonLoosely(text) {
  const stripped = text.trim().replace(/^```(?:json)?\n?/i, '').replace(/```$/, '');
  try {
    return JSON.parse(stripped);
  } catch {
    return null;
  }
}

// Returns a Documents-shaped draft: { title, category, issuer, policyNumber,
// expiryDate, notes }, each null if not clearly present in the image.
// Throws if no AI key is configured or the provider/parse fails -- the
// caller decides how to surface that (same pattern as generateDailyEditorial).
export async function extractDocumentFromImage(file) {
  const { send, apiKey, model } = await getActiveAiProvider();
  if (!apiKey) throw new Error('Add an API key in Settings > AI Assistant to scan documents.');

  const dataBase64 = await fileToBase64(file);
  const prompt = `This is a photo of a personal document (a bill, ID, insurance card, lease, warranty, etc.). `
    + `Extract only what is clearly legible in the image. Never guess or invent a value -- use null for anything not clearly present. `
    + `Respond with JSON only, no markdown fences, no commentary, in exactly this shape:\n`
    + `{"title": string|null, "category": string|null, "issuer": string|null, "policyNumber": string|null, "expiryDate": "YYYY-MM-DD"|null, "notes": string|null}\n`
    + `"category" should be a short word like "lease", "insurance", "warranty", "ID", "medical", "utility" -- whatever best fits. `
    + `"notes" is anything else useful on the document that doesn't fit the other fields (one or two sentences, or null).`;

  const { text } = await send(apiKey, [{
    role: 'user',
    content: [{ type: 'text', text: prompt }, { type: 'image', mimeType: file.type || 'image/jpeg', dataBase64 }],
  }], { model, maxTokens: 400 });

  const parsed = parseJsonLoosely(text);
  if (!parsed) throw new Error(`${(await getActiveAiProvider()).label} returned something that wasn't valid JSON. Try again.`);
  return {
    title: parsed.title || '',
    category: parsed.category || '',
    issuer: parsed.issuer || '',
    policyNumber: parsed.policyNumber || '',
    expiryDate: parsed.expiryDate || null,
    notes: parsed.notes || '',
  };
}

// --- Rules & automation engine v1: "IFTTT for your own life," scoped down
// to a small, fixed set of built-in rules rather than a general
// rule-builder/DSL (that's a bigger, more open-ended commitment for
// later). Each rule is off by default -- an automation mutates your data on
// your own behalf, so it should never turn itself on -- and is idempotent
// per its own trigger condition (a specific streak threshold, a specific
// expiry date), so re-running the check on every boot never double-fires.
// Runs once per app boot (see runAutomations, called from app.js) -- there
// is no background execution while the app isn't open, same limitation as
// the rest of this local-first PWA (see "Real background push" in
// FUTURE_FEATURES.md for the not-yet-built alternative).
//
// Deliberately does NOT include an automatic Telegram send for the classic
// "bill due soon and unpaid" example: telegram-client.js is explicitly
// user-triggered-only by design ("never automatically... same
// foreground/user-triggered philosophy as the geolocation nudges in
// Places"), and Dashboard already surfaces due-soon unpaid bills
// unconditionally, so automating that display again would add nothing new.
// The document-renewal rule below is the "surface + act" example instead:
// it creates a genuinely new record (a Task) rather than re-showing
// something already shown.

const HABIT_STREAK_MILESTONES = [7, 30, 100, 365];

function computeHabitStreak(dates) {
  const days = new Set(dates);
  let streak = 0;
  const cursor = new Date(nowIso().slice(0, 10) + 'T00:00:00');
  if (!days.has(cursor.toISOString().slice(0, 10))) cursor.setDate(cursor.getDate() - 1);
  while (days.has(cursor.toISOString().slice(0, 10))) {
    streak++;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

// When a habit's streak crosses a milestone it hasn't been logged for yet,
// creates a Milestones entry and records the threshold on the habit itself
// so crossing it again (or re-running this on the next boot) never
// double-logs. Exact match for the doc's own "streak hits 30 -> log a
// milestone" example.
async function runHabitMilestoneAutomation() {
  if (!(await Settings.get('automationHabitMilestoneEnabled'))) return;
  const [habits, logs] = await Promise.all([Habits.list(), HabitLogs.list()]);
  const logsByHabit = new Map();
  for (const log of logs) {
    if (!logsByHabit.has(log.habitId)) logsByHabit.set(log.habitId, []);
    logsByHabit.get(log.habitId).push(log);
  }
  for (const habit of habits) {
    const streak = computeHabitStreak((logsByHabit.get(habit.id) || []).map((l) => l.date));
    const already = habit.lastStreakMilestoneLogged || 0;
    // Highest threshold the current streak satisfies, not the lowest
    // unlogged one -- a streak that's already well past several thresholds
    // when this is first turned on should jump straight to the right one
    // in a single boot, not trickle in one milestone per app-open.
    const next = [...HABIT_STREAK_MILESTONES].reverse().find((m) => m > already && streak >= m);
    if (!next) continue;
    await Milestones.create({ title: `🔥 ${next}-day streak: ${habit.name || '(untitled habit)'}`, date: nowIso().slice(0, 10) });
    await Habits.update(habit.id, { lastStreakMilestoneLogged: next });
  }
}

// When a document is expired or expiring within the configured window and
// no renewal task has been created for THIS expiry date yet, creates one.
// Updating the document's own expiryDate (what actually happens when you
// renew something) naturally clears the guard, so the next expiry cycle
// can fire again on its own.
async function runDocumentRenewalAutomation() {
  if (!(await Settings.get('automationDocumentRenewalEnabled'))) return;
  const documentExpiryDays = await Settings.get('documentExpiryDays');
  const documents = await Documents.list();
  for (const doc of documents) {
    if (!doc.expiryDate) continue;
    if (!(isOverdue(doc.expiryDate) || isWithinDays(doc.expiryDate, documentExpiryDays))) continue;
    if (doc.lastRenewalTaskExpiryDate === doc.expiryDate) continue;
    await Tasks.create({ title: `Renew: ${doc.title || '(untitled document)'}`, dueDate: doc.expiryDate, status: 'open' });
    await Documents.update(doc.id, { lastRenewalTaskExpiryDate: doc.expiryDate });
  }
}

// Runs every built-in automation once. Each is independently gated by its
// own Settings toggle and safe to call repeatedly -- a no-op if disabled or
// nothing currently satisfies its trigger. Never throws -- called once from
// app.js's boot sequence, and a bug in an automation must never be able to
// brick the whole app's startup (same "self-contained, never throws"
// discipline as completePendingRedirectIfAny).
export async function runAutomations() {
  try {
    await runHabitMilestoneAutomation();
    await runDocumentRenewalAutomation();
  } catch (err) {
    console.error('runAutomations failed', err);
  }
}

// --- Health-device ingestion: a one-time manual import of an Apple Health
// export (see apple-health-import.js for the parser), aggregated down to
// this app's one-row-per-day HealthLogs shape. Merges field-by-field rather
// than overwriting a whole record -- an imported day fills in only the
// fields Apple actually had a value for, so a manually-added note or a
// field Apple doesn't cover on an existing log survives the import.

export async function importAppleHealthDays(days) {
  let created = 0, updated = 0;
  for (const day of days) {
    const patch = {};
    if (day.sleepHours != null) patch.sleepHours = day.sleepHours;
    if (day.workoutType) patch.workoutType = day.workoutType;
    if (day.workoutMinutes != null) patch.workoutMinutes = day.workoutMinutes;
    if (day.waterOz != null) patch.waterOz = day.waterOz;
    if (day.weight != null) patch.weight = day.weight;
    if (!Object.keys(patch).length) continue;

    const existing = (await HealthLogs.byIndex('date', day.date))[0];
    if (existing) { await HealthLogs.update(existing.id, patch); updated++; }
    else { await HealthLogs.create({ date: day.date, ...patch }); created++; }
  }
  return { created, updated };
}

// --- Recall: the Languages module's SRS engine generalized to resurface any
// record -- reuses the exact same addressing (graphKey/resolveGraphNode) as
// the Knowledge Graph, so "what's schedulable" is "what Search can find,"
// same reasoning that already governs the graph. Grading uses the identical
// interval scheme as languageCards' review flow (views/languages.js):
// again resets to 1 day, good doubles, easy triples.

export const ResurfaceItems = entities.resurfaceItems;
export const ResurfaceReviewLogs = entities.resurfaceReviewLogs;

function addDaysFromNow(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

// Schedules a record for recall, or returns its existing item if one's
// already scheduled (mirrors createGraphLink's anti-dupe pattern).
export async function addResurfaceItem(store, id) {
  const key = graphKey(store, id);
  const existing = await ResurfaceItems.byIndex('key', key);
  if (existing.length) return existing[0];
  return ResurfaceItems.create({ key, srs: { interval: 1, dueDate: nowIso().slice(0, 10) } });
}

export async function getDueResurfaceItems() {
  const today = nowIso().slice(0, 10);
  const items = await ResurfaceItems.list();
  return items.filter((item) => (item.srs?.dueDate || today) <= today);
}

export async function gradeResurfaceItem(itemId, quality) {
  const item = await ResurfaceItems.get(itemId);
  if (!item) return;
  const prevInterval = item.srs?.interval || 1;
  const nextInterval = quality === 'again' ? 1 : quality === 'good' ? prevInterval * 2 : prevInterval * 3;
  await ResurfaceItems.update(itemId, { srs: { interval: nextInterval, dueDate: addDaysFromNow(nextInterval) } });
  await ResurfaceReviewLogs.create({ itemId, date: nowIso().slice(0, 10), quality });
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
