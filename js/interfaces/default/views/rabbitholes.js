// Rabbit Hole Journal — track research tangents: what you went down a hole
// about, freeform notes as you go, a running list of links, and whether
// you've resolved it or it's still open.

import { el, fmtDate, todayStr } from '../dom.js';

let state = { selectedId: null, showResolved: false };

function linkRow(link, holeId, ctx, rerender) {
  return el('li', {}, [
    el('a', { href: link.url, target: '_blank', rel: 'noopener', text: link.title || link.url }),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Remove link',
      onclick: async (e) => {
        e.stopPropagation();
        const hole = await ctx.data.RabbitHoles.get(holeId);
        await ctx.data.RabbitHoles.update(holeId, { links: (hole.links || []).filter((l) => l.url !== link.url) });
        rerender();
      },
    }),
  ]);
}

async function renderDetail(canvas, hole, ctx, rerender) {
  canvas.append(el('div', { class: 'mer-detail-header' }, [
    el('h1', { text: hole.topic || '(untitled)' }),
    el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
  ]));
  canvas.append(el('p', { class: 'mer-muted', text: `Started ${fmtDate(hole.startedDate)}` }));

  const notesIn = el('textarea', { rows: '6', placeholder: 'Notes as you go…' }, [document.createTextNode(hole.notes || '')]);
  notesIn.addEventListener('change', async () => { await ctx.data.RabbitHoles.update(hole.id, { notes: notesIn.value }); });
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Notes' }));
  canvas.append(el('div', { class: 'mer-person-form' }, [notesIn]));

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Add a link' }));
  const urlIn = el('input', { type: 'url', placeholder: 'https://…' });
  const titleIn = el('input', { type: 'text', placeholder: 'Title (optional)' });
  canvas.append(el('div', { class: 'mer-person-form' }, [urlIn, titleIn, el('button', {
    type: 'button', text: 'Add link',
    onclick: async () => {
      if (!urlIn.value.trim()) return;
      const links = [...(hole.links || []), { url: urlIn.value.trim(), title: titleIn.value.trim() }];
      await ctx.data.RabbitHoles.update(hole.id, { links });
      rerender();
    },
  })]));

  canvas.append(el('div', { class: 'mer-subsection-label', text: `Links (${(hole.links || []).length})` }));
  if ((hole.links || []).length) {
    const ul = el('ul', { class: 'mer-starter-list' });
    for (const link of hole.links) ul.append(linkRow(link, hole.id, ctx, rerender));
    canvas.append(ul);
  } else {
    canvas.append(el('p', { class: 'mer-muted', text: 'No links yet.' }));
  }

  canvas.append(el('div', { class: 'mer-toolbar' }, [
    hole.status === 'resolved'
      ? el('button', { type: 'button', text: 'Reopen', onclick: async () => { await ctx.data.RabbitHoles.update(hole.id, { status: 'active' }); rerender(); } })
      : el('button', { type: 'button', text: 'Mark resolved', onclick: async () => { await ctx.data.RabbitHoles.update(hole.id, { status: 'resolved' }); rerender(); } }),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete',
      onclick: async () => { if (confirm('Delete this rabbit hole?')) { await ctx.data.RabbitHoles.remove(hole.id); state.selectedId = null; rerender(); } },
    }),
  ]));
}

function holeRow(hole, onSelect) {
  const row = el('div', { class: 'mer-task-row' }, [
    el('span', { class: 'mer-task-title', text: hole.topic || '(untitled)' }),
    el('span', { class: 'mer-task-meta', text: `${(hole.links || []).length} link${(hole.links || []).length === 1 ? '' : 's'}` }),
  ]);
  row.addEventListener('click', () => onSelect(hole.id));
  return row;
}

export async function renderRabbitHoles(canvas, ctx, rerender) {
  if (state.selectedId) {
    const hole = await ctx.data.RabbitHoles.get(state.selectedId);
    if (hole) return renderDetail(canvas, hole, ctx, rerender);
    state.selectedId = null;
  }

  canvas.append(el('h1', { text: 'Rabbit Hole Journal' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Track what you went down a hole researching.' }));

  const topicIn = el('input', { type: 'text', placeholder: 'What are you researching?' });
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Start a new one' }));
  canvas.append(el('div', { class: 'mer-person-form' }, [topicIn, el('button', {
    type: 'button', text: 'Start',
    onclick: async () => {
      if (!topicIn.value.trim()) return;
      const hole = await ctx.data.RabbitHoles.create({ topic: topicIn.value.trim(), notes: '', links: [], status: 'active', startedDate: todayStr() });
      state.selectedId = hole.id;
      rerender();
    },
  })]));

  const holes = await ctx.data.RabbitHoles.list();
  const active = holes.filter((h) => h.status !== 'resolved');
  const resolved = holes.filter((h) => h.status === 'resolved');

  canvas.append(el('div', { class: 'mer-subsection-label', text: `Active (${active.length})` }));
  if (!active.length) canvas.append(el('p', { class: 'mer-muted', text: 'Nothing open right now.' }));
  else {
    const area = el('div', { class: 'mer-task-list-area' });
    for (const h of active) area.append(holeRow(h, (id) => { state.selectedId = id; rerender(); }));
    canvas.append(area);
  }

  if (resolved.length) {
    canvas.append(el('button', {
      type: 'button', class: 'mer-reader-btn', text: state.showResolved ? 'Hide resolved' : `Show resolved (${resolved.length})`,
      onclick: () => { state.showResolved = !state.showResolved; rerender(); },
    }));
    if (state.showResolved) {
      const area = el('div', { class: 'mer-task-list-area' });
      for (const h of resolved) area.append(holeRow(h, (id) => { state.selectedId = id; rerender(); }));
      canvas.append(area);
    }
  }
}
