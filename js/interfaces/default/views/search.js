import { el } from '../dom.js';

let state = {
  query: '',
};

function resultRow(result, ctx) {
  const row = el('div', { class: 'mer-task-row' }, [
    el('span', { class: 'mer-task-title', text: result.title }),
    el('div', { class: 'mer-task-meta' }, [
      el('span', { class: 'mer-chip', text: ctx.modules.find((m) => m.id === result.module)?.label || result.module }),
    ]),
  ]);
  row.addEventListener('click', () => ctx.navigate(result.module));
  return row;
}

export async function renderSearch(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Search' }));

  const searchInput = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: 'Search across every module…', value: state.query,
    onchange: (e) => { state.query = e.target.value; rerender(); },
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [searchInput]));

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  if (!state.query.trim()) {
    area.append(el('p', { class: 'mer-muted', text: 'Type a search term and press Tab or Enter.' }));
    return;
  }

  const results = await ctx.data.globalSearch(state.query);
  if (!results.length) {
    area.append(el('p', { class: 'mer-muted', text: `No matches for "${state.query}".` }));
    return;
  }

  const byModule = new Map();
  for (const r of results) {
    if (!byModule.has(r.module)) byModule.set(r.module, []);
    byModule.get(r.module).push(r);
  }

  for (const [moduleId, items] of byModule) {
    const mod = ctx.modules.find((m) => m.id === moduleId);
    area.append(el('div', { class: 'mer-group-label', text: mod?.label || moduleId }));
    for (const item of items) area.append(resultRow(item, ctx));
  }
}
