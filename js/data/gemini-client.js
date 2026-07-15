// Direct browser-to-Gemini-API client. Unlike Anthropic (which has a
// documented direct-browser-access opt-in) and unlike OpenAI (which has no
// such opt-in and structurally refuses any browser-origin request), Gemini's
// API simply doesn't send CORS-blocking headers for this -- a plain fetch
// with the key in a header works with no server in between, same shape as
// claude-client.js.

const GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

// `content` is either a plain string (the common case) or an array of
// segments -- [{ type: 'text', text }, { type: 'image', mimeType,
// dataBase64 }] -- for multimodal calls like Documents' camera capture.
// Mapped here to Gemini's own part shape; a same-shaped array is mapped in
// claude-client.js to Anthropic's block shape, so api.js callers build one
// content array without knowing which provider is active.
function toGeminiParts(content) {
  if (typeof content === 'string') return [{ text: content }];
  return content.map((seg) => (seg.type === 'image'
    ? { inline_data: { mime_type: seg.mimeType, data: seg.dataBase64 } }
    : { text: seg.text }));
}

// `messages` is [{ role: 'user' | 'assistant', content }, ...] in
// chronological order, same shape callers already use for Claude -- mapped
// here to Gemini's own { role: 'user' | 'model', parts } shape so api.js
// doesn't need to know the difference. Returns { text }. Throws with
// Gemini's own error message on failure (bad key, rate limit, etc.) so the
// UI can show it as-is.
export async function sendGeminiMessage(apiKey, messages, { model, maxTokens = 1024 } = {}) {
  if (!apiKey) throw new Error('No Gemini API key set -- add one in Settings.');
  const contents = messages.map((m) => ({
    role: m.role === 'assistant' ? 'model' : 'user',
    parts: toGeminiParts(m.content),
  }));
  const res = await fetch(`${GEMINI_API_BASE}/${model}:generateContent`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-goog-api-key': apiKey,
    },
    body: JSON.stringify({ contents, generationConfig: { maxOutputTokens: maxTokens } }),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(data?.error?.message || `Gemini API error (HTTP ${res.status})`);
  }
  const parts = data?.candidates?.[0]?.content?.parts || [];
  const text = parts.map((p) => p.text || '').join('');
  if (!text) {
    // A response can come back with no text and a non-STOP finishReason
    // (e.g. blocked by a safety filter) rather than an HTTP error -- surface
    // that distinctly instead of returning silent emptiness.
    const reason = data?.candidates?.[0]?.finishReason;
    throw new Error(reason && reason !== 'STOP' ? `Gemini returned no text (${reason}).` : 'Gemini returned an empty response.');
  }
  return { text };
}

// Embeddings for Semantic memory (the Ask module). Gemini has a free embedding
// endpoint on the same key; Anthropic has no first-party embeddings API, which
// is why this is Gemini-only. Returns a Float32-friendly number[] (768 dims
// for text-embedding-004). Throws with Gemini's own message on failure.
const GEMINI_EMBED_MODEL = 'text-embedding-004';
export async function embedTextGemini(apiKey, text) {
  if (!apiKey) throw new Error('Semantic memory needs a Gemini API key (Settings > AI Assistant) — Anthropic has no embeddings API.');
  const res = await fetch(`${GEMINI_API_BASE}/${GEMINI_EMBED_MODEL}:embedContent`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-goog-api-key': apiKey },
    body: JSON.stringify({ content: { parts: [{ text: String(text).slice(0, 8000) }] } }),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.error?.message || `Gemini embedding error (HTTP ${res.status})`);
  const values = data?.embedding?.values;
  if (!Array.isArray(values) || !values.length) throw new Error('Gemini returned no embedding.');
  return values;
}
