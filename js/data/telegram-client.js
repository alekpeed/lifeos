// Telegram Bot API client, send-only. The app messages you through a bot you
// create yourself (via @BotFather) -- there's no listener for incoming
// messages, since a static PWA can't run a persistent process when it's not
// open. Triggered only by your own action (a button), never automatically,
// same "foreground/user-triggered" philosophy as the geolocation nudges in
// Places.

const TELEGRAM_API_BASE = 'https://api.telegram.org';

export async function sendTelegramMessage(botToken, chatId, text) {
  if (!botToken || !chatId) throw new Error('Telegram bot token / chat ID not set -- add both in Settings.');
  const res = await fetch(`${TELEGRAM_API_BASE}/bot${botToken}/sendMessage`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ chat_id: chatId, text }),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok || !data?.ok) {
    throw new Error(data?.description || `Telegram API error (HTTP ${res.status})`);
  }
  return data;
}
