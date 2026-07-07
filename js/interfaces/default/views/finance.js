import { el, fmtDate, todayStr } from '../dom.js';

const FREQS = [
  { value: 'monthly', label: 'Monthly' },
  { value: 'yearly', label: 'Yearly' },
  { value: 'weekly', label: 'Weekly' },
];

let state = {
  tab: 'networth', // networth | goals | subscriptions
  categoryFilter: 'all',
  showCancelled: false,
  selectedSnapshotId: null,
  selectedGoalId: null,
  selectedSubId: null,
};

function money(n) {
  return `$${Number(n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function monthlyEquivalent(amount, freq) {
  const a = Number(amount) || 0;
  if (freq === 'yearly') return a / 12;
  if (freq === 'weekly') return (a * 52) / 12;
  return a;
}

function progressBar(pct) {
  const clamped = Math.max(0, Math.min(100, pct));
  return el('div', { class: 'mer-progress' }, [
    el('div', { class: 'mer-progress-fill', style: `width: ${clamped}%` }),
  ]);
}

// --- Net Worth ---

function netWorth(snapshot) {
  const assets = (snapshot.assets || []).reduce((sum, a) => sum + (Number(a.value) || 0), 0);
  const liabilities = (snapshot.liabilities || []).reduce((sum, l) => sum + (Number(l.value) || 0), 0);
  return assets - liabilities;
}

function lineItemsEditor(snapshot, key, ctx, rerender) {
  const rows = el('div', {}, (snapshot[key] || []).map((item) => {
    const nameInput = el('input', { type: 'text', value: item.name || '', placeholder: 'Name' });
    const valueInput = el('input', { type: 'number', step: '0.01', value: item.value ?? '', placeholder: 'Value' });
    const commit = () => {
      const items = snapshot[key].map((i) => i.id === item.id ? { ...i, name: nameInput.value, value: Number(valueInput.value) || 0 } : i);
      ctx.data.FinanceSnapshots.update(snapshot.id, { [key]: items }).then(rerender);
    };
    nameInput.onchange = commit;
    valueInput.onchange = commit;
    return el('div', { class: 'mer-person-form' }, [
      nameInput, valueInput,
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: () => {
          const items = snapshot[key].filter((i) => i.id !== item.id);
          ctx.data.FinanceSnapshots.update(snapshot.id, { [key]: items }).then(rerender);
        },
      }),
    ]);
  }));

  const newName = el('input', { type: 'text', placeholder: 'Name' });
  const newValue = el('input', { type: 'number', step: '0.01', placeholder: 'Value' });
  const addBtn = el('button', {
    type: 'button', text: '+ Add',
    onclick: () => {
      if (!newName.value.trim()) return;
      const items = [...(snapshot[key] || []), { id: crypto.randomUUID(), name: newName.value.trim(), value: Number(newValue.value) || 0 }];
      ctx.data.FinanceSnapshots.update(snapshot.id, { [key]: items }).then(rerender);
    },
  });

  return el('div', {}, [rows, el('div', { class: 'mer-person-form' }, [newName, newValue, addBtn])]);
}

function snapshotDetail(snapshot, ctx, rerender) {
  const patch = (fields) => ctx.data.FinanceSnapshots.update(snapshot.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const dateInput = el('input', { type: 'date', value: snapshot.date || todayStr(), onchange: (e) => patch({ date: e.target.value }) });
  const notesInput = el('textarea', { rows: '2', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(snapshot.notes || '')]);

  return el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: `Snapshot — ${fmtDate(snapshot.date)}` }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedSnapshotId = null; rerender(); } }),
    ]),
    field('Date', dateInput),
    el('p', {}, [el('strong', { text: `Net worth: ${money(netWorth(snapshot))}` })]),
    el('div', { class: 'mer-subsection-label', text: 'Assets' }),
    lineItemsEditor(snapshot, 'assets', ctx, rerender),
    el('div', { class: 'mer-subsection-label', text: 'Liabilities' }),
    lineItemsEditor(snapshot, 'liabilities', ctx, rerender),
    field('Notes', notesInput),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete snapshot',
      onclick: async () => { await ctx.data.FinanceSnapshots.remove(snapshot.id); state.selectedSnapshotId = null; rerender(); },
    }),
  ]);
}

async function renderNetWorth(container, ctx, rerender) {
  const snapshots = (await ctx.data.FinanceSnapshots.list()).sort((a, b) => (a.date < b.date ? 1 : -1));

  const newBtn = el('button', {
    type: 'button', text: '+ New snapshot (today)',
    onclick: async () => {
      const created = await ctx.data.FinanceSnapshots.create({ date: todayStr(), assets: [], liabilities: [], notes: '' });
      state.selectedSnapshotId = created.id;
      rerender();
    },
  });
  container.append(el('div', { class: 'mer-toolbar' }, [newBtn]));

  const area = el('div', {});
  container.append(area);

  if (!snapshots.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No snapshots yet — add one to start tracking net worth over time.' }));
  } else {
    area.append(el('p', {}, [el('strong', { text: `Latest net worth: ${money(netWorth(snapshots[0]))}` }), el('span', { text: ` as of ${fmtDate(snapshots[0].date)}` })]));

    const list = el('div', {});
    snapshots.forEach((snap, i) => {
      const nw = netWorth(snap);
      const prev = snapshots[i + 1] ? netWorth(snapshots[i + 1]) : null;
      const delta = prev !== null ? nw - prev : null;
      const row = el('div', { class: 'mer-task-row' }, [
        el('span', { class: 'mer-task-title', text: fmtDate(snap.date) }),
        el('div', { class: 'mer-task-meta' }, [
          el('span', { class: 'mer-chip', text: money(nw) }),
          delta !== null ? el('span', { class: delta >= 0 ? 'mer-chip' : 'mer-chip is-overdue', text: `${delta >= 0 ? '+' : ''}${money(delta)}` }) : null,
        ]),
      ]);
      row.addEventListener('click', () => { state.selectedSnapshotId = state.selectedSnapshotId === snap.id ? null : snap.id; rerender(); });
      list.append(row);
    });
    area.append(list);
  }

  if (state.selectedSnapshotId) {
    const snap = snapshots.find((s) => s.id === state.selectedSnapshotId);
    if (snap) container.append(snapshotDetail(snap, ctx, rerender));
  }
}

// --- Savings Goals ---

function goalDetail(goal, ctx, rerender) {
  const patch = (fields) => ctx.data.SavingsGoals.update(goal.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const nameInput = el('input', { type: 'text', value: goal.name || '', onchange: (e) => patch({ name: e.target.value }) });
  const targetInput = el('input', { type: 'number', step: '0.01', value: goal.targetAmount ?? '', onchange: (e) => patch({ targetAmount: Number(e.target.value) || 0 }) });
  const currentInput = el('input', { type: 'number', step: '0.01', value: goal.currentAmount ?? '', onchange: (e) => patch({ currentAmount: Number(e.target.value) || 0 }) });
  const targetDateInput = el('input', { type: 'date', value: goal.targetDate || '', onchange: (e) => patch({ targetDate: e.target.value || null }) });
  const notesInput = el('textarea', { rows: '2', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(goal.notes || '')]);

  const pct = goal.targetAmount ? Math.round(((goal.currentAmount || 0) / goal.targetAmount) * 100) : 0;

  return el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: goal.name || '(untitled goal)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedGoalId = null; rerender(); } }),
    ]),
    progressBar(pct),
    el('p', { class: 'mer-muted', text: `${money(goal.currentAmount)} of ${money(goal.targetAmount)} (${pct}%)` }),
    el('div', { class: 'mer-field-grid' }, [
      field('Name', nameInput),
      field('Target amount', targetInput),
      field('Current amount', currentInput),
      field('Target date', targetDateInput),
    ]),
    field('Notes', notesInput),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete goal',
      onclick: async () => { await ctx.data.SavingsGoals.remove(goal.id); state.selectedGoalId = null; rerender(); },
    }),
  ]);
}

async function renderGoals(container, ctx, rerender) {
  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New savings goal — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.SavingsGoals.create({ name: e.target.value.trim(), targetAmount: 0, currentAmount: 0 });
      e.target.value = '';
      rerender();
    },
  });
  container.append(el('div', { class: 'mer-toolbar' }, [quickAdd]));

  const goals = await ctx.data.SavingsGoals.list();
  const area = el('div', {});
  container.append(area);

  if (!goals.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No savings goals yet.' }));
  } else {
    for (const goal of goals) {
      const pct = goal.targetAmount ? Math.round(((goal.currentAmount || 0) / goal.targetAmount) * 100) : 0;
      const row = el('div', { class: 'mer-task-row' }, [
        el('span', { class: 'mer-task-title', text: goal.name || '(untitled goal)' }),
        el('div', { class: 'mer-task-meta' }, [
          el('span', { class: 'mer-chip', text: `${money(goal.currentAmount)} / ${money(goal.targetAmount)}` }),
          el('span', { class: 'mer-chip', text: `${pct}%` }),
          goal.targetDate ? el('span', { class: 'mer-chip', text: fmtDate(goal.targetDate) }) : null,
        ]),
      ]);
      row.addEventListener('click', () => { state.selectedGoalId = state.selectedGoalId === goal.id ? null : goal.id; rerender(); });
      area.append(row);
    }
  }

  if (state.selectedGoalId) {
    const goal = goals.find((g) => g.id === state.selectedGoalId);
    if (goal) container.append(goalDetail(goal, ctx, rerender));
  }
}

// --- Subscriptions ---

function subDetail(sub, ctx, rerender) {
  const patch = (fields) => ctx.data.Subscriptions.update(sub.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const nameInput = el('input', { type: 'text', value: sub.name || '', onchange: (e) => patch({ name: e.target.value }) });
  const amountInput = el('input', { type: 'number', step: '0.01', value: sub.amount ?? '', onchange: (e) => patch({ amount: Number(e.target.value) || 0 }) });
  const freqSelect = el('select', { onchange: (e) => patch({ billingFreq: e.target.value }) },
    FREQS.map((f) => el('option', { value: f.value, text: f.label, selected: f.value === (sub.billingFreq || 'monthly') })));
  const categoryInput = el('input', { type: 'text', value: sub.category || '', placeholder: 'streaming, software…', onchange: (e) => patch({ category: e.target.value }) });
  const renewalInput = el('input', { type: 'date', value: sub.renewalDate || '', onchange: (e) => patch({ renewalDate: e.target.value || null }) });
  const notesInput = el('textarea', { rows: '2', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(sub.notes || '')]);
  const activeCheckbox = el('label', { class: 'mer-checkbox-label' }, [
    el('input', { type: 'checkbox', checked: sub.stillInUse !== false, onchange: (e) => patch({ stillInUse: e.target.checked }) }),
    el('span', { text: 'Still using this' }),
  ]);

  return el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: sub.name || '(untitled subscription)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedSubId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Name', nameInput),
      field('Amount', amountInput),
      field('Billing', freqSelect),
      field('Category', categoryInput),
      field('Renewal date', renewalInput),
    ]),
    activeCheckbox,
    field('Notes', notesInput),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete subscription',
      onclick: async () => { await ctx.data.Subscriptions.remove(sub.id); state.selectedSubId = null; rerender(); },
    }),
  ]);
}

async function renderSubscriptions(container, ctx, rerender) {
  const subs = await ctx.data.Subscriptions.list();
  const categories = [...new Set(subs.map((s) => s.category).filter(Boolean))];

  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New subscription — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Subscriptions.create({ name: e.target.value.trim(), amount: 0, billingFreq: 'monthly', stillInUse: true });
      e.target.value = '';
      rerender();
    },
  });
  const categorySelect = el('select', { onchange: (e) => { state.categoryFilter = e.target.value; rerender(); } }, [
    el('option', { value: 'all', text: 'All categories', selected: state.categoryFilter === 'all' }),
    ...categories.map((c) => el('option', { value: c, text: c, selected: c === state.categoryFilter })),
  ]);
  const cancelledToggle = el('label', { class: 'mer-checkbox-label' }, [
    el('input', { type: 'checkbox', checked: state.showCancelled, onchange: (e) => { state.showCancelled = e.target.checked; rerender(); } }),
    el('span', { text: 'Show cancelled' }),
  ]);
  container.append(el('div', { class: 'mer-toolbar' }, [quickAdd, categorySelect, cancelledToggle]));

  const filtered = subs
    .filter((s) => state.categoryFilter === 'all' || s.category === state.categoryFilter)
    .filter((s) => state.showCancelled || s.stillInUse !== false);

  const totalMonthly = filtered.filter((s) => s.stillInUse !== false).reduce((sum, s) => sum + monthlyEquivalent(s.amount, s.billingFreq), 0);
  const area = el('div', {});
  container.append(area);
  area.append(el('p', {}, [el('strong', { text: `${money(totalMonthly)}/mo` }), el('span', { text: ' across active subscriptions' })]));

  if (!filtered.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No subscriptions match the current filters.' }));
  } else {
    for (const sub of filtered) {
      const row = el('div', { class: 'mer-task-row' }, [
        el('span', { class: sub.stillInUse === false ? 'mer-task-title is-done' : 'mer-task-title', text: sub.name || '(untitled)' }),
        el('div', { class: 'mer-task-meta' }, [
          el('span', { class: 'mer-chip', text: money(sub.amount) }),
          el('span', { class: 'mer-chip', text: FREQS.find((f) => f.value === (sub.billingFreq || 'monthly'))?.label }),
          sub.category ? el('span', { class: 'mer-chip', text: sub.category }) : null,
          sub.stillInUse === false ? el('span', { class: 'mer-chip', text: 'Cancelled' }) : null,
        ]),
      ]);
      row.addEventListener('click', () => { state.selectedSubId = state.selectedSubId === sub.id ? null : sub.id; rerender(); });
      area.append(row);
    }
  }

  if (state.selectedSubId) {
    const sub = subs.find((s) => s.id === state.selectedSubId);
    if (sub) container.append(subDetail(sub, ctx, rerender));
  }
}

// --- Root ---

function tabsBar(rerender) {
  return el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.tab === 'networth' ? 'is-active' : '', text: 'Net Worth', onclick: () => { state.tab = 'networth'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'goals' ? 'is-active' : '', text: 'Savings Goals', onclick: () => { state.tab = 'goals'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'subscriptions' ? 'is-active' : '', text: 'Subscriptions', onclick: () => { state.tab = 'subscriptions'; rerender(); } }),
  ]);
}

export async function renderFinance(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Finance' }));
  canvas.append(el('div', { class: 'mer-toolbar' }, [tabsBar(rerender)]));

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  if (state.tab === 'networth') await renderNetWorth(area, ctx, rerender);
  else if (state.tab === 'goals') await renderGoals(area, ctx, rerender);
  else await renderSubscriptions(area, ctx, rerender);
}
