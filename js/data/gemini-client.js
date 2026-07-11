// Direct browser-to-Gemini-API client. Unlike Anthropic (which has a
// documented direct-browser-access opt-in) and unlike OpenAI (which has no
// such opt-in and structurally refuses any browser-origin request), Gemini's
// API simply doesn't send CORS-blocking headers for this -- a plain fetch
// with the key in a header works with no server in between, same shape as
// claude-client.js.

const GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

// `messages` is [{ role: 'user' | 'assistant', content: string }, ...] in
// chronological order, same shape callers already use for Claude -- mapped
// here to Gemini's own { role: 'user' | 'model', parts: [{ text }] } shape
// so api.js doesn't need to know the difference. Returns { text }. Throws
// with Gemini's own error message on failure (bad key, rate limit, etc.) so
// the UI can show it as-is.
export async function sendGeminiMessage(apiKey, messages, { model, maxTokens = 1024 } = {}) {
  if (!apiKey) throw new Error('No Gemini API key set -- add one in Settings.');
  const contents = messages.map((m) => ({
    role: m.role === 'assistant' ? 'model' : 'user',
    parts: [{ text: m.content }],
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
