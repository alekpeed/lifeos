// Quartermaster — a physical inventory with a lending ledger: what you own,
// and who has it right now if you lent it out. Also supports cataloging from
// a photo (AI vision drafts a list of items from a shelf/pantry/garage shot)
// instead of typing each one by hand -- see catalogItemsFromImage in
// js/data/api.js.

import { el, fmtDate, todayStr, parseTags } from '../dom.js';

let catalogState = { scanning: false, error: null, draft: null };
const expandedStock = new Set(); // item ids whose stock controls are open

// Few-shot low-stock: label reference photos of an item, then judge a new photo
// against them (see judgeStockFromImage / createStockReference in api.js).
function stockControls(item, ctx, rerender) {
  const wrap = el('div', { class: 'mer-person-form' });

  const labelIn = el('input', { type: 'text', placeholder: 'label (e.g. low, full)' });
  const refInput = el('input', {
    type: 'file', accept: 'image/*', capture: 'environment',
    onchange: async (e) => {
      const file = e.target.files[0];
      e.target.value = '';
      if (!file) return;
      if (!labelIn.value.trim()) { alert('Type a label first (e.g. "low" or "full").'); return; }
      await ctx.data.createStockReference(file, item.id, labelIn.value.trim());
      labelIn.value = '';
      rerender();
    },
  });

  const checkInput = el('input', {
    type: 'file', accept: 'image/*', capture: 'environment',
    onchange: async (e) => {
      const file = e.target.files[0];
      e.target.value = '';
      if (!file) return;
      const refs = await ctx.data.getStockReferences(item.id);
      if (!refs.length) { alert('Add at least one labeled reference photo first (e.g. a "low" and a "full" shot).'); return; }
      try {
        const res = await ctx.data.judgeStockFromImage(file, refs);
        await ctx.data.InventoryItems.update(item.id, { stockStatus: res.placement, stockNote: res.note, stockCheckedAt: todayStr() });
        rerender();
      } catch (err) { alert(err.message); }
    },
  });

  wrap.append(
    el('p', { class: 'mer-muted', text: 'Label a few reference photos ("low", "full"…), then check a new photo against them.' }),
    el('label', { class: 'mer-setting' }, [el('span', { text: '📷 Check stock from photo' }), checkInput]),
    labelIn,
    el('label', { class: 'mer-setting' }, [el('span', { text: '＋ Add labeled reference' }), refInput]),
  );

  const refsHolder = el('p', { class: 'mer-muted', text: 'Loading references…' });
  wrap.append(refsHolder);
  ctx.data.getStockReferences(item.id).then((refs) => {
    if (!refs.length) { refsHolder.textContent = 'No reference photos yet.'; return; }
    const counts = refs.reduce((m, r) => { m[r.stockLabel] = (m[r.stockLabel] || 0) + 1; return m; }, {});
    refsHolder.textContent = 'References: ' + Object.entries(counts).map(([l, n]) => `${l} ×${n}`).join(', ');
  });

  return wrap;
}

function catalogPanel(ctx, rerender) {
  if (catalogState.scanning) return el('p', { class: 'mer-muted', text: 'Looking at the photo…' });

  if (catalogState.draft) {
    const rows = catalogState.draft.map((item, i) => {
      const nameIn = el('input', { type: 'text', value: item.name, onchange: (e) => { item.name = e.target.value; } });
      return el('div', { class: 'mer-person-form' }, [
        nameIn,
        item.location ? el('span', { class: 'mer-muted', text: item.location }) : null,
        el('button', {
          type: 'button', class: 'mer-icon-btn', text: '×', title: 'Remove from list',
          onclick: () => { catalogState.draft.splice(i, 1); rerender(); },
        }),
      ].filter(Boolean));
    });
    return el('div', {}, [
      el('p', { class: 'mer-muted', text: `Found ${catalogState.draft.length} item${catalogState.draft.length === 1 ? '' : 's'} — edit names, remove anything wrong, then add them.` }),
      ...rows,
      el('div', { class: 'mer-toolbar' }, [
        el('button', {
          type: 'button', text: `Add ${catalogState.draft.length} item${catalogState.draft.length === 1 ? '' : 's'}`,
          onclick: async () => {
            for (const item of catalogState.draft) {
              if (!item.name.trim()) continue;
              await ctx.data.InventoryItems.create({ name: item.name.trim(), location: item.location || '', tags: [], lentTo: null, lentSince: null });
            }
            catalogState = { scanning: false, error: null, draft: null };
            rerender();
          },
        }),
        el('button', { type: 'button', text: 'Cancel', onclick: () => { catalogState = { scanning: false, error: null, draft: null }; rerender(); } }),
      ]),
    ]);
  }

  return el('label', { class: 'mer-setting' }, [
    el('span', { text: '📷 Catalog from a photo' }),
    el('input', {
      type: 'file', accept: 'image/*', capture: 'environment',
      onchange: async (e) => {
        const file = e.target.files[0];
        e.target.value = '';
        if (!file) return;
        catalogState = { scanning: true, error: null, draft: null };
        rerender();
        try {
          const items = await ctx.data.catalogItemsFromImage(file);
          catalogState = { scanning: false, error: null, draft: items };
        } catch (err) {
          catalogState = { scanning: false, error: err.message, draft: null };
        }
        rerender();
      },
    }),
    catalogState.error ? el('p', { class: 'mer-muted', text: catalogState.error }) : null,
  ].filter(Boolean));
}

function itemCard(item, ctx, rerender) {
  const lendForm = item.lentTo
    ? el('div', { class: 'mer-person-meta' }, [
      el('span', { text: `Lent to ${item.lentTo} since ${fmtDate(item.lentSince)}` }),
      el('button', {
        type: 'button', class: 'mer-reader-btn', text: 'Mark returned',
        onclick: async () => { await ctx.data.InventoryItems.update(item.id, { lentTo: null, lentSince: null }); rerender(); },
      }),
    ])
    : (() => {
      const nameIn = el('input', { type: 'text', placeholder: 'Lend to…' });
      return el('div', { class: 'mer-person-meta' }, [nameIn, el('button', {
        type: 'button', class: 'mer-reader-btn', text: 'Lend it out',
        onclick: async () => {
          if (!nameIn.value.trim()) return;
          await ctx.data.InventoryItems.update(item.id, { lentTo: nameIn.value.trim(), lentSince: todayStr() });
          rerender();
        },
      })]);
    })();

  const stockOpen = expandedStock.has(item.id);
  const info = el('div', { class: 'mer-person-info' }, [
    el('div', { class: 'mer-person-name', text: item.name || '(untitled)' }),
    el('div', { class: 'mer-person-meta' }, [
      item.location ? el('span', { class: 'mer-chip', text: `📍 ${item.location}` }) : null,
      item.stockStatus ? el('span', { class: 'mer-chip', text: `📦 ${item.stockStatus}`, title: item.stockNote || '' }) : null,
      ...(item.tags || []).map((t) => el('span', { class: 'mer-chip mer-chip-tag', text: `#${t}` })),
      el('button', {
        type: 'button', class: 'mer-reader-btn', text: stockOpen ? 'Hide stock' : '📦 Stock',
        onclick: () => { stockOpen ? expandedStock.delete(item.id) : expandedStock.add(item.id); rerender(); },
      }),
    ].filter(Boolean)),
    lendForm,
  ]);
  if (stockOpen) info.append(stockControls(item, ctx, rerender));

  return el('div', { class: 'mer-person-card' }, [
    info,
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Remove',
      onclick: async () => { if (confirm('Remove this item?')) { await ctx.data.InventoryItems.remove(item.id); rerender(); } },
    }),
  ]);
}

function addForm(ctx, rerender) {
  const nameIn = el('input', { type: 'text', placeholder: 'Item name' });
  const locIn = el('input', { type: 'text', placeholder: 'Location (optional)' });
  const tagsIn = el('input', { type: 'text', placeholder: 'Tags (comma separated)' });
  return el('div', { class: 'mer-person-form' }, [
    nameIn, locIn, tagsIn,
    el('button', {
      type: 'button', text: 'Add item',
      onclick: async () => {
        if (!nameIn.value.trim()) return;
        await ctx.data.InventoryItems.create({ name: nameIn.value.trim(), location: locIn.value.trim(), tags: parseTags(tagsIn.value), lentTo: null, lentSince: null });
        nameIn.value = ''; locIn.value = ''; tagsIn.value = '';
        rerender();
      },
    }),
  ]);
}

export async function renderQuartermaster(canvas, ctx, rerender) {
  const items = await ctx.data.InventoryItems.list();
  const lentOut = items.filter((i) => i.lentTo);
  const onHand = items.filter((i) => !i.lentTo);

  canvas.append(el('h1', { text: 'Quartermaster' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Your physical inventory, and who has what right now.' }));

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Add an item' }));
  canvas.append(addForm(ctx, rerender));
  canvas.append(catalogPanel(ctx, rerender));

  if (lentOut.length) {
    canvas.append(el('div', { class: 'mer-subsection-label', text: `Lent out (${lentOut.length})` }));
    const list = el('div', { class: 'mer-people-list' });
    for (const item of lentOut) list.append(itemCard(item, ctx, rerender));
    canvas.append(list);
  }

  canvas.append(el('div', { class: 'mer-subsection-label', text: `On hand (${onHand.length})` }));
  if (!onHand.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing logged yet.' }));
    return;
  }
  const list = el('div', { class: 'mer-people-list' });
  for (const item of onHand) list.append(itemCard(item, ctx, rerender));
  canvas.append(list);
}
