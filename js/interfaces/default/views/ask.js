// Ask -- semantic memory. Type a natural-language question ("when did I last
// see Sarah?", "notes about the apartment") and get the actual records ranked
// by MEANING, not keyword overlap. Backed by per-record embeddings (Gemini,
// stored device-local) and client-side cosine similarity -- see
// buildSemanticIndex / semanticSearch in js/data/api.js.

import { el } from '../dom.js';

let state = { query: '', results: null, building: false, error: null };

function moduleLabel(ctx, moduleId) {
  return ctx.modules.find((m) => m.id === moduleId)?.label || moduleId;
}

async function runSearch(ctx, rerender) {
  const q = state.query.trim();
  if (!q) { state.results = null; rerender(); return; }
  state.error = null;
  try {
    state.results = await ctx.data.semanticSearch(q);
  } catch (err) {
    state.error = err.message || String(err);
    state.results = [];
  }
  rerender();
}

export async function renderAsk(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Ask' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Ask your life a question in plain language — "when did I last see Sarah?", "notes about the apartment" — and get the actual records, ranked by meaning rather than keywords.' }));

  const idx = await ctx.data.getSemanticIndexState();

  if (!idx.hasKey) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Semantic search needs a Gemini API key (Settings → AI Assistant). It\'s Gemini-only — Anthropic has no embeddings API.' }));
    return;
  }

  // --- Index status + build control ---
  const idxLine = el('p', {
    class: 'mer-muted',
    text: `Indexed ${idx.indexed} of ${idx.total} records${idx.stale ? ` · ${idx.stale} need (re)indexing` : ''}.`,
  });
  const buildBtn = el('button', {
    type: 'button',
    text: idx.stale || idx.indexed < idx.total ? 'Build / refresh index' : 'Rebuild index',
    disabled: state.building,
    onclick: async () => {
      state.building = true;
      state.error = null;
      rerender();
      try {
        await ctx.data.buildSemanticIndex((p) => { idxLine.textContent = `Indexing… ${p.done}/${p.total}`; });
      } catch (err) {
        state.error = err.message || String(err);
      }
      state.building = false;
      rerender();
    },
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [buildBtn]), idxLine);
  if (state.error) {
    const errP = el('p', { class: 'mer-muted', text: state.error });
    errP.classList.add('mer-sync-error');
    canvas.append(errP);
  }

  // --- Query ---
  const input = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: 'Ask a question…', value: state.query,
    onkeydown: async (e) => { if (e.key === 'Enter') { state.query = e.target.value; await runSearch(ctx, rerender); } },
  });
  const askBtn = el('button', {
    type: 'button', text: 'Ask',
    onclick: async () => { state.query = input.value; await runSearch(ctx, rerender); },
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [input, askBtn]));

  // --- Results ---
  if (state.results) {
    if (!state.results.length) {
      canvas.append(el('p', { class: 'mer-muted', text: idx.indexed === 0 ? 'Build the index first, then ask.' : 'No matches — try rephrasing.' }));
    } else {
      const list = el('div', { class: 'mer-people-list' });
      for (const r of state.results) {
        list.append(el('div', {
          class: 'mer-person-card', style: 'cursor:pointer',
          onclick: () => ctx.navigate(r.module),
        }, [
          el('div', { class: 'mer-person-info' }, [
            el('div', { class: 'mer-person-name', text: r.title }),
            el('div', { class: 'mer-person-meta' }, [
              el('span', { class: 'mer-chip', text: moduleLabel(ctx, r.module) }),
              el('span', { class: 'mer-muted', text: `${Math.round(r.score * 100)}% match` }),
            ]),
          ]),
        ]));
      }
      canvas.append(list);
    }
  }
}
