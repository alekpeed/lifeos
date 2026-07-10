// Direct browser-to-Claude-API client. Anthropic's Messages API is CORS
// -blocked by default; anthropic-dangerous-direct-browser-access is the
// documented opt-in for exactly this shape of app -- a client-side tool
// using the user's own key, which never leaves their device (stored in
// Settings, excluded from Drive/cloud sync, sent only to api.anthropic.com).

const CLAUDE_API_URL = 'https://api.anthropic.com/v1/messages';
const ANTHROPIC_VERSION = '2023-06-01';

// `messages` is [{ role: 'user' | 'assistant', content: string }, ...] in
// chronological order. Returns { text }. Throws with Anthropic's own error
// message on failure (bad key, rate limit, etc.) so the UI can show it as-is.
export async function sendClaudeMessage(apiKey, messages, { model, maxTokens = 1024 } = {}) {
  if (!apiKey) throw new Error('No Anthropic API key set -- add one in Settings.');
  const res = await fetch(CLAUDE_API_URL, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-api-key': apiKey,
      'anthropic-version': ANTHROPIC_VERSION,
      'anthropic-dangerous-direct-browser-access': 'true',
    },
    body: JSON.stringify({ model, max_tokens: maxTokens, messages }),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(data?.error?.message || `Claude API error (HTTP ${res.status})`);
  }
  const text = (data?.content || []).filter((b) => b.type === 'text').map((b) => b.text).join('');
  return { text };
}
