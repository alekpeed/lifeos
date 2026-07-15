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
// Supabase personal-data sync -- the planned replacement for Drive sync,
// exposed alongside it during the side-by-side transition (see
// js/data/supabase-sync.js / sql/supabase-personal-sync-schema.sql).
export { connectSupabaseSync, syncSupabaseNow, disconnectSupabaseSync, getSupabaseSyncState } from './supabase-sync.js';
// Web Push (real background notifications). Client half only -- the sending
// server is a separately-deployed Supabase Edge Function. See js/data/push.js.
export { getPushState, enablePush, disablePush } from './push.js';

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
export const ImportedTransactions = entities.importedTransactions;
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
export const RabbitHoles = entities.rabbitHoles;
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

// --- Settings: plain key-value store, separate shape from the entity stores. ---

const SETTING_DEFAULTS = {
  theme: 'dark',
  accent: 'brass',
  density: 'comfortable',
  // A no-preference session (new device, or a "Clear site data" that wiped
  // IndexedDB along with the cache) lands on Test Mode -- the sidebar default,
  // and the only one that works on any device (spatial-1 is desktop-only, not
  // touch-safe). Was temporarily 'spatial-1' while that interface was being
  // actively shaped (2026-07); reverted to 'default' 2026-07-13 now that focus
  // has moved off it. Anyone who has explicitly picked an interface via
  // Settings has a real stored value here and is unaffected.
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
  // Life as Music, autonomous background mode (see js/audio/lifemusic.js) --
  // off by default since it's audio, opt-in like everything else that makes
  // noise.
  ambientMusicEnabled: false,
  // Notifications page's Sharebox-activity watermark -- items posted by
  // other members before this timestamp read as already-seen. null until
  // the page is visited for the first time (everything reads as new then).
  notificationsLastSeenAt: null,
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
import { sendGeminiMessage, embedTextGemini } from './gemini-client.js';

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
// Two-way Telegram linking (the bot itself is a Supabase Edge Function).
export { getTelegramLinkState, createTelegramDeepLink, unlinkTelegram } from './telegram-link.js';

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
  albums: (r) => r.name,
};

const SEARCH_MODULE_ROUTE = {
  tasks: 'tasks', places: 'places', links: 'links', semesters: 'education', courses: 'education',
  assignments: 'education', bills: 'finance', subscriptions: 'finance', books: 'books',
  recipes: 'recipes', documents: 'documents', contacts: 'contacts', milestones: 'milestones',
  habits: 'habits', albums: 'photos',
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

// Downscale + re-encode an image before sending it to a vision API. Object
// recognition and document OCR don't need a full-resolution native photo, and
// vision-API cost/latency scale with image size -- so cap the long edge and
// re-encode as JPEG. Falls back to the original file on any failure (a wrong
// image is worse than a big one). No-op for non-images.
export async function compressImageForVision(file, maxEdge = 1200, quality = 0.82) {
  if (!file || !file.type?.startsWith('image/')) return file;
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, maxEdge / Math.max(bitmap.width, bitmap.height));
    const w = Math.max(1, Math.round(bitmap.width * scale));
    const h = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    canvas.getContext('2d').drawImage(bitmap, 0, 0, w, h);
    bitmap.close?.();
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
    if (!blob) return file;
    return new File([blob], (file.name || 'image').replace(/\.\w+$/, '') + '.jpg', { type: 'image/jpeg' });
  } catch {
    return file;
  }
}

// Returns a Documents-shaped draft: { title, category, issuer, policyNumber,
// expiryDate, notes }, each null if not clearly present in the image.
// Throws if no AI key is configured or the provider/parse fails -- the
// caller decides how to surface that (same pattern as generateDailyEditorial).
export async function extractDocumentFromImage(file) {
  const { send, apiKey, model } = await getActiveAiProvider();
  if (!apiKey) throw new Error('Add an API key in Settings > AI Assistant to scan documents.');

  const dataBase64 = await fileToBase64(await compressImageForVision(file));
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

// Camera-vision cataloging (Quartermaster): photograph a shelf/pantry/garage
// and draft a list of distinct items instead of typing each one by hand.
// Same discipline as extractDocumentFromImage above -- one image in, a closed
// JSON shape out, the caller (quartermaster.js) always shows the list as an
// editable draft before creating anything. Deliberately doesn't attempt
// quantity/fill-level estimation (a harder vision problem, and "low" is
// subjective) -- see FUTURE_FEATURES.md for the separate few-shot low-stock
// flow this now pairs with (see judgeStockFromImage below).
export async function catalogItemsFromImage(file) {
  const { send, apiKey, model } = await getActiveAiProvider();
  if (!apiKey) throw new Error('Add an API key in Settings > AI Assistant to catalog from a photo.');

  const dataBase64 = await fileToBase64(await compressImageForVision(file));
  const prompt = `This is a photo of a shelf, pantry, garage, or storage area. Identify each distinct physical item visible. `
    + `Don't estimate quantity or how full/empty anything is -- just list what's there. Skip anything you can't identify with reasonable confidence. `
    + `Respond with JSON only, no markdown fences, no commentary, in exactly this shape:\n`
    + `{"items": [{"name": string, "location": string|null}]}\n`
    + `"name" should be a short, specific item name (e.g. "Cordless drill", "Canned tomatoes", "Christmas lights"), not a category. `
    + `"location" is a short descriptor of where in the photo it is if that's useful (e.g. "top shelf"), or null.`;

  const { text } = await send(apiKey, [{
    role: 'user',
    content: [{ type: 'text', text: prompt }, { type: 'image', mimeType: file.type || 'image/jpeg', dataBase64 }],
  }], { model, maxTokens: 800 });

  const parsed = parseJsonLoosely(text);
  if (!parsed || !Array.isArray(parsed.items)) throw new Error(`${(await getActiveAiProvider()).label} returned something that wasn't valid JSON. Try again.`);
  return parsed.items
    .filter((i) => i && i.name)
    .map((i) => ({ name: String(i.name).trim(), location: i.location ? String(i.location).trim() : '' }));
}

// --- Quartermaster few-shot low-stock detection ---
// Not model retraining -- a labeled-example flow. You tag reference photos of
// an item with your OWN words ("low", "full", whatever). Judging a new photo
// sends it PLUS your most-recent labeled examples to the vision model as
// calibration, asking it to place the new photo relative to them. Approximate
// placement, not precise quantity. Accuracy grows with your label library
// because each judgment has more of your own examples to anchor on. Reference
// photos are stored as attachments on the item (kind:'stockRef', stockLabel);
// all images are compressed before sending (compressImageForVision).

const MAX_STOCK_REFS = 5;

export async function createStockReference(file, itemId, label) {
  const compressed = await compressImageForVision(file);
  return Attachments.create({
    relatedStore: 'inventoryItems',
    relatedId: itemId,
    kind: 'stockRef',
    stockLabel: (label || '').trim(),
    filename: compressed.name || 'ref.jpg',
    mimeType: compressed.type,
    blob: compressed,
    driveFileId: null,
  });
}

export async function getStockReferences(itemId) {
  const all = await getAttachmentsFor('inventoryItems', itemId);
  return all
    .filter((a) => a.kind === 'stockRef' && a.stockLabel)
    .sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''));
}

// Sends the new photo + up to MAX_STOCK_REFS most-recent labeled references to
// the active vision provider. Returns { placement, note }: placement is one of
// the user's own labels (or "unsure"). Throws with no key or no references.
export async function judgeStockFromImage(file, references) {
  const { send, apiKey, model } = await getActiveAiProvider();
  if (!apiKey) throw new Error('Add an API key in Settings > AI Assistant to judge stock from a photo.');
  const refs = (references || []).slice(0, MAX_STOCK_REFS);
  if (!refs.length) throw new Error('Add at least one labeled reference photo first.');

  const newB64 = await fileToBase64(await compressImageForVision(file));
  const labels = [...new Set(refs.map((r) => r.stockLabel))];
  const content = [
    { type: 'text', text:
      `You judge how stocked an item is by comparing a NEW photo to the user's own LABELED reference photos. `
      + `Place the new photo relative to the references and answer with one of these exact labels the user has used: `
      + `${labels.map((l) => `"${l}"`).join(', ')}, or "unsure" if it doesn't clearly match any. `
      + `Respond with JSON only: {"placement": string, "note": string}. "note" is one short sentence of reasoning.` },
    { type: 'text', text: 'Labeled reference photos:' },
  ];
  for (const r of refs) {
    content.push({ type: 'text', text: `Reference labeled "${r.stockLabel}":` });
    content.push({ type: 'image', mimeType: r.mimeType || 'image/jpeg', dataBase64: await fileToBase64(r.blob) });
  }
  content.push({ type: 'text', text: 'The NEW photo to place:' });
  content.push({ type: 'image', mimeType: 'image/jpeg', dataBase64: newB64 });

  const { text } = await send(apiKey, [{ role: 'user', content }], { model, maxTokens: 200 });
  const parsed = parseJsonLoosely(text);
  if (!parsed || !parsed.placement) throw new Error(`${(await getActiveAiProvider()).label} returned something unexpected. Try again.`);
  return { placement: String(parsed.placement).trim(), note: (parsed.note || '').trim() };
}

// --- Semantic memory (the Ask module) -----------------------------------
// Embed every meaningful record into a vector (Gemini text-embedding-004,
// Gemini-only -- Anthropic has no embeddings API), stored device-local in the
// `embeddings` store keyed by `<store>:<id>`. A natural-language query embeds
// the same way and ranks records by cosine similarity, all client-side, so
// "when did I last see Sarah?" surfaces the actual records rather than a
// keyword match. The index is built/refreshed on demand and incrementally --
// only records whose text changed (or new ones) are re-embedded, and gone
// records are pruned.

// Stores worth embedding, and the text fields to pull from each record. A
// generic field sweep keeps this maintainable across ~40 modules without
// per-store code; ids/dates/flags are excluded by simply not listing them.
// store -> the module id to navigate to (mostly identity; the two camelCase
// stores map to their lowercase module ids).
const SEMANTIC_STORES = {
  tasks: 'tasks', ideas: 'ideas', places: 'places', links: 'links',
  books: 'books', recipes: 'recipes', documents: 'documents', contacts: 'contacts',
  milestones: 'milestones', rabbitHoles: 'rabbitholes', timeCapsules: 'timecapsules',
  habits: 'habits',
};
const SEMANTIC_TEXT_FIELDS = [
  'title', 'name', 'text', 'topic', 'body', 'notes', 'description',
  'author', 'company', 'relationship', 'url', 'message',
];

function semanticTextFor(record) {
  const parts = [];
  for (const f of SEMANTIC_TEXT_FIELDS) {
    if (typeof record[f] === 'string' && record[f].trim()) parts.push(record[f].trim());
  }
  return parts.join(' — ').slice(0, 2000);
}

function cosineSim(a, b) {
  let dot = 0, na = 0, nb = 0;
  const n = Math.min(a.length, b.length);
  for (let i = 0; i < n; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
  if (!na || !nb) return 0;
  return dot / (Math.sqrt(na) * Math.sqrt(nb));
}

async function geminiKey() {
  return (await Settings.get('geminiApiKey')) || '';
}

// All (store, record) pairs worth embedding, with their current text.
async function semanticCandidates() {
  const out = [];
  for (const store of Object.keys(SEMANTIC_STORES)) {
    let records;
    try { records = await db.getAll(store); } catch { continue; }
    for (const r of records) {
      const text = semanticTextFor(r);
      if (text) out.push({ id: `${store}:${r.id}`, store, recordId: r.id, title: SEMANTIC_TEXT_FIELDS.map((f) => r[f]).find((v) => typeof v === 'string' && v.trim()) || '(untitled)', text });
    }
  }
  return out;
}

export async function getSemanticIndexState() {
  const key = await geminiKey();
  const [candidates, existing] = await Promise.all([semanticCandidates(), db.getAll('embeddings')]);
  return {
    hasKey: Boolean(key),
    total: candidates.length,
    indexed: existing.length,
    stale: candidates.filter((c) => {
      const e = existing.find((x) => x.id === c.id);
      return !e || e.text !== c.text;
    }).length,
  };
}

// Embed anything new/changed and prune anything gone. onProgress({done,total}).
export async function buildSemanticIndex(onProgress) {
  const key = await geminiKey();
  if (!key) throw new Error('Semantic memory needs a Gemini API key (Settings > AI Assistant). Anthropic has no embeddings API.');

  const [candidates, existing] = await Promise.all([semanticCandidates(), db.getAll('embeddings')]);
  const candidateIds = new Set(candidates.map((c) => c.id));

  // Prune embeddings whose source record is gone or no longer indexable.
  for (const e of existing) {
    if (!candidateIds.has(e.id)) await db.remove('embeddings', e.id);
  }

  const existingById = new Map(existing.map((e) => [e.id, e]));
  const todo = candidates.filter((c) => {
    const e = existingById.get(c.id);
    return !e || e.text !== c.text;
  });

  let done = 0;
  for (const c of todo) {
    const vector = await embedTextGemini(key, c.text);
    await db.put('embeddings', { id: c.id, store: c.store, recordId: c.recordId, title: c.title, text: c.text, vector });
    done += 1;
    onProgress?.({ done, total: todo.length });
  }
  return { embedded: done, pruned: existing.length - existingById.size, total: candidates.length };
}

// Embed the query and return the top matches, each with its module + score.
export async function semanticSearch(query, topN = 15) {
  const key = await geminiKey();
  if (!key) throw new Error('Semantic memory needs a Gemini API key (Settings > AI Assistant).');
  const q = String(query || '').trim();
  if (!q) return [];

  const [qVec, rows] = await Promise.all([embedTextGemini(key, q), db.getAll('embeddings')]);
  return rows
    .map((r) => ({ store: r.store, id: r.recordId, title: r.title, module: SEMANTIC_STORES[r.store] || r.store, score: cosineSim(qVec, r.vector) }))
    .sort((a, b) => b.score - a.score)
    .slice(0, topN);
}

// --- Statement import & reconciliation: a one-time CSV import of bank/card
// transactions (Finance's "Import" tab), auto-matched against existing
// Bills/Subscriptions by description + amount, so importing mostly means
// reviewing suggestions rather than typing anything. Scoped to CSV only for
// this pass -- OFX is a real second parser (SGML-like, not just delimited
// text) and not worth the added surface until CSV proves useful. No AI
// involved: matching is plain string/number comparison, so it works fully
// offline and never depends on an API key.

function splitCsvLine(line) {
  const cells = [];
  let cur = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (inQuotes) {
      if (c === '"' && line[i + 1] === '"') { cur += '"'; i++; }
      else if (c === '"') inQuotes = false;
      else cur += c;
    } else if (c === '"') inQuotes = true;
    else if (c === ',') { cells.push(cur); cur = ''; }
    else cur += c;
  }
  cells.push(cur);
  return cells.map((c) => c.trim());
}

function parseCsvAmount(raw) {
  if (!raw) return null;
  const negative = /^\(.*\)$/.test(raw.trim());
  const cleaned = raw.replace(/[()$,\s]/g, '');
  const n = Number(cleaned);
  if (!Number.isFinite(n)) return null;
  return negative ? -Math.abs(n) : n;
}

function parseCsvDate(raw) {
  if (!raw) return null;
  const trimmed = raw.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return trimmed;
  const d = new Date(trimmed);
  if (Number.isNaN(d.getTime())) return null;
  return d.toISOString().slice(0, 10);
}

const HEADER_ALIASES = {
  date: ['date', 'transaction date', 'posted date', 'posting date'],
  description: ['description', 'memo', 'payee', 'name', 'merchant', 'transaction'],
  amount: ['amount', 'transaction amount'],
  debit: ['debit', 'withdrawal'],
  credit: ['credit', 'deposit'],
};

function matchHeader(cell) {
  const lower = cell.trim().toLowerCase();
  for (const [key, aliases] of Object.entries(HEADER_ALIASES)) {
    if (aliases.some((a) => lower === a || lower.includes(a))) return key;
  }
  return null;
}

// Parses a bank/card CSV export into { date: 'YYYY-MM-DD', description, amount }
// rows (amount negative = money out, matching how Bills/Subscriptions are
// tracked). Tolerant of a few common column-naming conventions and a
// separate debit/credit pair instead of one signed amount column. Rows that
// don't parse cleanly (unreadable date, no amount) are silently skipped --
// this is a best-effort import, not a strict-validation one.
export async function parseTransactionsCsv(file) {
  const text = await file.text();
  const lines = text.split(/\r?\n/).filter((l) => l.trim());
  if (!lines.length) return [];

  const headerCells = splitCsvLine(lines[0]);
  const columns = headerCells.map(matchHeader);
  if (!columns.includes('date') || (!columns.includes('amount') && !(columns.includes('debit') || columns.includes('credit')))) {
    throw new Error('Could not find date/amount columns in this file. Expected a header row with columns like Date, Description, Amount.');
  }

  const rows = [];
  for (const line of lines.slice(1)) {
    const cells = splitCsvLine(line);
    const byCol = {};
    columns.forEach((col, i) => { if (col) byCol[col] = cells[i]; });

    const date = parseCsvDate(byCol.date);
    let amount = parseCsvAmount(byCol.amount);
    if (amount === null && (byCol.debit || byCol.credit)) {
      const debit = parseCsvAmount(byCol.debit) || 0;
      const credit = parseCsvAmount(byCol.credit) || 0;
      amount = credit - Math.abs(debit);
    }
    if (!date || amount === null) continue;
    rows.push({ date, description: (byCol.description || '').trim(), amount });
  }
  return rows;
}

function normalizeForMatch(s) {
  return (s || '').toLowerCase().replace(/[^a-z0-9]/g, '');
}

// Suggests a Bill or Subscription match for each parsed transaction: the
// description contains (or is contained by) the record's name, and the
// amounts agree within a small tolerance (flat $1 or 2%, whichever is
// larger -- covers rounding/fee drift without matching unrelated amounts).
// Also flags transactions that look like a re-import of something already
// confirmed (same date+description+amount already in importedTransactions).
export async function suggestTransactionMatches(transactions) {
  const [bills, subs, existing] = await Promise.all([Bills.list(), Subscriptions.list(), ImportedTransactions.list()]);
  const candidates = [
    ...bills.filter((b) => typeof b.amount === 'number').map((b) => ({ type: 'bill', id: b.id, name: b.name || '', amount: b.amount })),
    ...subs.filter((s) => s.stillInUse !== false).map((s) => ({ type: 'subscription', id: s.id, name: s.name || '', amount: s.amount })),
  ];
  const existingKeys = new Set(existing.map((t) => `${t.date}|${t.description}|${t.amount}`));

  return transactions.map((t) => {
    const spend = Math.abs(t.amount);
    const normDesc = normalizeForMatch(t.description);
    const tolerance = Math.max(1, spend * 0.02);
    const match = candidates.find((c) => {
      const normName = normalizeForMatch(c.name);
      if (!normName) return false;
      const nameMatches = normDesc.includes(normName) || normName.includes(normDesc);
      const amountMatches = Math.abs(Math.abs(c.amount) - spend) <= tolerance;
      return nameMatches && amountMatches;
    }) || null;
    return { ...t, suggestedMatch: match, isDuplicate: existingKeys.has(`${t.date}|${t.description}|${t.amount}`) };
  });
}

// Confirms an import: creates one ImportedTransactions record per row
// (the permanent ledger of what was imported, for dedup on future imports),
// and for any row matched+confirmed to a Bill, also logs the payment and
// marks the bill paid -- same effect as manually checking it off in the
// Bills tab. Subscription matches are recorded but don't mutate the
// subscription (there's no "paid" state to flip there, unlike a Bill).
export async function confirmTransactionImport(transactions, importBatchId) {
  for (const t of transactions) {
    if (t.skip) continue;
    await ImportedTransactions.create({
      date: t.date, description: t.description, amount: t.amount, importBatchId,
      matchedBillId: t.match?.type === 'bill' ? t.match.id : null,
      matchedSubscriptionId: t.match?.type === 'subscription' ? t.match.id : null,
      status: t.match ? 'matched' : 'unmatched',
    });
    if (t.match?.type === 'bill' && t.markPaid) {
      const bill = await Bills.get(t.match.id);
      if (bill && !bill.paid) {
        await BillPayments.create({ billId: bill.id, datePaid: t.date, amountPaid: Math.abs(t.amount), method: 'import' });
        await Bills.update(bill.id, { paid: true });
      }
    }
  }
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

// --- Recall: a generalized spaced-repetition engine that can resurface any
// record -- reuses the exact same addressing (graphKey/resolveGraphNode) as
// the Knowledge Graph, so "what's schedulable" is "what Search can find,"
// same reasoning that already governs the graph. Grading uses a standard
// SRS interval scheme: again resets to 1 day, good doubles, easy triples.

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
