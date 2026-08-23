// Life OS — the scheduled digest, over Telegram (Supabase Edge Function, Deno).
// =============================================================================
// §7 D-5 Phase 1. The first half of real server push: something that reaches you
// when the app is closed, without Firebase, without device tokens, and without a
// service account.
//
// It borrows Telegram's own push infrastructure. On a cron schedule it reads what
// is due out of the rows the app already syncs, builds one digest per account, and
// posts it to that account's linked chat. Because it is a chat message rather than
// a device notification, it lands on the phone AND the laptop — which is the one
// thing FCM (Phase 2) cannot do.
//
// Why this is a NEW function rather than an edit of send-push/index.ts:
//
//   send-push queries `store in ('bills','tasks','assignments','documents')` with
//   one row per record. That was the web app's schema. The native app syncs one row
//   per Storage KEY under store='kv', each holding a whole module's JSON blob in
//   data.text — so that query matches nothing the native app has ever written. The
//   two read completely different shapes; sharing one file would mean a function
//   that is half dead code either way. send-push stays where it is for Phase 2,
//   which will reuse its VAPID/token plumbing rather than its query.
//
// Required secrets (supabase secrets set ...):
//   TELEGRAM_BOT_TOKEN — the same bot the webhook already uses.
// Auto-injected by Supabase: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.
//
// Deploy:  supabase functions deploy telegram-digest
// Schedule: sql/supabase-telegram-digest-cron.sql

import { createClient } from 'npm:@supabase/supabase-js@2';
import { buildMessage, itemsFrom, KEYS, type Item } from './digest.ts';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const BOT_TOKEN = Deno.env.get('TELEGRAM_BOT_TOKEN')!;

const TG = `https://api.telegram.org/bot${BOT_TOKEN}`;
const admin = createClient(SUPABASE_URL, SERVICE_ROLE);

async function send(chatId: number, text: string): Promise<boolean> {
  const res = await fetch(`${TG}/sendMessage`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ chat_id: chatId, text, disable_notification: false }),
  });
  if (!res.ok) console.error('telegram send failed', chatId, res.status, await res.text());
  return res.ok;
}

// ---- the run ---------------------------------------------------------------------

Deno.serve(async () => {
  // Only accounts with a linked chat can be reached at all, so start there and read
  // nobody else's rows — a digest for an account that cannot receive it is a pointless
  // read of their data.
  const { data: links, error: linkErr } = await admin
    .from('telegram_links')
    .select('user_id, telegram_chat_id');
  if (linkErr) {
    return new Response(JSON.stringify({ error: linkErr.message }), { status: 500 });
  }
  if (!links || links.length === 0) {
    return new Response(JSON.stringify({ sent: 0, reason: 'no linked chats' }));
  }

  const userIds = links.map((l) => l.user_id);
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

  const byUser = new Map<string, Item[]>();
  for (const r of rows ?? []) {
    // The native app stores the module's whole JSON blob as a string under data.text.
    const text = (r.data as Record<string, unknown> | null)?.text;
    if (typeof text !== 'string' || !text.trim()) continue;
    let blob: unknown;
    try {
      blob = JSON.parse(text);
    } catch {
      continue; // one unreadable blob must not cost the rest of the digest
    }
    const found = itemsFrom(r.record_id, blob);
    if (found.length === 0) continue;
    byUser.set(r.user_id, [...(byUser.get(r.user_id) ?? []), ...found]);
  }

  let sent = 0;
  let quiet = 0;
  for (const link of links) {
    const items = byUser.get(link.user_id);
    // Silence when there is nothing due. A digest that arrives every day whether or
    // not it has anything to say is one you stop reading.
    if (!items || items.length === 0) {
      quiet++;
      continue;
    }
    if (await send(Number(link.telegram_chat_id), buildMessage(items))) sent++;
  }

  return new Response(
    JSON.stringify({ sent, quiet, accounts: links.length }),
    { headers: { 'Content-Type': 'application/json' } },
  );
});
