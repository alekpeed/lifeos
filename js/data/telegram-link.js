// Two-way Telegram -- client half of the account<->chat linking. The bot
// itself is a Supabase Edge Function (supabase/functions/telegram-webhook);
// this only mints the one-time link token and reports link status. Gated on
// Supabase being configured + signed in (the link is per-account).

import { getSupabaseClient } from './supabase-client.js';
import { isSupabaseConfigured } from './supabase-config.js';

async function currentUser() {
  const supabase = await getSupabaseClient();
  const { data } = await supabase.auth.getUser();
  return { supabase, user: data?.user ?? null };
}

export async function getTelegramLinkState() {
  const state = { configured: isSupabaseConfigured(), signedIn: false, linked: false };
  if (!state.configured) return state;
  try {
    const { supabase, user } = await currentUser();
    if (!user) return state;
    state.signedIn = true;
    const { data } = await supabase.from('telegram_links').select('telegram_chat_id').eq('user_id', user.id).maybeSingle();
    state.linked = Boolean(data);
  } catch { /* signed out / offline -> unlinked */ }
  return state;
}

// Mints a one-time token, resolves the bot's @username via getMe (so we can
// build the t.me deep link), and returns the link the user taps to connect.
// Needs the bot token from Settings only to read the username.
export async function createTelegramDeepLink(botToken) {
  if (!botToken) throw new Error('Add your bot token above first (from @BotFather).');
  const { supabase, user } = await currentUser();
  if (!user) throw new Error('Sign in (Settings > Account) before connecting Telegram.');

  const meRes = await fetch(`https://api.telegram.org/bot${botToken}/getMe`);
  const me = await meRes.json().catch(() => null);
  if (!me?.ok || !me.result?.username) throw new Error('Could not read the bot — double-check the bot token above.');

  // Telegram /start payloads allow only [A-Za-z0-9_-], max 64 chars.
  const token = crypto.randomUUID().replace(/-/g, '');
  const { error } = await supabase.from('telegram_link_tokens').insert({ token, user_id: user.id });
  if (error) throw error;

  return `https://t.me/${me.result.username}?start=${token}`;
}

export async function unlinkTelegram() {
  const { supabase, user } = await currentUser();
  if (!user) return;
  await supabase.from('telegram_links').delete().eq('user_id', user.id);
}
