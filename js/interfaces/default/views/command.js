// Command -- the natural-language command bar. Type (or speak) a plain command
// like "remind me to call mom Friday" and it's parsed into a structured action
// (see parseCommand in js/data/api.js), shown for confirmation, then created.
// The AI proposes; you approve before anything is written.

import { el, todayStr } from '../dom.js';

let state = { input: '', interp: null, busy: false, result: null, error: null };

const ACTION_MODULE = { task: 'tasks', idea: 'ideas', contact: 'contacts', bill: 'finance', habit: 'habits' };

async function execute(interp, ctx) {
  const f = interp.fields || {};
  if (interp.action === 'task') {
    await ctx.data.Tasks.create({ title: f.title || 'Untitled', status: 'not_started', priority: 'low', dueDate: f.dueDate || null });
    return 'Task created.';
  }
  if (interp.action === 'idea') {
    await ctx.data.Ideas.create({ text: f.text || '', archived: false });
    return 'Idea captured.';
  }
  if (interp.action === 'contact') {
    await ctx.data.Contacts.create({ name: f.name || 'Untitled', notes: f.notes || '' });
    return 'Contact added.';
  }
  if (interp.action === 'bill') {
    await ctx.data.Bills.create({ name: f.name || 'Bill', amount: typeof f.amount === 'number' ? f.amount : null, dueDate: f.dueDate || null, paid: false });
    return 'Bill added.';
  }
  if (interp.action === 'habit') {
    const habits = await ctx.data.Habits.list();
    const wanted = (f.habitName || '').toLowerCase().trim();
    const match = habits.find((h) => (h.name || '').toLowerCase() === wanted)
      || habits.find((h) => (h.name || '').toLowerCase().includes(wanted));
    if (!match) throw new Error(`No habit matching "${f.habitName}".`);
    await ctx.data.HabitLogs.create({ habitId: match.id, date: todayStr() });
    return `Checked in "${match.name}".`;
  }
  throw new Error('Nothing to do.');
}

function micButton(input) {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SR) return null;
  return el('button', {
    type: 'button', class: 'mer-reader-btn', text: '🎤', title: 'Speak',
    onclick: () => {
      const rec = new SR();
      rec.lang = 'en-US';
      rec.interimResults = false;
      rec.onresult = (e) => { input.value = e.results[0][0].transcript; input.focus(); };
      rec.start();
    },
  });
}

export async function renderCommand(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Command' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Type or speak a plain command — "remind me to call mom Friday", "note: try the new ramen place", "add rent bill $1200 due the 1st" — and it turns into the right record. You confirm before anything\'s created.' }));

  const interpret = async (value) => {
    state.input = value;
    state.error = null;
    state.result = null;
    if (!value.trim()) { state.interp = null; rerender(); return; }
    state.busy = true;
    rerender();
    try {
      state.interp = await ctx.data.parseCommand(value);
    } catch (err) {
      state.error = err.message || String(err);
      state.interp = null;
    }
    state.busy = false;
    rerender();
  };

  const input = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: 'What do you want to do?', value: state.input,
    onkeydown: (e) => { if (e.key === 'Enter') interpret(e.target.value); },
  });
  const controls = [input];
  const mic = micButton(input);
  if (mic) controls.push(mic);
  controls.push(el('button', { type: 'button', text: 'Interpret', onclick: () => interpret(input.value) }));
  canvas.append(el('div', { class: 'mer-toolbar' }, controls));

  if (state.busy) canvas.append(el('p', { class: 'mer-muted', text: 'Reading that…' }));

  if (state.error) {
    const p = el('p', { class: 'mer-muted', text: state.error });
    p.classList.add('mer-sync-error');
    canvas.append(p);
  }

  if (state.result) {
    canvas.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [el('div', { class: 'mer-person-name', text: `✓ ${state.result.message}` })]),
      el('button', { type: 'button', class: 'mer-reader-btn', text: 'Open', onclick: () => ctx.navigate(state.result.module) }),
    ]));
  }

  if (state.interp && state.interp.action !== 'none') {
    canvas.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [
        el('div', { class: 'mer-person-name', text: state.interp.summary || `Create a ${state.interp.action}` }),
        el('div', { class: 'mer-person-meta' }, [el('span', { class: 'mer-chip', text: state.interp.action })]),
      ]),
      el('div', { class: 'mer-toolbar' }, [
        el('button', {
          type: 'button', text: 'Confirm',
          onclick: async (e) => {
            e.target.disabled = true;
            try {
              const message = await execute(state.interp, ctx);
              state.result = { message, module: ACTION_MODULE[state.interp.action] || 'dashboard' };
              state.interp = null;
              state.input = '';
            } catch (err) {
              state.error = err.message || String(err);
            }
            rerender();
          },
        }),
        el('button', { type: 'button', text: 'Cancel', onclick: () => { state.interp = null; rerender(); } }),
      ]),
    ]));
  } else if (state.interp && state.interp.action === 'none') {
    canvas.append(el('p', { class: 'mer-muted', text: state.interp.summary || "Didn't catch a clear action — try rephrasing." }));
  }
}
