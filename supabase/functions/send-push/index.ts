// Life OS -- Web Push sender (Supabase Edge Function, Deno runtime)
// ============================================================
// The SERVER half of real background notifications. A static PWA can't send
// Web Push; this can. Invoked on a schedule (pg_cron, see the runbook in
// SUPABASE_MIGRATION.md), it:
//   1. reads every user's due-soon/overdue items straight from sync_records
//      (the same data the app syncs), using the service-role key to bypass RLS,
//   2. builds one concise digest notification per user who has something,
//   3. sends it to each of that user's stored push subscriptions, signed with
//      the VAPID private key from Supabase secrets,
//   4. prunes subscriptions the push service reports as gone (410/404).
//
// UNTESTED against the live Deno runtime (it needs deploy + real subscriptions
// + the VAPID secret). If `npm:web-push` doesn't load under Supabase's Deno,
// the documented fallback is the Deno-native jsr:@negrel/webpush (its API
// differs -- see the runbook). Everything else here is plain data logic.
//
// Required secrets (supabase secrets set ...):
//   VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT (a mailto: or https URL)
// Auto-injected by Supabase: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.

import { createClient } from 'npm:@supabase/supabase-js@2';
import webpush from 'npm:web-push@3.6.7';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const VAPID_PUBLIC = Deno.env.get('VAPID_PUBLIC_KEY')!;
const VAPID_PRIVATE = Deno.env.get('VAPID_PRIVATE_KEY')!;
const VAPID_SUBJECT = Deno.env.get('VAPID_SUBJECT') ?? 'mailto:admin@example.com';

webpush.setVapidDetails(VAPID_SUBJECT, VAPID_PUBLIC, VAPID_PRIVATE);
const admin = createClient(SUPABASE_URL, SERVICE_ROLE);

// Server-side default thresholds. The app's per-device Settings values
// (billDueSoonDays / documentExpiryDays) are NOT synced, so the function can't
// read them -- these are sensible fixed defaults for the digest.
const DUE_SOON_DAYS = 3;

function daysUntil(dateStr: string): number | null {
  if (!dateStr) return null;
  const target = new Date(dateStr + 'T00:00:00');
  if (Number.isNaN(target.getTime())) return null;
  const now = new Date();
  const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target.getTime() - midnight.getTime()) / 86400000);
}

// Mirrors the app's getDueSoonFeed: open items due within the window (or past).
function isDueItem(store: string, data: Record<string, unknown>): { title: string; when: number } | null {
  const d = (k: string) => data[k] as string | undefined;
  if (store === 'bills') {
    if (data.paid) return null;
    const n = daysUntil(d('dueDate') ?? '');
    if (n === null || n > DUE_SOON_DAYS) return null;
    return { title: String(data.name ?? 'A bill'), when: n };
  }
  if (store === 'tasks') {
    if (data.status === 'done') return null;
    const n = daysUntil(d('dueDate') ?? '');
    if (n === null || n > DUE_SOON_DAYS) return null;
    return { title: String(data.title ?? 'A task'), when: n };
  }
  if (store === 'assignments') {
    if (data.status === 'done') return null;
    const n = daysUntil(d('dueDate') ?? '');
    if (n === null || n > DUE_SOON_DAYS) return null;
    return { title: String(data.title ?? 'An assignment'), when: n };
  }
  if (store === 'documents') {
    const n = daysUntil(d('expiryDate') ?? '');
    if (n === null || n > DUE_SOON_DAYS) return null;
    return { title: `${String(data.title ?? 'A document')} expires`, when: n };
  }
  return null;
}

function buildPayload(items: { title: string; when: number }[]) {
  items.sort((a, b) => a.when - b.when);
  const count = items.length;
  const top = items[0];
  const urgency = top.when < 0 ? 'overdue' : top.when === 0 ? 'due today' : `due in ${top.when}d`;
  return {
    title: count === 1 ? 'Life OS' : `Life OS — ${count} items need attention`,
    body: count === 1 ? `${top.title} — ${urgency}` : `${top.title} (${urgency}) and ${count - 1} more`,
    url: './#/notifications',
    tag: 'lifeos-due-digest', // one rolling digest, never a stack of them
  };
}

Deno.serve(async () => {
  // Pull every live due-relevant record across all users in one query.
  const { data: rows, error } = await admin
    .from('sync_records')
    .select('user_id, store, data')
    .in('store', ['bills', 'tasks', 'assignments', 'documents'])
    .is('deleted_at', null);
  if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500 });

  // Group due items per user.
  const byUser = new Map<string, { title: string; when: number }[]>();
  for (const r of rows ?? []) {
    const hit = isDueItem(r.store, r.data ?? {});
    if (!hit) continue;
    if (!byUser.has(r.user_id)) byUser.set(r.user_id, []);
    byUser.get(r.user_id)!.push(hit);
  }
  if (byUser.size === 0) return new Response(JSON.stringify({ sent: 0, reason: 'nothing due' }));

  // Fetch subscriptions only for users who have something to hear about.
  const userIds = [...byUser.keys()];
  const { data: subs, error: subErr } = await admin
    .from('push_subscriptions')
    .select('id, user_id, endpoint, p256dh, auth_key')
    .in('user_id', userIds);
  if (subErr) return new Response(JSON.stringify({ error: subErr.message }), { status: 500 });

  let sent = 0;
  let pruned = 0;
  for (const sub of subs ?? []) {
    const items = byUser.get(sub.user_id);
    if (!items || items.length === 0) continue;
    const payload = JSON.stringify(buildPayload(items));
    try {
      await webpush.sendNotification(
        { endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth_key } },
        payload,
      );
      sent++;
    } catch (err) {
      const status = (err as { statusCode?: number }).statusCode;
      if (status === 404 || status === 410) {
        await admin.from('push_subscriptions').delete().eq('id', sub.id);
        pruned++;
      } else {
        console.error('push send failed', sub.id, status, (err as Error).message);
      }
    }
  }

  return new Response(JSON.stringify({ sent, pruned }), { headers: { 'Content-Type': 'application/json' } });
});
