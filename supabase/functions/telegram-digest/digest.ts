// The pure half of the Telegram digest (§7 D-5 Phase 1): read a module's synced blob,
// work out what is due, and turn it into a message. No network, no Supabase, no Deno —
// which is the point. This is the part that mirrors six Kotlin data shapes, so it is the
// part most likely to drift, and it can be type-checked and unit-tested on its own.
//
// index.ts does the I/O: read the rows, call in here, post to Telegram.

// The app's per-device thresholds (billDueSoonDays, docExpiryDays) are deliberately
// not synced — they are device settings, not data. The digest uses its own window,
// stated here rather than guessed at per module.
export const DUE_SOON_DAYS = 3;
export const DOC_EXPIRY_DAYS = 30;

// Only the keys that carry dated obligations. Reading the whole store would work and
// would also drag every photo index and map cache through the function for nothing.
export const KEYS = ['Tasks', 'Finance', 'Education', 'Documents', 'Projects', 'Time Capsules'];

// `key` and `id` name the record this came out of, so a per-item push can carry a
// subject its notification's buttons can resolve ("<storage key>|<record id>", the
// same string the Kotlin side builds in push/Actions.kt). The digest itself only
// needs the title; it is one message about many things and has nothing to act on.
export type Item = { title: string; when: number; kind: string; key: string; id: number | null };

// Record ids are Kotlin Longs written as JSON numbers. Anything else — missing, a
// string, a float — yields no subject rather than a subject that names nothing.
export function idOf(raw: unknown): number | null {
  return typeof raw === 'number' && Number.isInteger(raw) ? raw : null;
}

// Empty when the item cannot be resolved back to a record; the client reads that as
// "no record behind this notification" and offers a plain Dismiss.
export function subjectOf(item: Item): string {
  return item.id === null ? '' : `${item.key}|${item.id}`;
}

// ---- dates -------------------------------------------------------------------

// The app stores "yyyy-MM-dd" or "yyyy-MM-ddTHH:mm" (M-01a). Only the date half
// matters for a daily digest, and slicing it is what the app's own parser does.
export function daysUntil(raw: unknown): number | null {
  if (typeof raw !== 'string' || !raw) return null;
  const date = raw.split('T')[0];
  const target = new Date(`${date}T00:00:00Z`);
  if (Number.isNaN(target.getTime())) return null;
  const now = new Date();
  const midnight = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  return Math.round((target.getTime() - midnight) / 86400000);
}

function within(n: number | null, days: number): n is number {
  return n !== null && n <= days;
}

// ---- reading one module's blob --------------------------------------------------

// Each of these mirrors the shape the Kotlin side writes. Written defensively: a blob
// that fails to parse, or that has a field missing, drops out rather than throwing and
// taking every other user's digest with it.
export function itemsFrom(key: string, blob: unknown): Item[] {
  const out: Item[] = [];
  const asArray = (v: unknown): Record<string, unknown>[] =>
    Array.isArray(v) ? (v as Record<string, unknown>[]) : [];
  const obj = (blob ?? {}) as Record<string, unknown>;

  if (key === 'Tasks') {
    // Tasks is a bare array at the root.
    for (const t of asArray(blob)) {
      if (t.status === 'done') continue;
      const n = daysUntil(t.due);
      if (!within(n, DUE_SOON_DAYS)) continue;
      out.push({ title: String(t.title ?? 'A task'), when: n, kind: 'Task', key, id: idOf(t.id) });
    }
    return out;
  }

  if (key === 'Finance') {
    for (const b of asArray(obj.bills)) {
      // A one-time bill that has been paid is settled; a recurring one rolls on.
      const history = asArray(b.paymentHistory);
      if (b.cadence === 'one-time' && history.length > 0) continue;
      const n = daysUntil(b.dueDate);
      if (!within(n, DUE_SOON_DAYS)) continue;
      const autopay = b.autopay === true ? ' (autopay)' : '';
      out.push({ title: `${String(b.name ?? 'A bill')}${autopay}`, when: n, kind: 'Bill', key, id: idOf(b.id) });
    }
    return out;
  }

  if (key === 'Education') {
    for (const a of asArray(obj.assignments)) {
      if (a.status === 'done') continue;
      const n = daysUntil(a.dueDate);
      if (!within(n, DUE_SOON_DAYS)) continue;
      out.push({ title: String(a.title ?? 'An assignment'), when: n, kind: 'Assignment', key, id: idOf(a.id) });
    }
    return out;
  }

  if (key === 'Documents') {
    for (const d of asArray(obj.documents)) {
      const n = daysUntil(d.expiryDate);
      if (!within(n, DOC_EXPIRY_DAYS)) continue;
      out.push({ title: `${String(d.title ?? 'A document')} expires`, when: n, kind: 'Document', key, id: idOf(d.id) });
    }
    return out;
  }

  if (key === 'Projects') {
    for (const p of asArray(obj.projects)) {
      if (p.status === 'DONE' || p.status === 'ARCHIVED') continue;
      const n = daysUntil(p.targetDate);
      if (!within(n, DUE_SOON_DAYS)) continue;
      out.push({ title: String(p.name ?? 'A project'), when: n, kind: 'Project', key, id: idOf(p.id) });
    }
    return out;
  }

  if (key === 'Time Capsules') {
    // A capsule that has opened and not been read. The digest is the third way this
    // surfaces (§5.4) — the alarm and the Briefing row are the other two — and it is
    // the only one that reaches you on a device you have not opened the app on.
    for (const c of asArray(obj.capsules)) {
      if (typeof c.readAt === 'string' && c.readAt) continue;
      const n = daysUntil(c.sealedUntil);
      if (n === null || n > 0) continue;
      out.push({ title: String(c.title || 'A sealed note'), when: n, kind: 'Capsule', key, id: idOf(c.id) });
    }
    return out;
  }

  return out;
}

// ---- the message ---------------------------------------------------------------

export function whenLabel(n: number): string {
  if (n < -1) return `${-n} days overdue`;
  if (n === -1) return 'overdue since yesterday';
  if (n === 0) return 'today';
  if (n === 1) return 'tomorrow';
  return `in ${n} days`;
}

// Plain text, not Markdown: a bill called "Rent *2*" would break Markdown parsing and
// Telegram would reject the whole message. Nothing here needs formatting badly enough
// to risk that.
export function buildMessage(items: Item[]): string {
  items.sort((a, b) => a.when - b.when);
  const overdue = items.filter((i) => i.when < 0).length;

  const head = overdue > 0
    ? `Life OS — ${items.length} need${items.length === 1 ? 's' : ''} you, ${overdue} overdue`
    : `Life OS — ${items.length} coming up`;

  const lines = items.slice(0, 15).map((i) => `• ${i.kind}: ${i.title} — ${whenLabel(i.when)}`);
  if (items.length > 15) lines.push(`…and ${items.length - 15} more.`);
  return [head, '', ...lines].join('\n');
}

