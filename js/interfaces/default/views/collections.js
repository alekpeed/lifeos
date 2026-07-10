// Collections Tracker — any freeform collection (records, cards, whatever)
// and the items in it. Deliberately schemaless beyond name/notes/tags: no
// per-collection custom fields, to keep this simple and generic.

import { el, fmtDate, parseTags } from '../dom.js';

let state = { selectedId: null };

function itemCard(item, ctx, rerender) {
  return el('div', { class: 'mer-person-card' }, [
    el('div', { class: 'mer-person-info' }, [
      el('div', { class: 'mer-person-name', text: item.name || '(untitled)' }),
      el('div', { class: 'mer-person-meta' }, [
        item.acquiredDate ? el('span', { text: fmtDate(item.acquiredDate) }) : null,
        ...(item.tags || []).map((t) => el('span', { class: 'mer-chip mer-chip-tag', text: `#${t}` })),
      ].filter(Boolean)),
      item.notes ? el('div', { class: 'mer-muted', text: item.notes }) : null,
    ]),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Remove',
      onclick: async () => { if (confirm('Remove this item?')) { await ctx.data.CollectionItems.remove(item.id); rerender(); } },
    }),
  ]);
}

function addItemForm(collectionId, ctx, rerender) {
  const nameIn = el('input', { type: 'text', placeholder: 'Item name' });
  const dateIn = el('input', { type: 'date' });
  const tagsIn = el('input', { type: 'text', placeholder: 'Tags (comma separated)' });
  const notesIn = el('input', { type: 'text', placeholder: 'Notes (optional)' });
  return el('div', { class: 'mer-person-form' }, [
    nameIn, dateIn, tagsIn, notesIn,
    el('button', {
      type: 'button', text: 'Add item',
      onclick: async () => {
        if (!nameIn.value.trim()) return;
        await ctx.data.CollectionItems.create({
          collectionId, name: nameIn.value.trim(), acquiredDate: dateIn.value || null,
          tags: parseTags(tagsIn.value), notes: notesIn.value.trim(),
        });
        rerender();
      },
    }),
  ]);
}

async function renderCollectionDetail(canvas, collection, ctx, rerender) {
  canvas.append(el('div', { class: 'mer-detail-header' }, [
    el('h1', { text: collection.name || '(untitled)' }),
    el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
  ]));
  if (collection.description) canvas.append(el('p', { class: 'mer-muted', text: collection.description }));

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Add an item' }));
  canvas.append(addItemForm(collection.id, ctx, rerender));

  const items = await ctx.data.CollectionItems.byIndex('collectionId', collection.id);
  const sorted = [...items].sort((a, b) => (b.acquiredDate || b.createdAt || '').localeCompare(a.acquiredDate || a.createdAt || ''));
  canvas.append(el('div', { class: 'mer-subsection-label', text: `Items (${sorted.length})` }));
  if (!sorted.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing in this collection yet.' }));
    return;
  }
  const list = el('div', { class: 'mer-people-list' });
  for (const item of sorted) list.append(itemCard(item, ctx, rerender));
  canvas.append(list);
}

function collectionCard(collection, itemCount, onSelect, ctx, rerender) {
  const card = el('div', { class: 'mer-place-card' }, [
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: collection.name || '(untitled)' }),
      el('div', { class: 'mer-place-meta' }, [el('span', { class: 'mer-chip', text: `${itemCount} item${itemCount === 1 ? '' : 's'}` })]),
    ]),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Delete collection',
      onclick: async (e) => {
        e.stopPropagation();
        if (!confirm(`Delete "${collection.name}" and all its items?`)) return;
        const items = await ctx.data.CollectionItems.byIndex('collectionId', collection.id);
        for (const item of items) await ctx.data.CollectionItems.remove(item.id);
        await ctx.data.Collections.remove(collection.id);
        rerender();
      },
    }),
  ]);
  card.addEventListener('click', () => onSelect(collection.id));
  return card;
}

function newCollectionForm(ctx, rerender) {
  const nameIn = el('input', { type: 'text', placeholder: 'Collection name (e.g. "Vinyl records")' });
  const descIn = el('input', { type: 'text', placeholder: 'Description (optional)' });
  return el('div', { class: 'mer-person-form' }, [
    nameIn, descIn,
    el('button', {
      type: 'button', text: 'Create collection',
      onclick: async () => {
        if (!nameIn.value.trim()) return;
        const c = await ctx.data.Collections.create({ name: nameIn.value.trim(), description: descIn.value.trim() });
        state.selectedId = c.id;
        rerender();
      },
    }),
  ]);
}

export async function renderCollections(canvas, ctx, rerender) {
  if (state.selectedId) {
    const collection = await ctx.data.Collections.get(state.selectedId);
    if (collection) return renderCollectionDetail(canvas, collection, ctx, rerender);
    state.selectedId = null;
  }

  canvas.append(el('h1', { text: 'Collections' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Track any collection you keep — records, cards, whatever.' }));

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'New collection' }));
  canvas.append(newCollectionForm(ctx, rerender));

  const collections = await ctx.data.Collections.list();
  canvas.append(el('div', { class: 'mer-subsection-label', text: `Your collections (${collections.length})` }));
  if (!collections.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'No collections yet.' }));
    return;
  }
  const counts = await Promise.all(collections.map((c) => ctx.data.CollectionItems.byIndex('collectionId', c.id)));
  const grid = el('div', { class: 'mer-place-grid' });
  collections.forEach((c, i) => grid.append(collectionCard(c, counts[i].length, (id) => { state.selectedId = id; rerender(); }, ctx, rerender)));
  canvas.append(grid);
}
