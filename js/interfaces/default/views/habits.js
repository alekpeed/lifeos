import { el, todayStr } from '../dom.js';

let state = {
  selectedId: null,
};

function computeStreak(dates) {
  const days = new Set(dates);
  let streak = 0;
  const cursor = new Date(todayStr() + 'T00:00:00');
  if (!days.has(cursor.toISOString().slice(0, 10))) {
    cursor.setDate(cursor.getDate() - 1);
  }
  while (days.has(cursor.toISOString().slice(0, 10))) {
    streak++;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

function habitRow(habit, logsByHabit, ctx, onSelect, rerender) {
  const logs = logsByHabit.get(habit.id) || [];
  const streak = computeStreak(logs.map((l) => l.date));
  const doneToday = logs.some((l) => l.date === todayStr());

  const toggle = el('input', {
    type: 'checkbox', checked: doneToday,
    onclick: async (e) => {
      e.stopPropagation();
      if (e.target.checked) {
        await ctx.data.HabitLogs.create({ habitId: habit.id, date: todayStr() });
      } else {
        const todayLog = logs.find((l) => l.date === todayStr());
        if (todayLog) await ctx.data.HabitLogs.remove(todayLog.id);
      }
      rerender();
    },
  });

  const row = el('div', { class: 'mer-task-row' }, [
    toggle,
    el('span', { class: 'mer-task-title', text: habit.name || '(untitled)' }),
    el('div', { class: 'mer-task-meta' }, [
      el('span', { class: 'mer-chip', text: `🔥 ${streak}` }),
    ]),
  ]);
  row.addEventListener('click', () => onSelect(habit.id));
  return row;
}

function detailEditor(habit, logs, ctx, rerender) {
  const patch = (fields) => ctx.data.Habits.update(habit.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const nameInput = el('input', { type: 'text', value: habit.name || '', onchange: (e) => patch({ name: e.target.value }) });
  const notesInput = el('textarea', { rows: '2', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(habit.notes || '')]);

  const streak = computeStreak(logs.map((l) => l.date));
  const totalCheckIns = logs.length;

  return el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: habit.name || '(untitled)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
    ]),
    el('p', {}, [el('strong', { text: `🔥 ${streak}-day streak` }), el('span', { text: ` · ${totalCheckIns} total check-ins` })]),
    field('Name', nameInput),
    field('Notes', notesInput),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete habit',
      onclick: async () => {
        await ctx.data.Habits.remove(habit.id);
        for (const log of logs) await ctx.data.HabitLogs.remove(log.id);
        state.selectedId = null;
        rerender();
      },
    }),
  ]);
}

export async function renderHabits(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Habits' }));

  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New habit — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Habits.create({ name: e.target.value.trim() });
      e.target.value = '';
    },
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [quickAdd]));

  const [habits, allLogs] = await Promise.all([ctx.data.Habits.list(), ctx.data.HabitLogs.list()]);
  const logsByHabit = new Map();
  for (const log of allLogs) {
    if (!logsByHabit.has(log.habitId)) logsByHabit.set(log.habitId, []);
    logsByHabit.get(log.habitId).push(log);
  }

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  const onSelect = (id) => { state.selectedId = state.selectedId === id ? null : id; rerender(); };

  if (!habits.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No habits yet -- add one to start a streak.' }));
  } else {
    for (const habit of habits) area.append(habitRow(habit, logsByHabit, ctx, onSelect, rerender));
  }

  if (state.selectedId) {
    const habit = habits.find((h) => h.id === state.selectedId);
    if (habit) canvas.append(detailEditor(habit, logsByHabit.get(habit.id) || [], ctx, rerender));
  }
}
