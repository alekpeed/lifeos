// Briefing -- the chief-of-staff view. A PRIORITIZED worklist of what actually
// needs you right now, with a one-tap suggested action per item where it makes
// sense. Distinct from the Daily Paper (a read-only editorial): this triages
// and lets you act. All items are real computed data (see getBriefing in
// js/data/api.js) -- no AI, no invention.

import { el, todayStr } from '../dom.js';

const KIND_ICON = {
  bill: '💵', task: '✅', assignment: '🎓', document: '📄', habit: '🔥', rabbithole: '🕳️',
};

function tomorrowStr() {
  const t = new Date();
  t.setDate(t.getDate() + 1);
  return new Date(t.getFullYear(), t.getMonth(), t.getDate()).toISOString().slice(0, 10);
}

async function runAction(action, ctx) {
  if (action.type === 'checkin') {
    await ctx.data.HabitLogs.create({ habitId: action.id, date: todayStr() });
  } else if (action.type === 'snooze') {
    await ctx.data.Tasks.update(action.id, { snoozedUntil: tomorrowStr() });
  } else if (action.type === 'renew') {
    await ctx.data.Tasks.create({ title: `Renew: ${action.title}`, status: 'not_started', priority: 'low', dueDate: action.expiryDate });
  }
}

function briefingRow(item, ctx, rerender) {
  const buttons = [];
  if (item.action) {
    buttons.push(el('button', {
      type: 'button', class: 'mer-reader-btn', text: item.action.label,
      onclick: async (e) => {
        e.stopPropagation();
        e.target.disabled = true;
        try { await runAction(item.action, ctx); await rerender(); }
        catch (err) { alert(err.message || String(err)); e.target.disabled = false; }
      },
    }));
  }
  buttons.push(el('button', {
    type: 'button', class: 'mer-reader-btn', text: 'Open',
    onclick: (e) => { e.stopPropagation(); ctx.navigate(item.module); },
  }));

  return el('div', { class: 'mer-person-card' }, [
    el('div', { class: 'mer-person-info' }, [
      el('div', { class: 'mer-person-name', text: `${KIND_ICON[item.kind] || '•'} ${item.title}` }),
      el('div', { class: 'mer-person-meta' }, [el('span', { class: 'mer-muted', text: item.detail })]),
    ]),
    el('div', { class: 'mer-toolbar' }, buttons),
  ]);
}

export async function renderBriefing(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Briefing' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'What actually needs you right now, most urgent first — with a one-tap action where it helps. Real data only, no guesswork.' }));

  const items = await ctx.data.getBriefing();
  if (!items.length) {
    canvas.append(el('p', { class: 'mer-muted', text: "You're all clear — nothing needs your attention right now. 🎉" }));
    return;
  }

  const list = el('div', { class: 'mer-people-list' });
  for (const item of items) list.append(briefingRow(item, ctx, rerender));
  canvas.append(list);
}
