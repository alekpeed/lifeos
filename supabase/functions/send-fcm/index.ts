// Life OS — per-item push over FCM (Supabase Edge Function, Deno).
// ================================================================
// §7 D-5 Phase 2, server half. Phase 1 (telegram-digest) is the daily look-ahead and
// reaches the laptop too; this is the other half of the division of labour in the
// decision doc: the individual urgent item, phone only, with buttons that resolve it.
//
// Why a new function rather than an edit of send-push/index.ts: send-push queries
// `store in ('bills','tasks','assignments','documents')` with one row per record, which
// was the deleted web app's schema. The native app syncs one row per Storage KEY under
// store='kv'. Same reason telegram-digest is its own function; the shape reading is
// shared instead, out of telegram-digest/digest.ts, so there is one mirror of the
// Kotlin data shapes rather than three.
//
// NOTHING HERE RUNS UNTIL A FIREBASE PROJECT EXISTS. It needs a service-account key in
// Supabase secrets and at least one device token in fcm_tokens, and both of those come
// from a Firebase project that only the account owner can create. Deployed without
// them it is inert: no tokens, nothing sent, and it says so in its response.
//
// Required secrets (supabase secrets set ...):
//   FCM_SERVICE_ACCOUNT — the whole service-account JSON key file, as one string.
// Auto-injected by Supabase: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.
//
// Deploy:  supabase functions deploy send-fcm
// Schema:  sql/supabase-fcm-schema.sql
// Schedule: sql/supabase-fcm-cron.sql

import { createClient } from 'npm:@supabase/supabase-js@2';
import { accessToken, parseServiceAccount } from './auth.ts';
import { buildMessage, isDeadToken, isUrgent, itemsFrom, KEYS, type Item } from './message.ts';
import { subjectOf } from '../telegram-digest/digest.ts';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const admin = createClient(SUPABASE_URL, SERVICE_ROLE);

// A backlog of forty overdue tasks is a reason to open the app, not to receive forty
// notifications. The digest already covers the long tail.
const MAX_PER_USER = 5;

const today = () => new Date().toISOString().slice(0, 10);

// One row per (account, record, day) in fcm_sent; this is its key in memory.
const seenKey = (userId: string, subject: string) => `${userId} ${subject}`;

Deno.serve(async () => {
  // Start from the devices, so an account with no token costs no read of its data.
  const { data: tokens, error: tokenErr } = await admin
    .from('fcm_tokens')
    .select('user_id, token');
  if (tokenErr) {
    return new Response(JSON.stringify({ error: tokenErr.message }), { status: 500 });
  }
  if (!tokens || tokens.length === 0) {
    return new Response(JSON.stringify({ sent: 0, reason: 'no registered devices' }));
  }

  const account = parseServiceAccount(Deno.env.get('FCM_SERVICE_ACCOUNT'));
  const bearer = await accessToken(account, Math.floor(Date.now() / 1000));
  const endpoint = `https://fcm.googleapis.com/v1/projects/${account.projectId}/messages:send`;

  const userIds = [...new Set(tokens.map((t) => t.user_id))];
  const { data: rows, error } = await admin
    .from('sync_records')
    .select('user_id, record_id, data')
    .eq('store', 'kv')
    .in('record_id', KEYS)
    .in('user_id', userIds)
    .is('deleted_at', null);
  if (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }

  const day = today();
  const { data: already } = await admin
    .from('fcm_sent')
    .select('user_id, subject')
    .eq('sent_on', day);
  const sentAlready = new Set((already ?? []).map((r) => seenKey(r.user_id, r.subject)));

  const byUser = new Map<string, Item[]>();
  for (const r of rows ?? []) {
    const text = (r.data as Record<string, unknown> | null)?.text;
    if (typeof text !== 'string' || !text.trim()) continue;
    let blob: unknown;
    try {
      blob = JSON.parse(text);
    } catch {
      continue; // one unreadable blob must not cost the rest of the run
    }
    const found = itemsFrom(r.record_id, blob).filter(isUrgent);
    if (found.length === 0) continue;
    byUser.set(r.user_id, [...(byUser.get(r.user_id) ?? []), ...found]);
  }

  let sent = 0;
  let skipped = 0;
  const dead: string[] = [];
  const recorded: { user_id: string; subject: string; sent_on: string }[] = [];

  for (const [userId, items] of byUser) {
    const devices = tokens.filter((t) => t.user_id === userId).map((t) => t.token);
    // An item with no id cannot carry a subject, so its notification would have no
    // buttons — which is the whole point of this transport. The digest still names it.
    const due = items
      .filter((i) => subjectOf(i))
      .sort((a, b) => a.when - b.when)
      .slice(0, MAX_PER_USER);

    for (const item of due) {
      const subject = subjectOf(item);
      if (sentAlready.has(seenKey(userId, subject))) {
        skipped++;
        continue;
      }
      let delivered = false;
      for (const token of devices) {
        const res = await fetch(endpoint, {
          method: 'POST',
          headers: { Authorization: `Bearer ${bearer}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(buildMessage(item, token)),
        });
        if (res.ok) {
          delivered = true;
          continue;
        }
        const body = await res.text();
        if (isDeadToken(res.status, body)) dead.push(token);
        else console.error('fcm send failed', res.status, body);
      }
      if (delivered) {
        sent++;
        recorded.push({ user_id: userId, subject, sent_on: day });
      }
    }
  }

  // Recorded after the fact, so a send that failed is retried on the next run rather
  // than marked delivered.
  if (recorded.length > 0) {
    await admin.from('fcm_sent').upsert(recorded, { onConflict: 'user_id,subject,sent_on' });
  }
  // A token for an uninstalled app never comes back; leaving it costs a failed request
  // every run forever.
  if (dead.length > 0) {
    await admin.from('fcm_tokens').delete().in('token', dead);
  }

  return new Response(
    JSON.stringify({ sent, skipped, devices: tokens.length, pruned: dead.length }),
    { headers: { 'Content-Type': 'application/json' } },
  );
});
