// contacts.js — one-tap import of the phone's address book into the Contacts
// module (FUTURE_FEATURES.md §13).
//
// NO-BUILD NOTE (same as the other native modules): the web app loads this as a
// plain ES module in the browser with no bundler, so we must NOT `import` the
// bare '@capacitor-community/contacts' specifier — the browser can't resolve it.
// The npm package only wires the NATIVE Android side via `cap sync`; on the JS
// side we reach the plugin through the runtime global
// window.Capacitor.Plugins.Contacts. In a plain browser that's absent, so every
// function here is a safe no-op and the capability layer hides the feature.

import { hasCapability } from './capabilities.js';
import { Contacts as ContactsStore } from '../data/api.js';

function plugin() {
  try {
    const c = typeof window !== 'undefined' ? window.Capacitor : null;
    return (c && c.Plugins && c.Plugins.Contacts) || null;
  } catch {
    return null;
  }
}

/** Available only in a native build with the contacts plugin present. */
export function canImportContacts() {
  return hasCapability('contacts') && !!plugin();
}

/** Ask the OS for contacts permission. Returns true if granted. */
export async function requestContactsPermission() {
  const p = plugin();
  if (!canImportContacts() || !p) return false;
  try {
    const res = await p.requestPermissions();
    return !!res && res.contacts === 'granted';
  } catch {
    return false;
  }
}

function newId() {
  try {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
  } catch { /* fall through */ }
  return `c${Date.now()}${Math.round(Math.abs(Math.sin(Date.now())) * 1e6)}`;
}

// Map one plugin contact to LifeOS's Contact record shape (name + label/value
// arrays), dropping empty values. Returns null if there's no usable name.
function mapContact(c) {
  const name = (c && c.name && (c.name.display
    || [c.name.given, c.name.middle, c.name.family].filter(Boolean).join(' '))) || '';
  const clean = name.trim();
  if (!clean) return null;
  const phones = ((c && c.phones) || [])
    .map((ph) => ({ id: newId(), label: (ph.label || ph.type || '').toString(), number: (ph.number || '').toString().trim() }))
    .filter((ph) => ph.number);
  const emails = ((c && c.emails) || [])
    .map((em) => ({ id: newId(), label: (em.label || em.type || '').toString(), email: (em.address || '').toString().trim() }))
    .filter((em) => em.email);
  return { name: clean, company: '', tags: ['imported'], phones, emails };
}

/**
 * Import the phone's contacts into the Contacts module. Skips any whose name
 * already exists (case-insensitive) so re-running is safe and non-duplicating.
 * Returns { imported, skipped, total }. No-op (zeros) off-native or if the
 * permission isn't granted.
 */
export async function importPhoneContacts() {
  const p = plugin();
  if (!canImportContacts() || !p) return { imported: 0, skipped: 0, total: 0 };
  let res;
  try {
    res = await p.getContacts({ projection: { name: true, phones: true, emails: true } });
  } catch {
    return { imported: 0, skipped: 0, total: 0 };
  }
  const incoming = (res && res.contacts) || [];
  const existing = await ContactsStore.list();
  const seen = new Set(existing.map((c) => (c.name || '').trim().toLowerCase()).filter(Boolean));

  let imported = 0;
  let skipped = 0;
  for (const raw of incoming) {
    const mapped = mapContact(raw);
    if (!mapped) { skipped++; continue; }
    const key = mapped.name.toLowerCase();
    if (seen.has(key)) { skipped++; continue; }   // already have someone by this name
    seen.add(key);                                 // also dedup within this batch
    try {
      await ContactsStore.create(mapped);
      imported++;
    } catch {
      skipped++;
    }
  }
  return { imported, skipped, total: incoming.length };
}
