// Ideas — a deliberately unstructured catch-all for anything worth jotting
// down before it evaporates. No folders, no tags, no module to pick first;
// that upfront categorization is exactly the friction that keeps stray
// thoughts from getting captured at all. An idea can later be promoted into
// a real Task once it's actually actionable, or just archived once it's run
// its course.

import { el, fmtDate } from '../dom.js';

let state = { showArchived: false, listening: false };

const SpeechRecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition;

// Voice capture uses whatever speech recognition the browser ships with --
// in Chrome that's a cloud call to Google, not a local model, so it needs
// network and isn't private the way an on-device Whisper build would be.
// That's a real, much bigger project (its own model download + WASM/WebGPU
// inference) tracked separately; this is the fast, works-today version.
function micButton(textarea, rerender) {
  if (!SpeechRecognitionCtor) return null;
  const btn = el('button', {
    type: 'button', class: state.listening ? 'mer-icon-btn is-active' : 'mer-icon-btn',
    title: 'Dictate (uses your browser\'s speech recognition)',
    text: state.listening ? '🔴 Listening…' : '🎤',
  });
  btn.addEventListener('click', () => {
    if (state.listening) return;
    const recognizer = new SpeechRecognitionCtor();
    recognizer.lang = navigator.language || 'en-US';
    recognizer.interimResults = false;
    recognizer.onresult = (e) => {
      const transcript = [...e.results].map((r) => r[0].transcript).join(' ');
      textarea.value = (textarea.value.trim() + ' ' + transcript).trim();
    };
    recognizer.onerror = () => { state.listening = false; rerender(); };
    recognizer.onend = () => { state.listening = false; rerender(); };
    state.listening = true;
    rerender();
    recognizer.start();
  });
  return btn;
}

function captureBar(ctx, rerender) {
  const textarea = el('textarea', { rows: '3', placeholder: 'Whatever\'s on your mind…' });
  const save = async () => {
    const text = textarea.value.trim();
    if (!text) return;
    await ctx.data.Ideas.create({ text, archived: false });
    rerender();
  };
  textarea.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) { e.preventDefault(); save(); }
  });
  const mic = micButton(textarea, rerender);
  return el('div', { class: 'mer-person-form' }, [
    textarea,
    el('div', { class: 'mer-toolbar' }, [
      mic,
      el('button', { type: 'button', text: '+ Save idea', onclick: save }),
      el('span', { class: 'mer-muted', text: 'Ctrl/Cmd+Enter to save' }),
    ].filter(Boolean)),
  ]);
}

function ideaRow(idea, ctx, rerender) {
  return el('div', { class: 'mer-task-detail' }, [
    el('p', { text: idea.text }),
    el('div', { class: 'mer-toolbar' }, [
      el('span', { class: 'mer-muted', text: fmtDate((idea.createdAt || '').slice(0, 10)) }),
      el('button', {
        type: 'button', text: '→ Task',
        title: 'Turn this into a Task',
        onclick: async () => {
          await ctx.data.Tasks.create({ title: idea.text, status: 'open' });
          await ctx.data.Ideas.update(idea.id, { archived: true });
          rerender();
        },
      }),
      el('button', {
        type: 'button', text: idea.archived ? 'Unarchive' : 'Archive',
        onclick: async () => { await ctx.data.Ideas.update(idea.id, { archived: !idea.archived }); rerender(); },
      }),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async () => { if (confirm('Delete this idea?')) { await ctx.data.Ideas.remove(idea.id); rerender(); } },
      }),
    ]),
  ]);
}

export async function renderIdeas(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Ideas' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Anything worth remembering, before it slips. No category to pick, no wrong place to put it.' }));
  canvas.append(captureBar(ctx, rerender));

  const all = (await ctx.data.Ideas.list()).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const active = all.filter((i) => !i.archived);
  const archived = all.filter((i) => i.archived);

  canvas.append(el('div', { class: 'mer-subsection-label', text: `Ideas (${active.length})` }));
  if (!active.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing yet — jot something down above.' }));
  } else {
    canvas.append(el('div', { class: 'mer-people-list' }, active.map((i) => ideaRow(i, ctx, rerender))));
  }

  if (archived.length) {
    canvas.append(el('button', {
      type: 'button', class: 'mer-reader-btn',
      text: state.showArchived ? 'Hide archived' : `Show archived (${archived.length})`,
      onclick: () => { state.showArchived = !state.showArchived; rerender(); },
    }));
    if (state.showArchived) {
      canvas.append(el('div', { class: 'mer-people-list' }, archived.map((i) => ideaRow(i, ctx, rerender))));
    }
  }
}
