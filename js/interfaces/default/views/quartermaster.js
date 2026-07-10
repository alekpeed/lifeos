// Quartermaster — a physical inventory with a lending ledger: what you own,
// and who has it right now if you lent it out.

import { el, fmtDate, todayStr, parseTags } from '../dom.js';

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

  return el('div', { class: 'mer-person-card' }, [
    el('div', { class: 'mer-person-info' }, [
      el('div', { class: 'mer-person-name', text: item.name || '(untitled)' }),
      el('div', { class: 'mer-person-meta' }, [
        item.location ? el('span', { class: 'mer-chip', text: `📍 ${item.location}` }) : null,
        ...(item.tags || []).map((t) => el('span', { class: 'mer-chip mer-chip-tag', text: `#${t}` })),
      ].filter(Boolean)),
      lendForm,
    ]),
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
