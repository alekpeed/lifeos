// Life OS -- Telegram webhook (Supabase Edge Function, Deno runtime)
// ============================================================
// Two-way Telegram: Telegram POSTs each incoming message here, and this replies
// + acts on it. A static PWA can't receive webhooks; this can. Reads/writes the
// user's data straight in sync_records (the migrated Postgres backend), so a
// message texted from anywhere lands in the app on its next sync.
//
// v1 behavior:
//   /start <token>  -> link this Telegram chat to a Life OS account (the token
//                      is minted in the app: Settings -> Telegram -> Connect)
//   /task <text>    -> create a task
//   /due            -> reply with what's due soon
//   /help           -> list commands
//   any other text  -> capture as an Idea
//
// Deploy with --no-verify-jwt (Telegram sends no Supabase auth header); security
// is the secret-token header instead (set via setWebhook, checked below).
//
// Secrets: TELEGRAM_BOT_TOKEN, TELEGRAM_WEBHOOK_SECRET.
// Auto-injected: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.

import { createClient } from 'npm:@supabase/supabase-js@2';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const BOT_TOKEN = Deno.env.get('TELEGRAM_BOT_TOKEN')!;
const WEBHOOK_SECRET = Deno.env.get('TELEGRAM_WEBHOOK_SECRET') ?? '';

const admin = createClient(SUPABASE_URL, SERVICE_ROLE);
const TG = `https://api.telegram.org/bot${BOT_TOKEN}`;
const DUE_SOON_DAYS = 3;

function nowIso() {
  return new Date().toISOString();
}

async function reply(chatId: number, text: string) {
  await fetch(`${TG}/sendMessage`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ chat_id: chatId, text }),
  });
}

// Insert a record into the same table the app syncs from, in the same shape the
// app's own create() produces (id/createdAt/updatedAt + fields). It pulls down
// to the app on the next sync.
async function insertRecord(userId: string, store: string, data: Record<string, unknown>) {
  await admin.from('sync_records').insert({
    user_id: userId,
    store,
    record_id: data.id,
    data,
    updated_at: data.updatedAt,
    deleted_at: null,
  });
}

async function userForChat(chatId: number): Promise<string | null> {
  const { data } = await admin.from('telegram_links').select('user_id').eq('telegram_chat_id', chatId).maybeSingle();
  return data?.user_id ?? null;
}

function daysUntil(dateStr?: string): number | null {
  if (!dateStr) return null;
  const target = new Date(dateStr + 'T00:00:00');
  if (Number.isNaN(target.getTime())) return null;
  const now = new Date();
  const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target.getTime() - midnight.getTime()) / 86400000);
}

async function dueSummary(userId: string): Promise<string> {
  const { data: rows } = await admin
    .from('sync_records')
    .select('store, data')
    .eq('user_id', userId)
    .in('store', ['bills', 'tasks', 'assignments', 'documents'])
    .is('deleted_at', null);

  const lines: { label: string; when: number }[] = [];
  for (const r of rows ?? []) {
    const d = r.data as Record<string, unknown>;
    if (r.store === 'bills' && !d.paid) {
      const n = daysUntil(d.dueDate as string);
      if (n !== null && n <= DUE_SOON_DAYS) lines.push({ label: `💵 ${d.name ?? 'Bill'}`, when: n });
    } else if ((r.store === 'tasks' || r.store === 'assignments') && d.status !== 'done') {
      const n = daysUntil(d.dueDate as string);
      if (n !== null && n <= DUE_SOON_DAYS) lines.push({ label: `${r.store === 'tasks' ? '✅' : '🎓'} ${d.title ?? 'Item'}`, when: n });
    } else if (r.store === 'documents') {
      const n = daysUntil(d.expiryDate as string);
      if (n !== null && n <= DUE_SOON_DAYS) lines.push({ label: `📄 ${d.title ?? 'Document'} expires`, when: n });
    }
  }
  if (lines.length === 0) return 'Nothing due in the next few days. 🎉';
  lines.sort((a, b) => a.when - b.when);
  const fmt = (n: number) => (n < 0 ? 'overdue' : n === 0 ? 'today' : `in ${n}d`);
  return 'Due soon:\n' + lines.map((l) => `• ${l.label} — ${fmt(l.when)}`).join('\n');
}

Deno.serve(async (req) => {
  if (WEBHOOK_SECRET && req.headers.get('x-telegram-bot-api-secret-token') !== WEBHOOK_SECRET) {
    return new Response('forbidden', { status: 403 });
  }

  const update = await req.json().catch(() => null);
  const msg = update?.message;
  const text: string | undefined = msg?.text?.trim();
  if (!msg || !text) return new Response('ok'); // ignore non-text updates
  const chatId: number = msg.chat.id;

  // --- Linking: /start <token> ---
  if (text.startsWith('/start')) {
    const token = text.slice('/start'.length).trim();
    if (!token) {
      await reply(chatId, 'Hi! Open Life OS → Settings → Telegram and tap Connect to link this chat to your account.');
      return new Response('ok');
    }
    const { data: tok } = await admin.from('telegram_link_tokens').select('user_id').eq('token', token).maybeSingle();
    if (!tok) {
      await reply(chatId, 'That link is invalid or already used. Generate a fresh one in Life OS → Settings → Telegram.');
      return new Response('ok');
    }
    await admin.from('telegram_links').upsert({ user_id: tok.user_id, telegram_chat_id: chatId }, { onConflict: 'user_id' });
    await admin.from('telegram_link_tokens').delete().eq('token', token);
    await reply(chatId, '✅ Connected! Text me anything to capture it as an idea, or try /task, /due, /help.');
    return new Response('ok');
  }

  const userId = await userForChat(chatId);
  if (!userId) {
    await reply(chatId, 'This chat isn\'t linked yet. Open Life OS → Settings → Telegram → Connect.');
    return new Response('ok');
  }

  if (text === '/help') {
    await reply(chatId, 'Commands:\n/task <text> — add a task\n/due — what\'s due soon\nAnything else — captured as an idea');
    return new Response('ok');
  }
  if (text === '/due') {
    await reply(chatId, await dueSummary(userId));
    return new Response('ok');
  }
  if (text.startsWith('/task')) {
    const title = text.slice('/task'.length).trim();
    if (!title) {
      await reply(chatId, 'Usage: /task buy milk');
      return new Response('ok');
    }
    await insertRecord(userId, 'tasks', {
      id: crypto.randomUUID(), createdAt: nowIso(), updatedAt: nowIso(),
      title, status: 'not_started', priority: 'low',
    });
    await reply(chatId, `✅ Task added: ${title}`);
    return new Response('ok');
  }

  // Default: capture as an Idea.
  await insertRecord(userId, 'ideas', {
    id: crypto.randomUUID(), createdAt: nowIso(), updatedAt: nowIso(),
    text, archived: false,
  });
  await reply(chatId, '✅ Captured as an idea.');
  return new Response('ok');
});
