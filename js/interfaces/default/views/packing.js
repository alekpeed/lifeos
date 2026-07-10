// Trip Packing Lists — a checklist per trip. "Auto-suggested" is a set of
// built-in templates (weekend / beach / ski / international) that bulk-add
// common items in one click; nothing here calls out to any external service.

import { el, fmtDate } from '../dom.js';

let state = { selectedId: null };

const TEMPLATES = {
  weekend: {
    label: 'Weekend trip',
    items: [
      ['Clothing', ['Underwear', 'Socks', 'Sleepwear', 'Casual outfit']],
      ['Toiletries', ['Toothbrush', 'Toothpaste', 'Deodorant']],
      ['Documents', ['ID / license', 'Wallet']],
      ['Electronics', ['Phone charger']],
    ],
  },
  beach: {
    label: 'Beach / warm',
    items: [
      ['Clothing', ['Swimsuit', 'Sandals', 'Sunhat', 'Light clothing']],
      ['Toiletries', ['Sunscreen', 'After-sun lotion', 'Toothbrush']],
      ['Gear', ['Beach towel', 'Sunglasses', 'Reusable water bottle']],
      ['Documents', ['ID / license']],
    ],
  },
  ski: {
    label: 'Ski / cold',
    items: [
      ['Clothing', ['Thermal base layers', 'Winter jacket', 'Gloves', 'Wool socks', 'Beanie']],
      ['Gear', ['Goggles', 'Hand warmers']],
      ['Toiletries', ['Lip balm', 'Moisturizer', 'Toothbrush']],
      ['Documents', ['ID / license']],
    ],
  },
  international: {
    label: 'International',
    items: [
      ['Documents', ['Passport', 'Visa (if needed)', 'Travel insurance', 'Copies of documents']],
      ['Electronics', ['Power adapter', 'Phone charger', 'Offline maps downloaded']],
      ['Clothing', ['Underwear', 'Socks', 'Versatile outfits']],
      ['Toiletries', ['Toothbrush', 'Toothpaste (travel size)']],
      ['Money', ['Local currency', 'Backup card']],
    ],
  },
};

function itemRow(item, ctx, rerender) {
  return el('div', { class: 'mer-task-row' }, [
    el('input', {
      type: 'checkbox', checked: item.packed,
      onclick: async (e) => { await ctx.data.PackingItems.update(item.id, { packed: e.target.checked }); rerender(); },
    }),
    el('span', { class: item.packed ? 'mer-task-title is-done' : 'mer-task-title', text: item.name || '(untitled)' }),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Remove',
      onclick: async () => { await ctx.data.PackingItems.remove(item.id); rerender(); },
    }),
  ]);
}

async function renderListDetail(canvas, list, ctx, rerender) {
  canvas.append(el('div', { class: 'mer-detail-header' }, [
    el('h1', { text: list.name || '(untitled trip)' }),
    el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
  ]));
  if (list.tripDate) canvas.append(el('p', { class: 'mer-muted', text: fmtDate(list.tripDate) }));

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Add from a template' }));
  const templateRow = el('div', { class: 'mer-toolbar' }, Object.entries(TEMPLATES).map(([key, tpl]) =>
    el('button', {
      type: 'button', text: tpl.label,
      onclick: async () => {
        for (const [category, names] of tpl.items) {
          for (const name of names) {
            await ctx.data.PackingItems.create({ listId: list.id, name, category, packed: false });
          }
        }
        rerender();
      },
    })));
  canvas.append(templateRow);

  const nameIn = el('input', { type: 'text', placeholder: 'Add an item…' });
  const catIn = el('input', { type: 'text', placeholder: 'Category (optional)' });
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Add an item' }));
  canvas.append(el('div', { class: 'mer-person-form' }, [nameIn, catIn, el('button', {
    type: 'button', text: 'Add',
    onclick: async () => {
      if (!nameIn.value.trim()) return;
      await ctx.data.PackingItems.create({ listId: list.id, name: nameIn.value.trim(), category: catIn.value.trim() || 'Other', packed: false });
      nameIn.value = ''; catIn.value = '';
      rerender();
    },
  })]));

  const items = await ctx.data.PackingItems.byIndex('listId', list.id);
  const packedCount = items.filter((i) => i.packed).length;
  canvas.append(el('div', { class: 'mer-subsection-label', text: `Packed ${packedCount} / ${items.length}` }));

  if (!items.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing on this list yet — add items or use a template above.' }));
    return;
  }

  const byCategory = new Map();
  for (const item of items) {
    const cat = item.category || 'Other';
    if (!byCategory.has(cat)) byCategory.set(cat, []);
    byCategory.get(cat).push(item);
  }
  for (const [cat, catItems] of byCategory) {
    canvas.append(el('div', { class: 'mer-subsection-label', text: cat }));
    const wrap = el('div', { class: 'mer-task-list-area' });
    for (const item of catItems) wrap.append(itemRow(item, ctx, rerender));
    canvas.append(wrap);
  }
}

function listCard(list, progress, onSelect, ctx, rerender) {
  const card = el('div', { class: 'mer-place-card' }, [
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: list.name || '(untitled trip)' }),
      el('div', { class: 'mer-place-meta' }, [
        list.tripDate ? el('span', { class: 'mer-chip', text: fmtDate(list.tripDate) }) : null,
        el('span', { class: 'mer-chip', text: `${progress.packed}/${progress.total} packed` }),
      ].filter(Boolean)),
    ]),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Delete list',
      onclick: async (e) => {
        e.stopPropagation();
        if (!confirm(`Delete "${list.name}" and all its items?`)) return;
        const items = await ctx.data.PackingItems.byIndex('listId', list.id);
        for (const item of items) await ctx.data.PackingItems.remove(item.id);
        await ctx.data.PackingLists.remove(list.id);
        rerender();
      },
    }),
  ]);
  card.addEventListener('click', () => onSelect(list.id));
  return card;
}

export async function renderPacking(canvas, ctx, rerender) {
  if (state.selectedId) {
    const list = await ctx.data.PackingLists.get(state.selectedId);
    if (list) return renderListDetail(canvas, list, ctx, rerender);
    state.selectedId = null;
  }

  canvas.append(el('h1', { text: 'Trip Packing Lists' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'One checklist per trip, with templates to get started fast.' }));

  const nameIn = el('input', { type: 'text', placeholder: 'Trip name (e.g. "Tokyo")' });
  const dateIn = el('input', { type: 'date' });
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'New trip' }));
  canvas.append(el('div', { class: 'mer-person-form' }, [nameIn, dateIn, el('button', {
    type: 'button', text: 'Create list',
    onclick: async () => {
      if (!nameIn.value.trim()) return;
      const list = await ctx.data.PackingLists.create({ name: nameIn.value.trim(), tripDate: dateIn.value || null });
      state.selectedId = list.id;
      rerender();
    },
  })]));

  const lists = await ctx.data.PackingLists.list();
  canvas.append(el('div', { class: 'mer-subsection-label', text: `Your trips (${lists.length})` }));
  if (!lists.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'No trips yet.' }));
    return;
  }
  const itemLists = await Promise.all(lists.map((l) => ctx.data.PackingItems.byIndex('listId', l.id)));
  const grid = el('div', { class: 'mer-place-grid' });
  lists.forEach((l, i) => {
    const items = itemLists[i];
    const progress = { packed: items.filter((it) => it.packed).length, total: items.length };
    grid.append(listCard(l, progress, (id) => { state.selectedId = id; rerender(); }, ctx, rerender));
  });
  canvas.append(grid);
}
