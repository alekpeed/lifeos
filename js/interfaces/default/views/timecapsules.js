// Time Capsules — write a sealed note to your future self, locked until a
// date you choose. Nothing enforces the lock cryptographically (this is a
// personal app, not a security boundary); the UI simply refuses to show the
// body until sealedUntil has passed, same spirit as the rest of the app's
// honor-system affordances (e.g. Books' "Finished" tab).

import { el, fmtDate, todayStr } from '../dom.js';

function isSealed(capsule) {
  return !!capsule.sealedUntil && capsule.sealedUntil > todayStr();
}

function daysUntil(dateStr) {
  const ms = new Date(dateStr + 'T00:00:00') - new Date(todayStr() + 'T00:00:00');
  return Math.max(1, Math.round(ms / 86400000));
}

function capsuleCard(capsule, ctx, rerender) {
  const sealed = isSealed(capsule);
  const body = sealed
    ? el('div', { class: 'mer-capsule-locked' }, [
      el('span', { text: '🔒 Sealed' }),
      el('span', { class: 'mer-muted', text: `Opens in ${daysUntil(capsule.sealedUntil)} day${daysUntil(capsule.sealedUntil) === 1 ? '' : 's'} (${fmtDate(capsule.sealedUntil)})` }),
    ])
    : el('div', { class: 'mer-capsule-open' }, [
      el('p', { class: 'mer-muted', text: capsule.sealedUntil ? `Opened ${fmtDate(capsule.sealedUntil)}` : 'Written' }),
      el('p', { text: capsule.body || '' }),
    ]);

  return el('div', { class: 'mer-person-card' }, [
    el('div', { class: 'mer-person-info' }, [
      el('div', { class: 'mer-person-name', text: capsule.title || '(untitled)' }),
      body,
    ]),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Delete',
      onclick: async () => { if (confirm('Delete this capsule?')) { await ctx.data.TimeCapsules.remove(capsule.id); rerender(); } },
    }),
  ]);
}

function writeForm(ctx, rerender) {
  const titleIn = el('input', { type: 'text', placeholder: 'Title (e.g. "For my 30th birthday")' });
  const bodyIn = el('textarea', { rows: '4', placeholder: 'Write to your future self…' });
  const dateIn = el('input', { type: 'date', min: todayStr() });
  return el('div', { class: 'mer-person-form' }, [
    titleIn, bodyIn, dateIn,
    el('button', {
      type: 'button', text: 'Seal it',
      onclick: async () => {
        if (!bodyIn.value.trim() || !dateIn.value) return;
        await ctx.data.TimeCapsules.create({ title: titleIn.value.trim(), body: bodyIn.value, sealedUntil: dateIn.value });
        rerender();
      },
    }),
  ]);
}

export async function renderTimeCapsules(canvas, ctx, rerender) {
  const capsules = await ctx.data.TimeCapsules.list();
  const sealed = capsules.filter(isSealed).sort((a, b) => a.sealedUntil.localeCompare(b.sealedUntil));
  const opened = capsules.filter((c) => !isSealed(c)).sort((a, b) => (b.sealedUntil || b.createdAt).localeCompare(a.sealedUntil || a.createdAt));

  canvas.append(el('h1', { text: 'Time Capsules' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Write a note now, seal it until a future date, and it surfaces on its own.' }));

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Write a new capsule' }));
  canvas.append(writeForm(ctx, rerender));

  canvas.append(el('div', { class: 'mer-subsection-label', text: `Sealed (${sealed.length})` }));
  if (!sealed.length) canvas.append(el('p', { class: 'mer-muted', text: 'Nothing sealed right now.' }));
  else {
    const list = el('div', { class: 'mer-people-list' });
    for (const c of sealed) list.append(capsuleCard(c, ctx, rerender));
    canvas.append(list);
  }

  canvas.append(el('div', { class: 'mer-subsection-label', text: `Opened (${opened.length})` }));
  if (!opened.length) canvas.append(el('p', { class: 'mer-muted', text: 'None have opened yet.' }));
  else {
    const list = el('div', { class: 'mer-people-list' });
    for (const c of opened) list.append(capsuleCard(c, ctx, rerender));
    canvas.append(list);
  }
}
