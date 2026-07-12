// Direct browser-to-Claude-API client. Anthropic's Messages API is CORS
// -blocked by default; anthropic-dangerous-direct-browser-access is the
// documented opt-in for exactly this shape of app -- a client-side tool
// using the user's own key, which never leaves their device (stored in
// Settings, excluded from Drive/cloud sync, sent only to api.anthropic.com).

const CLAUDE_API_URL = 'https://api.anthropic.com/v1/messages';
const ANTHROPIC_VERSION = '2023-06-01';

// `content` is either a plain string (the common case) or an array of
// segments -- [{ type: 'text', text }, { type: 'image', mimeType,
// dataBase64 }] -- for multimodal calls like Documents' camera capture.
// Mapped here to Anthropic's own content-block shape; a same-shaped array
// is mapped in gemini-client.js to Gemini's part shape, so api.js callers
// build one content array without knowing which provider is active.
function toClaudeContent(content) {
  if (typeof content === 'string') return content;
  return content.map((seg) => (seg.type === 'image'
    ? { type: 'image', source: { type: 'base64', media_type: seg.mimeType, data: seg.dataBase64 } }
    : { type: 'text', text: seg.text }));
}

// `messages` is [{ role: 'user' | 'assistant', content }, ...] in
// chronological order. Returns { text }. Throws with Anthropic's own error
// message on failure (bad key, rate limit, etc.) so the UI can show it as-is.
export async function sendClaudeMessage(apiKey, messages, { model, maxTokens = 1024 } = {}) {
  if (!apiKey) throw new Error('No Anthropic API key set -- add one in Settings.');
  const body = { model, max_tokens: maxTokens, messages: messages.map((m) => ({ role: m.role, content: toClaudeContent(m.content) })) };
  const res = await fetch(CLAUDE_API_URL, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-api-key': apiKey,
      'anthropic-version': ANTHROPIC_VERSION,
      'anthropic-dangerous-direct-browser-access': 'true',
    },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(data?.error?.message || `Claude API error (HTTP ${res.status})`);
  }
  const text = (data?.content || []).filter((b) => b.type === 'text').map((b) => b.text).join('');
  return { text };
}
