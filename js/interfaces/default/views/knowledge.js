// Knowledge Graph — link anything to anything (a task to a contact, a book
// to a milestone, a place to a document…) and walk the result as a radial
// web. Deliberately borrows the Harmony Map's interaction grammar: one thing
// in focus at the center, its connections as spokes, click a spoke to make
// IT the center and keep walking. Same mental model, different territory.
//
// Design decisions (agreed before building):
//   • Radial/hub layout, not a force-directed physics graph — consistent
//     with the app's existing visual identity, no new rendering machinery.
//   • "What is linkable" = "what Search can find": the graph reuses
//     globalSearch as its picker, so the two features share one definition
//     of addressable records (SEARCH_FIELDS in api.js) instead of drifting.
//   • Edges are undirected and title-free: endpoints resolve live at render
//     time (resolveGraphNode), so renaming a record never leaves a stale
//     label, and a deleted record shows an honest "(deleted)" tombstone
//     with an unlink offer instead of a silently lying node.

import { el } from '../dom.js';

let state = {
  focusKey: null,   // "<store>:<id>" of the record at the center, or null
  query: '',        // find-a-starting-point search text
  addQuery: '',     // add-a-connection search text
  suggestFor: null, // focusKey the current suggestions/loading/error state belongs to
  suggestions: null,
  suggestLoading: false,
  suggestError: null,
};

const SVG_NS = 'http://www.w3.org/2000/svg';

function svgEl(tag, attrs = {}, children = []) {
  const node = document.createElementNS(SVG_NS, tag);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  for (const child of children) node.append(child);
  return node;
}

function truncate(text, max = 24) {
  return text.length > max ? text.slice(0, max - 1) + '…' : text;
}

// Module id -> human label, from the ctx module list (no import needed).
function moduleLabel(ctx, moduleId) {
  return ctx.modules.find((m) => m.id === moduleId)?.label || moduleId || '?';
}

// --- The radial graph itself ---

function graphSvg(focus, neighbors, ctx, rerender) {
  const W = 660, H = 480, CX = W / 2, CY = H / 2;
  // Spoke length breathes a little with crowd size so a dense node ring
  // doesn't collide with the center card.
  const R = neighbors.length > 10 ? 195 : 170;
  const svg = svgEl('svg', { viewBox: `0 0 ${W} ${H}`, class: 'mer-kg' });

  const positioned = neighbors.map((n, i) => {
    const angle = (i / Math.max(1, neighbors.length)) * Math.PI * 2 - Math.PI / 2;
    return { ...n, x: CX + R * Math.cos(angle), y: CY + R * Math.sin(angle) };
  });

  // Edges under nodes.
  for (const n of positioned) {
    svg.append(svgEl('line', { x1: CX, y1: CY, x2: n.x, y2: n.y, class: 'mer-kg-edge' }));
  }

  // Neighbor nodes: rect + title + module kicker. Live nodes refocus on
  // click; tombstones ("(deleted)") are inert and dimmed.
  for (const n of positioned) {
    const label = truncate(n.title);
    const w = Math.max(72, label.length * 7.2 + 20);
    const node = svgEl('g', {
      class: n.exists ? 'mer-kg-node' : 'mer-kg-node is-gone',
      tabindex: n.exists ? '0' : '-1',
      role: n.exists ? 'button' : 'img',
    });
    node.append(svgEl('rect', { x: n.x - w / 2, y: n.y - 21, width: w, height: 42, rx: 10 }));
    node.append(svgEl('text', { x: n.x, y: n.y - 2, 'text-anchor': 'middle', class: 'mer-kg-title' }, [label]));
    node.append(svgEl('text', { x: n.x, y: n.y + 13, 'text-anchor': 'middle', class: 'mer-kg-kicker' }, [moduleLabel(ctx, n.module)]));
    if (n.exists) {
      const refocus = () => { state.focusKey = n.key; state.addQuery = ''; rerender(); };
      node.addEventListener('click', refocus);
      node.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); refocus(); } });
    }
    svg.append(node);
  }

  // Center node last, on top.
  const label = truncate(focus.title, 30);
  const cw = Math.max(110, label.length * 8 + 30);
  const center = svgEl('g', { class: 'mer-kg-node is-center' });
  center.append(svgEl('rect', { x: CX - cw / 2, y: CY - 27, width: cw, height: 54, rx: 14 }));
  center.append(svgEl('text', { x: CX, y: CY - 2, 'text-anchor': 'middle', class: 'mer-kg-title is-center' }, [label]));
  center.append(svgEl('text', { x: CX, y: CY + 16, 'text-anchor': 'middle', class: 'mer-kg-kicker' }, [moduleLabel(ctx, focus.module)]));
  svg.append(center);

  return svg;
}

// --- Search widgets (shared by "pick a focus" and "add a connection") ---

function searchForm(placeholder, initial, onSearch) {
  const input = el('input', {
    type: 'search', placeholder, value: initial,
    onkeydown: (e) => { if (e.key === 'Enter') onSearch(e.target.value); },
  });
  return el('div', { class: 'mer-person-form' }, [
    input,
    el('button', { type: 'button', text: 'Search', onclick: () => onSearch(input.value) }),
  ]);
}

function resultRow(result, ctx, actionLabel, onPick) {
  return el('div', { class: 'mer-task-row' }, [
    el('span', { class: 'mer-task-title', text: result.title }),
    el('span', { class: 'mer-chip', text: moduleLabel(ctx, result.module) }),
    el('button', { type: 'button', class: 'mer-reader-btn', text: actionLabel, onclick: onPick }),
  ]);
}

// --- View states ---

async function renderPickFocus(canvas, ctx, rerender) {
  canvas.append(el('p', { class: 'mer-muted', text: 'Pick anything — a task, a book, a person, a place — and see everything you’ve connected to it. Anything Search can find, the graph can link.' }));
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Find a starting point' }));
  canvas.append(searchForm('Search everything…', state.query, (q) => { state.query = q; rerender(); }));

  if (!state.query.trim()) return;
  const results = await ctx.data.globalSearch(state.query);
  if (!results.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'No matches.' }));
    return;
  }
  const area = el('div', { class: 'mer-task-list-area' });
  for (const r of results.slice(0, 30)) {
    area.append(resultRow(r, ctx, 'Focus →', () => {
      state.focusKey = ctx.data.graphKey(r.store, r.id);
      state.query = '';
      rerender();
    }));
  }
  canvas.append(area);
}

async function runSuggest(focus, ctx, rerender) {
  state.suggestLoading = true;
  state.suggestError = null;
  rerender();
  try {
    state.suggestions = await ctx.data.suggestGraphEdges(focus.key);
  } catch (err) {
    state.suggestError = err.message || String(err);
  }
  state.suggestLoading = false;
  rerender();
}

async function renderGraph(canvas, ctx, rerender) {
  const focus = await ctx.data.resolveGraphNode(state.focusKey);
  if (!focus.exists) {
    // The focused record itself was deleted (possible via another tab or a
    // sync). Fall back to the picker rather than centering a tombstone.
    state.focusKey = null;
    return renderPickFocus(canvas, ctx, rerender);
  }

  const links = await ctx.data.getGraphLinksFor(focus.store, focus.id);
  const neighbors = await Promise.all(links.map(async (link) => {
    const otherKey = link.fromKey === focus.key ? link.toKey : link.fromKey;
    const node = await ctx.data.resolveGraphNode(otherKey);
    return { ...node, linkId: link.id };
  }));

  canvas.append(el('div', { class: 'mer-toolbar' }, [
    el('button', { type: 'button', text: '← Change focus', onclick: () => { state.focusKey = null; state.query = ''; rerender(); } }),
    focus.module ? el('button', { type: 'button', text: `Open ${moduleLabel(ctx, focus.module)} →`, onclick: () => ctx.navigate(focus.module) }) : null,
  ].filter(Boolean)));

  canvas.append(graphSvg(focus, neighbors, ctx, rerender));

  // --- Manage connections (list mirrors the SVG, adds unlink) ---
  canvas.append(el('div', { class: 'mer-subsection-label', text: `Connections (${neighbors.length})` }));
  if (!neighbors.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing linked yet — connect something below.' }));
  } else {
    const area = el('div', { class: 'mer-task-list-area' });
    for (const n of neighbors) {
      area.append(el('div', { class: 'mer-task-row' }, [
        el('span', { class: n.exists ? 'mer-task-title' : 'mer-task-title is-done', text: n.title }),
        el('span', { class: 'mer-chip', text: moduleLabel(ctx, n.module) }),
        el('button', {
          type: 'button', class: 'mer-icon-btn', text: '×', title: 'Unlink',
          onclick: async () => { await ctx.data.GraphLinks.remove(n.linkId); rerender(); },
        }),
      ]));
    }
    canvas.append(area);
  }

  // --- AI-suggested connections: only ever offered from the same closed
  // candidate list resolveGraphNode/globalSearch already treat as "linkable"
  // -- see suggestGraphEdges in api.js for the no-invention guarantee. ---
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'AI-suggested connections' }));
  if (state.suggestFor !== focus.key) {
    state.suggestFor = focus.key;
    state.suggestions = null;
    state.suggestError = null;
  }
  const { apiKey: aiKey, label: aiLabel } = await ctx.data.getActiveAiProvider();
  if (!aiKey) {
    canvas.append(el('p', { class: 'mer-muted', text: `Add your ${aiLabel} API key in Settings to have ${aiLabel} propose non-obvious connections from your own data.` }));
  } else if (state.suggestLoading) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Thinking…' }));
  } else if (state.suggestError) {
    canvas.append(el('p', { class: 'mer-muted mer-sync-error', text: state.suggestError }));
    canvas.append(el('button', { type: 'button', text: 'Retry', onclick: () => runSuggest(focus, ctx, rerender) }));
  } else if (!state.suggestions) {
    canvas.append(el('button', { type: 'button', text: '✨ Suggest connections', onclick: () => runSuggest(focus, ctx, rerender) }));
  } else if (!state.suggestions.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing stood out this pass.' }), el('button', { type: 'button', text: '✨ Try again', onclick: () => runSuggest(focus, ctx, rerender) }));
  } else {
    const area = el('div', { class: 'mer-task-list-area' });
    for (const s of state.suggestions) {
      area.append(el('div', { class: 'mer-task-row' }, [
        el('div', {}, [
          el('span', { class: 'mer-task-title', text: s.title }),
          el('span', { class: 'mer-chip', text: moduleLabel(ctx, s.module) }),
          el('p', { class: 'mer-muted', text: s.reason }),
        ]),
        el('button', {
          type: 'button', class: 'mer-reader-btn', text: '+ Link',
          onclick: async () => {
            await ctx.data.createGraphLink(focus.store, focus.id, s.store, s.id);
            state.suggestions = state.suggestions.filter((x) => x.key !== s.key);
            rerender();
          },
        }),
      ]));
    }
    canvas.append(area);
    canvas.append(el('button', { type: 'button', class: 'mer-reader-btn', text: '✨ Suggest again', onclick: () => runSuggest(focus, ctx, rerender) }));
  }

  // --- Add a connection ---
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Add a connection' }));
  canvas.append(searchForm('Search for something to link…', state.addQuery, (q) => { state.addQuery = q; rerender(); }));

  if (state.addQuery.trim()) {
    const linkedKeys = new Set(neighbors.map((n) => n.key));
    const results = (await ctx.data.globalSearch(state.addQuery))
      .filter((r) => {
        const key = ctx.data.graphKey(r.store, r.id);
        return key !== focus.key && !linkedKeys.has(key);
      });
    if (!results.length) {
      canvas.append(el('p', { class: 'mer-muted', text: 'No linkable matches (already-linked records and the focus itself are hidden).' }));
    } else {
      const area = el('div', { class: 'mer-task-list-area' });
      for (const r of results.slice(0, 20)) {
        area.append(resultRow(r, ctx, '+ Link', async () => {
          await ctx.data.createGraphLink(focus.store, focus.id, r.store, r.id);
          state.addQuery = '';
          rerender();
        }));
      }
      canvas.append(area);
    }
  }
}

export async function renderKnowledge(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Knowledge Graph' }));
  if (state.focusKey) await renderGraph(canvas, ctx, rerender);
  else await renderPickFocus(canvas, ctx, rerender);
}
